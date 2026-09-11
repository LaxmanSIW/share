package com.invoicestudio.ui.views;

import com.invoicestudio.model.*;
import com.invoicestudio.service.BillingService;
import com.invoicestudio.service.CsvService;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import com.invoicestudio.ui.UiTheme;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Financial Dashboard — v3 redesign.
 * Same feature set (month navigation, recurring banner, 4 KPIs, 6-month trend,
 * GST summary, top buyers, recent invoices, GST CSV export) with production
 * polish: animated KPI counters, CSS hover-lift cards, cached data access and
 * zero inline styles.
 */
public class DashboardView extends BorderPane {

    private final StudioApp app;

    private LocalDate selectedMonth = LocalDate.now().withDayOfMonth(1);
    private final Label monthLabel = new Label();
    private final VBox contentBox = new VBox(20);
    private final Timeline counterTimeline = new Timeline();

    public DashboardView(StudioApp app) {
        this.app = app;

        setPadding(new Insets(20, 24, 16, 24));
        getStyleClass().add("bg-app");

        contentBox.setFillWidth(true);
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("scroll-pane");
        setCenter(scroll);

        refresh();
    }

    public void refresh() {
        counterTimeline.stop();
        contentBox.getChildren().clear();

        Settings settings = app.getData().getSettings();
        List<Bill> allBills = app.getData().getAllBills();

        contentBox.getChildren().add(createTopBar());

        List<Bill> dueRecurring = allBills.stream()
                .filter(b -> b.getRepeat() != null && b.getRepeat() != RepeatCadence.NONE && b.getDocType() == DocType.INVOICE && BillingService.isRepeatDue(b))
                .collect(Collectors.toList());
        if (!dueRecurring.isEmpty()) {
            contentBox.getChildren().add(createRecurringBanner(dueRecurring, settings));
        }

        contentBox.getChildren().add(createKpiCards(allBills, settings));

        HBox midRow = new HBox(20);
        midRow.getChildren().addAll(createChartCard(allBills, settings), createGstCard(allBills, settings));
        HBox.setHgrow(midRow.getChildren().get(0), Priority.ALWAYS);
        contentBox.getChildren().add(midRow);

        HBox botRow = new HBox(20);
        botRow.setFillHeight(true);
        VBox.setVgrow(botRow, Priority.ALWAYS);

        Node topBuyersCard = createTopBuyersCard(allBills, settings);
        Node recentBillsCard = createRecentBillsCard(allBills, settings);

        HBox.setHgrow(topBuyersCard, Priority.NEVER);
        HBox.setHgrow(recentBillsCard, Priority.ALWAYS);

        botRow.getChildren().addAll(topBuyersCard, recentBillsCard);
        contentBox.getChildren().add(botRow);
    }

    // ------------------------------------------------------------------
    // Top bar
    // ------------------------------------------------------------------

    private Node createTopBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Dashboard");
        title.getStyleClass().add("heading-l");
        titleBox.getChildren().add(title);

        // Segmented in-page switcher between Dashboard 1 and Dashboard 2
        HBox dashSwitch = new HBox(4);
        dashSwitch.getStyleClass().add("toggle-group-container");
        dashSwitch.setAlignment(Pos.CENTER_LEFT);

        Button btnOverview = new Button("Standard Overview");
        btnOverview.getStyleClass().addAll("btn-filter-pill", "active");

        Button btnDash2 = new Button("Financial & Logistics (Dashboard 2) ↗");
        btnDash2.getStyleClass().add("btn-filter-pill");
        btnDash2.setTooltip(new Tooltip("Switch to Alpha CC/CS Financial & Logistics Dashboard"));
        btnDash2.setOnAction(e -> app.showDashboard2());

        dashSwitch.getChildren().addAll(btnOverview, btnDash2);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox monthCtrl = new HBox(8);
        monthCtrl.getStyleClass().add("month-switcher");
        monthCtrl.setAlignment(Pos.CENTER);

        Button prevBtn = UiTheme.smallBtn("‹");
        prevBtn.setTooltip(new Tooltip("Previous Month"));
        prevBtn.setOnAction(e -> {
            selectedMonth = selectedMonth.minusMonths(1);
            refresh();
        });

        monthLabel.setText(selectedMonth.format(DateTimeFormatter.ofPattern("MMM yyyy")));
        monthLabel.getStyleClass().add("month-label");

        Button nextBtn = UiTheme.smallBtn("›");
        nextBtn.setTooltip(new Tooltip("Next Month"));
        nextBtn.setOnAction(e -> {
            selectedMonth = selectedMonth.plusMonths(1);
            refresh();
        });

