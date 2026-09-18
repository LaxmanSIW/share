package com.invoicestudio.ui.chat;

import com.invoicestudio.service.AiChatClient;
import com.invoicestudio.service.ModelCatalog;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.IconHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Consumer;

/**
 * Desktop-themed AI Model Picker Modal.
 * Replaces the unstyled default dialog with a rich, dark-themed catalogue viewer
 * featuring real-time model filtering, context window chips, active badges, and
 * one-click selection.
 */
public final class ChatbotModelPickerDialog {

    private static final String GOLD = "#D9A13B";
    private static final String BG_WINDOW = "#0B0F17";
    private static final String BORDER_COLOR = "#222F43";

    private final Stage stage = new Stage();
    private final TextField searchField = new TextField();
    private final ListView<ModelCatalog.ModelInfo> listView = new ListView<>();
    private final Label countLabel = new Label();
    private final Button selectBtn = new Button("Use Selected Model");

    public ChatbotModelPickerDialog(Window owner, String provider, List<ModelCatalog.ModelInfo> models,
                                   String currentModelId, Consumer<ModelCatalog.ModelInfo> onSelect) {
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        String provLabel = AiChatClient.providerLabel(provider);
        stage.setTitle("Select AI Model — " + provLabel);
        stage.setWidth(640);
        stage.setHeight(520);
        stage.setMinWidth(520);
        stage.setMinHeight(380);

        // 1. Header Bar
        Label icon = new Label();
        icon.setGraphic(IconHelper.getIcon(IconHelper.ICON_SPARKLES, 18, GOLD));

        Label title = new Label("Select AI Model");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #F1F5F9;");

        Label providerBadge = new Label(provLabel);
        providerBadge.setStyle("-fx-font-size: 10.5px; -fx-text-fill: " + GOLD + "; "
                + "-fx-background-color: rgba(217, 161, 59, 0.12); -fx-border-color: rgba(217, 161, 59, 0.35); "
                + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2 7 2 7; -fx-font-weight: bold;");

        HBox titleRow = new HBox(8, icon, title, providerBadge);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label subtitle = new Label("Live catalogue from " + provLabel + ". Pick a model for assistant reasoning and business tool execution.");
        subtitle.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #8A9BAE;");
        subtitle.setWrapText(true);

        VBox header = new VBox(4, titleRow, subtitle);
        header.setPadding(new Insets(14, 16, 10, 16));

        // 2. Search Box
        searchField.setPromptText("🔍  Search models (e.g. flash, lite, pro, 3.5)...");
        searchField.setStyle("-fx-background-color: #141D2B; -fx-border-color: #273449; "
                + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #E2E8F0; "
                + "-fx-prompt-text-fill: #64748B; -fx-padding: 8 12 8 12; -fx-font-size: 13px;");

        ObservableList<ModelCatalog.ModelInfo> masterList = FXCollections.observableArrayList(models);
        FilteredList<ModelCatalog.ModelInfo> filteredList = new FilteredList<>(masterList, p -> true);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = (newVal == null) ? "" : newVal.trim().toLowerCase();
            filteredList.setPredicate(mi -> {
                if (query.isEmpty()) return true;
                return (mi.displayName() != null && mi.displayName().toLowerCase().contains(query))
                        || (mi.id() != null && mi.id().toLowerCase().contains(query))
                        || (mi.description() != null && mi.description().toLowerCase().contains(query));
            });
            updateCount(filteredList.size(), masterList.size());
        });

        VBox searchBox = new VBox(searchField);
        searchBox.setPadding(new Insets(0, 16, 8, 16));

        // 3. Model List View
        listView.setItems(filteredList);
        listView.setStyle("-fx-background-color: #0A0E17; -fx-control-inner-background: #0A0E17; "
                + "-fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 8; -fx-background-radius: 8; "
                + "-fx-padding: 4;");
        VBox.setVgrow(listView, Priority.ALWAYS);

