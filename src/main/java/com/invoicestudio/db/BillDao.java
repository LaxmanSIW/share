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

    public List<Bill> getAllBills() {
        List<Bill> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM bills ORDER BY date DESC, bill_no DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String json = rs.getString("json_data");
                if (json != null) {
                    list.add(mapper.readValue(json, Bill.class));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Bill getBillById(String id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM bills WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Bill.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Bill getBillByNo(String billNo) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM bills WHERE bill_no = ?")) {
            ps.setString(1, billNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Bill.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveBill(Bill bill) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO bills (id, bill_no, date, doc_type, status, buyer_name, grand_total, due_amount, json_data, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET bill_no = excluded.bill_no, date = excluded.date, doc_type = excluded.doc_type, status = excluded.status, buyer_name = excluded.buyer_name, grand_total = excluded.grand_total, due_amount = excluded.due_amount, json_data = excluded.json_data, updated_at = excluded.updated_at")) {
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
            ps.setString(2, bill.getBillNo());
            ps.setString(3, bill.getDate());
            ps.setString(4, bill.getDocType().getCode());
            ps.setString(5, bill.getStatus().getCode());
            ps.setString(6, buyer);
            ps.setDouble(7, grand);
            ps.setDouble(8, due);
            ps.setString(9, mapper.writeValueAsString(bill));
            ps.setString(10, bill.getCreatedAt());
            ps.setString(11, bill.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteBill(String id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM bills WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
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

