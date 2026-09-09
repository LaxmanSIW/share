package com.invoicestudio.service;

import com.invoicestudio.model.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class BillingService {

    public static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    public static BillTotals computeTotals(List<BillItem> items, double discountPct, boolean interState) {
        if (items == null) items = List.of();
        double subtotal = 0;
        double itemDiscounts = 0;
        double gstTotal = 0;
        double totalQty = 0;

        for (BillItem it : items) {
            double gross = it.getGross();
            double amt = it.getAmount();
            subtotal += gross;
            itemDiscounts += (gross - amt);
            gstTotal += amt * (it.getGst() / 100.0);
            totalQty += it.getQty();
        }

        double globalDiscount = (subtotal - itemDiscounts) * (Math.max(0, discountPct) / 100.0);
        double discount = itemDiscounts + globalDiscount;
        double taxable = subtotal - discount;

        double cgst = !interState ? gstTotal / 2.0 : 0.0;
        double sgst = !interState ? gstTotal / 2.0 : 0.0;
        double igst = interState ? gstTotal : 0.0;

        double beforeRound = taxable + cgst + sgst + igst;
        double rounded = Math.round(beforeRound * 100.0) / 100.0;
        double roundOff = Math.round((Math.round(rounded) - beforeRound) * 100.0) / 100.0;
        double grandTotal = Math.round(beforeRound + roundOff);

        BillTotals t = new BillTotals();
        t.setSubtotal(round2(subtotal));
        t.setDiscount(round2(discount));
        t.setTaxable(round2(taxable));
        t.setCgst(round2(cgst));
        t.setSgst(round2(sgst));
        t.setIgst(round2(igst));
        t.setRoundOff(round2(roundOff));
        t.setGrandTotal(round2(grandTotal));
        t.setTotalQty(round2(totalQty));
        t.setItemCount(items.size());
        return t;
    }

    public static String formatMoney(double amount, String currency) {
        String cur = currency != null ? currency : "₹";
        long intPart = (long) Math.floor(Math.abs(amount));
        int fracPart = (int) Math.round((Math.abs(amount) - intPart) * 100);
        String s = String.valueOf(intPart);
        StringBuilder sb = new StringBuilder();
        int len = s.length();
        if (len <= 3) {
            sb.append(s);
        } else {
            sb.append(s.substring(len - 3));
            int rem = len - 3;
            while (rem > 0) {
                int take = Math.min(2, rem);
                sb.insert(0, ",");
                sb.insert(0, s.substring(rem - take, rem));
                rem -= take;
            }
        }
        String sign = amount < 0 ? "-" : "";
        return String.format("%s%s%s.%02d", sign, cur, sb.toString(), fracPart);
    }

    public static String nextBillNo(Settings settings) {
        String prefix = settings != null && settings.getBillNoPrefix() != null ? settings.getBillNoPrefix() : "INV-";
        int next = settings != null ? settings.getBillNoNext() : 1;
        return String.format("%s%04d", prefix, next);
    }

    public static String todayISO() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static String addDays(String iso, int days) {
        try {
            LocalDate d = LocalDate.parse(iso);
            return d.plusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return iso;
        }
    }

    public static String addMonths(String iso, int months) {
        try {
            LocalDate d = LocalDate.parse(iso);
            return d.plusMonths(months).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return iso;
        }
    }

    public static String addYears(String iso, int years) {
        return addMonths(iso, 12 * years);
    }

    public static String nextRepeatDate(Bill b) {
        if (b == null || b.getDate() == null) return todayISO();
        RepeatCadence r = b.getRepeat() != null ? b.getRepeat() : RepeatCadence.NONE;
        switch (r) {
            case WEEKLY: return addDays(b.getDate(), 7);
            case YEARLY: return addYears(b.getDate(), 1);
            case MONTHLY:
            default: return addMonths(b.getDate(), 1);
        }
    }

    public static boolean isRepeatDue(Bill b) {
        if (b == null || b.getRepeat() == null || b.getRepeat() == RepeatCadence.NONE) return false;
        if (b.getDocType() != DocType.INVOICE) return false;
        if (b.getRepeatEndDate() != null && !b.getRepeatEndDate().isBlank()) {
            if (nextRepeatDate(b).compareTo(b.getRepeatEndDate()) > 0) return false;
        }
        return nextRepeatDate(b).compareTo(todayISO()) <= 0;
    }

    /* ---------------- Indian Currency Number to Words ---------------- */

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private static String twoDigits(int n) {
        if (n < 20) return ONES[n];
        int t = n / 10;
        int o = n % 10;
        return TENS[t] + (o > 0 ? " " + ONES[o] : "");
    }

    private static String threeDigits(int n) {
        int h = n / 100;
        int rest = n % 100;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(ONES[h]).append(" Hundred");
        if (rest > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(twoDigits(rest));
        }
        return sb.toString().trim();
    }

    public static String numberToWordsIndian(long num) {
        long n = Math.abs(num);
        if (n == 0) return "Zero Rupees Only";

        long crore = n / 10000000L;
        long lakh = (n % 10000000L) / 100000L;
        long thousand = (n % 100000L) / 1000L;
        long hundred = n % 1000L;

        List<String> parts = new ArrayList<>();
        if (crore > 0) parts.add(threeDigits((int) crore) + " Crore");
        if (lakh > 0) parts.add(twoDigits((int) lakh) + " Lakh");
        if (thousand > 0) parts.add(twoDigits((int) thousand) + " Thousand");
        if (hundred > 0) parts.add(threeDigits((int) hundred));

        return String.join(" ", parts).trim() + " Rupees Only";
    }

    public static String amountInWords(double total) {
        long rupees = (long) Math.floor(Math.abs(total));
        int paise = (int) Math.round((Math.abs(total) - rupees) * 100);
        String base = numberToWordsIndian(rupees);
        if (paise > 0) {
            return base.replace(" Rupees Only", "") + " Rupees and " + twoDigits(paise) + " Paise Only";
        }
        return base;
    }

    public static Bill duplicateBill(Bill source, Settings settings) {
        Bill b = new Bill();
        b.setId("bill_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        b.setBillNo(nextBillNo(settings));
        b.setDate(todayISO());
        b.setTemplateId(source.getTemplateId());
        b.setTemplateName(source.getTemplateName());
        b.setVariables(new HashMap<>(source.getVariables()));
        List<BillItem> items = new ArrayList<>();
        for (BillItem it : source.getItems()) {
            items.add(new BillItem(
                "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                it.getDesc(), it.getHsn(), it.getQty(), it.getUnit(), it.getRate(), it.getGst(), it.getDiscPct()
            ));
        }
        b.setItems(items);
        b.setDiscountPct(source.getDiscountPct());
        b.setTotals(computeTotals(items, b.getDiscountPct(), settings.isInterState()));
        b.setAmountInWords(amountInWords(b.getTotals().getGrandTotal()));
        b.setNotes(source.getNotes());
        b.setStatus(BillStatus.UNPAID);
        b.setDocType(source.getDocType());
        b.setRepeat(RepeatCadence.NONE);
        b.setCreatedAt(todayISO());
        b.setUpdatedAt(todayISO());
        return b;
    }

    public static Bill convertToInvoice(Bill source, Settings settings) {
        Bill b = duplicateBill(source, settings);
        b.setDocType(DocType.INVOICE);
        return b;
    }

    public static Bill repeatBill(Bill source, Settings settings) {
        Bill b = duplicateBill(source, settings);
        b.setDocType(DocType.INVOICE);
        String due = nextRepeatDate(source);
        b.setDate(todayISO().compareTo(due) > 0 ? todayISO() : due);
        b.setRepeat(source.getRepeat() != null && source.getRepeat() != RepeatCadence.NONE ? source.getRepeat() : RepeatCadence.MONTHLY);
        b.setRepeatEndDate(source.getRepeatEndDate());
        return b;
    }
}
