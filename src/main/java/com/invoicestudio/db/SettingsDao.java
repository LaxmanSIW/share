package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Settings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SettingsDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public SettingsDao(DatabaseManager db) {
        this.db = db;
    }

    public Settings getSettings() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM settings WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String json = rs.getString("json_data");
                if (json != null && !json.isBlank()) {
                    return mapper.readValue(json, Settings.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Settings();
    }

    public void saveSettings(Settings settings) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO settings (id, json_data) VALUES (1, ?) ON CONFLICT(id) DO UPDATE SET json_data = excluded.json_data")) {
            ps.setString(1, mapper.writeValueAsString(settings));
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized int getNextBillNumberAndIncrement() {
        Settings s = getSettings();
        int current = s.getBillNoNext();
        s.setBillNoNext(current + 1);
        saveSettings(s);
        return current;
    }

    // --- Aliases for uniform API across views and services ---
    public Settings get() { return getSettings(); }
    public void save(Settings settings) { saveSettings(settings); }
}

