package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Transaction {
    private String id;
    private String buyerId = "";
    private String buyerName = "";
    private String bookType = "CC"; // "CC" (Credit) or "CS" (Cash Sale)
    private String transactionType = "sale"; // "sale" or "payment"
    private String transactionDate = LocalDate.now().toString();
    private String dueDate = "";
    private double amount = 0.0;
    private int totalQuantity = 0; // Total pieces / units
    private String checkNumber = ""; // Check / Cheque number
    private boolean includeInReporting = true;
    private int parcel = 0; // Shipment parcel count
    private String billId = ""; // Optional linked bill id
    private String billNo = ""; // Optional linked bill number
    private String notes = ""; // Optional notes / remarks
    private boolean deleted = false;
    private String deletedReason = "";
    private String deletedAt = "";
    private String createdAt;
    private String updatedAt;

    public Transaction() {
        this.id = "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public Transaction(String id, String buyerId, String buyerName, String bookType, String transactionType,
                       String transactionDate, String dueDate, double amount, int totalQuantity,
                       String checkNumber, boolean includeInReporting, int parcel) {
        this.id = (id != null && !id.isBlank()) ? id : "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.buyerId = buyerId != null ? buyerId : "";
        this.buyerName = buyerName != null ? buyerName : "";
        this.bookType = bookType != null ? bookType.toUpperCase() : "CC";
        this.transactionType = transactionType != null ? transactionType.toLowerCase() : "sale";
        this.transactionDate = (transactionDate != null && !transactionDate.isBlank()) ? transactionDate : LocalDate.now().toString();
        this.dueDate = dueDate != null ? dueDate : "";
        this.amount = amount;
        this.totalQuantity = totalQuantity;
        this.checkNumber = checkNumber != null ? checkNumber : "";
        this.includeInReporting = includeInReporting;
        this.parcel = parcel;
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public Transaction(String id, String buyerId, String buyerName, String bookType, String transactionType,
                       String transactionDate, String dueDate, int totalQuantity, double amount,
                       String checkNumber, boolean includeInReporting, String billNo) {
        this(id, buyerId, buyerName, bookType, transactionType, transactionDate, dueDate, amount, totalQuantity, checkNumber, includeInReporting, 0);
        this.billNo = billNo != null ? billNo : "";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBuyerId() { return buyerId != null ? buyerId : ""; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }

    public String getBuyerName() { return buyerName != null ? buyerName : ""; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public String getBookType() { return bookType != null ? bookType : "CC"; }
    public void setBookType(String bookType) { this.bookType = bookType != null ? bookType.toUpperCase() : "CC"; }

    public String getTransactionType() { return transactionType != null ? transactionType : "sale"; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType != null ? transactionType.toLowerCase() : "sale"; }

    public String getTransactionDate() { return transactionDate != null ? transactionDate : ""; }
    public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }

    public String getDueDate() { return dueDate != null ? dueDate : ""; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public int getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(int totalQuantity) { this.totalQuantity = totalQuantity; }

    public String getCheckNumber() { return checkNumber != null ? checkNumber : ""; }
    public void setCheckNumber(String checkNumber) { this.checkNumber = checkNumber; }

    public boolean isIncludeInReporting() { return includeInReporting; }
    public void setIncludeInReporting(boolean includeInReporting) { this.includeInReporting = includeInReporting; }

    public int getParcel() { return parcel; }
    public void setParcel(int parcel) { this.parcel = parcel; }

    public int getParcels() { return parcel; }
    public void setParcels(int parcels) { this.parcel = parcels; }

    public String getBillId() { return billId != null ? billId : ""; }
    public void setBillId(String billId) { this.billId = billId; }

    public String getBillNo() { return billNo != null ? billNo : ""; }
    public void setBillNo(String billNo) { this.billNo = billNo; }

    public String getBillNumber() { return billNo != null ? billNo : ""; }
    public void setBillNumber(String billNumber) { this.billNo = billNumber; }

    public String getNotes() { return notes != null ? notes : ""; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public String getDeletedReason() { return deletedReason != null ? deletedReason : ""; }
    public void setDeletedReason(String deletedReason) { this.deletedReason = deletedReason; }

    public String getDeletedAt() { return deletedAt != null ? deletedAt : ""; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public boolean isSale() {
        return "sale".equalsIgnoreCase(transactionType);
    }

    public boolean isPayment() {
        return "payment".equalsIgnoreCase(transactionType);
    }

    @Override
    public String toString() {
        return (isSale() ? "Sale #" : "Payment #") + id + " - " + buyerName + " (₹" + String.format("%.2f", amount) + ")";
    }
}
