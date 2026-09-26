package com.invoicestudio.ui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates theme CSS definitions against InvoiceStudio's design system tokens.
 * A valid theme must define every required variable in its {@code .root} block.
 */
public final class ThemeValidator {

    /**
     * Canonical list of required tokens in InvoiceStudio's {@code .root} scope.
     * Guaranteed to match globalfile.css.
     */
    public static final List<String> CANONICAL_REQUIRED_TOKENS = List.of(
        // 1. Core Surfaces & Backgrounds
        "-color-bg-deep",
        "-color-bg",
        "-color-bg-subtle",
        "-color-bg-alt",
        "-color-bg-glass",
        "-color-surface",
        "-color-surface-raised",
        "-color-surface-elevated",
        "-color-surface-hover",
        "-color-surface-active",
        "-color-surface-hover-subtle",
        "-color-canvas-sheet",

        // 2. Borders & Focus
        "-color-border-subtle",
        "-color-border",
        "-color-border-strong",
        "-color-border-muted",
        "-color-border-focus",
        "-fx-focus-color",
        "-fx-faint-focus-color",
        "-fx-selection-bar",
        "-fx-selection-bar-non-focused",
        "-fx-accent",

        // 3. Typography & Text
        "-color-text-pure-white",
        "-color-text-bright",
        "-color-text",
        "-color-text-light",
        "-color-text-secondary",
        "-color-text-tertiary",
        "-color-text-inverse",
        "-color-text-black",

        // 4. Brand Accent
        "-color-accent",
        "-color-accent-text",
        "-color-accent-hover",
        "-color-accent-bright",
        "-color-accent-pressed",
        "-color-accent-metallic",
        "-color-accent-deep",
        "-color-accent-subtle",
        "-color-accent-subtle-light",
        "-color-accent-badge",
        "-color-accent-glow",
        "-color-accent-glow-strong",
        "-color-accent-border-bright",

        // 5. Semantic Success
        "-color-success",
        "-color-success-text",
        "-color-success-bright",
        "-color-success-subtle",
        "-color-success-border",

        // 6. Semantic Warning
        "-color-warning",
        "-color-warning-dark",
        "-color-warning-deep",
        "-color-warning-subtle",
        "-color-warning-border",
        "-color-fg",

        // 7. Semantic Danger / Error
        "-color-error",
        "-color-error-text",
        "-color-error-light",
        "-color-error-dark",
        "-color-error-bg-dark",
        "-color-error-bg-hover",
        "-color-error-subtle",
        "-color-error-subtle-hover",
        "-color-error-border",
        "-color-error-glow",

        // 8. Semantic Info
        "-color-info",
        "-color-info-accent",
        "-color-info-subtle",
        "-color-info-border",
        "-color-text-selection-fill",

        // 9. Shadows & Overlays
        "-color-shadow-subtle",
        "-color-shadow-medium",
        "-color-shadow-deep",
        "-color-shadow-heavy",
        "-color-white-subtle",
        "-color-white-border",
        "-color-scrollbar-thumb",
        "-color-scrollbar-thumb-hover",

        // 10. Gradients
        "-gradient-accent",
        "-gradient-accent-hover",
        "-gradient-accent-active",
        "-gradient-accent-bright",
        "-gradient-metallic",
        "-gradient-metallic-hover",

        // 11. Fonts
        "-font-family-base",
        "-font-family-mono"
    );

    private static final Pattern VAR_PATTERN = Pattern.compile("(-[-\\w]+)\\s*:\\s*([^;]+);");
    private static Set<String> cachedRequiredTokens = null;

    private ThemeValidator() {}

