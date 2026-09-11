package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Buyer {
    private String id;
    private String name = "";
    private String address = "";
    private String gst = "";
    private String phone = "";
    private String state = "";
    private String stateCode = "";
    private String contactPerson = "";
    private String city = "";
    private double creditLimit = 100000.0;
    private int riskScore = 8; // 1-10 risk scale (Low, Medium, High)
    private String defaultTransportId = "";
    private double openingBalance = 0.0;
    private Map<String, String> custom = new HashMap<>();
    private String createdAt;
    private String updatedAt;

    public Buyer() {}

    public Buyer(String id, String name, String address, String gst, String phone, String state) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.gst = gst;
        this.phone = phone;
        this.state = state;
    }

    public Buyer(String id, String name, String address, String gst, String phone, String state, String stateCode) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.gst = gst;
        this.phone = phone;
        this.state = state;
        this.stateCode = stateCode;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() {
        return name != null && !name.isBlank() ? name : "Unnamed Buyer";
    }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getGst() { return gst; }
    public void setGst(String gst) { this.gst = gst; }

    public String getGstin() { return gst; }
    public void setGstin(String gstin) { this.gst = gstin; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getStateCode() { return stateCode != null ? stateCode : ""; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    public String getEffectiveStateCode() {
        if (stateCode != null && !stateCode.isBlank()) return stateCode.trim();
        if (gst != null && gst.trim().length() >= 2) {
            String prefix = gst.trim().substring(0, 2);
            if (prefix.matches("\\d{2}")) return prefix;
        }
        return "";
    }

    public Map<String, String> getCustom() { return custom; }
    public void setCustom(Map<String, String> custom) { this.custom = custom != null ? custom : new HashMap<>(); }

    public String getTradeName() {
        return custom != null ? custom.getOrDefault("trade_name", "") : "";
    }

    public String getContactPerson() { return contactPerson != null ? contactPerson : ""; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson != null ? contactPerson : ""; }

    public String getCity() { return city != null ? city : ""; }
    public void setCity(String city) { this.city = city != null ? city : ""; }

    public double getCreditLimit() { return creditLimit; }
    public void setCreditLimit(double creditLimit) { this.creditLimit = creditLimit; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    public String getRiskLevel() {
        if (riskScore <= 3) return "High";
        if (riskScore <= 7) return "Medium";
        return "Low";
    }

    public String getDefaultTransportId() { return defaultTransportId != null ? defaultTransportId : ""; }
    public void setDefaultTransportId(String defaultTransportId) { this.defaultTransportId = defaultTransportId != null ? defaultTransportId : ""; }

    public double getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(double openingBalance) { this.openingBalance = openingBalance; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() { return name; }
}
