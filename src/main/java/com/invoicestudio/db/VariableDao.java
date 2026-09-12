package com.invoicestudio.db;

import com.invoicestudio.model.VariableDef;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class VariableDao {
    private final DatabaseManager db;

    public VariableDao(DatabaseManager db) {
        this.db = db;
    }

    /** Reads all columns including the new scope and default_value columns. */
    private VariableDef fromResultSet(ResultSet rs) throws Exception {
        VariableDef v = new VariableDef(
            rs.getString("key"),
            rs.getString("label"),
            rs.getString("type"),
            rs.getInt("builtin") == 1
        );
        String scope = rs.getString("scope");
        v.setScope(scope != null ? scope : "fixed");
        String defVal = rs.getString("default_value");
        v.setDefaultValue(defVal != null ? defVal : "");
        return v;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<VariableDef> getAllVariables() {
        List<VariableDef> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        String sql = uid.isEmpty()
                ? "SELECT * FROM variables WHERE builtin = 1 ORDER BY builtin DESC, label ASC"
                : "SELECT * FROM variables WHERE builtin = 1 OR user_id = ? ORDER BY builtin DESC, label ASC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!uid.isEmpty()) {
                ps.setString(1, uid);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<VariableDef> getCustomVariables() {
        List<VariableDef> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return list;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM variables WHERE builtin = 0 AND user_id = ? ORDER BY label ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Returns only user-created variables with scope = 'table'.
     * Used by TemplateDesigner's column picker to offer custom table columns.
     */
    public List<VariableDef> getTableScopeVariables() {
        List<VariableDef> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return list;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM variables WHERE builtin = 0 AND scope = 'table' AND user_id = ? ORDER BY label ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Returns only user-created variables with scope = 'fixed'.
     * Used by CreateBillView to render per-bill custom input fields.
     */
    public List<VariableDef> getFixedScopeVariables() {
        List<VariableDef> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return list;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM variables WHERE builtin = 0 AND (scope = 'fixed' OR scope IS NULL) AND user_id = ? ORDER BY label ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void saveVariable(VariableDef v) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || v == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO variables (key, user_id, label, type, builtin, scope, default_value) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(key) DO UPDATE SET user_id = excluded.user_id, label = excluded.label, type = excluded.type, " +
                     "scope = excluded.scope, default_value = excluded.default_value WHERE variables.builtin = 0")) {
            ps.setString(1, v.getKey());
            ps.setString(2, uid);
            ps.setString(3, v.getLabel());
            ps.setString(4, v.getType());
            ps.setInt(5, v.isBuiltin() ? 1 : 0);
            ps.setString(6, v.getScope());
            ps.setString(7, v.getDefaultValue());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean deleteVariable(String key) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || key == null || key.isBlank()) return false;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM variables WHERE key = ? AND builtin = 0 AND user_id = ?")) {
            ps.setString(1, key.trim());
            ps.setString(2, uid);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- Aliases for uniform API across views and services ---
    public List<VariableDef> findAll() { return getAllVariables(); }
    public List<VariableDef> findCustom() { return getCustomVariables(); }
    public void save(VariableDef v) { saveVariable(v); }
    public void insert(VariableDef v) { saveVariable(v); }
    public void update(VariableDef v) { saveVariable(v); }
    public void delete(String key) { deleteVariable(key); }
}
