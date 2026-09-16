package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillPayment;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.DocType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BillDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public BillDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<Bill> getAllBills() {
        List<Bill> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM bills WHERE user_id = ? ORDER BY date DESC, bill_no DESC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null) {
                        list.add(mapper.readValue(json, Bill.class));
                    }
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }

    public Bill getBillById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM bills WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Bill.class);
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return null;
    }

    public Bill getBillByNo(String billNo) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || billNo == null || billNo.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM bills WHERE bill_no = ? AND user_id = ?")) {
            ps.setString(1, billNo);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Bill.class);
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return null;
    }

    public void saveBill(Bill bill) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || bill == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO bills (id, user_id, bill_no, date, doc_type, status, buyer_name, grand_total, due_amount, json_data, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, bill_no = excluded.bill_no, date = excluded.date, " +
                 "doc_type = excluded.doc_type, status = excluded.status, buyer_name = excluded.buyer_name, " +
                 "grand_total = excluded.grand_total, due_amount = excluded.due_amount, json_data = excluded.json_data, " +
                 "updated_at = excluded.updated_at WHERE bills.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (bill.getCreatedAt() == null) bill.setCreatedAt(now);
            bill.setUpdatedAt(now);

            double grand = bill.getTotals() != null ? bill.getTotals().getGrandTotal() : 0;
            double paid = bill.getPayments() != null ? bill.getPayments().stream().mapToDouble(BillPayment::getAmount).sum() : 0;
            if (paid == 0 && bill.getStatus() == BillStatus.PAID) paid = grand;
            double due = Math.max(0, grand - paid);
            if (bill.getStatus() == BillStatus.CANCELLED) due = 0;

            String buyer = bill.getVariables() != null ? bill.getVariables().getOrDefault("buyer_name", "") : "";

            ps.setString(1, bill.getId());
            ps.setString(2, uid);
            ps.setString(3, bill.getBillNo());
            ps.setString(4, bill.getDate());
            ps.setString(5, bill.getDocType().getCode());
            ps.setString(6, bill.getStatus().getCode());
            ps.setString(7, buyer);
            ps.setDouble(8, grand);
            ps.setDouble(9, due);
            ps.setString(10, mapper.writeValueAsString(bill));
            ps.setString(11, bill.getCreatedAt());
            ps.setString(12, bill.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public void deleteBill(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM bills WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public void incrementPrintCount(String id) {
        Bill b = getBillById(id);
        if (b != null) {
            b.setPrintCount(b.getPrintCount() + 1);
            saveBill(b);
        }
    }

    // --- Aliases for uniform API across views and services ---
    public List<Bill> findAll() { return getAllBills(); }
    public Bill findById(String id) { return getBillById(id); }
    public void save(Bill bill) { saveBill(bill); }
    public void insert(Bill bill) { saveBill(bill); }
    public void update(Bill bill) { saveBill(bill); }
    public void delete(String id) { deleteBill(id); }
}

