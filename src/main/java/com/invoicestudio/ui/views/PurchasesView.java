package com.invoicestudio.ui.views;

import com.invoicestudio.model.PurchaseBill;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import com.invoicestudio.ui.UiTheme;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Optional;

/**
 * Purchase History / Inward Register (Tally "Purchase Register").
 *
 * Lists supplier bills with supplier, taxable value, ITC and payable,
 * with search + status filter. Row actions open details, allow editing
 * (re-enters CreatePurchaseView) and deletion (which reverses stock).
 */
public class PurchasesView extends BorderPane {

    private final StudioApp app;

    private final TableView<PurchaseBill> table = new TableView<>();
    private FilteredList<PurchaseBill> filteredPurchases;
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final Label resultCountLbl = new Label("0 purchases");

    private final Label statTotalPurchases = UiTheme.kpiValue("0");
    private final Label statTotalValue = UiTheme.kpiValue("₹0.00");
    private final Label statTotalItc = UiTheme.kpiValue("₹0.00");
    private final Label statUnpaid = UiTheme.kpiValue("₹0.00");

    public PurchasesView(StudioApp app) {
        this.app = app;

        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        setTop(createTopBar());
        setCenter(createTableArea());

        refresh();
    }

    public void refresh() {
        List<PurchaseBill> purchases = app.getData().getAllPurchases();
        filteredPurchases = new FilteredList<>(FXCollections.observableArrayList(purchases), p -> true);
        table.setItems(filteredPurchases);
        applyFilter();
        updateSummaryStats(purchases);
    }

    private Node createTopBar() {
        VBox box = new VBox(16);
        box.setPadding(new Insets(0, 0, 16, 0));

        HBox bar1 = new HBox(16);
        bar1.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Purchase Register");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Inward supplies from sellers — stock IN, input tax credit and payables.");
        subtitle.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button newBtn = UiTheme.goldBtn("+ Record Purchase");
        newBtn.setTooltip(new Tooltip("Record a purchase bill from a supplier"));
        newBtn.setOnAction(e -> app.showCreatePurchase());

        bar1.getChildren().addAll(titleBox, sp, newBtn);

