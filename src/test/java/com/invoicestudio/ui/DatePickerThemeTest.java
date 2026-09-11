package com.invoicestudio.ui;

import javafx.application.Platform;
import javafx.scene.control.DatePicker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DatePickerThemeTest {

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }
    }

    @Test
    void testDatePickerFormattingAndParsing() {
        DatePicker picker = UiTheme.datePicker(LocalDate.of(2026, 9, 11), "dd/mm/yyyy");
        assertNotNull(picker);
        assertNotNull(picker.getConverter());
        assertEquals("dd/mm/yyyy", picker.getPromptText());

        // Test display formatting: dd/MM/yyyy
        assertEquals("11/09/2026", picker.getConverter().toString(LocalDate.of(2026, 9, 11)));
        assertEquals("", picker.getConverter().toString(null));

        // Test parsing multiple standard patterns
        assertEquals(LocalDate.of(2026, 9, 11), picker.getConverter().fromString("11/09/2026"));
        assertEquals(LocalDate.of(2026, 9, 11), picker.getConverter().fromString("11-09-2026"));
        assertEquals(LocalDate.of(2026, 9, 11), picker.getConverter().fromString("2026-09-11"));

        // Test safe fallbacks without throwing exception
        assertNull(picker.getConverter().fromString(""));
        assertNull(picker.getConverter().fromString("   "));
        assertNull(picker.getConverter().fromString(null));
        assertNull(picker.getConverter().fromString("invalid-date-string"));
    }
}
