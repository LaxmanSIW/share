import com.invoicestudio.model.*;
import com.invoicestudio.service.BillingService;
import com.invoicestudio.ui.ColorPickerButton;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.views.TemplateDesigner;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.control.MenuButton;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
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
import java.util.Optional;
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
    static final double MM_PX = 3.7795275591; // ~96 DPI

    Stage stage;
    int stepIndex = 0;
    int dialogShotSeq = 0;
    List<String> colOrderBefore;
    List<Double> rowHBefore;
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

        // -- v3.0.0 layers panel: switch to the Layers tab FIRST so its list cells
        //      materialize during the render gap, then drive eye/lock in-list --
        steps.add(new Step("14a-designer-layers-tab", () -> selectSideTab("Layers"),
                r -> {
                    try { return layerCell(0) != null; } catch (Throwable t) { return false; }
                }));
        steps.add(new Step("14a2-layers-eye-hide", () -> fireLayerIcon(0, 0),
                r -> firstLayerElement().map(TemplateElement::isHidden).orElse(false)));
        steps.add(new Step("14a3-layers-eye-show", () -> fireLayerIcon(0, 0),
                r -> firstLayerElement().map(e -> !e.isHidden()).orElse(false)));
        steps.add(new Step("14a4-layers-lock", () -> fireLayerIcon(0, 1),
                r -> firstLayerElement().map(TemplateElement::isLocked).orElse(false)));
        steps.add(new Step("14a5-layers-unlock", () -> fireLayerIcon(0, 1),
                r -> firstLayerElement().map(e -> !e.isLocked()).orElse(false)));

        // -- Template Designer: TABLE column reorder via ▲▼ property-panel buttons --
        steps.add(new Step("14b-designer-select-table-layer", () -> selectTableLayer(),
                r -> columnLabelOrder() != null && columnLabelOrder().size() >= 3));
        steps.add(new Step("14c-col-move-up-row2", () -> { colOrderBefore = columnLabelOrder(); fireColMove("▲", 1); },
                r -> verifySwapped(colOrderBefore, 0, 1)));
        steps.add(new Step("14d-col-move-down-row1", () -> { colOrderBefore = columnLabelOrder(); fireColMove("▼", 0); },
                r -> verifySwapped(colOrderBefore, 0, 1)));
        steps.add(new Step("14e-col-move-boundaries", () -> {},
                r -> boundaryButtonsDisabled()));

        // -- Template Designer: TABLE border / row-color / row-height properties --
        steps.add(new Step("14f-table-prop-controls", () -> {},
                r -> tablePropControlsPresent()));
        steps.add(new Step("14g-table-border-rows-only", () -> setBorderType("Rows Only"),
                r -> borderTypeIs("Rows Only")));
        steps.add(new Step("14h-table-border-restore-grid", () -> setBorderType("Grid"),
                r -> borderTypeIs("Grid")));
        steps.add(new Step("14i-table-border-side-top-off", () -> fireCheckBox("T"),
                r -> selectedTableElement().map(t -> !t.isBorderTop()).orElse(false)));
        steps.add(new Step("14j-table-border-side-top-on", () -> fireCheckBox("T"),
                r -> selectedTableElement().map(TemplateElement::isBorderTop).orElse(false)));
        steps.add(new Step("14k-table-rowheight-applies", () -> { rowHBefore = canvasTableRowHeights(); setRowHeightByLabel("Row Height (mm):", 12.0); },
                r -> rowHeightChanged(rowHBefore, 12.0)));
        steps.add(new Step("14l-table-rowheight-restore", () -> setRowHeightByLabel("Row Height (mm):", 6.0),
                r -> true));

        // -- v3.0.0: shapes, per-side stroke, layers panel, magnet, rulers, dialogs --
        steps.add(new Step("31-shape-ellipse-add", () -> safeFire(firstContentButton("+ Ellipse"), "+ Ellipse"),
                r -> selectedTableElement().map(e -> e.getType() == ElementType.ELLIPSE).orElse(false)));
        steps.add(new Step("32-shape-stroke-controls", () -> {},
                r -> findComboBoxWithItem("dashed") != null && findLabel("Stroke Width (mm):") != null));
        steps.add(new Step("33-shape-star-add", () -> safeFire(firstContentButton("+ Star"), "+ Star"),
                r -> selectedTableElement().map(e -> e.getType() == ElementType.STAR).orElse(false)
                        && findLabel("Star Points:") != null));
        steps.add(new Step("34-shape-arrow-add", () -> safeFire(firstContentButton("+ Arrow"), "+ Arrow"),
                r -> selectedTableElement().map(e -> e.getType() == ElementType.ARROW).orElse(false)));
        steps.add(new Step("35-rect-per-side-section", () -> safeFire(firstContentButton("+ Rect"), "+ Rect"),
                r -> findCheckBox("Top") != null && findCheckBox("Bottom") != null
                        && findCheckBox("Left") != null && findCheckBox("Right") != null));
        steps.add(new Step("36-rect-side-top-off", () -> fireCheckBox("Top"),
                r -> selectedTableElement().map(e -> !e.isBorderTop()).orElse(false)));
        steps.add(new Step("37-rect-side-top-on", () -> fireCheckBox("Top"),
                r -> selectedTableElement().map(TemplateElement::isBorderTop).orElse(false)));
        steps.add(new Step("42-object-rename", () -> renameViaPropField("Smoke Name"),
                r -> selectedTableElement().map(e -> "Smoke Name".equals(e.getName())).orElse(false)));
        steps.add(new Step("43-backspace-in-field-safe", () -> backspaceInSelectedTextElement(),
                r -> textElementStillPresent()));
        steps.add(new Step("44-color-picker-dialog-open", () -> fireAsync(firstColorPickerButton(), "color-picker"),
                r -> dialogWithHeader("Choose Color")));
        steps.add(new Step("45-color-picker-close", () -> closeDialogAsync(), r -> !isDialogOpen()));
        steps.add(new Step("46-shortcuts-help-dialog", () -> fireAsync(firstContentButton("? Help"), "? Help"),
                r -> dialogWithHeader("Template Designer — Shortcuts & Mouse Controls")));
        steps.add(new Step("47-shortcuts-help-close", () -> closeDialogAsync(), r -> !isDialogOpen()));
        steps.add(new Step("48-rulers-and-handles", () -> {},
                r -> rulerTicksPresent() && selectionHandleCount() == 6));
        steps.add(new Step("49-magnet-snap-math", () -> {}, r -> magnetSnapWorks()));

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

        // -- Dashboard revenue delta: red when negative, green when positive --
        // One '›' from the current month lands on a month with no bills whose
        // predecessor HAS bills → delta = -100.0% → sub label must turn RED.
        steps.add(new Step("28-delta-negative-red", () ->
                        safeFire(firstContentButton("\u203a"), "next-month \u203a"),
                r -> {
                    Label sub = revenueDeltaSubLabel();
                    return sub != null && sub.getStyleClass().contains("accent-red")
                            && sub.getText() != null && sub.getText().contains("-100.0%");
                }));
        // Step 28 left the dashboard on Oct 2026. Reset to the live month,
        // then two '‹' back: Jul 2026 holds ₹16610 (INV-SMOKE-001) vs empty
        // Jun 2026 → delta = +100.0% → sub label must turn GREEN. Top bar is
        // rebuilt on every refresh(), so re-lookup before each fire.
        steps.add(new Step("29-delta-positive-green", () -> {
                    safeFire(firstContentButton("Current Month"), "reset to current month");
                    safeFire(firstContentButton("\u2039"), "prev-month \u2039 (1/2)");
                    safeFire(firstContentButton("\u2039"), "prev-month \u2039 (2/2)");
                },
                r -> {
                    Label sub = revenueDeltaSubLabel();
                    return sub != null && sub.getStyleClass().contains("accent-emerald")
                            && sub.getText() != null && sub.getText().contains("+100.0%");
                }));
        // Restore the live month so any later navigation starts from "now".
        steps.add(new Step("30-delta-restore-current", () ->
                        safeFire(firstContentButton("Current Month"), "current-month"),
                r -> revenueDeltaSubLabel() != null));

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
    // Dashboard KPI helpers (revenue delta red/green verification)
    // ------------------------------------------------------------------

    /** Order-preserving DFS collecting labels carrying the kpi-subtext class. */
    static void collectByClass(Node n, String cls, List<Label> out) {
        if (n == null) return;
        if (n instanceof Label l && l.getStyleClass().contains(cls)) out.add(l);
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) collectByClass(ch, cls, out);
        }
    }

    /** The "%+N.N% vs last month" sub-label on the THIS MONTH REVENUE KPI card. */
    Label revenueDeltaSubLabel() {
        List<Label> subs = new ArrayList<>();
        collectByClass(contentArea(), "kpi-subtext", subs);
        return subs.stream()
                .filter(l -> l.getText() != null && l.getText().contains("vs last month"))
                .findFirst().orElse(null);
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

    // ------------------------------------------------------------------
    // Designer table-property helpers (border / row colors / row height)
    // ------------------------------------------------------------------

    /** The TABLE element currently selected through the Layers list. */
    Optional<TemplateElement> selectedTableElement() {
        Node n = stage.getScene().getRoot().lookup(".layers-list");
        if (n instanceof ListView<?> lv
                && lv.getSelectionModel().getSelectedItem() instanceof TemplateElement te) {
            return Optional.of(te);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // v3.0.0 helpers: shapes, layers panel, magnet, rulers, dialogs
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    ListView<TemplateElement> layersListView() {
        return (ListView<TemplateElement>) stage.getScene().getRoot().lookup(".layers-list");
    }

    Optional<TemplateElement> firstLayerElement() {
        ListView<TemplateElement> lv = layersListView();
        return lv != null && !lv.getItems().isEmpty() ? Optional.of(lv.getItems().get(0)) : Optional.empty();
    }

    /** Selects the first layer of the given type through the real selection-model path. */
    void selectLayerByType(ElementType type) {
        for (Node n : stage.getScene().getRoot().lookupAll(".tab-pane")) {
            if (n instanceof TabPane tp) {
                for (Tab t : tp.getTabs()) {
                    if ("Properties".equals(t.getText())) tp.getSelectionModel().select(t);
                }
            }
        }
        ListView<TemplateElement> lv = layersListView();
        for (int i = 0; i < lv.getItems().size(); i++) {
            if (lv.getItems().get(i).getType() == type) {
                lv.getSelectionModel().select(i);
                return;
            }
        }
        throw new IllegalStateException("no " + type + " element in template");
    }

    @SuppressWarnings("unchecked")
    ListCell<TemplateElement> layerCell(int index) {
        List<ListCell<TemplateElement>> cells = new ArrayList<>();
        for (Node n : stage.getScene().getRoot().lookupAll(".list-cell")) {
            if (n instanceof ListCell<?> lc && lc.getListView() == layersListView()
                    && lc.getItem() != null && lc.getIndex() >= 0) {
                cells.add((ListCell<TemplateElement>) lc);
            }
        }
        // VirtualFlow keeps spare blank cells — drop them, then match by real index
        return cells.stream()
                .filter(c -> c.getIndex() == index)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "layer cell " + index + " not rendered (rendered: "
                                + cells.stream().map(ListCell::getIndex).sorted().toList() + ")"));
    }

    static void collectLayerIconButtons(Node n, List<Button> out) {
        if (n == null) return;
        if (n instanceof Button b && b.getStyleClass().contains("layer-icon-btn")) { out.add(b); return; }
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) collectLayerIconButtons(ch, out);
        }
    }

    /** Fires the eye (buttonIndex 0) or padlock (1) of the layer cell {@code itemIndex}. */
    void fireLayerIcon(int itemIndex, int buttonIndex) {
        selectSideTab("Layers");
        List<Button> iconBtns = new ArrayList<>();
        collectLayerIconButtons(layerCell(itemIndex).getGraphic(), iconBtns);
        if (iconBtns.size() <= buttonIndex) {
            throw new IllegalStateException("layer icon buttons missing (found " + iconBtns.size() + ")");
        }
        iconBtns.get(buttonIndex).fire();
    }

    /** Forces the designer's Properties/Layers side tab (TabPane detaches hidden content). */
    void selectSideTab(String title) {
        for (Node n : stage.getScene().getRoot().lookupAll(".tab-pane")) {
            if (n instanceof TabPane tp) {
                for (Tab t : tp.getTabs()) {
                    if (title.equals(t.getText())) tp.getSelectionModel().select(t);
                }
            }
        }
        // newly attached tab content only materializes its skin/cells after a
        // layout pulse — force one synchronously so lookups see the cells
        Node root = stage.getScene().getRoot();
        root.applyCss();
        if (root instanceof Parent p) p.layout();
    }

    /** Types {@code name} into the Properties-panel object-name field. */
    void renameViaPropField(String name) {
        selectSideTab("Properties");
        TextField f = (TextField) findNode(stage.getScene().getRoot(), n ->
                n instanceof TextField tf && "Object name".equals(tf.getPromptText()));
        if (f == null) throw new IllegalStateException("object-name field not found");
        f.setText(name);
    }

    TemplateElement backspaceTarget;
    int elementsBeforeBackspace;

    /**
     * Selects a TEXT element, focuses its content TextArea and fires real
     * BACK_SPACE key events at it — the element itself must survive.
     */
    void backspaceInSelectedTextElement() {
        selectLayerByType(ElementType.TEXT);
        backspaceTarget = selectedTableElement().orElseThrow();
        elementsBeforeBackspace = layersListView().getItems().size();
        TextArea ta = (TextArea) findNode(stage.getScene().getRoot(), n ->
                n instanceof TextArea t && t.getStyleClass().contains("designer-textarea"));
        if (ta == null) throw new IllegalStateException("designer text area not found");
        ta.requestFocus();
        KeyEvent backspace = new KeyEvent(KeyEvent.KEY_PRESSED, "\b", "\b",
                KeyCode.BACK_SPACE, false, false, false, false);
        Event.fireEvent(ta, backspace);
        Event.fireEvent(ta, backspace);
        Event.fireEvent(ta, backspace);
    }

    boolean textElementStillPresent() {
        ListView<TemplateElement> lv = layersListView();
        return lv.getItems().size() == elementsBeforeBackspace && lv.getItems().contains(backspaceTarget);
    }

    Button firstColorPickerButton() {
        Button b = (Button) findNode(stage.getScene().getRoot(), n ->
                n instanceof Button btn && btn.getStyleClass().contains("color-btn"));
        if (b == null) throw new IllegalStateException("no ColorPickerButton found");
        return b;
    }

    boolean dialogWithHeader(String title) {
        DialogPane dp = findOpenDialogPane();
        return dp != null && title.equals(dp.getHeaderText());
    }

    Label findLabel(String text) {
        return (Label) findNode(stage.getScene().getRoot(), n -> n instanceof Label l && text.equals(l.getText()));
    }

    boolean rulerTicksPresent() {
        Node n = stage.getScene().getRoot().lookup(".ruler-pane");
        return n instanceof Pane p && p.getChildren().size() > 40;
    }

    int selectionHandleCount() {
        return stage.getScene().getRoot().lookupAll(".sel-handle").size();
    }

    TemplateDesigner designerInstance() {
        return (TemplateDesigner) findNode(stage.getScene().getRoot(), n -> n instanceof TemplateDesigner);
    }

    /**
     * Deterministic magnet check: hide every element except two synthetic ones,
     * place B at x=150 and ask the snap engine where A (x=149.2) lands. The
     * result must align one of A's edges EXACTLY on a candidate line and move
     * it by at most the 2mm tolerance.
     */
    boolean magnetSnapWorks() {
        TemplateDesigner d = designerInstance();
        if (d == null) return false;
        ListView<TemplateElement> lv = layersListView();
        if (lv == null || lv.getItems().size() < 2) return false;
        TemplateElement e1 = lv.getItems().get(lv.getItems().size() - 1);
        TemplateElement e2 = lv.getItems().get(lv.getItems().size() - 2);

        List<TemplateElement> hidden = new ArrayList<>();
        try {
            for (TemplateElement e : lv.getItems()) {
                if (e != e1 && e != e2) { e.setHidden(true); hidden.add(e); }
            }
            e1.setX(149.2); e1.setW(50); e1.setH(10);
            e2.setX(150); e2.setW(24); e2.setH(24); e2.setY(Math.max(0, e2.getY()));

            double[] r = d.snapMove(e1, 149.2, e1.getY());
            double g = r[2];
            if (g < 0) return false;
            boolean aligned = Math.abs(r[0] - g) < 1e-9
                    || Math.abs(r[0] + e1.getW() / 2.0 - g) < 1e-9
                    || Math.abs(r[0] + e1.getW() - g) < 1e-9;
            boolean bounded = Math.abs(r[0] - 149.2) <= 2.0000001;
            return aligned && bounded;
        } finally {
            for (TemplateElement e : hidden) e.setHidden(false);
        }
    }


    boolean tablePropControlsPresent() {
        if (findComboBoxWithItem("Rows Only") == null) return false;
        for (String t : new String[]{"T", "B", "L", "R"}) {
            if (findCheckBox(t) == null) return false;
        }
        // headerBg + headerText + border + rowBg + rowText + zebra pickers
        // (v3: ColorPicker replaced everywhere by the themed ColorPickerButton)
        return countNodesOfType(stage.getScene().getRoot(), ColorPickerButton.class) >= 6;
    }

    @SuppressWarnings("unchecked")
    ComboBox<String> findComboBoxWithItem(String item) {
        return (ComboBox<String>) findNode(stage.getScene().getRoot(), n ->
                n instanceof ComboBox<?> cb && cb.getItems().stream()
                        .anyMatch(i -> item.equals(String.valueOf(i))));
    }

    CheckBox findCheckBox(String text) {
        return (CheckBox) findNode(stage.getScene().getRoot(), n ->
                n instanceof CheckBox c && text.equals(c.getText()));
    }

    static Node findNode(Node n, Predicate<Node> p) {
        if (p.test(n)) return n;
        if (n instanceof Parent parent) {
            for (Node ch : parent.getChildrenUnmodifiable()) {
                Node r = findNode(ch, p);
                if (r != null) return r;
            }
        }
        return null;
    }

    static int countNodesOfType(Node n, Class<? extends Node> type) {
        int c = type.isInstance(n) ? 1 : 0;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) c += countNodesOfType(ch, type);
        }
        return c;
    }

    /** Sets the Border Type combo (drives el.borderStyle + canvas refresh). */
    void setBorderType(String value) {
        ComboBox<String> cb = findComboBoxWithItem(value);
        if (cb == null) throw new IllegalStateException("border type combo not found");
        cb.setValue(value);
    }

    boolean borderTypeIs(String value) {
        ComboBox<String> cb = findComboBoxWithItem(value);
        return cb != null && value.equals(cb.getValue());
    }

    /** CheckBox.fire() toggles selection and runs the onAction handler. */
    void fireCheckBox(String text) {
        CheckBox cb = findCheckBox(text);
        if (cb == null) throw new IllegalStateException("checkbox not found: " + text);
        cb.fire();
    }

    /** Finds a GridPane spinner by its row label text, e.g. "Row Height (mm):". */
    @SuppressWarnings("unchecked")
    Spinner<Double> findSpinnerByLabel(String labelText) {
        Label lbl = (Label) findNode(stage.getScene().getRoot(), n ->
                n instanceof Label l && labelText.equals(l.getText()));
        if (lbl == null || !(lbl.getParent() instanceof GridPane gp)) return null;
        Integer row = GridPane.getRowIndex(lbl);
        for (Node ch : gp.getChildren()) {
            if (ch instanceof Spinner<?> s
                    && Integer.valueOf(1).equals(GridPane.getColumnIndex(ch))
                    && row != null && row.equals(GridPane.getRowIndex(ch))) {
                return (Spinner<Double>) s;
            }
        }
        return null;
    }

    void setRowHeightByLabel(String labelText, double mm) {
        Spinner<Double> sp = findSpinnerByLabel(labelText);
        if (sp == null) throw new IllegalStateException("spinner not found: " + labelText);
        sp.getValueFactory().setValue(mm);
    }

    /** Pref-heights of header/data HBoxes inside white table VBoxes on canvas. */
    List<Double> canvasTableRowHeights() {
        List<Double> out = new ArrayList<>();
        collectTableRows(stage.getScene().getRoot(), out);
        return out;
    }

    static void collectTableRows(Node n, List<Double> out) {
        if (n instanceof VBox vb && vb.getStyle() != null && vb.getStyle().contains("#ffffff")) {
            for (Node ch : vb.getChildrenUnmodifiable()) {
                if (ch instanceof HBox hb && hb.getPrefHeight() > 5) out.add(hb.getPrefHeight());
            }
        }
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) collectTableRows(ch, out);
        }
    }

    /** True if rows now render at {@code mm}, previously at the seed 6mm. */
    boolean rowHeightChanged(List<Double> before, double mm) {
        List<Double> now = canvasTableRowHeights();
        long nowCount = now.stream().filter(v -> Math.abs(v - mm * MM_PX) < 1.0).count();
        long beforeCount = before == null ? 0
                : before.stream().filter(v -> Math.abs(v - 6.0 * MM_PX) < 1.0).count();
        return nowCount >= 3 && beforeCount >= 3;
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
