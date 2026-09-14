package com.invoicestudio.ui.views;

import com.invoicestudio.db.*;
import com.invoicestudio.model.*;
import com.invoicestudio.model.TableColumn;
import com.invoicestudio.service.BillingService;
import com.invoicestudio.service.PdfExportService;
import com.invoicestudio.service.PrintingService;
import com.invoicestudio.ui.BillPreviewPane;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import com.invoicestudio.ui.UiTheme;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class CreateBillView extends BorderPane {

    private final PauseTransition previewDebounce = new PauseTransition(Duration.millis(150));

    private final StudioApp app;
    private final BillDao billDao;
    private final BuyerDao buyerDao;
    private final ItemDao itemDao;
    private final TemplateDao templateDao;
    private final SettingsDao settingsDao;
    private final com.invoicestudio.db.VariableDao variableDao;

    // Fixed-scope custom variable input fields keyed by variable key
    private final java.util.Map<String, TextField> customFixedInputs = new java.util.LinkedHashMap<>();

    private Bill editingBill;
    private Template currentTemplate;
    private Settings currentSettings;

    // Form controls
    private DocType selectedDocType = DocType.INVOICE;
    private final ComboBox<Template> templateCombo = new ComboBox<>();
    private final TextField billNoField = new TextField();
    private final DatePicker datePicker = UiTheme.datePicker(LocalDate.now(), "dd/mm/yyyy");

    // Buyer
    private final ComboBox<Buyer> buyerCombo = new ComboBox<>();
    private final TextField buyerNameField = new TextField();
    private final TextArea buyerAddressField = new TextArea();
    private final TextField buyerGstField = new TextField();
    private final TextField buyerPhoneField = new TextField();
    private final TextField buyerStateField = new TextField();
    private final TextField buyerStateCodeField = new TextField();
    private final CheckBox saveBuyerCb = new CheckBox("Save buyer to directory");

    // Logistics & Extra
    private final TextField poNoField = new TextField();
    private final TextField transportField = new TextField();
    private final TextField vehicleField = new TextField();
    private final TextField ewayField = new TextField();
    private final TextField parcelField = new TextField("1");
    private final TextField refInvoiceField = new TextField();
    private final TextField creditReasonField = new TextField();

    // Items
    private final StackPane lineItemsHeaderContainer = new StackPane();
    private final VBox itemsBox = new VBox(8);
    private final List<BillItemRow> itemRows = new ArrayList<>();

    // Totals & Options
    private final TextField discountPctField = new TextField("0");
    private final TextArea notesField = new TextArea();
    private final ComboBox<BillStatus> statusCombo = new ComboBox<>();
    private final ComboBox<RepeatCadence> repeatCombo = new ComboBox<>();
    private final DatePicker repeatEndPicker = UiTheme.datePicker("dd/mm/yyyy");

    // Totals labels
    private final Label subtotalLbl = new Label("₹0.00");
    private final Label discountLbl = new Label("₹0.00");
    private final Label taxableLbl = new Label("₹0.00");
    private final Label cgstLbl = new Label("₹0.00");
    private final Label sgstLbl = new Label("₹0.00");
    private final Label igstLbl = new Label("₹0.00");
    private final Label grandTotalLbl = new Label("₹0.00");
    private final Label amountInWordsLbl = new Label("Zero Rupees Only");

    // Right Preview
    private final BillPreviewPane previewPane = new BillPreviewPane();

    public CreateBillView(StudioApp app, Bill billToEdit, String initialTemplateId) {
        this.app = app;
        this.billDao = new BillDao(app.getDb());
        this.buyerDao = new BuyerDao(app.getDb());
        this.itemDao = new ItemDao(app.getDb());
        this.templateDao = new TemplateDao(app.getDb());
        this.settingsDao = new SettingsDao(app.getDb());
        this.variableDao = new com.invoicestudio.db.VariableDao(app.getDb());

        this.currentSettings = settingsDao.getSettings();
        List<Template> templates = templateDao.getAllTemplates();
        if (templates.isEmpty()) {
            templates = PresetTemplates.getAllPresets();
            for (Template t : templates) templateDao.saveTemplate(t);
        }

        if (initialTemplateId != null) {
            this.currentTemplate = templateDao.getTemplateById(initialTemplateId);
        }
        if (this.currentTemplate == null) {
            this.currentTemplate = templates.get(0);
        }

        this.editingBill = billToEdit;

        getStyleClass().add("bg-app");

        // Top Toolbar
        setTop(createToolbar());

        // Center SplitPane: Form on Left, Live Preview on Right
        SplitPane split = new SplitPane();
        split.getStyleClass().add("bg-transparent");

        Node leftForm = createFormPane(templates);
        Node rightPreview = createPreviewArea();

        split.getItems().addAll(leftForm, rightPreview);
        split.setDividerPositions(0.52);

        setCenter(split);

        initFormData();
        updateTotalsAndPreview();
    }

    public CreateBillView(StudioApp app, Bill billToEdit, String initialTemplateId, ItemRecord initialItem) {
        this(app, billToEdit, initialTemplateId);
        if (initialItem != null && (editingBill == null || editingBill.getItems() == null || editingBill.getItems().isEmpty())) {
            BillItem it = new BillItem(
                "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                initialItem.getName(),
                initialItem.getHsn() != null ? initialItem.getHsn() : "",
                1,
                initialItem.getUnit() != null ? initialItem.getUnit() : "PCS",
                initialItem.getRate(),
                initialItem.getGst(),
                0
            );
            BillItemRow row = new BillItemRow(it);
            itemRows.clear();
            itemsBox.getChildren().clear();
            itemRows.add(row);
            itemsBox.getChildren().add(row);
            updateTotalsAndPreview();
        }
    }

    private Node createToolbar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("bill-toolbar");

        Button backBtn = new Button("← Back");
        backBtn.getStyleClass().addAll("button-sm", "button-secondary");
        backBtn.setTooltip(new Tooltip("Return to History View"));
        backBtn.setOnAction(e -> app.showHistory());

        Label title = new Label(editingBill != null ? "Edit Bill: " + editingBill.getBillNo() : "Create New Document");
        title.getStyleClass().add("card-title");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button saveBtn = new Button("Save Document");
        saveBtn.getStyleClass().addAll("gold-btn");
        saveBtn.setTooltip(new Tooltip("Save invoice to database"));
        saveBtn.setOnAction(e -> saveBill(false, false));

        Button printBtn = new Button("Save & Print");
        printBtn.getStyleClass().addAll("button-secondary");
        printBtn.setTooltip(new Tooltip("Save invoice and open printer"));
        printBtn.setOnAction(e -> saveBill(true, false));

        Button pdfBtn = new Button("Save & PDF");
        pdfBtn.getStyleClass().addAll("button-secondary");
        pdfBtn.setTooltip(new Tooltip("Save invoice and export PDF document"));
        pdfBtn.setOnAction(e -> saveBill(false, true));

        bar.getChildren().addAll(backBtn, title, sp, saveBtn, printBtn, pdfBtn);
        return bar;
    }

    private Node createFormPane(List<Template> templates) {
        VBox form = new VBox(18);
        form.setPadding(new Insets(20));
        form.getStyleClass().add("bill-form");

        // 1. Document Type Tabs
        HBox docTypeTabs = new HBox(8);
        for (DocType dt : DocType.values()) {
            Button btn = new Button(dt.getLabel());
            btn.getStyleClass().addAll("button-sm", dt == selectedDocType ? "gold-btn" : "button-secondary");
            btn.setOnAction(e -> {
                selectedDocType = dt;
                for (Node n : docTypeTabs.getChildren()) {
                    n.getStyleClass().removeAll("gold-btn", "button-secondary");
                    n.getStyleClass().add("button-secondary");
                }
                btn.getStyleClass().remove("button-secondary");
                btn.getStyleClass().add("gold-btn");
                updateTotalsAndPreview();
            });
            docTypeTabs.getChildren().add(btn);
        }
        form.getChildren().add(docTypeTabs);

        // 2. Template, Bill No, Date
        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(12); metaGrid.setVgap(8);

        metaGrid.add(new Label("Template:"), 0, 0);
        templateCombo.setItems(FXCollections.observableArrayList(templates));
        templateCombo.setValue(currentTemplate);
        templateCombo.setOnAction(e -> {
            currentTemplate = templateCombo.getValue();
            rebuildLineItemsUI();
            updateTotalsAndPreview();
        });
        templateCombo.setMaxWidth(Double.MAX_VALUE);
        metaGrid.add(templateCombo, 0, 1);

        metaGrid.add(new Label("Bill Number:"), 1, 0);
        billNoField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        metaGrid.add(billNoField, 1, 1);

        metaGrid.add(new Label("Bill Date:"), 2, 0);
        datePicker.setMaxWidth(Double.MAX_VALUE);
        datePicker.setOnAction(e -> updateTotalsAndPreview());
        metaGrid.add(datePicker, 2, 1);

        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(40);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(30);
        ColumnConstraints c3 = new ColumnConstraints(); c3.setPercentWidth(30);
        metaGrid.getColumnConstraints().addAll(c1, c2, c3);

        form.getChildren().add(metaGrid);

        // 3. Buyer Section
        VBox buyerSec = new VBox(10);
        buyerSec.getStyleClass().add("panel-box");

        HBox byrTop = new HBox(10);
        byrTop.setAlignment(Pos.CENTER_LEFT);
        Label byrLbl = new Label("BUYER / RECIPIENT DETAILS");
        byrLbl.getStyleClass().add("section-eyebrow");

        Region bSp = new Region();
        HBox.setHgrow(bSp, Priority.ALWAYS);

        // Searchable / Filterable Buyer Dropdown
        List<Buyer> buyers = buyerDao.getAllBuyers();
        ObservableList<Buyer> masterBuyerList = FXCollections.observableArrayList(buyers);
        FilteredList<Buyer> filteredBuyers = new FilteredList<>(masterBuyerList, p -> true);

        buyerCombo.setEditable(true);
        buyerCombo.setItems(filteredBuyers);
        buyerCombo.setPrefWidth(280);

        buyerCombo.setConverter(new StringConverter<Buyer>() {
            @Override
            public String toString(Buyer b) {
                return b == null ? "" : b.getName();
            }

            @Override
            public Buyer fromString(String string) {
                if (string == null || string.isBlank()) return null;
                for (Buyer b : masterBuyerList) {
                    if (b.getName() != null && b.getName().equalsIgnoreCase(string.trim())) {
                        return b;
                    }
                }
                return null;
            }
        });

        buyerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Buyer b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String sub = "";
                    if (b.getPhone() != null && !b.getPhone().isBlank()) sub += " • 📞 " + b.getPhone();
                    if (b.getGst() != null && !b.getGst().isBlank()) sub += " • GST: " + b.getGst();
                    setText(b.getName() + sub);
                }
            }
        });

        TextField buyerEditor = buyerCombo.getEditor();
        buyerEditor.setPromptText("Search buyer by name, phone, GST...");
        buyerEditor.textProperty().addListener((obs, o, v) -> {
            Buyer cur = buyerCombo.getSelectionModel().getSelectedItem();
            if (cur != null && cur.getName() != null && cur.getName().equals(v)) {
                return;
            }
            String q = v != null ? v.trim().toLowerCase() : "";
            filteredBuyers.setPredicate(b -> {
                if (q.isEmpty()) return true;
                boolean mName = b.getName() != null && b.getName().toLowerCase().contains(q);
                boolean mPhone = b.getPhone() != null && b.getPhone().toLowerCase().contains(q);
                boolean mGst = b.getGst() != null && b.getGst().toLowerCase().contains(q);
                boolean mAddress = b.getAddress() != null && b.getAddress().toLowerCase().contains(q);
                boolean mTrade = b.getCustom() != null && b.getCustom().getOrDefault("trade_name", "").toLowerCase().contains(q);
                return mName || mPhone || mGst || mAddress || mTrade;
            });
            if (!buyerCombo.isShowing() && buyerEditor.isFocused() && !q.isEmpty()) {
                buyerCombo.show();
            }
        });

        buyerCombo.setOnAction(e -> {
            Buyer b = buyerCombo.getValue();
            if (b != null) {
                buyerNameField.setText(b.getName() != null ? b.getName() : "");
                buyerAddressField.setText(b.getAddress() != null ? b.getAddress() : "");
                buyerGstField.setText(b.getGst() != null ? b.getGst() : "");
                buyerPhoneField.setText(b.getPhone() != null ? b.getPhone() : "");
                buyerStateField.setText(b.getState() != null ? b.getState() : "");
                buyerStateCodeField.setText(b.getEffectiveStateCode() != null ? b.getEffectiveStateCode() : "");

                // Auto-fill default transport if assigned to buyer
                if (b.getDefaultTransportId() != null && !b.getDefaultTransportId().isBlank()) {
                    Transport tr = app.getData().getAllTransports().stream()
                            .filter(t -> b.getDefaultTransportId().equalsIgnoreCase(t.getId()))
                            .findFirst().orElse(null);
                    if (tr != null) {
                        transportField.setText(tr.getName() != null ? tr.getName() : "");
                        if (tr.getVehicleNo() != null && !tr.getVehicleNo().isBlank() && vehicleField.getText().isBlank()) {
                            vehicleField.setText(tr.getVehicleNo());
                        }
                    }
                }
                updateTotalsAndPreview();
            }
        });

        Button clearBuyerBtn = new Button("✕");
        clearBuyerBtn.getStyleClass().addAll("button-sm", "button-secondary");
        clearBuyerBtn.setTooltip(new Tooltip("Clear Search Filter"));
        clearBuyerBtn.setOnAction(e -> {
            buyerCombo.setValue(null);
            buyerEditor.clear();
            filteredBuyers.setPredicate(p -> true);
        });

        byrTop.getChildren().addAll(byrLbl, bSp, buyerCombo, clearBuyerBtn);
        buyerSec.getChildren().add(byrTop);

        GridPane byrGrid = new GridPane();
        byrGrid.setHgap(10); byrGrid.setVgap(8);

        byrGrid.add(new Label("Buyer Name:"), 0, 0);
        buyerNameField.setPromptText("e.g. Acme Corp");
        buyerNameField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        byrGrid.add(buyerNameField, 0, 1);

        byrGrid.add(new Label("GSTIN:"), 1, 0);
        buyerGstField.setPromptText("27ABCDE1234F1Z5");
        buyerGstField.textProperty().addListener((obs, o, v) -> {
            if (v != null && v.trim().length() >= 2 && buyerStateCodeField.getText().isBlank()) {
                String code = v.trim().substring(0, 2);
                if (code.matches("\\d{2}")) {
                    buyerStateCodeField.setText(code);
                }
            }
            updateTotalsAndPreview();
        });
        byrGrid.add(buyerGstField, 1, 1);

        byrGrid.add(new Label("Phone / Contact:"), 2, 0);
        buyerPhoneField.setPromptText("9820012345");
        buyerPhoneField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        byrGrid.add(buyerPhoneField, 2, 1);

        byrGrid.add(new Label("Billing Address:"), 0, 2, 2, 1);
        buyerAddressField.setPromptText("Address line 1, city, state, pin...");
        buyerAddressField.setPrefRowCount(2);
        buyerAddressField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        byrGrid.add(buyerAddressField, 0, 3, 2, 1);

        byrGrid.add(new Label("Place of Supply (State & Code):"), 2, 2);
        HBox stCodeBox = new HBox(6);
        stCodeBox.setAlignment(Pos.CENTER_LEFT);
        buyerStateField.setPromptText("e.g. Maharashtra");
        HBox.setHgrow(buyerStateField, Priority.ALWAYS);
        buyerStateField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        buyerStateCodeField.setPromptText("Code (27)");
        buyerStateCodeField.setPrefWidth(80);
        buyerStateCodeField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        stCodeBox.getChildren().addAll(buyerStateField, buyerStateCodeField);
        byrGrid.add(stCodeBox, 2, 3);

        saveBuyerCb.setSelected(true);
        byrGrid.add(saveBuyerCb, 0, 4, 3, 1);

        ColumnConstraints bc1 = new ColumnConstraints(); bc1.setPercentWidth(40);
        ColumnConstraints bc2 = new ColumnConstraints(); bc2.setPercentWidth(30);
        ColumnConstraints bc3 = new ColumnConstraints(); bc3.setPercentWidth(30);
        byrGrid.getColumnConstraints().addAll(bc1, bc2, bc3);

        buyerSec.getChildren().add(byrGrid);
        form.getChildren().add(buyerSec);

        // 4. Logistics & Credit note fields (accordion / collapsible)
        TitledPane logisticsPane = new TitledPane("Logistics, Transport & Order References", createLogisticsBox());
        logisticsPane.setExpanded(false);
        form.getChildren().add(logisticsPane);

        // 4b. Custom Bill Fields (fixed-scope variables) — only shown if any exist
        Node customFieldsPane = createCustomBillFieldsPane();
        if (customFieldsPane != null) form.getChildren().add(customFieldsPane);

        // 5. Line Items Editor
        VBox itemsSec = new VBox(8);
        HBox itTop = new HBox(8);
        itTop.setAlignment(Pos.CENTER_LEFT);
        Label itLbl = new Label("LINE ITEMS");
        itLbl.getStyleClass().add("section-eyebrow");

        Region itSp = new Region();
        HBox.setHgrow(itSp, Priority.ALWAYS);

        Button addItemBtn = new Button("Add Line Item");
        addItemBtn.getStyleClass().addAll("button-sm", "gold-btn");
        addItemBtn.setGraphic(IconHelper.getIcon("plus", 11, "#0B0E13"));
        addItemBtn.setGraphicTextGap(6);
        addItemBtn.getStyleClass().add("btn-dense");
        addItemBtn.setOnAction(e -> {
            BillItemRow row = new BillItemRow();
            itemRows.add(row);
            itemsBox.getChildren().add(row);
            refreshSrNumbers();
            updateTotalsAndPreview();
        });

        itTop.getChildren().addAll(itLbl, itSp, addItemBtn);
        lineItemsHeaderContainer.getChildren().setAll(createLineItemsHeader());
        itemsSec.getChildren().addAll(itTop, lineItemsHeaderContainer, itemsBox);
        form.getChildren().add(itemsSec);

        // Resize listener: cap description field growth so it never overflows the HBox
        itemsBox.widthProperty().addListener((obs, oldW, newW) -> updateItemDescMaxWidth(newW.doubleValue()));

        // 6. Summary, Totals & Actions
        form.getChildren().add(createSummaryBox());

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-base");
        return scroll;
    }

    private Node createLogisticsBox() {
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(10));

        g.add(new Label("PO / Order No:"), 0, 0);
        poNoField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        g.add(poNoField, 0, 1);

        g.add(new Label("Transport Name:"), 1, 0);
        transportField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        g.add(transportField, 1, 1);

        g.add(new Label("Vehicle No:"), 2, 0);
        vehicleField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        g.add(vehicleField, 2, 1);

        g.add(new Label("E-Way Bill No:"), 3, 0);
        ewayField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        g.add(ewayField, 3, 1);

        g.add(new Label("Parcels / Bales:"), 4, 0);
        parcelField.setTooltip(new Tooltip("Total shipment cartons, bales, or parcels count"));
        parcelField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        g.add(parcelField, 4, 1);

        // Credit note extra fields
        g.add(new Label("Against Invoice #:"), 0, 2);
        refInvoiceField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        g.add(refInvoiceField, 0, 3);

        g.add(new Label("Adjustment Reason:"), 1, 2, 3, 1);
        creditReasonField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        g.add(creditReasonField, 1, 3, 3, 1);

        return g;
    }

    /**
     * Builds a collapsible 'Custom Bill Fields' section for all fixed-scope
     * custom variables.  Returns null if there are no fixed-scope variables.
     */
    private Node createCustomBillFieldsPane() {
        java.util.List<VariableDef> fixedVars;
        try {
            fixedVars = variableDao.getFixedScopeVariables();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        if (fixedVars.isEmpty()) return null;

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(8); grid.setPadding(new Insets(10));

        int col = 0, row = 0;
        int cols = Math.min(3, fixedVars.size()); // max 3 columns
        for (int i = 0; i < cols; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }

        for (VariableDef vd : fixedVars) {
            VBox fieldBox = new VBox(4);
            Label lbl = new Label(vd.getLabel().toUpperCase() + ":");
            lbl.getStyleClass().add("field-label");
            TextField tf = new TextField();
            tf.setPromptText(vd.getDefaultValue().isEmpty() ? vd.getLabel() : vd.getDefaultValue());
            tf.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
            fieldBox.getChildren().addAll(lbl, tf);
            customFixedInputs.put(vd.getKey(), tf);
            grid.add(fieldBox, col, row);
            col++;
            if (col >= cols) { col = 0; row++; }
        }

        TitledPane pane = new TitledPane("Custom Bill Fields (" + fixedVars.size() + ")", grid);
        pane.setExpanded(true);
        return pane;
    }

    /**
     * Adjusts the description field's maxWidth on all item rows whenever the
     * itemsBox container is resized, preventing horizontal overflow / clipping.
     */
    private void updateItemDescMaxWidth(double containerWidth) {
        if (containerWidth <= 0) return;
        // Fixed overhead: pick-btn(30) + save-btn(30) + delete-btn(30) + spacing
        // We calculate the sum of all fixed-width columns via getColumnControlWidth
        // from the active template columns and add spacers.
        double fixedTotal = 30 + 30 + 30; // pick / save / delete buttons
        List<TableColumn> cols = getActiveTableColumns();
        for (TableColumn col : cols) {
            String k = col.getKey() != null ? col.getKey().toLowerCase().trim() : "";
            if (!k.equals("desc") && !k.equals("description") && !k.equals("name") && !k.equals("item_name")) {
                fixedTotal += getColumnControlWidth(col) + 8; // +8 HBox spacing
            }
        }
        double descMax = Math.max(140, containerWidth - fixedTotal - 24);
        for (BillItemRow bir : itemRows) {
            bir.descField.setMaxWidth(descMax);
        }
    }

    private Node createSummaryBox() {
        HBox box = new HBox(16);
        box.getStyleClass().add("panel-box");

        // Left notes & payment status
        VBox left = new VBox(10);
        left.getChildren().add(new Label("Notes & Terms:"));
        notesField.setPrefRowCount(3);
        notesField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        left.getChildren().add(notesField);

        HBox opts = new HBox(10);
        opts.setAlignment(Pos.CENTER_LEFT);
        statusCombo.setItems(FXCollections.observableArrayList(BillStatus.UNPAID, BillStatus.PAID));
        statusCombo.setValue(BillStatus.UNPAID);
        statusCombo.setOnAction(e -> updateTotalsAndPreview());

        repeatCombo.setItems(FXCollections.observableArrayList(RepeatCadence.values()));
        repeatCombo.setValue(RepeatCadence.NONE);
        repeatCombo.setOnAction(e -> updateTotalsAndPreview());

        opts.getChildren().addAll(new Label("Status:"), statusCombo, new Label("Repeat:"), repeatCombo);
        left.getChildren().add(opts);
        HBox.setHgrow(left, Priority.ALWAYS);

        // Right totals table
        VBox right = new VBox(6);
        right.setPrefWidth(260);

        HBox discRow = new HBox(8);
        discRow.setAlignment(Pos.CENTER_LEFT);
        Label dLbl = new Label("Global Discount (%):");
        discountPctField.setPrefWidth(60);
        discountPctField.textProperty().addListener((obs, o, v) -> updateTotalsAndPreview());
        discRow.getChildren().addAll(dLbl, discountPctField);
        right.getChildren().add(discRow);

        right.getChildren().addAll(
                createTotalLine("Subtotal:", subtotalLbl),
                createTotalLine("Total Discount:", discountLbl),
                createTotalLine("Taxable Value:", taxableLbl),
                createTotalLine("CGST:", cgstLbl),
                createTotalLine("SGST:", sgstLbl),
                createTotalLine("IGST:", igstLbl),
                new Separator(),
                createTotalLine("Grand Total:", grandTotalLbl)
        );
        grandTotalLbl.getStyleClass().add("grand-total-value");

        amountInWordsLbl.getStyleClass().add("text-note");
        amountInWordsLbl.setWrapText(true);
        right.getChildren().add(amountInWordsLbl);

        box.getChildren().addAll(left, right);
        return box;
    }

    private Node createTotalLine(String label, Label valLbl) {
        HBox h = new HBox();
        Label l = new Label(label);
        l.getStyleClass().add("text-muted");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        valLbl.getStyleClass().add("card-title-sm");
        h.getChildren().addAll(l, sp, valLbl);
        return h;
    }

    private Node createPreviewArea() {
        VBox box = new VBox(8);
        box.getStyleClass().add("panel-deep");

        HBox ctrl = new HBox(8);
        ctrl.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("LIVE DOCUMENT PREVIEW");
        title.getStyleClass().add("overline");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button zOut = new Button("−");
        zOut.getStyleClass().addAll("button-sm", "button-secondary");
        zOut.setTooltip(new Tooltip("Zoom Out"));

        Label zoomValLbl = new Label("75%");
        zoomValLbl.getStyleClass().add("zoom-value");

        Button zIn = new Button("+");
        zIn.getStyleClass().addAll("button-sm", "button-secondary");
        zIn.setTooltip(new Tooltip("Zoom In"));

        Button z100 = new Button("100%");
        z100.getStyleClass().addAll("button-sm", "button-secondary");
        z100.setTooltip(new Tooltip("Reset Zoom to 100%"));

        Button zFit = new Button("Fit");
        zFit.getStyleClass().addAll("button-sm", "button-secondary");
        zFit.setTooltip(new Tooltip("Fit Preview to Pane"));

        Runnable updateZoom = () -> {
            zoomValLbl.setText((int) Math.round(previewPane.getZoom() * 100) + "%");
        };

        zOut.setOnAction(e -> {
            previewPane.setZoom(Math.max(0.2, previewPane.getZoom() - 0.1));
            updateZoom.run();
        });
        zIn.setOnAction(e -> {
            previewPane.setZoom(Math.min(3.0, previewPane.getZoom() + 0.1));
            updateZoom.run();
        });
        z100.setOnAction(e -> {
            previewPane.setZoom(1.0);
            updateZoom.run();
        });
        zFit.setOnAction(e -> {
            previewPane.setZoom(0.75);
            updateZoom.run();
        });

        ctrl.getChildren().addAll(title, sp, zOut, zoomValLbl, zIn, z100, zFit);

        ScrollPane previewScroll = new ScrollPane(previewPane);
        previewScroll.setFitToWidth(true);
        // fitToHeight OFF — forcing viewport height collapses the scroll range
        // when zoomed (bottom space vanishes, top unreachable). Same fix as
        // the History bill viewer.
        previewScroll.setFitToHeight(false);
        previewScroll.getStyleClass().add("scroll-deep");
        VBox.setVgrow(previewScroll, Priority.ALWAYS);

        box.getChildren().addAll(ctrl, previewScroll);
        return box;
    }

    private void initFormData() {
        if (editingBill != null) {
            selectedDocType = editingBill.getDocType();
            billNoField.setText(editingBill.getBillNo());
            try { datePicker.setValue(LocalDate.parse(editingBill.getDate())); } catch (Exception ignored) {}
            buyerNameField.setText(editingBill.getVariables().getOrDefault("buyer_name", ""));
            buyerAddressField.setText(editingBill.getVariables().getOrDefault("buyer_address", ""));
            buyerGstField.setText(editingBill.getVariables().getOrDefault("buyer_gst", ""));
            buyerPhoneField.setText(editingBill.getVariables().getOrDefault("buyer_phone", ""));
            buyerStateField.setText(editingBill.getVariables().getOrDefault("buyer_state", ""));
            buyerStateCodeField.setText(editingBill.getVariables().getOrDefault("buyer_state_code", ""));
            poNoField.setText(editingBill.getVariables().getOrDefault("po_no", ""));
            transportField.setText(editingBill.getVariables().getOrDefault("transport_name", ""));
            vehicleField.setText(editingBill.getVariables().getOrDefault("vehicle_no", ""));
            ewayField.setText(editingBill.getVariables().getOrDefault("e_way_bill", ""));
            parcelField.setText(String.valueOf(editingBill.getParcel() > 0 ? editingBill.getParcel() : 1));
            refInvoiceField.setText(editingBill.getVariables().getOrDefault("ref_invoice_no", ""));
            creditReasonField.setText(editingBill.getVariables().getOrDefault("credit_reason", ""));
            discountPctField.setText(String.valueOf(editingBill.getDiscountPct()));
            notesField.setText(editingBill.getNotes());
            statusCombo.setValue(editingBill.getStatus());
            repeatCombo.setValue(editingBill.getRepeat());

            // Restore fixed-scope custom variable values
            for (Map.Entry<String, TextField> e : customFixedInputs.entrySet()) {
                e.getValue().setText(editingBill.getVariables().getOrDefault(e.getKey(), ""));
            }

            for (BillItem it : editingBill.getItems()) {
                BillItemRow r = new BillItemRow(it);
                itemRows.add(r);
                itemsBox.getChildren().add(r);
            }
        } else {
            billNoField.setText(BillingService.nextBillNo(currentSettings));
            // Pre-fill custom fixed fields with their default values
            for (Map.Entry<String, TextField> e : customFixedInputs.entrySet()) {
                // default value already set as promptText; leave empty unless a real default
            }
            BillItemRow r = new BillItemRow();
            itemRows.add(r);
            itemsBox.getChildren().add(r);
        }
        previewPane.setZoom(0.75);
    }

    private boolean isInterStateSale() {
        String buyerCode = buyerStateCodeField.getText() != null ? buyerStateCodeField.getText().trim() : "";
        if (buyerCode.isBlank() && buyerGstField.getText() != null && buyerGstField.getText().trim().length() >= 2) {
            String prefix = buyerGstField.getText().trim().substring(0, 2);
            if (prefix.matches("\\d{2}")) {
                buyerCode = prefix;
            }
        }

        String sellerCode = "";
        if (currentSettings != null && currentSettings.getBusiness() != null) {
            sellerCode = currentSettings.getBusiness().getStateCode() != null ? currentSettings.getBusiness().getStateCode().trim() : "";
            if (sellerCode.isBlank() && currentSettings.getBusiness().getGstin() != null && currentSettings.getBusiness().getGstin().trim().length() >= 2) {
                String prefix = currentSettings.getBusiness().getGstin().trim().substring(0, 2);
                if (prefix.matches("\\d{2}")) {
                    sellerCode = prefix;
                }
            }
        }

        // 1. If state codes are available on both sides, compare them
        if (!buyerCode.isBlank() && !sellerCode.isBlank()) {
            return !buyerCode.equalsIgnoreCase(sellerCode);
        }

        // 2. Fallback to state names
        String buyerState = buyerStateField.getText() != null ? buyerStateField.getText().trim() : "";
        String sellerState = currentSettings != null && currentSettings.getBusiness() != null && currentSettings.getBusiness().getState() != null
                ? currentSettings.getBusiness().getState().trim() : "";
        if (!buyerState.isBlank() && !sellerState.isBlank()) {
            return !buyerState.equalsIgnoreCase(sellerState);
        }

        // 3. Fallback to settings toggle
        return currentSettings != null && currentSettings.isInterState();
    }

    private void updateTotalsAndPreview() {
        List<BillItem> items = itemRows.stream().map(BillItemRow::getItem).collect(Collectors.toList());
        double disc = 0;
        try { disc = Double.parseDouble(discountPctField.getText()); } catch (Exception ignored) {}

        boolean interState = isInterStateSale();
        BillTotals totals = BillingService.computeTotals(items, disc, interState);
        String words = BillingService.amountInWords(totals.getGrandTotal());

        subtotalLbl.setText(String.format("₹%.2f", totals.getSubtotal()));
        discountLbl.setText(String.format("₹%.2f", totals.getDiscount()));
        taxableLbl.setText(String.format("₹%.2f", totals.getTaxable()));
        cgstLbl.setText(String.format("₹%.2f", totals.getCgst()));
        sgstLbl.setText(String.format("₹%.2f", totals.getSgst()));
        igstLbl.setText(String.format("₹%.2f", totals.getIgst()));
        grandTotalLbl.setText(String.format("₹%.2f", totals.getGrandTotal()));
        amountInWordsLbl.setText(words);

        // Debounced visual preview to eliminate scene-graph rebuild lag while typing
        previewDebounce.setOnFinished(e -> {
            if (currentTemplate != null) {
                Bill b = buildBillObject(totals, words);
                previewPane.render(currentTemplate, b, currentSettings, 0, 1);
            }
        });
        previewDebounce.playFromStart();
    }

    private Bill buildBillObject(BillTotals totals, String words) {
        Bill b = new Bill();
        b.setId(editingBill != null ? editingBill.getId() : "bill_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        b.setBillNo(billNoField.getText());
        b.setDate(datePicker.getValue() != null ? datePicker.getValue().toString() : BillingService.todayISO());
        b.setDocType(selectedDocType);
        b.setTemplateId(currentTemplate != null ? currentTemplate.getId() : "");
        b.setTemplateName(currentTemplate != null ? currentTemplate.getName() : "");

        Map<String, String> vars = new HashMap<>();
        vars.put("buyer_name", buyerNameField.getText());
        vars.put("buyer_address", buyerAddressField.getText());
        vars.put("buyer_gst", buyerGstField.getText());
        vars.put("buyer_phone", buyerPhoneField.getText());
        vars.put("buyer_state", buyerStateField.getText());
        vars.put("buyer_state_code", buyerStateCodeField.getText().trim());

        // Copy custom buyer fields from selected buyer if present
        Buyer selectedBuyer = buyerCombo.getValue();
        if (selectedBuyer != null && selectedBuyer.getCustom() != null) {
            for (Map.Entry<String, String> entry : selectedBuyer.getCustom().entrySet()) {
                vars.put("buyer_" + entry.getKey(), entry.getValue());
            }
        }

        vars.put("po_no", poNoField.getText());
        vars.put("transport_name", transportField.getText());
        vars.put("vehicle_no", vehicleField.getText());
        vars.put("e_way_bill", ewayField.getText());

        int parcelCount = 1;
        try {
            parcelCount = Integer.parseInt(parcelField.getText().trim());
            if (parcelCount <= 0) parcelCount = 1;
        } catch (Exception ignored) {}
        b.setParcel(parcelCount);
        vars.put("parcel", String.valueOf(parcelCount));
        vars.put("parcels", String.valueOf(parcelCount));

        vars.put("ref_invoice_no", refInvoiceField.getText());
        vars.put("credit_reason", creditReasonField.getText());

        // Include fixed-scope custom variable values
        for (Map.Entry<String, TextField> e : customFixedInputs.entrySet()) {
            vars.put(e.getKey(), e.getValue().getText());
        }

        b.setVariables(vars);

        b.setItems(itemRows.stream().map(BillItemRow::getItem).collect(Collectors.toList()));
        double disc = 0;
        try { disc = Double.parseDouble(discountPctField.getText()); } catch (Exception ignored) {}
        b.setDiscountPct(disc);

        b.setTotals(totals);
        b.setAmountInWords(words);
        b.setNotes(notesField.getText());
        b.setStatus(statusCombo.getValue() != null ? statusCombo.getValue() : BillStatus.UNPAID);
        b.setRepeat(repeatCombo.getValue() != null ? repeatCombo.getValue() : RepeatCadence.NONE);
        if (repeatEndPicker.getValue() != null) b.setRepeatEndDate(repeatEndPicker.getValue().toString());

        return b;
    }

    private void saveBill(boolean printAfter, boolean pdfAfter) {
        if (billNoField.getText() == null || billNoField.getText().isBlank()) {
            Toast.show(app.getRootPane(), "Missing Information", "Please enter a valid bill number.", true);
            return;
        }

        List<BillItem> items = itemRows.stream().map(BillItemRow::getItem).collect(Collectors.toList());
        double disc = 0;
        try { disc = Double.parseDouble(discountPctField.getText()); } catch (Exception ignored) {}
        boolean interState = isInterStateSale();
        BillTotals totals = BillingService.computeTotals(items, disc, interState);
        String words = BillingService.amountInWords(totals.getGrandTotal());

        final Bill bill = buildBillObject(totals, words);
        final boolean isNew = (editingBill == null);
        final boolean saveBuyer = saveBuyerCb.isSelected() && !buyerNameField.getText().isBlank();
        final String buyerName = buyerNameField.getText();
        final String buyerAddress = buyerAddressField.getText();
        final String buyerGst = buyerGstField.getText();
        final String buyerPhone = buyerPhoneField.getText();
        final String buyerState = buyerStateField.getText();
        final String buyerStateCode = buyerStateCodeField.getText().trim();

        // Immediate responsive toast
        Toast.show(app.getRootPane(), "Saving...", "Writing bill " + bill.getBillNo() + "...", false);

        // Perform database operations in background thread
        app.getDbExecutor().execute(() -> {
            try {
                app.getData().saveBill(bill);

                // Auto increment counter in settings if new bill
                if (isNew) {
                    currentSettings.setBillNoNext(currentSettings.getBillNoNext() + 1);
                    app.getData().saveSettings(currentSettings);
                }

                // Save buyer to directory if enabled
                if (saveBuyer) {
                    Buyer existing = buyerDao.findByName(buyerName);
                    if (existing == null) {
                        Buyer nb = new Buyer(
                                "byr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                                buyerName,
                                buyerAddress,
                                buyerGst,
                                buyerPhone,
                                buyerState,
                                buyerStateCode
                        );
                        buyerDao.saveBuyer(nb);
                    }
                }

                app.getData().invalidateBills();

                Platform.runLater(() -> {
                    app.reloadAllData();
                    Toast.show(app.getRootPane(), "Bill Saved", bill.getBillNo() + " saved successfully.", false);

                    if (printAfter) {
                        double prevZoom = previewPane.getZoom();
                        previewPane.setZoom(1.0);
                        try {
                            PrintingService.printTemplate(previewPane, currentTemplate, app.getPrimaryStage(), 1, bill.getBillNo());
                        } finally {
                            previewPane.setZoom(prevZoom);
                        }
                    }

                    if (pdfAfter) {
                        FileChooser fc = new FileChooser();
                        fc.setTitle("Export Invoice PDF");
                        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Document (*.pdf)", "*.pdf"));
                        fc.setInitialFileName(bill.getBillNo() + ".pdf");
                        File dest = fc.showSaveDialog(app.getPrimaryStage());
                        if (dest != null) {
                            app.getDbExecutor().execute(() -> {
                                try {
                                    PdfExportService.exportBillPdf(bill, currentTemplate, currentSettings, dest, 1);
                                    Platform.runLater(() -> Toast.show(app.getRootPane(), "PDF Exported", "Saved to " + dest.getName(), false));
                                } catch (Exception ex) {
                                    Platform.runLater(() -> Toast.show(app.getRootPane(), "PDF Export Failed", ex.getMessage(), true));
                                }
                            });
                        }
                    }

                    app.showHistory();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> Toast.show(app.getRootPane(), "Save Failed", "Error saving bill: " + ex.getMessage(), true));
            }
        });
    }

    private List<TableColumn> getActiveTableColumns() {
        if (currentTemplate != null && currentTemplate.getElements() != null) {
            for (TemplateElement el : currentTemplate.getElements()) {
                if (el.getType() == ElementType.TABLE && !el.isHidden() && el.getColumns() != null && !el.getColumns().isEmpty()) {
                    return el.getColumns();
                }
            }
            // If all table elements are marked hidden or none found, fallback to first table element
            for (TemplateElement el : currentTemplate.getElements()) {
                if (el.getType() == ElementType.TABLE && el.getColumns() != null && !el.getColumns().isEmpty()) {
                    return el.getColumns();
                }
            }
        }
        return PresetTemplates.defaultItemColumns();
    }

    private double getColumnControlWidth(TableColumn col) {
        if (col == null || col.getKey() == null) return 60;
        String k = col.getKey().toLowerCase().trim();
        return switch (k) {
            case "sr", "index", "#", "s_no", "sno" -> 30;
            case "hsn", "sac", "hsn_sac" -> 65;
            case "qty", "quantity" -> 50;
            case "unit" -> 55;
            case "rate", "price", "unit_price" -> 65;
            case "gst", "tax" -> 45;
            case "disc", "discount" -> 45;
            case "taxable", "taxable_value" -> 70;
            case "amount", "total", "total_amount" -> 75;
            default -> {
                if (col.getWidth() > 0) {
                    yield Math.max(50.0, Math.min(130.0, col.getWidth() * 5.5));
                }
                yield 70.0;
            }
        };
    }

    private Node createLineItemsHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("items-header-strip");

        // Catalog pick spacer (matches pick item button)
        Label hPick = new Label("");
        hPick.setPrefWidth(30); hPick.setMinWidth(30); hPick.setMaxWidth(30);
        header.getChildren().add(hPick);

        List<TableColumn> cols = getActiveTableColumns();
        boolean descAdded = false;

        for (TableColumn col : cols) {
            String k = col.getKey() != null ? col.getKey().toLowerCase().trim() : "";
            String labelText = col.getLabel() != null && !col.getLabel().isBlank() ? col.getLabel().toUpperCase() : k.toUpperCase();
            Label lbl = new Label(labelText);

            if ("desc".equals(k) || "description".equals(k) || "name".equals(k) || "item_name".equals(k)) {
                descAdded = true;
                lbl.setMinWidth(140);
                lbl.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(lbl, Priority.ALWAYS);
            } else {
                double w = getColumnControlWidth(col);
                lbl.setPrefWidth(w);
                lbl.setMinWidth(w);
                lbl.setMaxWidth(w);

                if ("right".equalsIgnoreCase(col.getAlign()) || "qty".equals(k) || "rate".equals(k) || "gst".equals(k) || "disc".equals(k) || "taxable".equals(k) || "amount".equals(k)) {
                    lbl.setAlignment(Pos.CENTER_RIGHT);
                } else if ("center".equalsIgnoreCase(col.getAlign()) || "sr".equals(k) || "hsn".equals(k) || "unit".equals(k)) {
                    lbl.setAlignment(Pos.CENTER);
                } else {
                    lbl.setAlignment(Pos.CENTER_LEFT);
                }
            }

            header.getChildren().add(lbl);

            // Catalog save spacer next to description column
            if ("desc".equals(k) || "description".equals(k) || "name".equals(k) || "item_name".equals(k)) {
                Label hSave = new Label("");
                hSave.setPrefWidth(30); hSave.setMinWidth(30); hSave.setMaxWidth(30);
                header.getChildren().add(hSave);
            }
        }

        if (!descAdded) {
            Label hDesc = new Label("ITEM DESCRIPTION");
            hDesc.setMinWidth(140);
            hDesc.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(hDesc, Priority.ALWAYS);
            header.getChildren().add(1, hDesc);

            Label hSave = new Label("");
            hSave.setPrefWidth(30); hSave.setMinWidth(30); hSave.setMaxWidth(30);
            header.getChildren().add(2, hSave);
        }

        // Delete button spacer
        Label hAction = new Label("");
        hAction.setPrefWidth(30); hAction.setMinWidth(30); hAction.setMaxWidth(30);
        header.getChildren().add(hAction);

        return header;
    }

    private void rebuildLineItemsUI() {
        lineItemsHeaderContainer.getChildren().setAll(createLineItemsHeader());
        List<BillItem> currentItems = new ArrayList<>();
        for (BillItemRow r : itemRows) {
            currentItems.add(r.getItem());
        }
        itemRows.clear();
        itemsBox.getChildren().clear();
        if (currentItems.isEmpty()) {
            BillItemRow row = new BillItemRow();
            itemRows.add(row);
            itemsBox.getChildren().add(row);
        } else {
            for (BillItem it : currentItems) {
                BillItemRow row = new BillItemRow(it);
                itemRows.add(row);
                itemsBox.getChildren().add(row);
            }
        }
        refreshSrNumbers();
    }

    private void refreshSrNumbers() {
        for (int i = 0; i < itemRows.size(); i++) {
            itemRows.get(i).srLbl.setText(String.valueOf(i + 1));
        }
    }

    // Inner class for line item row editor
    private class BillItemRow extends HBox {
        private final String itemId;
        private final TextField descField = new TextField();
        private final TextField hsnField = new TextField();
        private final TextField qtyField = new TextField("1");
        private final TextField unitField = new TextField("PCS");
        private final TextField rateField = new TextField("0");
        private final TextField gstField = new TextField("18");
        private final TextField discField = new TextField("0");
        private final Label amountLbl = new Label("₹0.00");
        private final Label taxableLbl = new Label("₹0.00");
        private final Label srLbl = new Label("1");
        private final Map<String, TextField> customInputs = new HashMap<>();
        private final Map<String, String> customData = new HashMap<>();

        public BillItemRow() {
            this(new BillItem("it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), "", "", 1, "PCS", 0, 18, 0));
        }

        public BillItemRow(BillItem it) {
            setSpacing(8);
            setAlignment(Pos.CENTER_LEFT);
            getStyleClass().add("item-row");

            this.itemId = it.getId() != null ? it.getId() : "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            descField.setText(it.getDesc() != null ? it.getDesc() : "");
            hsnField.setText(it.getHsn() != null ? it.getHsn() : "");
            qtyField.setText(String.valueOf(it.getQty()));
            unitField.setText(it.getUnit() != null ? it.getUnit() : "PCS");
            rateField.setText(String.valueOf(it.getRate()));
            gstField.setText(String.valueOf(it.getGst()));
            discField.setText(String.valueOf(it.getDiscPct()));
            if (it.getCustom() != null) {
                customData.putAll(it.getCustom());
            }

            descField.setPromptText("Item Name / Description");
            descField.setMinWidth(140);
            descField.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(descField, Priority.ALWAYS);

            hsnField.setPrefWidth(65); hsnField.setMinWidth(65); hsnField.setMaxWidth(65); hsnField.setPromptText("HSN");
            hsnField.setAlignment(Pos.CENTER);

            qtyField.setPrefWidth(50); qtyField.setMinWidth(50); qtyField.setMaxWidth(50); qtyField.setPromptText("Qty");
            qtyField.setAlignment(Pos.CENTER_RIGHT);

            unitField.setPrefWidth(55); unitField.setMinWidth(55); unitField.setMaxWidth(55); unitField.setPromptText("Unit");
            unitField.setAlignment(Pos.CENTER);

            rateField.setPrefWidth(65); rateField.setMinWidth(65); rateField.setMaxWidth(65); rateField.setPromptText("Rate");
            rateField.setAlignment(Pos.CENTER_RIGHT);

            gstField.setPrefWidth(45); gstField.setMinWidth(45); gstField.setMaxWidth(45); gstField.setPromptText("GST");
            gstField.setAlignment(Pos.CENTER_RIGHT);

            discField.setPrefWidth(45); discField.setMinWidth(45); discField.setMaxWidth(45); discField.setPromptText("Disc");
            discField.setAlignment(Pos.CENTER_RIGHT);

            taxableLbl.setPrefWidth(70); taxableLbl.setMinWidth(70); taxableLbl.setMaxWidth(70);
            taxableLbl.setAlignment(Pos.CENTER_RIGHT);
            taxableLbl.getStyleClass().add("cell-bold-secondary");

            amountLbl.setPrefWidth(75); amountLbl.setMinWidth(75); amountLbl.setMaxWidth(75);
            amountLbl.setAlignment(Pos.CENTER_RIGHT);
            amountLbl.getStyleClass().add("cell-bold");

            srLbl.setPrefWidth(30); srLbl.setMinWidth(30); srLbl.setMaxWidth(30);
            srLbl.setAlignment(Pos.CENTER);
            srLbl.getStyleClass().add("text-muted");

            descField.textProperty().addListener((obs, o, v) -> updateRowAmount());
            hsnField.textProperty().addListener((obs, o, v) -> updateRowAmount());
            qtyField.textProperty().addListener((obs, o, v) -> updateRowAmount());
            rateField.textProperty().addListener((obs, o, v) -> updateRowAmount());
            gstField.textProperty().addListener((obs, o, v) -> updateRowAmount());
            discField.textProperty().addListener((obs, o, v) -> updateRowAmount());

            // Catalog Pick Button
            Button catBtn = new Button();
            catBtn.setGraphic(IconHelper.getIcon("items", 12, "#D9A13B"));
            catBtn.getStyleClass().addAll("button-sm", "button-secondary");
            catBtn.setTooltip(new Tooltip("Pick Item from Catalog"));
            catBtn.setPrefSize(30, 28); catBtn.setMinSize(30, 28); catBtn.setMaxSize(30, 28);
            catBtn.getStyleClass().add("icon-btn");
            catBtn.setOnAction(e -> pickCatalogItem(this));
            getChildren().add(catBtn);

            // Catalog Save Button
            Button saveCatBtn = new Button();
            saveCatBtn.setGraphic(IconHelper.getIcon("upload", 12, "#CBD5E1"));
            saveCatBtn.getStyleClass().addAll("button-sm", "button-secondary");
            saveCatBtn.setTooltip(new Tooltip("Save Item to Catalog"));
            saveCatBtn.setPrefSize(30, 28); saveCatBtn.setMinSize(30, 28); saveCatBtn.setMaxSize(30, 28);
            saveCatBtn.getStyleClass().add("icon-btn");
            saveCatBtn.setOnAction(e -> saveItemToCatalog(this));

            List<TableColumn> cols = getActiveTableColumns();
            boolean descRendered = false;

            for (TableColumn col : cols) {
                String k = col.getKey() != null ? col.getKey().toLowerCase().trim() : "";
                switch (k) {
                    case "sr", "index", "#", "s_no", "sno" -> {
                        int idx = itemRows.indexOf(this) + 1;
                        srLbl.setText(String.valueOf(Math.max(1, idx)));
                        getChildren().add(srLbl);
                    }
                    case "desc", "name", "description", "item_name" -> {
                        descRendered = true;
                        getChildren().add(descField);
                        getChildren().add(saveCatBtn);
                    }
                    case "hsn", "sac", "hsn_sac" -> getChildren().add(hsnField);
                    case "qty", "quantity" -> getChildren().add(qtyField);
                    case "unit" -> getChildren().add(unitField);
                    case "rate", "price", "unit_price" -> getChildren().add(rateField);
                    case "gst", "tax" -> getChildren().add(gstField);
                    case "disc", "discount" -> getChildren().add(discField);
                    case "taxable", "taxable_value" -> getChildren().add(taxableLbl);
                    case "amount", "total", "total_amount" -> getChildren().add(amountLbl);
                    default -> {
                        double w = getColumnControlWidth(col);
                        TextField customTf = new TextField(customData.getOrDefault(k, ""));
                        customTf.setPrefWidth(w); customTf.setMinWidth(w); customTf.setMaxWidth(w);
                        customTf.setPromptText(col.getLabel() != null ? col.getLabel() : k);
                        if ("right".equalsIgnoreCase(col.getAlign())) {
                            customTf.setAlignment(Pos.CENTER_RIGHT);
                        } else if ("center".equalsIgnoreCase(col.getAlign())) {
                            customTf.setAlignment(Pos.CENTER);
                        } else {
                            customTf.setAlignment(Pos.CENTER_LEFT);
                        }
                        customTf.textProperty().addListener((obs, o, v) -> {
                            if (v == null || v.isBlank()) customData.remove(k);
                            else customData.put(k, v);
                            updateTotalsAndPreview();
                        });
                        customInputs.put(k, customTf);
                        getChildren().add(customTf);
                    }
                }
            }

            if (!descRendered) {
                getChildren().add(1, descField);
                getChildren().add(2, saveCatBtn);
            }

            Button delBtn = new Button();
            delBtn.setGraphic(IconHelper.getIcon("delete", 12, "#EF4444"));
            delBtn.getStyleClass().addAll("button-sm", "button-danger");
            delBtn.setTooltip(new Tooltip("Remove this line item"));
            delBtn.setPrefSize(30, 28); delBtn.setMinSize(30, 28); delBtn.setMaxSize(30, 28);
            delBtn.getStyleClass().add("icon-btn-danger");
            delBtn.setOnAction(e -> {
                itemRows.remove(this);
                itemsBox.getChildren().remove(this);
                refreshSrNumbers();
                updateTotalsAndPreview();
            });
            getChildren().add(delBtn);

            updateRowAmount();
        }

        private void updateRowAmount() {
            BillItem it = getItem();
            amountLbl.setText(String.format("₹%.2f", it.getAmount()));
            double gross = it.getGross();
            double d = Math.max(0, Math.min(100, it.getDiscPct()));
            double taxable = Math.round((gross - gross * (d / 100.0)) * 100.0) / 100.0;
            taxableLbl.setText(String.format("₹%.2f", taxable));
            updateTotalsAndPreview();
        }

        public BillItem getItem() {
            double q = 1, r = 0, g = 18, d = 0;
            try { q = Double.parseDouble(qtyField.getText()); } catch (Exception ignored) {}
            try { r = Double.parseDouble(rateField.getText()); } catch (Exception ignored) {}
            try { g = Double.parseDouble(gstField.getText()); } catch (Exception ignored) {}
            try { d = Double.parseDouble(discField.getText()); } catch (Exception ignored) {}

            BillItem bi = new BillItem(itemId, descField.getText(), hsnField.getText(), q, unitField.getText(), r, g, d);
            bi.setCustom(new HashMap<>(customData));
            return bi;
        }

        public void applyCatalogItem(ItemRecord ir) {
            descField.setText(ir.getName());
            hsnField.setText(ir.getHsn() != null ? ir.getHsn() : "");
            unitField.setText(ir.getUnit() != null ? ir.getUnit() : "PCS");
            rateField.setText(String.valueOf(ir.getRate()));
            gstField.setText(String.valueOf(ir.getGst()));
            updateRowAmount();
        }
    }

    private void pickCatalogItem(BillItemRow targetRow) {
        Dialog<ItemRecord> dlg = new Dialog<>();
        dlg.setTitle("Select Catalog Item");
        dlg.setHeaderText("Choose item to populate line:");

        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setPrefWidth(420);

        List<ItemRecord> items = itemDao.getAllItems();
        ObservableList<ItemRecord> masterItems = FXCollections.observableArrayList(items);
        FilteredList<ItemRecord> filteredItems = new FilteredList<>(masterItems, p -> true);

        TextField searchField = new TextField();
        searchField.setPromptText("Type to search items by name or HSN...");
        searchField.textProperty().addListener((obs, o, v) -> {
            String q = v != null ? v.trim().toLowerCase() : "";
            filteredItems.setPredicate(it -> {
                if (q.isEmpty()) return true;
                boolean mName = it.getName() != null && it.getName().toLowerCase().contains(q);
                boolean mHsn = it.getHsn() != null && it.getHsn().toLowerCase().contains(q);
                return mName || mHsn;
            });
        });

        ListView<ItemRecord> lv = new ListView<>(filteredItems);
        lv.setPrefHeight(260);
        lv.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(ItemRecord it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) setText(null);
                else setText(it.getName() + " — ₹" + String.format("%.2f", it.getRate()) + " (HSN: " + (it.getHsn() != null ? it.getHsn() : "-") + ", GST: " + (int) it.getGst() + "%)");
            }
        });
        box.getChildren().addAll(searchField, lv);

        dlg.getDialogPane().setContent(box);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResultConverter(b -> b == ButtonType.OK ? lv.getSelectionModel().getSelectedItem() : null);
        DialogHelper.styleDialog(dlg, 440, 380);

        dlg.showAndWait().ifPresent(targetRow::applyCatalogItem);
    }

    private void saveItemToCatalog(BillItemRow row) {
        BillItem bi = row.getItem();
        if (bi.getDesc().isBlank()) {
            Toast.show(app.getRootPane(), "Cannot Save", "Please enter an item name first.", true);
            return;
        }
        ItemRecord ir = new ItemRecord(
                "itm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                bi.getDesc(), bi.getHsn(), bi.getUnit(), bi.getRate(), bi.getGst()
        );
        itemDao.saveItem(ir);
        Toast.show(app.getRootPane(), "Catalog Item Saved", "\"" + bi.getDesc() + "\" added to catalog.", false);
    }
}
