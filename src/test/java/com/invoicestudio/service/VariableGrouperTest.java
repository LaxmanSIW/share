package com.invoicestudio.service;

import com.invoicestudio.model.VariableDef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the variable grouping used by the designer dropdown and the
 * Insert Template Variable picker: canonical order, section headers,
 * filtering and null/empty safety.
 */
class VariableGrouperTest {

    private static VariableDef v(String key, String label, String type) {
        return new VariableDef(key, label, type, true);
    }

    @Test
    void groupsHaveCanonicalOrderAndHeaders() {
        List<VariableDef> vars = Arrays.asList(
                v("grand_total", "Grand Total", "TOTALS"),
                v("invoice_no", "Invoice Number", "INVOICE"),
                v("buyer_name", "Buyer Name", "BUYER"),
                v("business_phone", "Business Phone", "BUSINESS"),
                v("bank_upi", "Bank UPI", "BANK"),
                v("page_no", "Page Number", "PAGING"),
                v("vehicle_no", "Vehicle No", "LOGISTICS"),
                v("badge_size", "Badge Size", "text")); // user custom → MY VARIABLES

        List<VariableGrouper.Row> rows = VariableGrouper.group(vars);

        // First 8 rows must be headers in GROUP_ORDER for the built-in groups
        List<String> headerOrder = new ArrayList<>();
        for (VariableGrouper.Row r : rows) {
            if (r.isHeader()) headerOrder.add(r.header());
        }
        assertTrue(headerOrder.get(0).startsWith("Invoice Details"));
        assertTrue(headerOrder.get(1).startsWith("Page Numbers"));
        assertTrue(headerOrder.get(2).startsWith("Buyer / Customer"));
        assertTrue(headerOrder.get(3).startsWith("My Business"));
        assertTrue(headerOrder.get(4).startsWith("Bank & Payment"));
        assertTrue(headerOrder.get(5).startsWith("Totals & Tax"));
        assertTrue(headerOrder.get(6).startsWith("Transport & Logistics"));
        assertTrue(headerOrder.get(7).startsWith("My Custom Variables"));
    }

    @Test
    void everyGroupStartsWithHeaderAndContainsItsOwnVariables() {
        List<VariableDef> vars = Arrays.asList(
                v("invoice_no", "Invoice Number", "INVOICE"),
                v("buyer_name", "Buyer Name", "BUYER"),
                v("cgst", "CGST", "TOTALS"));

        List<VariableGrouper.Row> rows = VariableGrouper.group(vars);

        assertEquals(6, rows.size()); // 3 headers + 3 items
        assertTrue(rows.get(0).isHeader());
        assertEquals("invoice_no", rows.get(1).var().getKey());
        assertTrue(rows.get(2).isHeader());
        assertEquals("buyer_name", rows.get(3).var().getKey());
        assertTrue(rows.get(4).isHeader());
        assertEquals("cgst", rows.get(5).var().getKey());
    }

    @Test
    void unknownTypesFallBackToCustomGroup() {
        List<VariableDef> vars = Arrays.asList(
                v("size_chart", "Size Chart", "number"),
                v("batch_code", "Batch Code", null));

        List<VariableGrouper.Row> rows = VariableGrouper.group(vars);
        assertEquals(1, rows.stream().filter(VariableGrouper.Row::isHeader).count());
        assertTrue(rows.get(0).header().startsWith("My Custom Variables"));
        assertEquals(2, rows.size() - 1);
    }

    @Test
    void headerCountsMatchGroupSizes() {
        List<VariableDef> vars = Arrays.asList(
                v("subtotal", "Subtotal", "TOTALS"),
                v("cgst", "CGST", "TOTALS"),
                v("sgst", "SGST", "TOTALS"),
                v("invoice_no", "Invoice Number", "INVOICE"));

        List<VariableGrouper.Row> rows = VariableGrouper.group(vars);
        // INVOICE sorts before TOTALS in canonical order, so find the totals header
        VariableGrouper.Row totalsHeader = rows.stream()
                .filter(VariableGrouper.Row::isHeader)
                .filter(r -> r.header().startsWith("Totals & Tax"))
                .findFirst().orElseThrow();
        assertTrue(totalsHeader.header().contains("(3)"), "expected count 3 in: " + totalsHeader.header());
    }

    @Test
    void filterRemovesEmptyGroups() {
        List<VariableDef> vars = Arrays.asList(
                v("invoice_no", "Invoice Number", "INVOICE"),
                v("buyer_name", "Buyer Name", "BUYER"),
                v("cgst", "CGST Amount", "TOTALS"));

        List<VariableGrouper.Row> rows = VariableGrouper.groupFiltered(vars, "buyer");
        long headers = rows.stream().filter(VariableGrouper.Row::isHeader).count();
        assertEquals(1, headers);
        assertTrue(rows.get(0).header().startsWith("Buyer / Customer"));
        assertEquals("buyer_name", VariableGrouper.firstItem(rows).getKey());
    }

    @Test
    void filterMatchesKeyLabelOrType() {
        List<VariableDef> vars = Arrays.asList(
                v("invoice_no", "Invoice Number", "INVOICE"),
                v("e_way_bill", "E-Way Bill Number", "LOGISTICS"));

        assertEquals(1, VariableGrouper.groupFiltered(vars, "e_way").size() - 1); // 1 header + 1 item
        assertEquals(0, VariableGrouper.groupFiltered(vars, "zzz-not-there").size());
        // Full list is returned unchanged for blank queries
        assertEquals(4, VariableGrouper.groupFiltered(vars, "   ").size());
    }

    @Test
    void nullAndEmptyInputsAreSafe() {
        assertTrue(VariableGrouper.group(null).isEmpty());
        assertTrue(VariableGrouper.group(Collections.emptyList()).isEmpty());
        assertNull(VariableGrouper.firstItem(null));
        assertNull(VariableGrouper.firstItem(new ArrayList<>()));
        // Null / blank-key variables are skipped, not crashes
        List<VariableDef> vars = new ArrayList<>();
        vars.add(null);
        vars.add(new VariableDef("", "Blank Key", "INVOICE", true));
        vars.add(v("invoice_no", "Invoice Number", "INVOICE"));
        assertEquals(2, VariableGrouper.group(vars).size()); // 1 header + 1 item
    }

    @Test
    void firstItemSkipsHeaders() {
        List<VariableDef> vars = Arrays.asList(
                v("invoice_no", "Invoice Number", "INVOICE"),
                v("invoice_date", "Invoice Date", "INVOICE"));
        List<VariableGrouper.Row> rows = VariableGrouper.group(vars);
        assertTrue(rows.get(0).isHeader());
        assertEquals("invoice_no", VariableGrouper.firstItem(rows).getKey());
    }

    @Test
    void prettyGroupHumanisesUnknownTokens() {
        assertEquals("Invoice Details", VariableGrouper.prettyGroup("INVOICE"));
        assertEquals("Buyer Custom Fields", VariableGrouper.prettyGroup("BUYER CUSTOM"));
        assertEquals("Warranty", VariableGrouper.prettyGroup("warranty"));
        assertEquals("Other", VariableGrouper.prettyGroup(null));
        assertEquals("Other", VariableGrouper.prettyGroup("  "));
    }
}
