package com.invoicestudio.ui;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.model.PageConfig;
import com.invoicestudio.model.PageSizeName;
import com.invoicestudio.model.Template;
import com.invoicestudio.service.PrintOptions;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.print.Paper;
import javafx.print.PageOrientation;
import javafx.print.Printer;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Modern, app-styled print window (Chrome-like): live bill preview on the
 * left, print options on the right.
 * <p>
 * Replaces the native Windows print dialog. The returned {@link PrintOptions}
 * fully determine the final page layout, so the printer driver's default
 * paper form can never shrink an A4 bill down to a small custom form.
 */
public class PrintPreviewDialog {

    /** Builds a fresh preview node for the given (possibly size-overridden) template. */
    public interface PreviewFactory {
        Node createPreview(Template effectiveTemplate);
    }

    private static final double MM_PX = 3.7795275591;

    private final Stage stage = new Stage();
    private final PreviewFactory previewFactory;
    private final Template baseTemplate;      // null for non-template nodes (calibration sheet)
    private final String jobName;

    private ComboBox<PrinterItem> printerCombo;
    private ComboBox<PaperItem> paperCombo;
    private Spinner<Integer> copiesSpinner;
    private ToggleButton portraitBtn;
    private ToggleButton landscapeBtn;
    private Label summaryLabel;
    private ScrollPane previewScroll;
    private Node currentPreview;

    private PrintOptions result;

    public PrintPreviewDialog(Window owner, Template template, PreviewFactory previewFactory,
                              int defaultCopies, String jobName, String title) {
        this.baseTemplate = template;
        this.previewFactory = previewFactory;
        this.jobName = jobName != null ? jobName : "InvoiceStudio Print";

        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        DialogHelper.applyAppIcon(stage); // logo in title bar from the very first frame
        stage.setTitle(title != null ? title : "Print");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("print-dialog");
        root.setCenter(buildPreviewArea());
        root.setRight(buildOptionsPanel(defaultCopies));

        Scene scene = new Scene(root, 1060, 700);
        DialogHelper.styleScene(scene);
        stage.setScene(scene);
        stage.setMinWidth(860);
        stage.setMinHeight(560);

        // Initial preview render once layout passes are known
        Platform.runLater(this::refreshPreview);
    }

    /* ------------------------------------------------------------------ */
    /*  Left: live preview                                                 */
    /* ------------------------------------------------------------------ */

    private Node buildPreviewArea() {
        previewScroll = new ScrollPane();
        previewScroll.getStyleClass().add("print-preview-stage");
        previewScroll.setFitToWidth(true);
        previewScroll.setFitToHeight(false);
        previewScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        previewScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        previewScroll.setPadding(new Insets(0));

        // Keep the page fitted to the visible width whenever the pane resizes
        previewScroll.viewportBoundsProperty().addListener((obs, o, n) -> applyFitZoom());

        return previewScroll;
    }

    private void refreshPreview() {
        Template effective = buildEffectiveTemplate();
        try {
            currentPreview = previewFactory.createPreview(effective);
        } catch (Exception ex) {
            Label err = new Label("Preview unavailable: " + ex.getMessage());
            err.getStyleClass().add("print-preview-error");
            currentPreview = err;
        }
        previewScroll.setContent(currentPreview);
        applyFitZoom();
        updateSummary();
    }

    /** Template clone with the chosen paper size / orientation applied (for the preview only). */
    private Template buildEffectiveTemplate() {
        Template base = baseTemplate;
        if (base == null) return null;

        PageConfig page = base.getPage() != null ? base.getPage() : new PageConfig();
        PageConfig.Margins m = page.getMargin();
        PageConfig.Margins marginCopy = new PageConfig.Margins(m.getTop(), m.getRight(), m.getBottom(), m.getLeft());
        PageConfig eff = new PageConfig(
                page.getSizeName(), page.getWidth(), page.getHeight(),
                page.getOrientation(), marginCopy);
        eff.setAutoHeight(page.isAutoHeight());

        PaperItem chosen = paperCombo != null && paperCombo.getValue() != null ? paperCombo.getValue() : null;
        PageOrientation orient = chosenOrientation();

        if (chosen != null && !page.isAutoHeight()) {
            double wMm = chosen.paper.getWidth() * 25.4 / 72.0;
            double hMm = chosen.paper.getHeight() * 25.4 / 72.0;
            // Standard sizes are unambiguous; custom sizes keep their authored dims
            boolean standard = isStandard(page.getSizeName());
            if (orient == PageOrientation.LANDSCAPE) {
                eff.setSizeName(rotateSizeName(page.getSizeName()));
                eff.setWidth(Math.max(wMm, hMm));
                eff.setHeight(Math.min(wMm, hMm));
            } else {
                if (standard) {
                    eff.setWidth(Math.min(wMm, hMm));
                    eff.setHeight(Math.max(wMm, hMm));
                } else {
                    eff.setWidth(wMm);
                    eff.setHeight(hMm);
                }
            }
        }

        Template copy = new Template(base.getId(), base.getName(), eff, base.getElements());
        copy.setPrintOffsetX(base.getPrintOffsetX());
        copy.setPrintOffsetY(base.getPrintOffsetY());
        return copy;
    }

