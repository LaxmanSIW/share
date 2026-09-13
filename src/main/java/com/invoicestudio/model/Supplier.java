package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;

/**
 * Supplier / Seller — the party from whom goods or services are purchased
 * (Sundry Creditor in Indian accounting terminology).
 *
 * Persisted as a JSON payload in the {@code suppliers} table via
 * {@link com.invoicestudio.db.SupplierDao}; scalar columns (name, phone, gst,
 * state) are duplicated for fast search indexing, matching the Buyer pattern.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Supplier {
    private String id;
    private String name = "";
    private String contactPerson = "";
    private String phone = "";
    private String email = "";
    private String gst = "";
    private String state = "";
    private String stateCode = "";
    private String city = "";
    private String address = "";
    private String pan = "";
    private String bankName = "";
    private String bankAccountNo = "";
    private String bankIfsc = "";
    /** Opening balance. Positive = payable to seller (Cr); negative = advance paid (Dr). */
    private double openingBalance = 0.0;
    /** Standard credit period this supplier allows (e.g. 30, 45, 60 days). */
    private int creditPeriodDays = 0;
    private Map<String, String> custom = new HashMap<>();
    private String createdAt;
    private String updatedAt;

    public Supplier() {}

    public Supplier(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() {
        return name != null && !name.isBlank() ? name : "Unnamed Supplier";
    }

    public String getContactPerson() { return contactPerson != null ? contactPerson : ""; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getPhone() { return phone != null ? phone : ""; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email != null ? email : ""; }
    public void setEmail(String email) { this.email = email; }

    public String getGst() { return gst != null ? gst : ""; }
    public void setGst(String gst) { this.gst = gst; }

    public String getState() { return state != null ? state : ""; }
    public void setState(String state) { this.state = state; }

    public String getStateCode() { return stateCode != null ? stateCode : ""; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    /**
     * Effective 2-digit GST state code: explicit field wins, otherwise derived
     * from the first two digits of the GSTIN.
     */
    public String getEffectiveStateCode() {
        if (stateCode != null && !stateCode.isBlank()) return stateCode.trim();
        if (gst != null && gst.trim().length() >= 2) {
            String prefix = gst.trim().substring(0, 2);
            if (prefix.matches("\\d{2}")) return prefix;
        }
        return "";
    }

    /**
     * PAN derived from GSTIN characters 3-12 (positions 3..12 of the 15-char
     * GSTIN) when the explicit PAN field is empty.
     */
    public String getEffectivePan() {
        if (pan != null && !pan.isBlank()) return pan.trim();
        if (gst != null && gst.trim().length() >= 12) return gst.trim().substring(2, 12);
        return "";
    }

    public String getCity() { return city != null ? city : ""; }
    public void setCity(String city) { this.city = city; }

    public String getAddress() { return address != null ? address : ""; }
    public void setAddress(String address) { this.address = address; }

    public String getPan() { return pan != null ? pan : ""; }
    public void setPan(String pan) { this.pan = pan; }

    public String getBankName() { return bankName != null ? bankName : ""; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getBankAccountNo() { return bankAccountNo != null ? bankAccountNo : ""; }
    public void setBankAccountNo(String bankAccountNo) { this.bankAccountNo = bankAccountNo; }

    public String getBankIfsc() { return bankIfsc != null ? bankIfsc : ""; }
    public void setBankIfsc(String bankIfsc) { this.bankIfsc = bankIfsc; }

    public double getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(double openingBalance) { this.openingBalance = openingBalance; }

    public int getCreditPeriodDays() { return creditPeriodDays; }
    public void setCreditPeriodDays(int creditPeriodDays) { this.creditPeriodDays = creditPeriodDays; }

    public Map<String, String> getCustom() { return custom; }
    public void setCustom(Map<String, String> custom) { this.custom = custom != null ? custom : new HashMap<>(); }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() { return name; }
}
