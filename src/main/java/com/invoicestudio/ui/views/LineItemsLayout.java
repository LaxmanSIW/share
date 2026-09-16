package com.invoicestudio.ui.views;

import com.invoicestudio.model.TableColumn;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.List;
import java.util.Locale;

/**
 * Stateless layout rules for the bill line-items strip (skill rule 5.2
 * role 3). Extracted verbatim from {@link CreateBillView}; behavior unchanged.
 */
final class LineItemsLayout {

    private LineItemsLayout() {}

    static boolean isDescriptionKey(String key) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT).trim();
        return "desc".equals(k) || "description".equals(k) || "name".equals(k) || "item_name".equals(k);
    }

    /** Fixed on-screen width for a column's editor control (mm-scaled by caller). */
    static double columnControlWidth(TableColumn col) {
        if (col == null || col.getKey() == null) return 60;
        String k = col.getKey().toLowerCase(Locale.ROOT).trim();
        return switch (k) {
            case "sr", "index", "#", "s_no", "sno" -> 30;
            case "hsn", "sac", "hsn_sac" -> 65;
            case "qty", "quantity" -> 50;
            case "unit" -> 55;
            case "rate", "price", "unit_price" -> 65;
            case "gst", "tax" -> 45;
            case "disc", "discount" -> 45;
            case "taxable", "taxable_value" -> 70;
            case "amount", "total", "total_amount" -> 75;
            default -> {
                if (col.getWidth() > 0) {
                    yield Math.max(50.0, Math.min(130.0, col.getWidth() * 5.5));
                }
                yield 70.0;
            }
        };
    }

    /** Right/center/left alignment rule shared by header and row cells. */
    static Pos alignmentFor(TableColumn col, String key) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT).trim();
        if ("right".equalsIgnoreCase(col.getAlign()) || "qty".equals(k) || "rate".equals(k)
                || "gst".equals(k) || "disc".equals(k) || "taxable".equals(k) || "amount".equals(k)) {
            return Pos.CENTER_RIGHT;
        }
        if ("center".equalsIgnoreCase(col.getAlign()) || "sr".equals(k) || "hsn".equals(k) || "unit".equals(k)) {
            return Pos.CENTER;
        }
        return Pos.CENTER_LEFT;
    }

    /** Builds the column-header strip above the item rows. */
    static HBox buildHeader(List<TableColumn> cols) {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("items-header-strip");

        // Catalog pick spacer (matches pick item button)
        Label hPick = spacer();
        header.getChildren().add(hPick);

        boolean descAdded = false;

        for (TableColumn col : cols) {
            String k = col.getKey() != null ? col.getKey().toLowerCase(Locale.ROOT).trim() : "";
            String labelText = col.getLabel() != null && !col.getLabel().isBlank() ? col.getLabel().toUpperCase() : k.toUpperCase();
            Label lbl = new Label(labelText);

            if (isDescriptionKey(k)) {
                descAdded = true;
                lbl.setMinWidth(140);
                lbl.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(lbl, Priority.ALWAYS);
            } else {
                double w = columnControlWidth(col);
                lbl.setPrefWidth(w);
                lbl.setMinWidth(w);
                lbl.setMaxWidth(w);
                lbl.setAlignment(alignmentFor(col, k));
            }

            header.getChildren().add(lbl);

            // Catalog save spacer next to description column
            if (isDescriptionKey(k)) {
                header.getChildren().add(spacer());
            }
        }

        if (!descAdded) {
            Label hDesc = new Label("ITEM DESCRIPTION");
            hDesc.setMinWidth(140);
            hDesc.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(hDesc, Priority.ALWAYS);
            header.getChildren().add(1, hDesc);

            header.getChildren().add(2, spacer());
        }

        // Delete button spacer
        header.getChildren().add(spacer());

        return header;
    }

    private static Label spacer() {
        Label s = new Label("");
        s.setPrefWidth(30);
        s.setMinWidth(30);
        s.setMaxWidth(30);
        return s;
    }
}
