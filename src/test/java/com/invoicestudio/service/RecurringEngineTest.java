package com.invoicestudio.service;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.DocType;
import com.invoicestudio.model.RepeatCadence;
import com.invoicestudio.model.Settings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecurringEngineTest {

    @Test
    void testNextRepeatDateMonthly() {
        Bill b = new Bill();
        b.setDate("2025-01-15");
        b.setRepeat(RepeatCadence.MONTHLY);

        String next = BillingService.nextRepeatDate(b);
        assertEquals("2025-02-15", next);
    }

    @Test
    void testNextRepeatDateWeekly() {
        Bill b = new Bill();
        b.setDate("2025-01-01");
        b.setRepeat(RepeatCadence.WEEKLY);

        String next = BillingService.nextRepeatDate(b);
        assertEquals("2025-01-08", next);
    }

    @Test
    void testNextRepeatDateYearly() {
        Bill b = new Bill();
        b.setDate("2025-03-10");
        b.setRepeat(RepeatCadence.YEARLY);

        String next = BillingService.nextRepeatDate(b);
        assertEquals("2026-03-10", next);
    }

    @Test
    void testIsRepeatDue() {
        Bill pastBill = new Bill();
        pastBill.setDate("2020-01-01");
        pastBill.setDocType(DocType.INVOICE);
        pastBill.setRepeat(RepeatCadence.MONTHLY);

        assertTrue(BillingService.isRepeatDue(pastBill));

        Bill futureBill = new Bill();
        futureBill.setDate("2099-01-01");
        futureBill.setDocType(DocType.INVOICE);
        futureBill.setRepeat(RepeatCadence.MONTHLY);

        assertFalse(BillingService.isRepeatDue(futureBill));
    }

    @Test
    void testRepeatEndDateBlocksDue() {
        Bill b = new Bill();
        b.setDate("2020-01-01");
        b.setDocType(DocType.INVOICE);
        b.setRepeat(RepeatCadence.MONTHLY);
        b.setRepeatEndDate("2020-01-15"); // End date is before next repeat date (2020-02-01)

        assertFalse(BillingService.isRepeatDue(b));
    }
}
