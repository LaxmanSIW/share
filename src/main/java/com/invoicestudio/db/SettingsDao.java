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

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public boolean hasNoSettingsForUser(String uid) {
        if (uid == null || uid.isBlank()) return true;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM settings WHERE user_id = ? LIMIT 1")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next();
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
            return true;
        }
    }

    public Settings getSettings() {
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return new Settings();
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM settings WHERE user_id = ? LIMIT 1")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null && !json.isBlank()) {
                        return mapper.readValue(json, Settings.class);
                    }
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return new Settings();
    }

    public void saveSettings(Settings settings) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || settings == null) {
            return;
        }
        try (Connection conn = db.getConnection()) {
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM settings WHERE user_id = ? LIMIT 1")) {
                ps.setString(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) exists = true;
                }
            }
            if (exists) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE settings SET json_data = ? WHERE user_id = ?")) {
                    ps.setString(1, mapper.writeValueAsString(settings));
                    ps.setString(2, uid);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO settings (user_id, json_data) VALUES (?, ?)")) {
                    ps.setString(1, uid);
                    ps.setString(2, mapper.writeValueAsString(settings));
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
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

