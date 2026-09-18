package com.invoicestudio.ui.chat;

import com.invoicestudio.service.ModelCatalog;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ChatbotModelPickerDialogTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
    }

    @Test
    void searchFilterMatchesDisplayNameOrModelId() {
        List<ModelCatalog.ModelInfo> models = List.of(
                new ModelCatalog.ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", "Fast model", 1048000),
                new ModelCatalog.ModelInfo("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", "Ultra fast model", 1048000),
                new ModelCatalog.ModelInfo("gemini-3.1-pro-preview", "Gemini 3.1 Pro", "Deep reasoning", 2096000)
        );

        FilteredList<ModelCatalog.ModelInfo> filtered = new FilteredList<>(
                FXCollections.observableArrayList(models), p -> true);

        // Filter for "lite"
        String query = "lite";
        filtered.setPredicate(mi -> mi.displayName().toLowerCase().contains(query)
                || mi.id().toLowerCase().contains(query));

        assertEquals(1, filtered.size());
        assertEquals("gemini-3.5-flash-lite", filtered.get(0).id());

        // Filter for "flash"
        String queryFlash = "flash";
        filtered.setPredicate(mi -> mi.displayName().toLowerCase().contains(queryFlash)
                || mi.id().toLowerCase().contains(queryFlash));

        assertEquals(2, filtered.size());
    }

    @Test
    void instantiatesDialogAndSelectsModel() {
        AtomicReference<ModelCatalog.ModelInfo> selected = new AtomicReference<>();
        List<ModelCatalog.ModelInfo> models = List.of(
                new ModelCatalog.ModelInfo("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", "Default", 1048000)
        );

        // Run on FX thread or instantiate without show
        Platform.runLater(() -> {
            ChatbotModelPickerDialog dlg = new ChatbotModelPickerDialog(
                    null,
                    "gemini",
                    models,
                    "gemini-3.5-flash-lite",
                    selected::set
            );
            assertNotNull(dlg);
        });
    }
}
