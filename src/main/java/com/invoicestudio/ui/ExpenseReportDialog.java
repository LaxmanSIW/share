package com.invoicestudio.ui;

import com.invoicestudio.model.Expense;
import com.invoicestudio.service.AppExecutors;
import com.invoicestudio.service.ExpenseAnalytics;
import com.invoicestudio.service.ExpenseAnalytics.Bucket;
import com.invoicestudio.service.ExpenseAnalytics.Report;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.geometry.Side;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;

/**
 * Account / Category expense report dialog — industry-standard layout:
 * period presets + KPI cards + monthly trend bar chart + category-split pie
 * + voucher table with CSV export. Both report dimensions share this shell;
 * the math lives in {@link ExpenseAnalytics} (pure, unit-tested).
 *
 * Charts reuse the app's themed .chart CSS (same as Reports view).
 */
public class ExpenseReportDialog extends Stage {

    private final com.invoicestudio.ui.StudioApp app;
    private final String dimension;   // "Account" or "Category"
    private final String filterName;  // account/category name, or null = overview

    private final ComboBox<String> periodBox = new ComboBox<>();
    private final DatePicker fromPick = UiTheme.datePicker("From");
    private final DatePicker toPick = UiTheme.datePicker("To");
    private final Label kpiTotal = UiTheme.kpiValue("₹0.00");
    private final Label kpiCount = UiTheme.kpiValue("0");
    private final Label kpiAvg = UiTheme.kpiValue("₹0.00");
    private final Label kpiTop = UiTheme.kpiValue("—");
    private final TableView<Expense> table = new TableView<>();
    private final BarChart<String, Number> trendChart;
    private final PieChart splitChart;
    private final DecimalFormat money = new DecimalFormat("#,##0.00");

    private Report current;

