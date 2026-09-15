package com.invoicestudio.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings → Knowledge Hub: the researched reference material for the
 * thermal label printing stack — the TSC TA210 printer, its native
 * TSPL/TSPL2 command language and how InvoiceStudio's print pipeline
 * drives it — presented as a modern, searchable topic browser.
 * <p>
 * All command syntax documented here was verified against the official
 * "TSPL/TSPL2 Programming Language" manual (TSC Auto ID) and field prints.
 * <p>
 * Design (modernized per user request): a gold-accented hero header, a
 * searchable sidebar whose topics are grouped into category sections
 * (PRINTER / TSPL LANGUAGE / GUIDES / INSIDE THE APP) with card-style
 * cells, and the article itself on a matching dark card with a gold
 * accent bar. All reference content is unchanged — only the shell is new.
 */
public class KnowledgeHubPanel extends VBox {

    /** One browsable knowledge topic: category + title + intro + content blocks. */
    private record Topic(String category, String title, String subtitle, Node[] blocks) {}

    /** Sidebar row: either a group header or a selectable topic. */
    private static final class Row {
        final String header;
        final Topic topic;
        private Row(String header, Topic topic) { this.header = header; this.topic = topic; }
        static Row header(String h) { return new Row(h, null); }
        static Row topic(Topic t) { return new Row(null, t); }
        boolean isHeader() { return header != null; }
        /** Runtime verify harnesses locate topics by scanning item strings —
         *  keep the title (or header) in toString(). */
        @Override public String toString() { return header != null ? header : (topic != null ? topic.title() : ""); }
    }

    private static final String CODE_STYLE =
            "-fx-background-color: #0d1117; -fx-text-fill: #c9d1d9; -fx-font-family: 'Consolas','Courier New',monospace;"
            + "-fx-font-size: 12px; -fx-padding: 10 12 10 12; -fx-background-radius: 6;"
            + "-fx-border-color: #2d3b4e; -fx-border-radius: 6; -fx-border-width: 1;";

    private static final String NOTE_STYLE =
            "-fx-background-color: rgba(217,161,59,0.10); -fx-text-fill: #e8d5a3; -fx-padding: 10 12 10 12;"
            + "-fx-background-radius: 6; -fx-border-color: rgba(217,161,59,0.45); -fx-border-radius: 6; -fx-border-width: 1;";

    private static final String CARD_STYLE =
            "-fx-background-color: #151C29; -fx-background-radius: 8;"
            + "-fx-border-color: #273245; -fx-border-radius: 8; -fx-border-width: 1;";

    private final List<Topic> allTopics = new ArrayList<>();
    private final ListView<Row> topicList = new ListView<>();
    private final TextField searchField = new TextField();
    private final VBox contentBox = new VBox(12);
    private final Label contentTitle = new Label();
    private final Label contentSubtitle = new Label();
    private final Label countChip = new Label();

    public KnowledgeHubPanel() {
        setSpacing(10);
        setPadding(new Insets(12, 14, 14, 14));

        allTopics.addAll(buildTopics());

        getChildren().addAll(buildHero(), buildBody());

        searchField.textProperty().addListener((obs, old, val) -> refreshRows());
        refreshRows();
        showTopic(allTopics.get(0));
    }

    // ─────────────────────────────────────────────────────────────────
    // Modern shell: hero header + searchable sidebar / article card
    // ─────────────────────────────────────────────────────────────────

    private Node buildHero() {
        StackPane badge = new StackPane(IconHelper.getIcon(IconHelper.ICON_SPARKLES, 20, "#D9A13B"));
        badge.setStyle("-fx-background-color: rgba(217,161,59,0.14); -fx-background-radius: 10;"
                + "-fx-border-color: rgba(217,161,59,0.45); -fx-border-radius: 10; -fx-border-width: 1;");
        badge.setPrefSize(42, 42);
        badge.setMinSize(42, 42);
        badge.setMaxSize(42, 42);

        Label title = new Label("Knowledge Hub");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #F2F4F8;");
        Label subtitle = new Label("Everything the app knows about the TSC TA210 printer, the TSPL/TSPL2 "
                + "command language and how InvoiceStudio turns your label design into burned dots.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #97A3B6;");
        subtitle.setWrapText(true);

        countChip.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #D9A13B;"
                + "-fx-background-color: rgba(217,161,59,0.12); -fx-background-radius: 8;"
                + "-fx-border-color: rgba(217,161,59,0.35); -fx-border-radius: 8; -fx-border-width: 1;"
                + "-fx-padding: 2 8 2 8;");
        countChip.setText(allTopics.size() + " topics");

        HBox titleRow = new HBox(8, title, countChip);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(2, titleRow, subtitle);

