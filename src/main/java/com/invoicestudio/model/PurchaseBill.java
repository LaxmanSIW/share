package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Purchase Bill (inward supply) from a Seller / Supplier.
 *
 * Mirrors the sales-side {@link Bill} but records stock IN and Input Tax
 * Credit instead of stock OUT and output GST. Reuses {@link BillItem} for
 * line items and {@link BillTotals} for the GST computation block.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PurchaseBill {
    private String id;
    private String billNo;          // internal purchase voucher no (PUR-0001)
    private String supplierBillNo;  // supplier's original invoice no (GST matching)
    private String date;            // yyyy-MM-dd (bill date)
    private String supplierId = "";
    private String supplierName = "";
    private String supplierGstin = "";
    private List<BillItem> items = new ArrayList<>();
    private double discountPct;
    private double freight;         // other charges (freight inward / packing)
    private BillTotals totals = new BillTotals();
    private boolean paid;           // false = on credit (adds to payable)
    private String paymentMode = ""; // CASH / BANK / CHEQUE / UPI
    private String notes = "";
    private String createdAt;
    private String updatedAt;

    public PurchaseBill() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBillNo() { return billNo != null ? billNo : ""; }
    public void setBillNo(String billNo) { this.billNo = billNo; }

    public String getSupplierBillNo() { return supplierBillNo != null ? supplierBillNo : ""; }
    public void setSupplierBillNo(String supplierBillNo) { this.supplierBillNo = supplierBillNo; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getSupplierId() { return supplierId != null ? supplierId : ""; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName != null ? supplierName : ""; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getSupplierGstin() { return supplierGstin != null ? supplierGstin : ""; }
    public void setSupplierGstin(String supplierGstin) { this.supplierGstin = supplierGstin; }

    public List<BillItem> getItems() { return items; }
    public void setItems(List<BillItem> items) { this.items = items != null ? items : new ArrayList<>(); }

    public double getDiscountPct() { return discountPct; }
    public void setDiscountPct(double discountPct) { this.discountPct = discountPct; }

    public double getFreight() { return freight; }
    public void setFreight(double freight) { this.freight = Math.max(0, freight); }

    public BillTotals getTotals() { return totals; }
    public void setTotals(BillTotals totals) { this.totals = totals != null ? totals : new BillTotals(); }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public String getPaymentMode() { return paymentMode != null ? paymentMode : ""; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getNotes() { return notes != null ? notes : ""; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    /** Grand total including freight (other charges) and GST. */
    public double getAmountPayable() {
        double base = totals != null ? totals.getGrandTotal() : 0.0;
        return base + (freight > 0 ? freight : 0.0);
    }

    public Map<String, String> extraVars() {
        Map<String, String> m = new HashMap<>();
        m.put("supplier_bill_no", supplierBillNo);
        m.put("freight", String.valueOf(freight));
        return m;
    }
}
