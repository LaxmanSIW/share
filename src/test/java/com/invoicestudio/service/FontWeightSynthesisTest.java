package com.invoicestudio.service;

import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.TemplateElement;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Weights above what a font family actually ships (800/900 on a Regular/Bold
 * only family) used to be invisible because JavaFX resolves to the nearest REAL
 * face. The renderer now synthesizes the missing weight, progressively.
 */
class FontWeightSynthesisTest {

    private static final String FAMILY = "Segoe UI";

    @BeforeAll
    static void initFx() {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await();
        } catch (IllegalStateException alreadyRunning) {
            // toolkit already up
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static TemplateElement textEl(int weight) {
        TemplateElement el = new TemplateElement();
        el.setType(ElementType.TEXT);
        el.setText("NERROW");
        el.setFontFamily(FAMILY);
        el.setFontSize(12);
        el.setLineHeight(1.25);
        el.setFontWeight(weight);
        return el;
    }

    private static Node render(int weight) throws Exception {
        AtomicReference<Node> out = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            out.set(DesignObjectRenderer.render(textEl(weight), null, 120, 40));
            latch.countDown();
        });
        latch.await();
        return out.get();
    }

    @Test
    void lightWeightsNeverThicken() {
        assertEquals(0, DesignObjectRenderer.syntheticExtraCopies(FAMILY, 400));
        assertEquals(0, DesignObjectRenderer.syntheticExtraCopies(FAMILY, 600));
    }

    @Test
    void heavierWeightsThickenMoreThanBold() {
        int bold = DesignObjectRenderer.syntheticExtraCopies(FAMILY, 700);
        int extra = DesignObjectRenderer.syntheticExtraCopies(FAMILY, 800);
        int black = DesignObjectRenderer.syntheticExtraCopies(FAMILY, 900);
        assertTrue(extra >= bold, "800 must be at least as heavy as 700");
        assertTrue(black >= extra, "900 must be at least as heavy as 800");
        // If the family genuinely reaches 900, no synthesis is required.
        if (DesignObjectRenderer.resolveAvailableWeight(FAMILY, 900) >= 900) {
            assertEquals(0, black);
        } else {
            assertTrue(black > bold || extra > bold,
                    "an unreachable 900 must be visibly heavier than 700");
        }
    }

    @Test
    void regularTextRendersAsASingleLabel() throws Exception {
        Node n = render(400);
        assertInstanceOf(Label.class, n, "no synthesis for regular weight");
    }

    @Test
    void unreachableHeavyWeightRendersAsStackedCopies() throws Exception {
        int copies = DesignObjectRenderer.syntheticExtraCopies(FAMILY, 900);
        Node n = render(900);
        if (copies > 0) {
            Group g = assertInstanceOf(Group.class, n, "synthesized bold must stack copies");
            assertEquals(copies + 1, g.getChildren().size(), "label plus one copy per pass");
        } else {
            assertInstanceOf(Label.class, n);
        }
    }
}
