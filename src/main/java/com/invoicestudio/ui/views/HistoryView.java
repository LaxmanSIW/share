package com.invoicestudio.ui.views;

import com.invoicestudio.db.BillDao;
import com.invoicestudio.db.SettingsDao;
import com.invoicestudio.db.TemplateDao;
import com.invoicestudio.model.*;
import com.invoicestudio.service.BillingService;
import com.invoicestudio.service.CsvService;
import com.invoicestudio.service.PdfExportService;
import com.invoicestudio.service.PrintingService;
import com.invoicestudio.ui.BillPreviewPane;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public class HistoryView extends BorderPane {

    private final StudioApp app;
    private final BillDao billDao;
    private final TemplateDao templateDao;
    private final SettingsDao settingsDao;

    private final TableView<Bill> table = new TableView<>();
    private FilteredList<Bill> filteredBills;

    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> docTypeFilter = new ComboBox<>();
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();

    public HistoryView(StudioApp app) {
        this.app = app;
        this.billDao = new BillDao(app.getDb());
        this.templateDao = new TemplateDao(app.getDb());
        this.settingsDao = new SettingsDao(app.getDb());

        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        setTop(createFilterBar());
        setCenter(createTableArea());

        refresh();
    }

    public void refresh() {
        List<Bill> bills = billDao.getAllBills();
        filteredBills = new FilteredList<>(FXCollections.observableArrayList(bills), b -> true);
        table.setItems(filteredBills);
        applyFilter();
    }

    private Node createFilterBar() {
        VBox topBox = new VBox(14);
        topBox.setPadding(new Insets(0, 0, 16, 0));

        HBox bar1 = new HBox(16);
        bar1.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Billing & Invoice History");
        title.getStyleClass().add("heading-l");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button exportCsvBtn = new Button("Export CSV");
        exportCsvBtn.getStyleClass().addAll("button-sm", "button-secondary");
        exportCsvBtn.setTooltip(new Tooltip("Export filtered invoices list to CSV"));
        exportCsvBtn.setOnAction(e -> exportFilteredCsv());

        Button newBillBtn = new Button("+ Create Bill");
        newBillBtn.getStyleClass().addAll("gold-btn");
        newBillBtn.setTooltip(new Tooltip("Create New Invoice or Bill"));
        newBillBtn.setOnAction(e -> app.showCreateBill(null, null));

        bar1.getChildren().addAll(title, sp, exportCsvBtn, newBillBtn);

        // Filter Controls row
        HBox filters = new HBox(12);
        filters.setAlignment(Pos.CENTER_LEFT);

        searchField.setPromptText("Search by Bill No, Buyer Name, Phone...");
        searchField.setPrefWidth(260);
        searchField.textProperty().addListener((obs, o, v) -> applyFilter());

        statusFilter.setItems(FXCollections.observableArrayList("All Statuses", "Unpaid", "Paid", "Cancelled"));
        statusFilter.setValue("All Statuses");
        statusFilter.setOnAction(e -> applyFilter());

        docTypeFilter.setItems(FXCollections.observableArrayList("All Types", "Invoice", "Proforma", "Quotation", "Challan", "Credit Note"));
        docTypeFilter.setValue("All Types");
        docTypeFilter.setOnAction(e -> applyFilter());

        fromPicker.setPromptText("From Date");
        fromPicker.setOnAction(e -> applyFilter());

        toPicker.setPromptText("To Date");
        toPicker.setOnAction(e -> applyFilter());

        Button clearFilter = new Button("Clear");
        clearFilter.getStyleClass().addAll("button-sm", "button-secondary");
        clearFilter.setTooltip(new Tooltip("Reset search and filter fields"));
        clearFilter.setOnAction(e -> {
            searchField.clear();
            statusFilter.setValue("All Statuses");
            docTypeFilter.setValue("All Types");
            fromPicker.setValue(null);
            toPicker.setValue(null);
            applyFilter();
        });

        filters.getChildren().addAll(searchField, statusFilter, docTypeFilter, fromPicker, toPicker, clearFilter);

        topBox.getChildren().addAll(bar1, filters);
        return topBox;
    }

    private Node createTableArea() {
        table.getStyleClass().add("table-view");

        TableColumn<Bill, String> colNo = new TableColumn<>("Bill No");
        colNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBillNo()));
        colNo.setPrefWidth(110);

        TableColumn<Bill, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));
        colDate.setPrefWidth(95);

        TableColumn<Bill, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDocType().getLabel()));
        colType.setPrefWidth(90);

        TableColumn<Bill, String> colBuyer = new TableColumn<>("Buyer");
        colBuyer.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getVariables().getOrDefault("buyer_name", "—")));
        colBuyer.setPrefWidth(180);

        TableColumn<Bill, String> colTotal = new TableColumn<>("Grand Total");
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(String.format("₹%.2f", d.getValue().getTotals().getGrandTotal())));
        colTotal.setPrefWidth(110);

        TableColumn<Bill, String> colDue = new TableColumn<>("Due Balance");
        colDue.setCellValueFactory(d -> {
            Bill b = d.getValue();
            double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (paid == 0 && b.getStatus() == BillStatus.PAID) paid = b.getTotals().getGrandTotal();
            double due = Math.max(0, b.getTotals().getGrandTotal() - paid);
            if (b.getStatus() == BillStatus.CANCELLED) due = 0;
            return new SimpleStringProperty(String.format("₹%.2f", due));
        });
        colDue.setPrefWidth(100);

        TableColumn<Bill, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().getLabel()));
        colStatus.setPrefWidth(85);

        TableColumn<Bill, String> colPrints = new TableColumn<>("Prints");
        colPrints.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getPrintCount())));
        colPrints.setPrefWidth(60);

        TableColumn<Bill, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(300);
        colActions.setCellFactory(col -> new TableCell<Bill, Void>() {
            private final HBox box = new HBox(6);
            private final Button viewBtn = new Button("View");
            private final Button printBtn = new Button("Print");
            private final Button pdfBtn = new Button("PDF");
            private final Button payBtn = new Button("Pay");
            private final MenuButton moreBtn = new MenuButton("•••");

            {
                box.setAlignment(Pos.CENTER_LEFT);
                viewBtn.getStyleClass().addAll("button-sm", "button-secondary");
                viewBtn.setTooltip(new Tooltip("Preview document"));
                printBtn.getStyleClass().addAll("button-sm", "button-secondary");
                printBtn.setTooltip(new Tooltip("Print document copies"));
                pdfBtn.getStyleClass().addAll("button-sm", "button-secondary");
                pdfBtn.setTooltip(new Tooltip("Export to PDF"));
                payBtn.getStyleClass().addAll("button-sm", "button-secondary");
                payBtn.setTooltip(new Tooltip("Record payment receipt"));
                moreBtn.getStyleClass().addAll("button-sm", "button-secondary");
                moreBtn.setTooltip(new Tooltip("More bill actions"));

                viewBtn.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) showBillPreviewDialog(b);
                });
                printBtn.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) showPrintCopiesDialog(b);
                });
                pdfBtn.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) exportBillToPdf(b);
                });
                payBtn.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) showRecordPaymentDialog(b);
                });

                MenuItem editItem = new MenuItem("Edit Bill");
                editItem.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) app.editBill(b);
                });

                MenuItem dupItem = new MenuItem("Duplicate Bill");
                dupItem.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) app.duplicateBill(b);
                });

                MenuItem convItem = new MenuItem("Convert to Tax Invoice");
                convItem.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) app.convertBill(b);
                });

                MenuItem repeatItem = new MenuItem("Repeat Next (Recurring)");
                repeatItem.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) app.repeatBill(b);
                });

                MenuItem waItem = new MenuItem("Share on WhatsApp");
                waItem.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) shareOnWhatsApp(b);
                });

                MenuItem receiptItem = new MenuItem("Print Receipt Document");
                receiptItem.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) printPaymentReceipt(b);
                });

                MenuItem statusPaid = new MenuItem("Mark as Paid");
                statusPaid.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) {
                        b.setStatus(BillStatus.PAID);
                        billDao.saveBill(b);
                        refresh();
                    }
                });

                MenuItem statusCancel = new MenuItem("Cancel Bill");
                statusCancel.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) {
                        b.setStatus(BillStatus.CANCELLED);
                        billDao.saveBill(b);
                        refresh();
                    }
                });

                MenuItem delItem = new MenuItem("Delete Bill");
                delItem.setStyle("-fx-text-fill: #EF4444;");
                delItem.setOnAction(e -> {
                    Bill b = getTableRow() != null ? getTableRow().getItem() : null;
                    if (b != null) {
                        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete bill " + b.getBillNo() + "?", ButtonType.YES, ButtonType.NO);
                        DialogHelper.styleDialog(a);
                        a.showAndWait().ifPresent(ans -> {
                            if (ans == ButtonType.YES) {
                                billDao.deleteBill(b.getId());
                                refresh();
                                Toast.show(app.getRootPane(), "Bill Deleted", b.getBillNo() + " removed.", false);
                            }
                        });
                    }
                });

                moreBtn.getItems().addAll(editItem, dupItem, convItem, repeatItem, new SeparatorMenuItem(), waItem, receiptItem, new SeparatorMenuItem(), statusPaid, statusCancel, new SeparatorMenuItem(), delItem);
                box.getChildren().addAll(viewBtn, printBtn, pdfBtn, payBtn, moreBtn);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(box);
                }
            }
        });

        table.getColumns().addAll(colNo, colDate, colType, colBuyer, colTotal, colDue, colStatus, colPrints, colActions);
        return table;
    }

    private void applyFilter() {
        if (filteredBills == null) return;
        String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String status = statusFilter.getValue();
        String docType = docTypeFilter.getValue();
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();

        filteredBills.setPredicate(b -> {
            if (!query.isEmpty()) {
                boolean matchNo = b.getBillNo() != null && b.getBillNo().toLowerCase().contains(query);
                boolean matchBuyer = b.getVariables().getOrDefault("buyer_name", "").toLowerCase().contains(query);
                boolean matchPhone = b.getVariables().getOrDefault("buyer_phone", "").toLowerCase().contains(query);
                if (!matchNo && !matchBuyer && !matchPhone) return false;
            }
            if (status != null && !status.equals("All Statuses")) {
                if (!b.getStatus().getLabel().equalsIgnoreCase(status)) return false;
            }
            if (docType != null && !docType.equals("All Types")) {
                if (!b.getDocType().getLabel().equalsIgnoreCase(docType)) return false;
            }
            if (from != null && b.getDate() != null) {
                try {
                    if (LocalDate.parse(b.getDate()).isBefore(from)) return false;
                } catch (Exception ignored) {}
            }
            if (to != null && b.getDate() != null) {
                try {
                    if (LocalDate.parse(b.getDate()).isAfter(to)) return false;
                } catch (Exception ignored) {}
            }
            return true;
        });
    }

    private void showBillPreviewDialog(Bill bill) {
        if (bill == null) return;
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Invoice Preview — " + bill.getBillNo());
        dlg.setHeaderText(bill.getDocType().getTitle() + " • " + bill.getBillNo() + " (Date: " + bill.getDate() + ")");

        Settings settings = settingsDao.getSettings();
        Template template = templateDao.getTemplateById(bill.getTemplateId());
        if (template == null) template = PresetTemplates.buildClassic();

        final Template finalTemplate = template;
        final double[] currentZoom = {0.85};

        BillPreviewPane preview = new BillPreviewPane();
        preview.setZoom(currentZoom[0]);
        preview.render(finalTemplate, bill, settings, 0, 1);

        // Zoom & Quick Actions Toolbar
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 14, 8, 14));
        toolbar.setStyle("-fx-background-color: #0E131A; -fx-border-color: #1E2738; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label zoomTitle = new Label("Zoom:");
        zoomTitle.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px; -fx-font-weight: bold;");

        Button zoomOutBtn = new Button("−");
        zoomOutBtn.getStyleClass().add("secondary-button");
        zoomOutBtn.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 3 10; -fx-cursor: hand;");

        Label zoomLabel = new Label("85%");
        zoomLabel.setStyle("-fx-text-fill: #F5C868; -fx-font-size: 12px; -fx-font-weight: bold; -fx-min-width: 44px; -fx-alignment: center;");

        Button zoomInBtn = new Button("+");
        zoomInBtn.getStyleClass().add("secondary-button");
        zoomInBtn.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 3 10; -fx-cursor: hand;");

        Button zoomResetBtn = new Button("100%");
        zoomResetBtn.getStyleClass().add("secondary-button");
        zoomResetBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8; -fx-cursor: hand;");

        Button zoomFitBtn = new Button("Fit Width");
        zoomFitBtn.getStyleClass().add("secondary-button");
        zoomFitBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8; -fx-cursor: hand;");

        Runnable updateZoomUI = () -> {
            zoomLabel.setText((int) Math.round(currentZoom[0] * 100) + "%");
            preview.setZoom(currentZoom[0]);
        };

        zoomInBtn.setOnAction(e -> {
            currentZoom[0] = Math.min(2.5, currentZoom[0] + 0.15);
            updateZoomUI.run();
        });

        zoomOutBtn.setOnAction(e -> {
            currentZoom[0] = Math.max(0.3, currentZoom[0] - 0.15);
            updateZoomUI.run();
        });

        zoomResetBtn.setOnAction(e -> {
            currentZoom[0] = 1.0;
            updateZoomUI.run();
        });

        // Copy selector
        Label copyLbl = new Label("Copy:");
        copyLbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px; -fx-font-weight: bold;");
        ComboBox<String> copyCombo = new ComboBox<>(FXCollections.observableArrayList("Original", "Duplicate", "Triplicate"));
        copyCombo.setValue("Original");
        copyCombo.setStyle("-fx-font-size: 11px;");
        copyCombo.setOnAction(e -> {
            int idx = copyCombo.getSelectionModel().getSelectedIndex();
            preview.render(finalTemplate, bill, settings, idx, 1);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button printBtn = new Button("🖨 Print Invoice");
        printBtn.getStyleClass().add("primary-button");
        printBtn.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 5 14; -fx-cursor: hand;");
        printBtn.setOnAction(e -> {
            int copies = copyCombo.getSelectionModel().getSelectedIndex() + 1;
            boolean success = PrintingService.printNode(preview, app.getPrimaryStage(), copies, bill.getBillNo());
            if (success) {
                billDao.incrementPrintCount(bill.getId());
                refresh();
                Toast.show(app.getRootPane(), "Print Sent", "Sent " + copies + " cop" + (copies == 1 ? "y" : "ies") + " to printer.", false);
            }
        });

        Button exportPdfBtn = new Button("📥 Export PDF");
        exportPdfBtn.getStyleClass().add("secondary-button");
        exportPdfBtn.setStyle("-fx-font-size: 12px; -fx-padding: 5 14; -fx-cursor: hand;");
        exportPdfBtn.setOnAction(e -> exportBillToPdf(bill));

        toolbar.getChildren().addAll(
                zoomTitle, zoomOutBtn, zoomLabel, zoomInBtn, zoomResetBtn, zoomFitBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                copyLbl, copyCombo,
                spacer,
                printBtn, exportPdfBtn
        );

        // Preview Wrapper
        StackPane previewWrapper = new StackPane(preview);
        previewWrapper.setAlignment(Pos.TOP_CENTER);
        previewWrapper.setStyle("-fx-background-color: #0B0E13; -fx-padding: 18;");

        ScrollPane sp = new ScrollPane(previewWrapper);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: #0B0E13; -fx-border-color: #1E2738; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;");

        zoomFitBtn.setOnAction(e -> {
            double availWidth = sp.getWidth() - 60;
            double pageMm = finalTemplate.getPage() != null ? finalTemplate.getPage().getWidth() : 210.0;
            double pagePx = pageMm * 3.7795275591;
            if (pagePx > 0 && availWidth > 0) {
                currentZoom[0] = Math.max(0.3, Math.min(2.0, availWidth / pagePx));
                updateZoomUI.run();
            }
        });

        VBox container = new VBox(10, toolbar, sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        container.setPrefSize(960, 720);
        container.setMaxWidth(Double.MAX_VALUE);
        container.setMaxHeight(Double.MAX_VALUE);

        dlg.getDialogPane().setContent(container);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogHelper.styleDialog(dlg, 640, 480);
        dlg.showAndWait();
    }

    private void showPrintCopiesDialog(Bill bill) {
        if (bill == null) return;
        ChoiceDialog<Integer> dlg = new ChoiceDialog<>(1, 1, 2, 3);
        dlg.setTitle("Print Invoices");
        dlg.setHeaderText("Choose number of copies to print for " + bill.getBillNo() + ":");
        dlg.setContentText("Copies (1=Original, 2=Original+Duplicate, 3=Triplicate):");
        DialogHelper.styleDialog(dlg);

        dlg.showAndWait().ifPresent(copies -> {
            Settings settings = settingsDao.getSettings();
            Template template = templateDao.getTemplateById(bill.getTemplateId());
            if (template == null) template = PresetTemplates.buildClassic();

            BillPreviewPane preview = new BillPreviewPane();
            preview.render(template, bill, settings, 0, 1);

            boolean success = PrintingService.printNode(preview, app.getPrimaryStage(), copies, bill.getBillNo());
            if (success) {
                billDao.incrementPrintCount(bill.getId());
                refresh();
                Toast.show(app.getRootPane(), "Print Sent", "Sent " + copies + " cop" + (copies == 1 ? "y" : "ies") + " to printer.", false);
            }
        });
    }

    private void exportBillToPdf(Bill bill) {
        if (bill == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Invoice PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Document (*.pdf)", "*.pdf"));
        fc.setInitialFileName(bill.getBillNo() + ".pdf");
        File dest = fc.showSaveDialog(app.getPrimaryStage());
        if (dest != null) {
            try {
                Settings settings = settingsDao.getSettings();
                Template template = templateDao.getTemplateById(bill.getTemplateId());
                if (template == null) template = PresetTemplates.buildClassic();

                PdfExportService.exportBillPdf(bill, template, settings, dest, 1);
                Toast.show(app.getRootPane(), "PDF Exported", "Invoice saved to " + dest.getName(), false);
            } catch (Exception ex) {
                Toast.show(app.getRootPane(), "Export Failed", ex.getMessage(), true);
            }
        }
    }

    private void showRecordPaymentDialog(Bill bill) {
        if (bill == null) return;
        Dialog<BillPayment> dlg = new Dialog<>();
        dlg.setTitle("Record Payment Receipt");
        dlg.setHeaderText("Record payment against " + bill.getBillNo());

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));

        double paidSoFar = bill.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
        double remainingDue = Math.max(0, bill.getTotals().getGrandTotal() - paidSoFar);

        TextField amtField = new TextField(String.format("%.2f", remainingDue));
        DatePicker pDatePicker = new DatePicker(LocalDate.now());
        ComboBox<PaymentMethod> methodCombo = new ComboBox<>(FXCollections.observableArrayList(PaymentMethod.values()));
        methodCombo.setValue(PaymentMethod.UPI);
        TextField refField = new TextField();
        refField.setPromptText("Txn ID / UTR / Cheque No");
        TextField noteField = new TextField();

        g.add(new Label("Amount (₹):"), 0, 0); g.add(amtField, 1, 0);
        g.add(new Label("Date:"), 0, 1); g.add(pDatePicker, 1, 1);
        g.add(new Label("Method:"), 0, 2); g.add(methodCombo, 1, 2);
        g.add(new Label("Reference / Txn:"), 0, 3); g.add(refField, 1, 3);
        g.add(new Label("Notes:"), 0, 4); g.add(noteField, 1, 4);

        dlg.getDialogPane().setContent(g);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogHelper.styleDialog(dlg);

        dlg.setResultConverter(b -> {
            if (b == ButtonType.OK) {
                try {
                    double amt = Double.parseDouble(amtField.getText());
                    return new BillPayment(
                            "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                            pDatePicker.getValue().toString(),
                            amt,
                            methodCombo.getValue(),
                            refField.getText(),
                            noteField.getText()
                    );
                } catch (Exception ignored) {}
            }
            return null;
        });

        dlg.showAndWait().ifPresent(payment -> {
            bill.getPayments().add(payment);
            double totalPaid = bill.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (totalPaid >= bill.getTotals().getGrandTotal() - 0.01) {
                bill.setStatus(BillStatus.PAID);
                bill.setPaidAt(payment.getDate());
            }
            billDao.saveBill(bill);
            refresh();
            Toast.show(app.getRootPane(), "Payment Recorded", "Recorded payment of ₹" + payment.getAmount(), false);
        });
    }

    private void printPaymentReceipt(Bill bill) {
        if (bill == null) return;
        List<BillPayment> payments = bill.getPayments();
        BillPayment p = !payments.isEmpty() ? payments.get(payments.size() - 1) : new BillPayment("pay_1", bill.getDate(), bill.getTotals().getGrandTotal(), PaymentMethod.CASH, "", "");

        FileChooser fc = new FileChooser();
        fc.setTitle("Save Payment Receipt PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Document (*.pdf)", "*.pdf"));
        fc.setInitialFileName("Receipt-" + bill.getBillNo() + ".pdf");
        File dest = fc.showSaveDialog(app.getPrimaryStage());
        if (dest != null) {
            try {
                PdfExportService.exportReceiptPdf(bill, p, "RCP-" + bill.getBillNo(), settingsDao.getSettings(), dest);
                Toast.show(app.getRootPane(), "Receipt Saved", "Receipt PDF saved to " + dest.getName(), false);
            } catch (Exception ex) {
                Toast.show(app.getRootPane(), "Receipt Failed", ex.getMessage(), true);
            }
        }
    }

    private void shareOnWhatsApp(Bill bill) {
        if (bill == null) return;
        Settings settings = settingsDao.getSettings();
        String buyerPhone = bill.getVariables().getOrDefault("buyer_phone", "").replaceAll("[^0-9]", "");
        if (buyerPhone.length() == 10) buyerPhone = "91" + buyerPhone;

        String msg = String.format("*%s*\n%s — %s\nDate: %s\nBuyer: %s\nAmount: %s%.2f\nStatus: %s\nThank you for your business!",
                settings.getBusiness().getName(),
                bill.getDocType().getTitle(),
                bill.getBillNo(),
                bill.getDate(),
                bill.getVariables().getOrDefault("buyer_name", "Valued Customer"),
                settings.getCurrency(),
                bill.getTotals().getGrandTotal(),
                bill.getStatus().getLabel()
        );

        String url = "https://wa.me/" + buyerPhone + "?text=" + URLEncoder.encode(msg, StandardCharsets.UTF_8);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Toast.show(app.getRootPane(), "WhatsApp URL", url, false);
            }
        } catch (Exception ex) {
            Toast.show(app.getRootPane(), "WhatsApp Share", url, false);
        }
    }

    private void exportFilteredCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Bills to CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Spreadsheet (*.csv)", "*.csv"));
        fc.setInitialFileName("bills-export-" + LocalDate.now() + ".csv");
        File dest = fc.showSaveDialog(app.getPrimaryStage());
        if (dest != null) {
            try (FileWriter fw = new FileWriter(dest)) {
                fw.write(CsvService.exportBills(new ArrayList<>(filteredBills)));
                Toast.show(app.getRootPane(), "Export Successful", "Saved " + filteredBills.size() + " bills to " + dest.getName(), false);
            } catch (Exception ex) {
                Toast.show(app.getRootPane(), "Export Failed", ex.getMessage(), true);
            }
        }
    }
}
