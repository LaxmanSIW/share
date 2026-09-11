package com.invoicestudio.db;

import com.invoicestudio.model.ItemRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ItemDao {
    private final DatabaseManager db;

    public ItemDao(DatabaseManager db) {
        this.db = db;
    }

    public List<ItemRecord> getAllItems() {
        List<ItemRecord> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM items ORDER BY name ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ItemRecord it = new ItemRecord(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("hsn"),
                    rs.getString("unit"),
                    rs.getDouble("rate"),
                    rs.getDouble("gst")
                );
                try {
                    it.setCategoryId(rs.getString("category_id"));
                    it.setCategoryName(rs.getString("category_name"));
                } catch (Exception ignored) {}
                it.setCreatedAt(rs.getString("created_at"));
                it.setUpdatedAt(rs.getString("updated_at"));
                list.add(it);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public ItemRecord getItemById(String id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM items WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ItemRecord it = new ItemRecord(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("hsn"),
                        rs.getString("unit"),
                        rs.getDouble("rate"),
                        rs.getDouble("gst")
                    );
                    try {
                        it.setCategoryId(rs.getString("category_id"));
                        it.setCategoryName(rs.getString("category_name"));
                    } catch (Exception ignored) {}
                    it.setCreatedAt(rs.getString("created_at"));
                    it.setUpdatedAt(rs.getString("updated_at"));
                    return it;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveItem(ItemRecord item) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO items (id, name, hsn, unit, rate, gst, category_id, category_name, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET name = excluded.name, hsn = excluded.hsn, unit = excluded.unit, " +
                 "rate = excluded.rate, gst = excluded.gst, category_id = excluded.category_id, " +
                 "category_name = excluded.category_name, updated_at = excluded.updated_at")) {
            String now = Instant.now().toString();
            if (item.getCreatedAt() == null) item.setCreatedAt(now);
            item.setUpdatedAt(now);

            ps.setString(1, item.getId());
            ps.setString(2, item.getName());
            ps.setString(3, item.getHsn());
            ps.setString(4, item.getUnit());
            ps.setDouble(5, item.getRate());
            ps.setDouble(6, item.getGst());
            ps.setString(7, item.getCategoryId());
            ps.setString(8, item.getCategoryName());
            ps.setString(9, item.getCreatedAt());
            ps.setString(10, item.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteItem(String id) {
        if (id == null || id.isBlank()) return;
        if ("item_pent".equalsIgnoreCase(id)) return; // Protected default item
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM items WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Aliases for uniform API across views and services ---
    public List<ItemRecord> findAll() { return getAllItems(); }
    public ItemRecord findById(String id) { return getItemById(id); }
    public void save(ItemRecord item) { saveItem(item); }
    public void insert(ItemRecord item) { saveItem(item); }
    public void update(ItemRecord item) { saveItem(item); }
    public void delete(String id) { deleteItem(id); }
}

