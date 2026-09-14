package com.invoicestudio.ui;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for every keyboard shortcut in the application.
 * Rendered by both {@link ShortcutsPanel} (Settings → Shortcuts tab) and
 * {@link ShortcutsDialog} (F1 overlay), so the two can never drift apart.
 */
public final class ShortcutCatalog {

    private ShortcutCatalog() {}

    /** Grouped shortcuts: group title → {keys, description} rows, in display order. */
    public static Map<String, String[][]> groups() {
        Map<String, String[][]> groups = new LinkedHashMap<>();

        groups.put("Global (works on every screen)", new String[][]{
                {"F1", "Toggle shortcuts help popup"},
                {"Ctrl + N", "New Bill (invoice entry)"},
                {"Ctrl + P", "Purchases — record a purchase bill"},
                {"Ctrl + E", "Expenses — new expense voucher"},
                {"Ctrl + B", "Buyers directory"},
                {"Ctrl + D", "Dashboard"},
                {"Ctrl + Shift + L", "Label Designer — opens Template Designer in Barcode Mode"},
                {"Ctrl + Shift + B", "Bulk Label Print — opens the bulk label printing window"},
                {"Esc", "Close dialogs / cancel entry (contextual)"}
        });

        groups.put("Billing & Entry Forms", new String[][]{
                {"Ctrl + S", "Save the bill / purchase / expense being edited"},
                {"Enter", "Confirm focused dialog (OK)"},
                {"Tab / Shift + Tab", "Move between fields in entry forms"}
        });

        groups.put("Template Designer — Tools", new String[][]{
                {"V", "Select & Move tool"},
                {"H / Space (hold)", "Pan tool (hold Space to pan anytime)"},
                {"P", "Vector Pen tool (plot points / curves)"},
                {"Double-Click (Text)", "Inline text editing directly on canvas"},
                {"Enter (Editing)", "Commit inline text changes"},
                {"Shift + Enter (Editing)", "Insert newline while editing text"},
                {"Esc", "Cancel text edit / pen tool / deselect element"},
                {"Delete / Backspace", "Delete selected element (safe while typing)"}
        });

        groups.put("Template Designer — Canvas & Zoom", new String[][]{
                {"Ctrl + Mouse Wheel", "Zoom canvas in / out (30% – 400%)"},
                {"Ctrl + +  /  Ctrl + -", "Zoom in / Zoom out"},
                {"Ctrl + 0", "Reset zoom to 100%"},
                {"Arrow Keys", "Nudge selected element 1 mm (Shift = 5 mm)"},
                {"Grid", "Adapts to zoom: 10 → 5 → 2 → 1 mm cells"}
        });

        groups.put("Template Designer — Edit & Save", new String[][]{
                {"Ctrl + S", "Save template"},
                {"Ctrl + Z / Ctrl + Y", "Undo / Redo (Ctrl+Shift+Z also redoes)"},
                {"Ctrl + C / Ctrl + V", "Copy / Paste element"},
                {"Ctrl + D", "Duplicate selected element"},
                {"Ctrl + G", "Group selected elements"},
                {"Ctrl + Shift + G", "Ungroup selected elements"}
        });

        groups.put("Barcode Mode & Bulk Label Print", new String[][]{
                {"Ctrl + Shift + L", "Toggle Barcode Mode on the open template"},
                {"Ctrl + Shift + B", "Open Bulk Label Print window"},
                {"Enter", "Commit row value & move down (auto-adds rows)"},
                {"Insert / Alt + N", "Add a new print row"},
                {"Ctrl + Delete", "Delete the selected print row"},
                {"Ctrl + Enter", "Print the whole queue now"},
                {"F4", "Close bulk print window"},
                {"Tab / Arrows", "Move between row cells"}
        });

        return groups;
    }
}
