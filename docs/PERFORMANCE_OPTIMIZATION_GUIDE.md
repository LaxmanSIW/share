# InvoiceStudio Performance Optimization Guide
## Non-Breaking High-Performance Architecture for Billing & Template Designing

> **Guiding Principle**: Achieve 60 FPS UI responsiveness, sub-50ms user interactions, and instant printing/PDF generation **without altering any visual output, millimeter precision, calculation formulas, or user workflows**.

---

## Table of Contents
1. [Core Pillars & Architectural Overview](#1-core-pillars--architectural-overview)
2. [Pillar 1: Billing & Invoice Management System](#2-pillar-1-billing--invoice-management-system)
   - 2.1 The Bottlenecks
   - 2.2 Non-Breaking Optimizations
   - 2.3 Implementation Details & Code Blueprints
3. [Pillar 2: Bill Template Designer & Rendering Engine](#3-pillar-2-bill-template-designer--rendering-engine)
   - 3.1 The Bottlenecks
   - 3.2 Non-Breaking Optimizations
   - 3.3 Implementation Details & Code Blueprints
4. [Cross-Cutting Performance Strategies](#4-cross-cutting-performance-strategies)
   - 4.1 Zero-Work on JavaFX Application Thread
   - 4.2 Raster Caching for Heavy Shapes & SVGs
   - 4.3 QR Code & Base64 Image In-Memory Caching
   - 4.4 JVM & Packaging Tunings
5. [Verification & Performance Audit Checklist](#5-verification--performance-audit-checklist)

---

## 1. Core Pillars & Architectural Overview

InvoiceStudio serves two distinct functional missions:
```
+-------------------------------------------------------------------------------+
|                                INVOICESTUDIO                                  |
+---------------------------------------+---------------------------------------+
|  PILLAR 1: BILLING & INVOICE MGMT     |  PILLAR 2: TEMPLATE DESIGNER & ENGINE |
+---------------------------------------+---------------------------------------+
| - Document creation (Tax Invoice,     | - WYSIWYG Template Canvas             |
|   Bill of Supply, Quotations, etc.)   | - 18+ Geometric & Dynamic Elements    |
| - Live calculations (GST, IGST, etc.) | - Snap-to-grid, margins, guidelines   |
| - Real-time invoice preview           | - Multi-layer z-index management      |
| - SQLite data persistence             | - Property inspector & live updates   |
| - Thermal & standard paper printing   | - Custom fonts, barcodes, QR, tables  |
| - 300 DPI high-res PDF generation     | - Roll paper / auto-height extension  |
+---------------------------------------+---------------------------------------+
```

---

## 2. Pillar 1: Billing & Invoice Management System

### 2.1 Identified Bottlenecks

1. **Preview Thrashing on Keystroke (`CreateBillView.java` & `BillPreviewPane.java`)**:
   - In `CreateBillView`, every character typed in description, quantity, rate, discount, or custom fields invokes `updateTotalsAndPreview()`.
   - `previewPane.render()` executes `pagePane.getChildren().clear()`, rebuilding every line, text, table row, QR code, and barcode from scratch.
   - For a document with 15 line items and logo/QR, this destroys and recreates 100+ JavaFX nodes on **every single keystroke**.

2. **Synchronous Database Operations on UI Thread (`CreateBillView.java`)**:
   - In `saveBill()`, database calls (`app.getData().saveBill(bill)`, `saveSettings()`, `saveBuyer()`) execute synchronously on the JavaFX Application Thread.
   - When a bill is saved, `app.reloadAllData()` triggers fresh database queries on the UI thread, causing perceptible frame drops.

3. **High-Memory 300 DPI Allocations (`PdfExportService.java`)**:
   - In `PdfExportService.exportBillPdf`, an A4 page at 300 DPI creates a `BufferedImage(2480, 3508, TYPE_INT_RGB)`, consuming ~35 MB of heap per page copy.
   - Exporting or printing multiple copies generates massive GC pressure if not recycled.

4. **Synchronous Catalog Dialog Queries (`CreateBillView.java:L1298`)**:
   - `pickCatalogItem` directly invokes `itemDao.getAllItems()` on the UI thread when opening the selection dialog.

---

### 2.2 Non-Breaking Optimizations

| Bottleneck | Optimization Strategy | Behavioral Guarantee |
| :--- | :--- | :--- |
| **Real-time Preview Rebuild** | **Debounced Preview Rendering (150ms)**: Calculations run immediately (0ms delay), while visual preview rebuild is debounced using `javafx.animation.PauseTransition`. | Numbers update instantly in the editor; the visual preview refreshes smoothly without UI stutter. |
| **Preview Image/QR Re-generation** | **In-Memory LRU Bitmap Cache**: Cache generated QR codes, barcodes, and decoded logo `Image` instances by hash of payload/dimensions. | Identical visual output; eliminates ZXing matrix generation and Base64 decoding on every render. |
| **Save & Print Blocking** | **Asynchronous Background Execution**: Offload SQLite write + PDF rasterization to `StudioApp.dbExecutor`. Show a fast spinner toast. | Zero UI freeze during save, print dialog open, or PDF file writing. |
| **Table Line Items Layout** | **Batch Node Mutation**: Wrap dynamic rows insertion in an unmanaged or invisible container during rebuild to suppress intermediate layout passes. | Identical table styling and alignment. |

---

### 2.3 Implementation Details & Code Blueprints

#### A. 150ms Preview Debouncing in `CreateBillView.java`
```java
// Keep financial calculations immediate, but debounce the expensive scene graph rebuild
private final PauseTransition previewDebounce = new PauseTransition(Duration.millis(150));

private void updateTotalsAndPreview() {
    // 1. Calculations happen IMMEDIATELY (0ms latency for inputs & labels)
    recalculateTotalsImmediate();

    // 2. Visual scene-graph rebuild is debounced
    previewDebounce.setOnFinished(e -> {
        if (currentTemplate != null) {
            Bill currentBillState = buildBillObject(computedTotals, amountWords);
            previewPane.render(currentTemplate, currentBillState, currentSettings);
        }
    });
    previewDebounce.playFromStart();
}
```

#### B. Asynchronous Save & Notification
```java
private void saveBill(boolean printAfter, boolean pdfAfter) {
    // 1. UI validation on FX thread
    if (billNoField.getText().isBlank()) return;
    
    // 2. Build immutable snapshot
    final Bill snapshot = buildBillObject(totals, words);
    final boolean saveBuyer = saveBuyerCb.isSelected();
    final String buyerName = buyerNameField.getText();

    // 3. Show instant optimistic feedback
    Toast.show(app.getRootPane(), "Saving...", "Writing bill " + snapshot.getBillNo(), false);

    // 4. Offload DB writes to background thread
    app.getDbExecutor().execute(() -> {
        app.getData().saveBill(snapshot);
        if (editingBill == null) {
            currentSettings.setBillNoNext(currentSettings.getBillNoNext() + 1);
            app.getData().saveSettings(currentSettings);
        }
        if (saveBuyer && !buyerName.isBlank()) {
            // save buyer...
        }
        app.getData().invalidateBills();

        // 5. Return to FX thread for print/PDF/toast
        Platform.runLater(() -> {
            Toast.show(app.getRootPane(), "Saved", snapshot.getBillNo() + " saved successfully.", false);
            if (printAfter) executePrint(snapshot);
            if (pdfAfter) executePdfExport(snapshot);
        });
    });
}
```

---

## 3. Pillar 2: Bill Template Designer & Rendering Engine

### 3.1 Identified Bottlenecks

1. **Grid Guidelines Generation (`TemplateDesigner.java:L990-1005`)**:
   - `refreshCanvas()` clears `gridPane` and loops creating individual `javafx.scene.shape.Line` objects every 10mm. On an A4 canvas, this creates **50+ individual Shape nodes** in the scene graph.
   - JavaFX must calculate bounds, mouse pick paths, and layout passes for all 50 lines.

2. **Full Node Destruction on Drag / Resize (`TemplateDesigner.java:L1330`)**:
   - Moving or resizing an element in `updateElementVisualInPlace()` repeatedly calls `renderVisualElement()`, regenerating fresh node hierarchies and re-parsing typography tracking on every mouse move.

3. **Inspector Panel Full Reconstruction (`TemplateDesigner.java:L2034`)**:
   - `updatePropertiesPanel()` completely reconstructs dozens of `VBox`, `GridPane`, color pickers, sliders, and text fields every time an element is clicked or dragged.

4. **Redundant Color Parsing & String CSS Lookups**:
   - Repeated calls to `parseColorSafe()` and inline `-fx-background-color` string assignments cause the JavaFX CSS parser to run repeatedly.

---

### 3.2 Non-Breaking Optimizations

| Bottleneck | Optimization Strategy | Behavioral Guarantee |
| :--- | :--- | :--- |
| **Grid Lines (50+ Shape Nodes)** | **Canvas / CSS Pattern Background**: Render the 10mm grid onto a single static `Canvas` or use an image pattern fill instead of 50 `Line` scene graph nodes. | Visually identical 10mm gray grid `#ececec`; zero node overhead in layout passes. |
| **Element Drag/Move** | **Geometry-Only Transform during Drag**: While dragging, update only `wrapper.setLayoutX()` and `wrapper.setLayoutY()`. Do NOT re-render the inner visual until `onMouseReleased`. | 60 FPS smooth dragging; visual elements remain crisp without re-instantiation. |
| **Selection Overlay** | **Lightweight Reusable Handles**: Keep 8 handle rectangles allocated permanently and reposition them, rather than re-creating handle nodes. | Identical gold handles (`#D9A13B`) with exact same resize cursors and behaviors. |
| **Hardware Raster Caching** | **Static Element Caching**: Apply `setCache(true)` and `setCacheHint(CacheHint.SPEED)` to static complex shapes, SVGs, and multi-line text. | Pixel-identical appearance; GPU handles rendering during zoom and panning. |

---

### 3.3 Implementation Details & Code Blueprints

#### A. Single-Canvas Grid Replacement
```java
// INSTEAD of adding 50 Line nodes to gridPane:
private void renderGridFast(Canvas gridCanvas, double pageW, double pageH) {
    GraphicsContext gc = gridCanvas.getGraphicsContext2D();
    gc.clearRect(0, 0, pageW, pageH);
    if (!showGrid) return;

    gc.setStroke(Color.web("#ececec"));
    gc.setLineWidth(0.5);

    double step = 10 * MM_PX;
    for (double x = step; x < pageW; x += step) {
        gc.strokeLine(Math.round(x) + 0.5, 0, Math.round(x) + 0.5, pageH);
    }
    for (double y = step; y < pageH; y += step) {
        gc.strokeLine(0, Math.round(y) + 0.5, pageW, Math.round(y) + 0.5);
    }
}
```

#### B. High-Performance Drag Loop
```java
// During drag: update coordinates ONLY (0 layout thrashing)
wrapper.setOnMouseDragged(e -> {
    if (el.isLocked()) return;
    double newX = moveStart[2] + (e.getScreenX() - moveStart[0]) / (MM_PX * zoom);
    double newY = moveStart[3] + (e.getScreenY() - moveStart[1]) / (MM_PX * zoom);
    
    el.setX(newX);
    el.setY(newY);

    // Fast-path: update layout position directly
    wrapper.setLayoutX(newX * MM_PX);
    wrapper.setLayoutY(newY * MM_PX);
    updateSelectionOverlayFast(); // Reposition existing handles
});

// Full visual sync only on release:
wrapper.setOnMouseReleased(e -> {
    if (isMoved[0]) {
        saveState("Move Element");
        syncGeoSpinnersIfPresent();
    }
});
```

---

## 4. Cross-Cutting Performance Strategies

### 4.1 Global QR & Barcode LRU Cache
`BarcodeService.java` can store generated `javafx.scene.image.Image` objects in a bounded LRU cache (e.g. 100 entries).

```java
public class BarcodeCache {
    private static final int MAX_ENTRIES = 120;
    private static final Map<String, Image> CACHE = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public static synchronized Image getQr(String payload, int size) {
        String key = "QR:" + size + ":" + payload;
        return CACHE.computeIfAbsent(key, k -> BarcodeService.generateQrFxImage(payload, size));
    }
}
```

### 4.2 Base64 Image Decoding Cache
Logos and custom images embedded in templates as Base64 strings should be decoded once and held in a soft/LRU cache keyed by the Base64 MD5/hash, preventing repeated string allocations and memory copies.

### 4.3 JVM & Packaging Tuning Flags
In `.github/workflows/windows-installer.yml` and `packaging/build-windows-installer.ps1`, pass optimal runtime options to `jpackage`:

```powershell
--java-options "-XX:+UseG1GC" `
--java-options "-XX:MaxGCPauseMillis=20" `
--java-options "-XX:+UseStringDeduplication" `
--java-options "-Dprism.order=d3d,sw" `
--java-options "-Dprism.vsync=true"
```
* `-XX:+UseG1GC -XX:MaxGCPauseMillis=20`: Eliminates noticeable GC micro-stutters during UI interactions.
* `-XX:+UseStringDeduplication`: Drastically reduces heap usage from repeated Base64 template strings, JSON blobs, and table labels.
* `-Dprism.order=d3d,sw`: Forces Direct3D hardware acceleration on Windows, falling back to software rasterizer only if GPU drivers are unavailable.

---

## 5. Verification & Performance Audit Checklist

To guarantee zero behavioral regression while verifying speedup:

1. **Visual Fidelity Verification**:
   - [ ] Render all 5 default templates (`buildClassic`, `buildModern`, `buildThermalRoll`, etc.). Verify that millimeters-to-pixels alignment (`MM_PX = 3.7795275591`) remains identical down to 0.1mm.
   - [ ] Print test invoices to PDF and verify barcodes/QR codes scan accurately.
2. **Calculation Verification**:
   - [ ] Check tax calculations for intra-state (CGST + SGST) vs inter-state (IGST).
   - [ ] Confirm round-off rules and "Amount in Words" string generation match exactly.
3. **Responsiveness Audit**:
   - [ ] Rapid typing in line item descriptions and quantities remains at a solid 60 FPS without cursor lag.
   - [ ] Dragging complex multi-element groups in `TemplateDesigner` is smooth without rubber-banding.
   - [ ] Saving a bill displays immediate visual feedback with no window freeze.
