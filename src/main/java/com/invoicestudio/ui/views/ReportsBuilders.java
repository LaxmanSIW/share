package com.invoicestudio.ui.views;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.model.*;
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
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;

import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javafx.scene.chart.PieChart;

import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Report tab builders for {@link ReportsView} (skill rule 5.2 role 1).
 * Extracted wholesale — behavior identical; ReportsView now delegates.
 */
class ReportsBuilders {

    private final ReportsView owner;
    private final StudioApp app;
    private final DecimalFormat currencyFmt;

    // Tab 2 controls (owned here; exposed to shell for cross-view selection)
    ComboBox<Buyer> statementBuyerCombo;

    ReportsBuilders(ReportsView owner, StudioApp app, DecimalFormat currencyFmt) {
        this.owner = owner;
        this.app = app;
        this.currencyFmt = currencyFmt;
    }

    ComboBox<Buyer> statementBuyerCombo() { return statementBuyerCombo; }

    private ComboBox<Buyer> newComboBox() { return new ComboBox<>(); }

    // =========================================================================
    // Card Helper
    // =========================================================================
    private Node createKpiCard(String title, String value, String subtext, String accentColor) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setStyle("-fx-background-color: #151B26; -fx-border-color: #222F3E; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-border-top-color: " + accentColor + "; -fx-border-top-width: 3;");
        card.setPrefWidth(210);
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lblTitle = new Label(title.toUpperCase());
        lblTitle.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 10.5px; -fx-font-weight: bold;");

        Label lblVal = new Label(value);
        lblVal.setStyle("-fx-text-fill: #F8FAFC; -fx-font-size: 19px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', sans-serif;");

        Label lblSub = new Label(subtext);
        lblSub.setStyle("-fx-text-fill: " + accentColor + "; -fx-opacity: 0.88; -fx-font-size: 11px;");

