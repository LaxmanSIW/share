package com.invoicestudio.db;

import com.invoicestudio.model.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TransactionDao {
    private final DatabaseManager db;

    public TransactionDao(DatabaseManager db) {
        this.db = db;
    }

    public List<Transaction> getAllTransactions() {
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM transactions WHERE deleted = 0 ORDER BY transaction_date DESC, created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Transaction> getTransactions(String bookType, String transactionType) {
        List<Transaction> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE deleted = 0");
        List<String> params = new ArrayList<>();
        if (bookType != null && !bookType.equalsIgnoreCase("ALL")) {
            sql.append(" AND UPPER(book_type) = ?");
            params.add(bookType.toUpperCase());
        }
        if (transactionType != null && !transactionType.equalsIgnoreCase("ALL")) {
            sql.append(" AND LOWER(transaction_type) = ?");
            params.add(transactionType.toLowerCase());
        }
        sql.append(" ORDER BY transaction_date DESC, created_at DESC");

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Transaction> getTransactionsByBuyer(String buyerId) {
        List<Transaction> list = new ArrayList<>();
        if (buyerId == null || buyerId.isBlank()) return list;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM transactions WHERE buyer_id = ? AND deleted = 0 ORDER BY transaction_date ASC, id ASC")) {
            ps.setString(1, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Transaction getTransactionById(String id) {
        if (id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM transactions WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveTransaction(Transaction t) {
        if (t == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO transactions (id, buyer_id, buyer_name, book_type, transaction_type, " +
                 "transaction_date, due_date, amount, total_quantity, check_number, include_in_reporting, " +
                 "parcel, bill_id, bill_no, deleted, deleted_reason, deleted_at, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET buyer_id = excluded.buyer_id, buyer_name = excluded.buyer_name, " +
                 "book_type = excluded.book_type, transaction_type = excluded.transaction_type, " +
                 "transaction_date = excluded.transaction_date, due_date = excluded.due_date, " +
                 "amount = excluded.amount, total_quantity = excluded.total_quantity, " +
                 "check_number = excluded.check_number, include_in_reporting = excluded.include_in_reporting, " +
                 "parcel = excluded.parcel, bill_id = excluded.bill_id, bill_no = excluded.bill_no, " +
                 "deleted = excluded.deleted, deleted_reason = excluded.deleted_reason, " +
                 "deleted_at = excluded.deleted_at, updated_at = excluded.updated_at")) {
            String now = Instant.now().toString();
            if (t.getCreatedAt() == null || t.getCreatedAt().isBlank()) t.setCreatedAt(now);
            t.setUpdatedAt(now);

            ps.setString(1, t.getId());
            ps.setString(2, t.getBuyerId());
            ps.setString(3, t.getBuyerName());
            ps.setString(4, t.getBookType());
            ps.setString(5, t.getTransactionType());
            ps.setString(6, t.getTransactionDate());
            ps.setString(7, t.getDueDate());
            ps.setDouble(8, t.getAmount());
            ps.setInt(9, t.getTotalQuantity());
            ps.setString(10, t.getCheckNumber());
            ps.setInt(11, t.isIncludeInReporting() ? 1 : 0);
            ps.setInt(12, t.getParcel());
            ps.setString(13, t.getBillId());
            ps.setString(14, t.getBillNo());
            ps.setInt(15, t.isDeleted() ? 1 : 0);
            ps.setString(16, t.getDeletedReason());
            ps.setString(17, t.getDeletedAt());
            ps.setString(18, t.getCreatedAt());
            ps.setString(19, t.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteTransaction(String id, String reason) {
        if (id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE transactions SET deleted = 1, deleted_reason = ?, deleted_at = ?, updated_at = ? WHERE id = ?")) {
            String now = Instant.now().toString();
            ps.setString(1, reason != null ? reason : "Archived by user");
            ps.setString(2, now);
            ps.setString(3, now);
            ps.setString(4, id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void hardDelete(String id) {
        if (id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM transactions WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Transaction mapRow(ResultSet rs) throws Exception {
        Transaction t = new Transaction();
        t.setId(rs.getString("id"));
        t.setBuyerId(rs.getString("buyer_id"));
        t.setBuyerName(rs.getString("buyer_name"));
        t.setBookType(rs.getString("book_type"));
        t.setTransactionType(rs.getString("transaction_type"));
        t.setTransactionDate(rs.getString("transaction_date"));
        t.setDueDate(rs.getString("due_date"));
        t.setAmount(rs.getDouble("amount"));
        t.setTotalQuantity(rs.getInt("total_quantity"));
        t.setCheckNumber(rs.getString("check_number"));
        t.setIncludeInReporting(rs.getInt("include_in_reporting") == 1);
        t.setParcel(rs.getInt("parcel"));
        t.setBillId(rs.getString("bill_id"));
        t.setBillNo(rs.getString("bill_no"));
        t.setDeleted(rs.getInt("deleted") == 1);
        t.setDeletedReason(rs.getString("deleted_reason"));
        t.setDeletedAt(rs.getString("deleted_at"));
        t.setCreatedAt(rs.getString("created_at"));
        t.setUpdatedAt(rs.getString("updated_at"));
        return t;
    }
}
