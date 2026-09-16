package com.invoicestudio.ui.views;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.TemplateElement;
import com.invoicestudio.service.AppLog;

import java.util.Stack;

/**
 * Mutable session state of the Template Designer (skill rule 5.2 role 1).
 *
 * Owns the undo/redo stacks (whole-template JSON snapshots, capped at 50),
 * the element clipboard and the shared Jackson mapper — plain data with no
 * JavaFX scene-graph dependency. Extracted verbatim from the original
 * designer class; behavior is unchanged.
 */
final class DesignerState {

    private static final int MAX_UNDO_DEPTH = 50;

    final ObjectMapper mapper = new ObjectMapper();
    private final Stack<String> undoStack = new Stack<>();
    private final Stack<String> redoStack = new Stack<>();
    private TemplateElement clipboardElement;

    // ─── Undo / redo (whole-template JSON snapshots) ───────────────────────

    /** Pushes a snapshot unless it duplicates the current top; clears redo. */
    void saveState(Template template) {
        try {
            String json = mapper.writeValueAsString(template);
            if (!undoStack.isEmpty() && undoStack.peek().equals(json)) {
                return;
            }
            undoStack.push(json);
            if (undoStack.size() > MAX_UNDO_DEPTH) {
                undoStack.remove(0);
            }
            redoStack.clear();
        } catch (Exception e) {
            AppLog.error(e);
        }
    }

    /**
     * Restores the previous snapshot.
     *
     * @return the restored template, or {@code null} when there is nothing to undo.
     */
    Template undo() {
        if (undoStack.size() <= 1) {
            return null;
        }
        try {
            String current = undoStack.pop();
            redoStack.push(current);
            String previous = undoStack.peek();
            return mapper.readValue(previous, Template.class);
        } catch (Exception e) {
            AppLog.error(e);
            return null;
        }
    }

    /**
     * Restores the last undone snapshot.
     *
     * @return the restored template, or {@code null} when there is nothing to redo.
     */
    Template redo() {
        if (redoStack.isEmpty()) {
            return null;
        }
        try {
            String next = redoStack.pop();
            undoStack.push(next);
            return mapper.readValue(next, Template.class);
        } catch (Exception e) {
            AppLog.error(e);
            return null;
        }
    }

    // ─── Clipboard ─────────────────────────────────────────────────────────

    /** Deep-copies the element into the clipboard. */
    void copy(TemplateElement el) {
        if (el == null) return;
        try {
            clipboardElement = mapper.readValue(mapper.writeValueAsString(el), TemplateElement.class);
        } catch (Exception e) {
            AppLog.error(e);
        }
    }

    /** Deep-copies the clipboard element, or null when the clipboard is empty. */
    TemplateElement pasteSource() {
        if (clipboardElement == null) return null;
        try {
            return mapper.readValue(mapper.writeValueAsString(clipboardElement), TemplateElement.class);
        } catch (Exception e) {
            AppLog.error(e);
            return null;
        }
    }
}
