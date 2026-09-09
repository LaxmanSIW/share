import com.invoicestudio.model.*;
import com.invoicestudio.service.BillingService;
import com.invoicestudio.ui.StudioApp;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Deep runtime verification harness (v2).
 *
 * Differences vs v1 SmokeRunner:
 *  - Real navigation: fires actual sidebar Buttons / row action Buttons found in
 *    the live scene graph (same ActionEvent path as a user click), not just API calls.
 *  - Seeds realistic data through the REAL save path (DataManager.saveBill → cache
 *    invalidation + listener notification) so tables, KPIs and dialogs have content.
 *  - Drives dialogs: opens History "View" preview + Buyers "Ledger", screenshots them
 *    open, closes them via their own CLOSE button.
 *  - Drives every bill flow: edit / duplicate / convert / repeat + CreateBill with
 *    initialTemplateId and initialItem variants.
 *  - Second full sidebar round to exercise the cached-view refresher paths.
 *  - Captures screenshots of every step + open dialogs.
 *  - Global uncaught-exception trap (default + FX thread handler): ANY rendering
 *    error (layout pass, cell factory, CSS apply) fails the run.
 *
 * Exit code 0 = all steps passed and no uncaught exceptions.
 */
public class NavSmokeRunner extends StudioApp {

    static final List<String> failures = new CopyOnWriteArrayList<>();
    static final List<String> passed = new CopyOnWriteArrayList<>();
    static final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
    static final List<String> shotLog = new CopyOnWriteArrayList<>();
    static final File SHOT_DIR = new File(System.getProperty("smoke.shots", "screenshots"));

    Stage stage;
    int stepIndex = 0;
    int dialogShotSeq = 0;
    List<String> colOrderBefore;
    final List<Step> steps = new ArrayList<>();

    record Step(String name, Runnable action, Predicate<NavSmokeRunner> verify) {}

    // ------------------------------------------------------------------
    // Bootstrap
    // ------------------------------------------------------------------

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        super.start(stage);