    /**
     * Strips CSS multi-line and single-line comments.
     */
    public static String stripComments(String css) {
        if (css == null) return "";
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    /**
     * Returns the authoritative set of required CSS variables.
     * Attempts to read directly from {@code /css/globalfile.css}, falling back to
     * {@link #CANONICAL_REQUIRED_TOKENS}.
     */
    public static synchronized Set<String> getRequiredTokens() {
        if (cachedRequiredTokens != null) {
            return cachedRequiredTokens;
        }

        Set<String> tokens = new LinkedHashSet<>();
        try (InputStream in = ThemeValidator.class.getResourceAsStream("/css/globalfile.css")) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                String content = sb.toString();
                Matcher rootMatcher = Pattern.compile("\\.root\\s*\\{([^}]+)\\}").matcher(content);
                if (rootMatcher.find()) {
                    String rootBody = stripComments(rootMatcher.group(1));
                    Matcher vm = VAR_PATTERN.matcher(rootBody);
                    while (vm.find()) {
                        String varName = vm.group(1).trim();
                        tokens.add(varName);
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to CANONICAL_REQUIRED_TOKENS
        }

        if (tokens.isEmpty()) {
            tokens.addAll(CANONICAL_REQUIRED_TOKENS);
        }
        cachedRequiredTokens = Collections.unmodifiableSet(tokens);
        return cachedRequiredTokens;
    }

    /**
     * Validates a CSS block. Checks whether every single required variable exists.
     *
     * @param cssContent The CSS block (either full {@code .root { ... }} or key-value list).
     * @return ValidationResult indicating success or list of missing variables.
     */
    public static ValidationResult validate(String cssContent) {
        if (cssContent == null || cssContent.trim().isEmpty()) {
            return new ValidationResult(false, List.copyOf(getRequiredTokens()), Collections.emptySet(),
                    "CSS content is empty. Please provide a .root { ... } definition.");
        }

        Set<String> presentVars = extractVariables(cssContent);
        Set<String> required = getRequiredTokens();
        List<String> missing = new ArrayList<>();

        for (String req : required) {
            if (!presentVars.contains(req)) {
                missing.add(req);
            }
        }

        if (missing.isEmpty()) {
            return new ValidationResult(true, Collections.emptyList(), presentVars, null);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Validation Error: ").append(missing.size())
              .append(" required variable(s) are missing from .root:\n");
            for (String m : missing) {
                sb.append("  • ").append(m).append("\n");
            }
            return new ValidationResult(false, missing, presentVars, sb.toString().trim());
        }
    }

    /**
     * Extracts all CSS variable names declared in the input text.
     */
    public static Set<String> extractVariables(String cssContent) {
        Set<String> found = new LinkedHashSet<>();
        if (cssContent == null) return found;

        String clean = stripComments(cssContent);
        Matcher m = VAR_PATTERN.matcher(clean);
        while (m.find()) {
            found.add(m.group(1).trim());
        }
        return found;
    }

    /**
     * Normalizes a theme's CSS content to ensure it is cleanly enclosed in {@code .root { ... }}.
     */
    public static String normalizeCss(String cssContent) {
        if (cssContent == null) return ".root {\n}\n";
        String trimmed = cssContent.trim();
        if (trimmed.startsWith(".root") && trimmed.endsWith("}")) {
            return trimmed;
        }
        // If user pasted just lines of variables without .root wrapping:
        return ".root {\n" + indent(trimmed, "    ") + "\n}\n";
    }

    private static String indent(String text, String indent) {
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(indent).append(l.trim()).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Represents the outcome of validating a theme's CSS.
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final List<String> missingVariables;
        private final Set<String> presentVariables;
        private final String errorMessage;

        public ValidationResult(boolean valid, List<String> missingVariables,
                                Set<String> presentVariables, String errorMessage) {
            this.valid = valid;
            this.missingVariables = missingVariables != null ? Collections.unmodifiableList(missingVariables) : Collections.emptyList();
            this.presentVariables = presentVariables != null ? Collections.unmodifiableSet(presentVariables) : Collections.emptySet();
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getMissingVariables() {
            return missingVariables;
        }

        public Set<String> getPresentVariables() {
            return presentVariables;
        }

        public int getTotalRequired() {
            return ThemeValidator.getRequiredTokens().size();
        }

        public int getPresentCount() {
            return presentVariables.size();
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