    private static boolean isStandard(PageSizeName sizeName) {
        return sizeName == PageSizeName.A4 || sizeName == PageSizeName.A5
                || sizeName == PageSizeName.LETTER || sizeName == PageSizeName.LEGAL;
    }

    private static PageSizeName rotateSizeName(PageSizeName n) {
        return switch (n) {
            case A4 -> PageSizeName.A4;
            case A5 -> PageSizeName.A5;
            case LETTER -> PageSizeName.LETTER;
            case LEGAL -> PageSizeName.LEGAL;
            default -> n;
        };
    }

    private void applyFitZoom() {
        if (!(currentPreview instanceof com.invoicestudio.ui.BillPreviewPane pane)) return;
        Template eff = buildEffectiveTemplate();
        double widthMm = eff != null && eff.getPage() != null ? eff.getPage().getWidth() : 210.0;
        double avail = previewScroll.getViewportBounds().getWidth() - 48;
        double pagePx = widthMm * MM_PX;
        if (avail > 40 && pagePx > 0) {
            double z = Math.max(0.2, Math.min(2.0, avail / pagePx));
            pane.setZoom(z);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Right: options panel                                               */
    /* ------------------------------------------------------------------ */

    private Node buildOptionsPanel(int defaultCopies) {
        Label heading = new Label("Print");
        heading.getStyleClass().add("print-heading");

        // --- Printer ---
        Label printerLbl = new Label("Printer");
        printerLbl.getStyleClass().add("field-label");
        printerCombo = new ComboBox<>();
        printerCombo.setMaxWidth(Double.MAX_VALUE);
        printerCombo.setConverter(new StringConverter<>() {
            @Override public String toString(PrinterItem item) { return item == null ? "" : item.displayName; }
            @Override public PrinterItem fromString(String s) { return null; }
        });
        List<PrinterItem> printers = collectPrinters();
        printerCombo.getItems().setAll(printers);
        Printer defaultPrinter = Printer.getDefaultPrinter();
        printerCombo.setValue(printerItemFor(defaultPrinter, printers));
        printerCombo.valueProperty().addListener((obs, o, n) -> onPrinterChanged());
        printerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(PrinterItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName);
                if (item != null && item.isDefault) getStyleClass().add("default-printer-cell");
                else getStyleClass().remove("default-printer-cell");
            }
        });

        Label printerInfo = new Label();
        printerInfo.getStyleClass().add("print-muted");
        printerInfo.setWrapText(true);
        Runnable updPrinterInfo = () -> {
            PrinterItem sel = printerCombo.getValue();
            printerInfo.setText(sel == null ? "No printer found — install a printer first"
                    : (sel.isDefault ? "Default printer" : "Available printer"));
        };
        updPrinterInfo.run();
        printerCombo.valueProperty().addListener((obs, o, n) -> updPrinterInfo.run());

        // --- Paper size ---
        Label paperLbl = new Label("Paper size");
        paperLbl.getStyleClass().add("field-label");
        paperCombo = new ComboBox<>();
        paperCombo.setMaxWidth(Double.MAX_VALUE);
        paperCombo.setConverter(new StringConverter<>() {
            @Override public String toString(PaperItem item) { return item == null ? "" : item.label; }
            @Override public PaperItem fromString(String s) { return null; }
        });
        loadPapersForPrinter(printerCombo.getValue());
        paperCombo.valueProperty().addListener((obs, o, n) -> refreshPreview());

