import com.invoicestudio.db.AuthDao;
import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.model.*;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.service.BillingService;
import com.invoicestudio.service.ExpenseAccountService;
import com.invoicestudio.service.PurchaseService;
import com.invoicestudio.ui.DataManager;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * "Kumar Textiles, Mumbai" — a wholesale trouser merchant's first 3 months
 * on InvoiceStudio (June–August 2026), seeded exactly as a real user would
 * enter it. Deterministic (Random(42)) so reports can be verified twice.
 *
 * Run:  java -cp target/invoice-studio-desktop-4.0.0.jar;target/test-classes MerchantSimSeed
 * Then launch the app with -Dinvoicestudio.data.dir=merchant-sim to log in as
 * the seeded remembered session (uid_merchant).
 */
public class MerchantSimSeed {

    static final String DIR = "merchant-sim";
    static final LocalDate START = LocalDate.of(2026, 6, 1);
    static final LocalDate END = LocalDate.of(2026, 8, 31);

    record Spec(String name, String cat, double buy, double sell) {}
    static final Spec[] SPECS = {
            new Spec("Denim Straight Fit — Dark Blue",  "Denim",  420, 560),
            new Spec("Denim Slim Fit — Black",          "Denim",  440, 590),
            new Spec("Denim Regular Fit — Stone Wash",  "Denim",  430, 570),
            new Spec("Cotton Chino — Beige",            "Cotton", 310, 425),
            new Spec("Cotton Cargo — Olive",            "Cotton", 360, 490),
            new Spec("Cotton Lycra — Comfort Fit",      "Cotton", 350, 480),
            new Spec("Formal Trouser — Poly Viscose Black", "Formal", 380, 520),
            new Spec("Formal Trouser — Grey Stripe",    "Formal", 390, 535),
            new Spec("White Lycra Stretch",             "Lycra",  340, 470),
            new Spec("Black Lycra Stretch",             "Lycra",  340, 470),
            new Spec("Lycra Skinny Fit — Blue",         "Lycra",  355, 485),
            new Spec("Lycra Ankle Length — Navy",       "Lycra",  330, 450),
    };

    record BuyerSpec(String name, String city, String state, String code, String gst, boolean frequent) {}
    static final BuyerSpec[] BUYERS = {
            new BuyerSpec("Zaid Garments",            "Nagpur",    "Maharashtra",   "27", "27AAECK1234M1ZQ", true),
            new BuyerSpec("Royal Fashions",           "Pune",      "Maharashtra",   "27", "27AAFCR9876K1ZB", true),
            new BuyerSpec("Shree Balaji Collection",  "Nashik",    "Maharashtra",   "27", "",               true),
            new BuyerSpec("Metro Retail Pvt Ltd",     "Mumbai",    "Maharashtra",   "27", "27AAAGM4567P1Z8", true),
            new BuyerSpec("Khan Traders",             "Bhiwandi",  "Maharashtra",   "27", "",               true),
            new BuyerSpec("Patna Style House",        "Patna",     "Bihar",         "10", "10AABCP2345L1ZK", false),
            new BuyerSpec("Indore Denim Hub",         "Indore",    "Madhya Pradesh","23", "23AACCI7788N1Z2", false),
            new BuyerSpec("Surat Fashion Point",      "Surat",     "Gujarat",       "24", "",               false),
            new BuyerSpec("Hyderabad Trendz",         "Hyderabad", "Telangana",     "36", "36AABCH5566J1ZV", false),
            new BuyerSpec("Counter Sales (Walk-in)",  "Mumbai",    "Maharashtra",   "27", "",               false),
    };

