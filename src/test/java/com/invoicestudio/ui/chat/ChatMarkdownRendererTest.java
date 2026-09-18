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
                if (t.getText().contains("Probe Jeans") && t.getFont().getName().contains("Consolas")) {
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
