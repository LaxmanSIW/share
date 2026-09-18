package com.invoicestudio.ui.chat;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatMarkdownRendererTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
    }

    @Test
    void rendersMarkdownTableIntoGridPaneCard() {
        String md = """
                Here is your inventory breakdown:
                | Item | Stock | Rate | Total |
                |---|---|---|---|
                | Denim Jeans | 15 pcs | ₹900.00 | ₹13,500.00 |
                | Cotton Shirt | 40 pcs | ₹350.00 | ₹14,000.00 |
                
                All stock is updated.
                """;

        Node root = ChatMarkdownRenderer.render(md, false);
        assertTrue(root instanceof VBox);
        VBox vbox = (VBox) root;

        // Should have paragraph, table scrollpane, and following paragraph
        assertEquals(3, vbox.getChildren().size());

        Node tableNode = vbox.getChildren().get(1);
        assertTrue(tableNode instanceof ScrollPane, "Table should be wrapped in a horizontal ScrollPane");
        ScrollPane sp = (ScrollPane) tableNode;
        assertTrue(sp.getContent() instanceof VBox);
        VBox card = (VBox) sp.getContent();
        assertTrue(card.getChildren().get(0) instanceof GridPane, "Card should contain a GridPane");

        GridPane grid = (GridPane) card.getChildren().get(0);
        // 4 cols * (1 header + 2 data rows) = 12 cells
        assertEquals(12, grid.getChildren().size());
        assertEquals(4, grid.getColumnConstraints().size());
        for (var cc : grid.getColumnConstraints()) {
            assertEquals(javafx.scene.layout.Region.USE_PREF_SIZE, cc.getMinWidth());
        }
    }

    @Test
    void cleansMarkdownBoldInTableCellsAndSetsBoldStyle() {
        String md = """
                | Item | Stock | Total |
                |---|---|---|
                | **Probe Denim Jeans** | 15 pcs | **₹13,500.00** |
                | **Total** | 15 pcs | **₹13,500.00** |
                """;
        Node root = ChatMarkdownRenderer.render(md, false);
        assertTrue(root instanceof VBox);
        ScrollPane sp = (ScrollPane) ((VBox) root).getChildren().get(0);
        VBox card = (VBox) sp.getContent();
        GridPane grid = (GridPane) card.getChildren().get(0);

        // Find cell labels and verify asterisks are stripped and bold styling applied
        boolean foundJeans = false;
        boolean foundTotal = false;
        for (Node n : grid.getChildren()) {
            if (n instanceof javafx.scene.control.Label l) {
                if ("Probe Denim Jeans".equals(l.getText())) {
                    foundJeans = true;
                    assertTrue(l.getStyle().contains("-fx-font-weight: bold;"), "Cell should have bold style");
                    assertEquals(javafx.scene.layout.Region.USE_PREF_SIZE, l.getMinWidth());
                }
                if ("Total".equals(l.getText())) {
                    foundTotal = true;
                    assertTrue(l.getStyle().contains("-fx-font-weight: bold;"));
                }
            }
        }
        assertTrue(foundJeans, "Should strip ** from **Probe Denim Jeans**");
        assertTrue(foundTotal, "Should strip ** from **Total**");
    }

    @Test
    void parseRichTextExtractsBoldAndInlineCode() {
        String text = "We have **15 pieces** of `Probe Jeans` in warehouse.";
        TextFlow flow = ChatMarkdownRenderer.parseRichText(text);

        List<Node> children = flow.getChildren();
        assertTrue(children.size() >= 4);

        // Find bold and code
        boolean foundBold = false;
        boolean foundCode = false;
        for (Node n : children) {
            if (n instanceof Text t) {
                if ("15 pieces".equals(t.getText()) && t.getFont().getStyle().contains("Bold")) {
                    foundBold = true;
                }
                // Inline code is rendered at 12.0pt (normal/bold are 13.0pt).
                // Do NOT assert the resolved family name: Font.font("Consolas")
                // resolves to a platform fallback (e.g. DejaVu on Linux) —
                // only the SIZE and the padded text are portable guarantees.
                if (t.getText().contains("Probe Jeans") && t.getFont().getSize() == 12.0) {
                    foundCode = true;
                }
            }
        }
        assertTrue(foundBold, "Should parse bold **15 pieces**");
        assertTrue(foundCode, "Should parse inline code `Probe Jeans`");
    }

    @Test
    void rendersFencedCodeBlockWithCopyAction() {
        String md = """
                ```json
                {"status": "ok", "items": 2}
                ```
                """;
        Node root = ChatMarkdownRenderer.render(md, false);
        assertTrue(root instanceof VBox);
        VBox vbox = (VBox) root;
        assertEquals(1, vbox.getChildren().size());
        assertTrue(vbox.getChildren().get(0) instanceof VBox);
    }

    @Test
    void rendersBulletAndNumberedLists() {
        String md = """
                - First point
                - Second point
                1. Item one
                2. Item two
                """;
        Node root = ChatMarkdownRenderer.render(md, false);
        assertTrue(root instanceof VBox);
        VBox vbox = (VBox) root;
        assertEquals(4, vbox.getChildren().size());
        for (Node child : vbox.getChildren()) {
            assertTrue(child instanceof HBox, "Each list item should render as an indented HBox row");
        }
    }
}
