package com.invoicestudio.db;

import com.invoicestudio.model.Transport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TransportDao {
    private final DatabaseManager db;

    public TransportDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<Transport> getAllTransports() {
        List<Transport> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM transports WHERE user_id = ? ORDER BY name ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Transport t = new Transport(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("vehicle_number")
                    );
                    t.setCreatedAt(rs.getString("created_at"));
                    t.setUpdatedAt(rs.getString("updated_at"));
                    list.add(t);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Transport getTransportById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM transports WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Transport t = new Transport(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("vehicle_number")
                    );
                    t.setCreatedAt(rs.getString("created_at"));
                    t.setUpdatedAt(rs.getString("updated_at"));
                    return t;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveTransport(Transport t) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || t == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO transports (id, user_id, name, phone, vehicle_number, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, phone = excluded.phone, " +
                 "vehicle_number = excluded.vehicle_number, updated_at = excluded.updated_at WHERE transports.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (t.getCreatedAt() == null || t.getCreatedAt().isBlank()) t.setCreatedAt(now);
            t.setUpdatedAt(now);

            ps.setString(1, t.getId());
            ps.setString(2, uid);
            ps.setString(3, t.getName());
            ps.setString(4, t.getPhone());
            ps.setString(5, t.getVehicleNumber());
            ps.setString(6, t.getCreatedAt());
            ps.setString(7, t.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteTransport(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM transports WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Transport findByName(String name) {
        if (name == null || name.isBlank()) return null;
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM transports WHERE LOWER(name) = LOWER(?) AND user_id = ? LIMIT 1")) {
            ps.setString(1, name.trim());
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Transport t = new Transport(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("vehicle_number")
                    );
                    t.setCreatedAt(rs.getString("created_at"));
                    t.setUpdatedAt(rs.getString("updated_at"));
                    return t;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Transport> findAll() { return getAllTransports(); }
    public Transport findById(String id) { return getTransportById(id); }
}
