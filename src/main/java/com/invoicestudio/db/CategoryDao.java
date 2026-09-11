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

    public List<ItemCategory> getAllCategories() {
        List<ItemCategory> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM categories ORDER BY name ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ItemCategory cat = new ItemCategory(
                    rs.getString("id"),
                    rs.getString("name")
                );
                cat.setCreatedAt(rs.getString("created_at"));
                cat.setUpdatedAt(rs.getString("updated_at"));
                list.add(cat);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public ItemCategory getCategoryById(String id) {
        if (id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM categories WHERE id = ?")) {
            ps.setString(1, id);
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
        if (cat == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO categories (id, name, created_at, updated_at) VALUES (?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET name = excluded.name, updated_at = excluded.updated_at")) {
            String now = Instant.now().toString();
            if (cat.getCreatedAt() == null || cat.getCreatedAt().isBlank()) cat.setCreatedAt(now);
            cat.setUpdatedAt(now);

            ps.setString(1, cat.getId());
            ps.setString(2, cat.getName());
            ps.setString(3, cat.getCreatedAt());
            ps.setString(4, cat.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteCategory(String id) {
        if (id == null || id.isBlank()) return;
        if ("cat_trouser".equalsIgnoreCase(id) || "cat_trousers".equalsIgnoreCase(id)) return; // Protected default category
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM categories WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<ItemCategory> findAll() { return getAllCategories(); }
    public ItemCategory findById(String id) { return getCategoryById(id); }
}
