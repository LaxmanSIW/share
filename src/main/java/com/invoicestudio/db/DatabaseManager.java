package com.invoicestudio.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.*;

import java.io.File;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static DatabaseManager instance;
    private final String dbUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            // Per-user data dir (see AppDirs) so installed copies work out of
            // write-protected Program Files; legacy CWD DBs are migrated once.
            instance = new DatabaseManager(com.invoicestudio.AppDirs.databaseUrl());
        }
        return instance;
    }

    public static synchronized DatabaseManager initCustom(String dbUrl) {
        instance = new DatabaseManager(dbUrl);
        return instance;
    }

    public DatabaseManager(String dbUrl) {
        this.dbUrl = dbUrl;
        initSchema();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    private void initSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY, json_data TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS templates (id TEXT PRIMARY KEY, name TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS bills (id TEXT PRIMARY KEY, bill_no TEXT, date TEXT, doc_type TEXT, status TEXT, buyer_name TEXT, grand_total REAL, due_amount REAL, json_data TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bills_bill_no ON bills(bill_no)");
            stmt.execute("CREATE TABLE IF NOT EXISTS buyers (id TEXT PRIMARY KEY, name TEXT, phone TEXT, gst TEXT, state TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)");
            // v4.3 — Purchase foundation: supplier (Sundry Creditor) directory
            stmt.execute("CREATE TABLE IF NOT EXISTS suppliers (id TEXT PRIMARY KEY, name TEXT, phone TEXT, gst TEXT, state TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)");
            // v4.3.1 — Purchase cycle: purchase bills + immutable stock ledger
            stmt.execute("CREATE TABLE IF NOT EXISTS purchase_bills (id TEXT PRIMARY KEY, bill_no TEXT, supplier_bill_no TEXT, date TEXT, supplier_id TEXT, supplier_name TEXT, total REAL, itc REAL, status TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_purchases_supplier ON purchase_bills(supplier_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_purchases_date ON purchase_bills(date)");
            stmt.execute("CREATE TABLE IF NOT EXISTS stock_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id TEXT NOT NULL, transaction_date TEXT NOT NULL, voucher_type TEXT NOT NULL, voucher_id TEXT NOT NULL, voucher_no TEXT, qty_in REAL DEFAULT 0, qty_out REAL DEFAULT 0, unit_price REAL, user_id TEXT DEFAULT '', created_at TEXT)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stock_item ON stock_ledger(item_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stock_voucher ON stock_ledger(voucher_id)");
            // v4.4 — Expense accounting (direct & indirect heads)
            stmt.execute("CREATE TABLE IF NOT EXISTS expenses (id TEXT PRIMARY KEY, date TEXT, category TEXT, amount REAL, payment_mode TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY, name TEXT, hsn TEXT, unit TEXT, rate REAL, gst REAL, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS variables (key TEXT PRIMARY KEY, label TEXT, type TEXT, builtin INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, val TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS auth_session (id INTEGER PRIMARY KEY, user_id TEXT, email TEXT, display_name TEXT, id_token TEXT, refresh_token TEXT, expires_at INTEGER, remember_me INTEGER, created_at TEXT)");

            // v4.0.0 Financial Web Integration Schema
            stmt.execute("CREATE TABLE IF NOT EXISTS transports (id TEXT PRIMARY KEY, name TEXT, phone TEXT, vehicle_number TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS categories (id TEXT PRIMARY KEY, name TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS transactions (id TEXT PRIMARY KEY, buyer_id TEXT, buyer_name TEXT, book_type TEXT, transaction_type TEXT, transaction_date TEXT, due_date TEXT, amount REAL, total_quantity INTEGER, check_number TEXT, include_in_reporting INTEGER, parcel INTEGER, bill_id TEXT, bill_no TEXT, deleted INTEGER, deleted_reason TEXT, deleted_at TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_buyer ON transactions(buyer_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_date ON transactions(transaction_date)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_book ON transactions(book_type)");

            // Alter column migrations
            try { stmt.execute("ALTER TABLE items ADD COLUMN category_id TEXT"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE items ADD COLUMN category_name TEXT"); } catch (Exception ignored) {}
            // v4.1 — variable scope + default value support
            try { stmt.execute("ALTER TABLE variables ADD COLUMN scope TEXT DEFAULT 'fixed'"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE variables ADD COLUMN default_value TEXT DEFAULT ''"); } catch (Exception ignored) {}

            // v4.2 — user_id multi-user local data partitioning
            try { stmt.execute("ALTER TABLE bills ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE buyers ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE items ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE templates ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE categories ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE transports ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE transactions ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE settings ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE variables ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE meta ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_bills_user ON bills(user_id)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_buyers_user ON buyers(user_id)"); } catch (Exception ignored) {}
            // v4.3 — Purchase foundation: supplier directory + item purchase/stock groundwork
            try { stmt.execute("ALTER TABLE suppliers ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE expenses ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE purchase_bills ADD COLUMN user_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE items ADD COLUMN purchase_rate REAL DEFAULT 0"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE items ADD COLUMN current_stock REAL DEFAULT 0"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE items ADD COLUMN opening_stock REAL DEFAULT 0"); } catch (Exception ignored) {}
            try { stmt.execute("ALTER TABLE items ADD COLUMN reorder_level REAL DEFAULT 0"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_suppliers_user ON suppliers(user_id)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_expenses_user ON expenses(user_id)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_items_user ON items(user_id)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_templates_user ON templates(user_id)"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_user ON transactions(user_id)"); } catch (Exception ignored) {}

            // Purge unauthenticated legacy orphan records created by earlier pre-auth runs
            try { stmt.execute("DELETE FROM categories WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM items WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM templates WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM settings WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM variables WHERE builtin = 0 AND (user_id = '' OR user_id IS NULL)"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM transports WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM buyers WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM suppliers WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM purchase_bills WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM stock_ledger WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM expenses WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM bills WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}
            try { stmt.execute("DELETE FROM transactions WHERE user_id = '' OR user_id IS NULL"); } catch (Exception ignored) {}

            // MCP hardening: categories are auto-created BY NAME by MCP tools, so
            // (user_id, name) must be unique or two concurrent calls could create
            // duplicates. Merge existing duplicates first (keep the oldest row),
            // repoint items that referenced a merged-away category, then enforce
            // uniqueness at the DB level as the race backstop.
            try {
                stmt.execute("DELETE FROM categories WHERE rowid NOT IN (SELECT MIN(rowid) FROM categories GROUP BY user_id, LOWER(name))");
            } catch (Exception e) { e.printStackTrace(); }
            try {
                stmt.execute("UPDATE items SET category_id = (SELECT k.id FROM categories k WHERE k.user_id = items.user_id AND LOWER(k.name) = LOWER(items.category_name)) WHERE category_id IS NOT NULL AND category_id <> '' AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.id = items.category_id AND c.user_id = items.user_id)");
                stmt.execute("UPDATE items SET category_id = NULL, category_name = NULL WHERE category_id IS NOT NULL AND category_id <> '' AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.id = items.category_id AND c.user_id = items.user_id)");
            } catch (Exception e) { e.printStackTrace(); }
            try {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_user_name ON categories(user_id, name COLLATE NOCASE)");
            } catch (Exception e) { e.printStackTrace(); }

            // Ensure built-in system variables exist
            seedVariables(conn);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void seedVariables(Connection conn) {
        try {
            List<VariableDef> builtins = List.of(
                new VariableDef("buyer_name", "Buyer Name", "text", true),
                new VariableDef("buyer_address", "Buyer Address", "text", true),
                new VariableDef("buyer_gst", "Buyer GSTIN", "text", true),
                new VariableDef("buyer_phone", "Buyer Phone", "text", true),
                new VariableDef("buyer_state", "Buyer State (Place of Supply)", "text", true),
                new VariableDef("buyer_state_code", "Buyer State Code", "text", true),
                new VariableDef("buyer_city", "Buyer City", "text", true),
                new VariableDef("buyer_contact_person", "Buyer Contact Person", "text", true),
                new VariableDef("po_no", "PO / Order No", "text", true),
                new VariableDef("parcel", "Parcels / Bales Count", "number", true),
                new VariableDef("parcels", "Total Parcels", "number", true),
                new VariableDef("transport_name", "Transport Name", "text", true),
                new VariableDef("transport_phone", "Transport Phone", "text", true),
                new VariableDef("transport_contact", "Transport Contact Person", "text", true),
                new VariableDef("vehicle_no", "Vehicle No", "text", true),
                new VariableDef("e_way_bill", "E-Way Bill No", "text", true)
            );
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO variables (key, label, type, builtin) VALUES (?, ?, ?, ?)")) {
                for (VariableDef v : builtins) {
                    ps.setString(1, v.getKey());
                    ps.setString(2, v.getLabel());
                    ps.setString(3, v.getType());
                    ps.setInt(4, v.isBuiltin() ? 1 : 0);
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
