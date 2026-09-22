package com.invoicestudio.ui.views;

import com.invoicestudio.model.Template;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Templates Gallery "Export" dropdown is the ONLY way to download one or
 * several templates, so its multi-select contract is pinned here:
 * ticked boxes stay open (hideOnClick off), the count label follows the ticks,
 * and each action hands the callback EXACTLY the templates the user picked.
 *
 * Runs on the real FX thread with the app's own theme classes — the same
 * harness style as {@code CopyButtonFactoryTest}.
 */
class TemplatesExportMenuTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyRunning) {
            // Toolkit already initialized by another test class
        }
    }

    private static void fx(Runnable body) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "FX task timed out");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        }
        if (err.get() != null) {
            if (err.get() instanceof RuntimeException re) throw re;
            if (err.get() instanceof Error e) throw e;
            throw new RuntimeException(err.get());
        }
    }

    private static Template tpl(String id, String name) {
        Template t = new Template();
        t.setId(id);
        t.setName(name);
        return t;
    }

    private static List<CheckBox> checkBoxes(MenuButton menu) {
        List<CheckBox> out = new ArrayList<>();
        for (MenuItem item : menu.getItems()) {
            if (item instanceof CustomMenuItem custom && custom.getContent() instanceof CheckBox cb) {
                out.add(cb);
            }
        }
        return out;
    }

    private static MenuItem itemStartingWith(MenuButton menu, String prefix) {
        return menu.getItems().stream()
                .filter(i -> i.getText() != null && i.getText().startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no menu item starting with: " + prefix));
    }

    @Test
    void emptyLibraryShowsOneDisabledHint() {
        fx(() -> {
            MenuButton menu = TemplatesView.buildExportMenu(List.of(), list -> fail("must not download"));
            assertEquals(1, menu.getItems().size());
            assertTrue(menu.getItems().get(0).isDisable(), "hint item must be non-clickable");
        });
    }

    @Test
    void menuIsThemedAndStaysOpenWhileSelecting() {
        fx(() -> {
            MenuButton menu = TemplatesView.buildExportMenu(
                    List.of(tpl("tpl_1", "Alpha"), tpl("tpl_2", "Beta")), list -> {});

            assertEquals("Export", menu.getText());
            assertTrue(menu.getStyleClass().contains("button-secondary"),
                    "export dropdown must use the app theme button style");
            assertTrue(menu.getStyleClass().contains("button-sm"));

            List<CheckBox> boxes = checkBoxes(menu);
            assertEquals(2, boxes.size(), "one themed checkbox per saved template");
            assertEquals("Alpha", boxes.get(0).getText());

            for (MenuItem item : menu.getItems()) {
                if (item instanceof CustomMenuItem custom && custom.getContent() instanceof CheckBox) {
                    assertFalse(custom.isHideOnClick(),
                            "the dropdown must stay open so multiple templates can be ticked");
                }
            }
        });
    }

    @Test
    void selectedCountFollowsTheTicksAndDownloadsOnlyThose() {
        fx(() -> {
            AtomicReference<List<Template>> downloaded = new AtomicReference<>();
            MenuButton menu = TemplatesView.buildExportMenu(
                    List.of(tpl("tpl_1", "Alpha"), tpl("tpl_2", "Beta"), tpl("tpl_3", "Gamma")),
                    downloaded::set);

            MenuItem exportSelected = itemStartingWith(menu, "Download Selected");
            assertTrue(exportSelected.isDisable(), "nothing ticked → nothing to download");
            assertEquals("Download Selected (0)", exportSelected.getText());

            List<CheckBox> boxes = checkBoxes(menu);
            boxes.get(0).setSelected(true);
            boxes.get(2).setSelected(true);

            assertEquals("Download Selected (2)", exportSelected.getText());
            assertFalse(exportSelected.isDisable());

            exportSelected.fire();
            List<String> names = downloaded.get().stream().map(Template::getName).toList();
            assertEquals(List.of("Alpha", "Gamma"), names, "only the ticked templates may be exported");
        });
    }

    @Test
    void untickingRemovesTemplateAndDisablesAction() {
        fx(() -> {
            AtomicReference<List<Template>> downloaded = new AtomicReference<>();
            MenuButton menu = TemplatesView.buildExportMenu(
                    List.of(tpl("tpl_1", "Alpha"), tpl("tpl_2", "Beta")), downloaded::set);

            List<CheckBox> boxes = checkBoxes(menu);
            boxes.get(0).setSelected(true);
            boxes.get(0).setSelected(false);

            MenuItem exportSelected = itemStartingWith(menu, "Download Selected");
            assertEquals("Download Selected (0)", exportSelected.getText());
            assertTrue(exportSelected.isDisable());
            assertNull(downloaded.get(), "nothing may be handed to the downloader");
        });
    }

    @Test
    void selectAllAndClearDriveEveryCheckbox() {
        fx(() -> {
            MenuButton menu = TemplatesView.buildExportMenu(
                    List.of(tpl("tpl_1", "Alpha"), tpl("tpl_2", "Beta"), tpl("tpl_3", "Gamma")), list -> {});

            Button selectAll = findButton(menu, "Select all");
            Button clear = findButton(menu, "Clear");

            selectAll.fire();
            List<CheckBox> boxes = checkBoxes(menu);
            assertTrue(boxes.stream().allMatch(CheckBox::isSelected));
            assertEquals("Download Selected (3)", itemStartingWith(menu, "Download Selected").getText());

            clear.fire();
            assertTrue(boxes.stream().noneMatch(CheckBox::isSelected));
            assertTrue(itemStartingWith(menu, "Download Selected").isDisable());
        });
    }

    @Test
    void downloadAllPassesEveryTemplateInOrder() {
        fx(() -> {
            AtomicReference<List<Template>> downloaded = new AtomicReference<>();
            MenuButton menu = TemplatesView.buildExportMenu(
                    List.of(tpl("tpl_1", "Alpha"), tpl("tpl_2", "Beta")), downloaded::set);

            itemStartingWith(menu, "Download All").fire();
            assertEquals(List.of("Alpha", "Beta"),
                    downloaded.get().stream().map(Template::getName).toList());
        });
    }

    private static Button findButton(MenuButton menu, String text) {
        for (MenuItem item : menu.getItems()) {
            if (item instanceof CustomMenuItem custom) {
                if (custom.getContent() instanceof javafx.scene.layout.Pane pane) {
                    for (javafx.scene.Node node : pane.getChildrenUnmodifiable()) {
                        if (node instanceof Button b && text.equals(b.getText())) return b;
                    }
                }
            }
        }
        throw new AssertionError("button not found: " + text);
    }
}
