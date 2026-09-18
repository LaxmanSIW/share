package com.invoicestudio.ui.chat;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native JavaFX Markdown and Tabular Content Renderer.
 * Transforms assistant Markdown outputs into rich, polished JavaFX components:
 * <ul>
 *   <li><b>Markdown Tables</b>: Converted into styled, scrollable {@link GridPane} cards
 *       with distinct header cells, alternating row highlights, and right-aligned figures.</li>
 *   <li><b>Headings</b>: Rendered with distinct hierarchy and accent colors.</li>
 *   <li><b>Code Blocks</b>: Formatted in dark monospace containers with a quick-copy button.</li>
 *   <li><b>Lists</b>: Clean bullet and numbered list items with aligned spacing.</li>
 *   <li><b>Inline Formatting</b>: Styled bold ({@code **...**}) and inline code ({@code `...`})
 *       via wrapping {@link TextFlow} nodes.</li>
 * </ul>
 */
public final class ChatMarkdownRenderer {

    private static final String GOLD = "#D9A13B";
    private static final String TEXT_PRIMARY = "#E6EAF0";
    private static final String TEXT_BOLD = "#FFFFFF";
    private static final String TEXT_CODE = "#FCD34D";
    private static final String MONO_FONT = "Consolas, 'Courier New', monospace";

    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern CODE_PATTERN = Pattern.compile("`(.+?)`");

    private ChatMarkdownRenderer() {}

    /**
     * Renders a Markdown string into a JavaFX Node tree suitable for embedding
     * inside an assistant message bubble.
     */
    public static Node render(String markdown, boolean isError) {
        if (markdown == null || markdown.isBlank()) {
            Label empty = new Label("");
            return empty;
        }

        if (isError) {
            Label err = new Label(markdown);
            err.setWrapText(true);
            err.setStyle("-fx-text-fill: #f1b0b0; -fx-font-size: 13px;");
            return err;
        }

        VBox container = new VBox(6);
        container.setAlignment(Pos.TOP_LEFT);

        String[] lines = markdown.split("\\R");
        int i = 0;
        int n = lines.length;

        while (i < n) {
            String line = lines[i];
            String trimmed = line.trim();

            // 1. Fenced Code Block: ```...```
            if (trimmed.startsWith("```")) {
                String lang = trimmed.length() > 3 ? trimmed.substring(3).trim() : "";
                StringBuilder code = new StringBuilder();
                i++;
                while (i < n && !lines[i].trim().startsWith("```")) {
                    if (code.length() > 0) code.append("\n");
                    code.append(lines[i]);
                    i++;
                }
                i++; // Skip closing ```
                container.getChildren().add(renderCodeBlock(lang, code.toString()));
                continue;
            }

            // 2. Markdown Table: starts with '|' and followed by a separator line '|---|'
            if (isTableStart(lines, i)) {
                List<String> tableLines = new ArrayList<>();
                while (i < n && isTableLine(lines[i])) {
                    tableLines.add(lines[i]);
                    i++;
                }
                container.getChildren().add(renderTable(tableLines));
                continue;
            }

            // 3. Headings: #, ##, ###
            if (trimmed.startsWith("#")) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }
                if (level <= 6 && level < trimmed.length() && trimmed.charAt(level) == ' ') {
                    String headingText = trimmed.substring(level + 1).trim();
                    container.getChildren().add(renderHeading(headingText, level));
                    i++;
                    continue;
                }
            }

            // 4. Bullet / Numbered Lists
            if (isListItem(trimmed)) {
                container.getChildren().add(renderListItem(trimmed));
                i++;
                continue;
            }

            // 5. Blank line
            if (trimmed.isEmpty()) {
                i++;
                continue;
            }