    public static void main(String[] args) throws Exception {
        File dir = new File(DIR);
        dir.mkdirs();
        new File(dir, "invoicestudio.db").delete();
        System.setProperty("invoicestudio.data.dir", dir.getAbsolutePath());

        DatabaseManager db = DatabaseManager.initCustom("jdbc:sqlite:" + new File(dir, "invoicestudio.db").getAbsolutePath());
        UserSession session = new UserSession("uid_merchant", "kumar@textiles.in", "Rajesh Kumar",
                "seed", "seed", System.currentTimeMillis() + 90L * 24 * 3600_000, true);
        AuthSessionManager.setActiveSession(session);
        new AuthDao(db).saveSession(session);   // remembered login → app opens straight to dashboard
        DataManager dm = DataManager.init(db);

        seedSettings(dm);
        seedMasters(dm);
        Map<String, Integer> stock = new HashMap<>();          // item → pcs bought
        Map<String, Integer> sold  = new HashMap<>();          // item → pcs sold
        seedOpeningPurchases(dm, stock);

        double salesTaxable = 0, salesGst = 0, purchTotal = 0, expTotal = 0;
        int bills = 0, unpaid = 0; double unpaidAmt = 0;
        int inv = 0, pur = 3;   // opening purchases already used PUR-0001..0003
        Random r = new Random(42);
        List<LocalDate> replenish = List.of(LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 26),
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 24), LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 21));

        for (LocalDate d = START; !d.isAfter(END); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;   // market closed
            if (replenish.contains(d)) {
                PurchaseBill pb = new PurchaseBill();
                pb.setId("pur_" + d);
                pb.setBillNo(String.format("PUR-%04d", ++pur));
                pb.setSupplierBillNo(String.valueOf(100000 + r.nextInt(900000)));
                pb.setDate(d.toString());
                Supplier s = dm.getAllSuppliers().get(r.nextInt(3));
                pb.setSupplierId(s.getId()); pb.setSupplierName(s.getName()); pb.setSupplierGstin(s.getGst());
                // A real merchant reorders his FASTEST-MOVING (lowest-stock) lines
                List<int[]> needs = new ArrayList<>();
                for (int i = 0; i < SPECS.length; i++) {
                    int onHand = stock.getOrDefault(SPECS[i].name(), 0) - sold.getOrDefault(SPECS[i].name(), 0);
                    needs.add(new int[]{i, onHand});
                }
                needs.sort((a, b2) -> Integer.compare(a[1], b2[1]));
                List<BillItem> lines = new ArrayList<>();
                int lines2 = 3 + r.nextInt(3);
                for (int k = 0; k < lines2; k++) {
                    int idx = needs.get(k)[0];
                    int onHand = needs.get(k)[1];
                    Spec sp = SPECS[idx];
                    int q = Math.max(100, (r.nextInt(4) + 2) * 50 - Math.min(onHand, 0) * 0);
                    if (onHand < 0) q += -onHand;   // cover the shortfall too
                    lines.add(line(sp, q));
                    stock.merge(sp.name(), q, Integer::sum);
                }
                pb.setItems(lines);
                boolean inter = !s.getStateCode().equals("27");
                pb.setFreight(r.nextInt(8) * 100);
                pb.setTotals(PurchaseService.computePurchaseTotals(lines, 0, inter));
                pb.setPaid(r.nextBoolean());
                pb.setPaymentMode(pb.isPaid() ? List.of("Cash", "Bank Transfer", "UPI").get(r.nextInt(3)) : "");
                dm.savePurchase(pb);
                purchTotal += pb.getTotals().getGrandTotal();
            }
            int nBills = 1 + r.nextInt(2);
            for (int b = 0; b < nBills; b++) {
                BuyerSpec bs = pickBuyer(r);
                Bill bill = new Bill();
                bill.setId("bill_" + d + "_" + b);
                bill.setBillNo(String.format("INV-%04d", ++inv));
                bill.setDate(d.toString());
                bill.setBuyerName(bs.name());
                bill.setDocType(DocType.INVOICE);
                List<BillItem> items = new ArrayList<>();
                int lines = 1 + r.nextInt(3);
                int qty = 0;
                for (int k = 0; k < lines; k++) {
                    Spec sp = SPECS[r.nextInt(SPECS.length)];
                    int onHand = stock.getOrDefault(sp.name(), 0) - sold.getOrDefault(sp.name(), 0);
                    int q = 5 + r.nextInt(8) * 5;                       // 5..40 pcs
                    q = Math.min(q, onHand);                            // never sell what we don't have
                    if (q <= 0) continue;
                    items.add(line(sp, q));
                    qty += q;
                    sold.merge(sp.name(), q, Integer::sum);
                }
                if (items.isEmpty()) continue;
                bill.setItems(items);
                double disc = qty > 30 ? 2 + r.nextInt(4) : 0;
                bill.setDiscountPct(disc);
                boolean inter = !bs.code.equals("27");
                bill.setTotals(BillingService.computeTotals(items, disc, inter));
                bill.setAmountInWords(words((long) bill.getTotals().getGrandTotal()));
                boolean walkIn = bs.name().startsWith("Counter");
                if (walkIn || r.nextDouble() < 0.6) {
                    bill.setStatus(BillStatus.PAID);
                    BillPayment p = new BillPayment();
                    p.setId("pay_" + bill.getId());
                    p.setDate(d.toString());
                    p.setAmount(bill.getTotals().getGrandTotal());
                    p.setMethod(walkIn ? PaymentMethod.CASH
                            : List.of(PaymentMethod.UPI, PaymentMethod.BANK_TRANSFER, PaymentMethod.CHEQUE).get(r.nextInt(3)));
                    bill.setPayments(new ArrayList<>(List.of(p)));
                    bill.setPaidAt(d.toString());
                } else {
                    bill.setStatus(BillStatus.UNPAID);
                    unpaid++; unpaidAmt += bill.getTotals().getGrandTotal();
                }
                dm.saveBill(bill);
                bills++;
                salesTaxable += bill.getTotals().getTaxable();
                salesGst += bill.getTotals().getCgst() + bill.getTotals().getSgst() + bill.getTotals().getIgst();
            }
            // daily op expenses
            expTotal += seedDayExpense(dm, d, r);
        }
        // month-end Salaries already handled per day? No — add explicitly:
        for (LocalDate m : List.of(START, START.plusMonths(1), START.plusMonths(2))) {
            expTotal += exp(dm, m.plusDays(1), "Salaries", "Rakesh S. (Cutting Master)", 12000, "Cash");
            expTotal += exp(dm, m.plusDays(2), "Salaries", "Santosh Y. (Packing & Dispatch)", 11000, "Cash");
            expTotal += exp(dm, m.withDayOfMonth(3), "Electricity", "MSEB", 1600 + r.nextInt(6) * 100, "UPI");
        }
        for (ExpenseAccountService a : java.util.List.<ExpenseAccountService>of()) {} // no-op guard
        int seededAccounts = ExpenseAccountService.backfillNow(dm);

        Settings st = dm.getSettings();
        st.setBillNoNext(inv + 1);
        dm.saveSettings(st);

        System.out.println("=== MERCHANT SIM SEEDED (" + DIR + ") ===");
        System.out.println("Period          : " + START + " to " + END);
        System.out.println("Invoices        : " + bills + "  (INV-0001.." + String.format("INV-%04d", inv) + ")");
        System.out.println("Purchases       : " + pur + " vouchers, ₹" + round(purchTotal));
        System.out.println("Sales taxable   : ₹" + round(salesTaxable) + "  + GST ₹" + round(salesGst));
        System.out.println("Expenses        : ₹" + round(expTotal) + "  (" + dm.getAllExpenses().size() + " vouchers, accounts seeded: " + seededAccounts + ")");
        System.out.println("Outstanding     : " + unpaid + " bills, ₹" + round(unpaidAmt));
        System.out.println("--- Stock on hand (bought - sold) ---");
        for (Spec sp : SPECS) {
            System.out.printf("  %-38s %5d pcs%n", sp.name(), stock.getOrDefault(sp.name(), 0) - sold.getOrDefault(sp.name(), 0));
        }
        System.out.println("Login           : session pre-seeded (uid_merchant, remember-me)");
    }

    static BillItem line(Spec sp, int qty) {
        BillItem bi = new BillItem();
        bi.setDesc(sp.name());
        bi.setHsn("6203");
        bi.setQty(qty);
        bi.setRate(sp.sell);
        bi.setGst(12);
        return bi;
    }

    static BuyerSpec pickBuyer(Random r) {
        while (true) {
            BuyerSpec bs = BUYERS[r.nextInt(BUYERS.length)];
            if (bs.frequent() && r.nextInt(10) < 7) return bs;
            if (!bs.frequent() && r.nextInt(10) < 3) return bs;
        }
    }

    static double seedDayExpense(DataManager dm, LocalDate d, Random r) {
        double sum = 0;
        if (d.getDayOfMonth() == 1) sum += exp(dm, d, "Office Rent", "Shivneri Complex Estate", 15000, "Bank Transfer");
        if (r.nextInt(10) < 2) sum += exp(dm, d, "Freight Inward", "VRL Logistics", 800 + r.nextInt(9) * 100, "UPI");
        if (r.nextInt(10) < 4) sum += exp(dm, d, "Packaging", "Shree Poly Packs", 400 + r.nextInt(10) * 100, "Cash");
        if (r.nextInt(14) < 2) sum += exp(dm, d, "Transport", "Local Tempo", 500 + r.nextInt(6) * 100, "Cash");
        if (r.nextInt(7) < 1) sum += exp(dm, d, "Tea & Pantry", "Canteen", 150 + r.nextInt(6) * 50, "Cash");
        return sum;
    }

    static double exp(DataManager dm, LocalDate d, String cat, String payee, double amt, String mode) {
        Expense e = new Expense("exp_" + d + "_" + cat.replace(" ", "").replace("&", "") + "_" + payee.split(" ")[0],
                d.toString(), cat, cat + " — " + payee, amt, mode);
        e.setPayee(payee);
        dm.saveExpense(e);
        return amt;
    }

    static void seedSettings(DataManager dm) {
        Settings st = new Settings();
        st.getBusiness().setName("Kumar Textiles");
        st.getBusiness().setAddress("Shop 14, Textile Market, Mangaldas Road\nMumbai – 400002, Maharashtra");
        st.getBusiness().setGstin("27AAJCK1234N1ZP");
        st.getBusiness().setPhone("+91 98205 43210");
        st.getBusiness().setState("Maharashtra");
        st.getBusiness().setStateCode("27");
        st.setCurrency("₹");
        st.setInterState(false);
        st.setBillNoNext(1);
        dm.saveSettings(st);
    }

    static void seedMasters(DataManager dm) {
        String[] cats = {"Denim", "Cotton", "Formal", "Lycra"};
        for (int i = 0; i < cats.length; i++)
            dm.categories().saveCategory(new ItemCategory("cat_" + cats[i].toLowerCase(), cats[i]));
        String[][] sups = {
                {"sup_denimmills", "Aurangabad Denim Mills", "27AAECA1111H1Z9", "Maharashtra", "27", "90210 11111"},
                {"sup_erodecotton", "Erode Cotton Fabrics", "33AAECE2222J1Z7", "Tamil Nadu", "33", "90210 22222"},
                {"sup_lucky", "Lucky Menswear Imports", "07AAECL3333K1Z5", "Delhi", "07", "90210 33333"},
        };
        for (String[] s : sups) {
            Supplier su = new Supplier(s[0], s[1]);
            su.setGst(s[2]); su.setState(s[3]); su.setStateCode(s[4]); su.setPhone(s[5]);
            dm.suppliers().saveSupplier(su);
        }
        String[][] buys = {
                {"buy_zaid", "Zaid Garments", "Nagpur", "27AAECK1234M1ZQ", "1"},
                {"buy_royal", "Royal Fashions", "Pune", "27AAFCR9876K1ZB", "1"},
                {"buy_balaji", "Shree Balaji Collection", "Nashik", "", "1"},
                {"buy_metro", "Metro Retail Pvt Ltd", "Mumbai", "27AAAGM4567P1Z8", "1"},
                {"buy_khan", "Khan Traders", "Bhiwandi", "", "1"},
                {"buy_patna", "Patna Style House", "Patna", "10AABCP2345L1ZK", "0"},
                {"buy_indore", "Indore Denim Hub", "Indore", "23AACCI7788N1Z2", "0"},
                {"buy_surat", "Surat Fashion Point", "Surat", "", "0"},
                {"buy_hyd", "Hyderabad Trendz", "Hyderabad", "36AABCH5566J1ZV", "0"},
        };
        for (String[] b : buys) {
            BuyerSpec spec = Arrays.stream(BUYERS).filter(x -> x.name().equals(b[1])).findFirst().orElseThrow();
            Buyer bu = new Buyer(b[0], b[1], "Market Yard, " + b[2], b[3],
                "98" + (10000000 + Math.abs(b[0].hashCode()) % 89999999), spec.state(), spec.code());
            bu.setCity(b[2]);
            dm.buyers().saveBuyer(bu);
        }
        int i = 0;
        for (Spec sp : SPECS) {
            ItemRecord it = new ItemRecord("itm_" + (++i), sp.name(), "6203", "PCS", sp.sell, 12);
            it.setPurchaseRate(sp.buy());
            it.setCategoryId("cat_" + sp.cat().toLowerCase());
            it.setCategoryName(sp.cat());
            it.setReorderLevel(50);
            dm.items().saveItem(it);
        }
    }

    static void seedOpeningPurchases(DataManager dm, Map<String, Integer> stock) {
        String[][] opens = {
                {"pur_open1", "sup_denimmills", "Aurangabad Denim Mills", "27", "2026-06-01", "DM/26-27/441",
                        "0;300", "1;250", "2;200", "9;150", "10;150", "11;120"},   // denim + lycra
                {"pur_open2", "sup_erodecotton", "Erode Cotton Fabrics", "33", "2026-06-01", "EC/883",
                        "3;250", "4;180", "5;200", "6;150", "7;150"},               // cotton + formal
                {"pur_open3", "sup_lucky", "Lucky Menswear Imports", "07", "2026-06-01", "LMI/2026/77",
                        "8;200", "10;180", "2;150", "7;120"},                       // lycra + extras
        };
        int pur = 0;
        for (String[] o : opens) {
            PurchaseBill pb = new PurchaseBill();
            pb.setId(o[0]);
            pb.setBillNo(String.format("PUR-%04d", ++pur));
            pb.setSupplierBillNo(o[5]);
            pb.setDate(o[4]);
            pb.setSupplierId(o[1]); pb.setSupplierName(o[2]);
            Supplier s = dm.getAllSuppliers().stream().filter(x -> x.getId().equals(o[1])).findFirst().orElseThrow();
            pb.setSupplierGstin(s.getGst());
            List<BillItem> lines = new ArrayList<>();
            for (int k = 6; k < o.length; k++) {
                String[] part = o[k].split(";");
                Spec sp = SPECS[Integer.parseInt(part[0])];
                int q = Integer.parseInt(part[1]);
                lines.add(line(sp, q));
                // cost price on the opening lines:
                lines.get(lines.size() - 1).setRate(sp.buy());
                stock.merge(sp.name(), q, Integer::sum);
            }
            pb.setItems(lines);
            boolean inter = !o[3].equals("27");
            pb.setTotals(PurchaseService.computePurchaseTotals(lines, 0, inter));
            pb.setPaid(true);
            pb.setPaymentMode("Bank Transfer");
            dm.savePurchase(pb);
        }
    }

    static String words(long n) {
        if (n <= 0) return "";
        String[] ones = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
                "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        StringBuilder sb = new StringBuilder();
        int cr = (int) (n / 10000000); n %= 10000000;
        int lk = (int) (n / 100000); n %= 100000;
        int th = (int) (n / 1000); n %= 1000;
        int h = (int) (n / 100); n %= 100;
        if (cr > 0) sb.append(num(cr, ones, tens)).append(" Crore ");
        if (lk > 0) sb.append(num(lk, ones, tens)).append(" Lakh ");
        if (th > 0) sb.append(num(th, ones, tens)).append(" Thousand ");
        if (h > 0) sb.append(ones[h]).append(" Hundred ");
        if (n > 0) sb.append(num((int) n, ones, tens));
        return (sb.toString().trim() + " Rupees Only");
    }

    static String num(int n, String[] ones, String[] tens) {
        if (n < 20) return ones[n];
        return tens[n / 10] + (n % 10 > 0 ? " " + ones[n % 10] : "");
    }

    static String round(double d) { return String.format("%,.0f", d); }
}