        final String activeId = currentModelId == null ? "" : currentModelId.trim();
        final String defaultId = AiChatClient.defaultModel(provider);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ModelCatalog.ModelInfo mi, boolean empty) {
                super.updateItem(mi, empty);
                if (empty || mi == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                boolean isActive = mi.id().equalsIgnoreCase(activeId)
                        || (activeId.isEmpty() && mi.id().equalsIgnoreCase(defaultId));

                // Left details: Name + technical ID
                Label nameLbl = new Label(mi.displayName());
                nameLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; "
                        + "-fx-text-fill: " + (isActive ? GOLD : "#F1F5F9") + ";");

                Label idLbl = new Label(mi.id());
                idLbl.setStyle("-fx-font-size: 11px; -fx-font-family: Consolas, 'Courier New', monospace; "
                        + "-fx-text-fill: #64748B;");

                VBox leftBox = new VBox(2, nameLbl, idLbl);
                leftBox.setAlignment(Pos.CENTER_LEFT);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                // Right chips: Context window + Active badge
                HBox rightBox = new HBox(6);
                rightBox.setAlignment(Pos.CENTER_RIGHT);

                if (mi.inputTokenLimit() > 0) {
                    long k = mi.inputTokenLimit() / 1000;
                    String contextText = k >= 1000 ? (k / 1000) + "M context" : k + "K context";
                    Label ctxLbl = new Label(contextText);
                    ctxLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #94A3B8; "
                            + "-fx-background-color: #1A2333; -fx-border-color: #273449; "
                            + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2 6 2 6;");
                    rightBox.getChildren().add(ctxLbl);
                }

                if (isActive) {
                    Label activeChip = new Label("Current");
                    activeChip.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + GOLD + "; "
                            + "-fx-background-color: rgba(217, 161, 59, 0.15); -fx-border-color: " + GOLD + "; "
                            + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2 6 2 6;");
                    rightBox.getChildren().add(activeChip);
                }

                HBox row = new HBox(8, leftBox, spacer, rightBox);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8, 12, 8, 12));

                setGraphic(row);
                setText(null);

                // Styling updates based on selection
                if (isSelected()) {
                    setStyle("-fx-background-color: #152238; -fx-background-radius: 6; "
                            + "-fx-border-color: " + GOLD + "; -fx-border-width: 0 0 0 3;");
                } else {
                    setStyle("-fx-background-color: transparent; -fx-background-radius: 6;");
                }
            }
        });

        // Double click to select
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ModelCatalog.ModelInfo sel = listView.getSelectionModel().getSelectedItem();
                if (sel != null && onSelect != null) {
                    onSelect.accept(sel);
                    stage.close();
                }
            }
        });

        // Pre-select active model if present
        for (int i = 0; i < filteredList.size(); i++) {
            ModelCatalog.ModelInfo mi = filteredList.get(i);
            if (mi.id().equalsIgnoreCase(activeId) || (activeId.isEmpty() && mi.id().equalsIgnoreCase(defaultId))) {
                listView.getSelectionModel().select(i);
                listView.scrollTo(i);
                break;
            }
        }

        // 4. Footer Bar
        updateCount(filteredList.size(), masterList.size());
        countLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #64748B;");

        Region footerSpring = new Region();
        HBox.setHgrow(footerSpring, Priority.ALWAYS);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #1A2333; -fx-text-fill: #CBD5E1; "
                + "-fx-border-color: #2E3C52; -fx-border-radius: 6; -fx-background-radius: 6; "
                + "-fx-padding: 7 16 7 16; -fx-font-size: 12px; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> stage.close());

        selectBtn.setStyle("-fx-background-color: " + GOLD + "; -fx-text-fill: #0B0E13; "
                + "-fx-font-weight: bold; -fx-border-radius: 6; -fx-background-radius: 6; "
                + "-fx-padding: 7 18 7 18; -fx-font-size: 12px; -fx-cursor: hand;");
        selectBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
        selectBtn.setOnAction(e -> {
            ModelCatalog.ModelInfo sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null && onSelect != null) {
                onSelect.accept(sel);
                stage.close();
            }
        });

        HBox footer = new HBox(10, countLabel, footerSpring, cancelBtn, selectBtn);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(12, 16, 14, 16));

        // Assemble root layout
        VBox root = new VBox(header, searchBox, listView, footer);
        root.setStyle("-fx-background-color: " + BG_WINDOW + ";");
        root.setPadding(new Insets(0, 8, 0, 8));

        Scene scene = new Scene(root);
        DialogHelper.styleScene(scene);

        // Escape key closes modal, Enter selects model
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.close();
            } else if (e.getCode() == KeyCode.ENTER && !selectBtn.isDisable()) {
                selectBtn.fire();
            }
        });

        stage.setScene(scene);
        DialogHelper.applyAppIcon(stage);
    }

    private void updateCount(int showing, int total) {
        if (showing == total) {
            countLabel.setText(total + " models available");
        } else {
            countLabel.setText("Showing " + showing + " of " + total + " models");
        }
    }

    public void showAndWait() {
        stage.showAndWait();
    }
}
