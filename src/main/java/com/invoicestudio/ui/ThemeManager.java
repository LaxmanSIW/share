package com.invoicestudio.ui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.invoicestudio.AppDirs;
import com.invoicestudio.service.AppLog;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages theme storage, validation, persistence, and dynamic application across
 * all JavaFX scenes and native OS windows.
 */
public final class ThemeManager {

    public static final String DEFAULT_THEME_ID = "default-obsidian-gold";

    private static final String STORE_FILE_NAME = "themes.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static ThemeManager instance;

    private final Map<String, Theme> builtInThemes = new LinkedHashMap<>();
    private final Map<String, Theme> customThemes = new LinkedHashMap<>();
    private Theme activeTheme;
    private String activeThemeStylesheetUrl;

    private final List<Consumer<Theme>> changeListeners = new CopyOnWriteArrayList<>();
    private boolean windowTrackingInstalled = false;

    private ThemeManager() {
        loadBuiltInThemes();
        loadCustomThemes();
        installWindowTracker();
    }

    public static synchronized ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    /**
     * Initializes built-in signature themes.
     */
    private void loadBuiltInThemes() {
        // 1. Default System Theme (Obsidian & Gold)
        String defaultCss = readDefaultRootCss();
        Theme defaultTheme = new Theme(
            DEFAULT_THEME_ID,
            "Obsidian & Gold (Default)",
            "Signature obsidian dark surface (#0B0E13) with warm brand gold accents (#D9A13B).",
            defaultCss,
            true
        );
        builtInThemes.put(defaultTheme.getId(), defaultTheme);

        // Pre-built curated themes
        addBuiltInTheme("midnight-sapphire", "Midnight Sapphire",
            "Deep slate navy (#0B1120) with vivid ice and sapphire blue highlights (#38BDF8).",
            "/themes/midnight-sapphire.css");

        addBuiltInTheme("emerald-forest", "Emerald Forest",
            "Deep obsidian jade (#07150E) paired with luminous emerald accents (#10B981).",
            "/themes/emerald-forest.css");

        addBuiltInTheme("amethyst-night", "Amethyst Night",
            "Velvet dark violet (#100918) with radiant royal amethyst highlights (#A855F7).",
            "/themes/amethyst-night.css");

        addBuiltInTheme("crimson-forge", "Crimson Forge",
            "Smoky ruby carbon (#12090B) accented with high-energy crimson rose (#F43F5E).",
            "/themes/crimson-forge.css");

        addBuiltInTheme("nordic-cyan", "Nordic Cyan",
            "Arctic dark slate (#0A1014) with crisp electric cyan accents (#06B6D4).",
            "/themes/nordic-cyan.css");

        activeTheme = defaultTheme;
    }

    private void addBuiltInTheme(String id, String name, String desc, String resourcePath) {
        try {
            String css = readResource(resourcePath);
            if (css != null && !css.isBlank()) {
                builtInThemes.put(id, new Theme(id, name, desc, css, true));
            }
        } catch (Exception e) {
            AppLog.debug(e);
        }
    }

