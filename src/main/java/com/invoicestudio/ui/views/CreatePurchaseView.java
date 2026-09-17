package com.invoicestudio.ui.views;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.BillTotals;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.model.PurchaseBill;
import com.invoicestudio.model.Supplier;
import com.invoicestudio.service.PurchaseService;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import com.invoicestudio.ui.UiTheme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Record Purchase Bill — Tally "F9: Purchase" equivalent.
 *
 * Inward flow: pick supplier → enter supplier's original bill no/date →
 * add item lines (stock IN at cost price) → live GST auto-calc
 * (CGST/SGST or IGST from supplier GSTIN vs company state) →
 * save as credit (payable) or paid (cash/bank/cheque).
 *
 * Built entirely from UiTheme / globalfile.css so it matches the
 * "Obsidian & Gold" design system.
 */
public class CreatePurchaseView extends VBox {

    private final StudioApp app;
    private final PurchaseBill editing; // non-null when editing an existing purchase

    private final ComboBox<Supplier> supplierBox = new ComboBox<>();
    private final TextField supplierBillNoF = new TextField();
    private final TextField internalNoF = new TextField();
    private final javafx.scene.control.DatePicker datePick = UiTheme.datePicker("Bill Date");
    private final TextField freightF = new TextField("0");
    private final ComboBox<String> paymentModeBox = new ComboBox<>();
    private final javafx.scene.control.CheckBox paidCb = new javafx.scene.control.CheckBox("Mark as Paid");
    private final javafx.scene.control.TextArea notesF = new javafx.scene.control.TextArea();

    private final VBox itemRowsBox = new VBox(6);
    private final List<ItemRow> itemRows = new ArrayList<>();

    // Totals card (live)
    private final Label taxableVal = UiTheme.kpiValue("₹0.00");
    private final Label cgstVal = UiTheme.kpiValue("₹0.00");
    private final Label sgstVal = UiTheme.kpiValue("₹0.00");
    private final Label igstVal = UiTheme.kpiValue("₹0.00");
    private final Label grandVal = UiTheme.kpiValue("₹0.00");
    private final Label payableVal = UiTheme.kpiValue("₹0.00");
    private final Label taxSplitTitle = new Label("CGST / SGST (Intra-State)");

    public CreatePurchaseView(StudioApp app) {
        this(app, null);
    }

    public CreatePurchaseView(StudioApp app, PurchaseBill editing) {
        this.app = app;
        this.editing = editing;

        setSpacing(16);
        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        getChildren().addAll(createHeader(), createHeaderForm(), createItemsCard(), createTotalsCard());
        if (editing != null) {
            populateForEdit(editing);
        } else {
            internalNoF.setText(PurchaseService.nextPurchaseBillNo(1, 4));
            addRow(null);
        }
        updateTotals();
    }

    // ------------------------------------------------------------------
    // UI sections
    // ------------------------------------------------------------------

    private Node createHeader() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label(editing != null ? "Edit Purchase Bill" : "Record Purchase Bill");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Inward supply from supplier — increases stock, records Input Tax Credit (ITC).");
        subtitle.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button cancelBtn = UiTheme.secondaryBtn("Cancel");
        cancelBtn.setOnAction(e -> app.showPurchases());

        Button saveBtn = UiTheme.goldBtn(editing != null ? "Update Purchase" : "Save Purchase");
        saveBtn.setTooltip(new Tooltip("Save purchase bill, increase stock and update supplier payable"));
        saveBtn.setOnAction(e -> savePurchase());

