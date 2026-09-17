import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.service.PdfExportService;
import com.invoicestudio.ui.DataManager;
import com.invoicestudio.model.UserSession;
import com.invoicestudio.model.Bill;

import java.io.File;
import java.util.List;

/**
 * Bulk-print stress test on the merchant's 3-month book: render EVERY invoice
 * through the real PDF pipeline (the same renderer bulk print feeds), time it,
 * and verify each file landed non-empty. Then multiply copies on a subset.
 */
public class MerchantBulkStress {
    public static void main(String[] args) throws Exception {
        File dir = new File(MerchantSimSeed.DIR);
        System.setProperty("invoicestudio.data.dir", dir.getAbsolutePath());
        DatabaseManager db = DatabaseManager.initCustom(
                "jdbc:sqlite:" + new File(dir, "invoicestudio.db").getAbsolutePath());
        AuthSessionManager.setActiveSession(new UserSession("uid_merchant", "kumar@textiles.in",
                "Rajesh Kumar", "t", "r", System.currentTimeMillis() + 3600_000, true));
        DataManager dm = DataManager.init(db);
        dm.seedIfEmpty();   // presets (templates) seed exactly like the real app's first run

        List<Bill> bills = dm.getAllBills();
        Template tpl = dm.templates().getAllTemplates().stream()
                .filter(t -> "bill".equals(t.getMode())).findFirst()
                .orElseGet(() -> dm.templates().getAllTemplates().get(0));
        Settings st = dm.getSettings();

        File out = new File(dir, "pdf-out");
        out.mkdirs();
        for (File f : out.listFiles()) f.delete();

        // Pass 1 — every invoice, 1 copy each
        long t0 = System.nanoTime();
        int ok = 0, bad = 0;
        for (Bill b : bills) {
            try {
                File f = new File(out, b.getBillNo() + ".pdf");
                PdfExportService.exportBillPdf(b, tpl, st, f, 1);
                if (f.exists() && f.length() > 1000) ok++; else bad++;
            } catch (Exception e) {
                bad++;
                System.out.println("  FAIL " + b.getBillNo() + ": " + e);
            }
        }
        long oneCopyMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("PASS1 single-copy bulk export: " + bills.size() + " invoices → ok=" + ok
                + " bad=" + bad + " in " + oneCopyMs + "ms ("
                + String.format("%.1f", bills.size() * 1000.0 / Math.max(1, oneCopyMs)) + " docs/sec = "
                + String.format("%.0f", bills.size() * 60000.0 / Math.max(1, oneCopyMs)) + " docs/min)");

        // Pass 2 — 20 invoices × 5 copies (the "party needs 5 copies" run)
        long t1 = System.nanoTime();
        int ok2 = 0;
        for (int i = 0; i < 20; i++) {
            Bill b = bills.get(i);
            File f = new File(out, b.getBillNo() + "-x5.pdf");
            PdfExportService.exportBillPdf(b, tpl, st, f, 5);
            if (f.exists() && f.length() > 1000) ok2++;
        }
        long multiMs = (System.nanoTime() - t1) / 1_000_000;
        System.out.println("PASS2 multi-copy: 20 invoices x 5 copies → ok=" + ok2 + "/20 in " + multiMs + "ms");

        long total = 0; int count = 0;
        for (File f : out.listFiles()) { total += f.length(); count++; }
        System.out.println("Output: " + count + " PDFs, " + String.format("%.1f", total / 1024.0 / 1024.0) + " MB in pdf-out/");
        System.out.println(bad == 0 && ok2 == 20 ? "BULK STRESS: SUCCESS" : "BULK STRESS: FAILURES PRESENT");
    }
}
