package com.invoicestudio.db;

import com.invoicestudio.model.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    private static DatabaseManager db;
    private static ItemDao itemDao;
    private static BuyerDao buyerDao;
    private static BillDao billDao;
    private static TemplateDao templateDao;
    private static SettingsDao settingsDao;
    private static VariableDao variableDao;
    private static final String TEST_DB_FILE = "test_studio.db";

    @BeforeAll
    static void setUp() {
        new File(TEST_DB_FILE).delete();
        com.invoicestudio.service.AuthSessionManager.setActiveSession(
                new com.invoicestudio.model.UserSession("test_suite_user", "test@invoicestudio.test", "Test User", "id_tok", "ref_tok", System.currentTimeMillis() + 86400000L, true)
        );
        db = DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB_FILE);
        itemDao = new ItemDao(db);
        buyerDao = new BuyerDao(db);
        billDao = new BillDao(db);
        templateDao = new TemplateDao(db);
        settingsDao = new SettingsDao(db);
        variableDao = new VariableDao(db);
    }

    @AfterAll
    static void tearDown() {
        com.invoicestudio.service.AuthSessionManager.clear();
        new File(TEST_DB_FILE).delete();
    }

    @Test
    void testItemCrud() {
        ItemRecord it = new ItemRecord("it_test_01", "Graphic Design Services", "998311", "HRS", 1500, 18);
        itemDao.save(it);

        ItemRecord retrieved = itemDao.findById("it_test_01");
        assertNotNull(retrieved);
        assertEquals("Graphic Design Services", retrieved.getName());
        assertEquals(1500.0, retrieved.getRate());

        it.setRate(2000.0);
        itemDao.save(it);
        assertEquals(2000.0, itemDao.findById("it_test_01").getRate());

        itemDao.delete("it_test_01");
        assertNull(itemDao.findById("it_test_01"));
    }

    @Test
    void testItemSaveGeneratesIdWhenMissing() {
        ItemRecord item = new ItemRecord();
        item.setName("New Auto-ID Item");
        item.setHsn("9999");
        item.setUnit("PCS");
        item.setRate(250.0);
        item.setGst(18.0);

        itemDao.save(item);

        assertNotNull(item.getId(), "Item ID should be generated when missing");
        assertNotNull(itemDao.findById(item.getId()));

        itemDao.delete(item.getId());
        assertNull(itemDao.findById(item.getId()));
    }

    @Test
    void testBuyerCrud() {
        Buyer buyer = new Buyer();
        buyer.setId("byr_test_01");
        buyer.setName("Solaris Enterprises");
        buyer.setGstin("27AAPFU0939F1ZV");
        buyer.setPhone("9876543210");
        buyer.setState("Maharashtra");
        buyer.setStateCode("27");
        buyer.getCustom().put("credit_limit", "50000");
        buyerDao.save(buyer);

        Buyer found = buyerDao.findById("byr_test_01");
        assertNotNull(found);
        assertEquals("Solaris Enterprises", found.getName());
        assertEquals("27", found.getStateCode());
        assertEquals("27", found.getEffectiveStateCode());
        assertEquals("50000", found.getCustom().get("credit_limit"));

        Buyer foundByName = buyerDao.findByName("solaris enterprises");
        assertNotNull(foundByName);
        assertEquals("byr_test_01", foundByName.getId());

        buyerDao.delete("byr_test_01");
        assertNull(buyerDao.findById("byr_test_01"));
    }

    @Test
    void testSettingsSaveAndGet() {
        Settings s = settingsDao.get();
        assertNotNull(s);

        s.setCurrency("₹");
        s.setBillNoPrefix("TEST-");
        s.setBillNoNext(42);

        settingsDao.save(s);

        Settings updated = settingsDao.get();
        assertEquals("TEST-", updated.getBillNoPrefix());
        assertEquals(42, updated.getBillNoNext());
    }

    @Test
    void testBillCrud() {
        Bill b = new Bill();
        b.setId("bill_test_01");
        b.setBillNo("TEST-001");
        b.setDate("2025-01-10");
        b.setDocType(DocType.INVOICE);
        b.setStatus(BillStatus.UNPAID);
        b.setBuyerName("Acme Ltd");
        b.setTotals(new BillTotals(1000, 90, 90, 0, 1180, 0, 1180));

        billDao.save(b);

        Bill found = billDao.findById("bill_test_01");
        assertNotNull(found);
        assertEquals("TEST-001", found.getBillNo());
        assertEquals("Acme Ltd", found.getBuyerName());
        assertEquals(1180.0, found.getTotals().getGrandTotal());

        billDao.delete("bill_test_01");
        assertNull(billDao.findById("bill_test_01"));
    }

    @Test
    void testVariablesCrud() {
        VariableDef v = new VariableDef("po_date", "Purchase Order Date", "date", false);
        variableDao.save(v);

        List<VariableDef> custom = variableDao.findCustom();
        assertTrue(custom.stream().anyMatch(var -> "po_date".equals(var.getKey())));

        variableDao.delete("po_date");
        assertFalse(variableDao.findCustom().stream().anyMatch(var -> "po_date".equals(var.getKey())));
    }
}
