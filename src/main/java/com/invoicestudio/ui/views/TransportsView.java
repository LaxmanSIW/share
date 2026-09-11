package com.invoicestudio.ui.views;

import com.invoicestudio.model.Buyer;
import com.invoicestudio.model.Transport;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import com.invoicestudio.ui.UiTheme;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Transport Directory — Version 4.0.0
 * Manages freight & logistics carriers, vehicle numbers, and contact information.
 */
public class TransportsView extends BorderPane {

    private final StudioApp app;
    private final TableView<Transport> table = new TableView<>();
    private FilteredList<Transport> filteredTransports;
    private final TextField searchField = new TextField();
    private final Label countBadge = new Label("0 carriers");

    public TransportsView(StudioApp app) {
        this.app = app;
        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        setTop(createHeader());
        setCenter(createTableArea());

        refresh();
    }

    public void refresh() {
        List<Transport> list = app.getData().getAllTransports();
        filteredTransports = new FilteredList<>(FXCollections.observableArrayList(list), t -> true);
        table.setItems(filteredTransports);
        applyFilter();
    }

    private Node createHeader() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(0, 0, 16, 0));

        HBox topRow = new HBox(16);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Transport Directory");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Manage transport carriers, fleet vehicle numbers & logistics partners.");
        subtitle.getStyleClass().add("kpi-subtext");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button newBtn = UiTheme.goldBtn("+ New Transport");
        newBtn.setOnAction(e -> showAddEditDialog(null));

        topRow.getChildren().addAll(titleBox, sp, newBtn);

        // Search bar
        HBox searchRow = new HBox(12);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        searchField.setPromptText("Search by Agency Name, Vehicle Number, Phone...");
        searchField.setPrefWidth(320);
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, o, v) -> applyFilter());

        countBadge.getStyleClass().addAll("badge", "badge-gray");

        searchRow.getChildren().addAll(searchField, countBadge);
        box.getChildren().addAll(topRow, searchRow);
        return box;
    }

    private Node createTableArea() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("data-table");

        TableColumn<Transport, String> colName = new TableColumn<>("Carrier / Agency Name");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colName.setPrefWidth(220);

        TableColumn<Transport, String> colPhone = new TableColumn<>("Phone / Contact");
        colPhone.setPrefWidth(160);
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(!d.getValue().getPhone().isBlank() ? d.getValue().getPhone() : "—"));

        TableColumn<Transport, String> colVehicle = new TableColumn<>("Vehicle Number");
        colVehicle.setPrefWidth(160);
        colVehicle.setCellValueFactory(d -> new SimpleStringProperty(!d.getValue().getVehicleNumber().isBlank() ? d.getValue().getVehicleNumber() : "—"));
        colVehicle.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null || val.equals("—")) {
                    setText(empty ? null : "—");
                    setGraphic(null);
                } else {
                    Label badge = new Label(val);
                    badge.getStyleClass().addAll("badge", "badge-blue");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<Transport, Number> colBuyers = new TableColumn<>("Default for Buyers");
        colBuyers.setPrefWidth(150);
        colBuyers.setCellValueFactory(d -> {
            String trpId = d.getValue().getId();
            String trpName = d.getValue().getName();
            long count = app.getData().buyers().getAllBuyers().stream()
                .filter(b -> trpId.equalsIgnoreCase(b.getDefaultTransportId()) ||
                    (b.getCustom() != null && trpName.equalsIgnoreCase(b.getCustom().get("transport_name"))))
                .count();
            return new SimpleIntegerProperty((int) count);
        });
        colBuyers.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(val.intValue() + " customers");
                    badge.getStyleClass().addAll("badge", val.intValue() > 0 ? "badge-green" : "badge-gray");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<Transport, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(140);
        colActions.setStyle("-fx-alignment: CENTER-RIGHT;");
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = UiTheme.iconBtn(IconHelper.ICON_EDIT, "Edit Transport");
            private final Button delBtn = UiTheme.iconBtn(IconHelper.ICON_TRASH, "Delete Transport");
            private final HBox actBox = new HBox(6, editBtn, delBtn);

            {
                actBox.setAlignment(Pos.CENTER_RIGHT);
                editBtn.setOnAction(e -> {
                    Transport t = getTableRow().getItem();
                    if (t != null) showAddEditDialog(t);
                });
                delBtn.setOnAction(e -> {
                    Transport t = getTableRow().getItem();
                    if (t != null) confirmDelete(t);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : actBox);
            }
        });

        table.getColumns().addAll(colName, colPhone, colVehicle, colBuyers, colActions);

        VBox wrap = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrap;
    }

    private void applyFilter() {
        if (filteredTransports == null) return;
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        filteredTransports.setPredicate(t -> {
            if (q.isEmpty()) return true;
            return (t.getName() != null && t.getName().toLowerCase().contains(q)) ||
                   (t.getPhone() != null && t.getPhone().toLowerCase().contains(q)) ||
                   (t.getVehicleNumber() != null && t.getVehicleNumber().toLowerCase().contains(q));
        });
        countBadge.setText(filteredTransports.size() + " carriers");
    }

    private void showAddEditDialog(Transport existing) {
        Dialog<Transport> dlg = new Dialog<>();
        dlg.setTitle(existing == null ? "New Transport Agency" : "Edit Transport — " + existing.getName());
        dlg.setHeaderText(existing == null ? "Register a transport carrier / vehicle" : "Update transport agency details");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));

        ColumnConstraints col0 = new ColumnConstraints(140);
        ColumnConstraints col1 = new ColumnConstraints(280);
        grid.getColumnConstraints().addAll(col0, col1);

        TextField nameF = new TextField(existing != null ? existing.getName() : "");
        nameF.setPromptText("Agency Name (e.g. VRL Logistics)");

        TextField phoneF = new TextField(existing != null ? existing.getPhone() : "");
        phoneF.setPromptText("Contact phone / driver number");

        TextField vehicleF = new TextField(existing != null ? existing.getVehicleNumber() : "");
        vehicleF.setPromptText("e.g. MH-12-AB-1234");

        grid.add(new Label("Carrier Name:"), 0, 0);
        grid.add(nameF, 1, 0);
        grid.add(new Label("Phone / Mobile:"), 0, 1);
        grid.add(phoneF, 1, 1);
        grid.add(new Label("Vehicle Number:"), 0, 2);
        grid.add(vehicleF, 1, 2);

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogHelper.styleDialog(dlg);

        dlg.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String name = nameF.getText().trim();
                if (name.isBlank()) {
                    Toast.show(app.getRootPane(), "Validation Error", "Transport name is required.", true);
                    return null;
                }
                if (existing != null) {
                    existing.setName(name);
                    existing.setPhone(phoneF.getText().trim());
                    existing.setVehicleNumber(vehicleF.getText().trim());
                    existing.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    return existing;
                } else {
                    return new Transport(null, name, phoneF.getText().trim(), vehicleF.getText().trim());
                }
            }
            return null;
        });

        dlg.showAndWait().ifPresent(t -> {
            app.getData().saveTransport(t);
            refresh();
            Toast.show(app.getRootPane(), "Transport Saved", "Carrier '" + t.getName() + "' saved.", false);
        });
    }

    private void confirmDelete(Transport t) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Transport");
        alert.setHeaderText("Delete carrier '" + t.getName() + "'?");
        alert.setContentText("Are you sure you want to remove this transport record?");
        DialogHelper.styleDialog(alert);

        alert.showAndWait().ifPresent(ans -> {
            if (ans == ButtonType.OK) {
                app.getData().deleteTransport(t.getId());
                refresh();
                Toast.show(app.getRootPane(), "Transport Deleted", "Carrier removed successfully.", false);
            }
        });
    }
}
