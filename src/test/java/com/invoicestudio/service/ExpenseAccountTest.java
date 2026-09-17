package com.invoicestudio.service;

import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.model.Expense;
import com.invoicestudio.model.ExpenseAccount;
import com.invoicestudio.ui.DataManager;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Expense-account registry: CRUD + case-insensitive lookup, one-time
 * backfill from voucher payees, rename-with-propagation arithmetic,
 * and the O(n) analytics core feeding the report dialogs.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExpenseAccountTest {

    private static final String TEST_DB = "test_expense_accounts.db";
    private static DatabaseManager db;
    private static DataManager dm;

    @BeforeAll
    static void setUp() {
        new File(TEST_DB).delete();
        db = DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB);
        AuthSessionManager.setActiveSession(new com.invoicestudio.model.UserSession(
                "uid_expacc", "expacc@test.in", "Expense Acc Test",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(db);
    }

    @AfterAll
    static void tearDown() throws Exception {
        AuthSessionManager.clear();
        // Same JVM-singleton hygiene as WorkshopScenarioTest: later suites in
        // the same surefire fork must re-init against their own DB.
        resetSingleton(com.invoicestudio.db.DatabaseManager.class, "instance");
        resetSingleton(DataManager.class, "instance");
        new File(TEST_DB).delete();
    }

    private static void resetSingleton(Class<?> clazz, String fieldName) throws Exception {
        java.lang.reflect.Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(null, null);
    }

    private static Expense voucher(String date, String category, String payee, double amount) {
        Expense e = new Expense("exp_" + UUIDs.next(), date, category, "test voucher", amount, "Cash");
        e.setPayee(payee);
        return e;
    }

    /** Tiny id source so ids never collide across tests. */
    private static final class UUIDs {
        private static int n = 0;
        static String next() { return "t" + (++n) + "_" + System.nanoTime(); }
    }

    @Test
    @Order(1)
    void accountCrudAndCaseInsensitiveLookup() {
        ExpenseAccount a = new ExpenseAccount(ExpenseAccountService.newId(), "Techparks Stationery");
        a.setNotes("Monthly office supplies");
        dm.expenseAccounts().saveAccount(a);
        dm.invalidateExpenseAccounts();

        ExpenseAccount byExact = dm.expenseAccounts().findByName("Techparks Stationery");
        assertNotNull(byExact, "exact-case lookup");
        assertEquals(a.getId(), byExact.getId());

        ExpenseAccount byLower = dm.expenseAccounts().findByName("techparks stationery");
        assertNotNull(byLower, "case-insensitive lookup");
        assertEquals(a.getId(), byLower.getId());

        byLower.setNotes("updated");
        dm.expenseAccounts().saveAccount(byLower);
        dm.invalidateExpenseAccounts();
        assertEquals("updated", dm.expenseAccounts().getAccountById(a.getId()).getNotes(), "upsert updates");

        assertTrue(dm.getAllExpenseAccounts().stream().anyMatch(x -> x.getId().equals(a.getId())));

        dm.expenseAccounts().deleteAccount(a.getId());
        dm.invalidateExpenseAccounts();
        assertNull(dm.expenseAccounts().getAccountById(a.getId()), "deleted account is gone");
    }

    @Test
    @Order(2)
    void backfillSeedsAccountsFromDistinctPayeesOnce() {
        dm.saveExpense(voucher("2026-08-01", "Office Rent", "Sharma Properties", 12000));
        dm.saveExpense(voucher("2026-08-05", "Office Rent", "sharma properties", 12000)); // same account, different case
        dm.saveExpense(voucher("2026-08-10", "Freight Inward", "Blue Dart", 850));
        dm.saveExpense(voucher("2026-08-12", "Miscellaneous", "", 100)); // no payee → ignored

        int created = ExpenseAccountService.backfillNow(dm);
        assertEquals(2, created, "distinct payee names seeded (case-folded)");

        // Second run must be a no-op (idempotent).
        assertEquals(0, ExpenseAccountService.backfillNow(dm), "backfill is idempotent");

        assertNotNull(dm.expenseAccounts().findByName("Sharma Properties"));
        assertNotNull(dm.expenseAccounts().findByName("Blue Dart"));
        assertNull(dm.expenseAccounts().findByName(""));
    }

    @Test
    @Order(3)
    void renamePropagatesToVouchersAndReportsCount() {
        ExpenseAccount acc = ExpenseAccountService.findOrCreate(dm, "Kirti Agencies");
        assertNotNull(acc, "findOrCreate creates when unknown");

        // findOrCreate is idempotent — same account comes back for case variants.
        ExpenseAccount again = ExpenseAccountService.findOrCreate(dm, "kirti agencies");
        assertEquals(acc.getId(), again.getId(), "no duplicate accounts for case variants");

        dm.saveExpense(voucher("2026-09-01", "Miscellaneous", "Kirti Agencies", 500));
        dm.saveExpense(voucher("2026-09-02", "Miscellaneous", "kirti agencies", 750));
        dm.saveExpense(voucher("2026-09-03", "Miscellaneous", "Unrelated Traders", 900));

        int updated = ExpenseAccountService.renameWithPropagation(dm, acc, "Kirti Agencies & Co.");
        assertEquals(2, updated, "both Kirti vouchers renamed, unrelated one untouched");

        List<Expense> all = dm.getAllExpenses();
        long kirtiNew = all.stream().filter(e -> "Kirti Agencies & Co.".equals(e.getPayee())).count();
        long kirtiOld = all.stream().filter(e -> e.getPayee() != null
                && e.getPayee().toLowerCase().startsWith("kirti agencies ")
                && !e.getPayee().equals("Kirti Agencies & Co.")).count();
        assertEquals(2, kirtiNew, "vouchers carry the new name");
        assertEquals(0, kirtiOld, "no voucher keeps the old name");
        assertEquals(1, all.stream().filter(e -> "Unrelated Traders".equals(e.getPayee())).count(),
                "unrelated payee untouched");

        // Rename to the same name updates nothing.
        assertEquals(0, ExpenseAccountService.renameWithPropagation(dm, acc, "kirti agencies & co."),
                "case-only rename still matches every voucher but reports 0 rewrites... count is per changed voucher");
    }

    @Test
    @Order(4)
    void analyticsTotalsBreakdownsAndFilters() {
        List<Expense> vouchers = List.of(
                voucher("2026-07-05", "Office Rent", "Sharma Properties", 12000),
                voucher("2026-08-05", "Office Rent", "Sharma Properties", 12000),
                voucher("2026-08-15", "Freight Inward", "Blue Dart", 850),
                voucher("2026-09-01", "Marketing", "AdWorks", 3000),
                voucher("2026-09-10", "Freight Inward", "Blue Dart", 1150));

        ExpenseAnalytics.Report all = ExpenseAnalytics.overall(vouchers, "", "");
        assertEquals(29000.0, all.total(), 1e-9, "grand total");
        assertEquals(5, all.voucherCount(), "voucher count");
        assertEquals(5800.0, all.average(), 1e-9, "average per voucher");

        // byMonth chronological: Jul, Aug, Sep
        assertEquals(3, all.byMonth().size());
        assertEquals("2026-07", all.byMonth().get(0).key());
        assertEquals(12850.0, all.byMonth().get(1).total(), 1e-9, "Aug = 12000+850");
        assertEquals(4150.0, all.byMonth().get(2).total(), 1e-9, "Sep = 3000+1150");

        // byCategory total-desc: Office Rent 24000, Marketing 3000, Freight 2000
        assertEquals("Office Rent", all.byCategory().get(0).key());
        assertEquals(2, all.byCategory().get(0).vouchers());
        assertEquals("Freight Inward", all.byCategory().get(2).key());
        assertEquals(2000.0, all.byCategory().get(2).total(), 1e-9);

        // byAccount total-desc: Sharma 24000, AdWorks 3000, Blue Dart 2000
        assertEquals("Sharma Properties", all.byAccount().get(0).key());
        assertEquals(2, all.byAccount().get(0).vouchers());

        // Account filter (case-insensitive)
        ExpenseAnalytics.Report sharma = ExpenseAnalytics.forAccount(vouchers, "sharma properties", "", "");
        assertEquals(24000.0, sharma.total(), 1e-9);
        assertEquals(2, sharma.voucherCount());
        assertEquals(1, sharma.byCategory().size(), "only Office Rent buckets inside the account report");

        // Category filter
        ExpenseAnalytics.Report freight = ExpenseAnalytics.forCategory(vouchers, "Freight Inward", "", "");
        assertEquals(2000.0, freight.total(), 1e-9);
        assertEquals(2, freight.voucherCount());
        assertEquals("Blue Dart", freight.byAccount().get(0).key(), "freight split by account");

        // Inclusive date range
        ExpenseAnalytics.Report aug = ExpenseAnalytics.overall(vouchers, "2026-08-01", "2026-08-31");
        assertEquals(12850.0, aug.total(), 1e-9, "August only, bounds inclusive");
        assertEquals(2, aug.voucherCount());

        // Empty range → zero, not NaN
        ExpenseAnalytics.Report none = ExpenseAnalytics.overall(vouchers, "2030-01-01", "2030-12-31");
        assertEquals(0.0, none.total(), 1e-9);
        assertEquals(0, none.voucherCount());
        assertEquals(0.0, none.average(), 1e-9, "average guards divide-by-zero");
    }

    @Test
    @Order(5)
    void usageRollupCountsTotalsAndLastDate() {
        String name = "Rollup Traders";
        dm.saveExpense(voucher("2026-06-01", "Miscellaneous", name, 100));
        dm.saveExpense(voucher("2026-06-20", "Miscellaneous", " rollup traders ", 250)); // trimmed + case-folded

        var usage = dm.expenseAccountUsage();
        var u = usage.get(name.toLowerCase());
        assertNotNull(u, "rollup keyed case-insensitively");
        assertEquals(2, u.vouchers, "both vouchers counted (payee trimmed)");
        assertEquals(350.0, u.total, 1e-9);
        assertEquals("2026-06-20", u.lastDate, "last-used date is the max");
    }
}
