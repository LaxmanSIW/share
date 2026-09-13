package com.invoicestudio.service;

import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.BillTotals;
import com.invoicestudio.model.PurchaseBill;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Purchase-side computation engine (Tally "F9: Purchase" equivalent).
 *
 * Mirrors {@link BillingService#computeTotals}: same discount, taxable,
 * CGST/SGST vs IGST split and rupee round-off logic, but applied to purchase
 * (inward) items. GST paid on purchases becomes Input Tax Credit (ITC).
 */
public class PurchaseService {

    public static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    public static BillTotals computePurchaseTotals(List<BillItem> items, double discountPct, boolean interState) {
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

    /** "PUR-0001" style sequential purchase bill number. */
    public static String nextPurchaseBillNo(int next, int digits) {
        if (digits <= 1) return "PUR-" + next;
        return String.format("PUR-%0" + digits + "d", next);
    }

    public static String todayISO() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static String newPurchaseId() {
        return "pur_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String newItemId() {
        return "pit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** Inter-state detection from GSTIN state-code prefix vs company code. */
    public static boolean isInterStateSupply(String gstin, String companyStateCode) {
        if (gstin == null || gstin.trim().length() < 2) return false;
        if (companyStateCode == null || companyStateCode.isBlank()) return false;
        return !gstin.trim().substring(0, 2).equals(companyStateCode.trim());
    }
}