        Button todayBtn = UiTheme.smallBtn("Current Month");
        todayBtn.setTooltip(new Tooltip("Jump to current calendar month"));
        todayBtn.setOnAction(e -> {
            selectedMonth = LocalDate.now().withDayOfMonth(1);
            refresh();
        });

        monthCtrl.getChildren().addAll(prevBtn, monthLabel, nextBtn, todayBtn);

        Button newBillBtn = UiTheme.goldBtn("+ Create Bill");
        newBillBtn.setTooltip(new Tooltip("Create New Invoice or Bill"));
        newBillBtn.setOnAction(e -> app.showCreateBill(null, null));

        bar.getChildren().addAll(titleBox, dashSwitch, spacer, monthCtrl, newBillBtn);
        return bar;
    }

    private Node createRecurringBanner(List<Bill> dueRecurring, Settings settings) {
        HBox banner = new HBox(16);
        banner.getStyleClass().add("banner-gold");
        banner.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("⟳");
        icon.getStyleClass().addAll("icon-accent", "icon-lg");

        VBox text = new VBox(2);
        Label title = new Label(dueRecurring.size() + " Recurring Invoice" + (dueRecurring.size() == 1 ? "" : "s") + " Due");
        title.getStyleClass().addAll("table-cell-title", "accent-gold");
        Label sub = new Label("Invoices scheduled to roll forward are ready for generation.");
        sub.getStyleClass().add("kpi-subtext");
        text.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button autoBtn = UiTheme.smallBtn(settings.isAutoRecurring() ? "Auto-Create: ON" : "Turn ON Auto-Create");
        autoBtn.setTooltip(new Tooltip("Toggle automatic creation of recurring invoices"));
        autoBtn.setOnAction(e -> {
            settings.setAutoRecurring(!settings.isAutoRecurring());
            app.getData().saveSettings(settings);
            refresh();
            Toast.show(app.getRootPane(), "Settings Updated", "Auto recurring generation is now " + (settings.isAutoRecurring() ? "enabled" : "disabled"), false);
        });

        Button runSweepBtn = UiTheme.smallBtn("Create Due Now");
        runSweepBtn.getStyleClass().remove("button-secondary");
        runSweepBtn.getStyleClass().add("gold-btn");
        runSweepBtn.setTooltip(new Tooltip("Instantly generate all overdue recurring invoices"));
        runSweepBtn.setOnAction(e -> {
            var res = app.getRecurringEngine().runSweep(true);
            app.getData().invalidateBills();
            refresh();
            Toast.show(app.getRootPane(), "Sweep Completed", "Generated " + res.created.size() + " new invoices.", false);
        });

        banner.getChildren().addAll(icon, text, spacer, autoBtn, runSweepBtn);
        return banner;
    }

    // ------------------------------------------------------------------
    // KPI cards with animated counters
    // ------------------------------------------------------------------

    private Node createKpiCards(List<Bill> bills, Settings settings) {
        String monthKey = selectedMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String cur = settings.getCurrency();

        List<Bill> invoices = bills.stream()
                .filter(b -> b.getDocType() == DocType.INVOICE && b.getStatus() != BillStatus.CANCELLED)
                .collect(Collectors.toList());

        double monthRevenue = invoices.stream()
                .filter(b -> b.getDate() != null && b.getDate().startsWith(monthKey))
                .mapToDouble(b -> b.getTotals().getGrandTotal()).sum();

        String prevMonthKey = selectedMonth.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        double prevRevenue = invoices.stream()
                .filter(b -> b.getDate() != null && b.getDate().startsWith(prevMonthKey))
                .mapToDouble(b -> b.getTotals().getGrandTotal()).sum();
        double momDelta = prevRevenue > 0 ? ((monthRevenue - prevRevenue) / prevRevenue) * 100 : (monthRevenue > 0 ? 100 : 0);

        double totalCollected = invoices.stream().mapToDouble(b -> {
            double p = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (p == 0 && b.getStatus() == BillStatus.PAID) p = b.getTotals().getGrandTotal();
            return p;
        }).sum();

        double monthCollected = invoices.stream().mapToDouble(b -> {
            double sum = 0;
            for (BillPayment p : b.getPayments()) {
                if (p.getDate() != null && p.getDate().startsWith(monthKey)) sum += p.getAmount();
            }
            if (b.getPayments().isEmpty() && b.getStatus() == BillStatus.PAID && b.getDate() != null && b.getDate().startsWith(monthKey)) {
                sum += b.getTotals().getGrandTotal();
            }
            return sum;
        }).sum();

        List<Bill> unpaidList = invoices.stream().filter(b -> {
            double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (paid == 0 && b.getStatus() == BillStatus.PAID) return false;
            return b.getTotals().getGrandTotal() - paid > 0.01;
        }).collect(Collectors.toList());
        double totalDue = unpaidList.stream().mapToDouble(b -> {
            double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            return Math.max(0, b.getTotals().getGrandTotal() - paid);
        }).sum();

        double totalSales = invoices.stream().mapToDouble(b -> b.getTotals().getGrandTotal()).sum();
        double avgTicket = invoices.isEmpty() ? 0 : totalSales / invoices.size();

        Label revenueVal = UiTheme.kpiValue(cur + "0.00");
        Label collectedVal = UiTheme.kpiValue(cur + "0.00");
        Label dueVal = UiTheme.kpiValue(cur + "0.00");
        Label lifetimeVal = UiTheme.kpiValue(cur + "0.00");

        HBox grid = new HBox(16);
        grid.getChildren().addAll(
                UiTheme.kpiCard("THIS MONTH REVENUE", revenueVal,
                        String.format("%+.1f%% vs last month", momDelta), momDelta >= 0 ? "accent-emerald" : "accent-red"),
                UiTheme.kpiCard("TOTAL COLLECTED", collectedVal,
                        String.format("%s%.2f collected this month", cur, monthCollected), "accent-gold"),
                UiTheme.kpiCard("OUTSTANDING DUE", dueVal,
                        unpaidList.size() + " pending invoice" + (unpaidList.size() == 1 ? "" : "s"), "accent-amber"),
                UiTheme.kpiCard("LIFETIME SALES", lifetimeVal,
                        String.format("%s%.2f avg ticket size", cur, avgTicket), "accent-sky")
        );

        for (Node c : grid.getChildren()) HBox.setHgrow(c, Priority.ALWAYS);

        // Animate the numbers counting up — subtle, 550ms, eases out.
        animateValue(revenueVal, cur, monthRevenue);
        animateValue(collectedVal, cur, totalCollected);
        animateValue(dueVal, cur, totalDue);
        animateValue(lifetimeVal, cur, totalSales);

        return grid;
    }

    private void animateValue(Label label, String currency, double target) {
        final int steps = 18;
        final long durationMs = 550;
        counterTimeline.getKeyFrames().clear();

        for (int i = 1; i <= steps; i++) {
            final double frac = i / (double) steps;
            // ease-out cubic
            final double eased = 1 - Math.pow(1 - frac, 3);
            counterTimeline.getKeyFrames().add(new KeyFrame(
                    Duration.millis(durationMs * i / steps),
                    e -> {
                        double v = target * eased;
                        label.setText(String.format("%s%.2f", currency, v));
                    },
                    new KeyValue[0]
            ));
        }
        counterTimeline.setCycleCount(1);
        counterTimeline.setAutoReverse(false);
        counterTimeline.setOnFinished(e -> label.setText(String.format("%s%.2f", currency, target)));
        counterTimeline.play();
    }

    // ------------------------------------------------------------------
    // 6-month revenue trend
    // ------------------------------------------------------------------

    private Node createChartCard(List<Bill> bills, Settings settings) {
        VBox card = UiTheme.card(12);

        Label title = UiTheme.sectionTitle("6-Month Revenue Trend");
        card.getChildren().add(title);

        List<String> months = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate d = selectedMonth.minusMonths(i);
            String key = d.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            double rev = bills.stream()
                    .filter(b -> b.getDocType() == DocType.INVOICE && b.getStatus() != BillStatus.CANCELLED && b.getDate() != null && b.getDate().startsWith(key))
                    .mapToDouble(b -> b.getTotals().getGrandTotal()).sum();
            months.add(d.format(DateTimeFormatter.ofPattern("MMM yy")));
            values.add(rev);
        }

        double maxVal = Math.max(1.0, values.stream().mapToDouble(v -> v).max().orElse(1.0));

        HBox chart = new HBox(14);
        chart.setAlignment(Pos.BOTTOM_CENTER);
        chart.setPrefHeight(150);
        chart.setPadding(new Insets(10, 0, 0, 0));

        for (int i = 0; i < months.size(); i++) {
            VBox col = new VBox(6);
            col.setAlignment(Pos.BOTTOM_CENTER);
            HBox.setHgrow(col, Priority.ALWAYS);

            double val = values.get(i);
            double barH = Math.max(4.0, (val / maxVal) * 105.0);

            Label valLbl = new Label(val > 0 ? String.format("%.0f", val) : "");
            valLbl.getStyleClass().add("chart-bar-label");

            Rectangle bar = new Rectangle(28, barH);
            boolean isCurrent = i == 5;
            bar.setFill(Color.web(isCurrent ? "#D9A13B" : "#2A374A"));
            bar.setArcWidth(4);
            bar.setArcHeight(4);
            if (isCurrent && val > 0) {
                // Gentle grow-in for the current month's bar.
                bar.setHeight(4);
                Timeline grow = new Timeline(new KeyFrame(Duration.millis(450),
                        new KeyValue(bar.heightProperty(), barH)));
                grow.setDelay(Duration.millis(120));
                grow.play();
            }

            Label mLbl = new Label(months.get(i));
            mLbl.getStyleClass().add("chart-month-label");
            if (isCurrent) mLbl.getStyleClass().add("current");

            col.getChildren().addAll(valLbl, bar, mLbl);
            chart.getChildren().add(col);
        }

        card.getChildren().add(chart);
        return card;
    }

    // ------------------------------------------------------------------
    // GST summary
    // ------------------------------------------------------------------

    private Node createGstCard(List<Bill> bills, Settings settings) {
        VBox card = UiTheme.card(12);
        card.setMinWidth(320);

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label title = UiTheme.sectionTitle("GST Summary (Live)");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button exportGstBtn = UiTheme.smallBtn("Export CSV");
        exportGstBtn.setTooltip(new Tooltip("Export GST breakdown to CSV"));
        exportGstBtn.setOnAction(e -> exportGstSummary(bills));

        top.getChildren().addAll(title, sp, exportGstBtn);
        card.getChildren().add(top);

        String cur = settings.getCurrency();
        List<Bill> invoices = bills.stream()
                .filter(b -> b.getDocType() == DocType.INVOICE && b.getStatus() != BillStatus.CANCELLED)
                .collect(Collectors.toList());

        double totalTaxable = invoices.stream().mapToDouble(b -> b.getTotals().getTaxable()).sum();
        double totalCgst = invoices.stream().mapToDouble(b -> b.getTotals().getCgst()).sum();
        double totalSgst = invoices.stream().mapToDouble(b -> b.getTotals().getSgst()).sum();
        double totalIgst = invoices.stream().mapToDouble(b -> b.getTotals().getIgst()).sum();
        double totalTax = totalCgst + totalSgst + totalIgst;
        double totalDiscount = invoices.stream().mapToDouble(b -> b.getTotals().getDiscount()).sum();

        VBox stats = new VBox(8);
        stats.getChildren().addAll(
                UiTheme.statRow("Total Taxable Sales", String.format("%s%.2f", cur, totalTaxable)),
                UiTheme.statRow("CGST (Central)", String.format("%s%.2f", cur, totalCgst)),
                UiTheme.statRow("SGST (State)", String.format("%s%.2f", cur, totalSgst)),
                UiTheme.statRow("IGST (Interstate)", String.format("%s%.2f", cur, totalIgst)),
                UiTheme.statRow("Total Tax Liability", String.format("%s%.2f", cur, totalTax)),
                UiTheme.statRow("Lifetime Discounts", String.format("%s%.2f", cur, totalDiscount))
        );
        card.getChildren().add(stats);
        return card;
    }

    // ------------------------------------------------------------------
    // Top buyers
    // ------------------------------------------------------------------

    private Node createTopBuyersCard(List<Bill> bills, Settings settings) {
        VBox card = UiTheme.card(12);
        card.setMinWidth(330);
        card.setMaxWidth(360);
        VBox.setVgrow(card, Priority.ALWAYS);
        card.setMaxHeight(Double.MAX_VALUE);

        Label title = UiTheme.sectionTitle("Top 5 Buyers (By Revenue)");
        card.getChildren().add(title);

        Map<String, double[]> buyerStats = new HashMap<>(); // [count, total]
        for (Bill b : bills) {
            if (b.getDocType() != DocType.INVOICE || b.getStatus() == BillStatus.CANCELLED) continue;
            String name = b.getVariables().getOrDefault("buyer_name", "Unknown Buyer");
            if (name.isBlank()) name = "Unknown Buyer";
            double[] arr = buyerStats.computeIfAbsent(name, k -> new double[2]);
            arr[0] += 1;
            arr[1] += b.getTotals().getGrandTotal();
        }

        List<Map.Entry<String, double[]>> list = buyerStats.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
                .limit(5)
                .collect(Collectors.toList());

        if (list.isEmpty()) {
            card.getChildren().add(UiTheme.emptyState("👥", "No buyer data available yet.", null));
        } else {
            VBox rows = new VBox(10);
            VBox.setVgrow(rows, Priority.ALWAYS);
            int rank = 1;
            for (Map.Entry<String, double[]> e : list) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);

                Label rankLbl = new Label("#" + rank++);
                rankLbl.getStyleClass().addAll("code-pill");

                VBox text = new VBox(2);
                Label nameLbl = new Label(e.getKey());
                nameLbl.getStyleClass().add("table-cell-title");
                Label invLbl = new Label((int) e.getValue()[0] + " invoice" + (e.getValue()[0] == 1 ? "" : "s"));
                invLbl.getStyleClass().add("kpi-subtext");
                text.getChildren().addAll(nameLbl, invLbl);

                Region s = new Region();
                HBox.setHgrow(s, Priority.ALWAYS);

                Label val = new Label(String.format("%s%.2f", settings.getCurrency(), e.getValue()[1]));
                val.getStyleClass().addAll("table-cell-mono", "accent-gold");
                row.getChildren().addAll(rankLbl, text, s, val);
                rows.getChildren().add(row);
            }
            card.getChildren().add(rows);
        }
        return card;
    }

    // ------------------------------------------------------------------
    // Recent invoices table
    // ------------------------------------------------------------------

    private Node createRecentBillsCard(List<Bill> bills, Settings settings) {
        VBox card = UiTheme.card(12);
        VBox.setVgrow(card, Priority.ALWAYS);
        card.setMaxHeight(Double.MAX_VALUE);

        HBox top = new HBox();
        top.setAlignment(Pos.CENTER_LEFT);
        Label title = UiTheme.sectionTitle("Recent Invoices");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button viewAllBtn = UiTheme.smallBtn("View All History →");
        viewAllBtn.setTooltip(new Tooltip("Navigate to full invoice history"));
        viewAllBtn.setOnAction(e -> app.showHistory());

        top.getChildren().addAll(title, sp, viewAllBtn);
        card.getChildren().add(top);

        TableView<Bill> table = new TableView<>();
        table.getStyleClass().add("table-view");
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMinHeight(240);
        table.setMaxHeight(Double.MAX_VALUE);

        TableColumn<Bill, String> colNo = new TableColumn<>("Bill No");
        colNo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getBillNo()));
        colNo.setMinWidth(100);
        colNo.setPrefWidth(110);

        TableColumn<Bill, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDate()));
        colDate.setMinWidth(90);
        colDate.setPrefWidth(95);

        TableColumn<Bill, String> colBuyer = new TableColumn<>("Buyer");
        colBuyer.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getVariables().getOrDefault("buyer_name", "—")));
        colBuyer.setMinWidth(160);
        colBuyer.setPrefWidth(220);

        TableColumn<Bill, String> colTotal = new TableColumn<>("Total");
        colTotal.setCellValueFactory(data -> new SimpleStringProperty(String.format("%s%.2f", settings.getCurrency(), data.getValue().getTotals().getGrandTotal())));
        colTotal.setMinWidth(100);
        colTotal.setPrefWidth(110);

        TableColumn<Bill, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus().getLabel()));
        colStatus.setMinWidth(80);
        colStatus.setPrefWidth(90);

        table.getColumns().addAll(colNo, colDate, colBuyer, colTotal, colStatus);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        List<Bill> recent = bills.stream().limit(15).collect(Collectors.toList());
        table.getItems().addAll(recent);

        card.getChildren().add(table);
        return card;
    }

    private void exportGstSummary(List<Bill> bills) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export GST Monthly Summary CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Spreadsheet (*.csv)", "*.csv"));
        fc.setInitialFileName("gst-monthly-summary-" + LocalDate.now() + ".csv");
        File f = fc.showSaveDialog(app.getPrimaryStage());
        if (f != null) {
            try (FileWriter fw = new FileWriter(f)) {
                fw.write(CsvService.exportGstSummary(bills));
                Toast.show(app.getRootPane(), "Export Successful", "GST report saved to " + f.getName(), false);
            } catch (Exception ex) {
                Toast.show(app.getRootPane(), "Export Failed", ex.getMessage(), true);
            }
        }
    }
}
