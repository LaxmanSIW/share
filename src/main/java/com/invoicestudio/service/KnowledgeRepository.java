package com.invoicestudio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.invoicestudio.AppDirs;
import com.invoicestudio.model.KnowledgeArticle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Repository for managing Knowledge Hub documentation articles.
 * Articles are persisted as JSON in the user's application data directory:
 * {@code %APPDATA%\InvoiceStudio\knowledge-hub.json}.
 *
 * <p>All articles are organized under hierarchical slash-separated paths
 * (e.g. "TSC / TA210 — Printer", "Invoicing / GST Rules"). On initial run,
 * seeds the complete reference library for the TSC label printing pipeline.</p>
 */
public final class KnowledgeRepository {

    private static final String FILE_NAME = "knowledge-hub.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static KnowledgeRepository instance;

    private final Path storagePath;
    private final List<KnowledgeArticle> articles = new CopyOnWriteArrayList<>();

    public static synchronized KnowledgeRepository getInstance() {
        if (instance == null) {
            instance = new KnowledgeRepository(AppDirs.dataDir().resolve(FILE_NAME));
        }
        return instance;
    }

    public static KnowledgeRepository createCustom(Path path) {
        return new KnowledgeRepository(path);
    }

    KnowledgeRepository(Path storagePath) {
        this.storagePath = storagePath;
        load();
    }

    public synchronized void load() {
        articles.clear();
        if (Files.exists(storagePath)) {
            try {
                List<KnowledgeArticle> loaded = MAPPER.readValue(
                        storagePath.toFile(),
                        new TypeReference<List<KnowledgeArticle>>() {});
                if (loaded != null && !loaded.isEmpty()) {
                    articles.addAll(loaded);
                    return;
                }
            } catch (Exception ex) {
                AppLog.error(ex);
            }
        }
        // Seed default documentation
        articles.addAll(createDefaultArticles());
        saveToFile();
    }

    public synchronized void saveArticle(KnowledgeArticle article) {
        if (article == null) return;
        int idx = -1;
        for (int i = 0; i < articles.size(); i++) {
            if (articles.get(i).id().equals(article.id())) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            articles.set(idx, article);
        } else {
            articles.add(article);
        }
        saveToFile();
    }

    public synchronized boolean deleteArticle(String id) {
        if (id == null) return false;
        boolean removed = articles.removeIf(a -> a.id().equals(id));
        if (removed) {
            saveToFile();
        }
        return removed;
    }

    public synchronized void resetToDefaults() {
        articles.clear();
        articles.addAll(createDefaultArticles());
        saveToFile();
    }

    public List<KnowledgeArticle> getAllArticles() {
        return Collections.unmodifiableList(new ArrayList<>(articles));
    }

