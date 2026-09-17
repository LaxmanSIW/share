package com.invoicestudio.mcp;

import com.invoicestudio.service.AppLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.BillPayment;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.BillTotals;
import com.invoicestudio.model.Buyer;
import com.invoicestudio.model.Expense;
import com.invoicestudio.model.ItemCategory;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.model.PaymentMethod;
import com.invoicestudio.model.PurchaseBill;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Supplier;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.Transaction;
import com.invoicestudio.model.Transport;
import com.invoicestudio.model.VariableDef;
import com.invoicestudio.service.BillingService;
import com.invoicestudio.service.BulkPrintStateStore;
import com.invoicestudio.service.ExpenseAccountService;
import com.invoicestudio.service.ExpenseAnalytics;
import com.invoicestudio.service.FinancialService;
import com.invoicestudio.service.LabelGeometryService;
import com.invoicestudio.service.LabelPrintService;
import com.invoicestudio.model.TemplateElement;
import com.invoicestudio.service.PurchaseService;
import com.invoicestudio.ui.DataManager;
import com.invoicestudio.ui.ShortcutManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The MCP tool surface: every capability of the InvoiceStudio UI exposed as
 * a typed tool with a JSON schema.
 *
 * <p>Safety model (mirrors MCP_SERVER.md):</p>
 * <ul>
 *   <li>Read tools execute immediately.</li>
 *   <li>Create tools execute immediately (they only add records).</li>
 *   <li>Update/delete tools never run directly — they queue a
 *       {@link PendingOperations.PendingOp} and return
 *       {@code requiresConfirmation}; execution happens only after the user
 *       approves in Settings → MCP Server (or via {@code confirm_operation}).</li>
 * </ul>
 */
public final class McpToolRegistry {

    /** JSON schema of one tool (sent in tools/list). */
    public static final class ToolDef {
        public final String name;
        public final String description;
        public final Map<String, Object> inputSchema;
        public final boolean mutates;
        public final boolean destructive;

        ToolDef(String name, String description, Map<String, Object> inputSchema,
                boolean mutates, boolean destructive) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.mutates = mutates;
            this.destructive = destructive;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final FinancialService FIN = new FinancialService();

    private McpToolRegistry() {}

    // ------------------------------------------------------------------
    // Tool catalogue
    // ------------------------------------------------------------------

    private static final List<ToolDef> TOOLS = buildTools();

    public static List<ToolDef> tools() {
        return TOOLS;
    }

