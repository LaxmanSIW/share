package com.invoicestudio.ui;

import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.TemplateElement;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TemplateDesignerEnhancementsTest {

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }
    }

    @Test
    void testIconHelperMenuIcons() {
        String[] testIcons = {
                IconHelper.ICON_SHAPES,
                IconHelper.ICON_RECT,
                IconHelper.ICON_ROUND_RECT,
                IconHelper.ICON_CIRCLE,
                IconHelper.ICON_ELLIPSE,
                IconHelper.ICON_LINE_H,
                IconHelper.ICON_LINE_V,
                IconHelper.ICON_ARROW,
                IconHelper.ICON_STAR,
                IconHelper.ICON_POLYGON,
                IconHelper.ICON_ARC,
                IconHelper.ICON_PATH,
                IconHelper.ICON_PEN,
                IconHelper.ICON_DIVIDER,
                IconHelper.ICON_SIGNATURE,
                IconHelper.ICON_WATERMARK,
                IconHelper.ICON_MEDIA,
                IconHelper.ICON_MEDIA_IMAGE,
                IconHelper.ICON_MEDIA_SVG,
                IconHelper.ICON_CODE,
                IconHelper.ICON_CODE_QR,
                IconHelper.ICON_CODE_BARCODE
        };

        for (String iconName : testIcons) {
            Node iconNode = IconHelper.getMenuIcon(iconName, "#94A3B8");
            assertNotNull(iconNode, "Menu icon should not be null for: " + iconName);
            assertTrue(iconNode instanceof StackPane, "Menu icon should be a StackPane for: " + iconName);
            StackPane iconPane = (StackPane) iconNode;
            assertEquals(18.0, iconPane.getPrefWidth());
            assertEquals(18.0, iconPane.getPrefHeight());
            assertFalse(iconPane.getChildren().isEmpty(), "Icon pane should contain a child node for: " + iconName);
            Node grpNode = iconPane.getChildren().get(0);
            assertTrue(grpNode instanceof javafx.scene.Group, "Child should be a Group for: " + iconName);
            javafx.scene.Group grp = (javafx.scene.Group) grpNode;
            assertFalse(grp.getChildren().isEmpty(), "Group should contain an SVGPath for: " + iconName);
            Node child = grp.getChildren().get(0);
            assertTrue(child instanceof SVGPath, "Child should be an SVGPath for: " + iconName);
            SVGPath svg = (SVGPath) child;
            assertNotNull(svg.getContent(), "SVG path content should not be null for: " + iconName);
            assertFalse(svg.getContent().isBlank(), "SVG path content should not be blank for: " + iconName);
        }
    }

    @Test
    void testRegularPolygonPointGeneration() {
        double wMm = 60.0;
        double hMm = 60.0;

        int[] sidesList = {3, 4, 5, 6, 8, 12};
        for (int sides : sidesList) {
            String pointsStr = generateTestPolygonPoints(sides, wMm, hMm);
            assertNotNull(pointsStr);
            String[] tokens = pointsStr.trim().split("\\s+");
            assertEquals(sides, tokens.length, "Number of generated vertices must match sides for " + sides + "-gon");

            for (String token : tokens) {
                String[] xy = token.split(",");
                assertEquals(2, xy.length, "Each vertex must have X and Y coordinates");
                double x = Double.parseDouble(xy[0]);
                double y = Double.parseDouble(xy[1]);
                assertTrue(x >= -0.1 && x <= wMm + 0.1, "Vertex X " + x + " must stay within [0, " + wMm + "]");
                assertTrue(y >= -0.1 && y <= hMm + 0.1, "Vertex Y " + y + " must stay within [0, " + hMm + "]");
            }
        }
    }

    @Test
    void testElementRotationAndTableProperties() {
        TemplateElement el = new TemplateElement();
        el.setType(ElementType.TABLE);
        el.setW(180.0);
        el.setH(60.0);
        el.setX(15.0);
        el.setY(50.0);

        // Rotation
        el.setRotation(45.0);
        assertEquals(45.0, el.getRotation());
        el.setRotation(360.0);
        assertEquals(360.0, el.getRotation());

        // Table styles and borders
        el.setBorderStyle("grid");
        assertEquals("grid", el.getBorderStyle());
        el.setHeaderBg("#3B82F6");
        assertEquals("#3B82F6", el.getHeaderBg());
        el.setRowHeight(8.0);
        assertEquals(8.0, el.getRowHeight());
        el.setFontSize(9.0);
        assertEquals(9.0, el.getFontSize());
        el.setTableBorderWidth(0.3);
        assertEquals(0.3, el.getTableBorderWidth());
    }

    private String generateTestPolygonPoints(int sides, double wMm, double hMm) {
        if (sides < 3) sides = 3;
        StringBuilder sb = new StringBuilder();
        double cx = wMm / 2.0;
        double cy = hMm / 2.0;
        double rx = wMm / 2.0;
        double ry = hMm / 2.0;
        for (int i = 0; i < sides; i++) {
            double angle = -Math.PI / 2.0 + (2.0 * Math.PI * i / sides);
            double x = cx + rx * Math.cos(angle);
            double y = cy + ry * Math.sin(angle);
            if (i > 0) sb.append(" ");
            sb.append(String.format(Locale.US, "%.1f,%.1f", x, y));
        }
        return sb.toString();
    }
}