    public Optional<KnowledgeArticle> getArticleById(String id) {
        return articles.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    /**
     * Returns a sorted list of all unique category paths currently in use.
     */
    public List<String> getAllCategoryPaths() {
        Set<String> paths = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (KnowledgeArticle a : articles) {
            if (a.path() != null && !a.path().isBlank()) {
                paths.add(a.path().trim());
            }
        }
        if (paths.isEmpty()) {
            paths.add("General");
        }
        return new ArrayList<>(paths);
    }

    private void saveToFile() {
        try {
            Path parent = storagePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(storagePath.toFile(), articles);
        } catch (IOException e) {
            AppLog.error(e);
        }
    }

    // ── Tree Construction ─────────────────────────────────────────────

    public static final class CategoryNode {
        private final String name;
        private final String fullPath;
        private final List<CategoryNode> subCategories = new ArrayList<>();
        private final List<KnowledgeArticle> articles = new ArrayList<>();
        private boolean expanded;

        public CategoryNode(String name, String fullPath, boolean expanded) {
            this.name = name;
            this.fullPath = fullPath;
            this.expanded = expanded;
        }

        public String name() { return name; }
        public String fullPath() { return fullPath; }
        public List<CategoryNode> subCategories() { return subCategories; }
        public List<KnowledgeArticle> articles() { return articles; }
        public boolean isExpanded() { return expanded; }
        public void setExpanded(boolean exp) { this.expanded = exp; }

        public int totalArticles() {
            int count = articles.size();
            for (CategoryNode sub : subCategories) {
                count += sub.totalArticles();
            }
            return count;
        }

        public boolean matches(String query) {
            if (query == null || query.isBlank()) return true;
            String q = query.trim().toLowerCase(Locale.ROOT);
            if (name.toLowerCase(Locale.ROOT).contains(q)) return true;
            for (KnowledgeArticle a : articles) {
                if (a.title().toLowerCase(Locale.ROOT).contains(q)) return true;
                if (a.subtitle().toLowerCase(Locale.ROOT).contains(q)) return true;
                if (a.markdown().toLowerCase(Locale.ROOT).contains(q)) return true;
            }
            for (CategoryNode sub : subCategories) {
                if (sub.matches(q)) return true;
            }
            return false;
        }
    }

    /**
     * Builds a hierarchical tree of categories and articles from the current article collection.
     */
    public CategoryNode buildCategoryTree() {
        CategoryNode rootNode = new CategoryNode("ROOT", "", true);

        for (KnowledgeArticle article : articles) {
            String path = article.path();
            if (path == null || path.isBlank()) path = "General";

            String[] parts = path.split("\\s*/\\s*");
            CategoryNode current = rootNode;
            StringBuilder currentPath = new StringBuilder();

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) continue;

                if (currentPath.length() > 0) currentPath.append(" / ");
                currentPath.append(part);

                String pStr = currentPath.toString();
                CategoryNode next = null;
                for (CategoryNode existing : current.subCategories) {
                    if (existing.name.equalsIgnoreCase(part)) {
                        next = existing;
                        break;
                    }
                }
                if (next == null) {
                    // Top level ("TSC") expanded by default; deeper levels collapsed
                    boolean defaultExpanded = (i == 0);
                    next = new CategoryNode(part, pStr, defaultExpanded);
                    current.subCategories.add(next);
                }
                current = next;
            }
            current.articles.add(article);
        }

