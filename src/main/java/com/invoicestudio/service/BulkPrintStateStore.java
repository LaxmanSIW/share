package com.invoicestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers the last-used bulk-print dialog state per template (skill rule:
 * seamless UX — a repeat print session should never start from a blank grid).
 *
 * <p>Storage: {@code AppDirs.dataDir()/bulk-print-state.json}, one entry per
 * template id holding the exact rows the user left behind (variable values +
 * per-row copies) and the selected printer. Written whenever a bulk dialog
 * closes; read when the next one opens. All failures degrade silently — the
 * dialog simply starts empty, never blocking a print run on a preference file.</p>
 */
public final class BulkPrintStateStore {

    /** One remembered print line. */
    public record Row(Map<String, String> values, int copies) {
        public Row {
            values = values == null ? Map.of() : values;
            copies = Math.max(1, copies);
        }
    }

    /** Everything remembered for one template. */
    public record TemplateState(List<Row> rows, String printer) {
        public TemplateState {
            rows = rows == null ? List.of() : List.copyOf(rows);
            printer = printer == null ? "" : printer;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Object LOCK = new Object();

    private BulkPrintStateStore() { }

    /** Reads the remembered state for a template, or {@code null} if none. */
    public static TemplateState load(String templateId) {
        synchronized (LOCK) {
            try {
                Path file = file();
                if (!Files.isRegularFile(file)) return null;
                Map<String, TemplateState> all = MAPPER.readValue(file.toFile(),
                        MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, TemplateState.class));
                return all.get(templateId);
            } catch (Exception e) {
                AppLog.debug(e);
                return null; // corrupt/missing file → blank dialog, never block printing
            }
        }
    }

    /** Saves the remembered state for a template. Failures are logged, never thrown. */
    public static void save(String templateId, TemplateState state) {
        synchronized (LOCK) {
            try {
                Path file = file();
                Files.createDirectories(file.getParent());
                Map<String, TemplateState> all = Files.isRegularFile(file)
                        ? MAPPER.readValue(file.toFile(), MAPPER.getTypeFactory()
                                .constructMapType(LinkedHashMap.class, String.class, TemplateState.class))
                        : new LinkedHashMap<>();
                all.put(templateId, state);
                MAPPER.writeValue(file.toFile(), all);
            } catch (IOException e) {
                AppLog.debug(e);
            }
        }
    }

    private static Path file() {
        return com.invoicestudio.AppDirs.dataDir().resolve("bulk-print-state.json");
    }
}
