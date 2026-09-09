package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemRecord {
    private String id;
    private String name = "";
    private String hsn = "";
    private String unit = "PCS";
    private double rate;
    private double gst = 18.0;
    private String createdAt;
    private String updatedAt;

    public ItemRecord() {}

    public ItemRecord(String id, String name, String hsn, String unit, double rate, double gst) {
        this.id = id;
        this.name = name;
        this.hsn = hsn;
        this.unit = unit;
        this.rate = rate;
        this.gst = gst;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }

    public double getGst() { return gst; }
    public void setGst(double gst) { this.gst = gst; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() { return name; }
}
