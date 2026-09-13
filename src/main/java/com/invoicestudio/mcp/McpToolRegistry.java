package com.invoicestudio.mcp;

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
import com.invoicestudio.service.BillingService;
import com.invoicestudio.service.FinancialService;
import com.invoicestudio.service.PurchaseService;
import com.invoicestudio.ui.DataManager;

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
        t.add(new ToolDef("create_buyer", "Create a buyer/customer.",
                obj(
                        "name", str("Buyer/firm name (required)"),
                        "phone", str("Phone"),
                        "gst", str("15-char GSTIN"),
                        "address", str("Address"),
                        "state", str("State name"),
                        "stateCode", str("2-digit state code (decides IGST vs CGST/SGST)")), true, false));
        t.add(new ToolDef("update_buyer", "Update an existing buyer. Requires user confirmation.",
                obj("id", str("Buyer id"),
                        "name", str("New name"),
                        "phone", str("New phone"),
                        "gst", str("New GSTIN"),
                        "address", str("New address"),
                        "state", str("New state"),
                        "stateCode", str("New state code")), true, true));
        t.add(new ToolDef("delete_buyer", "Delete a buyer permanently. Requires user confirmation.",
                obj("id", str("Buyer id")), true, true));

        // --- Suppliers ---
        t.add(new ToolDef("list_suppliers", "List all sellers/suppliers (Sundry Creditors) with balances.",
                obj(
                        "query", str("Optional search text"),
                        "limit", num("Max rows (default 100)")), false, false));
        t.add(new ToolDef("create_supplier", "Create a seller/supplier.",
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
        t.add(new ToolDef("update_supplier", "Update a supplier. Requires user confirmation.",
                obj("id", str("Supplier id"),
                        "name", str("New name"),
                        "phone", str("New phone"),
                        "gst", str("New GSTIN"),
                        "state", str("New state"),
                        "city", str("New city")), true, true));
        t.add(new ToolDef("delete_supplier", "Delete a supplier permanently. Requires user confirmation.",
                obj("id", str("Supplier id")), true, true));

        // --- Items ---
        t.add(new ToolDef("list_items", "List catalog items with rates, GST %, cost, live stock and reorder level.",
                obj(
                        "query", str("Optional search text"),
                        "limit", num("Max rows (default 100)")), false, false));
        t.add(new ToolDef("create_item", "Create a catalog item.",
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
        t.add(new ToolDef("update_item", "Update a catalog item (rates, reorder level etc; stock itself is ledger-managed). Requires user confirmation.",
                obj("id", str("Item id"),
                        "name", str("New name"),
                        "rate", num("New selling rate"),
                        "gst", num("New GST %"),
                        "purchaseRate", num("New cost rate"),
                        "reorderLevel", num("New reorder level")), true, true));
        t.add(new ToolDef("delete_item", "Delete a catalog item permanently. Requires user confirmation.",
                obj("id", str("Item id")), true, true));

        // --- Read-only masters ---
        t.add(new ToolDef("list_categories", "List item categories.", obj(), false, false));
        t.add(new ToolDef("list_transports", "List transports (logistics partners).", obj(), false, false));
        t.add(new ToolDef("list_templates", "List print templates with element counts and page sizes.", obj(), false, false));
        t.add(new ToolDef("get_template", "Get one print template in full: page size and every positioned element (type, x/y/w/h in mm, text, variable binding). Read this + get_app_guide to learn how templates work.",
                obj("id", str("Template id")), false, false));
        t.add(new ToolDef("create_template", "Create a print template. Elements: [{type: TEXT|TABLE|LINE|RECT|QRCODE..., name, x, y, w, h (mm), text?, binding?}] — binding binds a Field to a variable (see list_variables).",
                obj("name", str("Template name"),
                        "pageSize", str("A4 | A5 | THERMAL_80 | THERMAL_58 (default A4)"),
                        "elements", arr("Element list (can be empty and edited later)")), true, false));
        t.add(new ToolDef("update_template", "Update a template's name, page size or replace its elements. Requires user confirmation.",
                obj("id", str("Template id"),
                        "name", str("New name"),
                        "pageSize", str("New page size"),
                        "elements", arr("Replacement element list")), true, true));
        t.add(new ToolDef("duplicate_template", "Duplicate an existing template under a new name (great starting point: duplicate a preset then edit).",
                obj("id", str("Template to copy"),
                        "newName", str("Name for the copy")), true, false));
        t.add(new ToolDef("delete_template", "Delete a print template permanently. Requires user confirmation.",
                obj("id", str("Template id")), true, true));
        t.add(new ToolDef("list_variables", "List template variables (fixed app variables + custom) usable as element bindings.", obj(), false, false));
        t.add(new ToolDef("create_variable", "Create a custom template variable.",
                obj("key", str("Snake_case key used in bindings"),
                        "label", str("Human label"),
                        "type", str("text | number | date"),
                        "defaultValue", str("Default value"),
                        "scope", str("fixed | table")), true, false));
        t.add(new ToolDef("delete_variable", "Delete a custom variable. Requires user confirmation.",
                obj("key", str("Variable key")), true, true));
        t.add(new ToolDef("create_transport", "Add a transport (logistics) partner.",
                obj("name", str("Transport name"),
                        "phone", str("Phone"),
                        "vehicleNumber", str("Vehicle number")), true, false));
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
        t.add(new ToolDef("create_bill", "Create a tax invoice (same as + New Bill).",
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
        t.add(new ToolDef("create_purchase", "Record a purchase bill from a supplier (stock IN + ITC).",
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
        t.add(new ToolDef("record_expense", "Record an expense voucher (direct heads hit the Trading Account, indirect hit P&L).",
                obj(
                        "category", str("e.g. Freight Inward (direct), Office Rent, Salaries, Electricity, Marketing, Bank Charges (indirect)"),
                        "amount", num("Amount"),
                        "date", str("ISO date (default today)"),
                        "description", str("Narration"),
                        "paymentMode", str("Cash | Bank | NEFT | Cheque | UPI"),
                        "payee", str("Paid to")), true, false));
        t.add(new ToolDef("delete_expense", "Delete an expense voucher. Requires user confirmation.",
                obj("id", str("Expense id")), true, true));
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
                if (args.containsKey("city")) s.setCity(str(args, "city"));
                dm.suppliers().saveSupplier(s);
            });
            case "delete_supplier": return confirmable("delete_supplier", args, () -> dm.suppliers().deleteSupplier(str(args, "id")));

            // Items
            case "list_items": return itemsMap(dm, str(args, "query"), intVal(args, "limit", 100));
            case "create_item": return createItem(dm, args);
            case "update_item": return confirmable("update_item", args, () -> {
                ItemRecord it = requireItem(dm, str(args, "id"));
                if (args.containsKey("name") && !str(args, "name").isBlank()) it.setName(str(args, "name").trim());
                if (args.containsKey("rate")) it.setRate(dbl(args, "rate", it.getRate()));
                if (args.containsKey("gst")) it.setGst(dbl(args, "gst", it.getGst()));
                if (args.containsKey("purchaseRate")) it.setPurchaseRate(dbl(args, "purchaseRate", it.getPurchaseRate()));
                if (args.containsKey("reorderLevel")) it.setReorderLevel(dbl(args, "reorderLevel", it.getReorderLevel()));
                dm.items().saveItem(it);
            });
            case "delete_item": return confirmable("delete_item", args, () -> dm.items().deleteItem(str(args, "id")));

            // Read-only masters
            case "list_categories": return dm.getAllCategories().stream().map(c -> mapOf(
                    "id", c.getId(), "name", c.getName())).collect(java.util.stream.Collectors.toList());
            case "list_transports": return dm.getAllTransports().stream().map(tr -> mapOf(
                    "id", tr.getId(), "name", tr.getName(), "phone", tr.getPhone(),
                    "vehicleNumber", tr.getVehicleNumber())).collect(java.util.stream.Collectors.toList());
            case "list_templates": return templatesMap(dm);
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
        switch (tool) {
            case "delete_bill": return "Delete invoice " + str(args, "id") + " (stock rows reversed)";
            case "delete_purchase": return "Delete purchase bill " + str(args, "id") + " (stock rows reversed)";
            case "delete_buyer": return "Permanently delete buyer " + str(args, "id");
            case "delete_supplier": return "Permanently delete supplier " + str(args, "id");
            case "delete_item": return "Permanently delete catalog item " + str(args, "id");
            case "delete_expense": return "Delete expense " + str(args, "id");
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
        Buyer b = new Buyer("byr_mcp_" + UUID.randomUUID().toString().substring(0, 8),
                name.trim(), strOr(args, "address", ""), strOr(args, "gst", "").toUpperCase(Locale.ROOT),
                strOr(args, "phone", ""), strOr(args, "state", ""));
        b.setStateCode(strOr(args, "stateCode", ""));
        dm.buyers().saveBuyer(b);
        return mapOf("ok", true, "id", b.getId(), "name", b.getName());
    }

    private static Map<String, Object> createSupplier(DataManager dm, Map<String, Object> args) throws Exception {
        String name = str(args, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        Supplier s = new Supplier();
        s.setId("sup_mcp_" + UUID.randomUUID().toString().substring(0, 8));
        s.setName(name.trim());
        s.setPhone(strOr(args, "phone", ""));
        s.setGst(strOr(args, "gst", "").toUpperCase(Locale.ROOT));
        s.setState(strOr(args, "state", ""));
        s.setStateCode(strOr(args, "stateCode", ""));
        s.setCity(strOr(args, "city", ""));
        s.setAddress(strOr(args, "address", ""));
        s.setOpeningBalance(dbl(args, "openingBalance", 0.0));
        s.setCreditPeriodDays((int) dbl(args, "creditPeriodDays", 0));
        dm.suppliers().saveSupplier(s);
        return mapOf("ok", true, "id", s.getId(), "name", s.getName());
    }

    private static Map<String, Object> createItem(DataManager dm, Map<String, Object> args) throws Exception {
        String name = str(args, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
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
        it.setCategoryId(strOr(args, "categoryId", ""));
        it.setCategoryName(strOr(args, "categoryName", ""));
        dm.items().saveItem(it);
        return mapOf("ok", true, "id", it.getId(), "name", it.getName());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> createBill(DataManager dm, Map<String, Object> args) throws Exception {
        Settings st = dm.getSettings();
        Bill bill = new Bill();
        bill.setId("bill_mcp_" + UUID.randomUUID().toString().substring(0, 8));
        String nextNo = st.getBillNoPrefix() + String.format("%0" + Math.max(1, st.getBillNoDigits()) + "d", st.getBillNoNext());
        bill.setBillNo(nextNo);
        bill.setDate(strOr(args, "date", LocalDate.now().toString()));

        // Buyer resolution
        String buyerId = strOr(args, "buyerId", "");
        String buyerName = strOr(args, "buyerName", "");
        Buyer buyer = null;
        if (!buyerId.isBlank()) buyer = dm.buyers().getBuyerById(buyerId);
        if (buyer == null && !buyerName.isBlank()) buyer = dm.buyers().findByName(buyerName);
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
                ItemRecord cat = dm.items().getItemById(itemId);
                if (cat != null) {
                    bi.setId(cat.getId());
                    bi.setDesc(cat.getName());
                    if (!line.containsKey("rate")) bi.setRate(cat.getRate());
                    if (!line.containsKey("gst")) bi.setGst(cat.getGst());
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

        // Bump the invoice counter only when the generated number was used
        if (nextNo.equals(bill.getBillNo())) {
            st.setBillNoNext(st.getBillNoNext() + 1);
            dm.saveSettings(st);
        }
        return mapOf("ok", true, "id", bill.getId(), "billNo", bill.getBillNo(),
                "grandTotal", bill.getTotals().getGrandTotal(),
                "status", bill.getStatus().name(),
                "interState", interState);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> createPurchase(DataManager dm, Map<String, Object> args) throws Exception {
        Supplier supplier;
        String sid = strOr(args, "supplierId", "");
        String sname = strOr(args, "supplierName", "");
        if (!sid.isBlank()) supplier = dm.suppliers().getSupplierById(sid);
        else if (!sname.isBlank()) {
            supplier = dm.suppliers().getAllSuppliers().stream()
                    .filter(s -> s.getName().equalsIgnoreCase(sname.trim())).findFirst().orElse(null);
        } else supplier = null;
        if (supplier == null) {
            throw new IllegalArgumentException("supplierId or supplierName must resolve to an existing supplier");
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
                ItemRecord cat = dm.items().getItemById(itemIdArg);
                if (cat != null) {
                    bi.setId(cat.getId());
                    bi.setDesc(cat.getName());
                    if (!line.containsKey("rate") && cat.getPurchaseRate() > 0) bi.setRate(cat.getPurchaseRate());
                    if (!line.containsKey("gst")) bi.setGst(cat.getGst());
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
        return mapOf("ok", true, "id", pb.getId(), "billNo", pb.getBillNo(),
                "grandTotal", pb.getTotals() != null ? pb.getTotals().getGrandTotal() : 0.0,
                "itc", pb.getTotals() != null
                        ? PurchaseService.round2(pb.getTotals().getCgst() + pb.getTotals().getSgst() + pb.getTotals().getIgst())
                        : 0.0,
                "interState", interState,
                "amountPayable", pb.getAmountPayable());
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
            for (var e : t.getElements()) {
                els.add(mapOf("id", e.getId(), "name", e.getName(),
                        "type", String.valueOf(e.getType()),
                        "x", e.getX(), "y", e.getY(), "w", e.getW(), "h", e.getH(),
                        "text", e.getText(), "binding", e.getBinding()));
            }
        }
        return mapOf("id", t.getId(), "name", t.getName(),
                "pageSize", String.valueOf(t.getPage().getSizeName()),
                "orientation", t.getPage().getOrientation(),
                "widthMm", t.getPage().getWidth(), "heightMm", t.getPage().getHeight(),
                "elements", els);
    }

    private static Map<String, Object> createTemplate(DataManager dm, Map<String, Object> args) throws Exception {
        String name = str(args, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        Template t = new Template();
        t.setId("tpl_mcp_" + UUID.randomUUID().toString().substring(0, 8));
        t.setName(name.trim());
        t.setPage(pageFor(strOr(args, "pageSize", "A4"), null));
        if (args.get("elements") instanceof List<?> els) t.setElements(elementsFrom(els));
        dm.templates().saveTemplate(t);
        return mapOf("ok", true, "id", t.getId(), "name", t.getName(),
                "elements", t.getElements() == null ? 0 : t.getElements().size());
    }

    private static Map<String, Object> duplicateTemplate(DataManager dm, Map<String, Object> args) throws Exception {
        Template src = requireTemplate(dm, str(args, "id"));
        String newName = strOr(args, "newName", src.getName() + " (copy)");
        Template copy = new Template();
        copy.setId("tpl_mcp_" + UUID.randomUUID().toString().substring(0, 8));
        copy.setName(newName);
        copy.setPage(src.getPage());
        if (src.getElements() != null) {
            copy.setElements(new ArrayList<>(src.getElements()));
        }
        dm.templates().saveTemplate(copy);
        return mapOf("ok", true, "id", copy.getId(), "name", copy.getName());
    }

    @SuppressWarnings("unchecked")
    private static List<com.invoicestudio.model.TemplateElement> elementsFrom(List<?> raw) {
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
            }
            e.setX(dbl(m, "x", 10.0));
            e.setY(dbl(m, "y", 10.0));
            e.setW(dbl(m, "w", 40.0));
            e.setH(dbl(m, "h", 10.0));
            e.setText(str(m, "text"));
            e.setBinding(str(m, "binding"));
            out.add(e);
        }
        return out;
    }

    private static com.invoicestudio.model.PageConfig pageFor(String sizeName, com.invoicestudio.model.PageConfig fallback) {
        com.invoicestudio.model.PageConfig pc = fallback != null ? fallback : new com.invoicestudio.model.PageConfig();
        if (sizeName != null && !sizeName.isBlank()) {
            String s = sizeName.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            try {
                pc.setSizeName(com.invoicestudio.model.PageSizeName.valueOf(s));
            } catch (IllegalArgumentException ignored) {
                // keep default A4 for unrecognized names
            }
        }
        return pc;
    }

    private static Map<String, Object> createVariable(DataManager dm, Map<String, Object> args) throws Exception {
        String key = str(args, "key");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key is required");
        com.invoicestudio.model.VariableDef v = new com.invoicestudio.model.VariableDef(
                key.trim(), strOr(args, "label", key.trim()),
                strOr(args, "type", "text"), false);
        v.setScope(strOr(args, "scope", "fixed"));
        v.setDefaultValue(str(args, "defaultValue"));
        dm.variables().saveVariable(v);
        return mapOf("ok", true, "key", v.getKey());
    }

    private static Map<String, Object> createTransport(DataManager dm, Map<String, Object> args) throws Exception {
        String name = str(args, "name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        com.invoicestudio.model.Transport tr = new com.invoicestudio.model.Transport(
                "trn_mcp_" + UUID.randomUUID().toString().substring(0, 8),
                name.trim(), strOr(args, "phone", ""), strOr(args, "vehicleNumber", ""));
        dm.saveTransport(tr);
        return mapOf("ok", true, "id", tr.getId(), "name", tr.getName());
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
    // List/map projections
    // ------------------------------------------------------------------

    private static List<Map<String, Object>> buyersMap(DataManager dm, String query, int limit) {
        return dm.getAllBuyers().stream()
                .filter(b -> matches(query, b.getName(), b.getPhone(), b.getGst(), b.getCity()))
                .limit(limit)
                .map(b -> mapOf("id", b.getId(), "name", b.getName(), "phone", b.getPhone(),
                        "gst", b.getGst(), "state", b.getState(), "stateCode", b.getEffectiveStateCode(),
                        "city", b.getCity(), "creditLimit", b.getCreditLimit()))
                .collect(java.util.stream.Collectors.toList());
    }

    private static List<Map<String, Object>> suppliersMap(DataManager dm, String query, int limit) {
        List<PurchaseBill> purchases = dm.getAllPurchases();
        return dm.getAllSuppliers().stream()
                .filter(s -> matches(query, s.getName(), s.getPhone(), s.getGst(), s.getCity()))
                .limit(limit)
                .map(s -> {
                    double bal = s.getOpeningBalance();
                    for (PurchaseBill p : purchases) {
                        if (s.getId() != null && s.getId().equals(p.getSupplierId()) && !p.isPaid()) {
                            bal += p.getAmountPayable() - p.getPaidAmount();
                        }
                    }
                    return mapOf("id", s.getId(), "name", s.getName(), "phone", s.getPhone(),
                            "gst", s.getGst(), "state", s.getState(), "stateCode", s.getStateCode(),
                            "city", s.getCity(), "creditPeriodDays", s.getCreditPeriodDays(),
                            "payableBalance", PurchaseService.round2(bal));
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private static List<Map<String, Object>> itemsMap(DataManager dm, String query, int limit) {
        Map<String, Double> stock = dm.getStockBalances();
        return dm.getAllItems().stream()
                .filter(it -> matches(query, it.getName(), it.getHsn(), it.getCategoryName()))
                .limit(limit)
                .map(it -> mapOf("id", it.getId(), "name", it.getName(), "hsn", it.getHsn(),
                        "unit", it.getUnit(), "rate", it.getRate(), "gst", it.getGst(),
                        "purchaseRate", it.getPurchaseRate(), "reorderLevel", it.getReorderLevel(),
                        "stock", stock.getOrDefault(it.getId(), 0.0), "category", it.getCategoryName()))
                .collect(java.util.stream.Collectors.toList());
    }

    private static List<Map<String, Object>> templatesMap(DataManager dm) {
        return dm.templates().getAllTemplates().stream()
                .map(t -> mapOf("id", t.getId(), "name", t.getName(),
                        "pageSize", String.valueOf(t.getPage().getSizeName()),
                        "elements", t.getElements() == null ? 0 : t.getElements().size()))
                .collect(java.util.stream.Collectors.toList());
    }

    private static List<Map<String, Object>> billsMap(DataManager dm, String query, String status, int limit) {
        return dm.getAllBills().stream()
                .filter(b -> status == null || status.isBlank()
                        || b.getStatus().name().equalsIgnoreCase(status.trim()))
                .filter(b -> matches(query, b.getId(), b.getBillNo(), b.getBuyerName()))
                .sorted(java.util.Comparator.comparing(Bill::getDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .limit(limit)
                .map(McpToolRegistry::billSummary)
                .collect(java.util.stream.Collectors.toList());
    }

    private static Map<String, Object> billSummary(Bill b) {
        double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
        if (paid == 0 && b.getStatus() == BillStatus.PAID) paid = b.getTotals().getGrandTotal();
        return mapOf("id", b.getId(), "billNo", b.getBillNo(), "date", b.getDate(),
                "buyer", b.getBuyerName(), "grandTotal", b.getTotals().getGrandTotal(),
                "paid", PurchaseService.round2(paid),
                "status", b.getStatus().name());
    }

    private static Map<String, Object> billFull(Bill b) {
        Map<String, Object> m = billSummary(b);
        List<Map<String, Object>> lines = new ArrayList<>();
        if (b.getItems() != null) {
            for (BillItem it : b.getItems()) {
                lines.add(mapOf("itemId", it.getId(), "desc", it.getDesc(), "hsn", it.getHsn(),
                        "qty", it.getQty(), "unit", it.getUnit(), "rate", it.getRate(),
                        "gst", it.getGst(), "discPct", it.getDiscPct(),
                        "amount", PurchaseService.round2(it.getQty() * it.getRate() * (1 - it.getDiscPct() / 100.0))));
            }
        }
        m.put("items", lines);
        m.put("totals", totalsMap(b.getTotals()));
        List<Map<String, Object>> pays = new ArrayList<>();
        for (BillPayment p : b.getPayments()) {
            pays.add(mapOf("date", p.getDate(), "amount", p.getAmount(),
                    "method", p.getMethod() != null ? p.getMethod().name() : "", "reference", p.getReference()));
        }
        m.put("payments", pays);
        return m;
    }

    private static List<Map<String, Object>> purchasesMap(DataManager dm, String query, int limit) {
        return dm.getAllPurchases().stream()
                .filter(p -> matches(query, p.getBillNo(), p.getSupplierBillNo(), p.getSupplierName()))
                .limit(limit)
                .map(p -> mapOf("id", p.getId(), "billNo", p.getBillNo(), "supplierBillNo", p.getSupplierBillNo(),
                        "date", p.getDate(), "supplier", p.getSupplierName(),
                        "grandTotal", p.getTotals() != null ? p.getTotals().getGrandTotal() : 0.0,
                        "itc", p.getTotals() != null
                                ? PurchaseService.round2(p.getTotals().getCgst() + p.getTotals().getSgst() + p.getTotals().getIgst())
                                : 0.0,
                        "paid", p.getPaidAmount(), "fullyPaid", p.isPaid()))
                .collect(java.util.stream.Collectors.toList());
    }

    private static Map<String, Object> expenseMap(Expense e) {
        return mapOf("id", e.getId(), "date", e.getDate(), "category", e.getCategory(),
                "head", Expense.isDirect(e.getCategory()) ? "DIRECT" : "INDIRECT",
                "amount", e.getAmount(), "paymentMode", e.getPaymentMode(),
                "payee", e.getPayee(), "description", e.getDescription());
    }

    private static Map<String, Object> transactionMap(Transaction t) {
        return mapOf("id", t.getId(), "date", t.getTransactionDate(), "type", t.getTransactionType(),
                "billNo", t.getBillNo(), "buyer", t.getBuyerName(), "amount", t.getAmount());
    }

    private static Map<String, Object> totalsMap(BillTotals t) {
        return mapOf("subtotal", t.getSubtotal(), "discount", t.getDiscount(), "taxable", t.getTaxable(),
                "cgst", t.getCgst(), "sgst", t.getSgst(), "igst", t.getIgst(),
                "roundOff", t.getRoundOff(), "grandTotal", t.getGrandTotal());
    }

    private static Map<String, Object> settingsMap(Settings s) {
        var b = s.getBusiness();
        return mapOf("businessName", b.getName(), "gstin", b.getGstin(), "state", b.getState(),
                "stateCode", b.getStateCode(), "phone", b.getPhone(), "email", b.getEmail(),
                "currency", s.getCurrency(), "billNoPrefix", s.getBillNoPrefix(),
                "billNoNext", s.getBillNoNext(), "billNoDigits", s.getBillNoDigits(),
                "interStateDefault", s.isInterState());
    }

    // ------------------------------------------------------------------
    // Reports
    // ------------------------------------------------------------------

    private static List<Map<String, Object>> stockReport(DataManager dm) {
        Map<String, Double> balances = dm.getStockBalances();
        Map<String, ItemRecord> byId = new HashMap<>();
        for (ItemRecord it : dm.getAllItems()) byId.put(it.getId(), it);
        String fyStart = LocalDate.now().getMonthValue() >= 4
                ? LocalDate.now().getYear() + "-04-01"
                : (LocalDate.now().getYear() - 1) + "-04-01";
        String today = LocalDate.now().toString();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> low = new ArrayList<>();
        for (var row : FIN.stockSummary(dm.getAllItems(), balances,
                dm.getAllPurchases(), dm.getAllBills(), fyStart, today)) {
            rows.add(mapOf("itemId", row.itemId(), "name", row.name(), "unit", row.unit(),
                    "opening", row.openingQty(), "in", row.inQty(), "out", row.outQty(),
                    "closing", row.closingQty(), "costRate", row.costRate(), "closingValue", row.closingValue()));
            ItemRecord cat = byId.get(row.itemId());
            if (cat != null && row.closingQty() <= cat.getReorderLevel()) {
                low.add(mapOf("itemId", row.itemId(), "name", row.name(),
                        "closing", row.closingQty(), "reorderLevel", cat.getReorderLevel()));
            }
        }
        return List.of(mapOf("stock", rows, "lowStock", low));
    }

    private static List<Map<String, Object>> profitabilityReport(DataManager dm) {
        String fyStart = LocalDate.now().getMonthValue() >= 4
                ? LocalDate.now().getYear() + "-04-01"
                : (LocalDate.now().getYear() - 1) + "-04-01";
        String today = LocalDate.now().toString();
        return FIN.itemProfitability(dm.getAllItems(), dm.getAllPurchases(), dm.getAllBills(), fyStart, today)
                .stream()
                .map(r -> mapOf("itemId", r.itemId(), "name", r.name(), "qtySold", r.qtySold(),
                        "salesValue", r.salesValue(), "avgCost", r.avgCost(), "cogs", r.cogs(),
                        "grossProfit", r.grossProfit(), "gpPercent", r.gpPercent()))
                .collect(java.util.stream.Collectors.toList());
    }

    private static Map<String, Object> financialSummary(DataManager dm, Map<String, Object> args) {
        String from = strOr(args, "from", LocalDate.now().getMonthValue() >= 4
                ? LocalDate.now().getYear() + "-04-01"
                : (LocalDate.now().getYear() - 1) + "-04-01");
        String to = strOr(args, "to", LocalDate.now().toString());
        FinancialService.Financials f = FIN.compute(dm.getAllBills(), dm.getAllPurchases(), dm.getAllExpenses(),
                dm.getAllSuppliers(), dm.getAllItems(), dm.getStockBalances(), from, to);
        return mapOf(
                "period", mapOf("from", from, "to", to),
                "trading", mapOf("salesRevenue", f.salesRevenue(), "openingStock", f.openingStockValue(),
                        "purchases", f.purchasesValue(), "directExpenses", f.directExpenses(),
                        "closingStock", f.closingStockValue(), "grossProfit", f.grossProfit()),
                "profitAndLoss", mapOf("grossProfit", f.grossProfit(),
                        "indirectExpenses", f.indirectExpenses(), "netProfit", f.netProfit()),
                "balanceSheet", mapOf("sundryDebtors", f.sundryDebtors(), "sundryCreditors", f.sundryCreditors(),
                        "cashInHand", f.cashInHand(), "inventoryValue", f.inventoryValue(),
                        "gstPayable", f.gstPayable(), "totalAssets", f.totalAssets(),
                        "totalLiabilities", f.totalLiabilities()),
                "gst", mapOf("outputGst", f.outputGst(), "inputCredit", f.inputCredit(),
                        "netTaxPayable", f.netTaxPayable()));
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

    private static boolean matches(String query, String... fields) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase(Locale.ROOT);
        for (String f : fields) {
            if (f != null && f.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    private static PaymentMethod parseMethod(String mode) {
        if (mode == null) return PaymentMethod.CASH;
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "upi" -> PaymentMethod.UPI;
            case "bank", "bank transfer", "neft", "rtgs", "imps" -> PaymentMethod.BANK_TRANSFER;
            case "cheque", "check" -> PaymentMethod.CHEQUE;
            case "card" -> PaymentMethod.CARD;
            default -> PaymentMethod.CASH;
        };
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
        if (m == null) return "";
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : "";
    }

    private static String strOr(Map<String, Object> m, String key, String def) {
        String v = str(m, key);
        return v.isBlank() ? def : v;
    }

    private static double dbl(Map<String, Object> m, String key, double def) {
        if (m == null) return def;
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return v != null ? Double.parseDouble(String.valueOf(v)) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int intVal(Map<String, Object> m, String key, int def) {
        return (int) dbl(m, key, def);
    }

    private static boolean boolVal(Map<String, Object> m, String key, boolean def) {
        if (m == null) return def;
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        return v != null && Boolean.parseBoolean(String.valueOf(v));
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
