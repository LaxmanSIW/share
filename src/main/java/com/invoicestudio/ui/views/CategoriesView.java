package com.invoicestudio.ui.views;

import com.invoicestudio.model.ItemCategory;
import com.invoicestudio.model.ItemRecord;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Item Categories Directory — Version 4.0.0
 * Allows structured product categorization with real-time item count tracking.
 */
public class CategoriesView extends BorderPane {

    private final StudioApp app;
    private final TableView<ItemCategory> table = new TableView<>();
    private FilteredList<ItemCategory> filteredCategories;
    private final TextField searchField = new TextField();
    private final Label countBadge = new Label("0 categories");

    public CategoriesView(StudioApp app) {
        this.app = app;
        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        setTop(createHeader());
        setCenter(createTableArea());

        refresh();
    }

    public void refresh() {
        List<ItemCategory> list = app.getData().getAllCategories();
        filteredCategories = new FilteredList<>(FXCollections.observableArrayList(list), c -> true);
        table.setItems(filteredCategories);
        applyFilter();
    }

    private Node createHeader() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(0, 0, 16, 0));

        HBox topRow = new HBox(16);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Item Categories");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Manage product categories for reporting and cataloging.");
        subtitle.getStyleClass().add("kpi-subtext");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button newBtn = UiTheme.goldBtn("+ New Category");
        newBtn.setOnAction(e -> showAddEditDialog(null));

        topRow.getChildren().addAll(titleBox, sp, newBtn);

        // Search bar
        HBox searchRow = new HBox(12);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        searchField.setPromptText("Search categories by name...");
        searchField.setPrefWidth(300);
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

        TableColumn<ItemCategory, String> colName = new TableColumn<>("Category Name");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colName.setPrefWidth(240);

        TableColumn<ItemCategory, Number> colCount = new TableColumn<>("Assigned Items");
        colCount.setPrefWidth(140);
        colCount.setCellValueFactory(d -> {
            String catId = d.getValue().getId();
            String catName = d.getValue().getName();
            long count = app.getData().getAllItems().stream()
                .filter(it -> catId.equalsIgnoreCase(it.getCategoryId()) || catName.equalsIgnoreCase(it.getCategoryName()))
                .count();
            return new SimpleIntegerProperty((int) count);
        });
        colCount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(val.intValue() + " items");
                    badge.getStyleClass().addAll("badge", val.intValue() > 0 ? "badge-green" : "badge-gray");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<ItemCategory, String> colDate = new TableColumn<>("Created Date");
        colDate.setPrefWidth(160);
        colDate.setCellValueFactory(d -> {
            String c = d.getValue().getCreatedAt();
            if (c != null && c.length() >= 10) return new SimpleStringProperty(c.substring(0, 10));
            return new SimpleStringProperty("—");
        });

        TableColumn<ItemCategory, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(140);
        colActions.setStyle("-fx-alignment: CENTER-RIGHT;");
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = UiTheme.iconBtn(IconHelper.ICON_EDIT, "Edit Category");
            private final Button delBtn = UiTheme.iconBtn(IconHelper.ICON_TRASH, "Delete Category");
            private final HBox actBox = new HBox(6, editBtn, delBtn);

            {
                actBox.setAlignment(Pos.CENTER_RIGHT);
                editBtn.setOnAction(e -> {
                    ItemCategory cat = getTableRow().getItem();
                    if (cat != null) showAddEditDialog(cat);
                });
                delBtn.setOnAction(e -> {
                    ItemCategory cat = getTableRow().getItem();
                    if (cat != null) confirmDelete(cat);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : actBox);
            }
        });

        table.getColumns().addAll(colName, colCount, colDate, colActions);

        VBox wrap = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrap;
    }

    private void applyFilter() {
        if (filteredCategories == null) return;
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        filteredCategories.setPredicate(cat -> {
            if (q.isEmpty()) return true;
            return cat.getName() != null && cat.getName().toLowerCase().contains(q);
        });
        countBadge.setText(filteredCategories.size() + " categories");
    }

    private void showAddEditDialog(ItemCategory existing) {
        Dialog<ItemCategory> dlg = new Dialog<>();
        dlg.setTitle(existing == null ? "New Item Category" : "Edit Category — " + existing.getName());
        dlg.setHeaderText(existing == null ? "Create a new product category" : "Update category name");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));

        TextField nameF = new TextField(existing != null ? existing.getName() : "");
        nameF.setPromptText("e.g. Trousers, Formal Shirts, Denim Wear");
        nameF.setPrefWidth(280);

        grid.add(new Label("Category Name:"), 0, 0);
        grid.add(nameF, 1, 0);

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogHelper.styleDialog(dlg);

        dlg.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String name = nameF.getText().trim();
                if (name.isBlank()) {
                    Toast.show(app.getRootPane(), "Validation Error", "Category name cannot be blank.", true);
                    return null;
                }
                if (existing != null) {
                    existing.setName(name);
                    existing.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    return existing;
                } else {
                    return new ItemCategory(null, name);
                }
            }
            return null;
        });

        dlg.showAndWait().ifPresent(cat -> {
            app.getData().saveCategory(cat);
            refresh();
            Toast.show(app.getRootPane(), "Category Saved", "Category '" + cat.getName() + "' saved.", false);
        });
    }

    private void confirmDelete(ItemCategory cat) {
        long assignedCount = app.getData().getAllItems().stream()
            .filter(it -> cat.getId().equalsIgnoreCase(it.getCategoryId()) || cat.getName().equalsIgnoreCase(it.getCategoryName()))
            .count();

        String warnMsg = assignedCount > 0
            ? "There are " + assignedCount + " items assigned to this category. Deleting it will leave those items uncategorized."
            : "Are you sure you want to delete this category?";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Category");
        alert.setHeaderText("Delete '" + cat.getName() + "'?");
        alert.setContentText(warnMsg);
        DialogHelper.styleDialog(alert);

        alert.showAndWait().ifPresent(ans -> {
            if (ans == ButtonType.OK) {
                app.getData().deleteCategory(cat.getId());
                refresh();
                Toast.show(app.getRootPane(), "Category Deleted", "Category removed successfully.", false);
            }
        });
    }
}
