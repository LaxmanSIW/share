package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Expense voucher (Tally F5/F7: Payment/Expense).
 *
 * Persisted as JSON in the {@code expenses} table via ExpenseDao.
 * Category is one of the built-in direct/indirect heads (see
 * Expense.CATEGORIES) or a user-defined label stored in the JSON payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Expense {
    /** Built-in chart-of-accounts heads: first 4 direct, rest indirect. */
    public static final String[] CATEGORIES = {
            "Freight Inward", "Wages", "Power & Fuel", "Packaging",
            "Office Rent", "Electricity", "Salaries", "Telephone & Internet",
            "Marketing", "Bank Charges", "Software Subscription", "Tea & Pantry",
            "Repairs & Maintenance", "Miscellaneous"
    };

    public static boolean isDirect(String category) {
        if (category == null) return false;
        for (int i = 0; i < 4 && i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equalsIgnoreCase(category)) return true;
        }
        return false;
    }

    private String id;
    private String date;           // yyyy-MM-dd
    private String category = "Miscellaneous";
    private String description = "";
    private double amount;
    private String paymentMode = "Cash"; // Cash / Bank / NEFT / Cheque / UPI
    private String reference = "";       // cheque no / UTR
    private String payee = "";
    private String createdAt;
    private String updatedAt;

    public Expense() {}

    public Expense(String id, String date, String category, String description, double amount, String paymentMode) {
        this.id = id;
        this.date = date;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.paymentMode = paymentMode;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDate() { return date != null ? date : ""; }
    public void setDate(String date) { this.date = date; }

    public String getCategory() { return category != null ? category : "Miscellaneous"; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getPaymentMode() { return paymentMode != null ? paymentMode : "Cash"; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getReference() { return reference != null ? reference : ""; }
    public void setReference(String reference) { this.reference = reference; }

    public String getPayee() { return payee != null ? payee : ""; }
    public void setPayee(String payee) { this.payee = payee; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
