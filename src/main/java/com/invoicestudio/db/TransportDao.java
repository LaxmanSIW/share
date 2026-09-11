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

    public List<Transport> getAllTransports() {
        List<Transport> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM transports ORDER BY name ASC");
             ResultSet rs = ps.executeQuery()) {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Transport getTransportById(String id) {
        if (id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM transports WHERE id = ?")) {
            ps.setString(1, id);
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
        if (t == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO transports (id, name, phone, vehicle_number, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET name = excluded.name, phone = excluded.phone, " +
                 "vehicle_number = excluded.vehicle_number, updated_at = excluded.updated_at")) {
            String now = Instant.now().toString();
            if (t.getCreatedAt() == null || t.getCreatedAt().isBlank()) t.setCreatedAt(now);
            t.setUpdatedAt(now);

            ps.setString(1, t.getId());
            ps.setString(2, t.getName());
            ps.setString(3, t.getPhone());
            ps.setString(4, t.getVehicleNumber());
            ps.setString(5, t.getCreatedAt());
            ps.setString(6, t.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteTransport(String id) {
        if (id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM transports WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Transport> findAll() { return getAllTransports(); }
    public Transport findById(String id) { return getTransportById(id); }
}
