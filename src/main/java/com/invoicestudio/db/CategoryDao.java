package com.invoicestudio.db;

import com.invoicestudio.model.ItemCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CategoryDao {
    private final DatabaseManager db;

    public CategoryDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<ItemCategory> getAllCategories() {
        List<ItemCategory> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM categories WHERE user_id = ? ORDER BY name ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemCategory cat = new ItemCategory(
                        rs.getString("id"),
                        rs.getString("name")
                    );
                    cat.setCreatedAt(rs.getString("created_at"));
                    cat.setUpdatedAt(rs.getString("updated_at"));
                    list.add(cat);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public ItemCategory getCategoryById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM categories WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ItemCategory cat = new ItemCategory(
                        rs.getString("id"),
                        rs.getString("name")
                    );
                    cat.setCreatedAt(rs.getString("created_at"));
                    cat.setUpdatedAt(rs.getString("updated_at"));
                    return cat;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveCategory(ItemCategory cat) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || cat == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO categories (id, user_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, updated_at = excluded.updated_at WHERE categories.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (cat.getCreatedAt() == null || cat.getCreatedAt().isBlank()) cat.setCreatedAt(now);
            cat.setUpdatedAt(now);

            ps.setString(1, cat.getId());
            ps.setString(2, uid);
            ps.setString(3, cat.getName());
            ps.setString(4, cat.getCreatedAt());
            ps.setString(5, cat.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteCategory(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        if ("cat_trouser".equalsIgnoreCase(id) || "cat_trousers".equalsIgnoreCase(id)) return; // Protected default category
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM categories WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<ItemCategory> findAll() { return getAllCategories(); }
    public ItemCategory findById(String id) { return getCategoryById(id); }
}
