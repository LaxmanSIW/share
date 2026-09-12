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

    public List<VariableDef> getAllVariables() {
        List<VariableDef> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM variables ORDER BY builtin DESC, label ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(fromResultSet(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<VariableDef> getCustomVariables() {
        List<VariableDef> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM variables WHERE builtin = 0 ORDER BY label ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(fromResultSet(rs));
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
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM variables WHERE builtin = 0 AND scope = 'table' ORDER BY label ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(fromResultSet(rs));
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
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM variables WHERE builtin = 0 AND (scope = 'fixed' OR scope IS NULL) ORDER BY label ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(fromResultSet(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void saveVariable(VariableDef v) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO variables (key, label, type, builtin, scope, default_value) VALUES (?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(key) DO UPDATE SET label = excluded.label, type = excluded.type, " +
                     "scope = excluded.scope, default_value = excluded.default_value")) {
            ps.setString(1, v.getKey());
            ps.setString(2, v.getLabel());
            ps.setString(3, v.getType());
            ps.setInt(4, v.isBuiltin() ? 1 : 0);
            ps.setString(5, v.getScope());
            ps.setString(6, v.getDefaultValue());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean deleteVariable(String key) {
        if (key == null || key.isBlank()) return false;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM variables WHERE key = ?")) {
            ps.setString(1, key.trim());
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
