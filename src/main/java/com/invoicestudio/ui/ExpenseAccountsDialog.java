package com.invoicestudio.ui;

import com.invoicestudio.model.ExpenseAccount;
import com.invoicestudio.service.ExpenseAccountService;
import com.invoicestudio.ui.views.ExpensesView;
import com.invoicestudio.service.ExpenseAnalytics.Report;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Expense Accounts manager — list / add / edit / rename-with-propagation /
 * archive / delete with guardrails, plus a report drill-down per account.
 *
 * Standalone themed Stage (same pattern as LabelStockDialog), opened from the
 * Expense Register toolbar.
 */
public class ExpenseAccountsDialog extends Stage {

    private final com.invoicestudio.ui.views.ExpensesView owner;
    private final com.invoicestudio.ui.StudioApp app;
    private final TableView<Row> table = new TableView<>();
    private final DecimalFormat money = new DecimalFormat("#,##0.00");

    /** One manager row: account + live usage rollup. */
    static class Row {
        ExpenseAccount account;
        int vouchers;
        double total;
        String lastDate = "—";
        String name() { return account.getName(); }
    }

    public ExpenseAccountsDialog(com.invoicestudio.ui.views.ExpensesView owner,
                                 com.invoicestudio.ui.StudioApp app) {
        this.owner = owner;
        this.app = app;
        setTitle("Expense Accounts");
        initModality(Modality.WINDOW_MODAL);
        initOwner(app.getPrimaryStage());

        VBox root = new VBox(14);
        root.setPadding(new Insets(18));
        root.getStyleClass().add("dialog-root");

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(2);
        Label title = new Label("Expense Accounts");
        title.getStyleClass().add("heading-l");
        Label sub = new Label("Your payee directory — pick accounts in the register, rename once, every voucher follows.");
        sub.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(title, sub);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button addBtn = UiTheme.goldBtn("+ New Account");
        addBtn.setOnAction(e -> showEditDialog(null));
        header.getChildren().addAll(titleBox, sp, addBtn);

        // Table
        buildColumns();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setRowFactory(tv -> {
            TableRow<Row> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) openReport(row.getItem());
            });
            return row;
        });

        Label hint = new Label("Double-click an account (or use Report) to open its spend report with charts.");
        hint.getStyleClass().add("view-subtitle");

        // Footer
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        Button reportBtn = UiTheme.smallBtn("Report");
        reportBtn.setOnAction(e -> {
            Row r = table.getSelectionModel().getSelectedItem();
            if (r != null) openReport(r);
        });
        Button editBtn = UiTheme.smallBtn("Edit");
        editBtn.setOnAction(e -> {
            Row r = table.getSelectionModel().getSelectedItem();
            if (r != null) showEditDialog(r.account);
        });
        Button archiveBtn = UiTheme.smallBtn("Archive / Restore");
        archiveBtn.setOnAction(e -> toggleArchive());
        Button deleteBtn = UiTheme.smallBtn("Delete");
        deleteBtn.getStyleClass().add("button-danger");
        deleteBtn.setOnAction(e -> deleteSelected());
        Region fsp = new Region();
        HBox.setHgrow(fsp, Priority.ALWAYS);
        Button closeBtn = UiTheme.smallBtn("Close");
        closeBtn.setOnAction(e -> close());
        footer.getChildren().addAll(reportBtn, editBtn, archiveBtn, deleteBtn, fsp, closeBtn);

        root.getChildren().addAll(header, table, hint, footer);
        VBox.setVgrow(table, Priority.ALWAYS);

        Scene scene = new Scene(root, 860, 560);
        java.net.URL css = getClass().getResource("/css/globalfile.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        setScene(scene);
        setOnShown(e -> reload());

        // Esc closes
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) close();
        });
    }

    private void buildColumns() {
        TableColumn<Row, String> cName = new TableColumn<>("Account");
        cName.setPrefWidth(220);
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
        cName.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setGraphic(null); return; }
                setText(s);
                if (getTableRow().getItem() != null && getTableRow().getItem().account.isArchived()) {
                    setTextFill(javafx.scene.paint.Color.web("#94A3B8"));
                }
            }
        });

        TableColumn<Row, String> cVouchers = new TableColumn<>("Vouchers");
        cVouchers.setPrefWidth(90);
        cVouchers.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().vouchers)));
        cVouchers.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<Row, String> cTotal = new TableColumn<>("Total");
        cTotal.setPrefWidth(130);
        cTotal.setCellValueFactory(d -> new SimpleStringProperty(
                "₹" + money.format(d.getValue().total)));
        cTotal.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<Row, String> cLast = new TableColumn<>("Last Used");
        cLast.setPrefWidth(100);
        cLast.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().lastDate));

        TableColumn<Row, String> cStatus = new TableColumn<>("Status");
        cStatus.setPrefWidth(90);
        cStatus.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().account.isArchived() ? "Archived" : "Active"));
        cStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setGraphic(null); return; }
                setGraphic(UiTheme.statusPill(s, "Archived".equals(s) ? "muted" : "success"));
            }
        });

        table.getColumns().addAll(cName, cVouchers, cTotal, cLast, cStatus);
    }

    private void reload() {
        var dm = app.getData();
        List<ExpenseAccount> accounts = dm.getAllExpenseAccounts();
        var usage = dm.expenseAccountUsage();
        java.util.List<Row> rows = new java.util.ArrayList<>();
        for (ExpenseAccount a : accounts) {
            Row r = new Row();
            r.account = a;
            var u = usage.get(a.getName().toLowerCase());
            if (u != null) {
                r.vouchers = u.vouchers;
                r.total = u.total;
                r.lastDate = u.lastDate.isBlank() ? "—" : u.lastDate;
            }
            rows.add(r);
        }
        table.setItems(FXCollections.observableArrayList(rows));
        if (rows.isEmpty()) {
            table.setPlaceholder(UiTheme.emptyState("📁", "No accounts yet",
                    "Add one, or save a voucher with a payee and it appears here."));
        }
    }

    private void showEditDialog(ExpenseAccount existing) {
        TextInputDialog dlg = new TextInputDialog(existing != null ? existing.getName() : "");
        dlg.setTitle(existing != null ? "Edit Account" : "New Account");
        dlg.setHeaderText(existing != null ? "Rename account — all vouchers follow"
                : "Add an expense account (payee)");
        dlg.setContentText("Account name:");
        DialogHelper.styleDialog(dlg);
        dlg.showAndWait().ifPresent(name -> {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) return;
            var dm = app.getData();
            ExpenseAccount dup = dm.expenseAccounts().findByName(trimmed);
            if (dup != null && (existing == null || !dup.getId().equals(existing.getId()))) {
                Toast.show(app.getRootPane(), "Duplicate Account", "'" + trimmed + "' already exists.", true);
                return;
            }
            if (existing != null && !trimmed.equalsIgnoreCase(existing.getName())) {
                ExpenseAccountService.renameWithPropagationAsync(dm, existing, trimmed, count -> {
                    reload();
                    owner.refresh();
                    Toast.show(app.getRootPane(), "Account Renamed",
                            "'" + existing.getName() + "' → '" + trimmed + "' — " + count + " voucher(s) updated.", false);
                });
            } else if (existing != null) {
                existing.setName(trimmed);
                dm.expenseAccounts().saveAccount(existing);
                dm.invalidateExpenseAccounts();
                reload();
            } else {
                ExpenseAccount acc = new ExpenseAccount(ExpenseAccountService.newId(), trimmed);
                dm.expenseAccounts().saveAccount(acc);
                dm.invalidateExpenseAccounts();
                reload();
            }
        });
    }

    private void toggleArchive() {
        Row r = table.getSelectionModel().getSelectedItem();
        if (r == null) return;
        r.account.setArchived(!r.account.isArchived());
        app.getData().expenseAccounts().saveAccount(r.account);
        app.getData().invalidateExpenseAccounts();
        reload();
        Toast.show(app.getRootPane(), r.account.isArchived() ? "Account Archived" : "Account Restored",
                r.account.getName() + (r.account.isArchived()
                        ? " — hidden from pickers, vouchers unchanged."
                        : " — available again."), false);
    }

    private void deleteSelected() {
        Row r = table.getSelectionModel().getSelectedItem();
        if (r == null) return;
        var dm = app.getData();
        String name = r.account.getName();
        if (r.vouchers > 0) {
            Alert a = new Alert(Alert.AlertType.WARNING,
                    "'" + name + "' has " + r.vouchers + " voucher(s) totalling ₹" + money.format(r.total)
                            + ".\n\nDeleting would orphan them. Rename the account instead — every voucher follows the new name.",
                    ButtonType.OK);
            a.setHeaderText("Cannot Delete — Account In Use");
            a.setTitle("Account In Use");
            DialogHelper.styleDialog(a);
            a.showAndWait();
            showEditDialog(r.account); // offer rename as the safe path
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete account '" + name + "'? It has no vouchers.", ButtonType.YES, ButtonType.CANCEL);
        confirm.setHeaderText("Delete Account");
        DialogHelper.styleDialog(confirm);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                dm.expenseAccounts().deleteAccount(r.account.getId());
                dm.invalidateExpenseAccounts();
                reload();
                Toast.show(app.getRootPane(), "Account Deleted", name + " removed.", false);
            }
        });
    }

    private void openReport(Row r) {
        new ExpenseReportDialog(app, "Account", r.name()).show();
    }
}