        card.getChildren().addAll(lblTitle, lblVal, lblSub);
        return card;
    }

    // =========================================================================
    // 1. Outstanding & Aging Report
    // =========================================================================
    Node buildOutstandingReport() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14, 0, 0, 0));

        List<Buyer> buyers = app.getData().getAllBuyers();
        List<Transaction> txs = app.getData().getAllTransactions();

        class OutstandingRow {
            String buyerId;
            String companyName;
            String phone;
            String city;
            double openingBal = 0;
            double totalSales = 0;
            double totalPaid = 0;
            double outstanding = 0;
            int parcels = 0;
            String riskLevel = "Normal";
        }

        Map<String, OutstandingRow> map = new HashMap<>();
        for (Buyer b : buyers) {
            OutstandingRow r = new OutstandingRow();
            r.buyerId = b.getId();
            r.companyName = b.getDisplayName();
            r.phone = b.getPhone() != null ? b.getPhone() : "";
            r.city = b.getCity() != null ? b.getCity() : "";
            r.openingBal = b.getOpeningBalance();
            r.totalSales = b.getOpeningBalance();
            map.put(b.getId(), r);
        }

        for (Transaction t : txs) {
            OutstandingRow r = map.get(t.getBuyerId());
            if (r == null) continue;
            if ("sale".equalsIgnoreCase(t.getTransactionType())) {
                r.totalSales += t.getAmount();
                r.parcels += t.getParcels();
            } else if ("payment".equalsIgnoreCase(t.getTransactionType())) {
                r.totalPaid += t.getAmount();
            }
        }

        List<OutstandingRow> allRows = new ArrayList<>();
        double totalOutstandingAmount = 0;
        int activeDebtorsCount = 0;
        int totalParcelsCount = 0;
        double totalSalesBilled = 0;
        double totalCollected = 0;

        for (OutstandingRow r : map.values()) {
            r.outstanding = r.totalSales - r.totalPaid;
            totalSalesBilled += r.totalSales;
            totalCollected += r.totalPaid;
            if (r.outstanding > 0.01) {
                totalOutstandingAmount += r.outstanding;
                activeDebtorsCount++;
                totalParcelsCount += r.parcels;
                if (r.outstanding > 50000) r.riskLevel = "High";
                else if (r.outstanding > 20000) r.riskLevel = "Moderate";
                allRows.add(r);
            }
        }
        allRows.sort((a, b) -> Double.compare(b.outstanding, a.outstanding));

        double collectionEfficiency = totalSalesBilled > 0 ? (totalCollected / totalSalesBilled) * 100.0 : 100.0;

        // KPI Summary Cards
        HBox kpiRow = new HBox(12);
        kpiRow.getChildren().addAll(
            createKpiCard("Total Outstanding Due", "₹ " + currencyFmt.format(totalOutstandingAmount), "Pending accounts receivable", "#F87171"),
            createKpiCard("Active Debtors", activeDebtorsCount + " Accounts", "Buyers with unpaid balance", "#F2CA6B"),
            createKpiCard("Total Parcels Sent", totalParcelsCount + " Bales", "Shipments dispatched to debtors", "#38BDF8"),
            createKpiCard("Collection Efficiency", String.format("%.1f%%", collectionEfficiency), "Recovered receipts ratio", "#34D399")
        );

        // Filter Bar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("Filter by buyer name, phone, or city…");
        searchField.setPrefWidth(300);
        searchField.getStyleClass().add("designer-field");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button exportBtn = UiTheme.secondaryBtn("Export Outstanding CSV");
        exportBtn.setOnAction(e -> exportCsv(
            "outstanding_report_" + LocalDate.now() + ".csv",
            "Buyer,Phone,City,Opening Balance,Total Sales,Total Paid,Outstanding Due,Parcels,Risk Level\n",
            allRows.stream().map(r -> String.format("\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.2f,%.2f,%d,\"%s\"",
                r.companyName, r.phone, r.city, r.openingBal, r.totalSales, r.totalPaid, r.outstanding, r.parcels, r.riskLevel))
                .collect(Collectors.toList())
        ));

        filterBar.getChildren().addAll(searchField, sp, exportBtn);

        // Table
        TableView<OutstandingRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<OutstandingRow, String> cBuyer = new TableColumn<>("Buyer / Firm");
        cBuyer.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().companyName));
        cBuyer.setPrefWidth(220);

        TableColumn<OutstandingRow, String> cCity = new TableColumn<>("City");
        cCity.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().city.isBlank() ? "—" : d.getValue().city));
        cCity.setPrefWidth(110);

        TableColumn<OutstandingRow, String> cPhone = new TableColumn<>("Phone");
        cPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().phone.isBlank() ? "—" : d.getValue().phone));
        cPhone.setPrefWidth(120);

        TableColumn<OutstandingRow, Number> cSales = new TableColumn<>("Total Invoiced (₹)");
        cSales.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().totalSales));
        cSales.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-text-fill: #94A3B8;");
            }
        });

        TableColumn<OutstandingRow, Number> cPaid = new TableColumn<>("Total Paid (₹)");
        cPaid.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().totalPaid));
        cPaid.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-text-fill: #34D399;");
            }
        });

        TableColumn<OutstandingRow, Number> cOut = new TableColumn<>("Outstanding Due (₹)");
        cOut.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().outstanding));
        cOut.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #F87171;");
            }
        });

        TableColumn<OutstandingRow, Number> cParcels = new TableColumn<>("Parcels");
        cParcels.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().parcels));
        cParcels.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : String.valueOf(v.intValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-text-fill: #38BDF8;");
            }
        });

        TableColumn<OutstandingRow, Void> cAction = new TableColumn<>("Action");
        cAction.setPrefWidth(120);
        cAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = UiTheme.smallBtn("Statement ↗");
            {
                btn.setOnAction(e -> {
                    OutstandingRow r = getTableView().getItems().get(getIndex());
                    if (r != null) owner.selectBuyerStatement(r.buyerId);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
                setAlignment(Pos.CENTER);
            }
        });

        table.getColumns().addAll(cBuyer, cCity, cPhone, cSales, cPaid, cOut, cParcels, cAction);

        FilteredList<OutstandingRow> filtered = new FilteredList<>(FXCollections.observableArrayList(allRows), p -> true);
        searchField.textProperty().addListener((obs, o, v) -> {
            String q = v != null ? v.trim().toLowerCase() : "";
            filtered.setPredicate(r -> {
                if (q.isEmpty()) return true;
                return (r.companyName != null && r.companyName.toLowerCase().contains(q)) ||
                       (r.phone != null && r.phone.toLowerCase().contains(q)) ||
                       (r.city != null && r.city.toLowerCase().contains(q));
            });
        });
        table.setItems(filtered);

        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                OutstandingRow sel = table.getSelectionModel().getSelectedItem();
                if (sel != null) owner.selectBuyerStatement(sel.buyerId);
            }
        });

        root.getChildren().addAll(kpiRow, filterBar, table);
        return root;
    }

    // =========================================================================
    // 2. Buyer Statement & Ledger Report
    // =========================================================================
    Node buildBuyerStatementReport() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14, 0, 0, 0));

        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        statementBuyerCombo = newComboBox();
        statementBuyerCombo.setPrefWidth(280);
        statementBuyerCombo.setPromptText("Select Buyer / Account…");
        statementBuyerCombo.setItems(FXCollections.observableArrayList(app.getData().getAllBuyers()));
        statementBuyerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Buyer b, boolean emp) {
                super.updateItem(b, emp);
                setText(emp || b == null ? null : b.getDisplayName());
            }
        });
        statementBuyerCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Buyer b, boolean emp) {
                super.updateItem(b, emp);
                setText(emp || b == null ? null : b.getDisplayName());
            }
        });

        DatePicker startPicker = UiTheme.datePicker("From Date");
        startPicker.setPrefWidth(140);
        DatePicker endPicker = UiTheme.datePicker("To Date");
        endPicker.setPrefWidth(140);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button exportBtn = UiTheme.secondaryBtn("Export Statement CSV");

        filterRow.getChildren().addAll(new Label("Account:"), statementBuyerCombo, startPicker, endPicker, sp, exportBtn);

        // Summary Strip Cards
        HBox statementKpiRow = new HBox(12);
        statementKpiRow.getChildren().addAll(
            createKpiCard("Opening Balance", "₹ 0.00", "Starting balance", "#94A3B8"),
            createKpiCard("Total Billed (Debits)", "₹ 0.00", "Invoiced sales amount", "#F87171"),
            createKpiCard("Total Paid (Credits)", "₹ 0.00", "Receipts credited", "#34D399"),
            createKpiCard("Closing Ledger Balance", "₹ 0.00", "Net current outstanding due", "#F2CA6B")
        );

        // Ledger Table
        class LedgerRow {
            String date;
            String description;
            String book;
            double debit = 0;   // sales
            double credit = 0;  // payments
            double balance = 0;
            String checkNo;
        }

        TableView<LedgerRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<LedgerRow, String> cDate = new TableColumn<>("Date");
        cDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().date));
        cDate.setPrefWidth(100);

        TableColumn<LedgerRow, String> cDesc = new TableColumn<>("Description / Narration");
        cDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().description));
        cDesc.setPrefWidth(260);

        TableColumn<LedgerRow, String> cBook = new TableColumn<>("Book");
        cBook.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().book));
        cBook.setPrefWidth(70);

        TableColumn<LedgerRow, String> cCheck = new TableColumn<>("Ref / Check #");
        cCheck.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().checkNo != null ? d.getValue().checkNo : "—"));
        cCheck.setPrefWidth(120);

        TableColumn<LedgerRow, Number> cDebit = new TableColumn<>("Debit (Sales +)");
        cDebit.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().debit));
        cDebit.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                if (emp || v == null || v.doubleValue() == 0) setText(emp ? null : "—");
                else {
                    setText("+ ₹ " + currencyFmt.format(v.doubleValue()));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-text-fill: #F87171;");
                }
            }
        });

        TableColumn<LedgerRow, Number> cCredit = new TableColumn<>("Credit (Payments -)");
        cCredit.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().credit));
        cCredit.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                if (emp || v == null || v.doubleValue() == 0) setText(emp ? null : "—");
                else {
                    setText("- ₹ " + currencyFmt.format(v.doubleValue()));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-text-fill: #34D399;");
                }
            }
        });

        TableColumn<LedgerRow, Number> cBal = new TableColumn<>("Running Balance (₹)");
        cBal.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().balance));
        cBal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                if (emp || v == null) setText(null);
                else {
                    double b = v.doubleValue();
                    setText("₹ " + currencyFmt.format(b));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: " +
                            (b > 0.01 ? "#F87171" : (b < -0.01 ? "#34D399" : "#94A3B8")) + ";");
                }
            }
        });

        table.getColumns().addAll(cDate, cDesc, cBook, cCheck, cDebit, cCredit, cBal);

        Runnable loadStatement = () -> {
            Buyer sel = statementBuyerCombo.getValue();
            if (sel == null) return;

            List<Transaction> bTxs = app.getData().getAllTransactions().stream()
                .filter(t -> sel.getId().equals(t.getBuyerId()) || (t.getBuyerName() != null && t.getBuyerName().equalsIgnoreCase(sel.getName())))
                .sorted(Comparator.comparing(Transaction::getTransactionDate, Comparator.nullsLast(String::compareTo))
                        .thenComparing(t -> "sale".equalsIgnoreCase(t.getTransactionType()) ? 0 : 1))
                .collect(Collectors.toList());

            LocalDate sDate = startPicker.getValue();
            LocalDate eDate = endPicker.getValue();

            double runningBal = sel.getOpeningBalance();
            double totalDeb = 0;
            double totalCred = 0;
            List<LedgerRow> ledger = new ArrayList<>();

            // Opening balance row if > 0
            if (runningBal > 0) {
                LedgerRow op = new LedgerRow();
                op.date = "—";
                op.description = "Opening Ledger Balance";
                op.book = "OP";
                op.debit = runningBal;
                op.balance = runningBal;
                ledger.add(op);
                totalDeb += runningBal;
            }

            for (Transaction t : bTxs) {
                if (t.getTransactionDate() != null) {
                    try {
                        LocalDate d = LocalDate.parse(t.getTransactionDate());
                        if (sDate != null && d.isBefore(sDate)) continue;
                        if (eDate != null && d.isAfter(eDate)) continue;
                    } catch (Exception ignored) {
            AppLog.debug(ignored); }
                }

                boolean isSale = "sale".equalsIgnoreCase(t.getTransactionType());
                LedgerRow r = new LedgerRow();
                r.date = t.getTransactionDate();
                r.book = t.getBookType();
                r.checkNo = isSale ? "—" : (t.getCheckNumber() != null && !t.getCheckNumber().isBlank() ? t.getCheckNumber() : "—");

                if (isSale) {
                    r.description = (t.getBillNumber() != null && !t.getBillNumber().isBlank())
                        ? "Tax Invoice #" + t.getBillNumber() : "Sales Invoice (" + t.getBookType() + ")";
                    r.debit = t.getAmount();
                    runningBal += t.getAmount();
                    totalDeb += t.getAmount();
                } else {
                    r.description = "Payment Received / Credit (" + (t.getCheckNumber() != null && !t.getCheckNumber().isBlank() ? t.getCheckNumber() : t.getBookType()) + ")";
                    r.credit = t.getAmount();
                    runningBal -= t.getAmount();
                    totalCred += t.getAmount();
                }
                r.balance = runningBal;
                ledger.add(r);
            }

            table.setItems(FXCollections.observableArrayList(ledger));

            statementKpiRow.getChildren().setAll(
                createKpiCard("Opening Balance", "₹ " + currencyFmt.format(sel.getOpeningBalance()), "Starting balance", "#94A3B8"),
                createKpiCard("Total Billed (Debits)", "₹ " + currencyFmt.format(totalDeb), "Invoiced sales amount", "#F87171"),
                createKpiCard("Total Paid (Credits)", "₹ " + currencyFmt.format(totalCred), "Receipts credited", "#34D399"),
                createKpiCard("Closing Ledger Balance", "₹ " + currencyFmt.format(runningBal),
                    runningBal > 0.01 ? "Pending amount receivable" : "Account fully settled",
                    runningBal > 0.01 ? "#F87171" : "#34D399")
            );
        };

        statementBuyerCombo.valueProperty().addListener((o, ov, nv) -> loadStatement.run());
        startPicker.valueProperty().addListener((o, ov, nv) -> loadStatement.run());
        endPicker.valueProperty().addListener((o, ov, nv) -> loadStatement.run());

        exportBtn.setOnAction(e -> {
            Buyer sel = statementBuyerCombo.getValue();
            if (sel == null || table.getItems().isEmpty()) {
                Toast.show(app.getRootPane(), "Export Warning", "Select a buyer with statement items to export.", true);
                return;
            }
            exportCsv(
                "statement_" + sel.getDisplayName().replaceAll("[^a-zA-Z0-9]", "_") + "_" + LocalDate.now() + ".csv",
                "Date,Description,Book,Reference / Check Number,Debit,Credit,Running Balance\n",
                table.getItems().stream().map(r -> String.format("\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.2f",
                    r.date, r.description, r.book, r.checkNo != null ? r.checkNo : "", r.debit, r.credit, r.balance))
                    .collect(Collectors.toList())
            );
        });

        root.getChildren().addAll(filterRow, statementKpiRow, table);
        return root;
    }

    // =========================================================================
    // 3. Trouser Movement Report
    // =========================================================================
    Node buildTrouserMovementReport() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14, 0, 0, 0));

        List<Transaction> txs = app.getData().getAllTransactions().stream()
            .filter(t -> "sale".equalsIgnoreCase(t.getTransactionType()) && t.isIncludeInReporting())
            .collect(Collectors.toList());

        Map<String, int[]> monthStats = new TreeMap<>();
        int totalUnits = 0;
        int ccUnits = 0;
        int csUnits = 0;

        for (Transaction t : txs) {
            String d = t.getTransactionDate();
            if (d == null || d.length() < 7) continue;
            String mKey = d.substring(0, 7);
            int[] arr = monthStats.computeIfAbsent(mKey, k -> new int[2]);
            if ("CC".equalsIgnoreCase(t.getBookType())) {
                arr[0] += t.getTotalQuantity();
                ccUnits += t.getTotalQuantity();
            } else {
                arr[1] += t.getTotalQuantity();
                csUnits += t.getTotalQuantity();
            }
            totalUnits += t.getTotalQuantity();
        }

        HBox kpiRow = new HBox(12);
        kpiRow.getChildren().addAll(
            createKpiCard("Total Units Moved", totalUnits + " Pieces", "Total garment shipments", "#F2CA6B"),
            createKpiCard("CC Book Units", ccUnits + " Pieces", "Credit customer volume", "#38BDF8"),
            createKpiCard("CS Book Units", csUnits + " Pieces", "Cash sale customer volume", "#34D399")
        );

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        StackedBarChart<String, Number> chart = new StackedBarChart<>(xAxis, yAxis);
        chart.setPrefHeight(260);
        chart.setTitle("Monthly Trouser Sales Velocity (CC vs CS)");
        chart.setAnimated(false);
        chart.setCategoryGap(24);

        XYChart.Series<String, Number> ccSeries = new XYChart.Series<>();
        ccSeries.setName("CC Book (Pieces)");
        XYChart.Series<String, Number> csSeries = new XYChart.Series<>();
        csSeries.setName("CS Book (Pieces)");

        for (Map.Entry<String, int[]> e : monthStats.entrySet()) {
            ccSeries.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()[0]));
            csSeries.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()[1]));
        }
        chart.getData().addAll(ccSeries, csSeries);

        class MovementRow {
            String month;
            int ccQty;
            int csQty;
            int total;
        }

        TableView<MovementRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<MovementRow, String> cMonth = new TableColumn<>("Period (Month)");
        cMonth.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().month));

        TableColumn<MovementRow, Number> cCc = new TableColumn<>("CC Pieces");
        cCc.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().ccQty));
        cCc.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace;");

        TableColumn<MovementRow, Number> cCs = new TableColumn<>("CS Pieces");
        cCs.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().csQty));
        cCs.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace;");

        TableColumn<MovementRow, Number> cTot = new TableColumn<>("Total Trouser Pieces");
        cTot.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().total));
        cTot.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #F2CA6B;");

        table.getColumns().addAll(cMonth, cCc, cCs, cTot);

        List<MovementRow> rows = new ArrayList<>();
        for (Map.Entry<String, int[]> e : monthStats.entrySet()) {
            MovementRow r = new MovementRow();
            r.month = e.getKey();
            r.ccQty = e.getValue()[0];
            r.csQty = e.getValue()[1];
            r.total = r.ccQty + r.csQty;
            rows.add(r);
        }
        Collections.reverse(rows);
        table.setItems(FXCollections.observableArrayList(rows));

        root.getChildren().addAll(kpiRow, chart, table);
        return root;
    }

    // =========================================================================
    // 4. Sales Trends (Monthly / Weekly)
    // =========================================================================
    Node buildSalesTrendsReport() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14, 0, 0, 0));

        List<Transaction> txs = app.getData().getAllTransactions();

        Map<String, double[]> stats = new TreeMap<>();
        double totalSales = 0;
        double totalPayments = 0;

        for (Transaction t : txs) {
            String d = t.getTransactionDate();
            if (d == null || d.length() < 7) continue;
            String mKey = d.substring(0, 7);
            double[] arr = stats.computeIfAbsent(mKey, k -> new double[2]);
            if ("sale".equalsIgnoreCase(t.getTransactionType())) {
                arr[0] += t.getAmount();
                totalSales += t.getAmount();
            } else if ("payment".equalsIgnoreCase(t.getTransactionType())) {
                arr[1] += t.getAmount();
                totalPayments += t.getAmount();
            }
        }

        HBox kpiRow = new HBox(12);
        kpiRow.getChildren().addAll(
            createKpiCard("Total Sales Generated", "₹ " + currencyFmt.format(totalSales), "All-time invoiced revenue", "#38BDF8"),
            createKpiCard("Total Collections", "₹ " + currencyFmt.format(totalPayments), "All-time receipts collected", "#34D399"),
            createKpiCard("Net Uncollected Balance", "₹ " + currencyFmt.format(totalSales - totalPayments), "Net balance across all accounts", "#F87171")
        );

        class SalesTrendRow {
            String month;
            double sales;
            double payments;
            double net;
            double recoveryRate;
        }

        List<SalesTrendRow> list = new ArrayList<>();
        for (Map.Entry<String, double[]> e : stats.entrySet()) {
            SalesTrendRow r = new SalesTrendRow();
            r.month = e.getKey();
            r.sales = e.getValue()[0];
            r.payments = e.getValue()[1];
            r.net = r.sales - r.payments;
            r.recoveryRate = r.sales > 0 ? (r.payments / r.sales) * 100.0 : 100.0;
            list.add(r);
        }
        Collections.reverse(list);

        TableView<SalesTrendRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<SalesTrendRow, String> cM = new TableColumn<>("Month");
        cM.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().month));

        TableColumn<SalesTrendRow, Number> cS = new TableColumn<>("Total Invoiced Sales (₹)");
        cS.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().sales));
        cS.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-text-fill: #38BDF8;");
            }
        });

        TableColumn<SalesTrendRow, Number> cP = new TableColumn<>("Collections / Payments (₹)");
        cP.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().payments));
        cP.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-text-fill: #34D399;");
            }
        });

        TableColumn<SalesTrendRow, Number> cN = new TableColumn<>("Net Difference (₹)");
        cN.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().net));
        cN.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                if (emp || v == null) setText(null);
                else {
                    setText("₹ " + currencyFmt.format(v.doubleValue()));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: " +
                        (v.doubleValue() > 0 ? "#F87171" : "#34D399") + ";");
                }
            }
        });

        TableColumn<SalesTrendRow, Number> cRate = new TableColumn<>("Recovery %");
        cRate.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().recoveryRate));
        cRate.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                if (emp || v == null) setText(null);
                else {
                    setText(String.format("%.1f%%", v.doubleValue()));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold;");
                }
            }
        });

        table.getColumns().addAll(cM, cS, cP, cN, cRate);
        table.setItems(FXCollections.observableArrayList(list));

        root.getChildren().addAll(kpiRow, table);
        return root;
    }

    // =========================================================================
    // 5 & 6. Item Movement & Performance Reports
    // =========================================================================
    Node buildItemMovementReport() {
        return buildItemAnalytics(false);
    }

    Node buildItemPerformanceReport() {
        return buildItemAnalytics(true);
    }

    private Node buildItemAnalytics(boolean sortByRevenue) {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14, 0, 0, 0));

        List<ItemRecord> items = app.getData().getAllItems();
        List<Bill> bills = app.getData().getAllBills();

        class ItemStatsRow {
            String name;
            String category;
            String hsn;
            double rate;
            int unitsSold = 0;
            double revenue = 0;
        }

        Map<String, ItemStatsRow> map = new HashMap<>();
        for (ItemRecord it : items) {
            ItemStatsRow r = new ItemStatsRow();
            r.name = it.getName();
            r.category = it.getCategoryName() != null ? it.getCategoryName() : "General";
            r.hsn = it.getHsn() != null ? it.getHsn() : "—";
            r.rate = it.getRate();
            map.put(it.getName().trim().toLowerCase(), r);
        }

        // Ensure default PENT item exists in map
        ItemStatsRow pentRow = map.computeIfAbsent("pent", k -> {
            ItemStatsRow r = new ItemStatsRow();
            r.name = "PENT";
            r.category = "Trouser";
            r.hsn = "6203";
            r.rate = 550.0;
            return r;
        });

        Set<String> processedBillIds = new HashSet<>();
        for (Bill b : bills) {
            if (b.getId() != null) processedBillIds.add(b.getId());
            if (b.getItems() == null) continue;
            for (var bi : b.getItems()) {
                String key = bi.getDescription() != null ? bi.getDescription().trim().toLowerCase() : "";
                ItemStatsRow r = map.get(key);
                if (r == null) {
                    r = new ItemStatsRow();
                    r.name = bi.getDescription();
                    r.category = "General";
                    r.hsn = bi.getHsn();
                    r.rate = bi.getRate();
                    map.put(key, r);
                }
                r.unitsSold += (int) bi.getQty();
                r.revenue += bi.getAmount();
            }
        }

        // Aggregate financial sales transactions marked with includeInReporting not from itemized bills
        List<Transaction> txs = app.getData().getAllTransactions();
        for (Transaction t : txs) {
            if ("sale".equalsIgnoreCase(t.getTransactionType()) && t.isIncludeInReporting()) {
                boolean hasBillItems = t.getBillId() != null && processedBillIds.contains(t.getBillId());
                if (!hasBillItems) {
                    pentRow.unitsSold += t.getTotalQuantity();
                    pentRow.revenue += t.getAmount();
                }
            }
        }

        List<ItemStatsRow> rows = new ArrayList<>(map.values());
        if (sortByRevenue) {
            rows.sort((a, b) -> Double.compare(b.revenue, a.revenue));
        } else {
            rows.sort((a, b) -> Integer.compare(b.unitsSold, a.unitsSold));
        }

        double totalRev = rows.stream().mapToDouble(r -> r.revenue).sum();
        int totalUnits = rows.stream().mapToInt(r -> r.unitsSold).sum();

        HBox kpiRow = new HBox(12);
        kpiRow.getChildren().addAll(
            createKpiCard("Total Catalog Items", rows.size() + " SKUs", "Active catalog items tracked", "#38BDF8"),
            createKpiCard("Total Units Sold", totalUnits + " Pieces", "Dispatched item volume", "#F2CA6B"),
            createKpiCard("Item Sales Revenue", "₹ " + currencyFmt.format(totalRev), "Aggregate item billing", "#34D399")
        );

        TableView<ItemStatsRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<ItemStatsRow, String> cName = new TableColumn<>("Item Name / Description");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name));
        cName.setPrefWidth(220);

        TableColumn<ItemStatsRow, String> cCat = new TableColumn<>("Category");
        cCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().category));
        cCat.setPrefWidth(130);

        TableColumn<ItemStatsRow, String> cHsn = new TableColumn<>("HSN Code");
        cHsn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().hsn));
        cHsn.setPrefWidth(100);

        TableColumn<ItemStatsRow, Number> cQty = new TableColumn<>("Units Sold");
        cQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().unitsSold));
        cQty.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold;");

        TableColumn<ItemStatsRow, Number> cRev = new TableColumn<>("Total Revenue (₹)");
        cRev.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().revenue));
        cRev.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #34D399;");
            }
        });

        table.getColumns().addAll(cName, cCat, cHsn, cQty, cRev);
        table.setItems(FXCollections.observableArrayList(rows));

        root.getChildren().addAll(kpiRow, table);
        return root;
    }

    // =========================================================================
    // 7. Category Breakdown Report
    // =========================================================================
    Node buildCategoryBreakdownReport() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14, 0, 0, 0));

        List<ItemCategory> categories = app.getData().getAllCategories();
        List<ItemRecord> items = app.getData().getAllItems();
        List<Bill> bills = app.getData().getAllBills();

        Map<String, String> itemToCat = new HashMap<>();
        for (ItemRecord it : items) {
            itemToCat.put(it.getName().trim().toLowerCase(),
                it.getCategoryName() != null ? it.getCategoryName() : "General");
        }

        Map<String, double[]> catStats = new HashMap<>();
        for (ItemCategory c : categories) {
            catStats.put(c.getName(), new double[2]);
        }
        // Ensure Trouser category exists in breakdown
        String trouserCatName = categories.stream()
                .map(ItemCategory::getName)
                .filter(n -> "Trouser".equalsIgnoreCase(n) || "Trousers".equalsIgnoreCase(n))
                .findFirst().orElse("Trouser");
        catStats.computeIfAbsent(trouserCatName, k -> new double[2]);

        Set<String> processedBillIds = new HashSet<>();
        for (Bill b : bills) {
            if (b.getId() != null) processedBillIds.add(b.getId());
            if (b.getItems() == null) continue;
            for (var bi : b.getItems()) {
                String cat = itemToCat.getOrDefault(
                    bi.getDescription() != null ? bi.getDescription().trim().toLowerCase() : "",
                    "General"
                );
                double[] arr = catStats.computeIfAbsent(cat, k -> new double[2]);
                arr[0] += bi.getQty();
                arr[1] += bi.getAmount();
            }
        }

        // Aggregate financial sales transactions marked with includeInReporting not from itemized bills into Trouser
        List<Transaction> txs = app.getData().getAllTransactions();
        for (Transaction t : txs) {
            if ("sale".equalsIgnoreCase(t.getTransactionType()) && t.isIncludeInReporting()) {
                boolean hasBillItems = t.getBillId() != null && processedBillIds.contains(t.getBillId());
                if (!hasBillItems) {
                    double[] arr = catStats.computeIfAbsent(trouserCatName, k -> new double[2]);
                    arr[0] += t.getTotalQuantity();
                    arr[1] += t.getAmount();
                }
            }
        }

        double totalCatRev = catStats.values().stream().mapToDouble(a -> a[1]).sum();
        int totalCatUnits = (int) Math.round(catStats.values().stream().mapToDouble(a -> a[0]).sum());

        HBox kpiRow = new HBox(12);
        kpiRow.getChildren().addAll(
            createKpiCard("Active Categories", catStats.size() + " Groups", "Catalog groups tracked", "#F2CA6B"),
            createKpiCard("Total Units Sold", totalCatUnits + " Pieces", "Dispatched category volume", "#38BDF8"),
            createKpiCard("Category Sales Revenue", "₹ " + currencyFmt.format(totalCatRev), "All categorized billing", "#34D399")
        );

        PieChart pie = new PieChart();
        pie.setTitle("Revenue Share by Product Category");
        pie.setPrefHeight(260);

        for (Map.Entry<String, double[]> e : catStats.entrySet()) {
            if (e.getValue()[1] > 0) {
                pie.getData().add(new PieChart.Data(e.getKey() + " (₹ " + currencyFmt.format(e.getValue()[1]) + ")", e.getValue()[1]));
            }
        }

        class CategoryRow {
            String category;
            int units;
            double revenue;
            double sharePct;
        }

        List<CategoryRow> catRows = new ArrayList<>();
        for (Map.Entry<String, double[]> e : catStats.entrySet()) {
            if (e.getValue()[0] > 0 || e.getValue()[1] > 0) {
                CategoryRow r = new CategoryRow();
                r.category = e.getKey();
                r.units = (int) Math.round(e.getValue()[0]);
                r.revenue = e.getValue()[1];
                r.sharePct = totalCatRev > 0 ? (r.revenue / totalCatRev) * 100.0 : 0.0;
                catRows.add(r);
            }
        }
        catRows.sort((a, b) -> Double.compare(b.revenue, a.revenue));

        TableView<CategoryRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<CategoryRow, String> cCat = new TableColumn<>("Category Name");
        cCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().category));
        cCat.setPrefWidth(220);

        TableColumn<CategoryRow, Number> cQty = new TableColumn<>("Units Sold");
        cQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().units));
        cQty.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace;");

        TableColumn<CategoryRow, Number> cRev = new TableColumn<>("Total Revenue (₹)");
        cRev.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().revenue));
        cRev.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #34D399;");
            }
        });

        TableColumn<CategoryRow, Number> cShare = new TableColumn<>("Share (%)");
        cShare.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().sharePct));
        cShare.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                if (emp || v == null) setText(null);
                else {
                    setText(String.format("%.1f%%", v.doubleValue()));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #F2CA6B;");
                }
            }
        });

        table.getColumns().addAll(cCat, cQty, cRev, cShare);
        table.setItems(FXCollections.observableArrayList(catRows));

        root.getChildren().addAll(kpiRow, pie, table);
        return root;
    }

    // =========================================================================
    // 8. GST / Tax Summary Report
    // =========================================================================
    Node buildGstTaxReport() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14, 0, 0, 0));

        List<Bill> bills = app.getData().getAllBills();

        class GstRow {
            String month;
            int invoices = 0;
            double taxable = 0;
            double cgst = 0;
            double sgst = 0;
            double igst = 0;
            double totalTax = 0;
            double grandTotal = 0;
        }

        Map<String, GstRow> map = new TreeMap<>(Comparator.reverseOrder());
        double totalTaxableAll = 0;
        double totalTaxAll = 0;
        double totalTurnoverAll = 0;

        for (Bill b : bills) {
            String d = b.getDate();
            if (d == null || d.length() < 7) continue;
            String mKey = d.substring(0, 7);
            GstRow r = map.computeIfAbsent(mKey, k -> {
                GstRow n = new GstRow();
                n.month = k;
                return n;
            });

            r.invoices += 1;
            r.taxable += b.getTotals().getTaxable();
            r.cgst += b.getTotals().getCgst();
            r.sgst += b.getTotals().getSgst();
            r.igst += b.getTotals().getIgst();
            double tax = b.getTotals().getCgst() + b.getTotals().getSgst() + b.getTotals().getIgst();
            r.totalTax += tax;
            r.grandTotal += b.getTotals().getGrandTotal();

            totalTaxableAll += b.getTotals().getTaxable();
            totalTaxAll += tax;
            totalTurnoverAll += b.getTotals().getGrandTotal();
        }

        HBox kpiRow = new HBox(12);
        kpiRow.getChildren().addAll(
            createKpiCard("Total Taxable Turnover", "₹ " + currencyFmt.format(totalTaxableAll), "Net taxable value", "#38BDF8"),
            createKpiCard("Total GST Collected", "₹ " + currencyFmt.format(totalTaxAll), "CGST + SGST + IGST liability", "#F87171"),
            createKpiCard("Total Gross Turnover", "₹ " + currencyFmt.format(totalTurnoverAll), "Aggregate invoice value", "#F2CA6B")
        );

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_RIGHT);
        Button exportGstBtn = UiTheme.secondaryBtn("Export GSTR-1 CSV");
        exportGstBtn.setOnAction(e -> exportCsv(
            "gstr1_summary_" + LocalDate.now() + ".csv",
            "Period,Invoices,Taxable Value,CGST,SGST,IGST,Total Tax,Gross Total\n",
            map.values().stream().map(r -> String.format("\"%s\",%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f",
                r.month, r.invoices, r.taxable, r.cgst, r.sgst, r.igst, r.totalTax, r.grandTotal))
                .collect(Collectors.toList())
        ));
        topBar.getChildren().add(exportGstBtn);

        TableView<GstRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<GstRow, String> cM = new TableColumn<>("Tax Period (Month)");
        cM.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().month));

        TableColumn<GstRow, Number> cInv = new TableColumn<>("Invoices");
        cInv.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().invoices));
        cInv.setStyle("-fx-alignment: CENTER; -fx-font-family: 'Consolas', monospace;");

        TableColumn<GstRow, Number> cTaxable = new TableColumn<>("Taxable Value (₹)");
        cTaxable.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().taxable));
        cTaxable.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace;");
            }
        });

        TableColumn<GstRow, Number> cCgst = new TableColumn<>("CGST (₹)");
        cCgst.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().cgst));
        cCgst.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace;");
            }
        });

        TableColumn<GstRow, Number> cSgst = new TableColumn<>("SGST (₹)");
        cSgst.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().sgst));
        cSgst.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace;");
            }
        });

        TableColumn<GstRow, Number> cIgst = new TableColumn<>("IGST (₹)");
        cIgst.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().igst));
        cIgst.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace;");
            }
        });

        TableColumn<GstRow, Number> cTax = new TableColumn<>("Total Tax (₹)");
        cTax.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().totalTax));
        cTax.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #F87171;");
            }
        });

        table.getColumns().addAll(cM, cInv, cTaxable, cCgst, cSgst, cIgst, cTax);
        table.setItems(FXCollections.observableArrayList(new ArrayList<>(map.values())));

        root.getChildren().addAll(kpiRow, topBar, table);
        return root;
    }

    // =========================================================================
    // 9. Transport Performance Report
    // =========================================================================
    Node buildTransportPerformanceReport() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14, 0, 0, 0));

        List<Transport> transports = app.getData().getAllTransports();
        List<Buyer> buyers = app.getData().getAllBuyers();
        List<Transaction> txs = app.getData().getAllTransactions();

        class TransportStatsRow {
            String name;
            String vehicle;
            String phone;
            int buyerCount = 0;
            int totalParcels = 0;
            double goodsValue = 0;
        }

        Map<String, TransportStatsRow> map = new HashMap<>();
        for (Transport t : transports) {
            TransportStatsRow r = new TransportStatsRow();
            r.name = t.getName();
            r.vehicle = t.getVehicleNumber();
            r.phone = t.getPhone();
            map.put(t.getId(), r);
        }

        for (Buyer b : buyers) {
            if (b.getDefaultTransportId() != null && map.containsKey(b.getDefaultTransportId())) {
                map.get(b.getDefaultTransportId()).buyerCount++;
            }
        }

        int allParcels = 0;
        double allFreightVal = 0;

        for (Transaction t : txs) {
            Buyer b = buyers.stream().filter(by -> by.getId().equals(t.getBuyerId())).findFirst().orElse(null);
            if (b != null && b.getDefaultTransportId() != null && map.containsKey(b.getDefaultTransportId())) {
                TransportStatsRow r = map.get(b.getDefaultTransportId());
                r.totalParcels += t.getParcels();
                if ("sale".equalsIgnoreCase(t.getTransactionType())) {
                    r.goodsValue += t.getAmount();
                }
            }
            allParcels += t.getParcels();
            if ("sale".equalsIgnoreCase(t.getTransactionType())) {
                allFreightVal += t.getAmount();
            }
        }

        HBox kpiRow = new HBox(12);
        kpiRow.getChildren().addAll(
            createKpiCard("Registered Transporters", map.size() + " Agencies", "Active logistics partners", "#F2CA6B"),
            createKpiCard("Total Dispatched Parcels", allParcels + " Bales", "Dispatched shipment parcels", "#38BDF8"),
            createKpiCard("Total Cargo Value", "₹ " + currencyFmt.format(allFreightVal), "Goods value shipped via transport", "#34D399")
        );

        TableView<TransportStatsRow> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<TransportStatsRow, String> cName = new TableColumn<>("Transport Agency");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name));
        cName.setPrefWidth(220);

        TableColumn<TransportStatsRow, String> cVeh = new TableColumn<>("Vehicle Number");
        cVeh.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().vehicle.isBlank() ? "—" : d.getValue().vehicle));
        cVeh.setPrefWidth(140);

        TableColumn<TransportStatsRow, Number> cBuyers = new TableColumn<>("Assigned Buyers");
        cBuyers.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().buyerCount));
        cBuyers.setStyle("-fx-alignment: CENTER; -fx-font-family: 'Consolas', monospace;");

        TableColumn<TransportStatsRow, Number> cParcels = new TableColumn<>("Total Parcels Sent");
        cParcels.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().totalParcels));
        cParcels.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        TableColumn<TransportStatsRow, Number> cVal = new TableColumn<>("Goods Value Carried (₹)");
        cVal.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().goodsValue));
        cVal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean emp) {
                super.updateItem(v, emp);
                setText(emp || v == null ? null : "₹ " + currencyFmt.format(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-family: 'Consolas', monospace;");
            }
        });

        table.getColumns().addAll(cName, cVeh, cBuyers, cParcels, cVal);
        table.setItems(FXCollections.observableArrayList(new ArrayList<>(map.values())));

        root.getChildren().addAll(kpiRow, table);
        return root;
    }

    private void exportCsv(String defaultFilename, String header, List<String> rows) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Report to CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        fc.setInitialFileName(defaultFilename);
        File file = fc.showSaveDialog(owner.getScene().getWindow());
        if (file == null) return;

        try (FileWriter w = new FileWriter(file)) {
            w.write(header);
            for (String r : rows) {
                w.write(r + "\n");
            }
            Toast.show(app.getRootPane(), "Export Successful", "Report exported to " + file.getName(), false);
        } catch (IOException ex) {
            Toast.show(app.getRootPane(), "Export Error", "Failed to export: " + ex.getMessage(), true);
        }
    }
}