    public ExpenseReportDialog(com.invoicestudio.ui.StudioApp app, String dimension, String filterName) {
        this.app = app;
        this.dimension = dimension;
        this.filterName = filterName;

        boolean isAccount = "Account".equals(dimension);
        boolean isCategory = "Category".equals(dimension);
        String name = filterName == null ? "" : filterName;
        setTitle(isAccount ? "Account Report — " + name
                : isCategory ? "Category Report — " + name
                : "Expense Report — All Accounts & Categories");
        initModality(Modality.NONE);
        initOwner(app.getPrimaryStage());

        VBox root = new VBox(14);
        root.setPadding(new Insets(18));
        root.getStyleClass().add("dialog-root");

        // --- Header + period controls ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(2);
        Label title = new Label(isAccount ? "Account Report" : "Category Report");
        title.getStyleClass().add("heading-l");
        Label sub = new Label((filterName != null && !filterName.isBlank()) ? filterName : "All expense vouchers");
        sub.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(title, sub);
        Region hsp = new Region();
        HBox.setHgrow(hsp, Priority.ALWAYS);

        periodBox.getItems().addAll("This Month", "Last 30 Days", "This Quarter", "This Year", "All Time", "Custom");
        periodBox.setValue("This Year");
        periodBox.setOnAction(e -> onPeriodChanged());
        fromPick.valueProperty().addListener((o, ov, nv) -> { if ("Custom".equals(periodBox.getValue())) rebuild(); });
        toPick.valueProperty().addListener((o, ov, nv) -> { if ("Custom".equals(periodBox.getValue())) rebuild(); });

        Button csvBtn = UiTheme.smallBtn("Export CSV");
        csvBtn.setOnAction(e -> exportCsv());

        Button closeBtn = UiTheme.smallBtn("Close");
        closeBtn.setOnAction(e -> close());

        header.getChildren().addAll(titleBox, hsp, new Label("Period:"), periodBox, fromPick, toPick, csvBtn, closeBtn);
        fromPick.setDisable(!"Custom".equals(periodBox.getValue()));
        toPick.setDisable(!"Custom".equals(periodBox.getValue()));

        // --- KPI row ---
        HBox kpis = new HBox(12);
        kpis.getChildren().addAll(
                UiTheme.kpiCard("TOTAL SPEND", kpiTotal, "Selected period", "accent-red"),
                UiTheme.kpiCard("VOUCHERS", kpiCount, "Entries in period", "accent-emerald"),
                UiTheme.kpiCard("AVERAGE", kpiAvg, "Per voucher", "accent-blue"),
                UiTheme.kpiCard("TOP " + (isAccount ? "CATEGORY" : "ACCOUNT"), kpiTop, "By total spend", "accent-gold"));

        // --- Charts row ---
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Spend (₹)");
        trendChart = new BarChart<>(xAxis, yAxis);
        trendChart.setTitle("Monthly Trend");
        trendChart.setLegendVisible(false);
        trendChart.setPrefHeight(240);

        splitChart = new PieChart();
        splitChart.setTitle(isAccount ? "Category Split" : "Account Split");
        splitChart.setPrefHeight(240);
        splitChart.setLegendSide(Side.LEFT);

        HBox.setHgrow(trendChart, Priority.ALWAYS);
        HBox.setHgrow(splitChart, Priority.ALWAYS);
        HBox charts = new HBox(14, trendChart, splitChart);
        charts.setPrefHeight(250);

        // --- Voucher table ---
        buildTable();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(UiTheme.emptyState("🧾", "No vouchers in this period",
                "Adjust the period or record expenses in the register."));
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getChildren().addAll(header, kpis, charts, table);
        Scene scene = new Scene(root, 1020, 720);
        URL css = getClass().getResource("/css/globalfile.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        setScene(scene);
        scene.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ESCAPE) close(); });

        onPeriodChanged(); // initial build
    }

    private void buildTable() {
        TableColumn<Expense, String> cDate = new TableColumn<>("Date");
        cDate.setPrefWidth(95);
        cDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));

        TableColumn<Expense, String> cCat = new TableColumn<>("Category");
        cCat.setPrefWidth(160);
        cCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory()));

        TableColumn<Expense, String> cPayee = new TableColumn<>("Paid To");
        cPayee.setPrefWidth(160);
        cPayee.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getPayee().isBlank() ? "—" : d.getValue().getPayee()));

        TableColumn<Expense, String> cDesc = new TableColumn<>("Description");
        cDesc.setPrefWidth(220);
        cDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));

        TableColumn<Expense, String> cAmt = new TableColumn<>("Amount");
        cAmt.setPrefWidth(110);
        cAmt.setCellValueFactory(d -> new SimpleStringProperty("₹" + money.format(d.getValue().getAmount())));
        cAmt.setStyle("-fx-alignment: CENTER-RIGHT;");

        table.getColumns().addAll(cDate, cCat, cPayee, cDesc, cAmt);
    }

    private void onPeriodChanged() {
        String p = periodBox.getValue();
        boolean custom = "Custom".equals(p);
        fromPick.setDisable(!custom);
        toPick.setDisable(!custom);
        LocalDate today = LocalDate.now();
        switch (p == null ? "" : p) {
            case "This Month" -> { fromPick.setValue(today.withDayOfMonth(1)); toPick.setValue(today); }
            case "Last 30 Days" -> { fromPick.setValue(today.minusDays(29)); toPick.setValue(today); }
            case "This Quarter" -> {
                int qm = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                fromPick.setValue(LocalDate.of(today.getYear(), qm, 1));
                toPick.setValue(today);
            }
            case "This Year" -> { fromPick.setValue(today.withDayOfYear(1)); toPick.setValue(today); }
            case "All Time" -> { fromPick.setValue(null); toPick.setValue(null); }
            case "Custom" -> { if (fromPick.getValue() == null) fromPick.setValue(today.withDayOfYear(1));
                               if (toPick.getValue() == null) toPick.setValue(today); }
        }
        rebuild();
    }

    private void rebuild() {
        // Compute off the FX thread; paint when ready (big registers stay fluid).
        String from = fromPick.getValue() != null ? fromPick.getValue().toString() : "";
        String to = toPick.getValue() != null ? toPick.getValue().toString() : "";
        final Report r;
        try {
            List<Expense> all = app.getData().getAllExpenses();
            if ("Account".equals(dimension)) r = ExpenseAnalytics.forAccount(all, filterName, from, to);
            else if ("Category".equals(dimension)) r = ExpenseAnalytics.forCategory(all, filterName, from, to);
            else r = ExpenseAnalytics.overall(all, from, to);
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
            return;
        }
        AppExecutors.runOnFx(() -> paint(r));
    }

    private void paint(Report r) {
        current = r;
        String cur = app.getData().getSettings().getCurrency();
        kpiTotal.setText(cur + money.format(r.total()));
        kpiCount.setText(String.valueOf(r.voucherCount()));
        kpiAvg.setText(cur + money.format(r.average()));

        boolean isAccount = "Account".equals(dimension);
        List<Bucket> topDim = isAccount ? r.byCategory() : r.byAccount();
        kpiTop.setText(topDim.isEmpty() ? "—" : topDim.get(0).key());

        table.setItems(FXCollections.<Expense>observableArrayList(r.vouchers()));

        // Charts
        trendChart.getData().clear();
        javafx.scene.chart.XYChart.Series<String, Number> series = new javafx.scene.chart.XYChart.Series<>();
        for (Bucket b : r.byMonth()) {
            series.getData().add(new javafx.scene.chart.XYChart.Data<>(
                    ExpenseAnalytics.monthLabel(b.key()), b.total()));
        }
        trendChart.getData().add(series);

        splitChart.getData().clear();
        List<Bucket> splitDim = isAccount ? r.byCategory() : r.byAccount();
        int shown = 0;
        double otherTotal = 0;
        for (Bucket b : splitDim) {
            if (shown < 7) {
                splitChart.getData().add(new PieChart.Data(b.key(), b.total()));
                shown++;
            } else {
                otherTotal += b.total();
            }
        }
        if (otherTotal > 0) splitChart.getData().add(new PieChart.Data("Other", otherTotal));
    }

    private void exportCsv() {
        if (current == null) return;
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export Report to CSV");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Spreadsheet (*.csv)", "*.csv"));
        String base = (filterName != null && !filterName.isBlank() ? filterName.replaceAll("\\W+", "-") : "expenses")
                + "-" + LocalDate.now();
        fc.setInitialFileName(base + ".csv");
        java.io.File dest = fc.showSaveDialog(app.getPrimaryStage());
        if (dest == null) return;
        try (java.io.FileWriter fw = new java.io.FileWriter(dest)) {
            fw.write(ExpenseAnalytics.csv(current, app.getData().getSettings().getCurrency()));
            Toast.show(app.getRootPane(), "Export Successful",
                    current.vouchers() + " vouchers exported.", false);
        } catch (Exception ex) {
            Toast.show(app.getRootPane(), "Export Failed", ex.getMessage(), true);
        }
    }
}
