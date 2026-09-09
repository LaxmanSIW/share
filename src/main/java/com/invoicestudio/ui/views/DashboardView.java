package com.invoicestudio.ui.views;

import com.invoicestudio.db.BillDao;
import com.invoicestudio.db.SettingsDao;
import com.invoicestudio.model.*;
import com.invoicestudio.service.BillingService;
import com.invoicestudio.service.CsvService;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class DashboardView extends BorderPane {

    private final StudioApp app;
    private final BillDao billDao;
    private final SettingsDao settingsDao;

    private LocalDate selectedMonth = LocalDate.now().withDayOfMonth(1);
    private final Label monthLabel = new Label();
    private final VBox contentBox = new VBox(20);

    public DashboardView(StudioApp app) {
        this.app = app;
        this.billDao = new BillDao(app.getDb());
        this.settingsDao = new SettingsDao(app.getDb());

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
        contentBox.getChildren().clear();

        Settings settings = settingsDao.getSettings();
        List<Bill> allBills = billDao.getAllBills();

        // 1. Month Bar & Actions
        contentBox.getChildren().add(createTopBar());

        // 2. Recurring Invoices Due Banner (if any)
        List<Bill> dueRecurring = allBills.stream()
                .filter(b -> b.getRepeat() != null && b.getRepeat() != RepeatCadence.NONE && b.getDocType() == DocType.INVOICE && BillingService.isRepeatDue(b))
                .collect(Collectors.toList());
        if (!dueRecurring.isEmpty()) {
            contentBox.getChildren().add(createRecurringBanner(dueRecurring, settings));
        }

        // 3. 4 KPI Metric Cards
        contentBox.getChildren().add(createKpiCards(allBills, settings));

        // 4. Middle row: 6-Month Chart + GST Breakdown Card
        HBox midRow = new HBox(20);
        midRow.getChildren().addAll(createChartCard(allBills, settings), createGstCard(allBills, settings));
        HBox.setHgrow(midRow.getChildren().get(0), Priority.ALWAYS);
        contentBox.getChildren().add(midRow);

        // 5. Bottom row: Top Buyers + Recent Bills
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

    private Node createTopBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Financial Dashboard");
        title.getStyleClass().add("heading-l");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Month controls
        HBox monthCtrl = new HBox(8);
        monthCtrl.setAlignment(Pos.CENTER);
        monthCtrl.setStyle("-fx-background-color: #151B25; -fx-padding: 4 10; -fx-background-radius: 6; -fx-border-color: #232B38; -fx-border-radius: 6;");

        Button prevBtn = new Button("<");
        prevBtn.getStyleClass().addAll("button-sm", "button-secondary");
        prevBtn.setTooltip(new Tooltip("Previous Month"));
        prevBtn.setOnAction(e -> {
            selectedMonth = selectedMonth.minusMonths(1);
            refresh();
        });

        monthLabel.setText(selectedMonth.format(DateTimeFormatter.ofPattern("MMM yyyy")));
        monthLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #CBD5E1; -fx-min-width: 80; -fx-alignment: CENTER;");

        Button nextBtn = new Button(">");
        nextBtn.getStyleClass().addAll("button-sm", "button-secondary");
        nextBtn.setTooltip(new Tooltip("Next Month"));
        nextBtn.setOnAction(e -> {
            selectedMonth = selectedMonth.plusMonths(1);
            refresh();
        });

        Button todayBtn = new Button("Current Month");
        todayBtn.getStyleClass().addAll("button-sm", "button-secondary");
        todayBtn.setTooltip(new Tooltip("Jump to current calendar month"));
        todayBtn.setOnAction(e -> {
            selectedMonth = LocalDate.now().withDayOfMonth(1);
            refresh();
        });

        monthCtrl.getChildren().addAll(prevBtn, monthLabel, nextBtn, todayBtn);

        Button newBillBtn = new Button("+ Create Bill");
        newBillBtn.getStyleClass().add("gold-btn");
        newBillBtn.setTooltip(new Tooltip("Create New Invoice or Bill"));
        newBillBtn.setOnAction(e -> app.showCreateBill(null, null));

        bar.getChildren().addAll(title, spacer, monthCtrl, newBillBtn);
        return bar;
    }

    private Node createRecurringBanner(List<Bill> dueRecurring, Settings settings) {
        HBox banner = new HBox(16);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setStyle("-fx-background-color: rgba(217, 161, 59, 0.15); -fx-border-color: #D9A13B; -fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 12 18;");

        Label icon = (Label) IconHelper.getIcon("history", 18, "#D9A13B");
        VBox text = new VBox(2);
        Label title = new Label(dueRecurring.size() + " Recurring Invoice" + (dueRecurring.size() == 1 ? "" : "s") + " Due");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #E5B055;");
        Label sub = new Label("Invoices scheduled to roll forward are ready for generation.");
        sub.setStyle("-fx-font-size: 11px; -fx-text-fill: #CBD5E1;");
        text.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button autoBtn = new Button(settings.isAutoRecurring() ? "Auto-Create: ON" : "Turn ON Auto-Create");
        autoBtn.getStyleClass().addAll("button-sm", "button-secondary");
        autoBtn.setTooltip(new Tooltip("Toggle automatic creation of recurring invoices"));
        autoBtn.setOnAction(e -> {
            settings.setAutoRecurring(!settings.isAutoRecurring());
            settingsDao.saveSettings(settings);
            refresh();
            Toast.show(app.getRootPane(), "Settings Updated", "Auto recurring generation is now " + (settings.isAutoRecurring() ? "enabled" : "disabled"), false);
        });

        Button runSweepBtn = new Button("Create Due Now");
        runSweepBtn.getStyleClass().addAll("button-sm", "gold-btn");
        runSweepBtn.setTooltip(new Tooltip("Instantly generate all overdue recurring invoices"));
        runSweepBtn.setOnAction(e -> {
            var res = app.getRecurringEngine().runSweep(true);
            refresh();
            Toast.show(app.getRootPane(), "Sweep Completed", "Generated " + res.created.size() + " new invoices.", false);
        });

        banner.getChildren().addAll(icon, text, spacer, autoBtn, runSweepBtn);
        return banner;
    }

    private Node createKpiCards(List<Bill> bills, Settings settings) {
        String monthKey = selectedMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String cur = settings.getCurrency();

        List<Bill> invoices = bills.stream()
                .filter(b -> b.getDocType() == DocType.INVOICE && b.getStatus() != BillStatus.CANCELLED)
                .collect(Collectors.toList());

        // This Month Revenue
        double monthRevenue = invoices.stream()
                .filter(b -> b.getDate() != null && b.getDate().startsWith(monthKey))
                .mapToDouble(b -> b.getTotals().getGrandTotal()).sum();

        // Previous Month for MoM Delta
        String prevMonthKey = selectedMonth.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        double prevRevenue = invoices.stream()
                .filter(b -> b.getDate() != null && b.getDate().startsWith(prevMonthKey))
                .mapToDouble(b -> b.getTotals().getGrandTotal()).sum();
        double momDelta = prevRevenue > 0 ? ((monthRevenue - prevRevenue) / prevRevenue) * 100 : (monthRevenue > 0 ? 100 : 0);

        // Total Collections
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

        // Outstanding Dues
        List<Bill> unpaidList = invoices.stream().filter(b -> {
            double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (paid == 0 && b.getStatus() == BillStatus.PAID) return false;
            return b.getTotals().getGrandTotal() - paid > 0.01;
        }).collect(Collectors.toList());
        double totalDue = unpaidList.stream().mapToDouble(b -> {
            double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            return Math.max(0, b.getTotals().getGrandTotal() - paid);
        }).sum();

        // Lifetime Sales & Average Ticket
        double totalSales = invoices.stream().mapToDouble(b -> b.getTotals().getGrandTotal()).sum();
        double avgTicket = invoices.isEmpty() ? 0 : totalSales / invoices.size();

        HBox grid = new HBox(16);
        grid.getChildren().addAll(
                buildCard("This Month Revenue", String.format("%s%.2f", cur, monthRevenue),
                        String.format("%s%.1f%% vs last month", momDelta >= 0 ? "+" : "", momDelta), momDelta >= 0 ? "#10B981" : "#EF4444"),
                buildCard("Total Collected", String.format("%s%.2f", cur, totalCollected),
                        String.format("%s%.2f collected this month", cur, monthCollected), "#D9A13B"),
                buildCard("Outstanding Due", String.format("%s%.2f", cur, totalDue),
                        unpaidList.size() + " pending invoice" + (unpaidList.size() == 1 ? "" : "s"), "#F59E0B"),
                buildCard("Total Lifetime Sales", String.format("%s%.2f", cur, totalSales),
                        String.format("%s%.2f avg ticket size", cur, avgTicket), "#38BDF8")
        );

        for (Node c : grid.getChildren()) HBox.setHgrow(c, Priority.ALWAYS);
        return grid;
    }

    private VBox buildCard(String title, String mainVal, String subVal, String accentColor) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        card.setStyle("-fx-background-color: #151B25; -fx-padding: 16; -fx-background-radius: 8; -fx-border-color: #232B38; -fx-border-radius: 8;");

        Label t = new Label(title);
        t.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");

        Label m = new Label(mainVal);
        m.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #F4F4F5;");

        Label s = new Label(subVal);
        s.setStyle("-fx-font-size: 11px; -fx-text-fill: " + accentColor + ";");

        card.getChildren().addAll(t, m, s);
        return card;
    }

    private Node createChartCard(List<Bill> bills, Settings settings) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setStyle("-fx-background-color: #151B25; -fx-padding: 18; -fx-background-radius: 8; -fx-border-color: #232B38; -fx-border-radius: 8;");

        Label title = new Label("6-Month Revenue Trend");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #CBD5E1;");
        card.getChildren().add(title);

        // Build 6 months series
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
        chart.setStyle("-fx-padding: 10 0 0 0;");

        for (int i = 0; i < months.size(); i++) {
            VBox col = new VBox(6);
            col.setAlignment(Pos.BOTTOM_CENTER);
            HBox.setHgrow(col, Priority.ALWAYS);

            double val = values.get(i);
            double barH = Math.max(4.0, (val / maxVal) * 105.0);

            Label valLbl = new Label(val > 0 ? String.format("%.0f", val) : "");
            valLbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #94A3B8;");

            Rectangle bar = new Rectangle(28, barH);
            boolean isCurrent = i == 5;
            bar.setFill(Color.web(isCurrent ? "#D9A13B" : "#2A374A"));
            bar.setArcWidth(4);
            bar.setArcHeight(4);

            Label mLbl = new Label(months.get(i));
            mLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (isCurrent ? "#E5B055" : "#64748B") + ";");

            col.getChildren().addAll(valLbl, bar, mLbl);
            chart.getChildren().add(col);
        }

        card.getChildren().add(chart);
        return card;
    }

    private Node createGstCard(List<Bill> bills, Settings settings) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setStyle("-fx-background-color: #151B25; -fx-padding: 18; -fx-background-radius: 8; -fx-border-color: #232B38; -fx-border-radius: 8; -fx-min-width: 320;");

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("GST Summary (Live)");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #CBD5E1;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button exportGstBtn = new Button("Export CSV");
        exportGstBtn.getStyleClass().addAll("button-sm", "button-secondary");
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
                createStatRow("Total Taxable Sales", String.format("%s%.2f", cur, totalTaxable)),
                createStatRow("CGST (Central)", String.format("%s%.2f", cur, totalCgst)),
                createStatRow("SGST (State)", String.format("%s%.2f", cur, totalSgst)),
                createStatRow("IGST (Interstate)", String.format("%s%.2f", cur, totalIgst)),
                createStatRow("Total Tax Liability", String.format("%s%.2f", cur, totalTax)),
                createStatRow("Lifetime Discounts", String.format("%s%.2f", cur, totalDiscount))
        );
        card.getChildren().add(stats);
        return card;
    }

    private Node createStatRow(String label, String value) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        Region s = new Region();
        HBox.setHgrow(s, Priority.ALWAYS);
        Label v = new Label(value);
        v.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #F4F4F5;");
        row.getChildren().addAll(l, s, v);
        return row;
    }

    private Node createTopBuyersCard(List<Bill> bills, Settings settings) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setStyle("-fx-background-color: #151B25; -fx-padding: 18; -fx-background-radius: 8; -fx-border-color: #232B38; -fx-border-radius: 8; -fx-min-width: 330; -fx-max-width: 350;");
        VBox.setVgrow(card, Priority.ALWAYS);
        card.setMaxHeight(Double.MAX_VALUE);

        Label title = new Label("Top 5 Buyers (By Revenue)");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #CBD5E1;");
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
            Label empty = new Label("No buyer data available yet.");
            empty.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
            VBox.setVgrow(empty, Priority.ALWAYS);
            card.getChildren().add(empty);
        } else {
            VBox rows = new VBox(10);
            VBox.setVgrow(rows, Priority.ALWAYS);
            for (Map.Entry<String, double[]> e : list) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                VBox text = new VBox(2);
                Label nameLbl = new Label(e.getKey());
                nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #F4F4F5;");
                Label invLbl = new Label((int) e.getValue()[0] + " invoice" + (e.getValue()[0] == 1 ? "" : "s"));
                invLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B;");
                text.getChildren().addAll(nameLbl, invLbl);

                Region s = new Region();
                HBox.setHgrow(s, Priority.ALWAYS);

                Label val = new Label(String.format("%s%.2f", settings.getCurrency(), e.getValue()[1]));
                val.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #D9A13B;");
                row.getChildren().addAll(text, s, val);
                rows.getChildren().add(row);
            }
            card.getChildren().add(rows);
        }
        return card;
    }

    private Node createRecentBillsCard(List<Bill> bills, Settings settings) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setStyle("-fx-background-color: #151B25; -fx-padding: 18; -fx-background-radius: 8; -fx-border-color: #232B38; -fx-border-radius: 8;");
        VBox.setVgrow(card, Priority.ALWAYS);
        card.setMaxHeight(Double.MAX_VALUE);

        HBox top = new HBox();
        top.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Recent Invoices");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #CBD5E1;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button viewAllBtn = new Button("View All History →");
        viewAllBtn.getStyleClass().addAll("button-sm", "button-secondary");
        viewAllBtn.setTooltip(new Tooltip("Navigate to full invoice history"));
        viewAllBtn.setOnAction(e -> app.showHistory());

        top.getChildren().addAll(title, sp, viewAllBtn);
        card.getChildren().add(top);

        TableView<Bill> table = new TableView<>();
        table.getStyleClass().add("table-view");
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMinHeight(240);
        table.setPrefHeight(Region.USE_COMPUTED_SIZE);
        table.setMaxHeight(Double.MAX_VALUE);

        TableColumn<Bill, String> colNo = new TableColumn<>("Bill No");
        colNo.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getBillNo()));
        colNo.setMinWidth(100);
        colNo.setPrefWidth(110);

        TableColumn<Bill, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDate()));
        colDate.setMinWidth(90);
        colDate.setPrefWidth(95);

        TableColumn<Bill, String> colBuyer = new TableColumn<>("Buyer");
        colBuyer.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getVariables().getOrDefault("buyer_name", "—")));
        colBuyer.setMinWidth(160);
        colBuyer.setPrefWidth(220);

        TableColumn<Bill, String> colTotal = new TableColumn<>("Total");
        colTotal.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("%s%.2f", settings.getCurrency(), data.getValue().getTotals().getGrandTotal())));
        colTotal.setMinWidth(100);
        colTotal.setPrefWidth(110);

        TableColumn<Bill, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getStatus().getLabel()));
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