        // --- Copies ---
        Label copiesLbl = new Label("Copies");
        copiesLbl.getStyleClass().add("field-label");
        copiesSpinner = new Spinner<>(1, 99, Math.max(1, defaultCopies));
        copiesSpinner.setEditable(true);
        copiesSpinner.setMaxWidth(Double.MAX_VALUE);
        copiesSpinner.valueProperty().addListener((obs, o, n) -> updateSummary());

        // --- Orientation ---
        Label orientLbl = new Label("Orientation");
        orientLbl.getStyleClass().add("field-label");
        ToggleGroup orientGroup = new ToggleGroup();
        portraitBtn = new ToggleButton("Portrait");
        landscapeBtn = new ToggleButton("Landscape");
        portraitBtn.setToggleGroup(orientGroup);
        landscapeBtn.setToggleGroup(orientGroup);
        portraitBtn.getStyleClass().addAll("print-seg-btn");
        landscapeBtn.getStyleClass().addAll("print-seg-btn");

        boolean landscape = baseTemplate != null && baseTemplate.getPage() != null
                && "landscape".equalsIgnoreCase(baseTemplate.getPage().getOrientation());
        portraitBtn.setSelected(!landscape);
        landscapeBtn.setSelected(landscape);
        portraitBtn.setOnAction(e -> refreshPreview());
        landscapeBtn.setOnAction(e -> refreshPreview());

        HBox seg = new HBox(portraitBtn, landscapeBtn);
        seg.getStyleClass().add("print-segmented");

        Region summarySpacer = new Region();
        summaryLabel = new Label();
        summaryLabel.getStyleClass().add("print-summary");
        summaryLabel.setWrapText(true);

        // --- Buttons ---
        Button printBtn = UiTheme.goldBtn("Print");
        printBtn.setMaxWidth(Double.MAX_VALUE);
        printBtn.setDefaultButton(true);
        printBtn.setOnAction(e -> onPrint());

