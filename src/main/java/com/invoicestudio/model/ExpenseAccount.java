package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A named expense account (payee directory) — "Techparks Rent", "Indian Oil",
 * etc. Referenced by name from {@link Expense#getPayee()} so existing vouchers
 * keep working with zero migration; this registry only adds structure on top.
 *
 * Persisted as JSON in the {@code expense_accounts} table via ExpenseAccountDao.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpenseAccount {

    private String id;
    private String name = "";
    private String notes = "";
    private String defaultPaymentMode = "";   // optional convenience default
    private boolean archived = false;
    private String createdAt;
    private String updatedAt;

    public ExpenseAccount() {}

    public ExpenseAccount(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getNotes() { return notes != null ? notes : ""; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDefaultPaymentMode() { return defaultPaymentMode != null ? defaultPaymentMode : ""; }
    public void setDefaultPaymentMode(String defaultPaymentMode) { this.defaultPaymentMode = defaultPaymentMode; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
