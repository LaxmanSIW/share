package com.invoicestudio.db;

import com.invoicestudio.model.LabelPrintHistory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read/write access to {@code label_print_history} — an info-only audit of
 * every Bulk Label Print run (when, which template, which printer, how many).
 */
public class LabelPrintHistoryDao {
    private final DatabaseManager db;

    public LabelPrintHistoryDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    /** Appends one print run. Never throws — history must not block printing. */
    public void insert(LabelPrintHistory h) {
        if (h == null) return;
        String uid = getEffectiveUserId();
        if (uid == null || uid.isEmpty()) uid = "";
        if (h.getId() == null || h.getId().isBlank()) {
            h.setId("lph_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO label_print_history (id, template_id, template_name, printer_name, label_width, label_height, " +
                     "columns, pages, labels, total_copies, summary, lines_json, user_id, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, h.getId());
            ps.setString(2, h.getTemplateId());
            ps.setString(3, h.getTemplateName());
            ps.setString(4, h.getPrinterName());
            ps.setDouble(5, h.getLabelWidth());
            ps.setDouble(6, h.getLabelHeight());
            ps.setInt(7, h.getColumns());
            ps.setInt(8, h.getPages());
            ps.setInt(9, h.getLabels());
            ps.setInt(10, h.getTotalCopies());
            ps.setString(11, h.getSummary());
            ps.setString(12, h.getLinesJson());
            ps.setString(13, uid);
            ps.setString(14, h.getCreatedAt() != null && !h.getCreatedAt().isBlank()
                    ? h.getCreatedAt() : java.time.Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public List<LabelPrintHistory> getAll() {
        List<LabelPrintHistory> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid == null || uid.isEmpty()) return list;
        String sql = "SELECT * FROM label_print_history WHERE user_id = ? ORDER BY created_at DESC LIMIT 1000";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }

    /** Deletes one run. Returns true when a row was removed. */
    public boolean delete(String id) {
        String uid = getEffectiveUserId();
        if (uid == null || uid.isEmpty() || id == null || id.isBlank()) return false;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM label_print_history WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            com.invoicestudio.service.AppLog.error(e);
            return false;
        }
    }

    /** Clears the whole history for the current user. Returns rows removed. */
    public int clearAll() {
        String uid = getEffectiveUserId();
        if (uid == null || uid.isEmpty()) return 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM label_print_history WHERE user_id = ?")) {
            ps.setString(1, uid);
            return ps.executeUpdate();
        } catch (SQLException e) {
            com.invoicestudio.service.AppLog.error(e);
            return 0;
        }
    }

    private LabelPrintHistory fromResultSet(ResultSet rs) throws SQLException {
        LabelPrintHistory h = new LabelPrintHistory();
        h.setId(rs.getString("id"));
        h.setTemplateId(rs.getString("template_id"));
        h.setTemplateName(rs.getString("template_name"));
        h.setPrinterName(rs.getString("printer_name"));
        h.setLabelWidth(rs.getDouble("label_width"));
        h.setLabelHeight(rs.getDouble("label_height"));
        h.setColumns(rs.getInt("columns"));
        h.setPages(rs.getInt("pages"));
        h.setLabels(rs.getInt("labels"));
        h.setTotalCopies(rs.getInt("total_copies"));
        h.setSummary(rs.getString("summary"));
        h.setLinesJson(rs.getString("lines_json"));
        h.setUserId(rs.getString("user_id"));
        h.setCreatedAt(rs.getString("created_at"));
        return h;
    }
}
