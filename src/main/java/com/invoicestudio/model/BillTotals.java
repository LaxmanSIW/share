package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BillTotals {
    private double subtotal;
    private double discount;
    private double taxable;
    private double cgst;
    private double sgst;
    private double igst;
    private double roundOff;
    private double grandTotal;
    private double totalQty;
    private int itemCount;

    public BillTotals() {}

    public BillTotals(double subtotal, double cgst, double sgst, double igst, double grandTotal, double roundOff, double dueAmount) {
        this.subtotal = subtotal;
        this.cgst = cgst;
        this.sgst = sgst;
        this.igst = igst;
        this.grandTotal = grandTotal;
        this.roundOff = roundOff;
    }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }

    public double getTaxable() { return taxable; }
    public void setTaxable(double taxable) { this.taxable = taxable; }

    public double getCgst() { return cgst; }
    public void setCgst(double cgst) { this.cgst = cgst; }

    public double getSgst() { return sgst; }
    public void setSgst(double sgst) { this.sgst = sgst; }

    public double getIgst() { return igst; }
    public void setIgst(double igst) { this.igst = igst; }

    public double getRoundOff() { return roundOff; }
    public void setRoundOff(double roundOff) { this.roundOff = roundOff; }

    public double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(double grandTotal) { this.grandTotal = grandTotal; }

    public double getTotalQty() { return totalQty; }
    public void setTotalQty(double totalQty) { this.totalQty = totalQty; }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
}
