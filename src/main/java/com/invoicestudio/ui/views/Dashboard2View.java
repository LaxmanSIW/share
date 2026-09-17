package com.invoicestudio.ui.views;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.Buyer;
import com.invoicestudio.model.Transaction;
import com.invoicestudio.service.AppFormatters;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.UiTheme;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.PopupWindow;
import javafx.util.Duration;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Dashboard 2 (Financial & Logistics Dashboard) — Version 4.0.0
 * Matches the layout and analytics of the reference financial web dashboard.
 * Coexists seamlessly with the original Dashboard 1.
 */

public class Dashboard2View extends BorderPane {

    // Cached formatters — ofPattern re-parses its pattern on every call (skill 2.1).
    private static final DateTimeFormatter F_YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter F_MON_YY = DateTimeFormatter.ofPattern("MMM yy");
    private static final DateTimeFormatter F_MON_YYYY = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter F_DD_MMM = DateTimeFormatter.ofPattern("dd MMM");
    private static final DateTimeFormatter F_DD_MM = DateTimeFormatter.ofPattern("dd/MM");

    private final StudioApp app;
    private final VBox contentBox = new VBox(20);
    /** Indian-grouped money format from the shared cache (skill 2.1) — same pattern, zero re-parse. */
    private final DecimalFormat currencyFmt = AppFormatters.inrFormat();

    private String activeBook = "ALL"; // "ALL", "CC", "CS"
    private String parcelPeriod = "Month"; // "Day", "Week", "Month", "Year"
    private String recentType = "Transactions"; // "Transactions", "Bills"

    /** Harness-visible counters: how many section-scoped swaps ran (no page rebuild). */
    public int recentSwapCount;
    public int parcelSwapCount;

    public Dashboard2View(StudioApp app) {
        this.app = app;
        setPadding(new Insets(20, 24, 20, 24));
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
        contentBox.getChildren().clear();

        List<Transaction> allTxs = app.getData().getAllTransactions().stream()
            .filter(t -> "ALL".equals(activeBook) || activeBook.equalsIgnoreCase(t.getBookType()))
            .collect(Collectors.toList());

        List<Bill> allBills = app.getData().getAllBills();

        // 1. Top Bar
        contentBox.getChildren().add(createTopBar());

        // 2. All-Time Accounts Overview (5 KPI Cards)
        contentBox.getChildren().add(createAllTimeKpis(allTxs));

        // 3. Current Period & Logistics Performance (4 KPI Cards with Trends)
        contentBox.getChildren().add(createCurrentPeriodKpis(allTxs));

        // 4. Two Charts Row (Monthly Sales vs Payments, Monthly Trouser Movement)
        contentBox.getChildren().add(createChartsRow(allTxs));

        // 5. Parcel Counts Analysis
        contentBox.getChildren().add(createParcelAnalysisCard(allTxs));

        // 6. Top 5 Widgets (Debtors, Paymasters, Volume Leaders)
        contentBox.getChildren().add(createTop5WidgetsRow(allTxs));

        // 7. Recent Transactions / Bills Table
        contentBox.getChildren().add(createRecentActivityCard(allTxs, allBills));
    }

    private Node createTopBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Dashboard 2 — Financial & Logistics");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("All-time and current period sales, receipts, ledger balance, and logistics performance.");
        subtitle.getStyleClass().add("kpi-subtext");
        titleBox.getChildren().addAll(title, subtitle);

        // Segmented in-page switcher between Dashboard 1 and Dashboard 2
        HBox dashSwitch = new HBox(4);
        dashSwitch.getStyleClass().add("toggle-group-container");
        dashSwitch.setAlignment(Pos.CENTER_LEFT);

        Button btnOverview = new Button("Standard Overview");
        btnOverview.getStyleClass().add("btn-filter-pill");
        btnOverview.setTooltip(new Tooltip("Return to the standard InvoiceStudio overview dashboard"));
        btnOverview.setOnAction(e -> app.showDashboard());

        Button btnDash2 = new Button("Financial & Logistics (Dashboard 2)");
        btnDash2.getStyleClass().addAll("btn-filter-pill", "active");

        dashSwitch.getChildren().addAll(btnOverview, btnDash2);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        // Book switch toggle
        HBox bookToggle = new HBox(4);
        bookToggle.getStyleClass().add("toggle-group-container");

        Button btnAll = createFilterPill("All Books", "ALL".equals(activeBook));
        Button btnCc = createFilterPill("Alpha CC", "CC".equals(activeBook));
        Button btnCs = createFilterPill("Alpha CS", "CS".equals(activeBook));

        btnAll.setOnAction(e -> { activeBook = "ALL"; refresh(); });
        btnCc.setOnAction(e -> { activeBook = "CC"; refresh(); });
        btnCs.setOnAction(e -> { activeBook = "CS"; refresh(); });

        bookToggle.getChildren().addAll(btnAll, btnCc, btnCs);

        Button newTxBtn = UiTheme.goldBtn("+ New Transaction");
        newTxBtn.setOnAction(e -> app.showTransactions());

