package com.invoicestudio.ui.views;

import com.invoicestudio.model.Expense;
import com.invoicestudio.service.PurchaseService;
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
import java.util.UUID;

/**
 * Expense Register (Tally "Expense Vouchers" day-book).
 *
 * Quick expense entry with chart-of-accounts category, payment mode and
 * reference. KPI cards split Direct (trading account) vs Indirect (P&L)
 * heads so the Financials view can consume the same data.
 */
public class ExpensesView extends BorderPane {

    private final StudioApp app;

    private final TableView<Expense> table = new TableView<>();
    private FilteredList<Expense> filteredExpenses;
    private final TextField searchField = new TextField();
    private final ComboBox<String> typeFilter = new ComboBox<>();
    private final Label resultCountLbl = new Label("0 expenses");

    private final Label statTotal = UiTheme.kpiValue("₹0.00");
    private final Label statDirect = UiTheme.kpiValue("₹0.00");
    private final Label statIndirect = UiTheme.kpiValue("₹0.00");
    private final Label statCount = UiTheme.kpiValue("0");

    public ExpensesView(StudioApp app) {
        this.app = app;

        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        setTop(createTopBar());
        setCenter(createTableArea());

        refresh();
    }

    public void refresh() {
        List<Expense> expenses = app.getData().getAllExpenses();
        filteredExpenses = new FilteredList<>(FXCollections.observableArrayList(expenses), e -> true);
        table.setItems(filteredExpenses);
        applyFilter();
        updateSummaryStats(expenses);
    }

    private Node createTopBar() {
        VBox box = new VBox(16);
        box.setPadding(new Insets(0, 0, 16, 0));

        HBox bar1 = new HBox(16);
        bar1.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Expense Register");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Day-to-day business expenses — direct (trading) and indirect (P&L) heads.");
        subtitle.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button addBtn = UiTheme.goldBtn("+ New Expense");
        addBtn.setTooltip(new Tooltip("Record an expense voucher"));
        addBtn.setOnAction(e -> showExpenseDialog(null));

        bar1.getChildren().addAll(titleBox, sp, addBtn);