    /**
     * Loads custom themes and active theme selection from {@code themes.json}.
     */
    private synchronized void loadCustomThemes() {
        File storeFile = AppDirs.dataDir().resolve(STORE_FILE_NAME).toFile();
        if (!storeFile.exists()) {
            return;
        }

        try {
            ThemesStore store = MAPPER.readValue(storeFile, ThemesStore.class);
            if (store != null) {
                if (store.customThemes != null) {
                    for (Theme t : store.customThemes) {
                        if (t != null && t.getId() != null && !builtInThemes.containsKey(t.getId())) {
                            t.setBuiltIn(false);
                            customThemes.put(t.getId(), t);
                        }
                    }
                }
                if (store.activeThemeId != null) {
                    Theme found = getTheme(store.activeThemeId);
                    if (found != null) {
                        activeTheme = found;
                        if (!DEFAULT_THEME_ID.equals(found.getId())) {
                            prepareStylesheetUrl(found);
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLog.debug("Could not read themes.json: " + e.getMessage());
        }
    }

    /**
     * Persists custom themes and active theme ID to {@code themes.json}.
     */
    private synchronized void persistThemesStore() {
        try {
            File storeFile = AppDirs.dataDir().resolve(STORE_FILE_NAME).toFile();
            ThemesStore store = new ThemesStore();
            store.activeThemeId = activeTheme != null ? activeTheme.getId() : DEFAULT_THEME_ID;
            store.customThemes = new ArrayList<>(customThemes.values());
            MAPPER.writeValue(storeFile, store);
        } catch (Exception e) {
            AppLog.debug("Failed saving themes.json: " + e.getMessage());
        }
    }

    /**
     * Tracks newly created windows to automatically apply the active theme stylesheet.
     */
    private void installWindowTracker() {
        if (windowTrackingInstalled) return;
        windowTrackingInstalled = true;

        try {
            Platform.runLater(() -> {
                try {
                    Window.getWindows().addListener((ListChangeListener<Window>) change -> {
                        while (change.next()) {
                            if (change.wasAdded()) {
                                for (Window w : change.getAddedSubList()) {
                                    if (w.getScene() != null) {
                                        applyToScene(w.getScene());
                                    }
                                    w.sceneProperty().addListener((obs, oldS, newS) -> {
                                        if (newS != null) {
                                            applyToScene(newS);
                                        }
                                    });
                                }
                            }
                        }
                    });

                    // Also process any windows already open:
                    for (Window w : Window.getWindows()) {
                        if (w.getScene() != null) {
                            applyToScene(w.getScene());
                        }
                    }
                } catch (Exception ignored) {}
            });
        } catch (IllegalStateException ignored) {
            // JavaFX runtime not yet started (e.g. running outside FX thread in early unit test)
        }
    }

    /**
     * Returns an unmodifiable list of all available themes (built-in first, then custom).
     */
    public synchronized List<Theme> getAllThemes() {
        List<Theme> all = new ArrayList<>(builtInThemes.size() + customThemes.size());
        all.addAll(builtInThemes.values());
        all.addAll(customThemes.values());
        return Collections.unmodifiableList(all);
    }

    public synchronized Theme getTheme(String id) {
        if (id == null) return null;
        Theme t = builtInThemes.get(id);
        if (t != null) return t;
        return customThemes.get(id);
    }

    public synchronized Theme getActiveTheme() {
        if (activeTheme == null) {
            activeTheme = builtInThemes.get(DEFAULT_THEME_ID);
        }
        return activeTheme;
    }

    public boolean isDefaultActive() {
        return activeTheme == null || DEFAULT_THEME_ID.equals(activeTheme.getId());
    }

    /**
     * Validates a CSS block using {@link ThemeValidator}.
     */
    public ThemeValidator.ValidationResult validateTheme(String cssContent) {
        return ThemeValidator.validate(cssContent);
    }

    /**
     * Saves a new or updated custom theme.
     *
     * @throws IllegalArgumentException if the CSS is missing required tokens.
     */
    public synchronized Theme saveCustomTheme(String id, String name, String description, String cssContent) {
        ThemeValidator.ValidationResult result = validateTheme(cssContent);
        if (!result.isValid()) {
            throw new IllegalArgumentException(result.getErrorMessage());
        }

        String normalizedCss = ThemeValidator.normalizeCss(cssContent);

        if (id == null || id.isBlank() || id.startsWith("new-")) {
            id = "custom-" + System.currentTimeMillis();
        }

        if (builtInThemes.containsKey(id)) {
            throw new IllegalArgumentException("Cannot overwrite built-in theme: " + id);
        }

        Theme theme = customThemes.get(id);
        if (theme == null) {
            theme = new Theme(id, name != null ? name.trim() : "Custom Theme",
                    description != null ? description.trim() : "", normalizedCss, false);
            customThemes.put(id, theme);
        } else {
            if (name != null && !name.isBlank()) theme.setName(name.trim());
            if (description != null) theme.setDescription(description.trim());
            theme.setCssContent(normalizedCss);
            theme.setUpdatedAt(System.currentTimeMillis());
        }

        persistThemesStore();

        // If this theme is currently active, re-apply it live:
        if (activeTheme != null && id.equals(activeTheme.getId())) {
            applyTheme(theme);
        }

        return theme;
    }

    /**
     * Deletes a custom theme. Cannot delete built-in themes or the active theme.
     */
    public synchronized boolean deleteCustomTheme(String id) {
        if (id == null || builtInThemes.containsKey(id)) {
            return false;
        }
        if (activeTheme != null && id.equals(activeTheme.getId())) {
            // Switch to default before deleting active theme
            applyTheme(DEFAULT_THEME_ID);
        }

        Theme removed = customThemes.remove(id);
        if (removed != null) {
            // Delete generated theme file if exists
            try {
                Path themeFile = AppDirs.dataDir().resolve("themes").resolve("theme_" + id + ".css");
                Files.deleteIfExists(themeFile);
            } catch (Exception ignored) {}

            persistThemesStore();
            return true;
        }
        return false;
    }

    /**
     * Applies a theme by ID.
     */
    public void applyTheme(String themeId) {
        Theme target = getTheme(themeId);
        if (target != null) {
            applyTheme(target);
        }
    }

    /**
     * Dynamically applies the chosen theme to all active JavaFX scenes and OS title bars.
     */
    public synchronized void applyTheme(Theme theme) {
        if (theme == null) return;
        this.activeTheme = theme;

        String oldStylesheetUrl = this.activeThemeStylesheetUrl;
        String newStylesheetUrl = null;

        if (DEFAULT_THEME_ID.equals(theme.getId())) {
            this.activeThemeStylesheetUrl = null;
            TitleBarTheme.resetColors();
        } else {
            newStylesheetUrl = prepareStylesheetUrl(theme);
            this.activeThemeStylesheetUrl = newStylesheetUrl;
            TitleBarTheme.setColors(theme.getBgColor(), theme.getTextColor(), theme.getBorderColor());
        }

        persistThemesStore();

        // Live update all scenes on FX thread
        final String finalOldUrl = oldStylesheetUrl;
        final String finalNewUrl = newStylesheetUrl;
        final Theme finalTheme = theme;

        Runnable applyTask = () -> {
            try {
                for (Window window : Window.getWindows()) {
                    Scene scene = window.getScene();
                    if (scene != null) {
                        if (finalOldUrl != null) {
                            scene.getStylesheets().remove(finalOldUrl);
                        }
                        if (finalNewUrl != null && !scene.getStylesheets().contains(finalNewUrl)) {
                            scene.getStylesheets().add(finalNewUrl);
                        }
                    }
                    if (window instanceof Stage stage) {
                        TitleBarTheme.apply(stage);
                    }
                }
                TitleBarTheme.applyToAllProcessWindows();
            } catch (Exception e) {
                AppLog.debug("Error applying theme live: " + e.getMessage());
            }

            for (Consumer<Theme> listener : changeListeners) {
                try {
                    listener.accept(finalTheme);
                } catch (Exception e) {
                    AppLog.debug(e);
                }
            }
        };

        if (Platform.isFxApplicationThread()) {
            applyTask.run();
        } else {
            try {
                Platform.runLater(applyTask);
            } catch (IllegalStateException ignored) {
                // FX toolkit not running
            }
        }
    }

    /**
     * Applies the current active theme stylesheet to an individual scene.
     * Useful for newly instantiated dialogs or secondary views.
     */
    public void applyToScene(Scene scene) {
        if (scene == null) return;
        String url = this.activeThemeStylesheetUrl;
        if (url != null && !scene.getStylesheets().contains(url)) {
            scene.getStylesheets().add(url);
        }
        if (scene.getWindow() instanceof Stage stage) {
            TitleBarTheme.apply(stage);
        }
    }

    /**
     * Generates a CSS file for the given theme and returns its URL external form.
     */
    private String prepareStylesheetUrl(Theme theme) {
        try {
            Path themesDir = AppDirs.dataDir().resolve("themes");
            Files.createDirectories(themesDir);

            // Use versioned/timestamped file name to prevent JavaFX stylesheet URL caching
            Path cssFile = themesDir.resolve("theme_" + theme.getId() + "_" + theme.getUpdatedAt() + ".css");
            Files.writeString(cssFile, theme.getCssContent(), StandardCharsets.UTF_8);

            return cssFile.toUri().toURL().toExternalForm();
        } catch (Exception e) {
            AppLog.debug("Failed writing theme css file: " + e.getMessage());
            return null;
        }
    }

    public void addThemeChangeListener(Consumer<Theme> listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    public void removeThemeChangeListener(Consumer<Theme> listener) {
        changeListeners.remove(listener);
    }

    /**
     * Extracts and returns the full canonical {@code .root { ... }} block from {@code /css/globalfile.css}.
     */
    public static String readDefaultRootCss() {
        try (InputStream in = ThemeManager.class.getResourceAsStream("/css/globalfile.css")) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                String content = sb.toString();
                Matcher m = Pattern.compile("(\\.root\\s*\\{[^}]+\\})").matcher(content);
                if (m.find()) {
                    return m.group(1);
                }
            }
        } catch (Exception ignored) {}
        return ".root {\n}\n";
    }

    private static String readResource(String path) {
        try (InputStream in = ThemeManager.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ThemesStore {
        public String activeThemeId = DEFAULT_THEME_ID;
        public List<Theme> customThemes = new ArrayList<>();
    }
}