        bar.getChildren().addAll(titleBox, dashSwitch, sp, bookToggle, newTxBtn);
        return bar;
    }

    private Button createFilterPill(String text, boolean active) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-filter-pill");
        if (active) b.getStyleClass().add("active");
        return b;
    }

    private Node createAllTimeKpis(List<Transaction> txs) {
        VBox section = new VBox(8);

        Label header = new Label("ALL-TIME ACCOUNTS OVERVIEW");
        header.getStyleClass().addAll("micro-label", "accent-gold");

        double totalSales = 0;
        double totalPayments = 0;
        int totalPieces = 0;
        int totalParcels = 0;

        for (Transaction t : txs) {
            if ("sale".equalsIgnoreCase(t.getTransactionType())) {
                totalSales += t.getAmount();
                if (t.isIncludeInReporting()) {
                    totalPieces += t.getTotalQuantity();
                }
                totalParcels += t.getParcels();
            } else if ("payment".equalsIgnoreCase(t.getTransactionType())) {
                totalPayments += t.getAmount();
            }
        }

        double outstanding = totalSales - totalPayments;

        HBox grid = new HBox(14);
        grid.getChildren().addAll(
            createBigKpiCard("Total Sale Amount", "₹ " + currencyFmt.format(totalSales),
                "All books, all time", "#8b5cf6"),
            createBigKpiCard("Total Payment Amount", "₹ " + currencyFmt.format(totalPayments),
                "Total collections received", "#10b981"),
            createBigKpiCard("Outstanding Balance", "₹ " + currencyFmt.format(outstanding),
                "Across all active buyers", outstanding > 0 ? "#ef4444" : "#10b981"),
            createBigKpiCard("Total Pieces Sold", String.format("%,d", totalPieces),
                "All time trouser pieces", "#3b82f6"),
            createBigKpiCard("Total Parcels Sent", String.format("%,d", totalParcels),
                "All time cargo shipments", "#f97316")
        );

        for (Node n : grid.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        section.getChildren().addAll(header, grid);
        return section;
    }

    private Node createCurrentPeriodKpis(List<Transaction> txs) {
        VBox section = new VBox(8);

        LocalDate now = LocalDate.now();
        String currentMonthKey = now.format(F_YM);
        String lastMonthKey = now.minusMonths(1).format(F_YM);
        String monthName = now.format(F_MON_YYYY);

        Label header = new Label("CURRENT PERIOD & LOGISTICS PERFORMANCE (" + monthName.toUpperCase() + ")");
        header.getStyleClass().addAll("micro-label", "accent-sky");

        double currSales = 0, lastSales = 0;
        double currPayments = 0, lastPayments = 0;
        int currPieces = 0, lastPieces = 0;
        int currParcels = 0, lastParcels = 0;

        for (Transaction t : txs) {
            String d = t.getTransactionDate();
            if (d == null) continue;
            boolean isSale = "sale".equalsIgnoreCase(t.getTransactionType());
            boolean isPay = "payment".equalsIgnoreCase(t.getTransactionType());

            if (d.startsWith(currentMonthKey)) {
                if (isSale) {
                    currSales += t.getAmount();
                    if (t.isIncludeInReporting()) currPieces += t.getTotalQuantity();
                    currParcels += t.getParcels();
                } else if (isPay) {
                    currPayments += t.getAmount();
                }
            } else if (d.startsWith(lastMonthKey)) {
                if (isSale) {
                    lastSales += t.getAmount();
                    if (t.isIncludeInReporting()) lastPieces += t.getTotalQuantity();
                    lastParcels += t.getParcels();
                } else if (isPay) {
                    lastPayments += t.getAmount();
                }
            }
        }

        double salesTrend = calcTrend(currSales, lastSales);
        double payTrend = calcTrend(currPayments, lastPayments);
        double piecesTrend = calcTrend(currPieces, lastPieces);
        double parcelsTrend = calcTrend(currParcels, lastParcels);

        HBox grid = new HBox(14);
        grid.getChildren().addAll(
            createTrendKpiCard("Total Sales", "₹ " + currencyFmt.format(currSales), salesTrend),
            createTrendKpiCard("Payments Received", "₹ " + currencyFmt.format(currPayments), payTrend),
            createTrendKpiCard("Pieces Sold", String.format("%,d pcs", currPieces), piecesTrend),
            createTrendKpiCard("Parcels Sent", String.format("%,d parcels", currParcels), parcelsTrend)
        );

        for (Node n : grid.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        section.getChildren().addAll(header, grid);
        return section;
    }

    private double calcTrend(double curr, double last) {
        if (last == 0) return curr > 0 ? 100.0 : 0.0;
        return ((curr - last) / Math.abs(last)) * 100.0;
    }

    private VBox createBigKpiCard(String title, String val, String sub, String accentColor) {
        VBox card = new VBox(6);
        card.getStyleClass().add("kpi-card");
        card.setStyle("-fx-border-top-color: " + accentColor + "; -fx-border-top-width: 3px;");

        Label tLbl = new Label(title.toUpperCase());
        tLbl.getStyleClass().add("kpi-subtext");
        tLbl.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold;");

        Label vLbl = new Label(val);
        vLbl.getStyleClass().add("kpi-value");

        Label sLbl = new Label(sub);
        sLbl.setStyle("-fx-text-fill: " + accentColor + "; -fx-opacity: 0.85; -fx-font-size: 11px;");

        card.getChildren().addAll(tLbl, vLbl, sLbl);
        return card;
    }

    private VBox createTrendKpiCard(String title, String val, double trendPercent) {
        VBox card = new VBox(6);
        card.getStyleClass().add("kpi-card");
        card.setPadding(new Insets(14, 16, 14, 16));

        Label tLbl = new Label(title.toUpperCase());
        tLbl.getStyleClass().add("kpi-subtext");
        tLbl.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold;");

        Label vLbl = new Label(val);
        vLbl.getStyleClass().add("kpi-value");

        boolean pos = trendPercent >= 0;
        String sign = pos ? "+" : "";
        Label trendBadge = new Label(String.format("%s%.1f%% vs last month", sign, trendPercent));
        trendBadge.getStyleClass().addAll("badge", pos ? "badge-green" : "badge-red");

        card.getChildren().addAll(tLbl, vLbl, trendBadge);
        return card;
    }

    private Node createChartsRow(List<Transaction> txs) {
        HBox row = new HBox(20);

        // Chart 1: Monthly Sales vs Payments (12 Months)
        VBox card1 = UiTheme.card(2);
        card1.setStyle("-fx-padding: 6px 14px 4px 14px;");
        HBox.setHgrow(card1, Priority.ALWAYS);

        HBox c1Header = new HBox(8);
        c1Header.setAlignment(Pos.CENTER_LEFT);
        c1Header.setStyle("-fx-padding: 0 0 2px 0;");
        Label c1Title = UiTheme.sectionTitle("Monthly Sales vs Payments");
        Label c1Sub = new Label("Last 12 months comparison");
        c1Sub.getStyleClass().add("kpi-subtext");
        c1Header.getChildren().addAll(c1Title, c1Sub);
        card1.getChildren().add(c1Header);

        CategoryAxis xAxis1 = new CategoryAxis();
        NumberAxis yAxis1 = new NumberAxis();
        LineChart<String, Number> lineChart = new LineChart<>(xAxis1, yAxis1);
        lineChart.setPrefHeight(390);
        lineChart.setMinHeight(340);
        lineChart.setAnimated(false);
        lineChart.setCreateSymbols(true);
        VBox.setVgrow(lineChart, Priority.ALWAYS);

        XYChart.Series<String, Number> salesSeries = new XYChart.Series<>();
        salesSeries.setName("Sales (₹)");
        XYChart.Series<String, Number> paymentSeries = new XYChart.Series<>();
        paymentSeries.setName("Payments (₹)");

        LocalDate now = LocalDate.now();
        for (int i = 11; i >= 0; i--) {
            LocalDate m = now.minusMonths(i);
            String mKey = m.format(F_YM);
            String mLabel = m.format(F_MON_YY);

            double mSales = 0;
            double mPayments = 0;
            for (Transaction t : txs) {
                if (t.getTransactionDate() != null && t.getTransactionDate().startsWith(mKey)) {
                    if ("sale".equalsIgnoreCase(t.getTransactionType())) mSales += t.getAmount();
                    else if ("payment".equalsIgnoreCase(t.getTransactionType())) mPayments += t.getAmount();
                }
            }
            salesSeries.getData().add(new XYChart.Data<>(mLabel, mSales));
            paymentSeries.getData().add(new XYChart.Data<>(mLabel, mPayments));
        }

        lineChart.getData().addAll(salesSeries, paymentSeries);
        for (XYChart.Data<String, Number> d : salesSeries.getData()) {
            attachLineVertex(d, "Monthly Sales", "#F2CA6B", "Billed Sales Revenue");
        }
        for (XYChart.Data<String, Number> d : paymentSeries.getData()) {
            attachLineVertex(d, "Payments Received", "#34D399", "Collections & Receipts");
        }
        card1.getChildren().add(lineChart);

        // Chart 2: Monthly Trouser Movement (Pieces Sold)
        VBox card2 = UiTheme.card(2);
        card2.setStyle("-fx-padding: 6px 14px 4px 14px;");
        HBox.setHgrow(card2, Priority.ALWAYS);

        HBox c2Header = new HBox(8);
        c2Header.setAlignment(Pos.CENTER_LEFT);
        c2Header.setStyle("-fx-padding: 0 0 2px 0;");
        Label c2Title = UiTheme.sectionTitle("Monthly Trouser Movement");
        Label c2Sub = new Label("Pieces sold per month");
        c2Sub.getStyleClass().add("kpi-subtext");
        c2Header.getChildren().addAll(c2Title, c2Sub);
        card2.getChildren().add(c2Header);

        CategoryAxis xAxis2 = new CategoryAxis();
        NumberAxis yAxis2 = new NumberAxis();
        BarChart<String, Number> barChart = new BarChart<>(xAxis2, yAxis2);
        barChart.setPrefHeight(390);
        barChart.setMinHeight(340);
        barChart.setAnimated(false);
        barChart.setLegendVisible(false);
        barChart.setCategoryGap(24);
        barChart.setBarGap(6);
        VBox.setVgrow(barChart, Priority.ALWAYS);

        XYChart.Series<String, Number> qtySeries = new XYChart.Series<>();
        qtySeries.setName("Pieces");

        for (int i = 11; i >= 0; i--) {
            LocalDate m = now.minusMonths(i);
            String mKey = m.format(F_YM);
            String mLabel = m.format(F_MON_YY);

            int mQty = 0;
            for (Transaction t : txs) {
                if (t.getTransactionDate() != null && t.getTransactionDate().startsWith(mKey)) {
                    if ("sale".equalsIgnoreCase(t.getTransactionType()) && t.isIncludeInReporting()) {
                        mQty += t.getTotalQuantity();
                    }
                }
            }
            qtySeries.getData().add(new XYChart.Data<>(mLabel, mQty));
        }

        barChart.getData().add(qtySeries);
        for (XYChart.Data<String, Number> d : qtySeries.getData()) {
            attachBarTooltip(d, "Trouser Movement", "#F2CA6B", "pcs", "Pieces Sold (Sales Volume)");
        }
        card2.getChildren().add(barChart);

        row.getChildren().addAll(card1, card2);
        return row;
    }

    private Node createParcelAnalysisCard(List<Transaction> txs) {
        VBox card = UiTheme.card(4);
        card.setStyle("-fx-padding: 8px 14px 6px 14px;");

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setStyle("-fx-padding: 0 0 2px 0;");

        VBox titleBox = new VBox(2);
        Label title = UiTheme.sectionTitle("Parcel Counts Analysis");
        Label sub = new Label("Dispatch velocity & parcel shipments");
        sub.getStyleClass().add("kpi-subtext");
        titleBox.getChildren().addAll(title, sub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox periodToggle = new HBox(4);
        periodToggle.getStyleClass().add("toggle-group-container");

        List<Button> periodPills = new ArrayList<>();
        String[] options = {"Day", "Week", "Month", "Year"};
        for (String opt : options) {
            Button b = createFilterPill(opt, opt.equals(parcelPeriod));
            b.setOnAction(e -> {
                if (!opt.equals(parcelPeriod)) {
                    parcelPeriod = opt;
                    // Section-scoped swap: only this card's chart body changes —
                    // the page, scroll position and every other section stay put.
                    for (Button p : periodPills) p.getStyleClass().remove("active");
                    b.getStyleClass().add("active");
                    swapParcelBody(card, txs);
                }
            });
            periodPills.add(b);
            periodToggle.getChildren().add(b);
        }

        top.getChildren().addAll(titleBox, sp, periodToggle);
        card.getChildren().add(top);

        card.getChildren().add(createParcelBody(txs));
        return card;
    }

    /** Replaces only the parcel card's chart body, in place (skill 5.2). */
    private void swapParcelBody(VBox card, List<Transaction> txs) {
        parcelSwapCount++;
        Node body = createParcelBody(txs);
        if (card.getChildren().size() > 1) {
            card.getChildren().set(1, body);
        } else {
            card.getChildren().add(body);
        }
    }

    /** Chart body of the parcel card (callout + bar chart for parcelPeriod). */
    private HBox createParcelBody(List<Transaction> txs) {
        // Sub layout: Left summary callout + Right bar chart
        HBox chartBox = new HBox(16);
        chartBox.setAlignment(Pos.CENTER_LEFT);

        int totalParcels = txs.stream().mapToInt(Transaction::getParcels).sum();

        VBox callout = new VBox(6);
        callout.setPrefWidth(220);
        callout.setPrefHeight(330);
        callout.setMinHeight(300);
        callout.setPadding(new Insets(16, 20, 16, 20));
        callout.setAlignment(Pos.CENTER);
        callout.getStyleClass().add("card-kpi-mini");
        callout.setStyle("-fx-border-left-color: #F2CA6B; -fx-border-left-width: 4px;");

        Label calloutNum = new Label(String.format("%,d", totalParcels));
        calloutNum.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #F2CA6B; -fx-font-family: 'Consolas', monospace;");

        Label calloutTitle = new Label("Total Parcels");
        calloutTitle.setStyle("-fx-font-weight: bold;");

        Label calloutSub = new Label("Aggregated across all matching transactions.");
        calloutSub.getStyleClass().add("kpi-subtext");
        calloutSub.setWrapText(true);
        calloutSub.setAlignment(Pos.CENTER);

        callout.getChildren().addAll(calloutNum, calloutTitle, calloutSub);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> parcelChart = new BarChart<>(xAxis, yAxis);
        parcelChart.setPrefHeight(330);
        parcelChart.setMinHeight(300);
        parcelChart.setLegendVisible(false);
        parcelChart.setAnimated(false);
        parcelChart.setCategoryGap(20);
        parcelChart.setBarGap(6);
        HBox.setHgrow(parcelChart, Priority.ALWAYS);
        VBox.setVgrow(parcelChart, Priority.ALWAYS);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Parcels");

        LocalDate now = LocalDate.now();
        if ("Month".equals(parcelPeriod)) {
            for (int i = 11; i >= 0; i--) {
                LocalDate m = now.minusMonths(i);
                String mKey = m.format(F_YM);
                String label = m.format(F_MON_YY);
                int pCount = txs.stream()
                    .filter(t -> t.getTransactionDate() != null && t.getTransactionDate().startsWith(mKey))
                    .mapToInt(Transaction::getParcels).sum();
                series.getData().add(new XYChart.Data<>(label, pCount));
            }
        } else if ("Day".equals(parcelPeriod)) {
            for (int i = 13; i >= 0; i--) {
                LocalDate d = now.minusDays(i);
                String dKey = d.toString();
                String label = d.format(F_DD_MMM);
                int pCount = txs.stream()
                    .filter(t -> dKey.equals(t.getTransactionDate()))
                    .mapToInt(Transaction::getParcels).sum();
                series.getData().add(new XYChart.Data<>(label, pCount));
            }
        } else if ("Year".equals(parcelPeriod)) {
            for (int i = 4; i >= 0; i--) {
                int yr = now.getYear() - i;
                String yKey = String.valueOf(yr);
                int pCount = txs.stream()
                    .filter(t -> t.getTransactionDate() != null && t.getTransactionDate().startsWith(yKey))
                    .mapToInt(Transaction::getParcels).sum();
                series.getData().add(new XYChart.Data<>(yKey, pCount));
            }
        } else { // Week
            for (int i = 7; i >= 0; i--) {
                LocalDate wStart = now.minusWeeks(i);
                String label = "Wk " + wStart.format(F_DD_MM);
                series.getData().add(new XYChart.Data<>(label, (int)(totalParcels / 8)));
            }
        }

        parcelChart.getData().add(series);
        for (XYChart.Data<String, Number> d : series.getData()) {
            attachBarTooltip(d, "Parcel Dispatch", "#F2CA6B", "parcels", "Total Cargo Shipments");
        }
        chartBox.getChildren().addAll(callout, parcelChart);
        return chartBox;
    }

    private void attachLineVertex(XYChart.Data<String, Number> data, String seriesName, String colorHex, String detail) {
        Consumer<Node> setup = node -> {
            if (node == null) return;
            node.setCursor(Cursor.HAND);
            node.setStyle(
                "-fx-background-color: #070B12, " + colorHex + "; " +
                "-fx-background-insets: 0, 1.5; " +
                "-fx-background-radius: 50%; " +
                "-fx-pref-width: 9px; -fx-pref-height: 9px; " +
                "-fx-cursor: hand;"
            );

            node.setOnMouseEntered(e -> {
                node.setScaleX(1.45);
                node.setScaleY(1.45);
                node.setStyle(
                    "-fx-background-color: " + colorHex + ", #FFFFFF; " +
                    "-fx-background-insets: 0, 2; " +
                    "-fx-background-radius: 50%; " +
                    "-fx-pref-width: 9px; -fx-pref-height: 9px; " +
                    "-fx-effect: dropshadow(gaussian, " + colorHex + ", 8, 0.6, 0, 0); " +
                    "-fx-cursor: hand;"
                );
            });
            node.setOnMouseExited(e -> {
                node.setScaleX(1.0);
                node.setScaleY(1.0);
                node.setStyle(
                    "-fx-background-color: #070B12, " + colorHex + "; " +
                    "-fx-background-insets: 0, 1.5; " +
                    "-fx-background-radius: 50%; " +
                    "-fx-pref-width: 9px; -fx-pref-height: 9px; " +
                    "-fx-cursor: hand;"
                );
            });

            double val = data.getYValue() != null ? data.getYValue().doubleValue() : 0.0;
            String valStr = "₹ " + currencyFmt.format(val);
            installCustomTooltip(node, data.getXValue(), seriesName, valStr, colorHex, detail);
        };

        if (data.getNode() != null) {
            setup.accept(data.getNode());
        }
        data.nodeProperty().addListener((obs, oldN, newN) -> {
            if (newN != null) {
                setup.accept(newN);
            }
        });
    }

    private void attachBarTooltip(XYChart.Data<String, Number> data, String metricTitle, String colorHex, String unit, String detail) {
        Consumer<Node> setup = node -> {
            if (node == null) return;
            node.setCursor(Cursor.HAND);

            node.setOnMouseEntered(e -> {
                node.setStyle("-fx-opacity: 0.85; -fx-effect: dropshadow(gaussian, " + colorHex + ", 10, 0.45, 0, 0);");
            });
            node.setOnMouseExited(e -> {
                node.setStyle(null);
            });

            int val = data.getYValue() != null ? data.getYValue().intValue() : 0;
            String valStr = String.format(Locale.US, "%,d %s", val, unit);
            installCustomTooltip(node, data.getXValue(), metricTitle, valStr, colorHex, detail);
        };

        if (data.getNode() != null) {
            setup.accept(data.getNode());
        }
        data.nodeProperty().addListener((obs, oldN, newN) -> {
            if (newN != null) {
                setup.accept(newN);
            }
        });
    }

    private void installCustomTooltip(Node node, String badgeText, String titleText, String valueText, String accentColorHex, String detailText) {
        Tooltip tooltip = new Tooltip();
        tooltip.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_BOTTOM_LEFT);
        tooltip.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-padding: 0; " +
            "-fx-background-insets: 0; " +
            "-fx-effect: null;"
        );

        VBox box = new VBox(5);
        box.setMouseTransparent(true);
        box.setStyle(
            "-fx-background-color: #070B12; " +
            "-fx-border-color: " + accentColorHex + "; " +
            "-fx-border-width: 1.2px; " +
            "-fx-border-radius: 8px; " +
            "-fx-background-radius: 8px; " +
            "-fx-padding: 8px 12px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.75), 4, 0.2, 0, 1);"
        );
        box.setMinWidth(160);

        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label badge = new Label(badgeText);
        badge.setStyle(
            "-fx-background-color: rgba(255, 255, 255, 0.08); " +
            "-fx-text-fill: #94A3B8; " +
            "-fx-font-size: 10.5px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 2px 6px; " +
            "-fx-background-radius: 4px;"
        );

        Label titleLbl = new Label(titleText);
        titleLbl.setStyle(
            "-fx-text-fill: #E2E8F0; " +
            "-fx-font-size: 11.5px; " +
            "-fx-font-weight: 600;"
        );

        headerRow.getChildren().addAll(badge, titleLbl);

        Label valLbl = new Label(valueText);
        valLbl.setStyle(
            "-fx-text-fill: " + accentColorHex + "; " +
            "-fx-font-size: 18px; " +
            "-fx-font-weight: bold; " +
            "-fx-font-family: 'Segoe UI', system-ui, sans-serif;"
        );

        Label detailLbl = new Label(detailText);
        detailLbl.setStyle(
            "-fx-text-fill: #64748B; " +
            "-fx-font-size: 10.5px;"
        );

        box.getChildren().addAll(headerRow, valLbl, detailLbl);
        tooltip.setGraphic(box);

        node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            if (node.getScene() != null && node.getScene().getWindow() != null) {
                tooltip.show(node, e.getScreenX(), e.getScreenY() - 7);
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            if (tooltip.isShowing()) {
                tooltip.setAnchorX(e.getScreenX());
                tooltip.setAnchorY(e.getScreenY() - 7);
            } else if (node.getScene() != null && node.getScene().getWindow() != null) {
                tooltip.show(node, e.getScreenX(), e.getScreenY() - 7);
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> tooltip.hide());
    }

    private Node createTop5WidgetsRow(List<Transaction> txs) {
        HBox row = new HBox(20);

        // 1. Top 5 Debtors (highest outstanding)
        Map<String, double[]> buyerTotals = new HashMap<>(); // [sales, payments, pieces]
        Map<String, String> buyerNames = new HashMap<>();

        for (Transaction t : txs) {
            String bId = t.getBuyerId() != null ? t.getBuyerId() : "unknown";
            String bName = t.getBuyerName() != null ? t.getBuyerName() : "Unknown Buyer";
            buyerNames.put(bId, bName);

            double[] arr = buyerTotals.computeIfAbsent(bId, k -> new double[3]);
            if ("sale".equalsIgnoreCase(t.getTransactionType())) {
                arr[0] += t.getAmount();
                if (t.isIncludeInReporting()) arr[2] += t.getTotalQuantity();
            } else if ("payment".equalsIgnoreCase(t.getTransactionType())) {
                arr[1] += t.getAmount();
            }
        }

        // Debtors
        List<Map.Entry<String, double[]>> debtors = buyerTotals.entrySet().stream()
            .filter(e -> (e.getValue()[0] - e.getValue()[1]) > 0.01)
            .sorted((a, b) -> Double.compare(b.getValue()[0] - b.getValue()[1], a.getValue()[0] - a.getValue()[1]))
            .limit(5)
            .collect(Collectors.toList());

        // Paymasters
        List<Map.Entry<String, double[]>> paymasters = buyerTotals.entrySet().stream()
            .filter(e -> e.getValue()[1] > 0.01)
            .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
            .limit(5)
            .collect(Collectors.toList());

        // Volume Leaders
        List<Map.Entry<String, double[]>> volumeLeaders = buyerTotals.entrySet().stream()
            .filter(e -> e.getValue()[2] > 0)
            .sorted((a, b) -> Double.compare(b.getValue()[2], a.getValue()[2]))
            .limit(5)
            .collect(Collectors.toList());

        VBox dCard = createTop5ListCard("Top 5 Debtors", "Highest unpaid balances", "#ef4444", debtors, buyerNames, 0);
        VBox pCard = createTop5ListCard("Top 5 Paymasters", "Largest collection sources", "#10b981", paymasters, buyerNames, 1);
        VBox vCard = createTop5ListCard("Volume Leaders", "Most pieces purchased", "#3b82f6", volumeLeaders, buyerNames, 2);

        HBox.setHgrow(dCard, Priority.ALWAYS);
        HBox.setHgrow(pCard, Priority.ALWAYS);
        HBox.setHgrow(vCard, Priority.ALWAYS);

        row.getChildren().addAll(dCard, pCard, vCard);
        return row;
    }

    private VBox createTop5ListCard(String title, String sub, String badgeColor,
                                   List<Map.Entry<String, double[]>> entries,
                                   Map<String, String> nameMap, int mode) {
        VBox card = UiTheme.card(10);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Region dot = new Region();
        dot.setPrefSize(10, 10);
        dot.setStyle("-fx-background-color: " + badgeColor + "; -fx-background-radius: 5px;");

        VBox titleBox = new VBox(1);
        Label titleLbl = UiTheme.sectionTitle(title);
        Label subLbl = new Label(sub);
        subLbl.getStyleClass().add("kpi-subtext");
        titleBox.getChildren().addAll(titleLbl, subLbl);

        header.getChildren().addAll(dot, titleBox);
        card.getChildren().add(header);

        if (entries.isEmpty()) {
            card.getChildren().add(UiTheme.emptyState("👥", "No buyer data available yet.", null));
            return card;
        }

        VBox list = new VBox(6);
        int rank = 1;
        for (Map.Entry<String, double[]> e : entries) {
            HBox itemRow = new HBox(8);
            itemRow.setAlignment(Pos.CENTER_LEFT);
            itemRow.setPadding(new Insets(6, 8, 6, 8));
            itemRow.getStyleClass().add("clickable-row");

            Label rankLbl = new Label("#" + rank++);
            rankLbl.getStyleClass().add("code-pill");

            String bName = nameMap.getOrDefault(e.getKey(), "Unknown");
            Label nameLbl = new Label(bName);
            nameLbl.getStyleClass().add("table-cell-title");
            HBox.setHgrow(nameLbl, Priority.ALWAYS);

            String valText;
            if (mode == 0) {
                double out = e.getValue()[0] - e.getValue()[1];
                valText = "₹ " + currencyFmt.format(out);
            } else if (mode == 1) {
                valText = "₹ " + currencyFmt.format(e.getValue()[1]);
            } else {
                valText = String.format("%,d pcs", (int) e.getValue()[2]);
            }

            Label valLbl = new Label(valText);
            valLbl.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-font-size: 12px;");

            itemRow.getChildren().addAll(rankLbl, nameLbl, valLbl);

            // Clicking opens Buyer Statement in Reports
            itemRow.setOnMouseClicked(ev -> app.showReports());

            list.getChildren().add(itemRow);
        }

        card.getChildren().add(list);
        return card;
    }

    private Node createRecentActivityCard(List<Transaction> txs, List<Bill> bills) {
        VBox card = UiTheme.card(12);

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);

        HBox switchBox = new HBox(4);
        switchBox.getStyleClass().add("toggle-group-container");

        Button txToggle = new Button("Recent Transactions");
        txToggle.getStyleClass().add("btn-filter-pill");
        if ("Transactions".equals(recentType)) txToggle.getStyleClass().add("active");

        Button billToggle = new Button("Recent Bills");
        billToggle.getStyleClass().add("btn-filter-pill");
        if ("Bills".equals(recentType)) billToggle.getStyleClass().add("active");

        // Toggle swaps ONLY the table inside this card — the rest of the
        // dashboard (KPIs, charts, scroll position) is never touched.
        txToggle.setOnAction(e -> {
            if (!"Transactions".equals(recentType)) {
                recentType = "Transactions";
                swapRecentTable(card, txs, bills);
                txToggle.getStyleClass().add("active");
                billToggle.getStyleClass().remove("active");
            }
        });
        billToggle.setOnAction(e -> {
            if (!"Bills".equals(recentType)) {
                recentType = "Bills";
                swapRecentTable(card, txs, bills);
                billToggle.getStyleClass().add("active");
                txToggle.getStyleClass().remove("active");
            }
        });
        switchBox.getChildren().addAll(txToggle, billToggle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button viewAllBtn = UiTheme.secondaryBtn("View All →");
        viewAllBtn.setOnAction(e -> {
            if ("Transactions".equals(recentType)) app.showTransactions();
            else app.showHistory();
        });

        top.getChildren().addAll(switchBox, sp, viewAllBtn);
        card.getChildren().add(top);

        if ("Transactions".equals(recentType)) {
            TableView<Transaction> table = new TableView<>();
            table.setMinHeight(260);
            table.setPrefHeight(260);
            table.getStyleClass().add("data-table");
            table.setPlaceholder(new Label("No recent transactions found."));
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

            TableColumn<Transaction, String> cDate = new TableColumn<>("Date");
            cDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTransactionDate()));
            cDate.setPrefWidth(95);

            TableColumn<Transaction, String> cBuyer = new TableColumn<>("Buyer");
            cBuyer.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBuyerName()));
            cBuyer.setPrefWidth(180);

            TableColumn<Transaction, String> cBook = new TableColumn<>("Book");
            cBook.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBookType()));
            cBook.setPrefWidth(70);

            TableColumn<Transaction, String> cType = new TableColumn<>("Type");
            cType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTransactionType()));
            cType.setPrefWidth(85);

            TableColumn<Transaction, Number> cQty = new TableColumn<>("Qty");
            cQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getTotalQuantity()));
            cQty.setStyle("-fx-alignment: CENTER-RIGHT;");
            cQty.setPrefWidth(70);

            TableColumn<Transaction, Number> cAmt = new TableColumn<>("Amount");
            cAmt.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getAmount()));
            cAmt.setStyle("-fx-alignment: CENTER-RIGHT;");
            cAmt.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(Number val, boolean empty) {
                    super.updateItem(val, empty);
                    if (empty || val == null) {
                        setText(null);
                        setStyle(null);
                    } else {
                        Transaction t = getTableRow().getItem();
                        boolean isSale = t == null || "sale".equalsIgnoreCase(t.getTransactionType());
                        setText((isSale ? "+ ₹ " : "- ₹ ") + currencyFmt.format(val.doubleValue()));
                        setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-alignment: CENTER-RIGHT; " +
                            "-fx-text-fill: " + (isSale ? "#dc2626" : "#16a34a") + ";");
                    }
                }
            });

            table.getColumns().addAll(cDate, cBuyer, cBook, cType, cQty, cAmt);
            List<Transaction> sortedTxs = txs.stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(15)
                .collect(Collectors.toList());
            table.setItems(FXCollections.observableArrayList(sortedTxs));
            card.getChildren().add(table);
        } else {
            TableView<Bill> table = new TableView<>();
            table.setMinHeight(260);
            table.setPrefHeight(260);
            table.getStyleClass().add("data-table");
            table.setPlaceholder(new Label("No recent bills found."));
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

            TableColumn<Bill, String> cNo = new TableColumn<>("Bill #");
            cNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBillNo()));
            cNo.setPrefWidth(110);

            TableColumn<Bill, String> cDate = new TableColumn<>("Date");
            cDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));
            cDate.setPrefWidth(95);

            TableColumn<Bill, String> cBuyer = new TableColumn<>("Buyer");
            cBuyer.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getVariables() != null ? d.getValue().getVariables().getOrDefault("buyer_name", "—") : "—"));
            cBuyer.setPrefWidth(180);

            TableColumn<Bill, String> cAmt = new TableColumn<>("Invoice Total");
            cAmt.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getTotals() != null ? "₹ " + currencyFmt.format(d.getValue().getTotals().getGrandTotal()) : "₹ 0.00"));
            cAmt.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;");

            table.getColumns().addAll(cNo, cDate, cBuyer, cAmt);
            List<Bill> sortedBills = bills.stream()
                .sorted(Comparator.comparing(Bill::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(15)
                .collect(Collectors.toList());
            table.setItems(FXCollections.observableArrayList(sortedBills));
            card.getChildren().add(table);
        }

        return card;
    }

    /**
     * Replaces the table body of the recent-activity card in place — a
     * section-scoped swap, not a page rebuild, so the scroll position and
     * every other section stay exactly where they are (skill 5.2: local
     * invalidation beats whole-scene rebuilds).
     */
    private void swapRecentTable(VBox card, List<Transaction> txs, List<Bill> bills) {
        // The card holds [topBar, table] — replace only index 1.
        recentSwapCount++;
        Node body = createRecentBody(txs, bills);
        if (card.getChildren().size() > 1) {
            card.getChildren().set(1, body);
        } else {
            card.getChildren().add(body);
        }
    }

    /** The table body of the recent-activity card (Transactions or Bills). */
    private Node createRecentBody(List<Transaction> txs, List<Bill> bills) {
        if ("Transactions".equals(recentType)) {
            TableView<Transaction> table = new TableView<>();
            table.setMinHeight(260);
            table.setPrefHeight(260);
            table.getStyleClass().add("data-table");
            table.setPlaceholder(new Label("No recent transactions found."));
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

            TableColumn<Transaction, String> cDate = new TableColumn<>("Date");
            cDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTransactionDate()));
            cDate.setPrefWidth(95);

            TableColumn<Transaction, String> cBuyer = new TableColumn<>("Buyer");
            cBuyer.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBuyerName()));
            cBuyer.setPrefWidth(180);

            TableColumn<Transaction, String> cBook = new TableColumn<>("Book");
            cBook.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBookType()));
            cBook.setPrefWidth(70);

            TableColumn<Transaction, String> cType = new TableColumn<>("Type");
            cType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTransactionType()));
            cType.setPrefWidth(85);

            TableColumn<Transaction, Number> cQty = new TableColumn<>("Qty");
            cQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getTotalQuantity()));
            cQty.setStyle("-fx-alignment: CENTER-RIGHT;");
            cQty.setPrefWidth(70);

            TableColumn<Transaction, Number> cAmt = new TableColumn<>("Amount");
            cAmt.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getAmount()));
            cAmt.setStyle("-fx-alignment: CENTER-RIGHT;");
            cAmt.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(Number val, boolean empty) {
                    super.updateItem(val, empty);
                    if (empty || val == null) {
                        setText(null);
                        setStyle(null);
                    } else {
                        Transaction t = getTableRow().getItem();
                        boolean isSale = t == null || "sale".equalsIgnoreCase(t.getTransactionType());
                        setText((isSale ? "+ ₹ " : "- ₹ ") + currencyFmt.format(val.doubleValue()));
                        setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-alignment: CENTER-RIGHT; " +
                            "-fx-text-fill: " + (isSale ? "#dc2626" : "#16a34a") + ";");
                    }
                }
            });

            table.getColumns().addAll(cDate, cBuyer, cBook, cType, cQty, cAmt);
            List<Transaction> sortedTxs = txs.stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(15)
                .collect(Collectors.toList());
            table.setItems(FXCollections.observableArrayList(sortedTxs));
            return table;
        }

        TableView<Bill> table = new TableView<>();
        table.setMinHeight(260);
        table.setPrefHeight(260);
        table.getStyleClass().add("data-table");
        table.setPlaceholder(new Label("No recent bills found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Bill, String> cNo = new TableColumn<>("Bill #");
        cNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBillNo()));
        cNo.setPrefWidth(110);

        TableColumn<Bill, String> cDate = new TableColumn<>("Date");
        cDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));
        cDate.setPrefWidth(95);

        TableColumn<Bill, String> cBuyer = new TableColumn<>("Buyer");
        cBuyer.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getVariables() != null ? d.getValue().getVariables().getOrDefault("buyer_name", "—") : "—"));
        cBuyer.setPrefWidth(180);

        TableColumn<Bill, String> cAmt = new TableColumn<>("Invoice Total");
        cAmt.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getTotals() != null ? "₹ " + currencyFmt.format(d.getValue().getTotals().getGrandTotal()) : "₹ 0.00"));
        cAmt.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;");

        table.getColumns().addAll(cNo, cDate, cBuyer, cAmt);
        List<Bill> sortedBills = bills.stream()
            .sorted(Comparator.comparing(Bill::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(15)
            .collect(Collectors.toList());
        table.setItems(FXCollections.observableArrayList(sortedBills));
        return table;
    }
}
