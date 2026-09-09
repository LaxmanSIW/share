package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BillItem {
    private String id;
    private String desc = "";
    private String hsn = "";
    private double qty;
    private String unit = "PCS";
    private double rate;
    private double gst = 18.0;
    private double discPct;

    public BillItem() {}

    public BillItem(String id, String desc, String hsn, double qty, String unit, double rate, double gst, double discPct) {
        this.id = id;
        this.desc = desc;
        this.hsn = hsn;
        this.qty = qty;
        this.unit = unit;
        this.rate = rate;
        this.gst = gst;
        this.discPct = discPct;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }

    public double getQty() { return qty; }
    public void setQty(double qty) { this.qty = qty; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }

    public double getGst() { return gst; }
    public void setGst(double gst) { this.gst = gst; }

    public double getDiscPct() { return discPct; }
    public void setDiscPct(double discPct) { this.discPct = discPct; }

    public double getGross() {
        return qty * rate;
    }

    public double getAmount() {
        double gross = getGross();
        double d = Math.max(0, Math.min(100, discPct));
        return Math.round((gross - gross * (d / 100.0)) * 100.0) / 100.0;
    }
}
