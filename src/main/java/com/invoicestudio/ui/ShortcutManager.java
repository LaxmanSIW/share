package com.invoicestudio.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.service.AppLog;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Central keyboard-shortcut registry (user-rebindable, persisted).
 *
 * - Every application action is a {@link ShortcutAction}.
 * - Bindings are {@code action-id -> combo string} in JSON at
 *   {@code AppDirs.data.../shortcuts.json}; missing entries fall back to the
 *   action's default combo.
 * - {@link #validate(String, String)} rejects combos that are already bound,
 *   reserved for window-level behavior (Tab, Alt+Tab style OS handling), or
 *   single unmodified letters that would fire while typing.
 * - {@link #installAll(Scene)} pushes the current bindings into the scene's
 *   accelerator map; call after any rebinding to apply live.
 */
public final class ShortcutManager {

    /** One rebindable application action. */
    public record ShortcutAction(String id, String group, String label, String defaultCombo,
                                 Runnable action, boolean worksWithoutData) {}

    private static final Map<String, ShortcutAction> ACTIONS = new LinkedHashMap<>();
    private static final Map<String, String> CUSTOM = new LinkedHashMap<>(); // actionId -> combo
    private static Path storeFile;
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Combos that must never be user-bound (window/OS semantics). */
    private static final Set<String> RESERVED = Set.of(
            "Alt+Tab", "Alt+F4", "Ctrl+Alt+Delete", "Meta", "Meta+Tab");

    private ShortcutManager() {}

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    /** Registers one action with its default binding. Idempotent by id. */
    public static void register(String id, String group, String label, String defaultCombo,
                                Runnable action, boolean worksWithoutData) {
        ACTIONS.putIfAbsent(id, new ShortcutAction(id, group, label, defaultCombo, action, worksWithoutData));
    }

    /** All registered actions in registration (display) order. */
    public static List<ShortcutAction> actions() {
        return List.copyOf(ACTIONS.values());
    }

    public static ShortcutAction action(String id) { return ACTIONS.get(id); }

    /** The effective combo for an action: user override or its default. */
    public static String comboOf(String actionId) {
        return CUSTOM.getOrDefault(actionId,
                ACTIONS.containsKey(actionId) ? ACTIONS.get(actionId).defaultCombo() : null);
    }

    /** Actions grouped by their group title, in display order. */
    public static Map<String, List<ShortcutAction>> grouped() {
        Map<String, List<ShortcutAction>> out = new LinkedHashMap<>();
        for (ShortcutAction a : ACTIONS.values()) {
            out.computeIfAbsent(a.group(), k -> new ArrayList<>()).add(a);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    public enum Validation { OK, TAKEN, RESERVED, TOO_SIMPLE, INVALID }

    /**
     * Checks a candidate combo for an action. {@code candidate} may be
     * {@code null}/blank (= unbinding, allowed).
     */
    public static Validation validate(String actionId, String candidate) {
        if (candidate == null || candidate.isBlank()) return Validation.OK; // unbind
        String c = normalize(candidate);
        if (c == null) return Validation.INVALID;
        if (RESERVED.contains(c) || RESERVED.contains(candidate)) return Validation.RESERVED;
        // Require at least one modifier (except F-keys) so typing never fires actions.
        boolean hasModifier = c.contains("+");
        boolean isFunctionKey = c.matches("F(1|2|3|4|5|6|7|8|9|10|11|12)");
        if (!hasModifier && !isFunctionKey) return Validation.TOO_SIMPLE;
        // Already bound to another action?
        for (ShortcutAction a : ACTIONS.values()) {
            if (!a.id().equals(actionId) && c.equalsIgnoreCase(comboOf(a.id()))) return Validation.TAKEN;
        }
        return Validation.OK;
    }

    /** Human-readable message for a failed validation. */
    public static String validationMessage(Validation v, String candidate) {
        return switch (v) {
            case OK -> "";
            case TAKEN -> "\"" + candidate + "\" is already used by another shortcut.";
            case RESERVED -> "\"" + candidate + "\" is reserved by the window system and cannot be rebound.";
            case TOO_SIMPLE -> "Use a modifier (Ctrl/Alt/Shift) or an F-key — plain letters would fire while typing.";
            case INVALID -> "Could not read that key combination.";
        };
    }

    // ------------------------------------------------------------------
    // Binding changes
    // ------------------------------------------------------------------

    /** Applies a binding (already validated) and persists it. */
    public static void bind(String actionId, String combo) {
        if (combo == null || combo.isBlank()) {
            CUSTOM.remove(actionId);
        } else {
            String norm = normalize(combo);
            CUSTOM.put(actionId, norm != null ? norm : combo);
        }
        save();
    }

    /** Restores the default binding for one action. */
    public static void resetToDefault(String actionId) {
        CUSTOM.remove(actionId);
        save();
    }

    /** Restores every default binding. */
    public static void resetAll() {
        CUSTOM.clear();
        save();
    }

    /**
     * Normalizes a user-typed combo to a canonical form:
     * modifiers sorted Ctrl → Shift → Alt, key title-cased
     * ({@code "ctrl+shift+l"} → {@code "Ctrl+Shift+L"}). {@code null} if unreadable.
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        String[] parts = s.split("\\+");
        LinkedHashSet<String> mods = new LinkedHashSet<>();
        String key = null;
        for (String p0 : parts) {
            String p = p0.trim();
            if (p.isEmpty()) return null;
            switch (p.toLowerCase()) {
                case "ctrl", "control" -> mods.add("Ctrl");
                case "shift" -> mods.add("Shift");
                case "alt" -> mods.add("Alt");
                case "meta", "cmd", "win" -> mods.add("Meta");
                default -> {
                    if (key != null) return null; // two non-modifiers
                    key = p.length() == 1 ? p.toUpperCase() : Character.toUpperCase(p.charAt(0)) + p.substring(1).toLowerCase();
                }
            }
        }
        if (key == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String m : new String[]{"Ctrl", "Shift", "Alt", "Meta"}) {
            if (mods.contains(m)) sb.append(m).append('+');
        }
        return sb.append(key).toString();
    }

    // ------------------------------------------------------------------
    // Installation & persistence
    // ------------------------------------------------------------------

    /** Pushes every binding into the scene's accelerator map (call after rebinding). */
    public static void installAll(javafx.scene.Scene scene) {
        if (scene == null) return;
        for (ShortcutAction a : ACTIONS.values()) {
            String combo = comboOf(a.id());
            if (combo == null || combo.isBlank()) continue;
            try {
                scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf(combo), a.action());
            } catch (Exception e) {
                AppLog.warn("Bad shortcut combo \"" + combo + "\" for " + a.id());
            }
        }
    }

    private static Path store() {
        if (storeFile == null) {
            storeFile = com.invoicestudio.AppDirs.dataDir().resolve("shortcuts.json");
        }
        return storeFile;
    }

    static void load() {
        try {
            if (!Files.exists(store())) return;
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = MAPPER.readValue(store().toFile(), Map.class);
            CUSTOM.clear();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                Object v = e.getValue();
                if (e.getKey() != null && v != null) CUSTOM.put(e.getKey(), String.valueOf(v));
            }
        } catch (Exception e) {
            AppLog.warn("Could not read shortcuts.json — using defaults (" + e.getMessage() + ")");
        }
    }

    private static void save() {
        try {
            Files.createDirectories(store().getParent());
            MAPPER.writeValue(store().toFile(), CUSTOM);
        } catch (IOException e) {
            AppLog.error("Could not save shortcuts.json", e);
        }
    }
}
