package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BillPayment {
    private String id;
    private String date; // yyyy-MM-dd
    private double amount;
    private PaymentMethod method = PaymentMethod.CASH;
    private String reference = "";
    private String note = "";

    public BillPayment() {}

    public BillPayment(String id, String date, double amount, PaymentMethod method, String reference, String note) {
        this.id = id;
        this.date = date;
        this.amount = amount;
        this.method = method;
        this.reference = reference;
        this.note = note;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public PaymentMethod getMethod() { return method != null ? method : PaymentMethod.CASH; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