    private static List<ToolDef> buildTools() {
        List<ToolDef> t = new ArrayList<>();

        // --- Discovery / knowledge ---
        t.add(new ToolDef("get_app_guide",
                "Full InvoiceStudio manual: what the app is, every feature, data model, and step-by-step usage (billing, purchases, stock, expenses, financials, templates). Read this first.",
                obj(), false, false));
        t.add(new ToolDef("get_mcp_docs",
                "MCP server documentation: protocol, full tool list and the confirmation safety model.",
                obj(), false, false));
        t.add(new ToolDef("get_settings",
                "Business profile & billing preferences (name, GSTIN, state code, currency, invoice numbering, inter-state mode). Read-only.",
                obj(), false, false));

        // --- Buyers ---
        t.add(new ToolDef("list_buyers", "List all buyers/customers (Sundry Debtors).",
                obj(
                        "query", str("Optional search text matched against name, phone, GSTIN, city"),
                        "limit", num("Max rows (default 100)")), false, false));
        t.add(new ToolDef("create_buyer", "Create a buyer/customer. Check-then-create: the default transport (transportId and/or transportName) is resolved first and AUTO-CREATED if missing — listed in response.autoCreated. Idempotent by name: if a buyer with the same name already exists it is returned unchanged (existed=true) instead of duplicated; use update_buyer to change it.",
                obj(
                        "name", str("Buyer/firm name (required)"),
                        "phone", str("Phone"),
                        "gst", str("15-char GSTIN"),
                        "address", str("Address"),
                        "state", str("State name"),
                        "stateCode", str("2-digit state code (decides IGST vs CGST/SGST)"),
                        "city", str("City"),
                        "contactPerson", str("Contact person name"),
                        "openingBalance", num("Opening receivable balance"),
                        "creditLimit", num("Credit limit (default 100000)"),
                        "transportId", str("Default transport id"),
                        "transportName", str("Default transport name (auto-created if missing)")), true, false));
        t.add(new ToolDef("update_buyer", "Update an existing buyer: name, phone, gst, address, state, stateCode, city, contactPerson, openingBalance, creditLimit — and assign its DEFAULT TRANSPORT via transportId and/or transportName (resolved first, auto-created if missing; the assignment is spelled out in the confirmation summary). Requires user confirmation.",
                obj("id", str("Buyer id"),
                        "name", str("New name"),
                        "phone", str("New phone"),
                        "gst", str("New GSTIN"),
                        "address", str("New address"),
                        "state", str("New state"),
                        "stateCode", str("New state code"),
                        "city", str("New city"),
                        "contactPerson", str("New contact person"),
                        "openingBalance", num("New opening balance"),
                        "creditLimit", num("New credit limit"),
                        "transportId", str("Default transport id to assign"),
                        "transportName", str("Default transport name to assign (auto-created if missing)")), true, true));
        t.add(new ToolDef("delete_buyer", "Delete a buyer permanently. Requires user confirmation.",
                obj("id", str("Buyer id")), true, true));

        // --- Suppliers ---
        t.add(new ToolDef("list_suppliers", "List all sellers/suppliers (Sundry Creditors) with balances.",
                obj(
                        "query", str("Optional search text"),
                        "limit", num("Max rows (default 100)")), false, false));
        t.add(new ToolDef("create_supplier", "Create a seller/supplier. Idempotent by name: returns the existing supplier unchanged (existed=true) instead of duplicating.",
                obj(
                        "name", str("Firm name (required)"),
                        "phone", str("Phone"),
                        "gst", str("GSTIN"),
                        "state", str("State"),
                        "stateCode", str("State code"),
                        "city", str("City"),
                        "address", str("Full address"),
                        "openingBalance", num("Opening payable (positive = you owe them)"),
                        "creditPeriodDays", num("Credit period in days")), true, false));
        t.add(new ToolDef("update_supplier", "Update a supplier: name, phone, gst, state, stateCode, city, address, openingBalance, creditPeriodDays. Requires user confirmation.",
                obj("id", str("Supplier id"),
                        "name", str("New name"),
                        "phone", str("New phone"),
                        "gst", str("New GSTIN"),
                        "state", str("New state"),
                        "stateCode", str("New state code"),
                        "city", str("New city"),
                        "address", str("New address"),
                        "openingBalance", num("New opening payable (positive = you owe them)"),
                        "creditPeriodDays", num("New credit period in days")), true, true));
        t.add(new ToolDef("delete_supplier", "Delete a supplier permanently. Requires user confirmation.",
                obj("id", str("Supplier id")), true, true));

        // --- Items ---
        t.add(new ToolDef("list_items", "List catalog items with rates, GST %, cost, live stock and reorder level.",
                obj(
                        "query", str("Optional search text"),
                        "limit", num("Max rows (default 100)")), false, false));
        t.add(new ToolDef("create_item", "Create a catalog item. Check-then-create: the category (categoryId and/or categoryName) is resolved first and AUTO-CREATED if missing — every auto-creation is listed in response.autoCreated, never silently linked. Idempotent by item name: an existing item is returned (existed=true); use update_item to change it.",
                obj(
                        "name", str("Item name (required)"),
                        "hsn", str("HSN code"),
                        "unit", str("Unit (PCS, KG, BOX...)"),
                        "rate", num("Selling rate"),
                        "gst", num("GST % (0/5/12/18/28)"),
                        "purchaseRate", num("Cost/purchase rate"),
                        "openingStock", num("Opening stock qty"),
                        "reorderLevel", num("Low-stock alert level"),
                        "categoryId", str("Category id"),
                        "categoryName", str("Category name")), true, false));
        t.add(new ToolDef("update_item", "Update a catalog item: name, hsn, unit, rate, gst, purchaseRate, openingStock, reorderLevel — and MOVE it to another category via categoryId and/or categoryName (THE only way to reassign: create_item never modifies an existing item; the target category is resolved first and auto-created if missing, and the move is spelled out in the confirmation summary). Requires user confirmation.",
                obj("id", str("Item id"),
                        "name", str("New name"),
                        "hsn", str("New HSN code"),
                        "unit", str("New unit (PCS, KG, BOX...)"),
                        "rate", num("New selling rate"),
                        "gst", num("New GST %"),
                        "purchaseRate", num("New cost rate"),
                        "openingStock", num("New opening stock qty"),
                        "reorderLevel", num("New reorder level"),
                        "categoryId", str("Category id to move the item into"),
                        "categoryName", str("Category name to move the item into (auto-created if missing)")), true, true));
        t.add(new ToolDef("delete_item", "Delete a catalog item permanently. Requires user confirmation.",
                obj("id", str("Item id")), true, true));

        // --- Read-only masters ---
        t.add(new ToolDef("list_categories", "List item categories with the live itemCount of catalog items assigned to each (0 = safe to delete).", obj(), false, false));
        t.add(new ToolDef("create_category", "Create an item category standalone (no item needed). Check-then-create: idempotent by name (case-insensitive) — an existing category is returned (existed=true), never duplicated; the unique DB index backstops races.",
                obj("name", str("Category name (required)")), true, false));
        t.add(new ToolDef("update_category", "Rename a category. The new name is CASCADED to every catalog item that references it (items store the category name denormalized, so the rename keeps them in sync — response reports how many items were updated). Rejects a name that collides with another existing category. Requires user confirmation.",
                obj("id", str("Category id"),
                        "name", str("New category name")), true, true));
        t.add(new ToolDef("delete_category", "Delete an EMPTY category. Refuses while items are still assigned (the error tells you how many and points at update_item with categoryId/categoryName to move them first). The default category is protected. Requires user confirmation.",
                obj("id", str("Category id")), true, true));
        t.add(new ToolDef("list_transports", "List transports (logistics partners).", obj(), false, false));
        t.add(new ToolDef("list_templates", "List print templates with element counts and page sizes.", obj(), false, false));
        t.add(new ToolDef("get_template_design_guide",
                "TEMPLATE DESIGN REFERENCE — the complete design vocabulary of print templates: all 23 element types, every styling property (positioning mm, typography, borders, gradients, shadows, images, QR/barcode, table columns), every variable binding, page sizes, proven A4/thermal layout recipes and the recommended design workflow (duplicate → update → render_template_preview → fix warnings). Read this before designing or editing any template.",
                obj(), false, false));
        t.add(new ToolDef("render_template_preview",
                "Render a print template to a PNG image EXACTLY as it will print (same engine as PDF export, realistic sample data) so you can visually verify a design and iterate BEFORE saving. Pass id to render a saved template, OR draft {name, pageSize, elements} to render an unsaved element list (nothing is written to the database). Returns a native image content block plus meta: page size, pixel size, elementCount and warnings (out-of-bounds elements, unresolved bindings). Pair with get_template_design_guide for the property vocabulary.",
                obj("id", str("Saved template id (use this OR draft)"),
                        "draft", obj("name", str("Draft name"),
                                "pageSize", str("A4 | A5 | LETTER | LEGAL | THERMAL_80 | THERMAL_58 | CUSTOM (default A4)"),
                                "elements", arr("Element list — same shape as create_template")),
                        "dpi", num("Render resolution 72-300 (default 150)")), false, false));
        t.add(new ToolDef("get_template", "Get one print template in full: page config and EVERY element with EVERY property (type, x/y/w/h mm, typography, colors, borders, image src, QR/barcode config, table columns, bindings). This is lossless — what you read here you can round-trip into update_template. Read get_template_design_guide for the property vocabulary.",
                obj("id", str("Template id")), false, false));
        t.add(new ToolDef("create_template", "Create a print template. Elements accept the FULL design vocabulary per element: {type: TEXT|TABLE|IMAGE|QRCODE|BARCODE|RECT|CIRCLE|ELLIPSE|LINE|PAGENO|POLYLINE|POLYGON|ARC|PATH|STAR|ARROW|DIVIDER|FREEHAND|WATERMARK|SVG|ICON|GROUP|COMPONENT, name, x, y, w, h (mm), text?, binding? {{var}}, fontFamily?, fontSize?, fontWeight?, color?, bg?, align?, borderWidth?, borderRadius?, columns? (TABLE), src?/useBusinessLogo? (IMAGE), qrSource? (QRCODE), barcodeData? ...} — see get_template_design_guide for every property. Recommended workflow: duplicate_template a preset, or create then iterate with render_template_preview (draft mode) before finalizing. Idempotent by name: returns the existing template (existed=true) instead of duplicating.",
                obj("name", str("Template name"),
                        "pageSize", str("A4 | A5 | LETTER | LEGAL | THERMAL_80 | THERMAL_58 | CUSTOM (default A4)"),
                        "elements", arr("Element list (can be empty and edited later)")), true, false));
        t.add(new ToolDef("update_template", "Update a template's name, page size or replace its elements. Elements accept the FULL design vocabulary (see get_template_design_guide); the element list you pass fully replaces the previous one — get_template first and edit what you read. Requires user confirmation.",
                obj("id", str("Template id"),
                        "name", str("New name"),
                        "pageSize", str("New page size"),
                        "elements", arr("Replacement element list")), true, true));
        t.add(new ToolDef("duplicate_template", "Duplicate an existing template under a new name (great starting point: duplicate a preset then edit). Idempotent: if newName is already taken the existing template is returned (existed=true).",
                obj("id", str("Template to copy"),
                        "newName", str("Name for the copy")), true, false));
        t.add(new ToolDef("delete_template", "Delete a print template permanently. Requires user confirmation.",
                obj("id", str("Template id")), true, true));
        t.add(new ToolDef("list_variables", "List template variables (fixed app variables + custom) usable as element bindings.", obj(), false, false));
        t.add(new ToolDef("create_variable", "Create a custom template variable. Idempotent by key: an existing key is returned unchanged (existed=true) — this tool never overwrites; use confirm-gated updates instead.",
                obj("key", str("Snake_case key used in bindings"),
                        "label", str("Human label"),
                        "type", str("text | number | date"),
                        "defaultValue", str("Default value"),
                        "scope", str("fixed | table")), true, false));
        t.add(new ToolDef("delete_variable", "Delete a custom variable. Requires user confirmation.",
                obj("key", str("Variable key")), true, true));
        t.add(new ToolDef("create_transport", "Add a transport (logistics) partner. Idempotent by name: returns the existing transport (existed=true) instead of duplicating.",
                obj("name", str("Transport name"),
                        "phone", str("Phone"),
                        "vehicleNumber", str("Vehicle number")), true, false));
        t.add(new ToolDef("update_transport", "Update an existing transport (logistics) partner: name, phone, vehicleNumber. Requires user confirmation.",
                obj("id", str("Transport id"),
                        "name", str("New name (optional)"),
                        "phone", str("New phone (optional)"),
                        "vehicleNumber", str("New vehicle number (optional)")), true, true));
        t.add(new ToolDef("delete_transport", "Delete a transport (logistics) partner. Refuses while buyers still reference it as their default (the error tells you how many). Pass force:true to clear the default-transport assignment on those buyers automatically and proceed. Requires user confirmation.",
                obj("id", str("Transport id"),
                        "force", str("Optional. true = clear the default transport on referencing buyers and delete anyway")), true, true));
        t.add(new ToolDef("create_backup", "Export a full data backup (all users' records in scope of the DB) to a JSON file. Default location: app data dir /backups.",
                obj("path", str("Optional destination file path")), true, false));
        t.add(new ToolDef("whoami", "Which user's books this server is operating on (Firebase account email, display name, session expiry). Answers 'how does authentication work here'. Never returns secrets.",
                obj(), false, false));

        // --- Sales ---
        t.add(new ToolDef("list_bills", "List sales invoices with buyer, total, status and paid amounts.",
                obj(
                        "query", str("Optional search text matched against bill no / buyer name"),
                        "status", str("Filter: UNPAID | PAID | CANCELLED"),
                        "limit", num("Max rows (default 50)")), false, false));
        t.add(new ToolDef("get_bill", "Get one invoice with full item lines, totals and payments.",
                obj("id", str("Bill id")), false, false));
        t.add(new ToolDef("create_bill", "Create a tax invoice (same as + New Bill). Check-then-create: buyerId/buyerName is resolved against the directory and AUTO-CREATED if unknown (blank = Walk-in Customer); line itemIds are resolved against the catalog and AUTO-CREATED from the line data if missing. Every auto-creation is listed in response.autoCreated. If the invoice itself fails to save, auto-created dependencies are rolled back so nothing is half-done.",
                obj(
                        "buyerName", str("Buyer name (matched to directory; walk-in if blank)"),
                        "buyerId", str("Buyer id (alternative to buyerName)"),
                        "date", str("ISO date (default today)"),
                        "items", arr("Lines: [{itemId?, desc, qty, rate?, gst?, discPct?}] — itemId auto-fills rate/GST"),
                        "discountPct", num("Bill-level discount %"),
                        "paid", bool("true = mark paid immediately"),
                        "paymentMode", str("Cash | UPI | Bank Transfer | Cheque | Card")),
                true, false));
        t.add(new ToolDef("update_bill_status", "Change invoice status (PAID / UNPAID / CANCELLED). Requires user confirmation.",
                obj("id", str("Bill id"),
                        "status", str("UNPAID | PAID | CANCELLED")), true, true));
        t.add(new ToolDef("delete_bill", "Delete an invoice; stock ledger rows are reversed. Requires user confirmation.",
                obj("id", str("Bill id")), true, true));

        // --- Purchases & payments ---
        t.add(new ToolDef("list_purchases", "List purchase bills with supplier, totals, ITC and paid amounts.",
                obj(
                        "query", str("Optional search text (bill no / supplier name)"),
                        "limit", num("Max rows (default 50)")), false, false));
        t.add(new ToolDef("create_purchase", "Record a purchase bill from a supplier (stock IN + ITC). Check-then-create: supplierId/supplierName is resolved and the supplier is AUTO-CREATED if unknown (never a hard error); line itemIds are resolved and AUTO-CREATED from line data if missing. Every auto-creation is listed in response.autoCreated; dependencies roll back if the purchase itself fails.",
                obj(
                        "supplierId", str("Supplier id (or supplierName)"),
                        "supplierName", str("Supplier name"),
                        "supplierBillNo", str("Supplier's own bill number"),
                        "date", str("ISO date (default today)"),
                        "items", arr("Lines: [{itemId?, desc, qty, rate (cost), gst?, discPct?}]"),
                        "discountPct", num("Bill-level discount %"),
                        "freight", num("Freight/other charges added to payable"),
                        "paid", bool("true = mark paid immediately"),
                        "paymentMode", str("Cash | Bank Transfer | Cheque | UPI")),
                true, false));
        t.add(new ToolDef("pay_purchase", "Record a supplier payment against a purchase bill (partial payments allowed).",
                obj("id", str("Purchase bill id"),
                        "amount", num("Payment amount (default = remaining balance)"),
                        "mode", str("Cash | Bank Transfer | Cheque | UPI"),
                        "reference", str("Cheque no / UTR")), true, false));
        t.add(new ToolDef("delete_purchase", "Delete a purchase bill; stock IN rows are reversed. Requires user confirmation.",
                obj("id", str("Purchase bill id")), true, true));

        // --- Expenses & money ---
        t.add(new ToolDef("list_expenses", "List expense vouchers.",
                obj("limit", num("Max rows (default 100)")), false, false));
        t.add(new ToolDef("record_expense", "Record an expense voucher (direct heads hit the Trading Account, indirect hit P&L). category is a free-text accounting head (e.g. Freight Inward, Office Rent) — deliberately NOT a directory reference, so nothing is auto-created and identical vouchers are allowed.",
                obj(
                        "category", str("e.g. Freight Inward (direct), Office Rent, Salaries, Electricity, Marketing, Bank Charges (indirect)"),
                        "amount", num("Amount"),
                        "date", str("ISO date (default today)"),
                        "description", str("Narration"),
                        "paymentMode", str("Cash | Bank | NEFT | Cheque | UPI"),
                        "payee", str("Paid to")), true, false));
        t.add(new ToolDef("delete_expense", "Delete an expense voucher. Requires user confirmation.",
                obj("id", str("Expense id")), true, true));

        // --- Expense accounts (payee registry) ---
        t.add(new ToolDef("list_expense_accounts", "List expense accounts (payee registry) with per-account usage rollups. Read-only.",
                obj("includeArchived", bool("Include archived accounts (default false)")), false, false));
        t.add(new ToolDef("create_expense_account", "Create an expense account (payee). Silently no-ops (returns existing) when the name already exists — safe to call repeatedly.",
                obj("name", str("Account name (unique, case-insensitive)"),
                        "notes", str("Optional notes"),
                        "defaultPaymentMode", str("Optional default payment mode")), true, false));
        t.add(new ToolDef("rename_expense_account", "Rename an expense account and propagate the new name to every expense voucher carrying the old one. Requires user confirmation.",
                obj("id", str("Account id (from list_expense_accounts)"),
                        "newName", str("New account name")), true, true));
        t.add(new ToolDef("expense_account_report", "Expense analytics for an account or category (or the whole register): totals, monthly trend, per-category and per-account breakdowns. Read-only.",
                obj("account", str("Account (payee) name — omit for all accounts"),
                        "category", str("Category head — omit for all categories"),
                        "from", str("ISO date (inclusive)"),
                        "to", str("ISO date (inclusive)")), false, false));
        t.add(new ToolDef("list_transactions", "List CC-book ledger transactions (sales & payments synced from invoices).",
                obj("limit", num("Max rows (default 100)")), false, false));

        // --- Reports ---
        t.add(new ToolDef("stock_report", "Stock summary per item (opening → in → out → closing qty & value) plus low-stock list. Read-only.",
                obj(), false, false));
        t.add(new ToolDef("profitability_report", "Item-wise profitability: qty sold, sales value, cost, gross profit, GP%. Read-only.",
                obj(), false, false));
        t.add(new ToolDef("financial_summary", "Trading Account, P&L, Balance Sheet and GST summary for a date range (defaults: current financial year). Read-only.",
                obj(
                        "from", str("ISO date (default Apr 1)"),
                        "to", str("ISO date (default today)")), false, false));
        t.add(new ToolDef("daybook", "Unified chronological journal: sales, receipts, purchases, payments, expenses. Read-only.",
                obj(), false, false));

        // --- System ---
        // --- Batch invoice export (History → Export PDFs) ---
        t.add(new ToolDef("export_bills_pdf",
                "Export one or more invoices to PDF files in a folder — the same engine as History → Export PDFs. Select bills with from/to dates, a query (bill no / buyer) and/or status, or an explicit ids list; exportBatch=true exports every invoice. Uses the first bill-mode template. Returns exported/failed counts, the output folder and per-bill failures.",
                obj(
                        "ids", arr("Explicit bill ids to export (optional; overrides from/to/query)"),
                        "from", str("Date from, YYYY-MM-DD (optional)"),
                        "to", str("Date to, YYYY-MM-DD (optional)"),
                        "query", str("Search text matched against bill no and buyer name (optional)"),
                        "status", str("PAID / UNPAID / CANCELLED (optional)"),
                        "limit", num("Max bills when selecting by filters (default 500)"),
                        "exportBatch", bool("true = ignore filters and export every invoice"),
                        "dir", str("Target folder (default: app data dir /exports/pdf-YYYY-MM-DD)")),
                false, false));

        // --- Keyboard shortcuts ---
        t.add(new ToolDef("list_shortcuts",
                "Every rebindable keyboard shortcut with its group, label, current effective binding and default binding. Read-only.",
                obj(), false, false));
        t.add(new ToolDef("rebind_shortcut",
                "Rebind a keyboard shortcut to a new combination with the app's full validation (collisions, OS-reserved combos, plain-letter rejection, canonical normalization). combo blank = unbind. Requires user confirmation.",
                obj(
                        "actionId", str("Shortcut action id from list_shortcuts (required)"),
                        "combo", str("New combination like Ctrl+Shift+H (blank = unbind)")),
                true, true));
        t.add(new ToolDef("reset_shortcut",
                "Restore the default binding for one shortcut action (resetAll=true restores every default). Requires user confirmation.",
                obj(
                        "actionId", str("Action id to reset (ignored when resetAll is true)"),
                        "resetAll", bool("true = reset every shortcut to its default")),
                true, true));

        // --- Label (barcode) bulk print ---
        t.add(new ToolDef("get_label_print_state",
                "Read the Bulk Label Print session remembered for a template: each row's variable values and copies, plus the last printer used. Use it to re-print the same batch without rebuilding it.",
                obj("templateId", str("Label-mode template id (required)")), false, false));
        t.add(new ToolDef("print_labels",
                "Print a barcode/label queue through the real label engine — the same path as the Bulk Label Print window (TSC/TSPL printers get a native RAW spool). lines = one entry per distinct label: {variableValues: {variableKey: value}, copies: n}. test=true prints a single free test label. Refuses templates not in Barcode Mode.",
                obj(
                        "templateId", str("Label-mode template id (required)"),
                        "lines", arr("Rows: [{variableValues:{key:value,...}, copies:int}, ...]"),
                        "variableOrder", arr("Ordered variable keys (optional; inferred from the template's element bindings when omitted)"),
                        "printer", str("Printer name (default: system default)"),
                        "test", bool("true = one test label, no charge")),
                false, false));

        // --- Expense account lifecycle (parity with the Accounts dialog) ---
        t.add(new ToolDef("update_expense_account",
                "Edit an expense account: rename (propagates to every voucher carrying the old name), set notes, set defaultPaymentMode, or archive/unarchive. Requires user confirmation.",
                obj(
                        "id", str("Expense account id"),
                        "name", str("New name (propagates to all vouchers)"),
                        "notes", str("Notes"),
                        "defaultPaymentMode", str("Default payment mode"),
                        "archived", bool("true = archive (hidden from pickers), false = unarchive")),
                true, true));
        t.add(new ToolDef("delete_expense_account",
                "Permanently delete an expense account — only when no voucher still references its name (use rename/archive for accounts with history). Requires user confirmation.",
                obj("id", str("Expense account id")), true, true));

        t.add(new ToolDef("server_status", "MCP server status, app version, user, pending-operation count.",
                obj(), false, false));
        t.add(new ToolDef("audit_log", "Recent MCP activity (tool calls, confirmations).",
                obj("limit", num("Max entries (default 50)")), false, false));
        t.add(new ToolDef("confirm_operation",
                "Approve or reject a pending destructive operation. ONLY call this after the human user has explicitly confirmed in chat.",
                obj(
                        "operationId", str("operationId returned by the update/delete tool"),
                        "approve", bool("true = execute, false = reject")), true, true));

        return t;
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    /** Executes a tool call and returns the JSON/text result payload. */
    public static Object call(String name, Map<String, Object> args) throws Exception {
        DataManager dm = DataManager.get();
        switch (name == null ? "" : name) {
            // Discovery
            case "get_app_guide": return GuideContent.appGuide();
            case "get_mcp_docs": return GuideContent.mcpDocs();
            case "get_settings": return settingsMap(dm.getSettings());

            // Buyers
            case "list_buyers": return buyersMap(dm, str(args, "query"), intVal(args, "limit", 100));
            case "create_buyer": return createBuyer(dm, args);
            case "update_buyer": return confirmable("update_buyer", args, () -> {
                Buyer b = requireBuyer(dm, str(args, "id"));
                if (args.containsKey("name") && !str(args, "name").isBlank()) b.setName(str(args, "name").trim());
                if (args.containsKey("phone")) b.setPhone(str(args, "phone"));
                if (args.containsKey("gst")) b.setGst(str(args, "gst").trim().toUpperCase(Locale.ROOT));
                if (args.containsKey("address")) b.setAddress(str(args, "address"));
                if (args.containsKey("state")) b.setState(str(args, "state"));
                if (args.containsKey("stateCode")) b.setStateCode(str(args, "stateCode"));
                if (args.containsKey("city")) b.setCity(str(args, "city"));
                if (args.containsKey("contactPerson")) b.setContactPerson(str(args, "contactPerson"));
                if (args.containsKey("openingBalance")) b.setOpeningBalance(dbl(args, "openingBalance", b.getOpeningBalance()));
                if (args.containsKey("creditLimit")) b.setCreditLimit(dbl(args, "creditLimit", b.getCreditLimit()));
                // Default transport assignment — check-then-create, no dangling ids.
                McpEnsure.Outcome tr = McpEnsure.ensureTransport(dm,
                        strOr(args, "transportId", ""), strOr(args, "transportName", ""));
                if (tr != null) b.setDefaultTransportId(tr.id);
                dm.buyers().saveBuyer(b);
            });
            case "delete_buyer": return confirmable("delete_buyer", args, () -> dm.buyers().deleteBuyer(str(args, "id")));

            // Suppliers
            case "list_suppliers": return suppliersMap(dm, str(args, "query"), intVal(args, "limit", 100));
            case "create_supplier": return createSupplier(dm, args);
            case "update_supplier": return confirmable("update_supplier", args, () -> {
                Supplier s = requireSupplier(dm, str(args, "id"));
                if (args.containsKey("name") && !str(args, "name").isBlank()) s.setName(str(args, "name").trim());
                if (args.containsKey("phone")) s.setPhone(str(args, "phone"));
                if (args.containsKey("gst")) s.setGst(str(args, "gst").trim().toUpperCase(Locale.ROOT));
                if (args.containsKey("state")) s.setState(str(args, "state"));
                if (args.containsKey("stateCode")) s.setStateCode(str(args, "stateCode"));
                if (args.containsKey("city")) s.setCity(str(args, "city"));
                if (args.containsKey("address")) s.setAddress(str(args, "address"));
                if (args.containsKey("openingBalance")) s.setOpeningBalance(dbl(args, "openingBalance", s.getOpeningBalance()));
                if (args.containsKey("creditPeriodDays")) s.setCreditPeriodDays((int) dbl(args, "creditPeriodDays", s.getCreditPeriodDays()));
                dm.suppliers().saveSupplier(s);
            });
            case "delete_supplier": return confirmable("delete_supplier", args, () -> dm.suppliers().deleteSupplier(str(args, "id")));

            // Items
            case "list_items": return itemsMap(dm, str(args, "query"), intVal(args, "limit", 100));
            case "create_item": return createItem(dm, args);
            case "update_item": return confirmable("update_item", args, () -> {
                ItemRecord it = requireItem(dm, str(args, "id"));
                if (args.containsKey("name") && !str(args, "name").isBlank()) it.setName(str(args, "name").trim());
                if (args.containsKey("hsn")) it.setHsn(strOr(args, "hsn", "").trim());
                if (args.containsKey("unit") && !str(args, "unit").isBlank()) it.setUnit(str(args, "unit").trim());
                if (args.containsKey("rate")) it.setRate(dbl(args, "rate", it.getRate()));
                if (args.containsKey("gst")) it.setGst(dbl(args, "gst", it.getGst()));
                if (args.containsKey("purchaseRate")) it.setPurchaseRate(dbl(args, "purchaseRate", it.getPurchaseRate()));
                if (args.containsKey("openingStock")) it.setOpeningStock(dbl(args, "openingStock", it.getOpeningStock()));
                if (args.containsKey("reorderLevel")) it.setReorderLevel(dbl(args, "reorderLevel", it.getReorderLevel()));
                // Category reassignment (the proper path — create_item never updates):
                // check-then-create so the item can never land on a dangling category id.
                McpEnsure.Outcome cat = McpEnsure.ensureCategory(dm,
                        strOr(args, "categoryId", ""), strOr(args, "categoryName", ""));
                if (cat != null) {
                    it.setCategoryId(cat.id);
                    it.setCategoryName(cat.name);
                }
                dm.items().saveItem(it);
                dm.stockLedger().recomputeItem(it.getId());
            });
            case "delete_item": return confirmable("delete_item", args, () -> dm.items().deleteItem(str(args, "id")));

            // Read-only masters
            case "list_categories": return dm.getAllCategories().stream().map(c -> mapOf(
                    "id", c.getId(), "name", c.getName(),
                    "itemCount", dm.items().countItemsInCategory(c.getId()))).collect(java.util.stream.Collectors.toList());
            case "create_category": return createCategory(dm, args);
            case "update_category": return confirmable("update_category", args, () -> {
                ItemCategory cat = requireCategory(dm, str(args, "id"));
                String newName = str(args, "name");
                if (newName == null || newName.isBlank()) throw new IllegalArgumentException("name is required");
                String trimmed = newName.trim();
                // case-insensitive duplicate guard (unique index backstops races)
                ItemCategory clash = McpEnsure.findCategory(dm, null, trimmed);
                if (clash != null && !clash.getId().equalsIgnoreCase(cat.getId()))
                    throw new IllegalArgumentException("Another category named '" + clash.getName()
                            + "' already exists (id " + clash.getId() + "); rename refused.");
                cat.setName(trimmed);
                dm.saveCategory(cat);
                // items carry category_name denormalized — cascade the rename
                dm.items().updateCategoryNameForCategory(cat.getId(), trimmed);
            });
            case "delete_category": return confirmable("delete_category", args, () -> {
                String id = str(args, "id");
                if ("cat_trouser".equalsIgnoreCase(id) || "cat_trousers".equalsIgnoreCase(id))
                    throw new IllegalArgumentException("The default category '" + id + "' is protected and cannot be deleted.");
                ItemCategory cat = requireCategory(dm, id);
                int assigned = dm.items().countItemsInCategory(id);
                if (assigned > 0)
                    throw new IllegalArgumentException("Category '" + cat.getName() + "' still has " + assigned
                            + " item(s) assigned. Move them first with update_item { id, categoryId/categoryName }, then delete the empty category.");
                dm.deleteCategory(id);
            });
            case "list_transports": return dm.getAllTransports().stream().map(tr -> mapOf(
                    "id", tr.getId(), "name", tr.getName(), "phone", tr.getPhone(),
                    "vehicleNumber", tr.getVehicleNumber())).collect(java.util.stream.Collectors.toList());
            case "list_templates": return templatesMap(dm);
            case "get_template_design_guide": return GuideContent.templateDesignGuide();
            case "render_template_preview": return renderTemplatePreview(dm, args);
            case "get_template": return templateFull(requireTemplate(dm, str(args, "id")));
            case "create_template": return createTemplate(dm, args);
            case "update_template": return confirmable("update_template", args, () -> {
                Template tpl = requireTemplate(dm, str(args, "id"));
                if (args.containsKey("name") && !str(args, "name").isBlank()) tpl.setName(str(args, "name").trim());
                if (args.containsKey("pageSize")) tpl.setPage(pageFor(str(args, "pageSize"), tpl.getPage()));
                if (args.get("elements") instanceof List<?> els) tpl.setElements(elementsFrom(els));
                dm.templates().saveTemplate(tpl);
            });
            case "duplicate_template": return duplicateTemplate(dm, args);
            case "delete_template": return confirmable("delete_template", args, () -> dm.templates().deleteTemplate(str(args, "id")));
            case "list_variables": return dm.variables().getAllVariables().stream().map(v -> mapOf(
                    "key", v.getKey(), "label", v.getLabel(), "type", v.getType(),
                    "builtin", v.isBuiltin(), "scope", v.getScope(), "defaultValue", v.getDefaultValue()))
                    .collect(java.util.stream.Collectors.toList());
            case "create_variable": return createVariable(dm, args);
            case "delete_variable": return confirmable("delete_variable", args, () -> dm.variables().deleteVariable(str(args, "key")));
            case "create_transport": return createTransport(dm, args);
            case "update_transport": return confirmable("update_transport", args, () -> {
                com.invoicestudio.model.Transport tr = requireTransport(dm, str(args, "id"));
                boolean changed = false;
                if (args.containsKey("name") && !strOr(args, "name", "").isBlank()) {
                    tr.setName(str(args, "name").trim());
                    changed = true;
                }
                if (args.containsKey("phone")) { tr.setPhone(strOr(args, "phone", "")); changed = true; }
                if (args.containsKey("vehicleNumber")) { tr.setVehicleNumber(strOr(args, "vehicleNumber", "")); changed = true; }
                if (!changed) throw new IllegalArgumentException("Nothing to update: pass name, phone and/or vehicleNumber");
                dm.saveTransport(tr);
            });
            case "delete_transport": return confirmable("delete_transport", args, () -> {
                String id = str(args, "id");
                com.invoicestudio.model.Transport tr = requireTransport(dm, id);
                // Buyers carry defaultTransportId — refuse while references exist
                // so no buyer is left pointing at a deleted transport.
                java.util.List<com.invoicestudio.model.Buyer> referencing = new java.util.ArrayList<>();
                for (com.invoicestudio.model.Buyer b : dm.getAllBuyers()) {
                    if (id.equals(b.getDefaultTransportId())) referencing.add(b);
                }
                if (!referencing.isEmpty() && !"true".equalsIgnoreCase(strOr(args, "force", ""))) {
                    throw new IllegalArgumentException("Transport '" + tr.getName() + "' is the default transport of "
                            + referencing.size() + " buyer(s). Reassign them first with update_buyer { id, transportName/transportId }, "
                            + "or call delete_transport again with force:true to clear their default transport automatically.");
                }
                if (!referencing.isEmpty()) {
                    for (com.invoicestudio.model.Buyer b : referencing) {
                        b.setDefaultTransportId("");
                        dm.buyers().saveBuyer(b);
                    }
                }
                dm.deleteTransport(id);
            });
            case "create_backup": return createBackup(dm, args);
            case "whoami": return whoami();

            // Sales
            case "list_bills": return billsMap(dm, str(args, "query"), str(args, "status"), intVal(args, "limit", 50));
            case "get_bill": return billFull(requireBill(dm, str(args, "id")));
            case "create_bill": return createBill(dm, args);
            case "update_bill_status": return confirmable("update_bill_status", args, () -> {
                Bill b = requireBill(dm, str(args, "id"));
                b.setStatus(BillStatus.valueOf(str(args, "status").trim().toUpperCase(Locale.ROOT)));
                dm.saveBill(b);
            });
            case "delete_bill": return confirmable("delete_bill", args, () -> dm.deleteBill(str(args, "id")));

            // Purchases
            case "list_purchases": return purchasesMap(dm, str(args, "query"), intVal(args, "limit", 50));
            case "create_purchase": return createPurchase(dm, args);
            case "pay_purchase": return payPurchase(dm, args);
            case "delete_purchase": return confirmable("delete_purchase", args, () -> dm.deletePurchase(str(args, "id")));

            // Expenses & money
            case "list_expenses": return dm.getAllExpenses().stream()
                    .limit(intVal(args, "limit", 100)).map(McpToolRegistry::expenseMap)
                    .collect(java.util.stream.Collectors.toList());
            case "record_expense": return recordExpense(dm, args);
            case "delete_expense": return confirmable("delete_expense", args, () -> dm.deleteExpense(str(args, "id")));

            // Expense accounts
            case "list_expense_accounts": return expenseAccountsMap(dm, boolVal(args, "includeArchived", false));
            case "create_expense_account": return createExpenseAccount(dm, args);
            case "rename_expense_account": return confirmable("rename_expense_account", args,
                    () -> renameExpenseAccount(dm, args));
            case "expense_account_report": return expenseAccountReport(dm, args);

            // Expense account lifecycle (Accounts dialog parity)
            case "update_expense_account": return confirmable("update_expense_account", args,
                    () -> updateExpenseAccount(dm, args));
            case "delete_expense_account": return confirmable("delete_expense_account", args,
                    () -> deleteExpenseAccount(dm, str(args, "id")));

            // Batch invoice export
            case "export_bills_pdf": return exportBillsPdf(dm, args);

            // Keyboard shortcuts
            case "list_shortcuts": return shortcutsMap();
            case "rebind_shortcut": {
                // Fail fast at call time (like the Shortcuts dialog); the
                // gated Runnable re-validates before actually binding.
                String raId = str(args, "actionId");
                if (ShortcutManager.action(raId) == null) {
                    throw new IllegalArgumentException("Unknown shortcut actionId: " + raId
                            + " — call list_shortcuts for valid ids");
                }
                ShortcutManager.Validation pre = ShortcutManager.validate(raId, strOr(args, "combo", ""));
                if (pre != ShortcutManager.Validation.OK) {
                    throw new IllegalArgumentException(ShortcutManager.validationMessage(pre, strOr(args, "combo", "")));
                }
                return confirmable("rebind_shortcut", args, () -> rebindShortcut(args));
            }
            case "reset_shortcut": {
                if (!boolVal(args, "resetAll", false)
                        && ShortcutManager.action(str(args, "actionId")) == null) {
                    throw new IllegalArgumentException("Unknown shortcut actionId: " + str(args, "actionId"));
                }
                return confirmable("reset_shortcut", args, () -> resetShortcut(args));
            }

            // Label (barcode) printing
            case "get_label_print_state": return labelPrintStateMap(dm, str(args, "templateId"));
            case "print_labels": return printLabels(dm, args);

            case "list_transactions": return dm.getAllTransactions().stream()
                    .limit(intVal(args, "limit", 100)).map(McpToolRegistry::transactionMap)
                    .collect(java.util.stream.Collectors.toList());

            // Reports
            case "stock_report": return stockReport(dm);
            case "profitability_report": return profitabilityReport(dm);
            case "financial_summary": return financialSummary(dm, args);
            case "daybook": return FIN.buildDaybook(dm.getAllBills(), dm.getAllPurchases(), dm.getAllExpenses())
                    .stream().map(e -> mapOf("date", e.date(), "type", e.type(),
                            "particulars", e.particulars(), "inflow", e.inflow(), "outflow", e.outflow()))
                    .collect(java.util.stream.Collectors.toList());

            // System
            case "server_status": return McpServer.statusMap();
            case "audit_log": return McpAuditLog.recent().stream()
                    .limit(intVal(args, "limit", 50)).collect(java.util.stream.Collectors.toList());
            case "confirm_operation": {
                boolean approve = boolVal(args, "approve", true);
                String opId = str(args, "operationId");
                boolean ok = approve ? PendingOperations.approve(opId) : PendingOperations.reject(opId);
                if (!ok) return mapOf("ok", false, "error", "Unknown or already-handled operationId: " + opId);
                return mapOf("ok", true, "approved", approve);
            }

            default:
                throw new IllegalArgumentException("Unknown tool: " + name);
        }
    }

    // ------------------------------------------------------------------
    // Confirmation wrapper for destructive ops
    // ------------------------------------------------------------------

    private static Map<String, Object> confirmable(String tool, Map<String, Object> args, Runnable action) {
        String summary = describeOp(tool, args);
        String detail = "Tool: " + tool + "\nArguments: " + safeJson(args)
                + "\n\nThis operation modifies or removes existing records. "
                + "Approve in Settings → MCP Server, or call confirm_operation.";
        String id = PendingOperations.queue(tool, summary, detail, args, action);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("requiresConfirmation", true);
        res.put("operationId", id);
        res.put("summary", summary);
        res.put("message", "Waiting for user approval in Settings → MCP Server. "
                + "Explain this to the user and ask them to approve (or to tell you to cancel). "
                + "Nothing has been changed yet.");
        return res;
    }

    private static String describeOp(String tool, Map<String, Object> args) {
        return describeOpForTest(tool, args);
    }

    /** Package-visible bridge so MCP tests can assert the confirmation summary text. */
    static String describeOpForTest(String tool, Map<String, Object> args) {
        switch (tool) {
            case "delete_bill": return "Delete invoice " + str(args, "id") + " (stock rows reversed)";
            case "delete_purchase": return "Delete purchase bill " + str(args, "id") + " (stock rows reversed)";
            case "delete_buyer": return "Permanently delete buyer " + str(args, "id");
            case "delete_supplier": return "Permanently delete supplier " + str(args, "id");
            case "delete_item": return "Permanently delete catalog item " + str(args, "id");
            case "update_transport": {
                StringBuilder sb = new StringBuilder("Update transport ").append(str(args, "id"));
                List<String> changes = new ArrayList<>();
                if (args.containsKey("name")) changes.add("name → " + str(args, "name"));
                if (args.containsKey("phone")) changes.add("phone → " + str(args, "phone"));
                if (args.containsKey("vehicleNumber")) changes.add("vehicleNumber → " + str(args, "vehicleNumber"));
                if (!changes.isEmpty()) sb.append(": ").append(String.join(", ", changes));
                return sb.toString();
            }
            case "delete_transport": {
                boolean force = "true".equalsIgnoreCase(strOr(args, "force", ""));
                return "Delete transport " + str(args, "id")
                        + (force ? " and CLEAR the default-transport assignment of every buyer using it"
                                 : " (refused while buyers still reference it as their default)");
            }
            case "update_buyer": {
                StringBuilder sb = new StringBuilder("Update buyer ").append(str(args, "id"));
                List<String> changes = new ArrayList<>();
                if (args.containsKey("name")) changes.add("name → " + str(args, "name"));
                if (args.containsKey("phone")) changes.add("phone → " + str(args, "phone"));
                if (args.containsKey("gst")) changes.add("gst → " + str(args, "gst"));
                if (args.containsKey("address")) changes.add("address → " + str(args, "address"));
                if (args.containsKey("state")) changes.add("state → " + str(args, "state"));
                if (args.containsKey("stateCode")) changes.add("stateCode → " + str(args, "stateCode"));
                if (args.containsKey("city")) changes.add("city → " + str(args, "city"));
                if (args.containsKey("contactPerson")) changes.add("contactPerson → " + str(args, "contactPerson"));
                if (args.containsKey("openingBalance")) changes.add("openingBalance → " + args.get("openingBalance"));
                if (args.containsKey("creditLimit")) changes.add("creditLimit → " + args.get("creditLimit"));
                String trName = strOr(args, "transportName", "");
                String trId = strOr(args, "transportId", "");
                if (!trName.isBlank() || !trId.isBlank())
                    changes.add("ASSIGN default transport → " + (!trName.isBlank() ? trName : trId));
                if (!changes.isEmpty()) sb.append(": ").append(String.join(", ", changes));
                return sb.toString();
            }
            case "update_supplier": {
                StringBuilder sb = new StringBuilder("Update supplier ").append(str(args, "id"));
                List<String> changes = new ArrayList<>();
                if (args.containsKey("name")) changes.add("name → " + str(args, "name"));
                if (args.containsKey("phone")) changes.add("phone → " + str(args, "phone"));
                if (args.containsKey("gst")) changes.add("gst → " + str(args, "gst"));
                if (args.containsKey("state")) changes.add("state → " + str(args, "state"));
                if (args.containsKey("stateCode")) changes.add("stateCode → " + str(args, "stateCode"));
                if (args.containsKey("city")) changes.add("city → " + str(args, "city"));
                if (args.containsKey("address")) changes.add("address → " + str(args, "address"));
                if (args.containsKey("openingBalance")) changes.add("openingBalance → " + args.get("openingBalance"));
                if (args.containsKey("creditPeriodDays")) changes.add("creditPeriodDays → " + args.get("creditPeriodDays"));
                if (!changes.isEmpty()) sb.append(": ").append(String.join(", ", changes));
                return sb.toString();
            }
            case "update_category": return "Rename category " + str(args, "id") + " → " + str(args, "name")
                    + " (cascades to all items referencing it)";
            case "delete_category": return "Delete category " + str(args, "id") + " (only allowed while it has no items)";
            case "update_item": {
                StringBuilder sb = new StringBuilder("Update item ").append(str(args, "id"));
                List<String> changes = new ArrayList<>();
                if (args.containsKey("name")) changes.add("name → " + str(args, "name"));
                if (args.containsKey("hsn")) changes.add("hsn → " + str(args, "hsn"));
                if (args.containsKey("unit")) changes.add("unit → " + str(args, "unit"));
                if (args.containsKey("rate")) changes.add("rate → " + args.get("rate"));
                if (args.containsKey("gst")) changes.add("gst → " + args.get("gst"));
                if (args.containsKey("purchaseRate")) changes.add("purchaseRate → " + args.get("purchaseRate"));
                if (args.containsKey("reorderLevel")) changes.add("reorderLevel → " + args.get("reorderLevel"));
                String catName = strOr(args, "categoryName", "");
                String catId = strOr(args, "categoryId", "");
                if (!catName.isBlank() || !catId.isBlank())
                    changes.add("MOVE category → " + (!catName.isBlank() ? catName : catId));
                if (!changes.isEmpty()) sb.append(": ").append(String.join(", ", changes));
                return sb.toString();
            }
            case "delete_expense": return "Delete expense " + str(args, "id");
            case "rename_expense_account": return "Rename expense account " + str(args, "id")
                    + " → " + str(args, "newName") + " (updates every voucher with the old name)";
            case "update_expense_account": {
                List<String> changes = new ArrayList<>();
                if (args.containsKey("name")) changes.add("name → " + str(args, "name") + " (propagates to all vouchers)");
                if (args.containsKey("notes")) changes.add("notes → " + str(args, "notes"));
                if (args.containsKey("defaultPaymentMode")) changes.add("defaultPaymentMode → " + str(args, "defaultPaymentMode"));
                if (args.containsKey("archived")) changes.add(boolVal(args, "archived", false) ? "ARCHIVE" : "UNARCHIVE");
                StringBuilder sb = new StringBuilder("Edit expense account ").append(str(args, "id"));
                if (!changes.isEmpty()) sb.append(": ").append(String.join(", ", changes));
                return sb.toString();
            }
            case "delete_expense_account": return "Permanently delete expense account " + str(args, "id")
                    + " (only allowed while no voucher still references it)";
            case "rebind_shortcut": return "Rebind shortcut \"" + shortcutLabel(str(args, "actionId"))
                    + "\" → " + (strOr(args, "combo", "").isBlank() ? "(unbind)" : str(args, "combo"));
            case "reset_shortcut": return boolVal(args, "resetAll", false)
                    ? "Reset EVERY keyboard shortcut to its default binding"
                    : "Reset shortcut \"" + shortcutLabel(str(args, "actionId")) + "\" to its default binding";
            case "update_bill_status": return "Change invoice " + str(args, "id") + " status → " + str(args, "status");
            default:
                StringBuilder sb = new StringBuilder("Modify ").append(tool.replace('_', ' '));
                if (args.containsKey("id")) sb.append(" (id ").append(str(args, "id")).append(")");
                return sb.toString();
        }
    }

    private static String safeJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    // ------------------------------------------------------------------
    // Entity creators
    // ------------------------------------------------------------------

    private static Map<String, Object> createBuyer(DataManager dm, Map<String, Object> args) throws Exception {
        String name = str(args, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        List<Map<String, Object>> autoCreated = new ArrayList<>();

        // Idempotent by name — never silently duplicate a directory entry.
        McpEnsure.Outcome out = McpEnsure.ensureBuyer(dm, null, name);
        if (!out.created) {
            return mapOf("ok", true, "id", out.id, "name", out.name,
                    "existed", true, "matchedBy", out.matchedBy,
                    "note", "Existing buyer returned unchanged; use update_buyer to modify.");
        }
        autoCreated.add(out.asMap("buyer"));
        // enrich the freshly auto-created record with the provided fields
        Buyer b = dm.buyers().getBuyerById(out.id);
        if (b != null) {
            if (!strOr(args, "gst", "").isBlank()) b.setGst(strOr(args, "gst", "").toUpperCase(Locale.ROOT));
            if (!strOr(args, "phone", "").isBlank()) b.setPhone(strOr(args, "phone", ""));
            if (!strOr(args, "address", "").isBlank()) b.setAddress(strOr(args, "address", ""));
            if (!strOr(args, "state", "").isBlank()) b.setState(strOr(args, "state", ""));
            if (!strOr(args, "stateCode", "").isBlank()) b.setStateCode(strOr(args, "stateCode", ""));
            if (!strOr(args, "city", "").isBlank()) b.setCity(strOr(args, "city", ""));
            if (!strOr(args, "contactPerson", "").isBlank()) b.setContactPerson(strOr(args, "contactPerson", ""));
            if (args.containsKey("openingBalance")) b.setOpeningBalance(dbl(args, "openingBalance", 0.0));
            if (args.containsKey("creditLimit")) b.setCreditLimit(dbl(args, "creditLimit", 100000.0));
            McpEnsure.Outcome transport = McpEnsure.ensureTransport(dm,
                    strOr(args, "transportId", ""), strOr(args, "transportName", ""));
            if (transport != null) {
                b.setDefaultTransportId(transport.id);
                if (transport.created) autoCreated.add(transport.asMap("transport"));
            }
            dm.buyers().saveBuyer(b);
        }
        return mapOf("ok", true, "id", out.id, "name", b != null ? b.getName() : out.name,
                "existed", false, "autoCreated", autoCreated);
    }

    private static Map<String, Object> createSupplier(DataManager dm, Map<String, Object> args) throws Exception {
        String name = str(args, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");

        // Idempotent by name — never silently duplicate a directory entry.
        McpEnsure.Outcome out = McpEnsure.ensureSupplier(dm, null, name);
        if (!out.created) {
            return mapOf("ok", true, "id", out.id, "name", out.name,
                    "existed", true, "matchedBy", out.matchedBy,
                    "note", "Existing supplier returned unchanged; use update_supplier to modify.");
        }
        Supplier s = dm.suppliers().getSupplierById(out.id);
        if (s != null) {
            s.setPhone(strOr(args, "phone", ""));
            s.setGst(strOr(args, "gst", "").toUpperCase(Locale.ROOT));
            s.setState(strOr(args, "state", ""));
            s.setStateCode(strOr(args, "stateCode", ""));
            s.setCity(strOr(args, "city", ""));
            s.setAddress(strOr(args, "address", ""));
            s.setOpeningBalance(dbl(args, "openingBalance", 0.0));
            s.setCreditPeriodDays((int) dbl(args, "creditPeriodDays", 0));
            dm.suppliers().saveSupplier(s);
        }
        return mapOf("ok", true, "id", out.id, "name", s != null ? s.getName() : out.name,
                "existed", false, "autoCreated", List.of(out.asMap("supplier")));
    }

    private static Map<String, Object> createItem(DataManager dm, Map<String, Object> args) throws Exception {
        String name = str(args, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");

        // Idempotent by name — never silently duplicate a catalog item.
        ItemRecord dup = McpEnsure.findItem(dm, null, name);
        if (dup != null) {
            return mapOf("ok", true, "id", dup.getId(), "name", dup.getName(),
                    "category", dup.getCategoryName(),
                    "existed", true, "matchedBy", "name",
                    "note", "Existing item returned unchanged — create_item NEVER updates (passing a different categoryName here does NOT move the item). To change any field or reassign the category use update_item with categoryId/categoryName (confirmation-gated).");
        }

        String categoryId = strOr(args, "categoryId", "").trim();
        String categoryName = strOr(args, "categoryName", "").trim();

        // Check-then-create: resolve the reference first; auto-create when missing.
        McpEnsure.Outcome category = McpEnsure.ensureCategory(dm, categoryId, categoryName);
        List<McpEnsure.Tracked> created = McpEnsure.track();
        List<Map<String, Object>> autoCreated = new ArrayList<>();
        if (category != null && category.created) {
            created.add(new McpEnsure.Tracked("category", category.id));
            autoCreated.add(category.asMap("category"));
        }

        ItemRecord it = new ItemRecord();
        it.setId("item_mcp_" + UUID.randomUUID().toString().substring(0, 8));
        it.setName(name.trim());
        it.setHsn(strOr(args, "hsn", ""));
        it.setUnit(strOr(args, "unit", "PCS"));
        it.setRate(dbl(args, "rate", 0.0));
        it.setGst(dbl(args, "gst", 0.0));
        it.setPurchaseRate(dbl(args, "purchaseRate", 0.0));
        it.setOpeningStock(dbl(args, "openingStock", 0.0));
        it.setReorderLevel(dbl(args, "reorderLevel", 0.0));
        if (category != null) {
            // the reference is guaranteed to exist now — no dangling category_id possible
            it.setCategoryId(category.id);
            it.setCategoryName(category.name);
        }
        dm.items().saveItem(it);
        dm.stockLedger().recomputeItem(it.getId());
        // DAOs swallow SQL failures — verify before declaring success.
        if (dm.items().getItemById(it.getId()) == null) {
            McpEnsure.rollback(dm, created);
            throw new IllegalArgumentException(
                    "Item save failed; auto-created category was rolled back (nothing half-done).");
        }
        return mapOf("ok", true, "id", it.getId(), "name", it.getName(),
                "category", it.getCategoryName(),
                "existed", false, "autoCreated", autoCreated);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> createBill(DataManager dm, Map<String, Object> args) throws Exception {
        Settings st = dm.getSettings();
        Bill bill = new Bill();
        bill.setId("bill_mcp_" + UUID.randomUUID().toString().substring(0, 8));
        String nextNo = st.getBillNoPrefix() + String.format("%0" + Math.max(1, st.getBillNoDigits()) + "d", st.getBillNoNext());
        bill.setBillNo(nextNo);
        bill.setDate(strOr(args, "date", LocalDate.now().toString()));

        // Check-then-create: resolve the buyer reference; auto-create when unknown.
        // ANY failure from here until the bill is verified-persisted must roll
        // back everything this call auto-created (no half-done state).
        List<McpEnsure.Tracked> created = McpEnsure.track();
        List<Map<String, Object>> autoCreated = new ArrayList<>();
        try {
        String buyerId = strOr(args, "buyerId", "");
        String buyerName = strOr(args, "buyerName", "");
        Buyer buyer = null;
        if (!buyerId.isBlank() || !buyerName.isBlank()) {
            McpEnsure.Outcome buyerRef = McpEnsure.ensureBuyer(dm, buyerId, buyerName);
            if (buyerRef != null) {
                buyer = dm.buyers().getBuyerById(buyerRef.id);
                if (buyerRef.created) {
                    created.add(new McpEnsure.Tracked("buyer", buyerRef.id));
                    autoCreated.add(buyerRef.asMap("buyer"));
                }
            }
        }
        if (buyer != null) {
            bill.setBuyerName(buyer.getName());
            buyerName = buyer.getName();
        } else {
            bill.setBuyerName(buyerName.isBlank() ? "Walk-in Customer" : buyerName.trim());
        }

        // Inter-state decision: buyer state code vs company state code
        boolean interState = st.isInterState();
        if (buyer != null && buyer.getEffectiveStateCode() != null && !buyer.getEffectiveStateCode().isBlank()) {
            String co = st.getBusiness().getStateCode();
            interState = !buyer.getEffectiveStateCode().equals(co);
        }

        List<BillItem> items = new ArrayList<>();
        Object itemsRaw = args.get("items");
        if (!(itemsRaw instanceof List<?> inLines) || inLines.isEmpty()) {
            throw new IllegalArgumentException("items[] is required (at least one line)");
        }
        for (Object o : inLines) {
            Map<String, Object> line = (Map<String, Object>) o;
            BillItem bi = new BillItem();
            String itemId = str(line, "itemId");
            if (itemId != null && !itemId.isBlank()) {
                // Check-then-create: an unknown catalog id is auto-created from this
                // line's own data, so no invoice line can reference a phantom item.
                McpEnsure.Outcome lineItem = McpEnsure.ensureItemForLine(dm, itemId, str(line, "desc"),
                        line.containsKey("rate") ? dbl(line, "rate", 0.0) : null,
                        line.containsKey("gst") ? dbl(line, "gst", 0.0) : null);
                if (lineItem != null) {
                    ItemRecord cat = dm.items().getItemById(lineItem.id);
                    if (cat != null) {
                        bi.setId(cat.getId());
                        bi.setDesc(cat.getName());
                        if (!line.containsKey("rate")) bi.setRate(cat.getRate());
                        if (!line.containsKey("gst")) bi.setGst(cat.getGst());
                    }
                    if (lineItem.created) {
                        created.add(new McpEnsure.Tracked("item", lineItem.id));
                        autoCreated.add(lineItem.asMap("item"));
                    }
                }
            }
            if (str(line, "desc") != null && !str(line, "desc").isBlank()) bi.setDesc(str(line, "desc").trim());
            if (bi.getDesc() == null || bi.getDesc().isBlank()) throw new IllegalArgumentException("each line needs itemId or desc");
            bi.setQty(dbl(line, "qty", 1.0));
            if (line.containsKey("rate")) bi.setRate(dbl(line, "rate", 0.0));
            if (line.containsKey("gst")) bi.setGst(dbl(line, "gst", 0.0));
            bi.setDiscPct(dbl(line, "discPct", 0.0));
            items.add(bi);
        }
        bill.setItems(items);
        bill.setDiscountPct(dbl(args, "discountPct", 0.0));
        bill.setTotals(BillingService.computeTotals(items, bill.getDiscountPct(), interState));

        boolean payNowBill = boolVal(args, "paid", false);
        bill.setStatus(payNowBill ? BillStatus.PAID : BillStatus.UNPAID);
        if (payNowBill) {
            BillPayment p = new BillPayment();
            p.setId("pay_" + UUID.randomUUID().toString().substring(0, 8));
            p.setDate(bill.getDate());
            p.setAmount(bill.getTotals().getGrandTotal());
            p.setMethod(parseMethod(strOr(args, "paymentMode", "Cash")));
            bill.setPayments(new ArrayList<>(List.of(p)));
            bill.setPaidAt(LocalDate.now().toString());
        }
        dm.saveBill(bill);
        // DAOs swallow SQL failures, so verify the write actually landed before
        // declaring success — otherwise auto-created deps would dangle.
        if (dm.bills().getBillById(bill.getId()) == null) {
            throw new IllegalArgumentException(
                    "Invoice save failed; auto-created dependencies were rolled back (nothing half-done).");
        }

        // Bump the invoice counter only when the generated number was used
        if (nextNo.equals(bill.getBillNo())) {
            st.setBillNoNext(st.getBillNoNext() + 1);
            dm.saveSettings(st);
        }
        Map<String, Object> respBill = new LinkedHashMap<>();
        respBill.put("ok", true);
        respBill.put("id", bill.getId());
        respBill.put("billNo", bill.getBillNo());
        respBill.put("grandTotal", bill.getTotals().getGrandTotal());
        respBill.put("status", bill.getStatus().name());
        respBill.put("interState", interState);
        respBill.put("autoCreated", autoCreated);
        return respBill;
        } catch (Exception e) {
            McpEnsure.rollback(dm, created);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> createPurchase(DataManager dm, Map<String, Object> args) throws Exception {
        // Check-then-create: resolve the supplier; AUTO-CREATE when unknown —
        // a missing directory entry is never a hard error.
        List<McpEnsure.Tracked> created = McpEnsure.track();
        List<Map<String, Object>> autoCreated = new ArrayList<>();
        try {
        String sid = strOr(args, "supplierId", "");
        String sname = strOr(args, "supplierName", "");
        if (sid.isBlank() && sname.isBlank()) {
            throw new IllegalArgumentException("supplierId or supplierName is required");
        }
        McpEnsure.Outcome supplierRef = McpEnsure.ensureSupplier(dm, sid, sname);
        Supplier supplier = dm.suppliers().getSupplierById(supplierRef.id);
        if (supplierRef.created) {
            created.add(new McpEnsure.Tracked("supplier", supplierRef.id));
            autoCreated.add(supplierRef.asMap("supplier"));
        }
        if (supplier == null) {
            throw new IllegalArgumentException("Supplier could not be persisted: " + supplierRef.id);
        }

        PurchaseBill pb = new PurchaseBill();
        pb.setId(PurchaseService.newPurchaseId());
        pb.setBillNo(PurchaseService.nextPurchaseBillNo(dm.getAllPurchases().size() + 1, 4));
        pb.setSupplierBillNo(strOr(args, "supplierBillNo", ""));
        pb.setDate(strOr(args, "date", com.invoicestudio.service.PurchaseService.todayISO()));
        pb.setSupplierId(supplier.getId());
        pb.setSupplierName(supplier.getName());
        pb.setSupplierGstin(supplier.getGst());

        boolean interState = PurchaseService.isInterStateSupply(supplier.getGst(), dm.getSettings().getBusiness().getStateCode());

        // --- Build the line items ---
        List<BillItem> purchaseLines = new ArrayList<>();
        Object itemsRaw = args.get("items");
        if (!(itemsRaw instanceof List<?> inItems) || inItems.isEmpty()) {
            throw new IllegalArgumentException("items[] is required (at least one line)");
        }
        for (Object o : inItems) {
            Map<String, Object> line = (Map<String, Object>) o;
            BillItem bi = new BillItem();
            String itemIdArg = str(line, "itemId");
            if (itemIdArg != null && !itemIdArg.isBlank()) {
                // Check-then-create: unknown catalog id → auto-create from this line.
                McpEnsure.Outcome lineItem = McpEnsure.ensureItemForLine(dm, itemIdArg, str(line, "desc"),
                        line.containsKey("rate") ? dbl(line, "rate", 0.0) : null,
                        line.containsKey("gst") ? dbl(line, "gst", 0.0) : null);
                if (lineItem != null) {
                    ItemRecord cat = dm.items().getItemById(lineItem.id);
                    if (cat != null) {
                        bi.setId(cat.getId());
                        bi.setDesc(cat.getName());
                        if (!line.containsKey("rate") && cat.getPurchaseRate() > 0) bi.setRate(cat.getPurchaseRate());
                        if (!line.containsKey("gst")) bi.setGst(cat.getGst());
                    }
                    if (lineItem.created) {
                        created.add(new McpEnsure.Tracked("item", lineItem.id));
                        autoCreated.add(lineItem.asMap("item"));
                    }
                }
            }
            if (str(line, "desc") != null && !str(line, "desc").isBlank()) bi.setDesc(str(line, "desc").trim());
            if (bi.getDesc() == null || bi.getDesc().isBlank()) throw new IllegalArgumentException("each line needs itemId or desc");
            bi.setQty(dbl(line, "qty", 1.0));
            if (line.containsKey("rate")) bi.setRate(dbl(line, "rate", 0.0));
            if (line.containsKey("gst")) bi.setGst(dbl(line, "gst", 0.0));
            bi.setDiscPct(dbl(line, "discPct", 0.0));
            purchaseLines.add(bi);
        }
        pb.setItems(purchaseLines);
        pb.setDiscountPct(dbl(args, "discountPct", 0.0));
        pb.setFreight(dbl(args, "freight", 0.0));
        pb.setTotals(PurchaseService.computePurchaseTotals(purchaseLines, pb.getDiscountPct(), interState));
        boolean payNow = boolVal(args, "paid", false);
        pb.setPaid(payNow);
        pb.setPaymentMode(strOr(args, "paymentMode", payNow ? "Cash" : ""));
        dm.savePurchase(pb);
        // DAOs swallow SQL failures — verify before declaring success.
        if (dm.purchases().getPurchaseBillById(pb.getId()) == null) {
            throw new IllegalArgumentException(
                    "Purchase save failed; auto-created dependencies were rolled back (nothing half-done).");
        }
        Map<String, Object> respP = new LinkedHashMap<>();
        respP.put("ok", true);
        respP.put("id", pb.getId());
        respP.put("billNo", pb.getBillNo());
        respP.put("grandTotal", pb.getTotals() != null ? pb.getTotals().getGrandTotal() : 0.0);
        respP.put("itc", pb.getTotals() != null
                ? PurchaseService.round2(pb.getTotals().getCgst() + pb.getTotals().getSgst() + pb.getTotals().getIgst())
                : 0.0);
        respP.put("interState", interState);
        respP.put("amountPayable", pb.getAmountPayable());
        respP.put("autoCreated", autoCreated);
        return respP;
        } catch (Exception e) {
            McpEnsure.rollback(dm, created);
            throw e;
        }
    }

    private static Map<String, Object> payPurchase(DataManager dm, Map<String, Object> args) throws Exception {
        PurchaseBill pb = dm.purchases().getPurchaseBillById(str(args, "id"));
        if (pb == null) throw new IllegalArgumentException("Purchase bill not found: " + str(args, "put an id from list_purchases"));
        double remaining = pb.getAmountPayable() - pb.getPaidAmount();
        double amount = args.containsKey("amount") ? dbl(args, "amount", remaining) : remaining;
        if (amount <= 0) throw new IllegalArgumentException("Nothing left to pay on " + pb.getBillNo());
        amount = Math.min(amount, remaining);

        BillPayment p = new BillPayment();
        p.setId("pmt_" + UUID.randomUUID().toString().substring(0, 8));
        p.setDate(LocalDate.now().toString());
        p.setAmount(amount);
        p.setMethod(parseMethod(strOr(args, "mode", "Cash")));
        p.setReference(strOr(args, "reference", ""));
        List<BillPayment> payments = new ArrayList<>(pb.getPayments());
        payments.add(p);
        pb.setPayments(payments);
        if (pb.getPaidAmount() + 0.005 >= pb.getAmountPayable()) pb.setPaid(true);
        dm.savePurchase(pb);
        return mapOf("ok", true, "paid", amount, "remaining",
                PurchaseService.round2(pb.getAmountPayable() - pb.getPaidAmount()),
                "fullySettled", pb.isPaid());
    }

    // ------------------------------------------------------------------
    // Templates, variables, transports, backup, identity
    // ------------------------------------------------------------------

    private static Template requireTemplate(DataManager dm, String id) {
        Template t = dm.templates().getTemplateById(id);
        if (t == null) throw new IllegalArgumentException("Template not found: " + id);
        return t;
    }

    private static Map<String, Object> templateFull(Template t) {
        List<Map<String, Object>> els = new ArrayList<>();
        if (t.getElements() != null) {
            for (var e : t.getElements()) els.add(elementFull(e));
        }
        return mapOf("id", t.getId(), "name", t.getName(),
                "pageSize", String.valueOf(t.getPage().getSizeName()),
                "orientation", t.getPage().getOrientation(),
                "widthMm", t.getPage().getWidth(), "heightMm", t.getPage().getHeight(),
                "autoHeight", t.getPage().isAutoHeight(),
                "margins", mapOf("top", t.getPage().getMargin().getTop(), "right", t.getPage().getMargin().getRight(),
                        "bottom", t.getPage().getMargin().getBottom(), "left", t.getPage().getMargin().getLeft()),
                "elements", els);
    }

    /** Lossless element readback — every design property the model supports, so get_template round-trips into update_template. */
    private static Map<String, Object> elementFull(com.invoicestudio.model.TemplateElement e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("type", String.valueOf(e.getType()));
        // position & layout
        m.put("x", e.getX()); m.put("y", e.getY()); m.put("w", e.getW()); m.put("h", e.getH());
        m.put("zIndex", e.getZIndex()); m.put("rotation", e.getRotation());
        m.put("locked", e.isLocked()); m.put("hidden", e.isHidden());
        m.put("repeatOnPages", e.isRepeatOnPages()); m.put("hideWhenBlank", e.isHideWhenBlank());
        m.put("opacity", e.getOpacity());
        m.put("clipEnabled", e.isClipEnabled()); m.put("clipShape", e.getClipShape());
        m.put("scaleX", e.getScaleX()); m.put("scaleY", e.getScaleY());
        m.put("flipHorizontal", e.isFlipHorizontal()); m.put("flipVertical", e.isFlipVertical());
        // typography
        m.put("text", e.getText()); m.put("binding", e.getBinding());
        m.put("fontFamily", e.getFontFamily()); m.put("fontSize", e.getFontSize());
        m.put("fontWeight", e.getFontWeight()); m.put("italic", e.isItalic());
        m.put("underline", e.isUnderline()); m.put("strikethrough", e.isStrikethrough());
        m.put("uppercase", e.isUppercase()); m.put("color", e.getColor());
        m.put("align", e.getAlign()); m.put("vAlign", e.getVAlign());
        m.put("lineHeight", e.getLineHeight()); m.put("letterSpacing", e.getLetterSpacing());
        m.put("wordSpacing", e.getWordSpacing()); m.put("textTransform", e.getTextTransform());
        // box & border
        m.put("bg", e.getBg()); m.put("borderWidth", e.getBorderWidth());
        m.put("borderColor", e.getBorderColor()); m.put("borderRadius", e.getBorderRadius());
        m.put("padding", e.getPadding());
        m.put("borderTop", e.isBorderTop()); m.put("borderBottom", e.isBorderBottom());
        m.put("borderLeft", e.isBorderLeft()); m.put("borderRight", e.isBorderRight());
        m.put("individualBorders", e.isIndividualBorders());
        m.put("borderTopWidth", e.getBorderTopWidth()); m.put("borderTopColor", e.getBorderTopColor()); m.put("borderTopStyle", e.getBorderTopStyle());
        m.put("borderBottomWidth", e.getBorderBottomWidth()); m.put("borderBottomColor", e.getBorderBottomColor()); m.put("borderBottomStyle", e.getBorderBottomStyle());
        m.put("borderLeftWidth", e.getBorderLeftWidth()); m.put("borderLeftColor", e.getBorderLeftColor()); m.put("borderLeftStyle", e.getBorderLeftStyle());
        m.put("borderRightWidth", e.getBorderRightWidth()); m.put("borderRightColor", e.getBorderRightColor()); m.put("borderRightStyle", e.getBorderRightStyle());
        // fill & gradient
        m.put("fillType", e.getFillType());
        m.put("gradientStartColor", e.getGradientStartColor()); m.put("gradientEndColor", e.getGradientEndColor());
        m.put("gradientAngle", e.getGradientAngle()); m.put("gradientCenterX", e.getGradientCenterX());
        m.put("gradientCenterY", e.getGradientCenterY()); m.put("gradientRadius", e.getGradientRadius());
        // stroke & dash
        m.put("strokeEnabled", e.isStrokeEnabled()); m.put("strokeType", e.getStrokeType());
        m.put("lineCap", e.getLineCap()); m.put("lineJoin", e.getLineJoin());
        m.put("dashPattern", e.getDashPattern()); m.put("dashOffset", e.getDashOffset());
        // image
        m.put("src", e.getSrc()); m.put("objectFit", e.getObjectFit()); m.put("useBusinessLogo", e.isUseBusinessLogo());
        // qr / barcode
        m.put("qrSource", e.getQrSource()); m.put("qrCustom", e.getQrCustom()); m.put("qrColor", e.getQrColor());
        m.put("barcodeData", e.getBarcodeData()); m.put("barcodeColor", e.getBarcodeColor()); m.put("barcodeShowText", e.isBarcodeShowText());
        // line / divider
        m.put("direction", e.getDirection());
        m.put("dividerOrientation", e.getDividerOrientation()); m.put("dividerStyle", e.getDividerStyle());
        // table
        if (e.getColumns() != null && !e.getColumns().isEmpty()) {
            List<Map<String, Object>> cols = new ArrayList<>();
            for (var c : e.getColumns()) cols.add(mapOf("key", c.getKey(), "label", c.getLabel(),
                    "width", c.getWidth(), "align", c.getAlign()));
            m.put("columns", cols);
        }
        m.put("headerBg", e.getHeaderBg()); m.put("headerColor", e.getHeaderColor());
        m.put("rowHeight", e.getRowHeight()); m.put("borderStyle", e.getBorderStyle());
        m.put("showZebra", e.isShowZebra()); m.put("tableBorderColor", e.getTableBorderColor());
        m.put("tableBorderWidth", e.getTableBorderWidth());
        m.put("rowBg", e.getRowBg()); m.put("rowColor", e.getRowColor()); m.put("zebraColor", e.getZebraColor());
        // shapes & geometry
        m.put("radius", e.getRadius()); m.put("radiusX", e.getRadiusX()); m.put("radiusY", e.getRadiusY());
        m.put("points", e.getPoints()); m.put("startAngle", e.getStartAngle());
        m.put("arcLength", e.getArcLength()); m.put("arcType", e.getArcType());
        m.put("pathData", e.getPathData()); m.put("starPoints", e.getStarPoints());
        m.put("innerRadius", e.getInnerRadius()); m.put("outerRadius", e.getOuterRadius());
        m.put("arrowShaftWidth", e.getArrowShaftWidth()); m.put("arrowHeadLength", e.getArrowHeadLength());
        m.put("arrowHeadWidth", e.getArrowHeadWidth()); m.put("arrowHeadStyle", e.getArrowHeadStyle());
        // watermark / svg / icon / group / component
        m.put("watermarkText", e.getWatermarkText()); m.put("watermarkOpacity", e.getWatermarkOpacity()); m.put("watermarkAngle", e.getWatermarkAngle());
        m.put("svgSource", e.getSvgSource()); m.put("iconName", e.getIconName());
        m.put("groupId", e.getGroupId()); m.put("componentType", e.getComponentType());
        // effects
        m.put("shadowEnabled", e.isShadowEnabled()); m.put("shadowColor", e.getShadowColor());
        m.put("shadowBlur", e.getShadowBlur()); m.put("shadowOffsetX", e.getShadowOffsetX());
        m.put("shadowOffsetY", e.getShadowOffsetY()); m.put("shadowOpacity", e.getShadowOpacity());
        m.put("blurEnabled", e.isBlurEnabled()); m.put("blurRadius", e.getBlurRadius());
        // conditions
        m.put("visibleCondition", e.getVisibleCondition());
        return m;
    }

    private static Map<String, Object> createTemplate(DataManager dm, Map<String, Object> args) throws Exception {
        String name = str(args, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        // Idempotent by name — never silently duplicate a template.
        Template existing = McpEnsure.findTemplateByName(dm, name);
        if (existing != null) {
            return mapOf("ok", true, "id", existing.getId(), "name", existing.getName(),
                    "elements", existing.getElements() == null ? 0 : existing.getElements().size(),
                    "existed", true, "matchedBy", "name",
                    "note", "Existing template returned unchanged; use update_template to modify it.");
        }
        List<String> warnings = new ArrayList<>();
        Template t = new Template();
        t.setId("tpl_mcp_" + UUID.randomUUID().toString().substring(0, 8));
        t.setName(name.trim());
        t.setPage(pageFor(strOr(args, "pageSize", "A4"), null));
        if (args.get("elements") instanceof List<?> els) t.setElements(elementsFrom(els, warnings));
        dm.templates().saveTemplate(t);
        Map<String, Object> resp = mapOf("ok", true, "id", t.getId(), "name", t.getName(),
                "elements", t.getElements() == null ? 0 : t.getElements().size(),
                "existed", false);
        if (!warnings.isEmpty()) resp.put("warnings", warnings);
        resp.put("next", "Render it with render_template_preview {id: '" + t.getId() + "'} and iterate before use.");
        return resp;
    }

    private static Map<String, Object> duplicateTemplate(DataManager dm, Map<String, Object> args) throws Exception {
        Template src = requireTemplate(dm, str(args, "id"));
        String newName = strOr(args, "newName", src.getName() + " (copy)");
        // Idempotent: if the target name is already taken, return that template.
        Template same = McpEnsure.findTemplateByName(dm, newName);
        if (same != null) {
            return mapOf("ok", true, "id", same.getId(), "name", same.getName(),
                    "existed", true, "matchedBy", "name",
                    "note", "A template named '" + newName + "' already exists; returning it unchanged.");
        }
        Template copy = new Template();
        copy.setId("tpl_mcp_" + UUID.randomUUID().toString().substring(0, 8));
        copy.setName(newName);
        copy.setPage(src.getPage());
        if (src.getElements() != null) {
            copy.setElements(new ArrayList<>(src.getElements()));
        }
        dm.templates().saveTemplate(copy);
        return mapOf("ok", true, "id", copy.getId(), "name", copy.getName(), "existed", false);
    }

    /**
     * render_template_preview — renders a saved template OR an unsaved draft
     * to a PNG using the real print engine (PdfExportService + RenderContext
     * with a realistic sample bill), returning a native MCP image content
     * block plus structured meta (page size, warnings for out-of-bounds
     * elements and unresolved bindings) so the AI can self-correct a design
     * without ever touching the database.
     */
    private static Object renderTemplatePreview(DataManager dm, Map<String, Object> args) throws Exception {
        Map<String, Object> draft = null;
        if (args.get("draft") instanceof Map<?, ?> d) draft = (Map<String, Object>) d;

        Template t;
        String mode;
        if (draft != null) {
            mode = "draft";
            t = new Template();
            t.setId("draft");
            t.setName(strOr(draft, "name", "Draft"));
            t.setPage(pageFor(strOr(draft, "pageSize", "A4"), null));
            t.setElements(new ArrayList<>());
            if (draft.get("elements") instanceof List<?> els) t.setElements(elementsFrom(els));
        } else {
            mode = "saved";
            t = requireTemplate(dm, str(args, "id"));
        }

        double dpi = dbl(args, "dpi", com.invoicestudio.service.TemplatePreviewService.DEFAULT_DPI);
        byte[] png = com.invoicestudio.service.TemplatePreviewService.renderPng(t, dm.getSettings(), dpi);
        double[] page = com.invoicestudio.service.TemplatePreviewService.effectivePageSize(t, dm.getSettings());

        int pxW = (int) Math.round(page[0] * Math.max(72, Math.min(300, dpi)) / 25.4);
        int pxH = (int) Math.round(page[1] * Math.max(72, Math.min(300, dpi)) / 25.4);

        List<String> warnings = designWarnings(dm, t, page[0], page[1]);
        Map<String, Object> meta = mapOf(
                "ok", true,
                "mode", mode,
                "templateName", t.getName(),
                "templateId", t.getId(),
                "pageSize", String.valueOf(t.getPage().getSizeName()),
                "widthMm", page[0], "heightMm", page[1],
                "pixelWidth", pxW, "pixelHeight", pxH,
                "dpi", Math.max(72, Math.min(300, dpi)),
                "elementCount", t.getElements() == null ? 0 : t.getElements().size(),
                "warnings", warnings,
                "note", warnings.isEmpty()
                        ? "Rendered with the same engine as PDF export on sample data. No warnings."
                        : "Rendered with the same engine as PDF export on sample data. Fix the warnings, re-render, then save/update.");
        return new McpImageResult("image/png", png, meta);
    }

    /** Out-of-bounds + unresolved-binding detection for a template at its effective page size. */
    private static List<String> designWarnings(DataManager dm, Template t, double widthMm, double heightMm) {
        List<String> warnings = new ArrayList<>();
        if (t.getElements() == null) return warnings;

        // Known binding keys: fixed RenderContext keys + custom variables.
        java.util.Set<String> known = new java.util.HashSet<>();
        try {
            com.invoicestudio.service.RenderContext probe = new com.invoicestudio.service.RenderContext(null, null, 0, 1, 1);
            known.addAll(probe.getValues().keySet());
        } catch (Exception ignored) {
            AppLog.debug(ignored);
            // fall through to custom variables only
        }
        try {
            for (VariableDef v : dm.variables().getAllVariables()) known.add(v.getKey());
        } catch (Exception ignored) {
            AppLog.debug(ignored); }

        java.util.regex.Pattern var = java.util.regex.Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");
        double margin = 0.5; // tiny tolerance for rounding
        for (var e : t.getElements()) {
            if (e.isHidden()) continue;
            if (e.getX() < -margin || e.getY() < -margin
                    || e.getX() + e.getW() > widthMm + margin
                    || e.getY() + e.getH() > heightMm + margin) {
                warnings.add("Element '" + (e.getName() == null || e.getName().isBlank() ? e.getId() : e.getName())
                        + "' (" + e.getType() + ") is outside the page: x=" + fmt(e.getX()) + " y=" + fmt(e.getY())
                        + " w=" + fmt(e.getW()) + " h=" + fmt(e.getH())
                        + " vs page " + fmt(widthMm) + "x" + fmt(heightMm) + " mm — move or resize it");
            }
            java.util.Set<String> used = new java.util.HashSet<>();
            if (e.getBinding() != null && !e.getBinding().isBlank()) used.add(e.getBinding().trim());
            if (e.getText() != null) {
                java.util.regex.Matcher m = var.matcher(e.getText());
                while (m.find()) used.add(m.group(1));
            }
            if (e.getBarcodeData() != null) {
                java.util.regex.Matcher m = var.matcher(e.getBarcodeData());
                while (m.find()) used.add(m.group(1));
            }
            for (String key : used) {
                if (!key.isBlank() && !known.contains(key)) {
                    warnings.add("Element '" + (e.getName() == null || e.getName().isBlank() ? e.getId() : e.getName())
                            + "' references unknown variable {{" + key + "}} — check list_variables or create it");
                }
            }
        }
        return warnings;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }

    @SuppressWarnings("unchecked")
    private static List<com.invoicestudio.model.TemplateElement> elementsFrom(List<?> raw) {
        List<String> ignored = new ArrayList<>();
        return elementsFrom(raw, ignored);
    }

    /**
     * Lossless element parsing — accepts the FULL design vocabulary
     * (see TEMPLATE_DESIGN_GUIDE.md). Every property the JSON carries is
     * applied; unknown element types degrade to TEXT and are REPORTED in
     * {@code warnings} (never silently dropped).
     */
    @SuppressWarnings("unchecked")
    private static List<com.invoicestudio.model.TemplateElement> elementsFrom(List<?> raw, List<String> warnings) {
        List<com.invoicestudio.model.TemplateElement> out = new ArrayList<>();
        for (Object o : raw) {
            Map<String, Object> m = (Map<String, Object>) o;
            com.invoicestudio.model.TemplateElement e = new com.invoicestudio.model.TemplateElement();
            e.setId("el_" + UUID.randomUUID().toString().substring(0, 8));
            if (str(m, "name") != null && !str(m, "name").isBlank()) e.setName(str(m, "name").trim());
            String type = strOr(m, "type", "TEXT").trim().toUpperCase(Locale.ROOT);
            try {
                e.setType(com.invoicestudio.model.ElementType.valueOf(type));
            } catch (IllegalArgumentException ex) {
                e.setType(com.invoicestudio.model.ElementType.TEXT);
                warnings.add("Element '" + strOr(m, "name", "unnamed") + "': unknown type '" + type
                        + "' downgraded to TEXT");
            }
            // position & layout
            e.setX(dbl(m, "x", 10.0)); e.setY(dbl(m, "y", 10.0));
            e.setW(dbl(m, "w", 40.0)); e.setH(dbl(m, "h", 10.0));
            e.setZIndex(intVal(m, "zIndex", 0)); e.setRotation(dbl(m, "rotation", 0.0));
            e.setLocked(boolVal(m, "locked", false)); e.setHidden(boolVal(m, "hidden", false));
            e.setRepeatOnPages(boolVal(m, "repeatOnPages", false));
            e.setHideWhenBlank(boolVal(m, "hideWhenBlank", false));
            e.setOpacity(dbl(m, "opacity", 1.0));
            e.setClipEnabled(boolVal(m, "clipEnabled", false)); e.setClipShape(strOr(m, "clipShape", "none"));
            e.setScaleX(dbl(m, "scaleX", 1.0)); e.setScaleY(dbl(m, "scaleY", 1.0));
            e.setFlipHorizontal(boolVal(m, "flipHorizontal", false)); e.setFlipVertical(boolVal(m, "flipVertical", false));
            // typography
            e.setText(str(m, "text")); e.setBinding(str(m, "binding"));
            if (str(m, "fontFamily") != null && !str(m, "fontFamily").isBlank()) e.setFontFamily(str(m, "fontFamily"));
            e.setFontSize(dbl(m, "fontSize", e.getFontSize()));
            e.setFontWeight(intVal(m, "fontWeight", e.getFontWeight()));
            e.setItalic(boolVal(m, "italic", false)); e.setUnderline(boolVal(m, "underline", false));
            e.setStrikethrough(boolVal(m, "strikethrough", false)); e.setUppercase(boolVal(m, "uppercase", false));
            if (str(m, "color") != null && !str(m, "color").isBlank()) e.setColor(str(m, "color"));
            if (str(m, "align") != null && !str(m, "align").isBlank()) e.setAlign(str(m, "align"));
            if (str(m, "vAlign") != null && !str(m, "vAlign").isBlank()) e.setVAlign(str(m, "vAlign"));
            e.setLineHeight(dbl(m, "lineHeight", e.getLineHeight()));
            e.setLineSpacing(dbl(m, "lineSpacing", e.getLineSpacing()));
            e.setLetterSpacing(dbl(m, "letterSpacing", e.getLetterSpacing()));
            e.setWordSpacing(dbl(m, "wordSpacing", e.getWordSpacing()));
            if (str(m, "textTransform") != null && !str(m, "textTransform").isBlank()) e.setTextTransform(str(m, "textTransform"));
            // box & border
            if (str(m, "bg") != null && !str(m, "bg").isBlank()) e.setBg(str(m, "bg"));
            e.setBorderWidth(dbl(m, "borderWidth", e.getBorderWidth()));
            if (str(m, "borderColor") != null && !str(m, "borderColor").isBlank()) e.setBorderColor(str(m, "borderColor"));
            e.setBorderRadius(dbl(m, "borderRadius", e.getBorderRadius()));
            e.setPadding(dbl(m, "padding", e.getPadding()));
            e.setBorderTop(boolVal(m, "borderTop", e.isBorderTop())); e.setBorderBottom(boolVal(m, "borderBottom", e.isBorderBottom()));
            e.setBorderLeft(boolVal(m, "borderLeft", e.isBorderLeft())); e.setBorderRight(boolVal(m, "borderRight", e.isBorderRight()));
            e.setIndividualBorders(boolVal(m, "individualBorders", e.isIndividualBorders()));
            if (m.containsKey("borderTopWidth")) e.setBorderTopWidth(doubleOrNull(m, "borderTopWidth"));
            if (str(m, "borderTopColor") != null && !str(m, "borderTopColor").isBlank()) e.setBorderTopColor(str(m, "borderTopColor"));
            if (str(m, "borderTopStyle") != null && !str(m, "borderTopStyle").isBlank()) e.setBorderTopStyle(str(m, "borderTopStyle"));
            if (m.containsKey("borderBottomWidth")) e.setBorderBottomWidth(doubleOrNull(m, "borderBottomWidth"));
            if (str(m, "borderBottomColor") != null && !str(m, "borderBottomColor").isBlank()) e.setBorderBottomColor(str(m, "borderBottomColor"));
            if (str(m, "borderBottomStyle") != null && !str(m, "borderBottomStyle").isBlank()) e.setBorderBottomStyle(str(m, "borderBottomStyle"));
            if (m.containsKey("borderLeftWidth")) e.setBorderLeftWidth(doubleOrNull(m, "borderLeftWidth"));
            if (str(m, "borderLeftColor") != null && !str(m, "borderLeftColor").isBlank()) e.setBorderLeftColor(str(m, "borderLeftColor"));
            if (str(m, "borderLeftStyle") != null && !str(m, "borderLeftStyle").isBlank()) e.setBorderLeftStyle(str(m, "borderLeftStyle"));
            if (m.containsKey("borderRightWidth")) e.setBorderRightWidth(doubleOrNull(m, "borderRightWidth"));
            if (str(m, "borderRightColor") != null && !str(m, "borderRightColor").isBlank()) e.setBorderRightColor(str(m, "borderRightColor"));
            if (str(m, "borderRightStyle") != null && !str(m, "borderRightStyle").isBlank()) e.setBorderRightStyle(str(m, "borderRightStyle"));
            // fill & gradient
            if (str(m, "fillType") != null && !str(m, "fillType").isBlank()) e.setFillType(str(m, "fillType"));
            if (str(m, "gradientStartColor") != null && !str(m, "gradientStartColor").isBlank()) e.setGradientStartColor(str(m, "gradientStartColor"));
            if (str(m, "gradientEndColor") != null && !str(m, "gradientEndColor").isBlank()) e.setGradientEndColor(str(m, "gradientEndColor"));
            e.setGradientAngle(dbl(m, "gradientAngle", e.getGradientAngle()));
            e.setGradientCenterX(dbl(m, "gradientCenterX", e.getGradientCenterX()));
            e.setGradientCenterY(dbl(m, "gradientCenterY", e.getGradientCenterY()));
            e.setGradientRadius(dbl(m, "gradientRadius", e.getGradientRadius()));
            // stroke & dash
            e.setStrokeEnabled(boolVal(m, "strokeEnabled", e.isStrokeEnabled()));
            if (str(m, "strokeType") != null && !str(m, "strokeType").isBlank()) e.setStrokeType(str(m, "strokeType"));
            if (str(m, "lineCap") != null && !str(m, "lineCap").isBlank()) e.setLineCap(str(m, "lineCap"));
            if (str(m, "lineJoin") != null && !str(m, "lineJoin").isBlank()) e.setLineJoin(str(m, "lineJoin"));
            if (m.containsKey("dashPattern")) e.setDashPattern(str(m, "dashPattern"));
            e.setDashOffset(dbl(m, "dashOffset", e.getDashOffset()));
            // image
            if (m.containsKey("src") && !str(m, "src").isBlank()) e.setSrc(str(m, "src"));
            if (str(m, "objectFit") != null && !str(m, "objectFit").isBlank()) e.setObjectFit(str(m, "objectFit"));
            e.setUseBusinessLogo(boolVal(m, "useBusinessLogo", e.isUseBusinessLogo()));
            // qr / barcode
            if (str(m, "qrSource") != null && !str(m, "qrSource").isBlank()) e.setQrSource(str(m, "qrSource"));
            if (m.containsKey("qrCustom")) e.setQrCustom(str(m, "qrCustom"));
            if (str(m, "qrColor") != null && !str(m, "qrColor").isBlank()) e.setQrColor(str(m, "qrColor"));
            if (m.containsKey("barcodeData") && !str(m, "barcodeData").isBlank()) e.setBarcodeData(str(m, "barcodeData"));
            if (str(m, "barcodeColor") != null && !str(m, "barcodeColor").isBlank()) e.setBarcodeColor(str(m, "barcodeColor"));
            e.setBarcodeShowText(boolVal(m, "barcodeShowText", e.isBarcodeShowText()));
            // line / divider
            if (str(m, "direction") != null && !str(m, "direction").isBlank()) e.setDirection(str(m, "direction"));
            if (str(m, "dividerOrientation") != null && !str(m, "dividerOrientation").isBlank()) e.setDividerOrientation(str(m, "dividerOrientation"));
            if (str(m, "dividerStyle") != null && !str(m, "dividerStyle").isBlank()) e.setDividerStyle(str(m, "dividerStyle"));
            // table
            if (m.get("columns") instanceof List<?> cols) {
                List<com.invoicestudio.model.TableColumn> parsed = new ArrayList<>();
                for (Object c : cols) {
                    if (!(c instanceof Map)) continue;
                    Map<String, Object> cm = (Map<String, Object>) c;
                    parsed.add(new com.invoicestudio.model.TableColumn(
                            strOr(cm, "key", "desc"), strOr(cm, "label", strOr(cm, "key", "Column")),
                            dbl(cm, "width", 20.0), strOr(cm, "align", "left")));
                }
                if (!parsed.isEmpty()) e.setColumns(parsed);
            }
            if (str(m, "headerBg") != null && !str(m, "headerBg").isBlank()) e.setHeaderBg(str(m, "headerBg"));
            if (str(m, "headerColor") != null && !str(m, "headerColor").isBlank()) e.setHeaderColor(str(m, "headerColor"));
            e.setRowHeight(dbl(m, "rowHeight", e.getRowHeight()));
            if (str(m, "borderStyle") != null && !str(m, "borderStyle").isBlank()) e.setBorderStyle(str(m, "borderStyle"));
            e.setShowZebra(boolVal(m, "showZebra", e.isShowZebra()));
            if (str(m, "tableBorderColor") != null && !str(m, "tableBorderColor").isBlank()) e.setTableBorderColor(str(m, "tableBorderColor"));
            e.setTableBorderWidth(dbl(m, "tableBorderWidth", e.getTableBorderWidth()));
            if (str(m, "rowBg") != null && !str(m, "rowBg").isBlank()) e.setRowBg(str(m, "rowBg"));
            if (str(m, "rowColor") != null && !str(m, "rowColor").isBlank()) e.setRowColor(str(m, "rowColor"));
            if (str(m, "zebraColor") != null && !str(m, "zebraColor").isBlank()) e.setZebraColor(str(m, "zebraColor"));
            // shapes & geometry
            e.setRadius(dbl(m, "radius", e.getRadius())); e.setRadiusX(dbl(m, "radiusX", e.getRadiusX())); e.setRadiusY(dbl(m, "radiusY", e.getRadiusY()));
            if (m.containsKey("points")) e.setPoints(str(m, "points"));
            e.setStartAngle(dbl(m, "startAngle", e.getStartAngle())); e.setArcLength(dbl(m, "arcLength", e.getArcLength()));
            if (str(m, "arcType") != null && !str(m, "arcType").isBlank()) e.setArcType(str(m, "arcType"));
            if (m.containsKey("pathData")) e.setPathData(str(m, "pathData"));
            e.setStarPoints(intVal(m, "starPoints", e.getStarPoints()));
            e.setInnerRadius(dbl(m, "innerRadius", e.getInnerRadius())); e.setOuterRadius(dbl(m, "outerRadius", e.getOuterRadius()));
            e.setArrowShaftWidth(dbl(m, "arrowShaftWidth", e.getArrowShaftWidth()));
            e.setArrowHeadLength(dbl(m, "arrowHeadLength", e.getArrowHeadLength()));
            e.setArrowHeadWidth(dbl(m, "arrowHeadWidth", e.getArrowHeadWidth()));
            if (str(m, "arrowHeadStyle") != null && !str(m, "arrowHeadStyle").isBlank()) e.setArrowHeadStyle(str(m, "arrowHeadStyle"));
            // watermark / svg / icon / group / component
            if (m.containsKey("watermarkText")) e.setWatermarkText(str(m, "watermarkText"));
            e.setWatermarkOpacity(dbl(m, "watermarkOpacity", e.getWatermarkOpacity()));
            e.setWatermarkAngle(dbl(m, "watermarkAngle", e.getWatermarkAngle()));
            if (m.containsKey("svgSource")) e.setSvgSource(str(m, "svgSource"));
            if (m.containsKey("iconName")) e.setIconName(str(m, "iconName"));
            if (m.containsKey("groupId")) e.setGroupId(str(m, "groupId"));
            if (m.containsKey("componentType")) e.setComponentType(str(m, "componentType"));
            // effects
            e.setShadowEnabled(boolVal(m, "shadowEnabled", e.isShadowEnabled()));
            if (str(m, "shadowColor") != null && !str(m, "shadowColor").isBlank()) e.setShadowColor(str(m, "shadowColor"));
            e.setShadowBlur(dbl(m, "shadowBlur", e.getShadowBlur()));
            e.setShadowOffsetX(dbl(m, "shadowOffsetX", e.getShadowOffsetX()));
            e.setShadowOffsetY(dbl(m, "shadowOffsetY", e.getShadowOffsetY()));
            e.setShadowOpacity(dbl(m, "shadowOpacity", e.getShadowOpacity()));
            e.setBlurEnabled(boolVal(m, "blurEnabled", e.isBlurEnabled()));
            e.setBlurRadius(dbl(m, "blurRadius", e.getBlurRadius()));
            // conditions
            if (m.containsKey("visibleCondition")) e.setVisibleCondition(str(m, "visibleCondition"));
            out.add(e);
        }
        return out;
    }

    private static Double doubleOrNull(Map<String, Object> m, String key) {
        if (m == null || !m.containsKey(key) || m.get(key) == null) return null;
        return dbl(m, key, 0.0);
    }

    private static com.invoicestudio.model.PageConfig pageFor(String sizeName, com.invoicestudio.model.PageConfig fallback) {
        com.invoicestudio.model.PageConfig pc = fallback != null ? fallback : new com.invoicestudio.model.PageConfig();
        if (sizeName != null && !sizeName.isBlank()) {
            String s = sizeName.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            try {
                com.invoicestudio.model.PageSizeName size = com.invoicestudio.model.PageSizeName.valueOf(s);
                pc.setSizeName(size);
                // Keep the page geometry in sync with the named size (thermal rolls get auto-height).
                pc.setWidth(size.getDefaultWidth());
                pc.setHeight(size.getDefaultHeight());
                pc.setAutoHeight(size == com.invoicestudio.model.PageSizeName.THERMAL_58
                        || size == com.invoicestudio.model.PageSizeName.THERMAL_80);
                if (pc.isAutoHeight() && pc.getMargin().getLeft() > 3) {
                    pc.setMargin(new com.invoicestudio.model.PageConfig.Margins(3, 2, 3, 2));
                }
            } catch (IllegalArgumentException ignored) {
                // keep default A4 for unrecognized names
            }
        }
        return pc;
    }

    private static Map<String, Object> createVariable(DataManager dm, Map<String, Object> args) throws Exception {
        String key = str(args, "key");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key is required");
        // Idempotent by key — an existing variable is returned unchanged, never overwritten.
        VariableDef dup = McpEnsure.findVariable(dm, key);
        if (dup != null) {
            return mapOf("ok", true, "key", dup.getKey(),
                    "existed", true, "matchedBy", "key",
                    "note", "Variable already exists; returned unchanged (this tool never overwrites).");
        }
        com.invoicestudio.model.VariableDef v = new com.invoicestudio.model.VariableDef(
                key.trim(), strOr(args, "label", key.trim()),
                strOr(args, "type", "text"), false);
        v.setScope(strOr(args, "scope", "fixed"));
        v.setDefaultValue(str(args, "defaultValue"));
        dm.variables().saveVariable(v);
        return mapOf("ok", true, "key", v.getKey(), "existed", false);
    }

    private static Map<String, Object> createTransport(DataManager dm, Map<String, Object> args) throws Exception {
        String name = str(args, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        // Idempotent by name — never silently duplicate a transport entry.
        Transport dup = McpEnsure.findTransportByName(dm, name);
        if (dup != null) {
            return mapOf("ok", true, "id", dup.getId(), "name", dup.getName(),
                    "existed", true, "matchedBy", "name",
                    "note", "Existing transport returned unchanged.");
        }
        com.invoicestudio.model.Transport tr = new com.invoicestudio.model.Transport(
                "trn_mcp_" + UUID.randomUUID().toString().substring(0, 8),
                name.trim(), strOr(args, "phone", ""), strOr(args, "vehicleNumber", ""));
        dm.saveTransport(tr);
        return mapOf("ok", true, "id", tr.getId(), "name", tr.getName(), "existed", false);
    }

    private static com.invoicestudio.model.Transport requireTransport(DataManager dm, String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Transport id is required");
        com.invoicestudio.model.Transport tr = dm.getTransportById(id.trim());
        if (tr == null) throw new IllegalArgumentException("Transport not found: " + id);
        return tr;
    }

    private static Map<String, Object> createCategory(DataManager dm, Map<String, Object> args) throws Exception {
        String name = str(args, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        // Check-then-create (idempotent by case-insensitive name, race-safe,
        // unique DB index backstop) — the exact same path items use.
        McpEnsure.Outcome out = McpEnsure.ensureCategory(dm, null, name);
        Map<String, Object> res = mapOf("ok", true, "id", out.id, "name", out.name,
                "existed", !out.created);
        if (out.created) res.put("autoCreated", List.of(out.asMap("category")));
        else res.put("matchedBy", out.matchedBy);
        return res;
    }

    private static Map<String, Object> createBackup(DataManager dm, Map<String, Object> args) throws Exception {
        java.io.File dest;
        String p = str(args, "path");
        if (p != null && !p.isBlank()) {
            dest = new java.io.File(p);
        } else {
            java.io.File dir = new java.io.File(com.invoicestudio.AppDirs.dataDir().toFile(), "backups");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create backups dir");
            dest = new java.io.File(dir, "mcp-backup-" + LocalDate.now() + ".json");
        }
        new com.invoicestudio.service.BackupRestoreService(dm.getDb()).exportBackup(dest);
        return mapOf("ok", true, "path", dest.getAbsolutePath(),
                "sizeBytes", dest.length());
    }

    /** Identity of whose books the server is operating on — never returns secrets. */
    private static Map<String, Object> whoami() {
        com.invoicestudio.model.UserSession s = com.invoicestudio.service.AuthSessionManager.getActiveSession();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("serverAccount", McpServer.SERVER_NAME);
        if (s == null) {
            m.put("loggedIn", false);
            m.put("note", "No Firebase session in the app; MCP tools operate on no user's books.");
            return m;
        }
        m.put("loggedIn", true);
        m.put("userId", s.getUserId());
        m.put("email", s.getEmail());
        m.put("displayName", s.getDisplayName());
        m.put("sessionExpiresAt", java.time.Instant.ofEpochMilli(s.getExpiresAtMillis()).toString());
        m.put("authModel", "You (the AI client) authenticate to THIS server with the MCP bearer token "
                + "shown in Settings → MCP Server. The app itself is logged in to Firebase with the "
                + "account above; every MCP operation is scoped to that account's data. "
                + "The Firebase password/token never leaves the app.");
        return m;
    }

    private static Map<String, Object> recordExpense(DataManager dm, Map<String, Object> args) throws Exception {
        double amount = dbl(args, "amount", 0.0);
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        Expense e = new Expense("exp_mcp_" + UUID.randomUUID().toString().substring(0, 8),
                strOr(args, "date", LocalDate.now().toString()),
                strOr(args, "category", "Miscellaneous"),
                strOr(args, "description", ""),
                amount,
                strOr(args, "paymentMode", "Cash"));
        e.setPayee(strOr(args, "payee", ""));
        dm.saveExpense(e);
        return mapOf("ok", true, "id", e.getId(),
                "head", Expense.isDirect(e.getCategory()) ? "DIRECT (Trading A/c)" : "INDIRECT (P&L)");
    }

    // ------------------------------------------------------------------
    // Expense accounts
    // ------------------------------------------------------------------

    private static List<Map<String, Object>> expenseAccountsMap(DataManager dm, boolean includeArchived) {
        Map<String, DataManager.AccountUsage> usage = dm.expenseAccountUsage();
        List<Map<String, Object>> out = new ArrayList<>();
        for (com.invoicestudio.model.ExpenseAccount a : dm.getAllExpenseAccounts()) {
            if (a.isArchived() && !includeArchived) continue;
            DataManager.AccountUsage u = usage.get(a.getName().toLowerCase());
            Map<String, Object> m = mapOf("id", a.getId(), "name", a.getName(),
                    "archived", a.isArchived(),
                    "vouchers", u != null ? u.vouchers : 0,
                    "totalSpent", u != null ? Math.round(u.total * 100.0) / 100.0 : 0.0,
                    "lastUsed", u != null && !u.lastDate.isEmpty() ? u.lastDate : null);
            out.add(m);
        }
        out.sort((x, y) -> Integer.compare((int) y.get("vouchers"), (int) x.get("vouchers")));
        return out;
    }

    private static Map<String, Object> createExpenseAccount(DataManager dm, Map<String, Object> args) {
        String name = str(args, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        com.invoicestudio.model.ExpenseAccount existing = dm.expenseAccounts().findByName(name.trim());
        if (existing != null) {
            return mapOf("ok", true, "id", existing.getId(), "name", existing.getName(), "created", false);
        }
        com.invoicestudio.model.ExpenseAccount acc = new com.invoicestudio.model.ExpenseAccount(
                ExpenseAccountService.newId(), name.trim());
        acc.setNotes(strOr(args, "notes", ""));
        acc.setDefaultPaymentMode(strOr(args, "defaultPaymentMode", ""));
        dm.expenseAccounts().saveAccount(acc);
        dm.invalidateExpenseAccounts();
        return mapOf("ok", true, "id", acc.getId(), "name", acc.getName(), "created", true);
    }

    private static Map<String, Object> renameExpenseAccount(DataManager dm, Map<String, Object> args) {
        String id = str(args, "id");
        String newName = str(args, "newName");
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("newName is required");
        com.invoicestudio.model.ExpenseAccount acc = dm.expenseAccounts().getAccountById(id);
        if (acc == null) throw new IllegalArgumentException("No expense account with id " + id);
        com.invoicestudio.model.ExpenseAccount clash = dm.expenseAccounts().findByName(newName.trim());
        if (clash != null && !clash.getId().equals(acc.getId())) {
            throw new IllegalArgumentException("An expense account named '" + newName.trim() + "' already exists");
        }
        int updated = ExpenseAccountService.renameWithPropagation(dm, acc, newName);
        return mapOf("ok", true, "id", acc.getId(), "name", acc.getName(), "vouchersUpdated", updated);
        // invalidate is done inside renameWithPropagation
    }

    private static Map<String, Object> expenseAccountReport(DataManager dm, Map<String, Object> args) {
        String account = strOr(args, "account", "");
        String category = strOr(args, "category", "");
        String from = strOr(args, "from", "");
        String to = strOr(args, "to", "");
        ExpenseAnalytics.Report r = ExpenseAnalytics.build(dm.getAllExpenses(),
                !account.isBlank() ? "Account" : (!category.isBlank() ? "Category" : "All"),
                !account.isBlank() ? account : category,
                account, category, from, to);
        List<Map<String, Object>> months = new ArrayList<>();
        for (ExpenseAnalytics.Bucket b : r.byMonth()) months.add(bucketMap(b));
        List<Map<String, Object>> cats = new ArrayList<>();
        for (ExpenseAnalytics.Bucket b : r.byCategory()) cats.add(bucketMap(b));
        List<Map<String, Object>> accs = new ArrayList<>();
        for (ExpenseAnalytics.Bucket b : r.byAccount()) accs.add(bucketMap(b));
        return mapOf("dimension", r.dimension(), "filter", r.filterName(),
                "from", r.from(), "to", r.to(),
                "total", Math.round(r.total() * 100.0) / 100.0,
                "vouchers", r.voucherCount(), "average", Math.round(r.average() * 100.0) / 100.0,
                "byMonth", months, "byCategory", cats, "byAccount", accs);
    }

    private static Map<String, Object> bucketMap(ExpenseAnalytics.Bucket b) {
        return mapOf("key", b.key(), "vouchers", b.vouchers(),
                "total", Math.round(b.total() * 100.0) / 100.0);
    }

    // ------------------------------------------------------------------
    // List/map projections
    // ------------------------------------------------------------------

    private static List<Map<String, Object>> buyersMap(DataManager dm, String query, int limit) {
        return McpProjections.buyersMap(dm, query, limit);
    }

    private static List<Map<String, Object>> suppliersMap(DataManager dm, String query, int limit) {
        return McpProjections.suppliersMap(dm, query, limit);
    }

    private static List<Map<String, Object>> itemsMap(DataManager dm, String query, int limit) {
        return McpProjections.itemsMap(dm, query, limit);
    }

    private static List<Map<String, Object>> templatesMap(DataManager dm) {
        return McpProjections.templatesMap(dm);
    }

    private static List<Map<String, Object>> billsMap(DataManager dm, String query, String status, int limit) {
        return McpProjections.billsMap(dm, query, status, limit);
    }

    private static Map<String, Object> billSummary(Bill b) {
        return McpProjections.billSummary(b);
    }

    private static Map<String, Object> billFull(Bill b) {
        return McpProjections.billFull(b);
    }

    private static List<Map<String, Object>> purchasesMap(DataManager dm, String query, int limit) {
        return McpProjections.purchasesMap(dm, query, limit);
    }

    private static Map<String, Object> expenseMap(Expense e) {
        return McpProjections.expenseMap(e);
    }

    private static Map<String, Object> transactionMap(Transaction t) {
        return McpProjections.transactionMap(t);
    }

    private static Map<String, Object> totalsMap(BillTotals t) {
        return McpProjections.totalsMap(t);
    }

    private static Map<String, Object> settingsMap(Settings s) {
        return McpProjections.settingsMap(s);
    }

    private static List<Map<String, Object>> stockReport(DataManager dm) {
        return McpProjections.stockReport(dm);
    }

    private static List<Map<String, Object>> profitabilityReport(DataManager dm) {
        return McpProjections.profitabilityReport(dm);
    }

    private static Map<String, Object> financialSummary(DataManager dm, Map<String, Object> args) {
        return McpProjections.financialSummary(dm, args);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Bill requireBill(DataManager dm, String id) {
        Bill b = dm.bills().getBillById(id);
        if (b == null) throw new IllegalArgumentException("Bill not found: " + id);
        return b;
    }

    private static Buyer requireBuyer(DataManager dm, String id) {
        Buyer b = dm.buyers().getBuyerById(id);
        if (b == null) throw new IllegalArgumentException("Buyer not found: " + id);
        return b;
    }

    private static Supplier requireSupplier(DataManager dm, String id) {
        Supplier s = dm.suppliers().getSupplierById(id);
        if (s == null) throw new IllegalArgumentException("Supplier not found: " + id);
        return s;
    }

    private static ItemRecord requireItem(DataManager dm, String id) {
        ItemRecord it = dm.items().getItemById(id);
        if (it == null) throw new IllegalArgumentException("Item not found: " + id);
        return it;
    }

    private static ItemCategory requireCategory(DataManager dm, String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Category id is required");
        ItemCategory c = dm.categories().getCategoryById(id.trim());
        if (c == null) throw new IllegalArgumentException("Category not found: " + id);
        return c;
    }

    // ------------------------------------------------------------------
    // Batch invoice export (History → Export PDFs parity)
    // ------------------------------------------------------------------

    private static Map<String, Object> exportBillsPdf(DataManager dm, Map<String, Object> args) {
        List<Bill> selected = new ArrayList<>();
        if (args.get("ids") instanceof List<?> ids && !ids.isEmpty()) {
            for (Object o : ids) selected.add(requireBill(dm, String.valueOf(o)));
        } else if (boolVal(args, "exportBatch", false)) {
            selected.addAll(dm.getAllBills());
        } else {
            String from = strOr(args, "from", "");
            String to = strOr(args, "to", "");
            String query = strOr(args, "query", "");
            String status = strOr(args, "status", "");
            int limit = intVal(args, "limit", 500);
            for (Bill b : dm.getAllBills()) {
                if (!from.isBlank() && (b.getDate() == null || b.getDate().compareTo(from) < 0)) continue;
                if (!to.isBlank() && (b.getDate() == null || b.getDate().compareTo(to) > 0)) continue;
                if (!status.isBlank() && !b.getStatus().name().equalsIgnoreCase(status.trim())) continue;
                if (!query.isBlank() && !matches(query, b.getBillNo(), b.getBuyerName())) continue;
                selected.add(b);
                if (selected.size() >= limit) break;
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException(
                    "No invoices match the selection (use from/to/query/status, ids, or exportBatch=true)");
        }

        Template template = dm.templates().getAllTemplates().stream()
                .filter(t -> !t.isLabelMode()).findFirst().orElse(null);
        if (template == null) {
            throw new IllegalStateException("No bill template available to render invoices");
        }

        java.io.File dir;
        String dirArg = strOr(args, "dir", "");
        if (!dirArg.isBlank()) {
            dir = new java.io.File(dirArg);
        } else {
            dir = new java.io.File(new java.io.File(
                    com.invoicestudio.AppDirs.dataDir().toFile(), "exports"), "pdf-" + LocalDate.now());
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create output folder: " + dir);
        }

        long t0 = System.nanoTime();
        BillingService.BatchResult r = BillingService.exportBatchPdf(selected, template, dm.getSettings(), dir, 1);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        return mapOf("ok", r.failed() == 0,
                "exported", r.ok(), "failed", r.failed(),
                "failures", r.failures(), "folder", dir.getAbsolutePath(),
                "template", template.getName(), "ms", ms);
    }

    // ------------------------------------------------------------------
    // Keyboard shortcuts (Settings → Shortcuts parity)
    // ------------------------------------------------------------------

    private static List<Map<String, Object>> shortcutsMap() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ShortcutManager.ShortcutAction a : ShortcutManager.actions()) {
            String current = ShortcutManager.comboOf(a.id());
            out.add(mapOf("actionId", a.id(), "group", a.group(), "label", a.label(),
                    "binding", current == null ? "" : current,
                    "defaultBinding", a.defaultCombo(),
                    "customized", !String.valueOf(a.defaultCombo()).equals(current)));
        }
        return out;
    }

    private static void rebindShortcut(Map<String, Object> args) {
        String actionId = str(args, "actionId");
        ShortcutManager.ShortcutAction a = ShortcutManager.action(actionId);
        if (a == null) {
            throw new IllegalArgumentException("Unknown shortcut actionId: " + actionId
                    + " — call list_shortcuts for valid ids");
        }
        String combo = strOr(args, "combo", "");
        ShortcutManager.Validation v = ShortcutManager.validate(actionId, combo);
        if (v != ShortcutManager.Validation.OK) {
            throw new IllegalArgumentException(ShortcutManager.validationMessage(v, combo));
        }
        ShortcutManager.bind(actionId, combo);
    }

    private static void resetShortcut(Map<String, Object> args) {
        if (boolVal(args, "resetAll", false)) {
            ShortcutManager.resetAll();
            return;
        }
        String actionId = str(args, "actionId");
        if (ShortcutManager.action(actionId) == null) {
            throw new IllegalArgumentException("Unknown shortcut actionId: " + actionId);
        }
        ShortcutManager.resetToDefault(actionId);
    }

    private static String shortcutLabel(String actionId) {
        ShortcutManager.ShortcutAction a = ShortcutManager.action(actionId);
        return a != null ? a.label() : actionId;
    }

    // ------------------------------------------------------------------
    // Label (barcode) bulk print
    // ------------------------------------------------------------------

    private static Map<String, Object> labelPrintStateMap(DataManager dm, String templateId) {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId is required");
        }
        requireTemplate(dm, templateId);
        BulkPrintStateStore.TemplateState st = BulkPrintStateStore.load(templateId);
        if (st == null) {
            return mapOf("templateId", templateId, "remembered", false,
                    "rows", List.of(), "printer", "");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BulkPrintStateStore.Row r : st.rows()) {
            rows.add(mapOf("values", r.values(), "copies", r.copies()));
        }
        return mapOf("templateId", templateId, "remembered", true,
                "rows", rows, "printer", st.printer() == null ? "" : st.printer());
    }

    private static Map<String, Object> printLabels(DataManager dm, Map<String, Object> args) {
        Template t = requireTemplate(dm, str(args, "templateId"));
        if (!t.isLabelMode()) {
            throw new IllegalArgumentException("Template '" + t.getName()
                    + "' is not in Barcode Mode — switch it in the Template Designer first");
        }
        boolean test = boolVal(args, "test", false);

        List<String> variableOrder = new ArrayList<>();
        if (args.get("variableOrder") instanceof List<?> vo) {
            for (Object o : vo) variableOrder.add(String.valueOf(o));
        }
        if (variableOrder.isEmpty() && t.getElements() != null) {
            for (TemplateElement e : t.getElements()) {
                String b = e.getBinding();
                if (b != null && !b.isBlank() && !variableOrder.contains(b.trim())) variableOrder.add(b.trim());
            }
        }

        List<LabelGeometryService.PrintLine> lines = new ArrayList<>();
        if (test) {
            lines.add(new LabelGeometryService.PrintLine(new java.util.LinkedHashMap<>(), 1));
        } else {
            if (!(args.get("lines") instanceof List<?> raw) || raw.isEmpty()) {
                throw new IllegalArgumentException("lines is required (or pass test=true for one free test label)");
            }
            int idx = 0;
            for (Object o : raw) {
                if (!(o instanceof Map<?, ?> row)) {
                    throw new IllegalArgumentException("lines[" + idx + "] must be an object {variableValues, copies}");
                }
                Map<String, String> values = new java.util.LinkedHashMap<>();
                if (row.get("variableValues") instanceof Map<?, ?> vm) {
                    for (Map.Entry<?, ?> en : vm.entrySet()) {
                        values.put(String.valueOf(en.getKey()), String.valueOf(en.getValue()));
                    }
                }
                int copies = row.get("copies") instanceof Number n ? n.intValue() : 1;
                lines.add(new LabelGeometryService.PrintLine(values, copies));
                idx++;
            }
        }

        javafx.print.Printer printer = null;
        String printerName = strOr(args, "printer", "");
        if (!printerName.isBlank()) {
            printer = javafx.print.Printer.getAllPrinters().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(printerName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Printer not found: " + printerName));
        }

        LabelPrintService.PrintResult r;
        try {
            r = LabelPrintService.printLabelsQueued(t, dm.getSettings(), lines, variableOrder,
                    printer, test, null);
        } catch (Exception | LinkageError e) {
            return mapOf("ok", false, "error", "Printing failed: " + e.getMessage(),
                    "pages", 0, "labels", 0);
        }
        return mapOf("ok", r.success(), "message", r.message(),
                "pages", r.pages(), "labels", r.labels());
    }

    // ------------------------------------------------------------------
    // Expense account lifecycle (Accounts dialog parity)
    // ------------------------------------------------------------------

    private static void updateExpenseAccount(DataManager dm, Map<String, Object> args) {
        String id = str(args, "id");
        com.invoicestudio.model.ExpenseAccount acc = dm.expenseAccounts().getAccountById(id);
        if (acc == null) throw new IllegalArgumentException("No expense account with id " + id);
        if (args.containsKey("name") && !str(args, "name").isBlank()
                && !str(args, "name").trim().equalsIgnoreCase(acc.getName())) {
            String newName = str(args, "name").trim();
            com.invoicestudio.model.ExpenseAccount clash = dm.expenseAccounts().findByName(newName);
            if (clash != null && !clash.getId().equals(acc.getId())) {
                throw new IllegalArgumentException("An expense account named '" + newName + "' already exists");
            }
            ExpenseAccountService.renameWithPropagation(dm, acc, newName);
        }
        if (args.containsKey("notes")) acc.setNotes(str(args, "notes"));
        if (args.containsKey("defaultPaymentMode")) acc.setDefaultPaymentMode(str(args, "defaultPaymentMode"));
        if (args.containsKey("archived")) acc.setArchived(boolVal(args, "archived", acc.isArchived()));
        dm.expenseAccounts().saveAccount(acc);
        dm.invalidateExpenseAccounts();
    }

    private static void deleteExpenseAccount(DataManager dm, String id) {
        com.invoicestudio.model.ExpenseAccount acc = dm.expenseAccounts().getAccountById(id);
        if (acc == null) throw new IllegalArgumentException("No expense account with id " + id);
        int inUse = (int) dm.getAllExpenses().stream()
                .filter(e -> acc.getName().equalsIgnoreCase(e.getPayee()))
                .count();
        if (inUse > 0) {
            throw new IllegalArgumentException("Account '" + acc.getName() + "' still has " + inUse
                    + " expense voucher(s). Rename the account instead, or reassign those vouchers first.");
        }
        dm.expenseAccounts().deleteAccount(id);
        dm.invalidateExpenseAccounts();
    }

    private static boolean matches(String query, String... fields) {
        return McpArgs.matches(query, fields);
    }

    private static PaymentMethod parseMethod(String mode) {
        return McpArgs.parseMethod(mode);
    }

    private static Map<String, Object> obj(Object... kv) {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = String.valueOf(kv[i]);
            String desc = String.valueOf(kv[i + 1]);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("type", key.equals("items") ? "array" : key.equals("limit") ? "integer" : "string");
            p.put("description", desc);
            props.put(key, p);
        }
        schema.put("properties", props);
        return schema;
    }

    private static String str(String s) { return s; }
    private static String num(String s) { return s; }
    private static String bool(String s) { return s; }
    private static String arr(String s) { return s; }

    private static String str(Map<String, Object> m, String key) {
        return McpArgs.str(m, key);
    }

    private static String strOr(Map<String, Object> m, String key, String def) {
        return McpArgs.strOr(m, key, def);
    }

    private static double dbl(Map<String, Object> m, String key, double def) {
        return McpArgs.dbl(m, key, def);
    }

    private static int intVal(Map<String, Object> m, String key, int def) {
        return McpArgs.intVal(m, key, def);
    }

    private static boolean boolVal(Map<String, Object> m, String key, boolean def) {
        return McpArgs.boolVal(m, key, def);
    }

    private static Map<String, Object> mapOf(Object... kv) {
        return McpArgs.mapOf(kv);
    }
}
