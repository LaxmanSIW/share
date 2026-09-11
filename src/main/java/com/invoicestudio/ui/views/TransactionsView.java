package com.invoicestudio.ui.views;

import com.invoicestudio.model.Buyer;
import com.invoicestudio.model.Transaction;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import com.invoicestudio.ui.UiTheme;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Transactions & Ledger View — Version 4.0.0
 * Financial tracking for Alpha CC (Credit) and CS (Cash) books, sales, payments, check payments, and parcels.
 */
public class TransactionsView extends BorderPane {

    private final StudioApp app;
    private final TableView<Transaction> table = new TableView<>();
    private FilteredList<Transaction> filteredTransactions;

    private final TextField searchField = new TextField();
    private String bookFilter = "ALL"; // "ALL", "CC", "CS"
    private String typeFilter = "ALL"; // "ALL", "sale", "payment"

    // Summary labels
    private final Label totalSalesLabel = new Label("₹ 0.00");
    private final Label totalPaymentsLabel = new Label("₹ 0.00");
    private final Label netBalanceLabel = new Label("₹ 0.00");
    private final Label totalPiecesLabel = new Label("0");
    private final Label countBadge = new Label("0 entries");

    private final DecimalFormat currencyFmt = new DecimalFormat("#,##,##0.00");

    public TransactionsView(StudioApp app) {
        this.app = app;
        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        setTop(createHeaderAndKpis());
        setCenter(createTableArea());

        refresh();
    }

    public void refresh() {
        List<Transaction> list = app.getData().getAllTransactions();
        filteredTransactions = new FilteredList<>(FXCollections.observableArrayList(list), t -> true);
        table.setItems(filteredTransactions);
        applyFilter();
        updateKpiSummary();
    }

    private Node createHeaderAndKpis() {
        VBox rootBox = new VBox(16);
        rootBox.setPadding(new Insets(0, 0, 16, 0));

        // Top Row: Title + New Transaction Button
        HBox topRow = new HBox(16);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Transactions & Ledger");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Alpha CC (Credit) & CS (Cash) Ledger entries and check tracking.");
        subtitle.getStyleClass().add("kpi-subtext");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button exportBtn = UiTheme.secondaryBtn("Export CSV");
        exportBtn.setOnAction(e -> exportToCsv());

        Button newBtn = UiTheme.goldBtn("+ New Transaction");
        newBtn.setOnAction(e -> showAddEditDialog(null));

        topRow.getChildren().addAll(titleBox, sp, exportBtn, newBtn);

        // 4 KPI Summary Cards (Sales, Payments, Balance, Pieces)
        HBox kpiRow = new HBox(12);
        kpiRow.setAlignment(Pos.CENTER_LEFT);

        kpiRow.getChildren().addAll(
            createMiniKpiCard("Total Sales", totalSalesLabel, "metric-sales", "#3b82f6"),
            createMiniKpiCard("Payments Received", totalPaymentsLabel, "metric-payments", "#10b981"),
            createMiniKpiCard("Net Balance", netBalanceLabel, "metric-balance", "#c4703f"),
            createMiniKpiCard("Total Pieces", totalPiecesLabel, "metric-pieces", "#8b5cf6")
        );