            // 6. Regular paragraph (collect consecutive paragraph lines)
            StringBuilder para = new StringBuilder(line);
            i++;
            while (i < n) {
                String next = lines[i];
                String nextTrimmed = next.trim();
                if (nextTrimmed.isEmpty() || nextTrimmed.startsWith("```") || isTableStart(lines, i)
                        || nextTrimmed.startsWith("#") || isListItem(nextTrimmed)) {
                    break;
                }
                para.append("\n").append(next);
                i++;
            }
            container.getChildren().add(renderParagraph(para.toString()));
        }

        return container;
    }

    // ── Table Parsing & Rendering ─────────────────────────────────────

    private static boolean isTableStart(String[] lines, int index) {
        if (index + 1 >= lines.length) return false;
        String line1 = lines[index].trim();
        String line2 = lines[index + 1].trim();
        return line1.startsWith("|") && line1.endsWith("|") && line1.contains("|")
                && isTableSeparator(line2);
    }

    private static boolean isTableLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("|") && trimmed.contains("|");
    }

    private static boolean isTableSeparator(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) return false;
        String inner = trimmed.substring(1, trimmed.length() - 1);
        for (String part : inner.split("\\|")) {
            String p = part.trim().replace(":", "");
            if (p.isEmpty()) return false;
            for (int i = 0; i < p.length(); i++) {
                if (p.charAt(i) != '-') return false;
            }
        }
        return true;
    }

    private static Node renderTable(List<String> tableLines) {
        if (tableLines.size() < 2) {
            return renderParagraph(String.join("\n", tableLines));
        }

        List<String> headers = splitRow(tableLines.get(0));
        int cols = headers.size();
        if (cols == 0) {
            return renderParagraph(String.join("\n", tableLines));
        }

        // Determine column alignments from separator row (index 1)
        List<Pos> alignments = new ArrayList<>();
        List<String> sepCols = splitRow(tableLines.get(1));
        for (int c = 0; c < cols; c++) {
            if (c < sepCols.size()) {
                String s = sepCols.get(c).trim();
                boolean left = s.startsWith(":");
                boolean right = s.endsWith(":");
                if (left && right) alignments.add(Pos.CENTER);
                else if (right) alignments.add(Pos.CENTER_RIGHT);
                else alignments.add(Pos.CENTER_LEFT);
            } else {
                alignments.add(Pos.CENTER_LEFT);
            }
        }

        // Check if values in columns are numeric/currency to default right-align
        for (int c = 0; c < cols; c++) {
            if (alignments.get(c) == Pos.CENTER_LEFT) {
                boolean allNumeric = true;
                int rowCount = 0;
                for (int r = 2; r < tableLines.size(); r++) {
                    List<String> cells = splitRow(tableLines.get(r));
                    if (c < cells.size() && !cells.get(c).isBlank()) {
                        rowCount++;
                        if (!isNumericValue(cells.get(c).trim())) {
                            allNumeric = false;
                            break;
                        }
                    }
                }
                if (rowCount > 0 && allNumeric) {
                    alignments.set(c, Pos.CENTER_RIGHT);
                }
            }
        }

        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);

        // Header row
        for (int c = 0; c < cols; c++) {
            String hText = headers.get(c);
            Label hLbl = new Label(hText);
            hLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #F1F5F9; -fx-font-size: 12px; "
                    + "-fx-padding: 7 12 7 12; -fx-background-color: #192333;");
            hLbl.setAlignment(alignments.get(c));
            hLbl.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(hLbl, Priority.ALWAYS);
            grid.add(hLbl, c, 0);
        }

        // Data rows
        int rowIndex = 1;
        for (int r = 2; r < tableLines.size(); r++) {
            List<String> cells = splitRow(tableLines.get(r));
            String rowBg = (rowIndex % 2 == 0) ? "transparent" : "rgba(255, 255, 255, 0.02)";
            for (int c = 0; c < cols; c++) {
                String val = c < cells.size() ? cells.get(c) : "";
                Label cellLbl = new Label(val);
                cellLbl.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 12px; "
                        + "-fx-padding: 6 12 6 12; -fx-background-color: " + rowBg + ";"
                        + "-fx-border-color: #1F2C3F; -fx-border-width: 1 0 0 0;");
                cellLbl.setAlignment(alignments.get(c));
                cellLbl.setMaxWidth(Double.MAX_VALUE);
                GridPane.setHgrow(cellLbl, Priority.ALWAYS);
                grid.add(cellLbl, c, rowIndex);
            }
            rowIndex++;
        }

        VBox card = new VBox(grid);
        card.setStyle("-fx-background-color: #0E1520; -fx-border-color: #28374D; "
                + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-border-width: 1; -fx-overflow: hidden;");

        ScrollPane sp = new ScrollPane(card);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        sp.setPadding(new Insets(4, 0, 4, 0));

        return sp;
    }

    private static List<String> splitRow(String row) {
        String trimmed = row.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        List<String> cells = new ArrayList<>();
        for (String p : trimmed.split("\\|")) {
            cells.add(p.trim());
        }
        return cells;
    }

    private static boolean isNumericValue(String s) {
        String clean = s.replace("₹", "").replace("$", "").replace("€", "")
                .replace("%", "").replace(",", "").trim();
        if (clean.isEmpty()) return false;
        try {
            Double.parseDouble(clean);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    // ── Headings, Code Blocks, Lists & Paragraphs ──────────────────────

    private static Node renderHeading(String text, int level) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        double size = level == 1 ? 15.0 : (level == 2 ? 14.0 : 13.0);
        String color = level <= 2 ? TEXT_BOLD : GOLD;
        lbl.setStyle("-fx-font-size: " + size + "px; -fx-font-weight: bold; -fx-text-fill: " + color + "; "
                + "-fx-padding: 4 0 1 0;");
        return lbl;
    }

    private static Node renderCodeBlock(String lang, String code) {
        Label langLbl = new Label(lang.isBlank() ? "CODE" : lang.toUpperCase());
        langLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #7C8AA0; -fx-font-weight: bold;");

        Button copyBtn = new Button("Copy");
        copyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #97A3B6; -fx-font-size: 10.5px; "
                + "-fx-cursor: hand; -fx-padding: 2 6;");
        copyBtn.setOnAction(e -> {
            Clipboard cb = Clipboard.getSystemClipboard();
            ClipboardContent cc = new ClipboardContent();
            cc.putString(code);
            cb.setContent(cc);
            copyBtn.setText("Copied!");
            javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
            pt.setOnFinished(ev -> copyBtn.setText("Copy"));
            pt.play();
        });

        HBox head = new HBox(6, langLbl);
        head.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(langLbl, Priority.ALWAYS);
        head.getChildren().add(copyBtn);

        Label codeText = new Label(code);
        codeText.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 12px; -fx-text-fill: #F1F5F9;");
        codeText.setWrapText(true);

        VBox box = new VBox(4, head, codeText);
        box.setStyle("-fx-background-color: #0A0F17; -fx-border-color: #273245; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 10 8 10;");
        return box;
    }

    private static boolean isListItem(String trimmed) {
        return trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")
                || trimmed.matches("^\\d+\\.\\s+.*");
    }

    private static Node renderListItem(String trimmed) {
        String bullet;
        String content;
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
            bullet = "•";
            content = trimmed.substring(2).trim();
        } else {
            int dot = trimmed.indexOf('.');
            bullet = trimmed.substring(0, dot + 1);
            content = trimmed.substring(dot + 1).trim();
        }

        Label bulletLbl = new Label(bullet);
        bulletLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: " + GOLD + "; -fx-font-size: 13px;");
        bulletLbl.setMinSize(16, 18);
        bulletLbl.setAlignment(Pos.TOP_RIGHT);

        TextFlow flow = parseRichText(content);
        HBox row = new HBox(6, bulletLbl, flow);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(flow, Priority.ALWAYS);
        row.setPadding(new Insets(1, 0, 1, 4));
        return row;
    }

    private static Node renderParagraph(String text) {
        TextFlow flow = parseRichText(text);
        flow.setLineSpacing(2.0);
        return flow;
    }

    /**
     * Parses inline markdown tokens (**bold**, `code`) into an ordered {@link TextFlow}.
     */
    static TextFlow parseRichText(String text) {
        TextFlow flow = new TextFlow();
        if (text == null || text.isEmpty()) return flow;

        // Scan for inline bold (**...**) and inline code (`...`)
        int index = 0;
        int len = text.length();

        while (index < len) {
            int nextBold = text.indexOf("**", index);
            int nextCode = text.indexOf("`", index);

            // Find whichever delimiter appears earliest
            int earliest = -1;
            boolean isBold = false;
            if (nextBold >= 0 && (nextCode < 0 || nextBold < nextCode)) {
                earliest = nextBold;
                isBold = true;
            } else if (nextCode >= 0) {
                earliest = nextCode;
                isBold = false;
            }

            if (earliest < 0) {
                // No more tokens; append rest of string as normal text
                flow.getChildren().add(normalText(text.substring(index)));
                break;
            }

            // Append preceding text
            if (earliest > index) {
                flow.getChildren().add(normalText(text.substring(index, earliest)));
            }

            if (isBold) {
                int endBold = text.indexOf("**", earliest + 2);
                if (endBold > earliest + 2) {
                    flow.getChildren().add(boldText(text.substring(earliest + 2, endBold)));
                    index = endBold + 2;
                } else {
                    flow.getChildren().add(normalText("**"));
                    index = earliest + 2;
                }
            } else {
                int endCode = text.indexOf("`", earliest + 1);
                if (endCode > earliest + 1) {
                    flow.getChildren().add(codeText(text.substring(earliest + 1, endCode)));
                    index = endCode + 1;
                } else {
                    flow.getChildren().add(normalText("`"));
                    index = earliest + 1;
                }
            }
        }

        return flow;
    }

    private static Text normalText(String str) {
        Text t = new Text(str);
        t.setFill(Color.web(TEXT_PRIMARY));
        t.setFont(Font.font("System", 13.0));
        return t;
    }

    private static Text boldText(String str) {
        Text t = new Text(str);
        t.setFill(Color.web(TEXT_BOLD));
        t.setFont(Font.font("System", FontWeight.BOLD, 13.0));
        return t;
    }

    private static Text codeText(String str) {
        Text t = new Text(" " + str + " ");
        t.setFill(Color.web(TEXT_CODE));
        t.setFont(Font.font("Consolas", 12.0));
        return t;
    }
}
