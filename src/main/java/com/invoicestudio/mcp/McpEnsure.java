package com.invoicestudio.mcp;

import com.invoicestudio.model.Buyer;
import com.invoicestudio.model.ItemCategory;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.model.Supplier;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.Transport;
import com.invoicestudio.model.VariableDef;
import com.invoicestudio.ui.DataManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fast existence-lookup + check-then-create layer backing every MCP create path.
 *
 * <p><b>Contract (mirrors MCP_SERVER.md §"Check-then-create"):</b> before an
 * entity that references another entity by value/id is inserted, the reference
 * is resolved here; a missing dependency is auto-created with sane defaults and
 * reported to the caller via the tool response's {@code autoCreated} array.</p>
 *
 * <p><b>Speed (Task 1):</b> every lookup is served from {@link DataManager}'s
 * in-memory caches (categories/transports) or a single indexed id lookup /
 * case-insensitive name scan on small directory tables — no redundant network
 * or full-register passes. Lookups run on every tool call, so they never
 * trigger cache invalidation or extra I/O beyond one SELECT at worst.</p>
 *
 * <p><b>Race safety (Task 2):</b> the MCP server dispatches on a 4-thread
 * pool, so two calls can create the same missing dependency concurrently.
 * Every ensure*() takes a striped lock keyed by (type, normalized lookup)
 * around the check-then-insert window, and a DB-level UNIQUE index on
 * categories(user_id, name) backstops the path that matters most
 * (item → category). A loser of a race re-reads the winner's row and returns
 * it ({@code created=false, matchedBy="race"}).</p>
 *
 * <p><b>Defaults (Task 2):</b> auto-created dependencies receive the same
 * field defaults the desktop UI uses (empty strings for contact fields, 0 for
 * money/stock, generated prefixed ids matching each model's convention:
 * cat_ / byr_ / sup_ / it_ / trn_).</p>
 *
 * <p><b>Partial failure (Task 2):</b> create flows that auto-create
 * dependencies first (create_bill, create_purchase) track what they created
 * and call {@link #rollback(DataManager, List)} if the primary insert throws,
 * so no half-done state is left behind.</p>
 */
public final class McpEnsure {

    /** Result of one ensure call — shaped into the tool response by the registry. */
    public static final class Outcome {
        public final String id;
        public final String name;
        public final boolean created;
        public final String matchedBy; // "id" | "name" | "race" | null when created

        Outcome(String id, String name, boolean created, String matchedBy) {
            this.id = id;
            this.name = name;
            this.created = created;
            this.matchedBy = matchedBy;
        }

        public java.util.Map<String, Object> asMap(String type) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("type", type);
            m.put("id", id);
            m.put("name", name);
            if (matchedBy != null) m.put("matchedBy", matchedBy);
            return m;
        }
    }

    private static final Object[] LOCKS = new Object[64];
    static {
        for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new Object();
    }

    private static Object lockFor(String key) {
        return LOCKS[(key.hashCode() & 0x7fffffff) % LOCKS.length];
    }

    private McpEnsure() {}

    // ------------------------------------------------------------------
    // Existence lookups (Task 1 — cheap, cache/DAO-backed)
    // ------------------------------------------------------------------

    public static ItemCategory findCategory(DataManager dm, String idArg, String nameArg) {
        List<ItemCategory> categories = dm.getAllCategories();
        if (idArg != null && !idArg.isBlank()) {
            for (ItemCategory c : categories) {
                if (c.getId() != null && c.getId().equalsIgnoreCase(idArg.trim())) return c;
            }
        }
        if (nameArg != null && !nameArg.isBlank()) {
            for (ItemCategory c : categories) {
                if (c.getName() != null && c.getName().equalsIgnoreCase(nameArg.trim())) return c;
            }
        }
        return null;
    }

    public static Buyer findBuyer(DataManager dm, String idArg, String nameArg) {
        if (idArg != null && !idArg.isBlank()) {
            Buyer b = dm.buyers().getBuyerById(idArg.trim());
            if (b != null) return b;
        }
        if (nameArg != null && !nameArg.isBlank()) {
            return dm.buyers().findByName(nameArg.trim());
        }
        return null;
    }

    public static Supplier findSupplier(DataManager dm, String idArg, String nameArg) {
        if (idArg != null && !idArg.isBlank()) {
            Supplier s = dm.suppliers().getSupplierById(idArg.trim());
            if (s != null) return s;
        }
        if (nameArg != null && !nameArg.isBlank()) {
            String needle = nameArg.trim().toLowerCase(Locale.ROOT);
            for (Supplier s : dm.suppliers().getAllSuppliers()) {
                if (s.getName() != null && s.getName().toLowerCase(Locale.ROOT).equals(needle)) return s;
            }
        }
        return null;
    }

    public static ItemRecord findItem(DataManager dm, String idArg, String nameArg) {
        if (idArg != null && !idArg.isBlank()) {
            ItemRecord it = dm.items().getItemById(idArg.trim());
            if (it != null) return it;
        }
        if (nameArg != null && !nameArg.isBlank()) {
            String needle = nameArg.trim().toLowerCase(Locale.ROOT);
            for (ItemRecord it : dm.items().getAllItems()) {
                if (it.getName() != null && it.getName().toLowerCase(Locale.ROOT).equals(needle)) return it;
            }
        }
        return null;
    }

    public static Transport findTransportByName(DataManager dm, String nameArg) {
        if (nameArg == null || nameArg.isBlank()) return null;
        String needle = nameArg.trim().toLowerCase(Locale.ROOT);
        for (Transport t : dm.getAllTransports()) {
            if (t.getName() != null && t.getName().toLowerCase(Locale.ROOT).equals(needle)) return t;
        }
        return null;
    }

    public static Template findTemplateByName(DataManager dm, String nameArg) {
        if (nameArg == null || nameArg.isBlank()) return null;
        String needle = nameArg.trim().toLowerCase(Locale.ROOT);
        for (Template t : dm.templates().getAllTemplates()) {
            if (t.getName() != null && t.getName().toLowerCase(Locale.ROOT).equals(needle)) return t;
        }
        return null;
    }

    public static VariableDef findVariable(DataManager dm, String key) {
        if (key == null || key.isBlank()) return null;
        for (VariableDef v : dm.variables().getAllVariables()) {
            if (v.getKey() != null && v.getKey().equalsIgnoreCase(key.trim())) return v;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Ensure (Task 2 — check-then-create with defaults + race safety)
    // ------------------------------------------------------------------

    /**
     * Resolves the item → category reference. Accepts an id, a name, or both.
     * A missing dependency is created: with the given id when one was supplied
     * (kills the dangling-category_id bug at the root), else generated.
     */
    public static Outcome ensureCategory(DataManager dm, String idArg, String nameArg) {
        String id = safe(idArg);
        String name = safe(nameArg);
        if (id.isEmpty() && name.isEmpty()) return null;

        ItemCategory existing = findCategory(dm, id, name);
        if (existing != null) {
            boolean byId = id.isEmpty() || (existing.getId() != null && existing.getId().equalsIgnoreCase(id));
            return new Outcome(existing.getId(), existing.getName(), false, byId ? "id" : "name");
        }

        String key = "category|" + (name.isEmpty() ? id : name).toLowerCase(Locale.ROOT);
        synchronized (lockFor(key)) {
            existing = findCategory(dm, id, name); // double-checked inside lock
            if (existing != null) {
                return new Outcome(existing.getId(), existing.getName(), false, "race");
            }
            ItemCategory c = new ItemCategory();
            c.setId(id.isEmpty() ? newId("cat_", 10) : sanitizeId(id, "cat_", 10));
            c.setName(name.isEmpty() ? sanitizeId(id, "", 40) : name);
            dm.saveCategory(c);
            return new Outcome(c.getId(), c.getName(), true, null);
        }
    }

    /** Buyer reference used by create_bill / create_buyer. */
    public static Outcome ensureBuyer(DataManager dm, String idArg, String nameArg) {
        String id = safe(idArg);
        String name = safe(nameArg);
        if (id.isEmpty() && name.isEmpty()) return null;

        Buyer existing = findBuyer(dm, id, name);
        if (existing != null) {
            boolean byId = !id.isEmpty() && existing.getId() != null && existing.getId().equalsIgnoreCase(id);
            return new Outcome(existing.getId(), existing.getName(), false, byId ? "id" : "name");
        }

        String key = "buyer|" + (name.isEmpty() ? id : name).toLowerCase(Locale.ROOT);
        synchronized (lockFor(key)) {
            existing = findBuyer(dm, id, name);
            if (existing != null) return new Outcome(existing.getId(), existing.getName(), false, "race");

            Buyer b = new Buyer();
            b.setId(id.isEmpty() ? newId("byr_", 10) : sanitizeId(id, "byr_", 10));
            b.setName(name.isEmpty() ? sanitizeId(id, "", 60) : name);
            b.setAddress("");
            b.setGst("");
            b.setPhone("");
            b.setState("");
            b.setStateCode("");
            dm.buyers().saveBuyer(b);
            return new Outcome(b.getId(), b.getName(), true, null);
        }
    }

    /** Supplier reference used by create_purchase — replaces the old hard error. */
    public static Outcome ensureSupplier(DataManager dm, String idArg, String nameArg) {
        String id = safe(idArg);
        String name = safe(nameArg);
        if (id.isEmpty() && name.isEmpty()) return null;

        Supplier existing = findSupplier(dm, id, name);
        if (existing != null) {
            boolean byId = !id.isEmpty() && existing.getId() != null && existing.getId().equalsIgnoreCase(id);
            return new Outcome(existing.getId(), existing.getName(), false, byId ? "id" : "name");
        }

        String key = "supplier|" + (name.isEmpty() ? id : name).toLowerCase(Locale.ROOT);
        synchronized (lockFor(key)) {
            existing = findSupplier(dm, id, name);
            if (existing != null) return new Outcome(existing.getId(), existing.getName(), false, "race");

            Supplier s = new Supplier();
            s.setId(id.isEmpty() ? newId("sup_", 10) : sanitizeId(id, "sup_", 10));
            s.setName(name.isEmpty() ? sanitizeId(id, "", 60) : name);
            s.setPhone("");
            s.setGst("");
            s.setState("");
            s.setStateCode("");
            s.setCity("");
            s.setAddress("");
            s.setOpeningBalance(0.0);
            s.setCreditPeriodDays(0);
            dm.suppliers().saveSupplier(s);
            return new Outcome(s.getId(), s.getName(), true, null);
        }
    }

    /**
     * Catalog item referenced by a bill/purchase line. When the referenced item
     * does not exist it is created from the line itself (desc/rate/gst become
     * the defaults), so no invoice line can point at a phantom catalog entry.
     */
    public static Outcome ensureItemForLine(DataManager dm, String idArg, String descArg, Double rate, Double gst) {
        String id = safe(idArg);
        String desc = safe(descArg);
        if (id.isEmpty() && desc.isEmpty()) return null;

        ItemRecord existing = findItem(dm, id, desc);
        if (existing != null) {
            boolean byId = !id.isEmpty() && existing.getId() != null && existing.getId().equalsIgnoreCase(id);
            return new Outcome(existing.getId(), existing.getName(), false, byId ? "id" : "name");
        }

        String key = "item|" + (desc.isEmpty() ? id : desc).toLowerCase(Locale.ROOT);
        synchronized (lockFor(key)) {
            existing = findItem(dm, id, desc);
            if (existing != null) return new Outcome(existing.getId(), existing.getName(), false, "race");

            ItemRecord it = new ItemRecord();
            it.setId(id.isEmpty() ? newId("it_", 12) : sanitizeId(id, "it_", 12));
            it.setName(desc.isEmpty() ? sanitizeId(id, "", 60) : desc);
            it.setHsn("");
            it.setUnit("PCS");
            it.setRate(rate != null ? rate : 0.0);
            it.setGst(gst != null ? gst : 0.0);
            dm.items().saveItem(it);
            return new Outcome(it.getId(), it.getName(), true, null);
        }
    }

    // ------------------------------------------------------------------
    // Partial-failure compensation
    // ------------------------------------------------------------------

    /** One tracked auto-creation, for rollback if the primary insert fails. */
    public record Tracked(String type, String id) {}

    public static List<Tracked> track() {
        return new ArrayList<>();
    }

    /** Deletes only the dependencies this call auto-created. Never throws. */
    public static void rollback(DataManager dm, List<Tracked> created) {
        if (created == null) return;
        for (Tracked t : created) {
            try {
                switch (t.type()) {
                    case "category" -> dm.deleteCategory(t.id());
                    case "buyer" -> dm.buyers().deleteBuyer(t.id());
                    case "supplier" -> dm.suppliers().deleteSupplier(t.id());
                    case "item" -> dm.items().deleteItem(t.id());
                    default -> { }
                }
            } catch (Exception ignored) {
                // compensation is best-effort; the original error is propagated
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String newId(String prefix, int hexChars) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, hexChars);
    }

    /** Accepts caller-supplied ids only when they are safe; otherwise generates a fresh one. */
    private static String sanitizeId(String raw, String prefix, int hexChars) {
        String id = raw.trim();
        if (id.matches("[A-Za-z0-9_.-]{1,64}")) return id;
        return newId(prefix, hexChars);
    }
}
