package com.invoicestudio.service;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.BillTotals;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BillingServiceTest {

    @Test
    void testComputeTotalsIntraState() {
        BillItem item1 = new BillItem();
        item1.setDesc("Web Design");
        item1.setQty(2);
        item1.setRate(1000); // 2000
        item1.setDiscPct(10); // 10% off -> 1800
        item1.setGst(18); // 18% GST -> 324 (CGST 162, SGST 162)

        BillTotals totals = BillingService.computeTotals(List.of(item1), 0, false);

        assertEquals(2000.0, totals.getSubtotal(), 0.001);
        assertEquals(200.0, totals.getDiscount(), 0.001);
        assertEquals(1800.0, totals.getTaxable(), 0.001);
        assertEquals(162.0, totals.getCgst(), 0.001);
        assertEquals(162.0, totals.getSgst(), 0.001);
        assertEquals(0.0, totals.getIgst(), 0.001);
        assertEquals(2124.0, totals.getGrandTotal(), 0.001);
    }

    @Test
    void testComputeTotalsInterState() {
        BillItem item1 = new BillItem();
        item1.setDesc("Consulting");
        item1.setQty(1);
        item1.setRate(5000);
        item1.setDiscPct(0);
        item1.setGst(18); // IGST 18% -> 900

        BillTotals totals = BillingService.computeTotals(List.of(item1), 0, true);

        assertEquals(5000.0, totals.getSubtotal(), 0.001);
        assertEquals(0.0, totals.getDiscount(), 0.001);
        assertEquals(5000.0, totals.getTaxable(), 0.001);
        assertEquals(0.0, totals.getCgst(), 0.001);
        assertEquals(0.0, totals.getSgst(), 0.001);
        assertEquals(900.0, totals.getIgst(), 0.001);
        assertEquals(5900.0, totals.getGrandTotal(), 0.001);
    }

    @Test
    void testNumberToWordsIndian() {
        String words1 = BillingService.numberToWordsIndian(1250);
        assertTrue(words1.toLowerCase().contains("one thousand two hundred fifty"),
                "Expected 'One Thousand Two Hundred Fifty', got: " + words1);

        String words2 = BillingService.numberToWordsIndian(100000);
        assertTrue(words2.toLowerCase().contains("one lakh"),
                "Expected 'One Lakh', got: " + words2);

        String words3 = BillingService.numberToWordsIndian(12345678);
        assertTrue(words3.toLowerCase().contains("one crore twenty three lakh"),
                "Expected crore and lakh formatting, got: " + words3);
    }

    @Test
    void testAmountInWords() {
        String words = BillingService.amountInWords(2500.50);
        assertTrue(words.toLowerCase().contains("two thousand five hundred rupees and fifty paise only"),
                "Expected rupees and paise words, got: " + words);
    }

    @Test
    void testFormatMoney() {
        String formatted = BillingService.formatMoney(1500000.50, "₹");
        assertTrue(formatted.contains("₹") && formatted.contains("15,00,000.50"),
                "Expected Indian numbering format ₹15,00,000.50, got: " + formatted);
    }

    @Test
    void testNextBillNoWithZeroPaddingAndEmptyPrefix() {
        com.invoicestudio.model.Settings s1 = new com.invoicestudio.model.Settings();
        s1.setBillNoPrefix("INV-");
        s1.setBillNoDigits(4);
        s1.setBillNoNext(5);
        assertEquals("INV-0005", BillingService.nextBillNo(s1));

        com.invoicestudio.model.Settings s2 = new com.invoicestudio.model.Settings();
        s2.setBillNoPrefix("");
        s2.setBillNoDigits(1);
        s2.setBillNoNext(1);
        assertEquals("1", BillingService.nextBillNo(s2));

        com.invoicestudio.model.Settings s3 = new com.invoicestudio.model.Settings();
        s3.setBillNoPrefix("BILL/");
        s3.setBillNoDigits(3);
        s3.setBillNoNext(42);
        assertEquals("BILL/042", BillingService.nextBillNo(s3));
    }
}