        Button cancelBtn = UiTheme.secondaryBtn("Cancel");
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> { result = null; stage.close(); });

        VBox panel = new VBox(10,
                heading,
                new Separator(Orientation.HORIZONTAL),
                printerLbl, printerCombo, printerInfo,
                paperLbl, paperCombo,
                copiesLbl, copiesSpinner,
                orientLbl, seg,
                summarySpacer,
                summaryLabel,
                new Separator(Orientation.HORIZONTAL),
                printBtn, cancelBtn);
        panel.getStyleClass().add("print-options-panel");
        panel.setPadding(new Insets(18));
        panel.setSpacing(8);
        panel.setPrefWidth(310);
        panel.setMinWidth(290);

        VBox.setVgrow(summarySpacer, Priority.ALWAYS);

        updateSummary();
        return panel;
    }

    /* ------------------------------------------------------------------ */
    /*  Data plumbing                                                      */
    /* ------------------------------------------------------------------ */

    private record PrinterItem(Printer printer, String displayName, boolean isDefault) {}

    private record PaperItem(Paper paper, String label) {}

    private static List<PrinterItem> collectPrinters() {
        List<PrinterItem> list = new ArrayList<>();
        Printer def = Printer.getDefaultPrinter();
        for (Printer p : Printer.getAllPrinters()) {
            if (p == null) continue;
            boolean isDef = p.equals(def);
            String name = p.getName() != null ? p.getName() : "Printer";
            list.add(new PrinterItem(p, isDef ? name + "  ✓" : name, isDef));
        }
        list.sort((a, b) -> Boolean.compare(b.isDefault, a.isDefault));
        return list;
    }

    private static PrinterItem printerItemFor(Printer p, List<PrinterItem> printers) {
        if (p == null) return printers.isEmpty() ? null : printers.get(0);
        for (PrinterItem it : printers) if (p.equals(it.printer)) return it;
        return printers.isEmpty() ? null : printers.get(0);
    }

    private void loadPapersForPrinter(PrinterItem item) {
        Map<String, PaperItem> byDims = new LinkedHashMap<>();
        if (item != null && item.printer != null) {
            try {
                for (Paper p : item.printer.getPrinterAttributes().getSupportedPapers()) {
                    if (p == null) continue;
                    double wMm = p.getWidth() * 25.4 / 72.0;
                    double hMm = p.getHeight() * 25.4 / 72.0;
                    // Treat the same sheet offered portrait/landscape as one entry
                    double short_ = Math.min(wMm, hMm), long_ = Math.max(wMm, hMm);
                    String key = String.format(java.util.Locale.US, "%.0fx%.0f", short_, long_);
                    byDims.putIfAbsent(key, new PaperItem(p, prettyPaperName(p, wMm, hMm)));
                }
            } catch (Exception ignored) {
            AppLog.debug(ignored); /* keep whatever we have */ }
        }

        // Always offer the template's own paper (resolved on this printer) and select it
        Paper target = PrintingServiceStatic.paperForTemplate(item != null ? item.printer : null, baseTemplate);
        String key = keyFor(target);
        byDims.put(key, new PaperItem(target, prettyPaperName(target,
                target.getWidth() * 25.4 / 72.0, target.getHeight() * 25.4 / 72.0)));

        paperCombo.getItems().setAll(new ArrayList<>(byDims.values()));
        PaperItem sel = null;
        for (PaperItem it : paperCombo.getItems()) {
            if (it.paper.getName() != null && it.paper.getName().equals(target.getName())) { sel = it; break; }
        }
        if (sel == null) sel = paperCombo.getItems().isEmpty() ? null : paperCombo.getItems().get(0);
        paperCombo.setValue(sel);
    }

    private static String keyFor(Paper p) {
        if (p == null) return "?";
        double wMm = p.getWidth() * 25.4 / 72.0;
        double hMm = p.getHeight() * 25.4 / 72.0;
        double short_ = Math.min(wMm, hMm), long_ = Math.max(wMm, hMm);
        return String.format(java.util.Locale.US, "%.0fx%.0f", short_, long_);
    }

    private static String prettyPaperName(Paper p, double wMm, double hMm) {
        String raw = p.getName() != null ? p.getName() : "";
        String name = raw.replace('-', ' ').replace('_', ' ').trim();
        if (name.length() <= 3) name = name.toUpperCase(java.util.Locale.US); // "a4" -> "A4"
        if (name.isEmpty()) name = "Custom";
        return String.format(java.util.Locale.US, "%s  ·  %.0f × %.0f mm", name, wMm, hMm);
    }

    private void onPrinterChanged() {
        loadPapersForPrinter(printerCombo.getValue());
        refreshPreview();
    }

    private PageOrientation chosenOrientation() {
        return landscapeBtn != null && landscapeBtn.isSelected()
                ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT;
    }

    private void updateSummary() {
        if (summaryLabel == null) return;
        PaperItem paper = paperCombo != null ? paperCombo.getValue() : null;
        int copies = copiesSpinner != null ? copiesSpinner.getValue() : 1;
        if (paper == null) {
            summaryLabel.setText("No paper selected");
            return;
        }
        String orient = chosenOrientation() == PageOrientation.LANDSCAPE ? "Landscape" : "Portrait";
        summaryLabel.setText(String.format("%s · %s · %d cop%s at 100%% size",
                orient, PrintOptions.paperSizeText(paper.paper), copies, copies == 1 ? "y" : "ies"));
    }

    private void onPrint() {
        PrinterItem sel = printerCombo.getValue();
        PaperItem paper = paperCombo.getValue();
        Printer printer = sel != null ? sel.printer : Printer.getDefaultPrinter();
        if (printer == null) {
            new Alert(Alert.AlertType.WARNING, "No printer is installed on this system.",
                    ButtonType.OK).showAndWait();
            return;
        }
        Paper p = paper != null ? paper.paper : Paper.A4;
        result = new PrintOptions(printer, p, chosenOrientation(), copiesSpinner.getValue(), jobName);
        stage.close();
    }

    /* ------------------------------------------------------------------ */
    /*  API                                                                */
    /* ------------------------------------------------------------------ */

    public Optional<PrintOptions> showAndWait() {
        result = null;
        stage.showAndWait();
        return Optional.ofNullable(result);
    }

    /** Small indirection so the dialog can resolve the template's paper without exposing PrintingService internals. */
    private static final class PrintingServiceStatic {
        static Paper paperForTemplate(Printer printer, Template template) {
            if (template != null && template.getPage() != null) {
                return com.invoicestudio.service.PrintingService.resolvePaper(
                        printer,
                        template.getPage().getSizeName(),
                        template.getPage().getWidth(),
                        template.getPage().getHeight());
            }
            return Paper.A4;
        }
    }
}
