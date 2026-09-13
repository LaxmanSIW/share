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

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<ItemRecord> getAllItems() {
        List<ItemRecord> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM items WHERE user_id = ? ORDER BY name ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
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
                    try { it.setPurchaseRate(rs.getDouble("purchase_rate")); } catch (Exception ignored) {}
                    try { it.setCurrentStock(rs.getDouble("current_stock")); } catch (Exception ignored) {}
                    try { it.setOpeningStock(rs.getDouble("opening_stock")); } catch (Exception ignored) {}
                    try { it.setReorderLevel(rs.getDouble("reorder_level")); } catch (Exception ignored) {}
                    it.setCreatedAt(rs.getString("created_at"));
                    it.setUpdatedAt(rs.getString("updated_at"));
                    list.add(it);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public ItemRecord getItemById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM items WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
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
                    try { it.setPurchaseRate(rs.getDouble("purchase_rate")); } catch (Exception ignored) {}
                    try { it.setCurrentStock(rs.getDouble("current_stock")); } catch (Exception ignored) {}
                    try { it.setOpeningStock(rs.getDouble("opening_stock")); } catch (Exception ignored) {}
                    try { it.setReorderLevel(rs.getDouble("reorder_level")); } catch (Exception ignored) {}
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
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || item == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO items (id, user_id, name, hsn, unit, rate, gst, category_id, category_name, purchase_rate, current_stock, opening_stock, reorder_level, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, hsn = excluded.hsn, unit = excluded.unit, " +
                 "rate = excluded.rate, gst = excluded.gst, category_id = excluded.category_id, " +
                 "category_name = excluded.category_name, purchase_rate = excluded.purchase_rate, current_stock = excluded.current_stock, " +
                 "opening_stock = excluded.opening_stock, reorder_level = excluded.reorder_level, updated_at = excluded.updated_at WHERE items.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (item.getCreatedAt() == null) item.setCreatedAt(now);
            item.setUpdatedAt(now);

            ps.setString(1, item.getId());
            ps.setString(2, uid);
            ps.setString(3, item.getName());
            ps.setString(4, item.getHsn());
            ps.setString(5, item.getUnit());
            ps.setDouble(6, item.getRate());
            ps.setDouble(7, item.getGst());
            ps.setString(8, item.getCategoryId());
            ps.setString(9, item.getCategoryName());
            ps.setDouble(10, item.getPurchaseRate());
            ps.setDouble(11, item.getCurrentStock());
            ps.setDouble(12, item.getOpeningStock());
            ps.setDouble(13, item.getReorderLevel());
            ps.setString(14, item.getCreatedAt());
            ps.setString(15, item.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteItem(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        if ("item_pent".equalsIgnoreCase(id)) return; // Protected default item
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM items WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
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

