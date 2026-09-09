package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Buyer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BuyerDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public BuyerDao(DatabaseManager db) {
        this.db = db;
    }

    public List<Buyer> getAllBuyers() {
        List<Buyer> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM buyers ORDER BY name ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String json = rs.getString("json_data");
                if (json != null) {
                    list.add(mapper.readValue(json, Buyer.class));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Buyer getBuyerById(String id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM buyers WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Buyer.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Buyer findByName(String name) {
        if (name == null || name.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM buyers WHERE LOWER(name) = LOWER(?) LIMIT 1")) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Buyer.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveBuyer(Buyer buyer) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO buyers (id, name, phone, gst, state, json_data, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = excluded.name, phone = excluded.phone, gst = excluded.gst, state = excluded.state, json_data = excluded.json_data, updated_at = excluded.updated_at")) {
            String now = Instant.now().toString();
            if (buyer.getCreatedAt() == null) buyer.setCreatedAt(now);
            buyer.setUpdatedAt(now);

            ps.setString(1, buyer.getId());
            ps.setString(2, buyer.getName());
            ps.setString(3, buyer.getPhone());
            ps.setString(4, buyer.getGst());
            ps.setString(5, buyer.getState());
            ps.setString(6, mapper.writeValueAsString(buyer));
            ps.setString(7, buyer.getCreatedAt());
            ps.setString(8, buyer.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteBuyer(String id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM buyers WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Aliases for uniform API across views and services ---
    public List<Buyer> findAll() { return getAllBuyers(); }
    public Buyer findById(String id) { return getBuyerById(id); }
    public void save(Buyer buyer) { saveBuyer(buyer); }
    public void insert(Buyer buyer) { saveBuyer(buyer); }
    public void update(Buyer buyer) { saveBuyer(buyer); }
    public void delete(String id) { deleteBuyer(id); }
}

