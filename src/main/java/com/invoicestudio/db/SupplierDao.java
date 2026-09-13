package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Supplier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SupplierDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public SupplierDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    /** All suppliers of the current authenticated user, ordered by firm name. */
    public List<Supplier> getAllSuppliers() {
        List<Supplier> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM suppliers WHERE user_id = ? ORDER BY name ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null) {
                        list.add(mapper.readValue(json, Supplier.class));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Supplier getSupplierById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM suppliers WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Supplier.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Case-insensitive firm-name lookup (used for duplicate detection on save). */
    public Supplier findByName(String name) {
        if (name == null || name.isBlank()) return null;
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM suppliers WHERE LOWER(name) = LOWER(?) AND user_id = ? LIMIT 1")) {
            ps.setString(1, name.trim());
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Supplier.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Insert-or-update (upsert) scoped to the current user. */
    public void saveSupplier(Supplier supplier) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || supplier == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO suppliers (id, user_id, name, phone, gst, state, json_data, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, phone = excluded.phone, " +
                 "gst = excluded.gst, state = excluded.state, json_data = excluded.json_data, updated_at = excluded.updated_at " +
                 "WHERE suppliers.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (supplier.getCreatedAt() == null) supplier.setCreatedAt(now);
            supplier.setUpdatedAt(now);

            ps.setString(1, supplier.getId());
            ps.setString(2, uid);
            ps.setString(3, supplier.getName());
            ps.setString(4, supplier.getPhone());
            ps.setString(5, supplier.getGst());
            ps.setString(6, supplier.getState());
            ps.setString(7, mapper.writeValueAsString(supplier));
            ps.setString(8, supplier.getCreatedAt());
            ps.setString(9, supplier.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteSupplier(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM suppliers WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Number of suppliers for the current user (used by KPI header). */
    public int count() {
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM suppliers WHERE user_id = ?")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("c");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    // --- Aliases for uniform API across views and services ---
    public List<Supplier> findAll() { return getAllSuppliers(); }
    public Supplier findById(String id) { return getSupplierById(id); }
    public void save(Supplier supplier) { saveSupplier(supplier); }
    public void insert(Supplier supplier) { saveSupplier(supplier); }
    public void update(Supplier supplier) { saveSupplier(supplier); }
    public void delete(String id) { deleteSupplier(id); }
}
