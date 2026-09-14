package com.invoicestudio.ui.views;

import com.invoicestudio.db.LabelPrintHistoryDao;
import com.invoicestudio.model.LabelPrintHistory;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import com.invoicestudio.ui.UiTheme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Label Print History — an info-only log of every Bulk Label Print run:
 * when it happened, which template/printer, the label size and how many
 * labels were produced. Reachable from Catalog → Label Print History.
 */
public class LabelHistoryView extends VBox {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final StudioApp app;
    private final LabelPrintHistoryDao dao;
    private final VBox listContainer = new VBox();
    private final Label countLbl = new Label("");

    public LabelHistoryView(StudioApp app) {
        this.app = app;
        this.dao = new LabelPrintHistoryDao(app.getDb());

        setSpacing(20);
        setPadding(new Insets(24));
        getStyleClass().add("view-page");

        buildHeader();
        buildList();
        getChildren().add(listContainer);

        reload();
    }

    private void buildHeader() {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🏷");
        icon.getStyleClass().addAll("icon-accent", "icon-lg");
        Label title = new Label("Label Print History");
        title.getStyleClass().add("view-title");
        titleRow.getChildren().addAll(icon, title);

        Label sub = new Label("Every Bulk Label Print run — when, which template & printer, and how many labels were produced. Info only.");
        sub.getStyleClass().add("view-subtitle");
        sub.setWrapText(true);
        titleBox.getChildren().addAll(titleRow, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearBtn = new Button("Clear History");
        clearBtn.getStyleClass().addAll("button-sm", "button-secondary");
        clearBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete ALL label print history entries?", ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Clear History");
            confirm.setHeaderText("Clear Label Print History");
            DialogHelper.styleDialog(confirm, 440, 200);
            confirm.showAndWait().ifPresent(res -> {
                if (res == ButtonType.YES) {
                    int n = dao.clearAll();
                    Toast.show(app.getRootPane(), "History Cleared", n + " entries removed.", false);
                    reload();
                }
            });
        });

        header.getChildren().addAll(titleBox, spacer, countLbl, clearBtn);
        getChildren().add(header);
    }

    private void buildList() {
        listContainer.setSpacing(20);
    }

    public void reload() {
        listContainer.getChildren().clear();
        List<LabelPrintHistory> entries = dao.getAll();
        countLbl.setText(entries.size() + " runs");

        if (entries.isEmpty()) {
            VBox card = UiTheme.card(14);
            card.getChildren().add(UiTheme.emptyState("🏷",
                    "No label printing yet",
                    "Open a template, switch on Barcode Mode and run a Bulk Print — runs will be logged here."));
            listContainer.getChildren().add(card);
            return;
        }

        VBox card = UiTheme.card(14);
        VBox rows = new VBox(0);

        // Header row
        HBox th = new HBox(16);
        th.setAlignment(Pos.CENTER_LEFT);
        th.getStyleClass().add("table-header-row");
        th.setPadding(new Insets(10, 16, 10, 16));
        th.getChildren().addAll(
                headCell("WHEN", 170),
                headCell("TEMPLATE", 180),
                headCell("PRINTER", 160),
                headCell("LABEL SIZE", 120),
                headCell("COLS", 50),
                headCell("PAGES", 60),
                headCell("LABELS", 70),
                headCell("PRINT QUEUE", 0),
                headCell("", 44));
        rows.getChildren().add(th);

        for (LabelPrintHistory h : entries) {
            HBox row = new HBox(16);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("table-data-row");
            row.setPadding(new Insets(10, 16, 10, 16));

            row.getChildren().add(cell(formatWhen(h.getCreatedAt()), 170));

            Label name = cell(h.getTemplateName(), 180);
            name.getStyleClass().add("table-cell-title");
            row.getChildren().add(name);

            row.getChildren().add(cell(h.getPrinterName(), 160));
            row.getChildren().add(cell(String.format(java.util.Locale.US, "%.0f × %.0f mm",
                    h.getLabelWidth(), h.getLabelHeight()), 120));
            row.getChildren().add(cell(String.valueOf(h.getColumns()), 50));
            row.getChildren().add(cell(String.valueOf(h.getPages()), 60));

            Label labels = cell(String.valueOf(h.getLabels()), 70);
            labels.getStyleClass().add("pill-success");
            row.getChildren().add(labels);

            Label summary = cell(h.getSummary(), 0);
            summary.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(summary, Priority.ALWAYS);
            summary.setWrapText(true);
            summary.setTextAlignment(TextAlignment.LEFT);
            summary.setTooltip(new Tooltip(summary.getText()));
            row.getChildren().add(summary);

            Button del = new Button("🗑");
            del.getStyleClass().add("button-icon-subtle");
            del.setTooltip(new Tooltip("Delete this entry"));
            del.setOnAction(e -> {
                dao.delete(h.getId());
                reload();
            });
            HBox act = new HBox(del);
            act.setAlignment(Pos.CENTER_RIGHT);
            act.setPrefWidth(44);
            row.getChildren().add(act);

            rows.getChildren().add(row);
        }

        card.getChildren().add(rows);
        listContainer.getChildren().add(card);
    }

    private Label headCell(String text, int width) {
        return headCell(text, width, false);
    }

    private Label headCell(String text, int width, boolean grow) {
        Label l = new Label(text);
        l.getStyleClass().add("table-th");
        if (width > 0) { l.setPrefWidth(width); l.setMinWidth(width); l.setMaxWidth(width); }
        if (grow) { l.setMaxWidth(Double.MAX_VALUE); }
        return l;
    }

    private Label cell(String text, int width) {
        Label l = new Label(text != null ? text : "");
        l.getStyleClass().add("table-cell-title");
        if (width > 0) { l.setPrefWidth(width); l.setMinWidth(width); l.setMaxWidth(width); }
        return l;
    }

    private String formatWhen(String iso) {
        try {
            LocalDateTime t = LocalDateTime.parse(iso.substring(0, Math.min(iso.length(), 23)));
            return t.format(TS_FMT);
        } catch (Exception e) {
            try {
                return java.time.Instant.parse(iso)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(TS_FMT);
            } catch (Exception e2) {
                return iso != null ? iso : "";
            }
        }
    }
}