        HBox hero = new HBox(12, badge, text);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setStyle(CARD_STYLE + "-fx-padding: 14 16 14 16;");
        return hero;
    }

    private Node buildBody() {
        // ── Sidebar: search + grouped topic list ─────────────────────
        searchField.setPromptText("Search topics…");
        searchField.setStyle("-fx-background-color: #0F1520; -fx-text-fill: #E6EAF0; -fx-prompt-text-fill: #5b6779;"
                + "-fx-background-radius: 6; -fx-border-color: #273245; -fx-border-radius: 6; -fx-border-width: 1;"
                + "-fx-padding: 6 10 6 10; -fx-font-size: 12px;");

        topicList.getStyleClass().add("bg-card");
        topicList.setStyle("-fx-background-color: transparent;");
        topicList.setPrefWidth(320);
        topicList.setMinWidth(280);
        topicList.setPlaceholder(new Label("No matching topics"));
        topicList.setCellFactory(lv -> new ListCell<>() {
            {
                // selection changes don't re-run updateItem — re-render on both
                selectedProperty().addListener((obs, a, b) -> render());
            }
            @Override protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty);
                render();
            }
            private void render() {
                Row r = getItem();
                if (r == null || isEmpty()) {
                    setText(null); setGraphic(null);
                    setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                    return;
                }
                if (r.isHeader()) {
                    Label h = new Label(r.header.toUpperCase(Locale.ROOT));
                    h.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #D9A13B;");
                    HBox box = new HBox(h);
                    box.setPadding(new Insets(10, 6, 2, 8));
                    setGraphic(box);
                    setText(null);
                    setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                    return;
                }
                boolean sel = isSelected();
                Label t = new Label(r.topic.title());
                t.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: "
                        + (sel ? "#F0D9A8" : "#ECEFF4") + ";");
                t.setWrapText(true);
                Label s = new Label(r.topic.subtitle());
                s.setStyle("-fx-font-size: 11px; -fx-text-fill: #8A94A6;");
                s.setWrapText(true);
                s.setMaxWidth(250);
                s.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
                VBox card = new VBox(2, t, s);
                card.setPadding(new Insets(7, 9, 7, 9));
                card.setStyle("-fx-background-color: " + (sel ? "#1D2839" : "transparent") + ";"
                        + "-fx-background-radius: 6; -fx-border-radius: 6;"
                        + (sel ? "-fx-border-color: #D9A13B; -fx-border-width: 0 0 0 3;" : ""));
                setGraphic(card);
                setText(null);
                setStyle("-fx-background-color: transparent; -fx-padding: 1 3;");
            }
        });
        topicList.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null && !sel.isHeader()) showTopic(sel.topic);
        });

        VBox sidebar = new VBox(8, searchField, topicList);
        sidebar.setStyle(CARD_STYLE + "-fx-padding: 10;");
        sidebar.setPrefWidth(344);
        sidebar.setMinWidth(300);
        VBox.setVgrow(topicList, Priority.ALWAYS);

        // ── Article card ─────────────────────────────────────────────
        Region accentBar = new Region();
        accentBar.setStyle("-fx-background-color: #D9A13B; -fx-background-radius: 2;");
        accentBar.setPrefSize(44, 4);
        accentBar.setMinSize(44, 4);
        accentBar.setMaxSize(44, 4);

        contentTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #F2F4F8;");
        contentTitle.setWrapText(true);
        contentSubtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #97A3B6;");
        contentSubtitle.setWrapText(true);
        contentBox.setPadding(new Insets(2, 2, 18, 2));

        var scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox article = new VBox(8, accentBar, contentTitle, contentSubtitle, scroll);
        article.setStyle(CARD_STYLE + "-fx-padding: 16 18 6 18;");
        HBox.setHgrow(article, Priority.ALWAYS);

        HBox split = new HBox(14, sidebar, article);
        split.setFillHeight(true);
        VBox.setVgrow(split, Priority.ALWAYS);
        return split;
    }

    /** Rebuilds the sidebar rows from the search filter, keeping grouped
     *  category headers; preserves the visible article if it still matches. */
    private void refreshRows() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        Topic selected = currentlyShown();
        List<Row> rows = new ArrayList<>();
        String lastCat = null;
        int shown = 0;
        for (Topic t : allTopics) {
            boolean match = q.isEmpty()
                    || t.title().toLowerCase(Locale.ROOT).contains(q)
                    || t.subtitle().toLowerCase(Locale.ROOT).contains(q)
                    || t.category().toLowerCase(Locale.ROOT).contains(q);
            if (!match) continue;
            if (!t.category().equals(lastCat)) {
                rows.add(Row.header(t.category()));
                lastCat = t.category();
            }
            rows.add(Row.topic(t));
            shown++;
        }
        topicList.getItems().setAll(rows);
        countChip.setText(shown + " topics");
        // restore selection: previously shown topic, else first topic row
        Row target = null;
        if (selected != null) {
            for (Row r : rows) {
                if (!r.isHeader() && r.topic == selected) { target = r; break; }
            }
        }
        if (target == null) {
            for (Row r : rows) { if (!r.isHeader()) { target = r; break; } }
        }
        if (target != null) {
            topicList.getSelectionModel().select(target);
            if (selected == target.topic) showTopic(selected); // re-render highlight
        } else {
            topicList.getSelectionModel().clearSelection();
        }
    }

    private Topic currentlyShown() {
        for (Topic t : allTopics) {
            if (t.title().equals(contentTitle.getText())) return t;
        }
        return null;
    }

    private void showTopic(Topic t) {
        if (t == null) return;
        contentTitle.setText(t.title());
        contentSubtitle.setText(t.subtitle());
        contentBox.getChildren().setAll(t.blocks());
    }

    // ─────────────────────────────────────────────────────────────────
    // Content builders
    // ─────────────────────────────────────────────────────────────────

    private static Node para(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 13px;");
        return l;
    }

    private static Node bullet(String text) {
        Label l = new Label("•  " + text);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 13px;");
        VBox.setMargin(l, new Insets(0, 0, 0, 10));
        return l;
    }

    private static Node heading(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #d9a13b; -fx-font-size: 12px; -fx-font-weight: bold;");
        VBox.setMargin(l, new Insets(6, 0, 0, 0));
        return l;
    }

    private static Node code(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle(CODE_STYLE);
        l.setFont(Font.font("Consolas", 12));
        return l;
    }

    private static Node note(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle(NOTE_STYLE);
        return l;
    }

    private static Node sep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color: #2d3b4e;");
        return s;
    }

    // ─────────────────────────────────────────────────────────────────
    // The knowledge base
    // ─────────────────────────────────────────────────────────────────

    private List<Topic> buildTopics() {
        return List.of(
            ta210Overview(),
            stockSensingAndOrientation(),
            tsplLanguage(),
            scriptAnatomy(),
            setupCommands(),
            bitmapCommand(),
            printCommand(),
            blankLabelsGuide(),
            appPipeline(),
            thresholdGuide(),
            troubleshooting(),
            advancedKnobs());
    }

    private Topic ta210Overview() {
        return new Topic("PRINTER", "TSC TA210 — Printer Overview",
            "The desktop label printer this app targets: specs, sensors and what makes it different from an A4 printer.",
            new Node[] {
                para("The TSC TA210 is a 4-inch desktop label printer (the TA310 is its 300-dpi sibling). Unlike an "
                    + "office printer it prints one die-cut label at a time from a roll, burning an image with a thermal "
                    + "print head. It supports both direct thermal media (heat-sensitive paper) and thermal transfer "
                    + "(ribbon + media)."),
                heading("KEY SPECIFICATIONS (OFFICIAL TSC DATASHEET)"),
                bullet("Resolution: 203 dpi = 8 dots per mm (TA300/TA310 are the 300 dpi / 12 dots-per-mm siblings)."),
                bullet("Maximum print width: 4.25 in / 108 mm — the physical width of the print head (300-dpi TA310: "
                    + "104 mm / 4.09 in)."),
                bullet("Maximum print speed: 5 ips (127 mm/s); maximum print length 90 in (2286 mm)."),
                bullet("Media sensors: movable GAP sensor (die-cut labels) and BLACK MARK sensor (continuous stock with marks)."),
                bullet("Command languages: TSPL / TSPL2 natively (also supports EPL/ZPL emulation on some firmware)."),
                note("Because the head is 108 mm wide, any liner up to that width prints full-bleed — a 77 mm two-up "
                    + "liner is perfectly in range. The app warns only when the Label Stock paper width exceeds 108 mm. "
                    + "(Early app builds wrongly claimed a 2-inch / 54 mm head and warned about clipping — corrected "
                    + "against the official datasheet.)"),
                sep(),
                para("Why this matters in the app: every label dimension you configure (Label Stock dialog) is converted "
                    + "from millimeters to dots at 8 dots/mm, and the print head receives a bitmap at exactly that density.")
            });
    }

    private Topic stockSensingAndOrientation() {
        return new Topic("PRINTER", "Label Stock, Sensors & Orientation — Who Knows What",
            "What the printer figures out by itself, what the software must declare, and how "
                + "BarTender's Page Setup maps to the Label Stock dialog.",
            new Node[] {
                para("A thermal printer is smart about the FEED direction only. Its gap sensor watches one vertical "
                    + "line of the web and sees the die-cut gaps (or black marks) scroll past. Everything ACROSS the "
                    + "strip — how many labels sit side by side, the liner width, the side margins — is invisible to "
                    + "that sensor and must be declared by the software that drives the printer."),
                heading("WHAT THE PRINTER LEARNS BY ITSELF — SENSOR CALIBRATION"),
                bullet("Calibration feeds a few labels through the sensor and MEASURES the roll's real pitch (label "
                    + "length + gap), then stores sensor thresholds. TSPL exposes it as GAPDETECT and AUTODETECT "
                    + "(manual p.6: \"feeds the paper through the sensor to determine the paper and gap sizes\"); "
                    + "the printer menu has the same function (Sensor → Auto Calibration)."),
                bullet("The app's one-click equivalent: Bulk Print dialog → Calibrate Sensor — it spools AUTODETECT "
                    + "as its own RAW job. Reprint after calibrating whenever the roll was changed."),
                bullet("That is the full extent of the printer's own knowledge: WHERE each label ends along the feed. "
                    + "It cannot name the label size, count the columns across, or place content — arrangement is "
                    + "always the sender's job."),
                heading("WHAT THE SOFTWARE MUST DECLARE — OUR SCRIPT OR THE DRIVER"),
                para("Printing through the Windows driver, the driver's Page Setup declares the stock and BarTender "
                    + "reads it. InvoiceStudio skips the GDI layer and spools raw TSPL, so OUR script is the "
                    + "declaration: SIZE (liner width × row height in dots), GAP (feed gap), DIRECTION, then the "
                    + "bitmap and PRINT. The Label Stock dialog is therefore exactly the driver's Page Setup — the "
                    + "numbers there must match the physical roll."),
                heading("BARTENDER PAGE SETUP ↔ LABEL STOCK DIALOG"),
                bullet("Paper Size 77.0 × 38.0 mm ↔ Paper (liner) width 77 mm; BarTender's paper height (38) = label "
                    + "height (36) + feed gap (2) — the FEED PITCH our dialog shows live."),
                bullet("Label Size 75.0 × 36.0 mm ↔ Label width/height ON STRIP (width across, height along the feed)."),
                bullet("Columns: 2 across, Rows: 1 down ↔ Columns across: 2 (our stock feeds one row per print)."),
                bullet("Margins 1.0 mm ↔ Left/Right margin. Corner radius ↔ Shape tab."),
                bullet("Orientation Portrait/Landscape ↔ Artwork direction. Seagull's own doc: orientation \"does not "
                    + "change the width and height Paper Size\" — BOTH systems rotate the CONTENT only; the label on "
                    + "the roll stays exactly the size you measured."),
                heading("THE SIMPLE WAY TO THINK ABOUT IT"),
                para("Measure the roll with a ruler: label width across, label height along the feed, the gap between "
                    + "rows, how many labels across. Type those numbers into Label Stock — the diagram mirrors the "
                    + "roll. Then decide only HOW the canvas artwork lands on that label: as designed, or rotated 90°. "
                    + "The canvas keeps showing the design cell; the diagram and Strip Preview always show the physical "
                    + "label, so nothing is ever in two minds about what prints."),
                note("Correction history: early app builds described the TA210 as a 2-inch / 54 mm printer and warned "
                    + "that 77 mm liners would clip. The official datasheet says 4-inch / 108 mm (4.25\") at 203 dpi — "
                    + "the app now matches the datasheet."),
                sep(),
                para("References: TSC TA210/TA310 datasheet (max print width 108 mm / 104 mm); TSPL/TSPL2 manual — "
                    + "AUTODETECT & GAPDETECT (p.6), GAP (p.2), SIZE (p.1); Seagull driver help — Page orientation.")
            });
    }

    private Topic tsplLanguage() {
        return new Topic("TSPL LANGUAGE", "TSPL / TSPL2 — The Command Language",
            "The text-based printer language we send to the TA210: commands, units and line endings.",
            new Node[] {
                para("TSPL (TSC Printer Language) and its extended version TSPL2 are the TA210's native command languages. "
                    + "A print job is simply a text script of commands, each on its own line terminated by CRLF, with some "
                    + "commands (like BITMAP) followed by raw binary data."),
                bullet("Text commands are ASCII, case-insensitive, CRLF (\\r\\n) terminated."),
                bullet("The default unit is the DOT — at 203 dpi, 1 mm = 8 dots. The app declares all sizes in dots so no "
                    + "rounding drift can appear between the declared stock and the bitmap."),
                bullet("The script owns the printer state: stock size (SIZE), sensor type (GAP/BLINE), print orientation "
                    + "(DIRECTION), the image buffer (CLS) and how many labels to print (PRINT)."),
                heading("WHY A NATIVE LANGUAGE BEATS THE WINDOWS DRIVER"),
                para("Printing through a normal Windows driver lets the spooler guess the paper size, scale and rotate the "
                    + "page. On a gap-sensor label printer a wrong guess feeds extra blank labels. Sending TSPL as RAW data "
                    + "bypasses GDI entirely — the driver passes our bytes untouched to the printer port, so quantities, "
                    + "position and orientation are exactly what the app declared.")
            });
    }

    private Topic scriptAnatomy() {
        return new Topic("TSPL LANGUAGE", "Script Anatomy — One Label Job",
            "The exact command sequence InvoiceStudio streams to the TA210 for a print run.",
            new Node[] {
                para("Every print run is one script: a setup header followed by one image buffer per strip row and a PRINT "
                    + "command that says how many copies to feed."),
                code("SIZE 432 dot,200 dot\r\n"
                    + "GAP 24 dot,0 dot\r\n"
                    + "DIRECTION 1\r\n"
                    + "CLS\r\n"
                    + "BITMAP 0,0,54,200,0,<binary bitmap data>\r\n"
                    + "PRINT 1,1"),
                bullet("SIZE — the strip row (web width × row height) in dots: 54 mm × 25 mm at 8 dots/mm = 432 × 200."),
                bullet("GAP — the physical feed gap between labels (3 mm = 24 dots here). The gap sensor uses it to find "
                    + "each label's start. Continuous stock is declared as GAP 0,0."),
                bullet("DIRECTION 1 — the bitmap's y=0 edge prints first, so the label reads upright after tearing."),
                bullet("CLS — clear the image buffer before drawing."),
                bullet("BITMAP — burn the 1-bit image of the whole strip row."),
                bullet("PRINT 1,1 — feed exactly ONE label. Five identical copies become one bitmap + PRINT 5,1 (the "
                    + "printer repeats its own buffer — no extra data, no extra feeds).")
            });
    }

    private Topic setupCommands() {
        return new Topic("TSPL LANGUAGE", "Setup Commands — SIZE, GAP, DIRECTION, CLS",
            "The four commands that put the printer into a known state before drawing.",
            new Node[] {
                heading("SIZE m,n — LABEL DIMENSIONS"),
                para("Defines the label width (across the head) and length (along the feed). The app always uses dot units: "
                    + "SIZE 432 dot,200 dot. Wrong SIZE = wrong feed length (extra blanks or clipped labels)."),
                heading("GAP m,n / BLINE — SENSOR SETUP"),
                para("GAP declares die-cut stock: the feed gap and its offset (GAP 24 dot,0 dot). GAP 0,0 means continuous "
                    + "stock. BLINE replaces GAP for black-mark stock. The stock type chosen in the app's Label Stock "
                    + "dialog picks which command is emitted."),
                heading("DIRECTION 0|1 — PRINT ORIENTATION"),
                para("Controls which edge of the bitmap leads. DIRECTION 1 prints the image so it reads upright after "
                    + "tearing — the app's default so the printout matches the strip preview exactly."),
                heading("CLS — CLEAR BUFFER"),
                para("Clears the image memory. Sent before every BITMAP so nothing from a previous row leaks into the next."),
                note("Support knob: if your hardware ever prints upside-down, the system property "
                    + "invoicestudio.tspl.direction=0 flips the orientation without touching the design.")
            });
    }

    private Topic bitmapCommand() {
        return new Topic("TSPL LANGUAGE", "Drawing the Label — the BITMAP Command",
            "How a 1-bit image reaches the head: syntax, bit polarity and row padding.",
            new Node[] {
                para("BITMAP draws a monochrome image directly into the image buffer — this is how the app sends your "
                    + "label design, byte-for-byte identical to the strip preview."),
                code("BITMAP x,y,width,height,mode,data"),
                bullet("x, y — top-left position in dots (the app draws at 0,0 covering the whole strip)."),
                bullet("width — image width in BYTES (not dots!). A 432-dot row = 54 bytes."),
                bullet("height — image height in DOTS (rows)."),
                bullet("mode — 0 = OVERWRITE (used by the app), 1 = OR, 2 = XOR."),
                bullet("data — raw binary, MSB first, each row padded to a full byte, then CRLF."),
                heading("BIT POLARITY — THE #1 SOURCE OF INVERTED PRINTS"),
                para("Bit 0 = BLACK (the head burns). Bit 1 = WHITE (paper stays blank). Every byte starts all-white "
                    + "(0xFF) and the app clears the bit of every dot that must burn. Getting this backwards prints a "
                    + "black label with white content-shaped holes."),
                code("Row of 8 dots:  B W W B W W W B\n"
                    + "Encoded byte:    0 1 1 0 1 1 1 0  =  0x6E"),
                note("The app renders the design at 2× the dot grid, downsamples 2×2, then cuts to black/white with the "
                    + "Brightness Threshold — see that topic for how the cut is chosen.")
            });
    }

    private Topic printCommand() {
        return new Topic("TSPL LANGUAGE", "Printing & Quantities — the PRINT Command",
            "PRINT m,n controls copies: how selecting 1 label feeds exactly 1 label.",
            new Node[] {
                code("PRINT m[,n]"),
                bullet("m — how many labels/sets to print (the app writes the queued label count here)."),
                bullet("n — optional repeat count of each set (the app always sends 1)."),
                para("PRINT 1,1 feeds exactly one label — the spooler and driver never add pages because the RAW script "
                    + "declares the stock itself. A queue of 100 identical labels compresses into ONE bitmap download "
                    + "followed by PRINT 100,1; different labels become one CLS/BITMAP/PRINT group per strip row."),
                heading("FEED & CUT HELPERS (NOT USED BY THE APP)"),
                bullet("FORMFEED — feed one label length; CUT — cut continuous stock (cutter models); FEED n — feed n dots."),
                note("If the printer still feeds an extra blank after every label, the stock declaration and the physical "
                    + "roll disagree — re-check Label Stock size/gap, then run Calibrate Stock Sensor (Bulk Print dialog). "
                    + "The dedicated \"Blank Labels After a Good Label\" topic walks the full diagnosis.")
            });
    }

    private Topic blankLabelsGuide() {
        return new Topic("GUIDES", "Blank Labels After a Good Label",
            "Printed one record but got extra empty labels? Work through the causes in order — "
                + "almost all are configuration, one is printer calibration.",
            new Node[] {
                para("First, what the app sends: the TSPL script contains exactly one CLS / BITMAP / PRINT group per "
                    + "strip row and PRINT 1,1 for a single record — verified byte-for-byte (and you can audit the "
                    + "exact bytes yourself: start the app with -Dinvoicestudio.tspl.dump=/path/job.tspl and the RAW "
                    + "script of every job is written to that file). Extra blank labels are always FEED, never extra "
                    + "PRINT commands. The success toast shows the numbers to check: SIZE/GAP in dots, the feed pitch "
                    + "in mm, and how many slots of the last strip row the job filled."),
                heading("CAUSE 1 — THE DECLARED FEED PITCH IS BIGGER THAN THE ROLL'S REAL PITCH"),
                para("The TSPL manual defines PRINT's feed precisely: the printer burns the bitmap (SIZE height) and "
                        + "then feeds \"label gap to tear bar position\" — in total exactly the declared pitch "
                        + "(Label Height + Feed Gap from Label Stock, sent as SIZE + GAP in dots). The printer CANNOT "
                        + "know if your die-cut pitch is really that long: if the declared pitch spans two or three "
                        + "physical labels on the roll, ONE PRINT feeds TWO OR THREE die-cuts — the first carries the "
                        + "content, the rest pass out blank. That is the classic \"printed one, got three — last two "
                        + "empty\" report on a 1-across template: e.g. a 78.0 mm declared pitch (75.0 mm label + "
                        + "3.0 mm gap) on a roll whose real label-to-label pitch is 39.0 mm = two labels per print; "
                        + "26.0 mm = three."),
                bullet("The fix is arithmetic, not calibration: measure ONE physical label + ONE gap on the roll "
                        + "with a ruler, then set Label Height + Feed Gap in Label Stock so they add up to exactly "
                        + "that distance. The Label Stock dialog shows the resulting FEED PITCH live."),
                bullet("A tall design on short-pitch roll? The design may need to print sideways: use \"Rotate Design "
                        + "90° into print orientation\" so the physical cell (and therefore the pitch) matches the roll "
                        + "— canvas, strip preview and print then all agree."),
                bullet("Sanity check: toast pitch 78.0 mm but three labels feed out? Your roll pitch is ~26 mm "
                        + "(78 ÷ 3) or ~39 mm if two feed — set Label Height + Feed Gap to what your ruler says."),
                heading("CAUSE 2 — MULTI-ACROSS STOCK WITH A SMALL QUEUE"),
                para("Label Stock defines how many die-cut labels sit ACROSS the strip (Columns). The printer feeds one "
                    + "full row per strip; on 4-across stock a one-record job fills slot 1 and slots 2–4 pass under the "
                    + "head blank. That is physics, not a bug: the three empty die-cuts were part of the same fed row. "
                    + "The toast says so explicitly: \"the last strip row fills 1 of 4 slots…\""),
                bullet("Single-column roll? Open Template Designer → Label Stock and set Columns = 1 (and Strip width = "
                    + "label width). The Strip Preview then shows one label across — exactly what prints."),
                bullet("Genuinely 4-across stock? Print records in multiples of 4 (or accept the blank waste on the "
                    + "last row) — every full row prints all slots."),
                heading("CAUSE 3 — STALE SENSOR CALIBRATION"),
                para("The gap sensor learned the CURRENT roll's pitch at some point (different label size, different "
                    + "vendor, a media reload). If its stored pitch no longer matches the loaded stock, the printer "
                    + "feeds past the real gap hunting for one at the old distance — one printed label plus one or "
                    + "more blank ones. Symptom: blanks appear even when the declared pitch matches your ruler."),
                bullet("One-click fix: Bulk Print dialog → Calibrate Sensor. It spools AUTODETECT (TSPL manual p.6) "
                        + "as its own RAW job — the printer feeds a few labels measuring the die-cut pitch, then "
                        + "stores the result. Reprint your job afterwards."),
                bullet("Hardware equivalent: power the printer off, hold FEED while powering on until it feeds several "
                        + "labels, release — same measurement, stored in the printer."),
                heading("CAUSE 4 — LABEL STOCK VALUES DON'T MATCH THE PHYSICAL ROLL (WIDTH/OTHER)"),
                para("Measure one physical label + one gap with a ruler and compare with the toast's feed pitch: "
                        + "\"Feed pitch 33.0 mm/label (label 30.0 mm + 3.0 mm gap)\". If the numbers disagree with "
                        + "your ruler, fix Label Stock (label height, feed gap) — the script declares exactly those "
                        + "values via SIZE/GAP, so wrong config = wrong feed."),
                heading("CAUSE 5 — SOMETHING OUTSIDE THE APP"),
                para("A spooler queue with a stuck older job, a printer-side form/offset setting, or a driver that "
                        + "injects its own setup before the RAW stream. Check the Windows spooler for leftover jobs, "
                        + "power-cycle the printer, and if a misfeed survives a fresh calibration with a verified "
                        + "config, test the same script from a plain text spool to isolate the driver."),
                note("Sanity check that separates the causes: the toast. It reports \"fills k of N slots\" only when "
                    + "the queue doesn't fill the last row (cause 2). A pitch that matches the ruler but still feeds "
                    + "blanks = calibration (cause 3). A pitch that does NOT match the ruler = cause 1/4 — fix the "
                    + "numbers first, calibrate second."),
                sep(),
                para("Reference: TSC Auto ID, \"TSPL/TSPL2 Programming Language\" — AUTODETECT (p.6), GAP (p.2), "
                        + "PRINT (p.24): \"This command will print one label and feed label gap to tear bar position "
                        + "for tearing away.\"")
            });
    }

    private Topic appPipeline() {
        return new Topic("INSIDE THE APP", "InvoiceStudio Print Pipeline",
            "From canvas design to burned dots: every stage between the Strip Preview and the label.",
            new Node[] {
                para("The print path reuses the exact same renderer as the Strip Preview, so what you see is byte-for-byte "
                    + "what burns:"),
                code("Label design (canvas)\n"
                    + "   → strip-row page (shared preview renderer, physical cells)\n"
                    + "   → snapshot at 2× the 203-dpi dot grid\n"
                    + "   → grayscale luminance (Rec. 601) + transparency → white\n"
                    + "   → 2×2 box downsample to dot resolution\n"
                    + "   → Brightness Threshold cut → only BLACK or WHITE dots\n"
                    + "   → TSPL script: SIZE / GAP / DIRECTION + CLS / BITMAP / PRINT\n"
                    + "   → RAW spool (datatype RAW — driver passes bytes untouched)\n"
                    + "   → TA210 burns the bitmap"),
                bullet("The spool runs on a background thread — the UI stays responsive and reports the final result in a toast."),
                bullet("The print history entry is written when the spooler accepts the job (Test Print stays history-clean)."),
                bullet("Non-TSC printers keep the classic JavaFX/driver path — the native pipeline routes by printer name "
                    + "(TSC / TA2xx / TA3xx).")
            });
    }

    private Topic thresholdGuide() {
        return new Topic("INSIDE THE APP", "Brightness Threshold — Sharp Black & White",
            "The Settings → Print slider that decides which pixels burn black and which stay white.",
            new Node[] {
                para("A thermal head has no gray levels: every dot either burns BLACK or stays WHITE. The Brightness "
                    + "Threshold (Settings → Print) decides where that cut happens on the downsampled image."),
                bullet("Every dot's gray value (0 = black … 255 = white) is compared to the threshold."),
                bullet("gray ≤ threshold → burns sharp BLACK. gray > threshold → stays sharp WHITE."),
                bullet("Default 150 — slightly above mid-gray so hairlines and small text survive."),
                bullet("Higher threshold (e.g. 180) = more pixels count as black = bolder, heavier print. Use for faint "
                    + "thermal media or light fonts."),
                bullet("Lower threshold (e.g. 110) = only true darks burn = crisper barcodes on high-quality media, and "
                    + "less ribbon/heat use on thermal transfer."),
                note("The output is always exactly two colors — black and white. There is no dithering or gray: that is "
                    + "what keeps 1-D barcodes scannable."),
                para("The setting is stored per user in Settings and applied to every TSPL print job. A technical override "
                    + "(-Dinvoicestudio.tspl.threshold=NNN) still exists for support sessions.")
            });
    }

    private Topic troubleshooting() {
        return new Topic("GUIDES", "Troubleshooting Guide",
            "Symptom → cause → fix for the most common thermal label printing problems.",
            new Node[] {
                heading("PRINTS ALL BLACK WITH WHITE CONTENT SHAPES"),
                para("Inverted bit polarity — fixed in the app: ink is now encoded as bit 0 (burn) and paper as bit 1. "
                    + "If you ever see this again, update the app before checking hardware."),
                heading("BLANK LABELS OR EXTRA FEEDS BETWEEN GOOD LABELS"),
                para("The declared pitch, the sensor and the physical stock disagree. Check in order: Label Height + "
                    + "Feed Gap must equal the roll's measured label-to-label distance (cause 1 of the Blank Labels "
                    + "topic); Label Stock type = GAP for die-cut rolls (continuous only for receipt stock); Columns = 1 "
                    + "for single-column rolls; then calibrate the sensor with the Bulk Print dialog's Calibrate Sensor "
                    + "button (or the printer's FEED button hold). The dedicated \"Blank Labels After a Good Label\" "
                    + "topic has the full cause walkthrough."),
                heading("PRINT IS TOO LIGHT / FAINT"),
                para("Raise the Brightness Threshold (Settings → Print) so more pixels burn; on thermal transfer also "
                    + "check ribbon seating and the driver's darkness setting. Clean the head with isopropyl alcohol."),
                heading("CONTENT IS CLIPPED ON THE RIGHT"),
                para("The strip is wider than the TA210's 108 mm (4.25 in) print head. Reduce paper width in the Label "
                    + "Stock dialog — the app prints a warning toast when this happens. (Older builds warned at 54 mm; "
                    + "that limit was wrong — the official datasheet gives 108 mm.)"),
                heading("PRINTS, THEN THE APP USED TO FREEZE"),
                para("The RAW spool wait is now on a background thread; the dialog shows Sending… and reports the result "
                    + "when the spooler answers. If Windows shows the job stuck in the queue, the printer is paused or "
                    + "out of media — check the printer itself."),
                heading("BARCODE WON'T SCAN"),
                para("Lower the Brightness Threshold (bolder edges can bloom on cheap media), verify the quiet zone "
                    + "(white margin around the barcode), and make sure the barcode element is at least 100% of its "
                    + "designed size in the print.")
            });
    }

    private Topic advancedKnobs() {
        return new Topic("INSIDE THE APP", "Advanced Settings & Knobs",
            "System properties for support sessions — plus how routing picks the native pipeline.",
            new Node[] {
                heading("PRINT ENGINE ROUTING"),
                bullet("Auto (default): printer names containing TSC / TA200 / TA210 / TA300 / TA310 go through the native "
                    + "TSPL pipeline; everything else uses the JavaFX driver path."),
                bullet("-Dinvoicestudio.print.engine=tspl — force the native pipeline for every printer."),
                bullet("-Dinvoicestudio.print.engine=driver — disable the native pipeline (classic driver printing)."),
                heading("TSPL TUNING"),
                bullet("-Dinvoicestudio.tspl.direction=0|1 — flip print orientation (default 1)."),
                bullet("-Dinvoicestudio.tspl.dotsPerMm=N — override dot density (TA210 = 8, TA300/310 = 12)."),
                bullet("-Dinvoicestudio.tspl.threshold=0..255 — override the Brightness Threshold from Settings."),
                bullet("-Dinvoicestudio.tspl.dump=/path/job.tspl — write the exact RAW bytes of every spooled job to "
                    + "this file (the full TSPL script: SIZE/GAP/DIRECTION + CLS/BITMAP/PRINT + the 1-bit payload). "
                    + "Use it to audit \"what are we actually sending\" against the manual or to replay a job "
                    + "straight to the port."),
                heading("RAW SPOOLING"),
                para("Jobs are sent through the JDK print service as BYTE_ARRAY AUTOSENSE — on Windows this spools with "
                    + "datatype RAW, which the TSC driver passes untouched to the USB port. The printer is matched by "
                    + "exact name (case-insensitive fallback); a named job is never silently redirected to the default "
                    + "printer."),
                sep(),
                para("Reference: TSC Auto ID, \"TSPL/TSPL2 Programming Language\" manual — SIZE (p.1), GAP (p.2), DIRECTION "
                    + "(p.12), CLS (p.15), PRINT (p.24), BITMAP (p.45–46).")
            });
    }
}