        HBox statsGrid = new HBox(16);
        statsGrid.getChildren().addAll(
                UiTheme.kpiCard("TOTAL EXPENSES", statTotal, "All vouchers this period", "accent-red"),
                UiTheme.kpiCard("DIRECT", statDirect, "Freight, wages, power — trading account", "accent-gold"),
                UiTheme.kpiCard("INDIRECT", statIndirect, "Rent, salaries, admin — P&L account", "accent-blue"),
                UiTheme.kpiCard("VOUCHERS", statCount, "Entries recorded", "accent-emerald")
        );

        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        searchField.setPromptText("Search by Category, Description, Payee, Reference...");
        searchField.setPrefWidth(340);
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, o, v) -> applyFilter());

        typeFilter.setItems(FXCollections.observableArrayList("All Types", "Direct", "Indirect"));
        typeFilter.setValue("All Types");
        typeFilter.setOnAction(e -> applyFilter());

        Button clearBtn = UiTheme.smallBtn("Clear");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            typeFilter.setValue("All Types");
            applyFilter();
        });

        Region filterSp = new Region();
        HBox.setHgrow(filterSp, Priority.ALWAYS);
        resultCountLbl.getStyleClass().add("result-count");

        filterRow.getChildren().addAll(searchField, typeFilter, clearBtn, filterSp, resultCountLbl);

        box.getChildren().addAll(bar1, statsGrid, filterRow);
        return box;
    }

    private void updateSummaryStats(List<Expense> expenses) {
        String cur = app.getData().getSettings().getCurrency();
        double total = 0, direct = 0, indirect = 0;
        for (Expense e : expenses) {
            total += e.getAmount();
            if (Expense.isDirect(e.getCategory())) direct += e.getAmount();
            else indirect += e.getAmount();
        }
        statTotal.setText(String.format("%s%.2f", cur, total));
        statDirect.setText(String.format("%s%.2f", cur, direct));
        statIndirect.setText(String.format("%s%.2f", cur, indirect));
        statCount.setText(String.valueOf(expenses.size()));
    }

    private Node createTableArea() {
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        VBox emptyBox = UiTheme.emptyState("🧾", "No Expenses Recorded",
                "Click '+ New Expense' to record rent, freight, salaries and other outflows.");
        table.setPlaceholder(emptyBox);

        buildColumns();
        return table;
    }

    private void buildColumns() {
        table.getColumns().clear();

        TableColumn<Expense, String> colDate = new TableColumn<>("Date");
        colDate.setPrefWidth(95);
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));

        TableColumn<Expense, String> colCat = new TableColumn<>("Category");
        colCat.setPrefWidth(170);
        colCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory()));
        colCat.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setGraphic(null); }
                else {
                    setText(s);
                    getStyleClass().add("table-cell-title");
                    if (Expense.isDirect(s)) {
                        setGraphic(UiTheme.statusPill("Direct", "warning"));
                    } else {
                        setGraphic(UiTheme.statusPill("Indirect", "info"));
                    }
                }
            }
        });

        TableColumn<Expense, String> colDesc = new TableColumn<>("Description");
        colDesc.setPrefWidth(230);
        colDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));

        TableColumn<Expense, String> colPayee = new TableColumn<>("Paid To");
        colPayee.setPrefWidth(140);
        colPayee.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getPayee().isBlank() ? "—" : d.getValue().getPayee()));

        TableColumn<Expense, String> colMode = new TableColumn<>("Mode / Ref");
        colMode.setPrefWidth(150);
        colMode.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getPaymentMode() + (d.getValue().getReference().isBlank() ? "" : " · " + d.getValue().getReference())));

        TableColumn<Expense, String> colAmount = new TableColumn<>("Amount");
        colAmount.setPrefWidth(120);
        colAmount.setCellValueFactory(d -> new SimpleStringProperty(
                String.format("%s%.2f", app.getData().getSettings().getCurrency(), d.getValue().getAmount())));
        colAmount.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<Expense, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(140);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn = new Button("✕");
            private final HBox box = new HBox(6, editBtn, delBtn);

            {
                editBtn.getStyleClass().addAll("button-sm", "button-secondary");
                editBtn.setOnAction(e -> {
                    Expense ex = getTableRow().getItem();
                    if (ex != null) showExpenseDialog(ex);
                });
                delBtn.getStyleClass().addAll("button-sm", "button-danger");
                delBtn.setOnAction(e -> {
                    Expense ex = getTableRow().getItem();
                    if (ex != null) confirmDelete(ex);
                });
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) setGraphic(null);
                else setGraphic(box);
            }
        });

        table.getColumns().addAll(colDate, colCat, colDesc, colPayee, colMode, colAmount, colActions);
    }

    private void applyFilter() {
        if (filteredExpenses == null) return;
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String type = typeFilter.getValue();
        filteredExpenses.setPredicate(e -> {
            if ("Direct".equals(type) && !Expense.isDirect(e.getCategory())) return false;
            if ("Indirect".equals(type) && Expense.isDirect(e.getCategory())) return false;
            if (q.isEmpty()) return true;
            return (e.getCategory() != null && e.getCategory().toLowerCase().contains(q))
                    || (e.getDescription() != null && e.getDescription().toLowerCase().contains(q))
                    || (e.getPayee() != null && e.getPayee().toLowerCase().contains(q))
                    || (e.getReference() != null && e.getReference().toLowerCase().contains(q));
        });
        resultCountLbl.setText("Showing " + filteredExpenses.size() + " of " + filteredExpenses.getSource().size() + " expenses");
    }

    // ------------------------------------------------------------------
    // Expense entry dialog
    // ------------------------------------------------------------------

    private void showExpenseDialog(Expense existing) {
        Dialog<Expense> dlg = new Dialog<>();
        dlg.setTitle(existing != null ? "Edit Expense" : "New Expense Voucher");
        dlg.setHeaderText(existing != null ? "Update expense entry" : "Record a business expense");

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));

        DatePicker datePick = UiTheme.datePicker("Expense Date");
        try {
            datePick.setValue(existing != null && existing.getDate() != null && !existing.getDate().isBlank()
                    ? java.time.LocalDate.parse(existing.getDate()) : java.time.LocalDate.now());
        } catch (Exception ignored) {}

        ComboBox<String> catBox = new ComboBox<>(FXCollections.observableArrayList(Expense.CATEGORIES));
        catBox.setEditable(true);
        catBox.setValue(existing != null ? existing.getCategory() : "Miscellaneous");
        catBox.setMaxWidth(Double.MAX_VALUE);

        TextField descF = new TextField(existing != null ? existing.getDescription() : "");
        descF.setPromptText("e.g. Office rent for September");

        TextField amountF = new TextField(existing != null && existing.getAmount() > 0
                ? String.valueOf(existing.getAmount()) : "");
        amountF.setPromptText("0.00");

        ComboBox<String> modeBox = new ComboBox<>(FXCollections.observableArrayList("Cash", "Bank / NEFT", "Cheque", "UPI"));
        modeBox.setValue(existing != null && !existing.getPaymentMode().isBlank() ? existing.getPaymentMode() : "Cash");

        TextField refF = new TextField(existing != null ? existing.getReference() : "");
        refF.setPromptText("Cheque No / UTR (optional)");

        TextField payeeF = new TextField(existing != null ? existing.getPayee() : "");
        payeeF.setPromptText("Paid to (optional)");

        g.add(new Label("Date:*"), 0, 0); g.add(datePick, 1, 0);
        g.add(new Label("Category:*"), 0, 1); g.add(catBox, 1, 1);
        g.add(new Label("Description:"), 0, 2); g.add(descF, 1, 2);
        g.add(new Label("Amount (₹):*"), 0, 3); g.add(amountF, 1, 3);
        g.add(new Label("Payment Mode:"), 0, 4); g.add(modeBox, 1, 4);
        g.add(new Label("Reference:"), 0, 5); g.add(refF, 1, 5);
        g.add(new Label("Paid To:"), 0, 6); g.add(payeeF, 1, 6);

        dlg.getDialogPane().setContent(g);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogHelper.styleDialog(dlg);

        dlg.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            if (datePick.getValue() == null) {
                Toast.show(app.getRootPane(), "Validation Error", "Date is required.", true);
                return null;
            }
            double amount = 0;
            try { amount = Double.parseDouble(amountF.getText().trim()); } catch (Exception ignored) {}
            if (amount <= 0) {
                Toast.show(app.getRootPane(), "Validation Error", "Amount must be greater than zero.", true);
                return null;
            }
            String category = catBox.getValue() != null ? catBox.getValue().trim() : "";
            if (category.isEmpty()) {
                Toast.show(app.getRootPane(), "Validation Error", "Category is required.", true);
                return null;
            }
            Expense e = existing != null ? existing : new Expense();
            if (existing == null) {
                e.setId("exp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            }
            e.setDate(datePick.getValue().toString());
            e.setCategory(category);
            e.setDescription(descF.getText() == null ? "" : descF.getText().trim());
            e.setAmount(amount);
            e.setPaymentMode(modeBox.getValue() != null ? modeBox.getValue() : "Cash");
            e.setReference(refF.getText() == null ? "" : refF.getText().trim());
            e.setPayee(payeeF.getText() == null ? "" : payeeF.getText().trim());
            return e;
        });

        dlg.showAndWait().ifPresent(e -> {
            app.getData().saveExpense(e);
            refresh();
            Toast.show(app.getRootPane(), existing != null ? "Expense Updated" : "Expense Saved",
                    String.format("%s%.2f — %s", app.getData().getSettings().getCurrency(), e.getAmount(), e.getCategory()), false);
        });
    }

    private void confirmDelete(Expense e) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete expense of " + String.format("%s%.2f", app.getData().getSettings().getCurrency(), e.getAmount())
                        + " (" + e.getCategory() + ")?",
                ButtonType.YES, ButtonType.CANCEL);
        confirm.setHeaderText("Delete Expense");
        DialogHelper.styleDialog(confirm);
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.YES) return;

        app.getData().deleteExpense(e.getId());
        refresh();
        Toast.show(app.getRootPane(), "Expense Deleted", e.getCategory() + " removed.", false);
    }
}
