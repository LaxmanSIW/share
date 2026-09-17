import com.invoicestudio.db.AuthDao;
import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.model.UserSession;
import com.invoicestudio.ui.StudioApp;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Merchant's first-week tour: the real app over the 3-month simulated books.
 * Navigates every major stop, screenshots each view, times the big-list
 * renders, and verifies on-screen numbers against the seeded truth.
 */
public class MerchantTour extends StudioApp {

    static final List<String> problems = new CopyOnWriteArrayList<>();
    static final List<String> notes = new CopyOnWriteArrayList<>();
    static Stage stage;
    static File shots;

    public static void main(String[] args) {
        shots = new File(System.getProperty("tour.shots", "tour-shots"));
        shots.mkdirs();
        launch(args);
    }

    @Override
    public void start(Stage st) {
        stage = st;
        super.start(st);
        settle(6.0, this::stop1Dashboard);
    }

    // ---- helpers -------------------------------------------------------

    void settle(double ms, Runnable next) {
        PauseTransition p = new PauseTransition(Duration.millis(ms));
        p.setOnFinished(e -> Platform.runLater(next));
        p.play();
    }

    void shot(String name) { shotScene(stage.getScene(), name); }

    void shotScene(Scene scene, String name) {
        try {
            WritableImage img = scene.snapshot(null);
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(shots, name + ".png"));
            System.out.println("[SHOT] " + name);
        } catch (Exception e) { problems.add("shot " + name + ": " + e); }
    }

    Button btn(String text) {
        List<Button> out = new ArrayList<>();
        collect(stage.getScene().getRoot(), text, out);
        return out.isEmpty() ? null : out.get(0);
    }

    static void collect(Node n, String text, List<Button> out) {
        if (n == null) return;
        if (n instanceof Button b && text.equals(b.getText())) out.add(b);
        if (n instanceof Parent p) for (Node ch : p.getChildrenUnmodifiable()) collect(ch, text, out);
    }

    static void collectAll(Node n, List<Button> out) {
        if (n == null) return;
        if (n instanceof Button b && b.getText() != null && !b.getText().isBlank()) out.add(b);
        if (n instanceof Parent p) for (Node ch : p.getChildrenUnmodifiable()) collectAll(ch, out);
    }

    static Stage stageByTitle(String title) {
        for (Window w : Window.getWindows()) if (w instanceof Stage s && title.equals(s.getTitle())) return s;
        return null;
    }

    int countTables(Scene sc) {
        List<TableView<?>> found = new ArrayList<>();
        collectTables(sc.getRoot(), found);
        int rows = 0;
        for (TableView<?> tv : found) rows = Math.max(rows, tv.getItems().size());
        return rows;
    }

    static void collectTables(Node n, List<TableView<?>> out) {
        if (n == null) return;
        if (n instanceof TableView<?> tv) out.add(tv);
        if (n instanceof Parent p) for (Node ch : p.getChildrenUnmodifiable()) collectTables(ch, out);
    }

    List<String> labels(Scene sc) {
        List<String> out = new ArrayList<>();
        collectLabels(sc.getRoot(), out);
        return out;
    }

    static void collectLabels(Node n, List<String> out) {
        if (n == null) return;
        if (n instanceof Label l && l.getText() != null && !l.getText().isBlank()) out.add(l.getText());
        if (n instanceof Parent p) for (Node ch : p.getChildrenUnmodifiable()) collectLabels(ch, out);
    }

    void nav(String stop, String button, Runnable next) {
        long t0 = System.nanoTime();
        Button b = btn(button);
        if (b == null) { problems.add(stop + ": button '" + button + "' not found"); next.run(); return; }
        b.fire();
        settle(1500, () -> {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            int rows = countTables(stage.getScene());
            System.out.println("[STOP] " + stop + " via '" + button + "' render+settle=" + ms + "ms maxTableRows=" + rows);
            shot(stop);
            next.run();
        });
    }

    // ---- the tour ------------------------------------------------------

    void stop1Dashboard() {
        System.out.println("[TOUR] Kumar Textiles — dashboard");
        settle(1200, () -> { shot("01-dashboard"); stop2Invoices(); });
    }

    void stop2Invoices() {
        nav("02-invoices-119-bills", "Invoices", () -> {
            Button batch = btn("Export PDFs");
            if (batch == null) problems.add("02: batch 'Export PDFs' button missing on Invoices");
            else notes.add("02: batch Export PDFs button present beside Export CSV");
            stop3Expenses();
        });
    }

    void stop3Expenses() {
        nav("03-expenses", "Expenses", () -> {
            // Verify the register's KPI labels carry the seeded truth
            List<String> ls = labels(stage.getScene());
            boolean hasDirect = ls.stream().anyMatch(s -> s.contains("DIRECT"));
            boolean hasIndirect = ls.stream().anyMatch(s -> s.contains("INDIRECT"));
            if (!hasDirect || !hasIndirect) problems.add("03: expense KPI cards missing DIRECT/INDIRECT");
            else notes.add("03: DIRECT/INDIRECT KPI cards present on Expense Register");
            String count = ls.stream().filter(s -> s.startsWith("Showing ")).findFirst().orElse("");
            notes.add("03: register counter says '" + count + "'");
            if (!count.contains(" of 88 ")) problems.add("03: register does not show all 88 vouchers (" + count + ")");
            stop4ExpenseAccounts();
        });
    }

    void stop4ExpenseAccounts() {
        Button b = btn("Accounts");
        if (b == null) { problems.add("04: Accounts button missing"); stop5ExpenseReport(); return; }
        b.fire();
        settle(1200, () -> {
            Stage dlg = stageByTitle("Expense Accounts");
            if (dlg == null) problems.add("04: Expense Accounts stage not found");
            else {
                int rows = countTables(dlg.getScene());
                System.out.println("[STOP] 04-expense-accounts rows=" + rows);
                if (rows < 6) problems.add("04: account manager shows only " + rows + " rows (expected the backfilled payees)");
                else notes.add("04: account manager lists " + rows + " payee accounts with usage");
                shotScene(dlg.getScene(), "04-expense-accounts");
                Button close = findButtonInScene(dlg.getScene(), "Close");
                if (close != null) close.fire();
            }
            stop5ExpenseReport();
        });
    }

    void stop5ExpenseReport() {
        Button b = btn("Reports");
        if (b == null) { problems.add("05: Reports button missing"); stop6Purchases(); return; }
        b.fire();
        settle(1800, () -> {
            Stage dlg = stageByTitle("Expense Report — All Accounts & Categories");
            if (dlg == null) problems.add("05: Expense Report stage not found");
            else {
                shotScene(dlg.getScene(), "05-expense-report");
                notes.add("05: report opened; labels sample: "
                        + labels(dlg.getScene()).stream().limit(8).toList());
                Button close = findButtonInScene(dlg.getScene(), "Close");
                if (close != null) close.fire(); else dlg.hide();
            }
            stop6Purchases();
        });
    }

    void stop6Purchases() {
        Button p = btn("Purchases");
        if (p != null) { nav("06-purchases", "Purchases", this::stop7Items); }
        else { problems.add("06: Purchases sidebar missing"); stop7Items(); }
    }

    void openCatalog(String label, String stop, Runnable next) {
        Button catalog = btn("Catalog");
        if (catalog == null) { problems.add(stop + ": Catalog button missing"); next.run(); return; }
        catalog.fire();
        settle(700, () -> {
            Button dest = buttonInAnyWindow(label);
            if (dest == null) {
                problems.add(stop + ": catalog destination '" + label + "' not found");
                shot(stop + "-catalog-miss");
                next.run();
                return;
            }
            long t0 = System.nanoTime();
            dest.fire();
            settle(1500, () -> {
                long ms = (System.nanoTime() - t0) / 1_000_000;
                int rows = countTables(stage.getScene());
                System.out.println("[STOP] " + stop + " render+settle=" + ms + "ms maxTableRows=" + rows);
                shot(stop);
                next.run();
            });
        });
    }

    Button buttonInAnyWindow(String text) {
        for (Window w : Window.getWindows()) {
            if (w.getScene() == null) continue;
            List<Button> out = new ArrayList<>();
            collect(w.getScene().getRoot(), text, out);
            if (!out.isEmpty()) return out.get(0);
        }
        return null;
    }

    void stop7Items() {
        openCatalog("Items", "07-items-inventory", this::stop8Buyers);
    }

    void stop8Buyers() {
        openCatalog("Buyers", "08-buyers", this::stop9Financials);
    }

    void stop9Financials() {
        Button fin = btn("Financials");
        if (fin != null) nav("09-financials", "Financials", this::stop9bStock);
        else { problems.add("09: Financials sidebar missing"); stop9bStock(); }
    }

    void stop9bStock() {
        Button sa = btn("Stock & Profit");
        if (sa != null) nav("09b-stock-profit", "Stock & Profit", this::stop10NewBill);
        else { problems.add("09b: Stock & Profit sidebar missing"); stop10NewBill(); }
    }

    void stop10NewBill() {
        Button nb = btn("New Bill");
        if (nb == null) nb = btn("+ New Bill");
        if (nb == null) nb = firstContaining("New Bill");
        if (nb == null) { problems.add("10: New Bill entry missing"); finish(); return; }
        long t0 = System.nanoTime();
        nb.fire();
        settle(1800, () -> {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            List<String> ls = labels(stage.getScene());
            boolean formPresent = ls.stream().anyMatch(s -> s.contains("Qty") || s.contains("Rate"))
                    || ls.stream().anyMatch(s -> s.toLowerCase().contains("buyer"));
            System.out.println("[STOP] 10-new-bill render=" + ms + "ms formPresent=" + formPresent);
            shot("10-new-bill");
            if (!formPresent) problems.add("10: New Bill form did not render (no Qty/Rate/Buyer labels)");
            finish();
        });
    }

    Button firstContaining(String phrase) {
        List<Button> all = new ArrayList<>();
        collectAll(stage.getScene().getRoot(), all);
        return all.stream().filter(b -> b.getText().contains(phrase)).findFirst().orElse(null);
    }

    Button findButtonInScene(Scene sc, String text) {
        List<Button> out = new ArrayList<>();
        collect(sc.getRoot(), text, out);
        return out.isEmpty() ? null : out.get(0);
    }

    void finish() {
        System.out.println("=== TOUR FINDINGS ===");
        for (String n : notes) System.out.println("  NOTE  " + n);
        for (String p : problems) System.out.println("  ISSUE " + p);
        System.out.println(problems.isEmpty() ? "MERCHANT TOUR: CLEAN" : "MERCHANT TOUR: " + problems.size() + " issue(s)");
        // Clean teardown: close every window first, then exit (glass.dll lesson)
        settle(400, () -> {
            for (Window w : new ArrayList<>(Window.getWindows())) {
                if (w instanceof Stage s && s != stage) s.close();
            }
            settle(400, Platform::exit);
        });
    }
}
