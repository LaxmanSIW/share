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
        seedIfEmpty();
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
            stmt.execute("CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY, name TEXT, hsn TEXT, unit TEXT, rate REAL, gst REAL, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS variables (key TEXT PRIMARY KEY, label TEXT, type TEXT, builtin INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, val TEXT)");

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
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void seedIfEmpty() {
        try (Connection conn = getConnection()) {
            // Check settings
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM settings")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedSettings(conn);
                }
            }
            // Check templates
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM templates")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedTemplates(conn);
                }
            }
            // Check buyers
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM buyers")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedBuyers(conn);
                }
            }
            // Check items
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM items")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedItems(conn);
                }
            }
            // Check categories
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM categories")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedCategories(conn);
                }
            }
            // Check transports
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM transports")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedTransports(conn);
                }
            }
            // Check bills
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM bills")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedBills(conn);
                }
            }
            // Check variables
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM variables")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedVariables(conn);
                }
            }
            // Check transactions
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM transactions")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    seedTransactions(conn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void seedSettings(Connection conn) {
        try {
            Settings settings = null;
            InputStream is = getClass().getResourceAsStream("/seed/settings.json");
            if (is != null) {
                settings = mapper.readValue(is, Settings.class);
            } else {
                File f = new File("nextjs_source/db/settings.json");
                if (f.exists()) settings = mapper.readValue(f, Settings.class);
            }
            if (settings == null) settings = new Settings();

            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO settings (id, json_data) VALUES (1, ?)")) {
                ps.setString(1, mapper.writeValueAsString(settings));
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void seedTemplates(Connection conn) {
        try {
            List<Template> list = null;
            InputStream is = getClass().getResourceAsStream("/seed/templates.json");
            if (is != null) {
                list = mapper.readValue(is, new TypeReference<List<Template>>() {});
            } else {
                File f = new File("nextjs_source/db/templates.json");
                if (f.exists()) list = mapper.readValue(f, new TypeReference<List<Template>>() {});
            }
            if (list == null || list.isEmpty()) {
                list = PresetTemplates.getAllPresets();
            }
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO templates (id, name, json_data, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")) {
                for (Template t : list) {
                    ps.setString(1, t.getId());
                    ps.setString(2, t.getName());
                    ps.setString(3, mapper.writeValueAsString(t));
                    ps.setString(4, t.getCreatedAt());
                    ps.setString(5, t.getUpdatedAt());
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void seedBuyers(Connection conn) {
        try {
            List<Buyer> list = null;
            InputStream is = getClass().getResourceAsStream("/seed/buyers.json");
            if (is != null) {
                list = mapper.readValue(is, new TypeReference<List<Buyer>>() {});
            } else {
                File f = new File("nextjs_source/db/buyers.json");
                if (f.exists()) list = mapper.readValue(f, new TypeReference<List<Buyer>>() {});
            }
            if (list != null) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO buyers (id, name, phone, gst, state, json_data, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (Buyer b : list) {
                        ps.setString(1, b.getId());
                        ps.setString(2, b.getName());
                        ps.setString(3, b.getPhone());
                        ps.setString(4, b.getGst());
                        ps.setString(5, b.getState());
                        ps.setString(6, mapper.writeValueAsString(b));
                        ps.setString(7, b.getCreatedAt());
                        ps.setString(8, b.getUpdatedAt());
                        ps.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void seedItems(Connection conn) {
        try {
            List<ItemRecord> list = null;
            InputStream is = getClass().getResourceAsStream("/seed/items.json");
            if (is != null) {
                list = mapper.readValue(is, new TypeReference<List<ItemRecord>>() {});
            } else {
                File f = new File("nextjs_source/db/items.json");
                if (f.exists()) list = mapper.readValue(f, new TypeReference<List<ItemRecord>>() {});
            }
            if (list != null) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO items (id, name, hsn, unit, rate, gst, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (ItemRecord it : list) {
                        ps.setString(1, it.getId());
                        ps.setString(2, it.getName());
                        ps.setString(3, it.getHsn());
                        ps.setString(4, it.getUnit());
                        ps.setDouble(5, it.getRate());
                        ps.setDouble(6, it.getGst());
                        ps.setString(7, it.getCreatedAt());
                        ps.setString(8, it.getUpdatedAt());
                        ps.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void seedBills(Connection conn) {
        try {
            List<Bill> list = null;
            InputStream is = getClass().getResourceAsStream("/seed/bills.json");
            if (is != null) {
                list = mapper.readValue(is, new TypeReference<List<Bill>>() {});
            } else {
                File f = new File("nextjs_source/db/bills.json");
                if (f.exists()) list = mapper.readValue(f, new TypeReference<List<Bill>>() {});
            }
            if (list != null) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO bills (id, bill_no, date, doc_type, status, buyer_name, grand_total, due_amount, json_data, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (Bill b : list) {
                        ps.setString(1, b.getId());
                        ps.setString(2, b.getBillNo());
                        ps.setString(3, b.getDate());
                        ps.setString(4, b.getDocType().getCode());
                        ps.setString(5, b.getStatus().getCode());
                        ps.setString(6, b.getVariables().getOrDefault("buyer_name", ""));
                        ps.setDouble(7, b.getTotals() != null ? b.getTotals().getGrandTotal() : 0);
                        double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
                        if (paid == 0 && b.getStatus() == BillStatus.PAID) paid = b.getTotals().getGrandTotal();
                        ps.setDouble(8, Math.max(0, (b.getTotals() != null ? b.getTotals().getGrandTotal() : 0) - paid));
                        ps.setString(9, mapper.writeValueAsString(b));
                        ps.setString(10, b.getCreatedAt());
                        ps.setString(11, b.getUpdatedAt());
                        ps.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
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

    private void seedCategories(Connection conn) {
        try {
            List<String[]> defaultCats = List.of(
                new String[]{"cat_trousers", "Trousers"},
                new String[]{"cat_shirts", "Formal Shirts"},
                new String[]{"cat_denim", "Denim Wear"},
                new String[]{"cat_casual", "Casuals"},
                new String[]{"cat_accessories", "Accessories"}
            );
            String now = java.time.Instant.now().toString();
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO categories (id, name, created_at, updated_at) VALUES (?, ?, ?, ?)")) {
                for (String[] c : defaultCats) {
                    ps.setString(1, c[0]);
                    ps.setString(2, c[1]);
                    ps.setString(3, now);
                    ps.setString(4, now);
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void seedTransports(Connection conn) {
        try {
            List<String[]> defaultTransports = List.of(
                new String[]{"trp_safex", "Safe Express Logistics", "9820011223", "MH-12-AB-1234"},
                new String[]{"trp_vrl", "VRL Logistics Ltd", "9820044556", "KA-25-CD-5678"},
                new String[]{"trp_delhivery", "Delhivery Surface", "9820077889", "DL-01-EF-9012"},
                new String[]{"trp_tci", "TCI Freight Express", "9820099001", "MH-04-GH-3456"}
            );
            String now = java.time.Instant.now().toString();
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO transports (id, name, phone, vehicle_number, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (String[] t : defaultTransports) {
                    ps.setString(1, t[0]);
                    ps.setString(2, t[1]);
                    ps.setString(3, t[2]);
                    ps.setString(4, t[3]);
                    ps.setString(5, now);
                    ps.setString(6, now);
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void seedTransactions(Connection conn) {
        try {
            // Check if bills exist; if so, create initial synchronized transactions from existing bills
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id, bill_no, date, buyer_name, grand_total, json_data FROM bills WHERE doc_type = 'INV' LIMIT 20")) {
                String now = java.time.Instant.now().toString();
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO transactions (id, buyer_id, buyer_name, book_type, transaction_type, transaction_date, due_date, amount, total_quantity, check_number, include_in_reporting, parcel, bill_id, bill_no, deleted, deleted_reason, deleted_at, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'CC', 'sale', ?, ?, ?, ?, '', 1, 1, ?, ?, 0, '', '', ?, ?)")) {
                    while (rs.next()) {
                        String billId = rs.getString("id");
                        String billNo = rs.getString("bill_no");
                        String date = rs.getString("date");
                        String buyer = rs.getString("buyer_name");
                        double amount = rs.getDouble("grand_total");
                        String txId = "tx_inv_" + billId.replace("-", "").substring(0, Math.min(10, billId.length()));

                        ps.setString(1, txId);
                        ps.setString(2, "byr_linked");
                        ps.setString(3, buyer != null ? buyer : "Walk-in Customer");
                        ps.setString(4, date != null && !date.isBlank() ? date : java.time.LocalDate.now().toString());
                        ps.setString(5, date != null && !date.isBlank() ? date : java.time.LocalDate.now().toString());
                        ps.setDouble(6, amount);
                        ps.setInt(7, 50); // Sample qty
                        ps.setString(8, billId);
                        ps.setString(9, billNo);
                        ps.setString(10, now);
                        ps.setString(11, now);
                        ps.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