        HBox statsGrid = new HBox(16);
        statsGrid.getChildren().addAll(
                UiTheme.kpiCard("TOTAL PURCHASES", statTotalPurchases, "Supplier bills recorded", "accent-gold"),
                UiTheme.kpiCard("PURCHASE VALUE", statTotalValue, "Taxable inward value", "accent-blue"),
                UiTheme.kpiCard("INPUT TAX CREDIT", statTotalItc, "GST paid on purchases (ITC)", "accent-emerald"),
                UiTheme.kpiCard("UNPAID (PAYABLE)", statUnpaid, "Owed to suppliers", "accent-red")
        );

        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        searchField.setPromptText("Search by Voucher No, Supplier's Bill No, Supplier...");
        searchField.setPrefWidth(340);
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, o, v) -> applyFilter());

        statusFilter.setItems(FXCollections.observableArrayList("All Statuses", "Unpaid", "Paid"));
        statusFilter.setValue("All Statuses");
        statusFilter.setOnAction(e -> applyFilter());

        Button clearBtn = UiTheme.smallBtn("Clear");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            statusFilter.setValue("All Statuses");
            applyFilter();
        });

        Region filterSp = new Region();
        HBox.setHgrow(filterSp, Priority.ALWAYS);
        resultCountLbl.getStyleClass().add("result-count");

        filterRow.getChildren().addAll(searchField, statusFilter, clearBtn, filterSp, resultCountLbl);

        box.getChildren().addAll(bar1, statsGrid, filterRow);
        return box;
    }

    private void updateSummaryStats(List<PurchaseBill> purchases) {
        String cur = app.getData().getSettings().getCurrency();
        double value = 0;
        double itc = 0;
        double unpaid = 0;
        for (PurchaseBill p : purchases) {
            value += p.getTotals() != null ? p.getTotals().getTaxable() : 0;
            itc += p.getTotals() != null
                    ? p.getTotals().getCgst() + p.getTotals().getSgst() + p.getTotals().getIgst() : 0;
            if (!p.isPaid()) unpaid += p.getAmountPayable();
        }
        statTotalPurchases.setText(String.valueOf(purchases.size()));
        statTotalValue.setText(String.format("%s%.2f", cur, value));
        statTotalItc.setText(String.format("%s%.2f", cur, itc));
        statUnpaid.setText(String.format("%s%.2f", cur, unpaid));
    }

    private Node createTableArea() {
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        VBox emptyBox = UiTheme.emptyState("📥", "No Purchases Recorded",
                "Click '+ Record Purchase' to enter a supplier bill (stock will increase automatically).");
        table.setPlaceholder(emptyBox);

        buildColumns();
        return table;
    }

    private void buildColumns() {
        table.getColumns().clear();

        TableColumn<PurchaseBill, String> colNo = new TableColumn<>("Voucher No");
        colNo.setPrefWidth(110);
        colNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBillNo()));
        colNo.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("table-cell-mono"); }
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s);
            }
        });

        TableColumn<PurchaseBill, String> colDate = new TableColumn<>("Date");
        colDate.setPrefWidth(95);
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));

        TableColumn<PurchaseBill, String> colSup = new TableColumn<>("Supplier");
        colSup.setPrefWidth(190);
        colSup.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSupplierName()));
        colSup.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setGraphic(null); }
                else { setText(s); getStyleClass().add("table-cell-title"); }
            }
        });

        TableColumn<PurchaseBill, String> colSupBill = new TableColumn<>("Supplier's Bill No");
        colSupBill.setPrefWidth(140);
        colSupBill.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getSupplierBillNo() != null && !d.getValue().getSupplierBillNo().isBlank()
                        ? d.getValue().getSupplierBillNo() : "—"));

        TableColumn<PurchaseBill, String> colTaxable = new TableColumn<>("Taxable");
        colTaxable.setPrefWidth(110);
        colTaxable.setCellValueFactory(d -> new SimpleStringProperty(
                String.format("%s%.2f", app.getData().getSettings().getCurrency(),
                        d.getValue().getTotals() != null ? d.getValue().getTotals().getTaxable() : 0)));
        colTaxable.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<PurchaseBill, String> colItc = new TableColumn<>("ITC (GST)");
        colItc.setPrefWidth(110);
        colItc.setCellValueFactory(d -> {
            double itc = d.getValue().getTotals() != null
                    ? d.getValue().getTotals().getCgst() + d.getValue().getTotals().getSgst() + d.getValue().getTotals().getIgst() : 0;
            return new SimpleStringProperty(String.format("%s%.2f", app.getData().getSettings().getCurrency(), itc));
        });
        colItc.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<PurchaseBill, String> colTotal = new TableColumn<>("Payable");
        colTotal.setPrefWidth(120);
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(
                String.format("%s%.2f", app.getData().getSettings().getCurrency(), d.getValue().getAmountPayable())));
        colTotal.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<PurchaseBill, String> colStatus = new TableColumn<>("Status");
        colStatus.setPrefWidth(100);
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isPaid() ? "PAID" : "UNPAID"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); setText(null); }
                else if ("PAID".equals(s)) setGraphic(UiTheme.statusPill("✓ Paid", "success"));
                else setGraphic(UiTheme.statusPill("Credit", "warning"));
            }
        });

        TableColumn<PurchaseBill, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(210);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            private final Button editBtn = new Button("Edit");
            private final Button delBtn = new Button("✕");
            private final HBox box = new HBox(6, viewBtn, editBtn, delBtn);

            {
                viewBtn.getStyleClass().addAll("button-sm", "button-secondary");
                viewBtn.setOnAction(e -> {
                    PurchaseBill p = getTableRow().getItem();
                    if (p != null) showDetails(p);
                });
                editBtn.getStyleClass().addAll("button-sm", "button-secondary");
                editBtn.setOnAction(e -> {
                    PurchaseBill p = getTableRow().getItem();
                    if (p != null) app.showEditPurchase(p);
                });
                delBtn.getStyleClass().addAll("button-sm", "button-danger");
                delBtn.setTooltip(new Tooltip("Delete purchase (reverses stock and payable)"));
                delBtn.setOnAction(e -> {
                    PurchaseBill p = getTableRow().getItem();
                    if (p != null) confirmDelete(p);
                });
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) setGraphic(null);
                else setGraphic(box);
            }
        });

        table.getColumns().addAll(colNo, colDate, colSup, colSupBill, colTaxable, colItc, colTotal, colStatus, colActions);
    }

    private void applyFilter() {
        if (filteredPurchases == null) return;
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String status = statusFilter.getValue();
        filteredPurchases.setPredicate(p -> {
            if ("Unpaid".equals(status) && p.isPaid()) return false;
            if ("Paid".equals(status) && !p.isPaid()) return false;
            if (q.isEmpty()) return true;
            return (p.getBillNo() != null && p.getBillNo().toLowerCase().contains(q))
                    || (p.getSupplierBillNo() != null && p.getSupplierBillNo().toLowerCase().contains(q))
                    || (p.getSupplierName() != null && p.getSupplierName().toLowerCase().contains(q));
        });
        resultCountLbl.setText("Showing " + filteredPurchases.size() + " of " + filteredPurchases.getSource().size() + " purchases");
    }

    private void showDetails(PurchaseBill p) {
        String cur = app.getData().getSettings().getCurrency();
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Purchase " + p.getBillNo());
        dlg.setHeaderText(p.getSupplierName() + (p.getSupplierBillNo().isBlank() ? "" : " — their bill " + p.getSupplierBillNo()));

        VBox content = new VBox(10);
        content.setPadding(new Insets(14));
        content.setPrefWidth(560);

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(6);
        int r = 0;
        g.add(new Label("Date:"), 0, r);
        g.add(new Label(p.getDate()), 1, r++);
        g.add(new Label("Taxable:"), 0, r);
        g.add(new Label(String.format("%s%.2f", cur, p.getTotals() != null ? p.getTotals().getTaxable() : 0)), 1, r++);
        if (p.getTotals() != null) {
            if (p.getTotals().getCgst() > 0) {
                g.add(new Label("CGST:"), 0, r);
                g.add(new Label(String.format("%s%.2f", cur, p.getTotals().getCgst())), 1, r++);
            }
            if (p.getTotals().getSgst() > 0) {
                g.add(new Label("SGST:"), 0, r);
                g.add(new Label(String.format("%s%.2f", cur, p.getTotals().getSgst())), 1, r++);
            }
            if (p.getTotals().getIgst() > 0) {
                g.add(new Label("IGST:"), 0, r);
                g.add(new Label(String.format("%s%.2f", cur, p.getTotals().getIgst())), 1, r++);
            }
        }
        if (p.getFreight() > 0) {
            g.add(new Label("Freight / Other:"), 0, r);
            g.add(new Label(String.format("%s%.2f", cur, p.getFreight())), 1, r++);
        }
        g.add(new Label("Total Payable:"), 0, r);
        Label totalLbl = new Label(String.format("%s%.2f", cur, p.getAmountPayable()));
        totalLbl.getStyleClass().add("table-cell-title");
        g.add(totalLbl, 1, r++);
        g.add(new Label("Status:"), 0, r);
        g.add(new Label(p.isPaid() ? "Paid (" + p.getPaymentMode() + ")" : "On Credit (Payable)"), 1, r++);
        if (!p.getNotes().isBlank()) {
            g.add(new Label("Notes:"), 0, r);
            g.add(new Label(p.getNotes()), 1, r++);
        }

        // Item lines summary
        ListView<String> lines = new ListView<>();
        for (com.invoicestudio.model.BillItem it : p.getItems()) {
            lines.getItems().add(String.format("%s — %.2f %s @ %s%.2f = %s%.2f (GST %.0f%%)",
                    it.getDesc(), it.getQty(), it.getUnit(), cur, it.getRate(), cur, it.getAmount(), it.getGst()));
        }
        lines.setPrefHeight(Math.min(160, 36 * (p.getItems().size() + 1)));
        lines.getStyleClass().add("card-pane-subtle");

        content.getChildren().addAll(g, new Label("Item Lines (Stock IN):"), lines);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogHelper.styleDialog(dlg, 600, 480);
        dlg.showAndWait();
    }

    private void confirmDelete(PurchaseBill p) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete purchase " + p.getBillNo() + " from " + p.getSupplierName()
                        + "? Stock will be reduced back and payable adjusted.",
                ButtonType.YES, ButtonType.CANCEL);
        confirm.setHeaderText("Delete Purchase");
        DialogHelper.styleDialog(confirm);
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.YES) return;

        app.getData().deletePurchase(p.getId());
        refresh();
        Toast.show(app.getRootPane(), "Purchase Deleted", p.getBillNo() + " removed; stock reversed.", false);
    }
}
