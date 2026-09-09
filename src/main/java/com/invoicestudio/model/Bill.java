package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Bill {
    private String id;
    private String billNo;
    private String date; // yyyy-MM-dd
    private String templateId;
    private String templateName;
    private Map<String, String> variables = new HashMap<>();
    private List<BillItem> items = new ArrayList<>();
    private double discountPct;
    private BillTotals totals = new BillTotals();
    private String amountInWords;
    private String notes = "";
    private int printCount;
    private BillStatus status = BillStatus.UNPAID;
    private String paidAt;
    private List<BillPayment> payments = new ArrayList<>();
    private DocType docType = DocType.INVOICE;
    private RepeatCadence repeat = RepeatCadence.NONE;
    private String repeatEndDate;
    private boolean repeatSkipNext;
    private String createdAt;
    private String updatedAt;

    public Bill() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) { this.variables = variables != null ? variables : new HashMap<>(); }

    public List<BillItem> getItems() { return items; }
    public void setItems(List<BillItem> items) { this.items = items != null ? items : new ArrayList<>(); }

    public double getDiscountPct() { return discountPct; }
    public void setDiscountPct(double discountPct) { this.discountPct = discountPct; }

    public BillTotals getTotals() { return totals; }
    public void setTotals(BillTotals totals) { this.totals = totals != null ? totals : new BillTotals(); }

    public String getAmountInWords() { return amountInWords; }
    public void setAmountInWords(String amountInWords) { this.amountInWords = amountInWords; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getPrintCount() { return printCount; }
    public void setPrintCount(int printCount) { this.printCount = printCount; }

    public BillStatus getStatus() { return status != null ? status : BillStatus.UNPAID; }
    public void setStatus(BillStatus status) { this.status = status; }

    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String paidAt) { this.paidAt = paidAt; }

    public List<BillPayment> getPayments() { return payments; }
    public void setPayments(List<BillPayment> payments) { this.payments = payments != null ? payments : new ArrayList<>(); }

    public DocType getDocType() { return docType != null ? docType : DocType.INVOICE; }
    public void setDocType(DocType docType) { this.docType = docType; }

    public RepeatCadence getRepeat() { return repeat != null ? repeat : RepeatCadence.NONE; }
    public void setRepeat(RepeatCadence repeat) { this.repeat = repeat; }

    public String getRepeatEndDate() { return repeatEndDate; }
    public void setRepeatEndDate(String repeatEndDate) { this.repeatEndDate = repeatEndDate; }

    public boolean isRepeatSkipNext() { return repeatSkipNext; }
    public void setRepeatSkipNext(boolean repeatSkipNext) { this.repeatSkipNext = repeatSkipNext; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getBuyerName() {
        return variables != null ? variables.getOrDefault("buyer_name", "") : "";
    }
    public void setBuyerName(String buyerName) {
        if (variables == null) variables = new HashMap<>();
        variables.put("buyer_name", buyerName);
    }
}
