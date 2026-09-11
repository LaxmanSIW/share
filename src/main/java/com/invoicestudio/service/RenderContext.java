package com.invoicestudio.service;

import com.invoicestudio.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RenderContext {
    private final Map<String, String> values = new HashMap<>();
    private final Bill bill;
    private final Settings settings;
    private final int pageNo;
    private final int pageCount;
    private final String copyLabel;

    private static final String[] COPY_LABELS = {
        "Original for Recipient",
        "Duplicate for Transporter",
        "Triplicate for Supplier"
    };

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    public RenderContext(Bill bill, Settings settings, int copyIndex, int pageNo, int pageCount) {
        this.bill = bill;
        this.settings = settings != null ? settings : new Settings();
        this.pageNo = pageNo;
        this.pageCount = pageCount;
        this.copyLabel = COPY_LABELS[Math.abs(copyIndex) % COPY_LABELS.length];
        buildValues();
    }

    private void buildValues() {
        BusinessProfile b = settings.getBusiness();
        values.put("invoice_no", bill != null ? bill.getBillNo() : "INV-0001");
        values.put("invoice_date", bill != null ? bill.getDate() : BillingService.todayISO());
        values.put("business_name", b.getName());
        values.put("business_address", b.getAddress());
        values.put("business_gst", b.getGstin());
        values.put("business_phone", b.getPhone());
        values.put("business_email", b.getEmail());
        values.put("business_state", b.getState());
        values.put("business_logo", b.getLogo());
        values.put("terms", b.getTerms());
        values.put("bank_name", b.getBankName());
        values.put("bank_account", b.getAccountNo());
        values.put("bank_ifsc", b.getIfsc());
        values.put("bank_upi", b.getUpi());
        values.put("notes", bill != null && bill.getNotes() != null ? bill.getNotes() : "");
        values.put("page_no", String.valueOf(pageNo));
        values.put("page_count", String.valueOf(pageCount));
        values.put("copy_label", copyLabel);
        values.put("doc_type", bill != null && bill.getDocType() != null ? bill.getDocType().getTitle() : "TAX INVOICE");

        // Buyer & variable values
        if (bill != null && bill.getVariables() != null) {
            for (Map.Entry<String, String> e : bill.getVariables().entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    values.put(e.getKey(), e.getValue());
                }
            }
        }

        // Parcels & logistics defaults
        int parcelsCount = bill != null ? bill.getParcel() : 1;
        values.put("parcel", String.valueOf(parcelsCount));
        values.put("parcels", String.valueOf(parcelsCount));

        if (bill == null) {
            // Sample values for Template Designer preview
            values.putIfAbsent("buyer_name", "Acme Enterprises Ltd");
            values.putIfAbsent("buyer_trade_name", "Acme Retail Store");
            values.putIfAbsent("buyer_gst", "27ABCDE1234F1Z5");
            values.putIfAbsent("buyer_gstin", "27ABCDE1234F1Z5");
            values.putIfAbsent("buyer_address", "Plot 42, Sector 18, MIDC Industrial Area, Pune, MH - 411019");
            values.putIfAbsent("buyer_phone", "+91 98765 43210");
            values.putIfAbsent("buyer_email", "billing@acme.com");
            values.putIfAbsent("buyer_state", "Maharashtra");
            values.putIfAbsent("buyer_state_code", "27");
            values.putIfAbsent("buyer_city", "Pune");
            values.putIfAbsent("buyer_contact_person", "Amit Verma");
            values.putIfAbsent("po_no", "PO-2026-892");
            values.putIfAbsent("vehicle_no", "MH-12-AB-1234");
            values.putIfAbsent("transport_name", "V-Trans Roadlines");
            values.putIfAbsent("transport_phone", "+91 98200 12345");
            values.putIfAbsent("transport_contact", "Rajesh Sharma");
            values.putIfAbsent("e_way_bill", "241019283746");
            values.putIfAbsent("due_date", BillingService.todayISO());
        }

        // Totals
        BillTotals t = bill != null ? bill.getTotals() : null;
        String cur = settings.getCurrency();
        values.put("subtotal", t != null ? String.format("%.2f", t.getSubtotal()) : "0.00");
        values.put("discount", t != null ? String.format("%.2f", t.getDiscount()) : "0.00");
        values.put("taxable", t != null ? String.format("%.2f", t.getTaxable()) : "0.00");
        values.put("cgst", t != null ? String.format("%.2f", t.getCgst()) : "0.00");
        values.put("sgst", t != null ? String.format("%.2f", t.getSgst()) : "0.00");
        values.put("igst", t != null ? String.format("%.2f", t.getIgst()) : "0.00");
        values.put("round_off", t != null ? String.format("%.2f", t.getRoundOff()) : "0.00");
        values.put("grand_total", t != null ? String.format("%s%.2f", cur, t.getGrandTotal()) : cur + "0.00");
        values.put("amount_in_words", bill != null && bill.getAmountInWords() != null ? bill.getAmountInWords() : "");
        values.put("total_qty", t != null ? String.format("%.2f", t.getTotalQty()) : "0");
        values.put("item_count", t != null ? String.valueOf(t.getItemCount()) : "0");

        double paid = bill != null && bill.getPayments() != null
                ? bill.getPayments().stream().mapToDouble(BillPayment::getAmount).sum()
                : 0.0;
        if (paid == 0 && bill != null && bill.getStatus() == BillStatus.PAID && t != null) {
            paid = t.getGrandTotal();
        }
        double due = t != null ? Math.max(0, t.getGrandTotal() - paid) : 0;
        if (bill != null && bill.getStatus() == BillStatus.CANCELLED) due = 0;

        values.put("paid_amount", String.format("%s%.2f", cur, paid));
        values.put("due_amount", String.format("%s%.2f", cur, due));
        values.put("payment_status", bill != null ? bill.getStatus().getLabel().toUpperCase() : "UNPAID");
    }

    public String resolveText(String raw) {
        if (raw == null) return "";
        Matcher m = VAR_PATTERN.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement = values.get(key);
            if (replacement == null) {
                replacement = bill == null ? "{{" + key + "}}" : "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public boolean isTextBlank(TemplateElement el) {
        if (el.getText() == null) return true;
        String raw = el.getText();
        Matcher m = VAR_PATTERN.matcher(raw);
        boolean hasVars = false;
        boolean allBlank = true;
        while (m.find()) {
            hasVars = true;
            String key = m.group(1);
            String val = values.getOrDefault(key, "").trim();
            if (!val.isEmpty() && !val.equals("0") && !val.equals("0.00") && !val.equals("₹0.00") && !val.equals("₹0")) {
                allBlank = false;
            }
        }
        if (!hasVars) {
            return raw.trim().isEmpty();
        }
        return allBlank;
    }

    public String getQrPayload(TemplateElement el) {
        String src = el.getQrSource();
        if ("custom".equalsIgnoreCase(src)) {
            String c = resolveText(el.getQrCustom());
            return !c.isBlank() ? c : "InvoiceStudio";
        }
        String upi = settings.getBusiness().getUpi();
        String name = settings.getBusiness().getName();
        double amt = 0;
        if ("upi_amount".equalsIgnoreCase(src)) {
            if (bill != null && bill.getTotals() != null) {
                amt = bill.getTotals().getGrandTotal();
            }
        }
        return BarcodeService.buildUpiPayload(upi, name, amt, bill != null ? bill.getBillNo() : "");
    }

    public String getBarcodePayload(TemplateElement el) {
        String data = el.getBarcodeData();
        if (data == null || data.isBlank()) data = "{{invoice_no}}";
        return resolveText(data);
    }

    public Map<String, String> getValues() { return values; }
    public Bill getBill() { return bill; }
    public Settings getSettings() { return settings; }
    public int getPageNo() { return pageNo; }
    public int getPageCount() { return pageCount; }
    public String getCopyLabel() { return copyLabel; }
}
