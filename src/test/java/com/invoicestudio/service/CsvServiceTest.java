package com.invoicestudio.service;

import com.invoicestudio.model.Buyer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvServiceTest {

    @Test
    void testGstinValidation() {
        // Valid 15-char GSTIN format
        assertTrue(CsvService.isValidGstin("27AAPFU0939F1ZV"));
        assertTrue(CsvService.isValidGstin("29AABCS1429B1ZB"));

        // Invalid GSTINs
        assertFalse(CsvService.isValidGstin("INVALID123"));
        assertFalse(CsvService.isValidGstin("27AAPFU0939F1Z")); // 14 chars
        assertFalse(CsvService.isValidGstin(null));
    }

    @Test
    void testPhoneValidation() {
        assertTrue(CsvService.isValidPhone("9876543210"));
        assertTrue(CsvService.isValidPhone("+91 98765 43210"));
        assertTrue(CsvService.isValidPhone("09876543210"));

        assertFalse(CsvService.isValidPhone("12345"));
        assertFalse(CsvService.isValidPhone("abcdefghij"));
        assertFalse(CsvService.isValidPhone(null));
    }

    @Test
    void testCsvCellEscaping() {
        assertEquals("SimpleText", CsvService.csvCell("SimpleText"));
        assertEquals("\"Text with, comma\"", CsvService.csvCell("Text with, comma"));
        assertEquals("\"Text with \"\"quotes\"\"\"", CsvService.csvCell("Text with \"quotes\""));
        assertEquals("\"Line1\nLine2\"", CsvService.csvCell("Line1\nLine2"));
    }

    @Test
    void testParseCsvBasic() {
        String csv = "Name,Phone,City\n\"Acme, Corp\",9876543210,Mumbai\nBeta Ltd,9123456789,Delhi";
        List<List<String>> rows = CsvService.parseCsv(csv);

        assertEquals(3, rows.size());
        assertEquals("Name", rows.get(0).get(0));
        assertEquals("Phone", rows.get(0).get(1));
        assertEquals("City", rows.get(0).get(2));

        assertEquals("Acme, Corp", rows.get(1).get(0));
        assertEquals("9876543210", rows.get(1).get(1));
        assertEquals("Mumbai", rows.get(1).get(2));

        assertEquals("Beta Ltd", rows.get(2).get(0));
    }

    @Test
    void testExportBuyers() {
        Buyer b1 = new Buyer();
        b1.setName("Acme Corp");
        b1.setGstin("27AAPFU0939F1ZV");
        b1.setPhone("9876543210");
        b1.setAddress("Nariman Point, Mumbai");

        String csv = CsvService.exportBuyers(List.of(b1), List.of());
        assertTrue(csv.contains("Acme Corp"));
        assertTrue(csv.contains("27AAPFU0939F1ZV"));
        assertTrue(csv.contains("9876543210"));
    }
}
