package com.invoicestudio.ui;

import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.db.TemplateDao;
import com.invoicestudio.model.PresetTemplates;
import com.invoicestudio.model.Template;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;

/**
 * Registers every application keyboard action with {@link ShortcutManager}
 * and installs the bindings on the primary scene. Defaults keep the
 * historical combos; users rebind them in Settings → Shortcuts.
 */
class AppShortcuts {

    private final StudioApp app;

    AppShortcuts(StudioApp app) {
        this.app = app;
        ShortcutManager.load();
        registerAll();
    }

    private void registerAll() {
        // ---- Workspace (every screen) ----
        ShortcutManager.register("nav.dashboard", "Workspace", "Dashboard", "Ctrl+D", app::showDashboard, false);
        ShortcutManager.register("nav.dashboard2", "Workspace", "Financial Dashboard (2)", "Ctrl+Alt+D", app::showDashboard2, false);
        ShortcutManager.register("nav.newbill", "Workspace", "New Bill", "Ctrl+N", app::showCreateBill, false);
        ShortcutManager.register("nav.invoices", "Workspace", "Invoices (History)", "Ctrl+H", app::showHistory, false);
        ShortcutManager.register("nav.transactions", "Workspace", "Transactions", "Ctrl+T", app::showTransactions, false);
        ShortcutManager.register("nav.purchases", "Workspace", "Purchases", "Ctrl+P", app::showCreatePurchase, false);
        ShortcutManager.register("nav.expenses", "Workspace", "Expenses", "Ctrl+E", app::showExpenses, false);

        // ---- Insights ----
        ShortcutManager.register("nav.financials", "Insights", "Financial Statements", "Ctrl+F", app::showFinancials, false);
        ShortcutManager.register("nav.stock", "Insights", "Stock & Profit", "Ctrl+I", app::showStockAnalysis, false);
        ShortcutManager.register("nav.reports", "Insights", "Reports & Ledger", "Ctrl+R", app::showReports, false);

        // ---- Directory & Catalog ----
        ShortcutManager.register("nav.buyers", "Directory", "Buyers", "Ctrl+B", app::showBuyers, false);
        ShortcutManager.register("nav.sellers", "Directory", "Sellers / Suppliers", "Ctrl+U", app::showSuppliers, false);
        ShortcutManager.register("nav.items", "Directory", "Items", "Ctrl+M", app::showItems, false);
        ShortcutManager.register("nav.categories", "Directory", "Categories", "Ctrl+G", app::showCategories, false);
        ShortcutManager.register("nav.transports", "Directory", "Transports", "Ctrl+L", app::showTransports, false);
        ShortcutManager.register("nav.variables", "Directory", "Variables", "Ctrl+K", app::showVariables, false);
        ShortcutManager.register("nav.labelhistory", "Directory", "Label Print History", "Ctrl+Y", app::showLabelHistory, false);

        // ---- Design & Print ----
        ShortcutManager.register("nav.templates", "Design & Print", "Templates Gallery", "Ctrl+Q", app::showTemplates, false);
        ShortcutManager.register("nav.designer", "Design & Print", "Label Designer (Barcode Mode)", "Ctrl+Shift+L",
                this::openLabelDesignerShortcut, false);
        ShortcutManager.register("nav.bulkprint", "Design & Print", "Bulk Label Print", "Ctrl+Shift+B",
                this::openBulkPrintShortcut, false);
        ShortcutManager.register("nav.settings", "Design & Print", "Settings", "Ctrl+Comma", app::showSettings, false);

        // ---- Data ----
        ShortcutManager.register("data.reload", "Data", "Reload all data", "F5", app::reloadAllData, false);
        ShortcutManager.register("help.shortcuts", "Data", "Toggle shortcuts help", "F1", app::toggleShortcutsHelp, true);
    }

    void installGlobalShortcuts(Scene scene) {
        ShortcutManager.installAll(scene);
    }

    // ------------------------------------------------------------------
    // Designer quick jumps
    // ------------------------------------------------------------------

    /** The designer currently on screen, or null when another view is active. */
    private com.invoicestudio.ui.views.TemplateDesigner activeDesigner() {
        if (!app.sidebar().isDesignerActive()) return null;
        for (Node n : app.mainContentPane().getChildren()) {
            if (n instanceof com.invoicestudio.ui.views.TemplateDesigner td) return td;
        }
        return null;
    }

    /** Most recently touched label template, or null when none exists yet. */
    private Template findLatestLabelTemplate() {
        try {
            TemplateDao dao = new TemplateDao(DatabaseManager.getInstance());
            Template best = null;
            for (Template t : dao.getAllTemplates()) {
                if (t != null && t.isLabelMode()) {
                    if (best == null || String.valueOf(t.getUpdatedAt()).compareTo(String.valueOf(best.getUpdatedAt())) >= 0) {
                        best = t;
                    }
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    /** Jump straight into the Template Designer in Barcode Mode. */
    private void openLabelDesignerShortcut() {
        if (!app.hasData()) return;
        com.invoicestudio.ui.views.TemplateDesigner designer = activeDesigner();
        if (designer != null) {
            designer.enterBarcodeModeFromShortcut();
            return;
        }
        Template lbl = findLatestLabelTemplate();
        if (lbl == null) {
            // First run: persist the starter label template, then open it.
            lbl = PresetTemplates.buildLabelTemplate();
            try {
                new TemplateDao(DatabaseManager.getInstance()).saveTemplate(lbl);
            } catch (Exception ignored) {}
        }
        app.showTemplateDesigner(lbl);
    }

    /** Open the Bulk Label Print window (designing first if needed). */
    private void openBulkPrintShortcut() {
        if (!app.hasData()) return;
        com.invoicestudio.ui.views.TemplateDesigner designer = activeDesigner();
        if (designer != null) {
            designer.openBulkPrintFromShortcut();
            return;
        }
        Template lbl = findLatestLabelTemplate();
        if (lbl == null) {
            lbl = PresetTemplates.buildLabelTemplate();
            try {
                new TemplateDao(DatabaseManager.getInstance()).saveTemplate(lbl);
            } catch (Exception ignored) {}
        }
        app.showTemplateDesigner(lbl);
        designer = activeDesigner();
        if (designer != null) designer.openBulkPrintFromShortcut();
    }
}