        // Trap exceptions on the FX thread specifically (handler on the thread
        // itself takes precedence over the default one).
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if ("JavaFX Application Thread".equals(t.getName())) {
                t.setUncaughtExceptionHandler((th, ex) -> recordUncaught(th, ex));
            }
        }

        // Wait for background DB init + first dashboard render, then begin.
        settleThen(6.0, this::buildSteps);
    }

    static void recordUncaught(Thread t, Throwable ex) {
        uncaught.add(ex);
        System.err.println("[UNCAUGHT][" + t.getName() + "] " + ex);
        ex.printStackTrace();
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(NavSmokeRunner::recordUncaught);
        // Do NOT set invoicestudio.init=1 — we want the production path.
        launch(args);
    }

    // ------------------------------------------------------------------
    // Step list
    // ------------------------------------------------------------------

    void buildSteps() {
        seedData(); // direct, synchronous — happens once before the walk

        steps.clear();
        steps.add(new Step("01-initial-dashboard", () -> {},
                r -> contentNodeCount() > 10));

        // -- Real sidebar navigation round 1 --
        steps.add(new Step("02-sidebar-dashboard-click", () -> clickSidebar("Dashboard"),
                r -> contentNodeCount() > 10));
        steps.add(new Step("03-sidebar-createbill-click", () -> clickSidebar("Create Bill"),
                r -> contentNodeCount() > 40));
        steps.add(new Step("04-sidebar-history-click", () -> clickSidebar("History"),
                r -> firstButton("View") != null));
        steps.add(new Step("04b-status-combo-open", () -> Platform.runLater(() -> {
                    Node cb = contentArea().lookup(".combo-box");
                    if (cb instanceof ComboBox<?> c) c.show();
                    else fail("status-combo-open", "no combo-box in content");
                }), r -> !openPopups().isEmpty()));
        steps.add(new Step("04c-status-combo-close", () -> Platform.runLater(() -> {
                    shotPopups("popup-status-combo");
                    openPopups().forEach(PopupWindow::hide);
                }), r -> openPopups().isEmpty()));
        steps.add(new Step("04d-more-menu-open", () -> Platform.runLater(() -> {
                    MenuButton mb = firstMenuButton();
                    if (mb != null) mb.show();
                    else fail("more-menu-open", "no MenuButton in content");
                }), r -> !openPopups().isEmpty()));
        steps.add(new Step("04e-more-menu-close", () -> Platform.runLater(() -> {
                    shotPopups("popup-more-menu");
                    openPopups().forEach(PopupWindow::hide);
                }), r -> openPopups().isEmpty()));
        steps.add(new Step("05-history-view-dialog-open", () -> fireAsync(firstButton("View"), "View"),
                NavSmokeRunner::isDialogOpen));
        steps.add(new Step("05b-history-view-dialog-close", () -> closeDialogAsync(),
                r -> !isDialogOpen()));
        steps.add(new Step("06-history-edit-bill", () -> editBill(billNo("INV-SMOKE-001")),
                r -> contentNodeCount() > 40));
        steps.add(new Step("07-sidebar-buyers-click", () -> clickSidebar("Buyers"),
                r -> firstButton("Ledger") != null));
        steps.add(new Step("08-buyers-ledger-dialog-open", () -> fireAsync(firstButton("Ledger"), "Ledger"),
                NavSmokeRunner::isDialogOpen));
        steps.add(new Step("08b-buyers-ledger-dialog-close", () -> closeDialogAsync(),
                r -> !isDialogOpen()));
        steps.add(new Step("09-sidebar-items-click", () -> clickSidebar("Items"),
                r -> contentNodeCount() > 20));
        steps.add(new Step("10-items-sales-reports-tab", () -> clickContentButton("Sales Reports"),
                r -> true));
        steps.add(new Step("11-sidebar-variables-click", () -> clickSidebar("Variables"),
                r -> contentNodeCount() > 20));
        steps.add(new Step("12-sidebar-settings-click", () -> clickSidebar("Settings"),
                r -> contentNodeCount() > 20));
        steps.add(new Step("13-sidebar-templates-click", () -> clickSidebar("Templates"),
                r -> firstContentButton("Designer") != null));
        steps.add(new Step("14-open-template-designer", () -> safeFire(firstContentButton("Designer"), "Designer"),
                r -> contentNodeCount() > 50));

        // -- Template Designer: TABLE column reorder via ▲▼ property-panel buttons --
        steps.add(new Step("14b-designer-select-table-layer", () -> selectTableLayer(),
                r -> columnLabelOrder() != null && columnLabelOrder().size() >= 3));
        steps.add(new Step("14c-col-move-up-row2", () -> { colOrderBefore = columnLabelOrder(); fireColMove("▲", 1); },
                r -> verifySwapped(colOrderBefore, 0, 1)));
        steps.add(new Step("14d-col-move-down-row1", () -> { colOrderBefore = columnLabelOrder(); fireColMove("▼", 0); },
                r -> verifySwapped(colOrderBefore, 0, 1)));
        steps.add(new Step("14e-col-move-boundaries", () -> {},
                r -> boundaryButtonsDisabled()));

        steps.add(new Step("15-leave-designer-templates", () -> clickSidebar("Templates"),
                r -> firstContentButton("Designer") != null));

        // -- CreateBill variants --
        steps.add(new Step("16-template-card-createbill", () -> clickContentButton("Create Bill"),
                r -> contentNodeCount() > 40));
        steps.add(new Step("17-createbill-initial-item", () -> showCreateBill(firstTemplateId(), firstItem()),
                r -> contentNodeCount() > 40));
        steps.add(new Step("18-duplicate-bill-flow", () -> duplicateBill(billNo("INV-SMOKE-002")),
                r -> contentNodeCount() > 40));
        steps.add(new Step("19-convert-bill-flow", () -> convertBill(billNo("INV-SMOKE-003")),
                r -> contentNodeCount() > 40));
        steps.add(new Step("20-repeat-bill-flow", () -> repeatBill(billNo("INV-SMOKE-001")),
                r -> contentNodeCount() > 40));

        // -- Round 2: exercise every cached-view refresher --
        steps.add(new Step("21-r2-history", () -> clickSidebar("History"), r -> firstButton("View") != null));
        steps.add(new Step("22-r2-buyers", () -> clickSidebar("Buyers"), r -> firstButton("Ledger") != null));
        steps.add(new Step("23-r2-items", () -> clickSidebar("Items"), r -> contentNodeCount() > 20));
        steps.add(new Step("24-r2-variables", () -> clickSidebar("Variables"), r -> contentNodeCount() > 20));
        steps.add(new Step("25-r2-settings", () -> clickSidebar("Settings"), r -> contentNodeCount() > 20));
        steps.add(new Step("26-r2-templates", () -> clickSidebar("Templates"), r -> firstContentButton("Designer") != null));
        steps.add(new Step("27-r2-dashboard-final", () -> clickSidebar("Dashboard"), r -> contentNodeCount() > 10));

        stepIndex = 0;
        runStep();
    }

    // ------------------------------------------------------------------
    // Step engine
    // ------------------------------------------------------------------

    void runStep() {
        if (stepIndex >= steps.size()) { finish(); return; }
        Step s = steps.get(stepIndex);
        try {
            s.action().run(); // may block in dialog nested loop — that's expected
        } catch (Throwable ex) {
            fail(s.name(), "action threw: " + ex);
            ex.printStackTrace();
            renderThen(() -> { shot(s.name()); stepIndex++; runStep(); });
            return;
        }
        renderThen(() -> {
            try {
                if (s.verify() != null && !s.verify().test(NavSmokeRunner.this)) {
                    fail(s.name(), "verify failed (see earlier context if any)");
                } else {
                    pass(s.name());
                }
            } catch (Throwable ex) {
                fail(s.name(), "verify threw: " + ex);
                ex.printStackTrace();
            }
            shot(s.name());
            stepIndex++;
            runStep();
        });
    }

    void finish() {
        System.out.println();
        System.out.println("=== NAV SMOKE RUNNER DONE ===");
        System.out.println("PASSED: " + passed.size());
        passed.forEach(s -> System.out.println("  [PASS] " + s));
        System.out.println("FAILED: " + failures.size());
        failures.forEach(s -> System.out.println("  [FAIL] " + s));
        System.out.println("UNCAUGHT (any thread): " + uncaught.size());
        for (int i = 0; i < uncaught.size(); i++) {
            Throwable t = uncaught.get(i);
            System.out.println("  [" + (i + 1) + "] " + t);
        }
        System.out.println("SCREENSHOTS: " + shotLog.size());
        shotLog.forEach(System.out::println);
        boolean ok = failures.isEmpty() && uncaught.isEmpty();
        System.out.println(ok ? "NAV SMOKE: SUCCESS" : "NAV SMOKE: FAILED");
        Platform.exit();
        Runtime.getRuntime().halt(ok ? 0 : 1);
    }

    void pass(String name) { passed.add(name + " rendered"); System.out.println("[PASS] " + name); }
    void fail(String name, String why) { failures.add(name + " — " + why); System.out.println("[FAIL] " + name + " — " + why); }

    // ------------------------------------------------------------------
    // Seeding (real save path: DataManager.saveBill → cache invalidate + listeners)
    // ------------------------------------------------------------------

    void seedData() {
        try {
            var dm = getData();
            Template tpl = dm.templates().getAllTemplates().get(0);

            Buyer b1 = new Buyer("smk_buy_1", "Aurora Textiles Pvt Ltd", "12 Weavers Road, Surat", "24AABCA1234C1Z5", "+91 98200 11111", "Gujarat", "24");
            Buyer b2 = new Buyer("smk_buy_2", "Nimbus Traders", "45 MG Road, Bengaluru", "29AABCN5678D1Z2", "+91 98200 22222", "Karnataka", "29");
            Buyer b3 = new Buyer("smk_buy_3", "Kavya Interiors", "7 Park Street, Kolkata", "19AABCK9012E1Z8", "+91 98200 33333", "West Bengal", "19");
            dm.buyers().saveBuyer(b1);
            dm.buyers().saveBuyer(b2);
            dm.buyers().saveBuyer(b3);

            ItemRecord i1 = new ItemRecord("smk_itm_1", "Cotton Fabric Roll", "5208", "ROLL", 850.0, 5.0);
            ItemRecord i2 = new ItemRecord("smk_itm_2", "Designer Cushion Set", "9404", "SET", 1250.0, 18.0);
            ItemRecord i3 = new ItemRecord("smk_itm_3", "Wall Panel (Oak)", "4411", "PCS", 2100.0, 18.0);
            dm.items().saveItem(i1);
            dm.items().saveItem(i2);
            dm.items().saveItem(i3);

            String today = LocalDate.now().toString();

            Bill bill1 = new Bill();
            bill1.setId("smk_bill_1");
            bill1.setBillNo("INV-SMOKE-001");
            bill1.setDate(LocalDate.now().minusDays(70).toString());
            bill1.setDocType(DocType.INVOICE);
            bill1.setStatus(BillStatus.PAID);
            bill1.setTemplateId(tpl.getId());
            bill1.setTemplateName(tpl.getName());
            bill1.setBuyerName(b1.getName());
            bill1.setNotes("Seeded by NavSmokeRunner");
            bill1.setItems(List.of(
                    new BillItem("it1", "Cotton Fabric Roll", "5208", 12, "ROLL", 850.0, 5.0, 0),
                    new BillItem("it2", "Designer Cushion Set", "9404", 4, "SET", 1250.0, 18.0, 0)));
            bill1.setTotals(BillingService.computeTotals(bill1.getItems(), 0, false));
            dm.saveBill(bill1);

            Bill bill2 = new Bill();
            bill2.setId("smk_bill_2");
            bill2.setBillNo("INV-SMOKE-002");
            bill2.setDate(LocalDate.now().minusDays(20).toString());
            bill2.setDocType(DocType.INVOICE);
            bill2.setStatus(BillStatus.UNPAID);
            bill2.setTemplateId(tpl.getId());
            bill2.setTemplateName(tpl.getName());
            bill2.setBuyerName(b2.getName());
            bill2.setDiscountPct(5);
            bill2.setItems(List.of(
                    new BillItem("it3", "Wall Panel (Oak)", "4411", 6, "PCS", 2100.0, 18.0, 0)));
            bill2.setTotals(BillingService.computeTotals(bill2.getItems(), 5, false));
            dm.saveBill(bill2);

            Bill bill3 = new Bill();
            bill3.setId("smk_bill_3");
            bill3.setBillNo("INV-SMOKE-003");
            bill3.setDate(LocalDate.now().minusDays(3).toString());
            bill3.setDocType(DocType.QUOTATION);
            bill3.setStatus(BillStatus.UNPAID);
            bill3.setTemplateId(tpl.getId());
            bill3.setTemplateName(tpl.getName());
            bill3.setBuyerName(b3.getName());
            bill3.setItems(List.of(
                    new BillItem("it4", "Cotton Fabric Roll", "5208", 2, "ROLL", 850.0, 5.0, 0),
                    new BillItem("it5", "Designer Cushion Set", "9404", 1, "SET", 1250.0, 18.0, 0),
                    new BillItem("it6", "Wall Panel (Oak)", "4411", 3, "PCS", 2100.0, 18.0, 0)));
            bill3.setTotals(BillingService.computeTotals(bill3.getItems(), 0, false));
            dm.saveBill(bill3);

            System.out.println("[SEED] seeded 3 buyers, 3 items, 3 bills; template=" + tpl.getName()
                    + " totalBills=" + dm.bills().getAllBills().size());
        } catch (Throwable t) {
            failures.add("seedData — " + t);
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // Scene-graph helpers (real navigation)
    // ------------------------------------------------------------------

    Parent contentArea() {
        Node n = stage.getScene().getRoot().lookup(".content-area");
        if (n instanceof Parent p) return p;
        throw new IllegalStateException("content-area node not found");
    }

    int contentNodeCount() {
        return countNodes(stage.getScene().getRoot());
    }

    static int countNodes(Node n) {
        if (n == null) return 0;
        int c = 1;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) c += countNodes(ch);
        }
        return c;
    }

    List<Button> findButtons(Parent root, String exactText) {
        List<Button> out = new ArrayList<>();
        collectButtons(root, exactText, out);
        return out;
    }

    static void collectButtons(Node n, String exactText, List<Button> out) {
        if (n == null) return;
        if (n instanceof Button b && exactText.equals(b.getText())) out.add(b);
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) collectButtons(ch, exactText, out);
        }
    }

    /** First matching Button anywhere in the scene (sidebar included). */
    Button firstButton(String text) {
        List<Button> all = findButtons(stage.getScene().getRoot(), text);
        return all.isEmpty() ? null : all.get(0);
    }

    /** First matching Button inside the main content area only (sidebar excluded). */
    Button firstContentButton(String text) {
        List<Button> all = findButtons(contentArea(), text);
        return all.isEmpty() ? null : all.get(0);
    }

    void clickSidebar(String label) {
        Button btn = firstButton(label);
        if (btn == null) throw new IllegalStateException("sidebar button not found: " + label);
        btn.fire(); // same ActionEvent path as a real user click
    }

    void clickContentButton(String label) {
        Button btn = firstContentButton(label);
        if (btn == null) throw new IllegalStateException("content button not found: " + label);
        btn.fire();
    }

    void safeFire(Button btn, String what) {
        if (btn == null) throw new IllegalStateException("button not found: " + what);
        btn.fire();
    }

    // ------------------------------------------------------------------
    // Designer column-reorder helpers (▲▼ buttons in properties panel)
    // ------------------------------------------------------------------

    /** Selects the first TABLE element through the Layers list — the same
     *  selection-model path a user click uses, which rebuilds the properties
     *  panel with the column manager. Also force-selects the Properties tab. */
    @SuppressWarnings("unchecked")
    void selectTableLayer() {
        for (Node n : stage.getScene().getRoot().lookupAll(".tab-pane")) {
            if (n instanceof TabPane tp) {
                for (Tab t : tp.getTabs()) {
                    if ("Properties".equals(t.getText())) tp.getSelectionModel().select(t);
                }
            }
        }
        Node n = stage.getScene().getRoot().lookup(".layers-list");
        if (!(n instanceof ListView)) throw new IllegalStateException("layers list not found");
        ListView<TemplateElement> lv = (ListView<TemplateElement>) n;
        for (int i = 0; i < lv.getItems().size(); i++) {
            TemplateElement te = lv.getItems().get(i);
            if (te.getType() == ElementType.TABLE) {
                lv.getSelectionModel().select(i);
                return;
            }
        }
        throw new IllegalStateException("no TABLE element in template");
    }

    /** Column label texts of the properties-panel column manager, in row order. */
    List<String> columnLabelOrder() {
        List<HBox> rows = new ArrayList<>();
        collectColRows(stage.getScene().getRoot(), rows);
        if (rows.isEmpty()) return null;
        List<String> out = new ArrayList<>();
        for (HBox row : rows) {
            for (Node ch : row.getChildren()) {
                if (ch instanceof TextField tf) { out.add(tf.getText()); break; }
            }
        }
        return out.size() == rows.size() ? out : null;
    }

    static void collectColRows(Node n, List<HBox> out) {
        if (n == null) return;
        if (n instanceof HBox hb && hb.getStyleClass().contains("col-row")) { out.add(hb); return; }
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) collectColRows(ch, out);
        }
    }

    /** Fires the ▲/▼ button of column row {@code rowIdx} (real button action). */
    void fireColMove(String text, int rowIdx) {
        List<Button> btns = new ArrayList<>();
        collectColMoveButtons(stage.getScene().getRoot(), text, btns);
        if (rowIdx >= btns.size()) {
            throw new IllegalStateException("col-move-btn not found: '" + text + "' row " + rowIdx
                    + " (found " + btns.size() + ")");
        }
        Button b = btns.get(rowIdx);
        if (b.isDisabled()) {
            throw new IllegalStateException("move button unexpectedly disabled: '" + text + "' row " + rowIdx);
        }
        b.fire();
    }

    static void collectColMoveButtons(Node n, String text, List<Button> out) {
        if (n == null) return;
        if (n instanceof Button b && text.equals(b.getText())
                && b.getStyleClass().contains("col-move-btn")) out.add(b);
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) collectColMoveButtons(ch, text, out);
        }
    }

    /** True if {@code now} equals {@code before} with rows i and j exchanged. */
    boolean verifySwapped(List<String> before, int i, int j) {
        List<String> now = columnLabelOrder();
        if (before == null || now == null || before.size() != now.size()) return false;
        for (int k = 0; k < now.size(); k++) {
            int expect = (k == i) ? j : (k == j) ? i : k;
            if (!now.get(k).equals(before.get(expect))) return false;
        }
        return true;
    }

    /** First row's ▲ and last row's ▼ must be disabled; interior ones enabled. */
    boolean boundaryButtonsDisabled() {
        List<String> order = columnLabelOrder();
        if (order == null || order.isEmpty()) return false;
        List<Button> ups = new ArrayList<>();
        List<Button> downs = new ArrayList<>();
        collectColMoveButtons(stage.getScene().getRoot(), "▲", ups);
        collectColMoveButtons(stage.getScene().getRoot(), "▼", downs);
        int n = order.size();
        if (ups.size() != n || downs.size() != n) return false;
        if (!ups.get(0).isDisabled()) return false;
        if (!downs.get(n - 1).isDisabled()) return false;
        if (n > 1) {
            if (downs.get(0).isDisabled()) return false;
            if (ups.get(n - 1).isDisabled()) return false;
        }
        return true;
    }

    /**
     * Fires a button from the FX event queue instead of the current animation
     * callback — this mirrors a real user click (event dispatch context) and
     * makes dlg.showAndWait() legal inside the handler.
     */
    void fireAsync(Button btn, String what) {
        if (btn == null) throw new IllegalStateException("button not found: " + what);
        Platform.runLater(() -> {
            try {
                btn.fire();
            } catch (Throwable t) {
                fail(what + "-fire", String.valueOf(t));
                t.printStackTrace();
            }
        });
    }

    // ------------------------------------------------------------------
    // Dialog helpers
    // ------------------------------------------------------------------

    static DialogPane findOpenDialogPane() {
        for (Window w : Window.getWindows()) {
            if (w.getScene() != null && w.getScene().getRoot() instanceof DialogPane dp) return dp;
        }
        return null;
    }

    boolean isDialogOpen() { return findOpenDialogPane() != null; }

    static List<PopupWindow> openPopups() {
        List<PopupWindow> out = new ArrayList<>();
        for (Window w : Window.getWindows()) {
            if (w instanceof PopupWindow p) out.add(p);
        }
        return out;
    }

    MenuButton firstMenuButton() {
        return findFirstNode(contentArea(), MenuButton.class);
    }

    static <T extends Node> T findFirstNode(Parent root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        for (Node ch : root.getChildrenUnmodifiable()) {
            if (type.isInstance(ch)) return type.cast(ch);
            if (ch instanceof Parent p) {
                T found = findFirstNode(p, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    static void shotPopups(String base) {
        List<PopupWindow> pops = openPopups();
        for (int i = 0; i < pops.size(); i++) {
            if (pops.get(i).getScene() != null) {
                shotNode(pops.get(i).getScene(), String.format("%s-%d", base, i + 1));
            }
        }
        if (pops.isEmpty()) System.out.println("[POPUP] none open for " + base);
    }

    /**
     * Screenshots the open dialog's own scene, then closes it via its own
     * CLOSE/CANCEL/OK button — dispatched from the event queue (real-user
     * equivalent of clicking the close button).
     */
    void closeDialogAsync() {
        Platform.runLater(() -> {
            DialogPane dp = findOpenDialogPane();
            if (dp == null) {
                fail("dialog-close", "no dialog open when close was requested");
                return;
            }
            shotNode(dp.getScene(), String.format("dialog-%02d", ++dialogShotSeq));
            ButtonType target = dp.getButtonTypes().stream()
                    .filter(bt -> bt == ButtonType.CLOSE || bt == ButtonType.CANCEL
                            || bt == ButtonType.OK || bt == ButtonType.FINISH)
                    .findFirst().orElse(null);
            if (target != null && dp.lookupButton(target) instanceof Button b) {
                b.fire();
            } else if (dp.getScene() != null && dp.getScene().getWindow() != null) {
                dp.getScene().getWindow().hide();
            }
        });
    }

    // ------------------------------------------------------------------
    // Bill flow helpers
    // ------------------------------------------------------------------

    String firstTemplateId() {
        List<Template> t = getData().templates().getAllTemplates();
        return t.isEmpty() ? null : t.get(0).getId();
    }

    ItemRecord firstItem() {
        List<ItemRecord> it = getData().items().getAllItems();
        return it.isEmpty() ? null : it.get(0);
    }

    Bill billNo(String no) {
        return getData().bills().getAllBills().stream()
                .filter(b -> no.equals(b.getBillNo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("seeded bill not found: " + no));
    }

    // ------------------------------------------------------------------
    // Screenshots
    // ------------------------------------------------------------------

    void shot(String name) {
        shotNode(stage.getScene(), String.format("%02d-%s", stepIndex + 1, name.replaceAll("[^A-Za-z0-9_-]", "_")));
    }

    static void shotNode(Scene scene, String base) {
        try {
            if (!SHOT_DIR.exists()) SHOT_DIR.mkdirs();
            double w = Math.max(1, scene.getWidth()), h = Math.max(1, scene.getHeight());
            WritableImage img = scene.snapshot(new WritableImage((int) w, (int) h));
            File f = new File(SHOT_DIR, base + ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", f);
            shotLog.add(f.getName());
            System.out.println("[SHOT] " + f.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[SHOT-FAIL] " + base + ": " + t);
        }
    }

    // ------------------------------------------------------------------
    // Timing helpers
    // ------------------------------------------------------------------

    void renderThen(Runnable r) {
        PauseTransition p = new PauseTransition(Duration.millis(1300));
        p.setOnFinished(e -> r.run());
        p.play();
    }

    void settleThen(double seconds, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.seconds(seconds));
        p.setOnFinished(e -> r.run());
        p.play();
    }
}