        // Filter Bar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        searchField.setPromptText("Search buyer, check #, bill #, notes...");
        searchField.setPrefWidth(280);
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, o, v) -> applyFilter());

        // Book filter toggle buttons
        HBox bookToggle = new HBox(4);
        bookToggle.getStyleClass().add("toggle-group-container");
        Button btnAllBooks = createFilterBtn("All Books", true);
        Button btnCcBook = createFilterBtn("CC Book", false);
        Button btnCsBook = createFilterBtn("CS Book", false);

        btnAllBooks.setOnAction(e -> {
            bookFilter = "ALL";
            updateToggleStyles(bookToggle, btnAllBooks);
            applyFilter();
        });
        btnCcBook.setOnAction(e -> {
            bookFilter = "CC";
            updateToggleStyles(bookToggle, btnCcBook);
            applyFilter();
        });
        btnCsBook.setOnAction(e -> {
            bookFilter = "CS";
            updateToggleStyles(bookToggle, btnCsBook);
            applyFilter();
        });
        bookToggle.getChildren().addAll(btnAllBooks, btnCcBook, btnCsBook);

        // Type filter ComboBox
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("All Types", "Sales Only", "Payments Only");
        typeCombo.setValue("All Types");
        typeCombo.getStyleClass().add("filter-combo");
        typeCombo.valueProperty().addListener((obs, o, v) -> {
            if ("Sales Only".equals(v)) typeFilter = "sale";
            else if ("Payments Only".equals(v)) typeFilter = "payment";
            else typeFilter = "ALL";
            applyFilter();
        });

        countBadge.getStyleClass().addAll("badge", "badge-gray");

        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);

        filterBar.getChildren().addAll(searchField, bookToggle, typeCombo, sp2, countBadge);

        rootBox.getChildren().addAll(topRow, kpiRow, filterBar);
        return rootBox;
    }

    private Button createFilterBtn(String text, boolean active) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-filter-pill");
        if (active) b.getStyleClass().add("active");
        return b;
    }

    private void updateToggleStyles(HBox container, Button activeBtn) {
        for (Node n : container.getChildren()) {
            if (n instanceof Button btn) {
                btn.getStyleClass().remove("active");
            }
        }
        activeBtn.getStyleClass().add("active");
    }

    private VBox createMiniKpiCard(String title, Label valLabel, String styleClass, String accentColor) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.getStyleClass().addAll("card-kpi-mini", styleClass);
        card.setStyle("-fx-border-left-color: " + accentColor + "; -fx-border-left-width: 3px;");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("kpi-subtext");
        lblTitle.setStyle("-fx-font-size: 11px;");

        valLabel.getStyleClass().add("heading-m");
        valLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        card.getChildren().addAll(lblTitle, valLabel);
        return card;
    }

    private Node createTableArea() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("data-table");

        TableColumn<Transaction, String> colDate = new TableColumn<>("Tx Date");
        colDate.setPrefWidth(95);
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTransactionDate()));
        colDate.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setText(null);
                } else {
                    setText(val);
                    setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
                }
            }
        });

        TableColumn<Transaction, String> colBuyer = new TableColumn<>("Buyer / Firm");
        colBuyer.setPrefWidth(180);
        colBuyer.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getBuyerName() != null && !d.getValue().getBuyerName().isBlank()
                ? d.getValue().getBuyerName() : "Unknown Buyer"));
        colBuyer.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setText(null);
                } else {
                    setText(val);
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });

        TableColumn<Transaction, String> colBook = new TableColumn<>("Book");
        colBook.setPrefWidth(70);
        colBook.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBookType()));
        colBook.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(val);
                    badge.getStyleClass().addAll("badge", "CC".equalsIgnoreCase(val) ? "badge-blue" : "badge-green");
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<Transaction, String> colType = new TableColumn<>("Type");
        colType.setPrefWidth(85);
        colType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTransactionType()));
        colType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label("sale".equalsIgnoreCase(val) ? "Sale" : "Payment");
                    badge.getStyleClass().addAll("badge", "sale".equalsIgnoreCase(val) ? "badge-red" : "badge-green");
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<Transaction, Number> colQty = new TableColumn<>("Pieces");
        colQty.setPrefWidth(75);
        colQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getTotalQuantity()));
        colQty.setStyle("-fx-alignment: CENTER-RIGHT;");
        colQty.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null || val.intValue() == 0) {
                    setText(empty ? null : "—");
                } else {
                    setText(String.valueOf(val.intValue()));
                    setStyle("-fx-font-family: 'Consolas', monospace; -fx-alignment: CENTER-RIGHT;");
                }
            }
        });

        TableColumn<Transaction, Number> colParcels = new TableColumn<>("Parcels");
        colParcels.setPrefWidth(70);
        colParcels.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getParcels()));
        colParcels.setStyle("-fx-alignment: CENTER-RIGHT;");
        colParcels.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null || val.intValue() == 0) {
                    setText(empty ? null : "—");
                } else {
                    setText(String.valueOf(val.intValue()));
                    setStyle("-fx-font-family: 'Consolas', monospace; -fx-alignment: CENTER-RIGHT;");
                }
            }
        });

        TableColumn<Transaction, Number> colAmount = new TableColumn<>("Amount (₹)");
        colAmount.setPrefWidth(125);
        colAmount.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getAmount()));
        colAmount.setStyle("-fx-alignment: CENTER-RIGHT;");
        colAmount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setText(null);
                } else {
                    Transaction t = getTableRow().getItem();
                    boolean isSale = t == null || "sale".equalsIgnoreCase(t.getTransactionType());
                    setText((isSale ? "+ ₹ " : "- ₹ ") + currencyFmt.format(val.doubleValue()));
                    setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-alignment: CENTER-RIGHT; " +
                        "-fx-text-fill: " + (isSale ? "#dc2626" : "#16a34a") + ";");
                }
            }
        });

        TableColumn<Transaction, String> colCheck = new TableColumn<>("Check No.");
        colCheck.setPrefWidth(100);
        colCheck.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getCheckNumber() != null && !d.getValue().getCheckNumber().isBlank()
                ? d.getValue().getCheckNumber() : "—"));
        colCheck.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setText(null);
                } else {
                    setText(val);
                    setStyle("-fx-font-family: 'Consolas', monospace;");
                }
            }
        });

        TableColumn<Transaction, String> colBill = new TableColumn<>("Bill / Inv #");
        colBill.setPrefWidth(110);
        colBill.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getBillNumber() != null && !d.getValue().getBillNumber().isBlank()
                ? d.getValue().getBillNumber() : "—"));
        colBill.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null || val.equals("—")) {
                    setText(empty ? null : "—");
                    setStyle(null);
                } else {
                    setText(val);
                    setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #2563eb;");
                }
            }
        });

        TableColumn<Transaction, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(100);
        colActions.setStyle("-fx-alignment: CENTER-RIGHT;");
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = UiTheme.iconBtn(IconHelper.ICON_EDIT, "Edit Transaction");
            private final Button delBtn = UiTheme.iconBtn(IconHelper.ICON_TRASH, "Delete Transaction");
            private final HBox actBox = new HBox(4, editBtn, delBtn);

            {
                actBox.setAlignment(Pos.CENTER_RIGHT);
                editBtn.setOnAction(e -> {
                    Transaction t = getTableRow().getItem();
                    if (t != null) showAddEditDialog(t);
                });
                delBtn.setOnAction(e -> {
                    Transaction t = getTableRow().getItem();
                    if (t != null) confirmDelete(t);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : actBox);
            }
        });

        table.getColumns().addAll(colDate, colBuyer, colBook, colType, colQty, colParcels, colAmount, colCheck, colBill, colActions);

        VBox wrap = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrap;
    }

    private void applyFilter() {
        if (filteredTransactions == null) return;
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";

        filteredTransactions.setPredicate(t -> {
            // Book filter
            if (!"ALL".equalsIgnoreCase(bookFilter) && !bookFilter.equalsIgnoreCase(t.getBookType())) {
                return false;
            }
            // Type filter
            if (!"ALL".equalsIgnoreCase(typeFilter) && !typeFilter.equalsIgnoreCase(t.getTransactionType())) {
                return false;
            }
            // Search text
            if (q.isEmpty()) return true;
            return (t.getBuyerName() != null && t.getBuyerName().toLowerCase().contains(q)) ||
                   (t.getCheckNumber() != null && t.getCheckNumber().toLowerCase().contains(q)) ||
                   (t.getBillNumber() != null && t.getBillNumber().toLowerCase().contains(q)) ||
                   (t.getNotes() != null && t.getNotes().toLowerCase().contains(q));
        });

        countBadge.setText(filteredTransactions.size() + " entries");
        updateKpiSummary();
    }

    private void updateKpiSummary() {
        if (filteredTransactions == null) return;
        double sales = 0;
        double payments = 0;
        int pieces = 0;

        for (Transaction t : filteredTransactions) {
            if ("sale".equalsIgnoreCase(t.getTransactionType())) {
                sales += t.getAmount();
                pieces += t.getTotalQuantity();
            } else if ("payment".equalsIgnoreCase(t.getTransactionType())) {
                payments += t.getAmount();
            }
        }

        totalSalesLabel.setText("₹ " + currencyFmt.format(sales));
        totalPaymentsLabel.setText("₹ " + currencyFmt.format(payments));
        double net = sales - payments;
        netBalanceLabel.setText("₹ " + currencyFmt.format(net));
        totalPiecesLabel.setText(String.valueOf(pieces));
    }

    private void showAddEditDialog(Transaction existing) {
        Dialog<Transaction> dlg = new Dialog<>();
        boolean isEdit = existing != null;
        dlg.setTitle(isEdit ? "Edit Transaction #" + existing.getId() : "Record New Transaction");
        dlg.setHeaderText(isEdit ? "Update ledger transaction details" : "Add financial entry to CC or CS book");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));

        ColumnConstraints col0 = new ColumnConstraints(140);
        ColumnConstraints col1 = new ColumnConstraints(280);
        grid.getColumnConstraints().addAll(col0, col1);

        int row = 0;

        // If linked to Tax Invoice, show banner
        if (isEdit && existing.getBillNumber() != null && !existing.getBillNumber().isBlank()) {
            Label linkedLbl = new Label("Linked Tax Invoice: " + existing.getBillNumber());
            linkedLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #2563eb;");
            grid.add(new Label("Bill Linked:"), 0, row);
            grid.add(linkedLbl, 1, row++);
        }

        // Buyer selection
        ComboBox<Buyer> buyerCombo = new ComboBox<>();
        List<Buyer> buyers = app.getData().getAllBuyers();
        buyerCombo.setItems(FXCollections.observableArrayList(buyers));
        buyerCombo.setPrefWidth(280);
        buyerCombo.setPromptText("Select Buyer / Customer...");
        buyerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Buyer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });
        buyerCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Buyer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDisplayName());
            }
        });

        if (isEdit) {
            buyers.stream()
                .filter(b -> b.getId().equals(existing.getBuyerId()))
                .findFirst()
                .ifPresent(buyerCombo::setValue);
        }

        grid.add(new Label("Buyer / Firm: *"), 0, row);
        grid.add(buyerCombo, 1, row++);

        // Book Type (CC / CS)
        ToggleGroup bookGroup = new ToggleGroup();
        RadioButton rbCc = new RadioButton("CC Book (Credit)");
        RadioButton rbCs = new RadioButton("CS Book (Cash)");
        rbCc.setToggleGroup(bookGroup);
        rbCs.setToggleGroup(bookGroup);
        if (isEdit && "CS".equalsIgnoreCase(existing.getBookType())) {
            rbCs.setSelected(true);
        } else {
            rbCc.setSelected(true);
        }
        HBox bookBox = new HBox(12, rbCc, rbCs);
        grid.add(new Label("Book Type: *"), 0, row);
        grid.add(bookBox, 1, row++);

        // Transaction Type (Sale / Payment)
        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton rbSale = new RadioButton("Sale");
        RadioButton rbPayment = new RadioButton("Payment");
        rbSale.setToggleGroup(typeGroup);
        rbPayment.setToggleGroup(typeGroup);
        if (isEdit && "payment".equalsIgnoreCase(existing.getTransactionType())) {
            rbPayment.setSelected(true);
        } else {
            rbSale.setSelected(true);
        }
        HBox typeBox = new HBox(12, rbSale, rbPayment);
        grid.add(new Label("Transaction Type: *"), 0, row);
        grid.add(typeBox, 1, row++);

        // Date Pickers
        DatePicker txDatePicker = new DatePicker();
        txDatePicker.setPrefWidth(280);
        if (isEdit && existing.getTransactionDate() != null) {
            try {
                txDatePicker.setValue(LocalDate.parse(existing.getTransactionDate()));
            } catch (Exception ex) {
                txDatePicker.setValue(LocalDate.now());
            }
        } else {
            txDatePicker.setValue(LocalDate.now());
        }
        grid.add(new Label("Tx Date: *"), 0, row);
        grid.add(txDatePicker, 1, row++);

        DatePicker dueDatePicker = new DatePicker();
        dueDatePicker.setPrefWidth(280);
        if (isEdit && existing.getDueDate() != null && !existing.getDueDate().isBlank()) {
            try {
                dueDatePicker.setValue(LocalDate.parse(existing.getDueDate()));
            } catch (Exception ignored) {}
        }
        grid.add(new Label("Due Date:"), 0, row);
        grid.add(dueDatePicker, 1, row++);

        // Amount
        TextField amountField = new TextField(isEdit ? String.valueOf(existing.getAmount()) : "");
        amountField.setPromptText("0.00");
        grid.add(new Label("Amount (₹): *"), 0, row);
        grid.add(amountField, 1, row++);

        // Trouser / Pieces Quantity
        TextField qtyField = new TextField(isEdit ? String.valueOf(existing.getTotalQuantity()) : "");
        qtyField.setPromptText("Total pieces (for sales)");
        grid.add(new Label("Trouser Qty (Pcs):"), 0, row);
        grid.add(qtyField, 1, row++);

        // Parcels
        TextField parcelsField = new TextField(isEdit && existing.getParcels() > 0 ? String.valueOf(existing.getParcels()) : "");
        parcelsField.setPromptText("Parcels / Bales sent");
        grid.add(new Label("Parcels:"), 0, row);
        grid.add(parcelsField, 1, row++);

        // Check Number
        TextField checkField = new TextField(isEdit && existing.getCheckNumber() != null ? existing.getCheckNumber() : "");
        checkField.setPromptText("Enter check number");
        grid.add(new Label("Check Number:"), 0, row);
        grid.add(checkField, 1, row++);

        // Include in Reporting Checkbox
        CheckBox reportingCheck = new CheckBox("Include in sales matrix reporting");
        if (isEdit) {
            reportingCheck.setSelected(existing.isIncludeInReporting());
        } else {
            reportingCheck.setSelected(true);
        }
        grid.add(new Label("Reporting:"), 0, row);
        grid.add(reportingCheck, 1, row++);

        // Notes
        TextField notesField = new TextField(isEdit && existing.getNotes() != null ? existing.getNotes() : "");
        notesField.setPromptText("Remarks or payment reference");
        grid.add(new Label("Notes:"), 0, row);
        grid.add(notesField, 1, row++);

        // Dynamic State Listeners (enforcing exact web logic):
        // 1. Check Number is ONLY enabled for: Payment + CC
        // 2. Reporting is disabled for: Any Payment OR Sale + CC
        // 3. Trouser Quantity is disabled for Payment
        Runnable updateFieldsState = () -> {
            boolean isPayment = rbPayment.isSelected();
            boolean isCc = rbCc.isSelected();

            // Trouser qty disabled for payment
            qtyField.setDisable(isPayment);
            if (isPayment) qtyField.setText("0");

            // Check number rule
            boolean checkEnabled = isPayment && isCc;
            checkField.setDisable(!checkEnabled);
            if (!checkEnabled && !isEdit) {
                checkField.setText("");
            }

            // Reporting rule
            boolean reportingDisabled = isPayment || (!isPayment && isCc);
            reportingCheck.setDisable(reportingDisabled);
            if (reportingDisabled) {
                reportingCheck.setSelected(!isPayment && isCc);
            }
        };

        rbCc.setOnAction(e -> updateFieldsState.run());
        rbCs.setOnAction(e -> updateFieldsState.run());
        rbSale.setOnAction(e -> updateFieldsState.run());
        rbPayment.setOnAction(e -> updateFieldsState.run());
        updateFieldsState.run();

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogHelper.styleDialog(dlg);

        dlg.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Buyer selBuyer = buyerCombo.getValue();
                if (selBuyer == null) {
                    Toast.show(app.getRootPane(), "Validation Error", "Please select a Buyer / Firm.", true);
                    return null;
                }

                double amount = 0;
                try {
                    amount = Double.parseDouble(amountField.getText().trim());
                    if (amount <= 0) throw new NumberFormatException();
                } catch (Exception ex) {
                    Toast.show(app.getRootPane(), "Validation Error", "Enter a valid positive amount.", true);
                    return null;
                }

                int qty = 0;
                if (!rbPayment.isSelected()) {
                    try {
                        String qText = qtyField.getText().trim();
                        if (!qText.isEmpty()) qty = Integer.parseInt(qText);
                    } catch (Exception ignored) {}
                }

                int parcels = 0;
                try {
                    String pText = parcelsField.getText().trim();
                    if (!pText.isEmpty()) parcels = Integer.parseInt(pText);
                } catch (Exception ignored) {}

                String bType = rbCc.isSelected() ? "CC" : "CS";
                String tType = rbSale.isSelected() ? "sale" : "payment";
                String txDate = txDatePicker.getValue() != null ? txDatePicker.getValue().toString() : LocalDate.now().toString();
                String dueDate = dueDatePicker.getValue() != null ? dueDatePicker.getValue().toString() : null;
                String checkNo = checkField.isDisable() ? null : checkField.getText().trim();
                boolean incRep = reportingCheck.isSelected();
                String notes = notesField.getText().trim();

                if (isEdit) {
                    existing.setBuyerId(selBuyer.getId());
                    existing.setBuyerName(selBuyer.getDisplayName());
                    existing.setBookType(bType);
                    existing.setTransactionType(tType);
                    existing.setTransactionDate(txDate);
                    existing.setDueDate(dueDate);
                    existing.setAmount(amount);
                    existing.setTotalQuantity(qty);
                    existing.setParcels(parcels);
                    existing.setCheckNumber(checkNo);
                    existing.setIncludeInReporting(incRep);
                    existing.setNotes(notes);
                    existing.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    return existing;
                } else {
                    Transaction t = new Transaction(
                        null,
                        selBuyer.getId(),
                        selBuyer.getDisplayName(),
                        bType,
                        tType,
                        txDate,
                        dueDate,
                        qty,
                        amount,
                        checkNo,
                        incRep,
                        null
                    );
                    t.setParcels(parcels);
                    t.setNotes(notes);
                    return t;
                }
            }
            return null;
        });

        dlg.showAndWait().ifPresent(t -> {
            app.getData().saveTransaction(t);
            refresh();
            Toast.show(app.getRootPane(), "Transaction Saved",
                "Transaction of ₹ " + currencyFmt.format(t.getAmount()) + " saved successfully.", false);
        });
    }

    private void confirmDelete(Transaction t) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Transaction");
        alert.setHeaderText("Delete Transaction #" + t.getId() + " (" + t.getBuyerName() + ")?");
        alert.setContentText("This will remove the transaction from the active ledger. Continue?");
        DialogHelper.styleDialog(alert);

        alert.showAndWait().ifPresent(ans -> {
            if (ans == ButtonType.OK) {
                app.getData().deleteTransaction(t.getId());
                refresh();
                Toast.show(app.getRootPane(), "Transaction Deleted", "Entry removed from ledger.", false);
            }
        });
    }

    private void exportToCsv() {
        if (filteredTransactions == null || filteredTransactions.isEmpty()) {
            Toast.show(app.getRootPane(), "Export Warning", "No transaction records to export.", true);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Transactions to CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        fileChooser.setInitialFileName("transactions_ledger_" + LocalDate.now() + ".csv");

        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("ID,Date,Buyer,Book,Type,Pieces,Parcels,Amount,Check Number,Bill Number,Notes\n");
            for (Transaction t : filteredTransactions) {
                writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,%.2f,\"%s\",\"%s\",\"%s\"\n",
                    t.getId(),
                    t.getTransactionDate() != null ? t.getTransactionDate() : "",
                    t.getBuyerName() != null ? t.getBuyerName().replace("\"", "\"\"") : "",
                    t.getBookType(),
                    t.getTransactionType(),
                    t.getTotalQuantity(),
                    t.getParcels(),
                    t.getAmount(),
                    t.getCheckNumber() != null ? t.getCheckNumber() : "",
                    t.getBillNumber() != null ? t.getBillNumber() : "",
                    t.getNotes() != null ? t.getNotes().replace("\"", "\"\"") : ""
                ));
            }
            Toast.show(app.getRootPane(), "Export Successful", "Exported " + filteredTransactions.size() + " transactions to CSV.", false);
        } catch (IOException ex) {
            Toast.show(app.getRootPane(), "Export Error", "Failed to export CSV: " + ex.getMessage(), true);
        }
    }
}
