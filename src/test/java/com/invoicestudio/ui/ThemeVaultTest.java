package com.invoicestudio.ui;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThemeVaultTest {

    @Test
    void canonicalTokensContainAllEssentialVariables() {
        var tokens = ThemeValidator.getRequiredTokens();
        assertTrue(tokens.contains("-color-bg"));
        assertTrue(tokens.contains("-color-surface"));
        assertTrue(tokens.contains("-color-accent"));
        assertTrue(tokens.contains("-color-border"));
        assertTrue(tokens.contains("-color-text"));
        assertTrue(tokens.contains("-color-success"));
        assertTrue(tokens.contains("-color-error"));
        assertTrue(tokens.contains("-gradient-accent"));
        assertTrue(tokens.size() >= 80, "Expected at least 80 tokens, found: " + tokens.size());
    }

    @Test
    void defaultRootCssPassesValidation() {
        String defaultCss = ThemeManager.readDefaultRootCss();
        assertNotNull(defaultCss);
        assertFalse(defaultCss.isBlank());

        ThemeValidator.ValidationResult result = ThemeValidator.validate(defaultCss);
        assertTrue(result.isValid(), "Default root CSS should be 100% valid. Missing: " + result.getMissingVariables());
        assertTrue(result.getMissingVariables().isEmpty());
    }

    @Test
    void builtInThemesAreAllValidWithZeroMissingTokens() {
        ThemeManager manager = ThemeManager.getInstance();
        List<Theme> themes = manager.getAllThemes();
        assertTrue(themes.size() >= 6, "Expected at least 6 built-in themes");

        for (Theme theme : themes) {
            if (theme.isBuiltIn()) {
                ThemeValidator.ValidationResult result = ThemeValidator.validate(theme.getCssContent());
                assertTrue(result.isValid(), "Built-in theme '" + theme.getName() + "' is missing tokens: " + result.getMissingVariables());
            }
        }
    }

    @Test
    void validatorDetectsMissingVariablesAccurately() {
        // Valid block with one variable stripped
        String validCss = ThemeManager.readDefaultRootCss();
        String defectiveCss = validCss.replaceAll("-color-accent\\s*:[^;]+;", "");

        ThemeValidator.ValidationResult result = ThemeValidator.validate(defectiveCss);
        assertFalse(result.isValid(), "Validation should fail when -color-accent is missing");
        assertTrue(result.getMissingVariables().contains("-color-accent"));
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("-color-accent"));
    }

    @Test
    void rejectsEmptyOrIncompleteCssOnSave() {
        ThemeManager manager = ThemeManager.getInstance();
        assertThrows(IllegalArgumentException.class, () -> {
            manager.saveCustomTheme("invalid-theme", "Invalid", "Test", ".root { -color-bg: #000; }");
        });
    }

    @Test
    void colorExtractionExtractsExpectedColors() {
        ThemeManager manager = ThemeManager.getInstance();
        Theme defaultTheme = manager.getTheme(ThemeManager.DEFAULT_THEME_ID);
        assertNotNull(defaultTheme);

        assertEquals("#0B0E13", defaultTheme.getBgColor().toUpperCase());
        assertEquals("#D9A13B", defaultTheme.getAccentColor().toUpperCase());
        assertEquals("#232B38", defaultTheme.getBorderColor().toUpperCase());
    }
}
