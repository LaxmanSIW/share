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

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() { return name; }
}
