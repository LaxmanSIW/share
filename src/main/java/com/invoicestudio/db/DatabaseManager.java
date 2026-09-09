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
                new VariableDef("po_no", "PO / Order No", "text", true),
                new VariableDef("transport_name", "Transport Name", "text", true),
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
