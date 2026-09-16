package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.PurchaseBill;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence for purchase bills (supplier inward supply register).
 * Mirrors {@link BillDao}: scalar columns for fast listing + full JSON payload.
 */
public class PurchaseBillDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public PurchaseBillDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<PurchaseBill> getAllPurchaseBills() {
        List<PurchaseBill> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT json_data FROM purchase_bills WHERE user_id = ? ORDER BY date DESC, bill_no DESC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null) {
                        list.add(mapper.readValue(json, PurchaseBill.class));
                    }
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }

    public PurchaseBill getPurchaseBillById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM purchase_bills WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), PurchaseBill.class);
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return null;
    }

    public void savePurchaseBill(PurchaseBill bill) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || bill == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO purchase_bills (id, user_id, bill_no, supplier_bill_no, date, supplier_id, supplier_name, total, itc, status, json_data, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, bill_no = excluded.bill_no, " +
                 "supplier_bill_no = excluded.supplier_bill_no, date = excluded.date, supplier_id = excluded.supplier_id, " +
                 "supplier_name = excluded.supplier_name, total = excluded.total, itc = excluded.itc, status = excluded.status, " +
                 "json_data = excluded.json_data, updated_at = excluded.updated_at WHERE purchase_bills.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (bill.getCreatedAt() == null) bill.setCreatedAt(now);
            bill.setUpdatedAt(now);

            double grand = bill.getAmountPayable();
            double itc = bill.getTotals() != null
                    ? bill.getTotals().getCgst() + bill.getTotals().getSgst() + bill.getTotals().getIgst()
                    : 0;

            ps.setString(1, bill.getId());
            ps.setString(2, uid);
            ps.setString(3, bill.getBillNo());
            ps.setString(4, bill.getSupplierBillNo());
            ps.setString(5, bill.getDate());
            ps.setString(6, bill.getSupplierId());
            ps.setString(7, bill.getSupplierName());
            ps.setDouble(8, grand);
            ps.setDouble(9, itc);
            ps.setString(10, bill.isPaid() ? "PAID" : "UNPAID");
            ps.setString(11, mapper.writeValueAsString(bill));
            ps.setString(12, bill.getCreatedAt());
            ps.setString(13, bill.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public void deletePurchaseBill(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM purchase_bills WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    // --- Aliases for uniform API across views and services ---
    public List<PurchaseBill> findAll() { return getAllPurchaseBills(); }
    public PurchaseBill findById(String id) { return getPurchaseBillById(id); }
    public void save(PurchaseBill bill) { savePurchaseBill(bill); }
    public void insert(PurchaseBill bill) { savePurchaseBill(bill); }
    public void update(PurchaseBill bill) { savePurchaseBill(bill); }
    public void delete(String id) { deletePurchaseBill(id); }
}
