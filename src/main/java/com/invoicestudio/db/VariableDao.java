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

    public List<VariableDef> getAllVariables() {
        List<VariableDef> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM variables ORDER BY builtin DESC, label ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new VariableDef(
                    rs.getString("key"),
                    rs.getString("label"),
                    rs.getString("type"),
                    rs.getInt("builtin") == 1
                ));
            }
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
            while (rs.next()) {
                list.add(new VariableDef(
                    rs.getString("key"),
                    rs.getString("label"),
                    rs.getString("type"),
                    false
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void saveVariable(VariableDef v) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO variables (key, label, type, builtin) VALUES (?, ?, ?, ?) ON CONFLICT(key) DO UPDATE SET label = excluded.label, type = excluded.type")) {
            ps.setString(1, v.getKey());
            ps.setString(2, v.getLabel());
            ps.setString(3, v.getType());
            ps.setInt(4, v.isBuiltin() ? 1 : 0);
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