        bar.getChildren().addAll(titleBox, sp, cancelBtn, saveBtn);
        return bar;
    }

    private Node createHeaderForm() {
        VBox card = UiTheme.card(12);

        Label secSupplier = new Label("SUPPLIER & BILL DETAILS");
        secSupplier.getStyleClass().add("section-eyebrow");

        HBox row1 = new HBox(12);
        row1.setAlignment(Pos.CENTER_LEFT);

        supplierBox.setPromptText("Select supplier (or create in Sellers)...");
        supplierBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(supplierBox, Priority.ALWAYS);
        supplierBox.setItems(javafx.collections.FXCollections.observableArrayList(app.getData().getAllSuppliers()));
        supplierBox.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Supplier s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.getName() + (!s.getGst().isBlank() ? " · " + s.getGst() : " · Unregistered"));
            }
        });
        supplierBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Supplier s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.getName());
            }
        });
        supplierBox.setOnAction(e -> updateTotals());

        supplierBillNoF.setPromptText("Supplier's Original Bill No (GST matching)");
        supplierBillNoF.setPrefWidth(240);

        internalNoF.setPromptText("Internal Voucher No");
        internalNoF.setPrefWidth(130);
        internalNoF.getStyleClass().add("table-cell-mono");
        internalNoF.setEditable(false);

        datePick.setPrefWidth(140);

        row1.getChildren().addAll(supplierBox, supplierBillNoF, internalNoF, datePick);

        HBox row2 = new HBox(12);
        row2.setAlignment(Pos.CENTER_LEFT);

        freightF.setPromptText("Freight / Other Charges (₹)");
        freightF.setPrefWidth(210);
        freightF.textProperty().addListener((obs, o, v) -> updateTotals());

        paidCb.getStyleClass().add("check-box");
        paidCb.selectedProperty().addListener((obs, o, v) -> paymentModeBox.setDisable(!v));

        paymentModeBox.setItems(javafx.collections.FXCollections.observableArrayList("Cash", "Bank Transfer", "Cheque", "UPI"));
        paymentModeBox.setValue("Cash");
        paymentModeBox.setPrefWidth(160);
        paymentModeBox.setDisable(true);

        notesF.setPromptText("Notes (optional)");
        notesF.setPrefRowCount(1);
        HBox.setHgrow(notesF, Priority.ALWAYS);

        row2.getChildren().addAll(freightF, paidCb, paymentModeBox, notesF);

        card.getChildren().addAll(secSupplier, row1, row2);
        return card;
    }

    private Node createItemsCard() {
        VBox card = UiTheme.card(10);

        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);

        Label itLbl = new Label("ITEM LINES (STOCK IN)");
        itLbl.getStyleClass().add("section-eyebrow");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button addItemBtn = new Button("Add Line Item");
        addItemBtn.getStyleClass().addAll("button-sm", "gold-btn");
        addItemBtn.setGraphic(IconHelper.getIcon("plus", 11, "#0B0E13"));
        addItemBtn.setGraphicTextGap(6);
        addItemBtn.setOnAction(e -> addRow(null));

        head.getChildren().addAll(itLbl, sp, addItemBtn);

        // Column header row
        HBox colHead = new HBox(8);
        colHead.getStyleClass().add("item-row-header");
        colHead.setAlignment(Pos.CENTER_LEFT);
        colHead.getChildren().addAll(
                fixedLabel("Item (Catalog)", 240, "text-muted"),
                fixedLabel("HSN", 70, "text-muted"),
                fixedLabel("Qty", 60, "text-muted"),
                fixedLabel("Unit", 55, "text-muted"),
                fixedLabel("Rate", 80, "text-muted"),
                fixedLabel("GST%", 55, "text-muted"),
                fixedLabel("Stock", 80, "text-muted"),
                fixedLabel("Amount", 90, "text-muted"),
                fixedLabel("", 34, "text-muted"));

        itemRowsBox.getChildren().clear();
        card.getChildren().addAll(head, colHead, itemRowsBox);
        return card;
    }

    private Node createTotalsCard() {
        HBox card = new HBox(24);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setAlignment(Pos.CENTER_LEFT);

        VBox left = new VBox(6);
        taxSplitTitle.getStyleClass().add("section-eyebrow");
        HBox taxes = new HBox(24);
        taxes.getChildren().addAll(
                statBlock("Taxable", taxableVal),
                statBlock("CGST", cgstVal),
                statBlock("SGST", sgstVal),
                statBlock("IGST", igstVal));
        left.getChildren().addAll(taxSplitTitle, taxes);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        VBox right = new VBox(4);
        right.setAlignment(Pos.CENTER_RIGHT);
        Label grandLbl = new Label("GRAND TOTAL");
        grandLbl.getStyleClass().add("section-eyebrow");
        Label payLbl = new Label("PAYABLE (Incl. Freight)");
        payLbl.getStyleClass().add("section-eyebrow");
        right.getChildren().addAll(grandLbl, grandVal, payLbl, payableVal);

        card.getChildren().addAll(left, sp, right);
        return card;
    }

    private VBox statBlock(String title, Label value) {
        VBox v = new VBox(2);
        Label t = new Label(title);
        t.getStyleClass().add("text-muted");
        v.getChildren().addAll(t, value);
        return v;
    }

    private Label fixedLabel(String text, double w, String style) {
        Label l = new Label(text);
        l.setPrefWidth(w);
        l.setMinWidth(w);
        l.setMaxWidth(w);
        if (style != null) l.getStyleClass().add(style);
        return l;
    }

    // ------------------------------------------------------------------
    // Item rows
    // ------------------------------------------------------------------

    private void addRow(BillItem seed) {
        ItemRow row = new ItemRow(seed);
        itemRows.add(row);
        itemRowsBox.getChildren().add(row);
        renumber();
    }

    private void removeRow(ItemRow row) {
        itemRows.remove(row);
        itemRowsBox.getChildren().remove(row);
        renumber();
        updateTotals();
    }

    private void renumber() {
        for (int i = 0; i < itemRows.size(); i++) {
            itemRows.get(i).srLbl.setText(String.valueOf(i + 1));
        }
    }

    private List<BillItem> collectItems() {
        List<BillItem> items = new ArrayList<>();
        for (ItemRow r : itemRows) {
            BillItem it = r.toBillItem();
            if (it != null) items.add(it);
        }
        return items;
    }

    private void updateTotals() {
        List<BillItem> items = collectItems();
        double freight = parseDouble(freightF.getText());
        boolean interState = isInterStateSelected();

        BillTotals t = PurchaseService.computePurchaseTotals(items, 0, interState);
        String cur = app.getData().getSettings().getCurrency();

        taxableVal.setText(String.format("%s%.2f", cur, t.getTaxable()));
        cgstVal.setText(String.format("%s%.2f", cur, t.getCgst()));
        sgstVal.setText(String.format("%s%.2f", cur, t.getSgst()));
        igstVal.setText(String.format("%s%.2f", cur, t.getIgst()));
        grandVal.setText(String.format("%s%.2f", cur, t.getGrandTotal()));
        payableVal.setText(String.format("%s%.2f", cur, t.getGrandTotal() + freight));

        taxSplitTitle.setText(interState ? "IGST (Inter-State Purchase)" : "CGST / SGST (Intra-State)");
    }

    private boolean isInterStateSelected() {
        Supplier s = supplierBox.getValue();
        if (s == null) return false;
        String companyCode = companyStateCode();
        return PurchaseService.isInterStateSupply(s.getGst(), companyCode);
    }

    private String companyStateCode() {
        try {
            com.invoicestudio.model.Settings st = app.getData().getSettings();
            for (java.lang.reflect.Field f : st.getClass().getDeclaredFields()) {
                if (f.getName().toLowerCase().contains("statecode")) {
                    f.setAccessible(true);
                    Object v = f.get(st);
                    return v != null ? v.toString() : "";
                }
            }
            for (java.lang.reflect.Field f : st.getClass().getDeclaredFields()) {
                if (f.getName().toLowerCase().contains("gstin") && f.getType() == String.class) {
                    f.setAccessible(true);
                    Object v = f.get(st);
                    if (v != null && v.toString().length() >= 2) return v.toString().substring(0, 2);
                }
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored); }
        return "";
    }

    private static double parseDouble(String s) {
        try {
            return s == null || s.isBlank() ? 0 : Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Save
    // ------------------------------------------------------------------

    private void savePurchase() {
        Supplier supplier = supplierBox.getValue();
        if (supplier == null) {
            Toast.show(app.getRootPane(), "Validation Error", "Select a supplier first (create one under Sellers).", true);
            return;
        }
        List<BillItem> items = collectItems();
        if (items.isEmpty()) {
            Toast.show(app.getRootPane(), "Validation Error", "Add at least one item line.", true);
            return;
        }
        for (BillItem it : items) {
            if (it.getDesc() == null || it.getDesc().isBlank()) {
                Toast.show(app.getRootPane(), "Validation Error", "Every item line needs a description.", true);
                return;
            }
            if (it.getId() == null || it.getId().isBlank()) {
                Toast.show(app.getRootPane(), "Info", "Line \"" + it.getDesc() + "\" is not linked to a catalog item; stock will not move for it. Pick from catalog to track stock.", false);
            }
        }
        String date = datePick.getValue() != null ? datePick.getValue().toString() : PurchaseService.todayISO();
        if (date.isBlank()) {
            Toast.show(app.getRootPane(), "Validation Error", "Bill date is required.", true);
            return;
        }

        // Books hygiene: the same supplier bill number recorded twice is almost
        // certainly a double entry — confirm before allowing it.
        String supBillNo = supplierBillNoF.getText() == null ? "" : supplierBillNoF.getText().trim();
        if (!supBillNo.isBlank()) {
            for (com.invoicestudio.model.PurchaseBill other : app.getData().getAllPurchases()) {
                if (editing != null && other.getId().equals(editing.getId())) continue;
                if (supBillNo.equalsIgnoreCase(other.getSupplierBillNo() == null ? "" : other.getSupplierBillNo())
                        && other.getSupplierId() != null && other.getSupplierId().equals(supplier.getId())) {
                    Alert warn = new Alert(Alert.AlertType.CONFIRMATION,
                            "Supplier bill no \"" + supBillNo + "\" was already recorded from " + other.getSupplierName()
                                    + " on " + other.getDate() + ".\nRecord it again anyway?",
                            ButtonType.YES, ButtonType.CANCEL);
                    warn.setHeaderText("Possible Duplicate Purchase");
                    com.invoicestudio.ui.DialogHelper.styleDialog(warn);
                    if (warn.showAndWait().filter(b -> b == ButtonType.YES).isEmpty()) return;
                    break;
                }
            }
        }

        // Margin nudge: any line priced at/below the catalog purchase rate needs a look.
        java.util.List<String> lowMargin = new java.util.ArrayList<>();
        for (BillItem it : items) {
            if (it.getId() == null || it.getId().isBlank()) continue;
            com.invoicestudio.model.ItemRecord cat = app.getData().items().getItemById(it.getId());
            if (cat != null && cat.getPurchaseRate() > 0 && it.getRate() > 0 && it.getRate() < cat.getPurchaseRate()) {
                lowMargin.add(it.getDesc() + " (₹" + String.format("%.0f", it.getRate()) + " < usual ₹"
                        + String.format("%.0f", cat.getPurchaseRate()) + ")");
            }
        }
        if (!lowMargin.isEmpty()) {
            Alert warn = new Alert(Alert.AlertType.CONFIRMATION,
                    "These lines are priced below the item's usual purchase rate:\n  • "
                            + String.join("\n  • ", lowMargin) + "\n\nSave anyway?",
                    ButtonType.YES, ButtonType.CANCEL);
            warn.setHeaderText("Below Usual Purchase Rate");
            com.invoicestudio.ui.DialogHelper.styleDialog(warn);
            if (warn.showAndWait().filter(b -> b == ButtonType.YES).isEmpty()) return;
        }

        boolean interState = isInterStateSelected();
        double freight = parseDouble(freightF.getText());

        PurchaseBill bill = editing != null ? editing : new PurchaseBill();
        if (editing == null) {
            bill.setId(PurchaseService.newPurchaseId());
            bill.setBillNo(PurchaseService.nextPurchaseBillNo(nextPurchaseSequence(), 4));
        }
        bill.setSupplierBillNo(supplierBillNoF.getText() == null ? "" : supplierBillNoF.getText().trim());
        bill.setDate(date);
        bill.setSupplierId(supplier.getId());
        bill.setSupplierName(supplier.getName());
        bill.setSupplierGstin(supplier.getGst());
        bill.setItems(items);
        bill.setFreight(freight);
        bill.setTotals(PurchaseService.computePurchaseTotals(items, 0, interState));
        bill.setPaid(paidCb.isSelected());
        bill.setPaymentMode(paidCb.isSelected() ? String.valueOf(paymentModeBox.getValue()) : "");
        bill.setNotes(notesF.getText() == null ? "" : notesF.getText().trim());

        app.getData().savePurchase(bill);
        Toast.show(app.getRootPane(), editing != null ? "Purchase Updated" : "Purchase Saved",
                bill.getBillNo() + " — stock increased, " + supplier.getName() + " payable updated.", false);
        app.showPurchases();
    }

    private int nextPurchaseSequence() {
        int max = 0;
        for (PurchaseBill p : app.getData().getAllPurchases()) {
            String no = p.getBillNo();
            if (no != null && no.startsWith("PUR-")) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(4)));
                } catch (Exception ignored) {
            AppLog.debug(ignored); }
            }
        }
        return max + 1;
    }

    private void populateForEdit(PurchaseBill b) {
        supplierBillNoF.setText(b.getSupplierBillNo());
        internalNoF.setText(b.getBillNo());
        try {
            if (b.getDate() != null && !b.getDate().isBlank()) {
                datePick.setValue(java.time.LocalDate.parse(b.getDate()));
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored); }
        freightF.setText(String.valueOf(b.getFreight()));
        paidCb.setSelected(b.isPaid());
        if (b.getPaymentMode() != null && !b.getPaymentMode().isBlank()) {
            paymentModeBox.setValue(b.getPaymentMode());
        }
        notesF.setText(b.getNotes());
        for (Supplier s : supplierBox.getItems()) {
            if (s.getId().equals(b.getSupplierId())) {
                supplierBox.setValue(s);
                break;
            }
        }
        itemRowsBox.getChildren().clear();
        itemRows.clear();
        for (BillItem it : b.getItems()) {
            addRow(it);
        }
        if (itemRows.isEmpty()) addRow(null);
    }

    // ------------------------------------------------------------------
    // Line item row editor (mirrors CreateBillView.BillItemRow conventions)
    // ------------------------------------------------------------------

    private class ItemRow extends HBox {
        private final TextField descField = new TextField();
        private final TextField hsnField = new TextField();
        private final TextField qtyField = new TextField("1");
        private final TextField unitField = new TextField("PCS");
        private final TextField rateField = new TextField("0");
        private final TextField gstField = new TextField("18");
        private final Label stockLbl = new Label("—");
        private final Label amountLbl = new Label("₹0.00");
        private final Label srLbl = new Label("1");
        /** Catalog item id linked to this line (drives stock movement). */
        private String catalogItemId = "";

        ItemRow(BillItem seed) {
            setSpacing(8);
            setAlignment(Pos.CENTER_LEFT);
            getStyleClass().add("item-row");

            if (seed != null) {
                catalogItemId = seed.getId() != null ? seed.getId() : "";
                descField.setText(seed.getDesc() != null ? seed.getDesc() : "");
                hsnField.setText(seed.getHsn() != null ? seed.getHsn() : "");
                qtyField.setText(String.valueOf(seed.getQty()));
                unitField.setText(seed.getUnit() != null ? seed.getUnit() : "PCS");
                rateField.setText(String.valueOf(seed.getRate()));
                gstField.setText(String.valueOf(seed.getGst()));
            }

            descField.setPromptText("Item name (pick from catalog)");
            descField.setMinWidth(200);
            descField.setEditable(false);
            descField.setOnMouseClicked(e -> pickCatalogItem(this));
            descField.setTooltip(new Tooltip("Click to pick from catalog (stock-tracked)"));

            hsnField.setPrefWidth(70); hsnField.setMinWidth(70); hsnField.setMaxWidth(70);
            hsnField.setEditable(false);
            hsnField.setAlignment(Pos.CENTER);

            qtyField.setPrefWidth(60); qtyField.setMinWidth(60); qtyField.setMaxWidth(60);
            qtyField.setAlignment(Pos.CENTER_RIGHT);

            unitField.setPrefWidth(55); unitField.setMinWidth(55); unitField.setMaxWidth(55);
            unitField.setAlignment(Pos.CENTER);

            rateField.setPrefWidth(80); rateField.setMinWidth(80); rateField.setMaxWidth(80);
            rateField.setAlignment(Pos.CENTER_RIGHT);

            gstField.setPrefWidth(55); gstField.setMinWidth(55); gstField.setMaxWidth(55);
            gstField.setAlignment(Pos.CENTER_RIGHT);

            stockLbl.setPrefWidth(80); stockLbl.setMinWidth(80); stockLbl.setMaxWidth(80);
            stockLbl.getStyleClass().add("text-muted");

            amountLbl.setPrefWidth(90); amountLbl.setMinWidth(90); amountLbl.setMaxWidth(90);
            amountLbl.getStyleClass().add("cell-bold");

            srLbl.setPrefWidth(24); srLbl.setMinWidth(24); srLbl.setMaxWidth(24);
            srLbl.getStyleClass().add("text-muted");

            qtyField.textProperty().addListener((obs, o, v) -> { updateRowAmount(); updateTotals(); });
            rateField.textProperty().addListener((obs, o, v) -> { updateRowAmount(); updateTotals(); });
            gstField.textProperty().addListener((obs, o, v) -> updateTotals());

            Button removeBtn = new Button("✕");
            removeBtn.getStyleClass().addAll("button-sm", "button-danger");
            removeBtn.setTooltip(new Tooltip("Remove line"));
            removeBtn.setPrefSize(34, 28);
            removeBtn.setOnAction(e -> removeRow(this));

            getChildren().addAll(srLbl, descField, hsnField, qtyField, unitField, rateField, gstField, stockLbl, amountLbl, removeBtn);
            updateRowAmount();
            refreshStockDisplay();
        }

        void updateRowAmount() {
            BillItem it = toBillItem();
            if (it != null) {
                amountLbl.setText(String.format("%s%.2f", app.getData().getSettings().getCurrency(), it.getAmount()));
            }
        }

        void refreshStockDisplay() {
            if (catalogItemId.isBlank()) {
                stockLbl.setText("—");
                stockLbl.getStyleClass().removeAll("accent-red", "accent-emerald");
                return;
            }
            Double bal = app.getData().getStockBalances().get(catalogItemId);
            double v = bal != null ? bal : 0;
            stockLbl.setText(String.format("%.0f", v) + (v < 0 ? " !" : ""));
            stockLbl.getStyleClass().removeAll("accent-red", "accent-emerald");
            stockLbl.getStyleClass().add(v < 0 ? "accent-red" : "accent-emerald");
        }

        BillItem toBillItem() {
            double qty = parseDouble(qtyField.getText());
            double rate = parseDouble(rateField.getText());
            double gst = parseDouble(gstField.getText());
            BillItem it = new BillItem(
                    catalogItemId,
                    descField.getText(),
                    hsnField.getText(),
                    qty,
                    unitField.getText() != null && !unitField.getText().isBlank() ? unitField.getText().trim() : "PCS",
                    rate,
                    gst,
                    0);
            return it;
        }
    }

    private void pickCatalogItem(ItemRow row) {
        javafx.scene.control.Dialog<ItemRecord> dlg = new javafx.scene.control.Dialog<>();
        dlg.setTitle("Pick Item from Catalog");
        dlg.setHeaderText("Select the item being purchased (stock IN)");

        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setPrefWidth(520);

        javafx.scene.control.TableView<ItemRecord> tv = new javafx.scene.control.TableView<>();
        tv.getStyleClass().add("table-view");
        tv.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);

        Map<String, Double> stock = app.getData().getStockBalances();

        javafx.scene.control.TableColumn<ItemRecord, String> cName = new javafx.scene.control.TableColumn<>("Item");
        cName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
        javafx.scene.control.TableColumn<ItemRecord, String> cHsn = new javafx.scene.control.TableColumn<>("HSN");
        cHsn.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getHsn()));
        cHsn.setPrefWidth(90);
        javafx.scene.control.TableColumn<ItemRecord, String> cPR = new javafx.scene.control.TableColumn<>("Cost");
        cPR.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f", d.getValue().getPurchaseRate())));
        cPR.setPrefWidth(80);
        javafx.scene.control.TableColumn<ItemRecord, String> cStk = new javafx.scene.control.TableColumn<>("In Stock");
        cStk.setCellValueFactory(d -> {
            Double bal = stock.get(d.getValue().getId());
            return new javafx.beans.property.SimpleStringProperty(String.format("%.0f %s", bal != null ? bal : 0, d.getValue().getUnit()));
        });
        cStk.setPrefWidth(90);
        tv.getColumns().addAll(cName, cHsn, cPR, cStk);
        tv.setItems(javafx.collections.FXCollections.observableArrayList(app.getData().getAllItems()));
        VBox.setVgrow(tv, Priority.ALWAYS);

        box.getChildren().add(tv);
        dlg.getDialogPane().setContent(box);
        dlg.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dlg.setResultConverter(btn -> btn == javafx.scene.control.ButtonType.OK && tv.getSelectionModel().getSelectedItem() != null
                ? tv.getSelectionModel().getSelectedItem() : null);
        com.invoicestudio.ui.DialogHelper.styleDialog(dlg, 560, 420);

        dlg.showAndWait().ifPresent(item -> {
            row.catalogItemId = item.getId();
            row.descField.setText(item.getName());
            row.hsnField.setText(item.getHsn());
            row.unitField.setText(item.getUnit());
            row.rateField.setText(item.getPurchaseRate() > 0 ? String.valueOf(item.getPurchaseRate())
                    : String.valueOf(item.getRate()));
            row.gstField.setText(String.valueOf(item.getGst()));
            row.refreshStockDisplay();
            updateTotals();
        });
    }
}
