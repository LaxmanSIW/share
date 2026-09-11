package com.invoicestudio.ui;

import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.TemplateElement;
import com.invoicestudio.ui.views.TemplateDesigner;
import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateDesignerVectorEnhancementsTest {

    @Test
    void testIconHelperCurveAndAnchorIcons() {
        assertNotNull(IconHelper.getIcon("shape-curve", 16.0, "#FFFFFF"), "shape-curve icon should exist");
        assertNotNull(IconHelper.getIcon("shape-anchor", 16.0, "#FFFFFF"), "shape-anchor icon should exist");
        assertNotNull(IconHelper.getIcon(IconHelper.ICON_CURVE, 16.0, "#FFFFFF"), "ICON_CURVE constant should be valid");
        assertNotNull(IconHelper.getIcon(IconHelper.ICON_ANCHOR, 16.0, "#FFFFFF"), "ICON_ANCHOR constant should be valid");
    }

    @Test
    void testGenerateSmoothBezierPathClosed() {
        List<Point2D> points = List.of(
                new Point2D(0, 0),
                new Point2D(50, 0),
                new Point2D(50, 50),
                new Point2D(0, 50)
        );

        String path = TemplateDesigner.generateSmoothBezierPath(points, 0.5, true);
        assertNotNull(path);
        assertTrue(path.startsWith("M 0.00,0.00"), "Path should start with M 0.00,0.00");
        assertTrue(path.contains("C "), "Path should contain cubic Bezier 'C' commands");
        assertTrue(path.endsWith("Z"), "Closed path should end with 'Z'");
    }

    @Test
    void testGenerateSmoothBezierPathOpen() {
        List<Point2D> points = List.of(
                new Point2D(10, 10),
                new Point2D(30, 40),
                new Point2D(60, 20)
        );

        String path = TemplateDesigner.generateSmoothBezierPath(points, 0.5, false);
        assertNotNull(path);
        assertTrue(path.startsWith("M 10.00,10.00"));
        assertTrue(path.contains("C "));
        assertFalse(path.endsWith("Z"), "Open path should not end with 'Z'");
    }

    @Test
    void testGenerateSmoothBezierPathEdgeCases() {
        assertEquals("", TemplateDesigner.generateSmoothBezierPath(null, 0.5, true));
        assertEquals("", TemplateDesigner.generateSmoothBezierPath(new ArrayList<>(), 0.5, true));

        List<Point2D> single = List.of(new Point2D(15, 25));
        assertEquals("M 15.00,25.00", TemplateDesigner.generateSmoothBezierPath(single, 0.5, true));

        List<Point2D> pair = List.of(new Point2D(0, 0), new Point2D(20, 20));
        assertEquals("M 0.00,0.00 L 20.00,20.00 Z", TemplateDesigner.generateSmoothBezierPath(pair, 0.5, true));
        assertEquals("M 0.00,0.00 L 20.00,20.00", TemplateDesigner.generateSmoothBezierPath(pair, 0.5, false));
    }

    @Test
    void testExtractPointsFromSvgPath() {
        String svgD = "M 10.5 20.5 L 40.0 50.0 C 45.0 55.0 60.0 65.0 70.0 80.0 Z";
        List<Point2D> pts = TemplateDesigner.extractPointsFromSvgPath(svgD);

        assertNotNull(pts);
        assertEquals(3, pts.size(), "Should extract start point, line endpoint, and curve endpoint");
        assertEquals(10.5, pts.get(0).getX(), 0.001);
        assertEquals(20.5, pts.get(0).getY(), 0.001);
        assertEquals(40.0, pts.get(1).getX(), 0.001);
        assertEquals(50.0, pts.get(1).getY(), 0.001);
        assertEquals(70.0, pts.get(2).getX(), 0.001);
        assertEquals(80.0, pts.get(2).getY(), 0.001);
    }

    @Test
    void testParseElementVerticesFromPointsString() {
        TemplateElement el = new TemplateElement();
        el.setType(ElementType.POLYGON);
        el.setPoints("0,0 25.5,50.0 60,10.2");

        List<Point2D> vertices = TemplateDesigner.parseElementVertices(el);
        assertEquals(3, vertices.size());
        assertEquals(0.0, vertices.get(0).getX(), 0.001);
        assertEquals(0.0, vertices.get(0).getY(), 0.001);
        assertEquals(25.5, vertices.get(1).getX(), 0.001);
        assertEquals(50.0, vertices.get(1).getY(), 0.001);
        assertEquals(60.0, vertices.get(2).getX(), 0.001);
        assertEquals(10.2, vertices.get(2).getY(), 0.001);
    }

    @Test
    void testParseElementVerticesFromPathData() {
        TemplateElement el = new TemplateElement();
        el.setType(ElementType.PATH);
        el.setPathData("M 5 5 L 15 25 L 35 5 Z");

        List<Point2D> vertices = TemplateDesigner.parseElementVertices(el);
        assertEquals(3, vertices.size());
        assertEquals(5.0, vertices.get(0).getX(), 0.001);
        assertEquals(5.0, vertices.get(0).getY(), 0.001);
        assertEquals(15.0, vertices.get(1).getX(), 0.001);
        assertEquals(25.0, vertices.get(1).getY(), 0.001);
        assertEquals(35.0, vertices.get(2).getX(), 0.001);
        assertEquals(5.0, vertices.get(2).getY(), 0.001);
    }
}