        return rootNode;
    }

    // ── Default Knowledge Seed ────────────────────────────────────────

    private List<KnowledgeArticle> createDefaultArticles() {
        List<KnowledgeArticle> list = new ArrayList<>();
        long now = System.currentTimeMillis();

        list.add(new KnowledgeArticle(
                "art_tsc_overview",
                "TSC / TA210 — Printer",
                "TSC TA210 — Printer Overview",
                "The desktop label printer this app targets: specs, sensors and what makes it different from an A4 printer.",
                """
                The **TSC TA210** is a 4-inch desktop thermal label printer (the TA310 is its 300-dpi sibling). Unlike an office printer, it prints one die-cut label at a time from a roll, burning an image directly with a thermal print head. It supports both direct thermal media (heat-sensitive paper) and thermal transfer (ribbon + media).

                ### Key Specifications (Official TSC Datasheet)
                - **Resolution**: 203 DPI = 8 dots per mm (TA300/TA310 are 300 DPI / 12 dots/mm siblings).
                - **Maximum Print Width**: 4.25 in / 108 mm — the physical width of the print head (300-dpi TA310: 104 mm / 4.09 in).
                - **Maximum Print Speed**: 5 ips (127 mm/s); maximum print length 90 in (2286 mm).
                - **Media Sensors**: Transmissive gap sensor (die-cut labels) and reflective black mark sensor (continuous stock with black marks).
                - **Command Languages**: TSPL / TSPL2 natively (also supports EPL/ZPL emulation on some firmware).

                > [!NOTE]
                > Because the head is 108 mm wide, any liner up to that width prints full-bleed — a 77 mm two-up liner is perfectly in range. The app warns only when the label stock paper width exceeds 108 mm.

                ---
                ### Why this matters in the app
                Every label dimension configured in the **Label Stock dialog** is converted from millimeters to dots at **8 dots/mm**, and the print head receives a 1-bit bitmap at exactly that density.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tsc_sensors",
                "TSC / TA210 — Printer",
                "Label Stock, Sensors & Orientation — Who Knows What",
                "What the printer figures out by itself, what the software must declare, and how BarTender's Page Setup maps to the Label Stock dialog.",
                """
                A thermal printer is smart about the **FEED direction only**. Its gap sensor watches one vertical line of the web and sees the die-cut gaps (or black marks) scroll past. Everything **ACROSS** the strip — how many labels sit side by side, the liner width, and side margins — is invisible to that sensor and must be declared by the software.

                ### What the Printer Learns by Itself — Sensor Calibration
                - **Calibration** feeds a few labels through the sensor and **measures the roll's real pitch** (label length + gap), then stores sensor thresholds.
                - TSPL exposes it as `GAPDETECT` and `AUTODETECT` (manual p.6: *"feeds the paper through the sensor to determine the paper and gap sizes"*).
                - The app's one-click equivalent: **Bulk Print dialog → Calibrate Sensor** — it spools `AUTODETECT` as its own RAW job. Always recalibrate after changing rolls.
                - **Crucial Rule**: That is the full extent of the printer's own knowledge: *where each label ends along the feed*. It cannot name the label size, count columns across, or arrange content — layout is always the software's responsibility.

                ### What the Software Must Declare — Our Script or the Driver
                Printing through the Windows driver, the driver's Page Setup declares the stock and BarTender reads it. InvoiceStudio skips the GDI layer and spools raw TSPL, so **our script is the declaration**:
                1. `SIZE` (liner width × row height in dots)
                2. `GAP` (feed gap between labels)
                3. `DIRECTION` (feed orientation)
                4. `BITMAP` (1-bit raster data)
                5. `PRINT` (quantities to feed)

                ### BarTender Page Setup ↔ Label Stock Dialog Mapping
                | BarTender Setting | InvoiceStudio Label Stock | Notes |
                | :--- | :--- | :--- |
                | Paper Size 77.0 × 38.0 mm | Liner width 77 mm | Height (38) = label height (36) + gap (2) |
                | Label Size 75.0 × 36.0 mm | Label Width & Height | Physical label dimensions on roll |
                | Columns: 2 across | Columns across: 2 | Feeds one row per print |
                | Margins 1.0 mm | Left / Right margin | Edge padding |
                | Orientation Portrait/Landscape | Artwork direction | Rotates content only; paper size stays constant |

                ### The Simple Way to Think About It
                Measure the physical roll with a ruler: label width across, label height along the feed, the gap between rows, and how many labels across. Enter those numbers into Label Stock. The diagram mirrors the roll.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tsc_sizes",
                "TSC / TA210 — Printer",
                "Label Sizes & Media Specs — TA210 Envelope",
                "Verified media envelope and the 17 built-in presets the Label Stock dialog offers.",
                """
                Every preset in the Label Stock dialog falls inside the TA210's official media envelope, validated before the fields are filled:

                ### Verified Media Specs (Official Datasheet)
                - **Media Width**: 25.4 – 118 mm (die-cut liner).
                - **Label Length**: 10 – 2794 mm (feed direction).
                - **Max Print Width**: 108 mm (the 203-dpi print head — media wider than this will not fully print).
                - **Resolution**: 203 DPI = 8 dots/mm.
                - **Typical Die-Cut Gap**: 2 mm or more.
                - **Media Core Diameter**: 25.4 – 38 mm.

                ### The Built-in Presets
                The preset selector offers 17 standard industry sizes, largest height first — from the full-width 108 × 2794 continuous roll down to the 25.4 × 10 mm jewelry tag minimum. Each preset carries suggested L/R margin, row gap, and column gap values:
                `[W × H] | L/R: x mm | Row Gap: y mm | Col Gap: z mm`

                > [!TIP]
                > Presets populate the dialog fields; they never lock them. Hand-typed dimensions are validated against the envelope above. The dialog explains exactly which constraint a custom size violates.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tsc_brightness",
                "TSC / Print Settings",
                "Brightness Threshold — Sharp Black & White",
                "The Settings → Print slider that decides which pixels burn black and which stay white.",
                """
                A thermal print head has no gray levels: every dot either **burns black** or **stays white**. The **Brightness Threshold** (Settings → Print) decides where that cut happens on the downsampled image.

                - Every dot's 8-bit gray value (`0 = black` to `255 = white`) is compared against the threshold.
                - `gray ≤ threshold` $\\rightarrow$ burns sharp **black**.
                - `gray > threshold` $\\rightarrow$ stays sharp **white**.
                - **Default 150**: Slightly above mid-gray so hairlines, fine barcodes, and small text survive clearly.
                - **Higher threshold (e.g. 180)**: More pixels count as black = bolder, heavier print. Best for faint thermal media or thin fonts.
                - **Lower threshold (e.g. 110)**: Only true darks burn = crisper barcode edges on high-quality synthetic media, reducing thermal ribbon wear.

                > [!NOTE]
                > The output is always exactly two colors — black and white. There is no dithering or halftone gray: that is what ensures 1-D barcodes (Code 128, EAN-13) remain 100% scan-reliable.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tsc_knobs",
                "TSC / Print Settings",
                "Advanced Settings & Knobs",
                "System properties for support sessions — plus how routing picks the native pipeline.",
                """
                InvoiceStudio provides technical override knobs via JVM system properties for diagnostics and custom hardware configurations.

                ### Print Engine Routing
                - **Auto (default)**: Printers whose name contains `TSC`, `TA200`, `TA210`, `TA300`, or `TA310` automatically use the native TSPL pipeline. All other printers use the standard JavaFX print driver path.
                - `-Dinvoicestudio.print.engine=tspl`: Force the native pipeline for every printer.
                - `-Dinvoicestudio.print.engine=driver`: Disable the native pipeline (classic driver printing).

                ### TSPL Tuning Properties
                - `-Dinvoicestudio.tspl.direction=0|1`: Invert print feed orientation (default `1`).
                - `-Dinvoicestudio.tspl.dotsPerMm=N`: Override dot density (default `8` for 203 DPI, `12` for 300 DPI).
                - `-Dinvoicestudio.tspl.threshold=0..255`: Override the GUI Brightness Threshold from Settings.
                - `-Dinvoicestudio.tspl.dump=/path/job.tspl`: Write the exact RAW bytes of every spooled job to a file (full TSPL script: `SIZE/GAP/DIRECTION + CLS/BITMAP/PRINT`). Useful for auditing against the TSC manual.

                ### RAW Spooling Details
                Jobs are sent through the JDK print service as `BYTE_ARRAY AUTOSENSE`. On Windows, this spools with datatype `RAW`, which the TSC driver passes untouched to the USB port.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tspl_language",
                "TSC / TSPL Language",
                "TSPL / TSPL2 — The Command Language",
                "The text-based printer language we send to the TA210: commands, units and line endings.",
                """
                **TSPL** (TSC Printer Language) and its extended version **TSPL2** are the native command languages of TSC thermal printers. A print job is a text script of commands, each on its own line terminated by `CRLF`, with raster commands like `BITMAP` followed by binary dot data.

                ### Core Protocol Rules
                - Text commands are ASCII, case-insensitive, and terminated with CRLF (`\\r\\n`).
                - The default unit is the **dot** — at 203 DPI, $1\\text{ mm} = 8\\text{ dots}$. The app declares all dimensions in dots to eliminate rounding drift.
                - The script controls printer state: stock dimensions (`SIZE`), sensor type (`GAP`/`BLINE`), print orientation (`DIRECTION`), image buffer clearing (`CLS`), and quantity (`PRINT`).

                ### Why Native TSPL Beats the Windows Driver
                Printing through a normal Windows GDI driver lets the print spooler guess paper sizes and scale pages. On a gap-sensor label printer, a 1mm mismatch feeds extra blank labels. Sending native TSPL as RAW data bypasses GDI entirely: quantities, dimensions, and positions are guaranteed byte-for-byte.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tspl_anatomy",
                "TSC / TSPL Language",
                "Script Anatomy — One Label Job",
                "The exact command sequence InvoiceStudio streams to the TA210 for a print run.",
                """
                Every print run sent by InvoiceStudio follows a clean, deterministic script structure: a setup header followed by an image buffer per strip row and a `PRINT` command specifying copies to feed.

                ```tspl
                SIZE 432 dot,200 dot
                GAP 24 dot,0 dot
                DIRECTION 1
                CLS
                BITMAP 0,0,54,200,0,<binary bitmap data>
                PRINT 1,1
                ```

                ### Command Breakdown
                - `SIZE 432 dot,200 dot`: The strip row (web width × row height) in dots ($54\\text{ mm} \\times 25\\text{ mm}$ at $8\\text{ dots/mm} = 432 \\times 200$).
                - `GAP 24 dot,0 dot`: The physical die-cut gap between labels ($3\\text{ mm} = 24\\text{ dots}$). The optical sensor uses this to detect label start.
                - `DIRECTION 1`: The bitmap's $y=0$ edge feeds first, so the label reads upright immediately after tearing.
                - `CLS`: Clears the image memory buffer before rendering.
                - `BITMAP`: Burns the 1-bit monochrome raster image of the full strip row.
                - `PRINT 1,1`: Feeds exactly ONE label row. Five identical copies become one bitmap followed by `PRINT 5,1`.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tspl_setup",
                "TSC / TSPL Language",
                "Setup Commands — SIZE, GAP, DIRECTION, CLS",
                "The four commands that put the printer into a known state before drawing.",
                """
                ### 1. `SIZE m,n` — Label Dimensions
                Defines the label width (across the head) and length (along the feed direction).
                - Syntax: `SIZE 432 dot,200 dot`
                - Wrong `SIZE` = wrong feed pitch (causing extra blank feeds or clipped graphics).

                ### 2. `GAP m,n` / `BLINE` — Sensor Setup
                - `GAP 24 dot,0 dot`: Declares die-cut stock with a 24-dot gap and 0-dot offset.
                - `GAP 0,0`: Declares continuous roll stock without gaps.
                - `BLINE`: Replaces `GAP` when using black-mark media.

                ### 3. `DIRECTION 0|1` — Print Orientation
                Controls which edge of the bitmap leads during paper feed. `DIRECTION 1` prints the image so it reads right-side-up after tearing at the tear bar.

                ### 4. `CLS` — Clear Image Buffer
                Clears the printer's internal raster memory. Sent before every `BITMAP` command so artifacts from preceding labels never contaminate subsequent prints.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tspl_bitmap",
                "TSC / TSPL Language",
                "Drawing the Label — the BITMAP Command",
                "How a 1-bit image reaches the head: syntax, bit polarity and row padding.",
                """
                The `BITMAP` command draws a monochrome 1-bit raster image directly into the printer's RAM buffer.

                ```tspl
                BITMAP x,y,width,height,mode,data
                ```

                ### Parameter Definitions
                - `x, y`: Top-left start coordinate in dots (InvoiceStudio always draws from `0,0`).
                - `width`: Row width in **bytes** (not dots!). A 432-dot row = $\\frac{432}{8} = 54\\text{ bytes}$.
                - `height`: Image height in **dots** (pixel scanlines).
                - `mode`: `0` = OVERWRITE (default used by the app), `1` = OR, `2` = XOR.
                - `data`: Raw binary dot stream, MSB first, with each line padded to a full byte boundary.

                ### Bit Polarity Rules
                - **Bit 0** = **BLACK** (thermal head heats up and burns).
                - **Bit 1** = **WHITE** (paper stays unheated/blank).
                - Every byte starts initialized to all-white (`0xFF`), and InvoiceStudio clears the bit of every dot scheduled to burn.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tspl_print",
                "TSC / TSPL Language",
                "Printing & Quantities — the PRINT Command",
                "PRINT m,n controls copies: how selecting 1 label feeds exactly 1 label.",
                """
                ```tspl
                PRINT m[,n]
                ```

                - `m`: How many labels/sets to print (the queued label count).
                - `n`: Optional repeat count of each set (InvoiceStudio always specifies `1`).

                ### Fast Repetition
                `PRINT 1,1` feeds exactly one label. A print queue of 100 identical labels compresses into **one single bitmap upload** followed by `PRINT 100,1`. The printer repeats its internal hardware buffer at full print speed (5 inches/sec) with zero PC communication lag.

                ### Helper Commands (Reference)
                - `FORMFEED`: Advances paper to the next die-cut gap.
                - `CUT`: Triggers the automatic rotary cutter (on cutter-equipped hardware).
                - `FEED n`: Feeds paper forward by $n$ dots without printing.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tsc_blank_labels",
                "TSC / Troubleshooting",
                "Blank Labels After a Good Label",
                "Printed one record but got extra empty labels? Work through the causes in order — almost all are configuration, one is printer calibration.",
                """
                Extra blank labels are **always a paper feed mismatch**, never extra `PRINT` commands sent by the software. Work through these verified causes in order:

                ### Cause 1: Declared Feed Pitch is Larger Than Real Roll Pitch
                The printer burns the bitmap and then feeds until the next detected gap. If the software declares a 78 mm pitch on a roll that physically has a 39 mm pitch, **one print feeds two labels** (first has content, second is blank).
                - **Fix**: Measure ONE physical label + ONE gap with a ruler. Set **Label Height** + **Feed Gap** in the Label Stock dialog so their sum equals your measured distance.

                ### Cause 2: Multi-Across Stock With Small Queue
                On 2-across or 4-across stock, the printer feeds a complete horizontal strip row. If you print 1 label on 4-across media, 1 label prints and the remaining 3 in that row pass out blank.
                - **Fix**: Set `Columns = 1` for single-column rolls. For multi-across rolls, print in multiples of the column count.

                ### Cause 3: Stale Sensor Calibration
                If the optical gap sensor has stored old threshold values from a previous roll, it fails to detect the gap at the expected position.
                - **Fix**: Open **Bulk Print dialog → Calibrate Sensor** to spool the `AUTODETECT` command, or hold the printer's physical `FEED` button while powering on.

                ### Cause 4: Hardware / Windows Spooler Leftovers
                Stuck print jobs in the Windows spooler can inject form feeds between jobs. Clear the Windows print queue and restart the printer.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tsc_troubleshooting",
                "TSC / Troubleshooting",
                "Troubleshooting Guide",
                "Symptom → cause → fix for the most common thermal label printing problems.",
                """
                ### Common Symptoms & Solutions

                #### 1. Prints All Black with White Content Shapes
                - **Cause**: Inverted bit polarity in driver.
                - **Fix**: InvoiceStudio encodes ink as bit 0 (burn) and paper as bit 1 natively. Ensure raw TSPL printing is enabled.

                #### 2. Print is Too Light or Faint
                - **Cause**: Insufficient head heat or worn ribbon.
                - **Fix**: Increase the **Brightness Threshold** (Settings → Print) to 170–180. On thermal transfer models, verify ribbon tension and clean the head with isopropyl alcohol.

                #### 3. Content is Clipped on the Right
                - **Cause**: Media exceeds maximum print width.
                - **Fix**: The TSC TA210 print head is 108 mm wide. Ensure your total liner width in Label Stock does not exceed 108 mm.

                #### 4. Barcode Fails to Scan
                - **Cause**: Over-burned edges blooming together.
                - **Fix**: Reduce Brightness Threshold to 120–130, ensure adequate white quiet-zones around the barcode, and avoid scaling barcodes below 100% element width.
                """,
                now));

        list.add(new KnowledgeArticle(
                "art_tsc_pipeline",
                "TSC / Inside the App",
                "InvoiceStudio Print Pipeline",
                "From canvas design to burned dots: every stage between the Strip Preview and the label.",
                """
                InvoiceStudio utilizes a unified rendering pipeline: what you see in the Strip Preview is rendered by the exact same engine that burns onto the physical label.

                ```mermaid
                flowchart TD
                    A["Label Canvas Design"] --> B["Strip-Row Page Composer"]
                    B --> C["2x Snapshot Rendering (406 DPI)"]
                    C --> D["Grayscale Luminance (Rec. 601)"]
                    D --> E["2x2 Box Downsample to 203 DPI"]
                    E --> F["Brightness Threshold Cut (1-bit B&W)"]
                    F --> G["TSPL Generator (SIZE/GAP/DIRECTION/BITMAP)"]
                    G --> H["RAW Byte Spooling (Direct to USB Port)"]
                    H --> I["TSC TA210 Head Burns Dots"]
                ```

                ### Pipeline Key Features
                - **Background Execution**: Spooling occurs off the JavaFX Application Thread, keeping UI controls completely fluid.
                - **Zero GDI Scaling**: Windows print spooler never resamples, rotates, or alters dot positioning.
                - **Deterministic Output**: Bitmaps match the preview down to the individual dot.
                """,
                now));

        return list;
    }
}
