package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Template;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TemplateDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public TemplateDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<Template> getAllTemplates() {
        List<Template> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM templates WHERE user_id = ? ORDER BY name ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null) {
                        list.add(mapper.readValue(json, Template.class));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Template getTemplateById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM templates WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Template.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveTemplate(Template template) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || template == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO templates (id, user_id, name, json_data, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, json_data = excluded.json_data, updated_at = excluded.updated_at WHERE templates.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (template.getCreatedAt() == null) template.setCreatedAt(now);
            template.setUpdatedAt(now);

            ps.setString(1, template.getId());
            ps.setString(2, uid);
            ps.setString(3, template.getName());
            ps.setString(4, mapper.writeValueAsString(template));
            ps.setString(5, template.getCreatedAt());
            ps.setString(6, template.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteTemplate(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM templates WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Aliases for uniform API across views and services ---
    public List<Template> findAll() { return getAllTemplates(); }
    public Template findById(String id) { return getTemplateById(id); }
    public void save(Template template) { saveTemplate(template); }
    public void insert(Template template) { saveTemplate(template); }
    public void update(Template template) { saveTemplate(template); }
    public void delete(String id) { deleteTemplate(id); }
}

