package com.invoicestudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.db.TemplateDao;
import com.invoicestudio.model.Template;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Template Package Service — DOWNLOAD (export) and UPLOAD (import) of print
 * templates as a small portable JSON file.
 *
 * <p>This is the template-level sibling of {@link BackupRestoreService}:
 * the backup service always dumps the ENTIRE account (settings, bills, buyers,
 * items, variables, templates) into one file, which is far too heavy for
 * "share this receipt layout with my accountant". This service moves ONLY the
 * templates the user picked — one, several, or all.</p>
 *
 * <p>Nothing here touches the database schema: templates already live in the
 * existing {@code templates} table as JSON, so import/export is a pure
 * round-trip through {@link TemplateDao} (the same DAO every other screen
 * uses).</p>
 *
 * <p><b>Safety model</b> — an import NEVER overwrites existing work:
 * <ul>
 *   <li>an incoming id that already exists in this account gets a fresh id;</li>
 *   <li>an incoming name that already exists gets an " (imported)" suffix;</li>
 *   <li>rows are only ever inserted/updated for the templates in the file.</li>
 * </ul>
 * Every stored template is scoped to the logged-in user by the DAO, so an
 * imported template lands in the current user's own account, exactly like a
 * template created in the designer.</p>
 */
public class TemplatePackageService {

    /** Marker written into exported files so we can recognise our own format. */
    public static final String FILE_KIND = "invoicestudio.templates";
    /** Package format version (independent of the app version). */
    public static final String FORMAT_VERSION = "1.0";
    /** Suggested file suffix for a single downloaded template. */
    public static final String FILE_SUFFIX = ".istemplates.json";

    private final TemplateDao templateDao;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public TemplatePackageService(DatabaseManager db) {
        this.templateDao = new TemplateDao(db);
    }

    // ------------------------------------------------------------------
    // Export (download)
    // ------------------------------------------------------------------

    /**
     * Writes the given templates to {@code destination} as one portable JSON package.
     *
     * @return how many templates were actually written.
     * @throws IOException when nothing was selected, no destination was picked,
     *                     or the file could not be written.
     */
    public int exportTemplates(Collection<Template> templates, File destination) throws IOException {
        if (templates == null || templates.isEmpty()) {
            throw new IOException("No template selected — pick at least one template to download.");
        }
        if (destination == null) {
            throw new IOException("No file chosen — download cancelled.");
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("kind", FILE_KIND);
        root.put("version", FORMAT_VERSION);
        root.put("app", "InvoiceStudio");
        root.put("exportedAt", Instant.now().toString());

        ArrayNode array = root.putArray("templates");
        for (Template t : templates) {
            if (t == null) continue;
            array.add(mapper.valueToTree(t));
        }
        if (array.isEmpty()) {
            throw new IOException("No template selected — pick at least one template to download.");
        }

        mapper.writeValue(destination, root);
        return array.size();
    }

    /** File name proposed in the save dialog: the template name for one, a count for many. */
    public static String suggestedFileName(Collection<Template> templates) {
        if (templates != null && templates.size() == 1) {
            Template only = templates.iterator().next();
            if (only != null && only.getName() != null && !only.getName().isBlank()) {
                return sanitizeFileName(only.getName()) + FILE_SUFFIX;
            }
        }
        int n = templates == null ? 0 : templates.size();
        return "InvoiceStudio_templates_" + n + ".json";
    }

    /** Strips characters Windows/macOS reject in file names. */
    private static String sanitizeFileName(String raw) {
        String clean = raw.trim().replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ");
        if (clean.length() > 60) clean = clean.substring(0, 60).trim();
        return clean.isEmpty() ? "template" : clean;
    }

    // ------------------------------------------------------------------
    // Import (upload)
    // ------------------------------------------------------------------

    /** Outcome of an import — used for the confirmation toast. */
    public static class ImportResult {
        public int imported;
        public int renamed;
        public final List<String> names = new ArrayList<>();

        /** One-line, human readable summary for a toast/dialog. */
        public String summary() {
            if (imported == 0) return "No templates were imported.";
            String base = imported == 1
                    ? "Imported template \"" + names.get(0) + "\"."
                    : "Imported " + imported + " templates.";
            if (renamed > 0) {
                base += " " + renamed + (renamed == 1 ? " name was" : " names were")
                        + " adjusted to avoid overwriting an existing template.";
            }
            return base;
        }
    }

    /**
     * Imports every template found in {@code source} into the current user's account.
     *
     * <p>Accepts both formats, so a user can also point this at a full
     * "Export Backup JSON" file and pull just the templates out of it:</p>
     * <ul>
     *   <li>a template package written by {@link #exportTemplates};</li>
     *   <li>any JSON object carrying a {@code "templates"} array;</li>
     *   <li>a bare JSON array of templates.</li>
     * </ul>
     */
    public ImportResult importTemplates(File source) throws IOException {
        if (source == null) throw new IOException("No file chosen — upload cancelled.");
        if (!source.isFile()) throw new IOException("File not found: " + source.getName());

        List<Template> incoming = readTemplates(source);
        if (incoming.isEmpty()) {
            throw new IOException("No templates found in " + source.getName() + ".");
        }

        Set<String> usedIds = new HashSet<>();
        Set<String> usedNames = new HashSet<>();
        for (Template existing : templateDao.getAllTemplates()) {
            if (existing == null) continue;
            if (existing.getId() != null) usedIds.add(existing.getId());
            if (existing.getName() != null) usedNames.add(existing.getName().trim().toLowerCase(Locale.ROOT));
        }

        ImportResult result = new ImportResult();
        for (Template t : incoming) {
            String id = t.getId();
            if (id == null || id.isBlank() || usedIds.contains(id)) {
                id = "tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            }
            t.setId(id);
            usedIds.add(id);

            String desired = (t.getName() == null || t.getName().isBlank()) ? "Imported Template" : t.getName().trim();
            String unique = uniqueName(desired, usedNames);
            if (!unique.equalsIgnoreCase(desired)) result.renamed++;
            t.setName(unique);
            usedNames.add(unique.toLowerCase(Locale.ROOT));

            templateDao.saveTemplate(t);
            result.imported++;
            result.names.add(unique);
        }
        return result;
    }

    /** Reads (and validates) the templates inside a JSON file without saving anything. */
    public List<Template> readTemplates(File source) throws IOException {
        if (source == null) throw new IOException("No file chosen.");

        JsonNode root = mapper.readTree(source);
        if (root == null || root.isNull()) throw new IOException("The file is empty.");

        JsonNode array = null;
        if (root.isArray()) {
            array = root;
        } else if (root.has("templates") && root.get("templates").isArray()) {
            array = root.get("templates");
        }
        if (array == null) {
            throw new IOException("This file does not contain any InvoiceStudio templates.");
        }

        List<Template> out = new ArrayList<>();
        for (JsonNode node : array) {
            if (node == null || !node.isObject()) continue;
            try {
                Template t = mapper.treeToValue(node, Template.class);
                if (t != null) out.add(t);
            } catch (Exception skipped) {
                // A single malformed entry must never abort the whole import.
                AppLog.debug(skipped);
            }
        }
        return out;
    }

    /** "Receipt" -> "Receipt (imported)" -> "Receipt (imported 2)" ... until unused. */
    private static String uniqueName(String desired, Set<String> usedNamesLower) {
        if (!usedNamesLower.contains(desired.toLowerCase(Locale.ROOT))) return desired;
        String candidate = desired + " (imported)";
        int n = 2;
        while (usedNamesLower.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = desired + " (imported " + n + ")";
            n++;
        }
        return candidate;
    }
}
