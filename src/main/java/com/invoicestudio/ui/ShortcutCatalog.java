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

        groups.put("Billing & Entry Forms", new String[][]{
                {"Ctrl + S", "Save the bill / purchase / expense being edited"},
                {"Enter", "Confirm focused dialog (OK)"},
                {"Tab / Shift + Tab", "Move between fields in entry forms"}
        });

        groups.put("Expense Accounts & Reports", new String[][]{
                {"Accounts button", "Open the expense-account manager (view / edit / rename / archive)"},
                {"Report button", "Account & category reports with charts — pick any account/category"},
                {"Payee combo", "Type to filter existing accounts; unknown names offer to be saved"},
                {"Filter combos", "Account / Category dropdowns filter the register instantly"}
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

        groups.put("Template Designer — Tools & Canvas", new String[][]{
                {"V", "Select & Move tool"},
                {"H / Space (hold)", "Pan tool (hold Space to pan anytime)"},
                {"P", "Vector Pen tool (plot points / curves)"},
                {"Double-Click (Text)", "Inline text editing directly on canvas"},
                {"Enter (Editing)", "Commit inline text changes"},
                {"Shift + Enter (Editing)", "Insert newline while editing text"},
                {"Esc", "Cancel text edit / pen tool / deselect element"},
                {"Delete / Backspace", "Delete selected element (safe while typing)"},
                {"Ctrl + Mouse Wheel", "Zoom canvas in / out (30% – 400%)"},
                {"Ctrl + +  /  Ctrl + -", "Zoom in / Zoom out"},
                {"Ctrl + 0", "Reset zoom to 100%"},
                {"Arrow Keys", "Nudge selected element 1 mm (Shift = 5 mm)"},
                {"Ctrl + S", "Save template"},
                {"Ctrl + Z / Ctrl + Y", "Undo / Redo (Ctrl+Shift+Z also redoes)"},
                {"Ctrl + C / Ctrl + V", "Copy / Paste element"},
                {"Ctrl + G", "Group selected elements"},
                {"Ctrl + Shift + G", "Ungroup selected elements"}
        });

        groups.put("Barcode Mode & Bulk Label Print", new String[][]{
                {"Ctrl + Shift + L", "Toggle Barcode Mode on the open template"},
                {"Ctrl + Shift + B", "Open Bulk Label Print window"},
                {"Enter", "In a cell: open dropdown, pick highlighted value & move down"},
                {"↓ / ↑", "Open the dropdown / browse its choices"},
                {"Esc", "Close the dropdown without changing the value"},
                {"Insert / Alt + N", "Add a new print row"},
                {"Ctrl + Delete", "Delete the selected print row"},
                {"Ctrl + Enter", "Print the whole queue now"},
                {"F4", "Close bulk print window"},
                {"Tab / ← →", "Move between row cells"}
        });

        return groups;
    }

    /**
     * Contextual (non-rebindable) sections for the F1 overlay — canvas tools
     * and dialog-local keys. The rebindable global actions live in
     * {@link ShortcutManager} and are prepended by the dialog itself.
     */
    public static Map<String, String[][]> contextGroups() {
        return groups();
    }
}
