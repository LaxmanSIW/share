package com.invoicestudio.ui.views;

import com.invoicestudio.db.SettingsDao;
import com.invoicestudio.db.TemplateDao;
import com.invoicestudio.db.VariableDao;
import com.invoicestudio.model.*;
import com.invoicestudio.model.TableColumn;
import com.invoicestudio.service.BarcodeService;
import com.invoicestudio.service.RenderContext;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import javafx.embed.swing.SwingFXUtils;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class TemplateDesigner extends BorderPane {

    private final StudioApp app;
    private final TemplateDao templateDao;
    private final SettingsDao settingsDao;
    private final VariableDao variableDao;

    private Template template;
    private TemplateElement selectedElement;
    private TemplateElement clipboardElement;
    private final Stack<String> undoStack = new Stack<>();
    private final Stack<String> redoStack = new Stack<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private final Pane canvas = new Pane();
    private final Pane gridPane = new Pane();
    private final Pane elementsPane = new Pane();
    private final Pane selectionPane = new Pane();
    private final Group scaleGroup = new Group(canvas);

    private double zoom = 0.9;
    private boolean snapToGrid = true;
    private boolean showGrid = true;
    private boolean isPanMode = false;
    private boolean isSpaceDown = false;
    private boolean isUpdatingLayersSelection = false;
    private boolean shiftWithMargins = true;

    private ScrollPane canvasScrollPane;
    private StackPane centerWrapper;

    private Button selectToolBtn;
    private Button panToolBtn;

    private final TextField nameField = new TextField();
    private final Label zoomLabel = new Label("90%");
    private final VBox propBox = new VBox(12);
    private final ListView<TemplateElement> layersList = new ListView<>();
    private final TabPane sideTabs = new TabPane();

    private static final double MM_PX = 3.7795275591; // ~96 DPI screen pixels per mm

    public TemplateDesigner(StudioApp app, Template template) {
        this.app = app;
        this.templateDao = new TemplateDao(app.getDb());
        this.settingsDao = new SettingsDao(app.getDb());
        this.variableDao = new VariableDao(app.getDb());
        this.template = template != null ? template : PresetTemplates.buildClassic();

        getStyleClass().add("bg-app");
        canvas.getStyleClass().add("bill-sheet-canvas");
        canvas.getChildren().addAll(gridPane, elementsPane, selectionPane);
        gridPane.setMouseTransparent(true);
        selectionPane.setPickOnBounds(false);

        setTop(createToolbar());

        SplitPane mainSplit = new SplitPane();
        mainSplit.setStyle("-fx-background-color: transparent;");
        Node canvasArea = createCanvasArea();
        Node sidebar = createSidebar();
        mainSplit.getItems().addAll(canvasArea, sidebar);
        mainSplit.setDividerPositions(0.68);
        SplitPane.setResizableWithParent(sidebar, false);
        setCenter(mainSplit);

        setupKeyboardShortcuts();
        saveState();
        refreshCanvas();
        updatePropertiesPanel();
        refreshLayersList();
    }

    private Node createToolbar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #0E131A; -fx-padding: 8 16; -fx-border-color: #232B38; -fx-border-width: 0 0 1 0;");

        Button backBtn = createToolbarBtn("← Back", "Return to Templates Directory", () -> app.showTemplates());

        nameField.setText(template.getName());
        nameField.setPrefWidth(180);
        nameField.setTooltip(new Tooltip("Template Name"));
        nameField.textProperty().addListener((obs, old, val) -> template.setName(val));

        Separator s1 = new Separator(javafx.geometry.Orientation.VERTICAL);

        // Element add buttons
        Button addText = createToolbarBtn("+ Text", "Add dynamic or static text label", () -> addElement(ElementType.TEXT));
        Button addImage = createToolbarBtn("+ Image", "Add business logo or graphic image", () -> addElement(ElementType.IMAGE));
        Button addTable = createToolbarBtn("+ Table", "Add line-item billing table with GST", () -> addElement(ElementType.TABLE));
        Button addLine = createToolbarBtn("+ Line", "Add dividing line (horizontal/vertical)", () -> addElement(ElementType.LINE));
        Button addRect = createToolbarBtn("+ Rect", "Add background rectangle or card", () -> addElement(ElementType.RECT));
        Button addQr = createToolbarBtn("+ QR Code", "Add dynamic UPI payment QR code", () -> addElement(ElementType.QRCODE));
        Button addBarcode = createToolbarBtn("+ Barcode", "Add Code 128 invoice barcode", () -> addElement(ElementType.BARCODE));

        Separator s2 = new Separator(javafx.geometry.Orientation.VERTICAL);

        // Zoom Controls
        Button zoomOut = createToolbarBtn("−", "Zoom Out (Ctrl -)", () -> setZoom(zoom - 0.1));
        
        zoomLabel.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 11px; -fx-font-weight: bold; -fx-min-width: 42; -fx-alignment: CENTER;");
        zoomLabel.setTooltip(new Tooltip("Current Zoom Level"));

        Button zoomIn = createToolbarBtn("+", "Zoom In (Ctrl +)", () -> setZoom(zoom + 0.1));
        Button zoom100 = createToolbarBtn("100%", "Reset Zoom to 100% (Ctrl 0)", () -> setZoom(1.0));
        Button zoomFit = createToolbarBtn("Fit", "Fit Page in Canvas Viewport", () -> setZoom(0.85));

        CheckBox gridCb = new CheckBox("Grid");
        gridCb.setSelected(true);
        gridCb.setTooltip(new Tooltip("Toggle 1mm background alignment grid"));
        gridCb.selectedProperty().addListener((obs, old, val) -> {
            showGrid = val;
            refreshCanvas();
        });

        CheckBox snapCb = new CheckBox("Snap");
        snapCb.setSelected(true);
        snapCb.setTooltip(new Tooltip("Snap element positioning to 1mm grid"));
        snapCb.selectedProperty().addListener((obs, old, val) -> snapToGrid = val);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button pageBtn = createToolbarBtn("Page Settings", "Configure page dimensions, paper size & margins", this::showPageSettingsDialog);

        // Tool Mode: Select vs Pan
        selectToolBtn = createToolbarBtn("↖ Select", "Select & Move Tool (V)", () -> setPanMode(false));
        panToolBtn = createToolbarBtn("✋ Pan", "Pan Canvas Tool (H / Space)", () -> setPanMode(true));
        updateToolButtons();

        // Undo / Redo buttons
        Button undoBtn = createToolbarBtn("↶ Undo", "Undo last change (Ctrl Z)", this::undo);
        Button redoBtn = createToolbarBtn("↷ Redo", "Redo undone change (Ctrl Y)", this::redo);

        Button saveBtn = new Button("Save Template");
        saveBtn.getStyleClass().addAll("gold-btn");
        saveBtn.setTooltip(new Tooltip("Save template changes to database (Ctrl S)"));
        saveBtn.setOnAction(e -> saveTemplate());

        bar.getChildren().addAll(
                backBtn, nameField, s1,
                addText, addImage, addTable, addLine, addRect, addQr, addBarcode, s2,
                selectToolBtn, panToolBtn, undoBtn, redoBtn,
                zoomOut, zoomLabel, zoomIn, zoom100, zoomFit, gridCb, snapCb, sp,
                pageBtn, saveBtn
        );
        return bar;
    }

    private void setPanMode(boolean pan) {
        this.isPanMode = pan;
        updateToolButtons();
        Cursor cur = isPanMode ? Cursor.OPEN_HAND : Cursor.DEFAULT;
        if (canvasScrollPane != null) canvasScrollPane.setCursor(cur);
        if (centerWrapper != null) centerWrapper.setCursor(cur);
        canvas.setCursor(cur);
    }

    private void updateToolButtons() {
        if (selectToolBtn == null || panToolBtn == null) return;
        if (isPanMode) {
            selectToolBtn.setStyle("-fx-background-color: #1E2634; -fx-text-fill: #CBD5E1; -fx-font-size: 11px;");
            panToolBtn.setStyle("-fx-background-color: #D9A13B; -fx-text-fill: #000000; -fx-font-weight: bold; -fx-font-size: 11px;");
        } else {
            selectToolBtn.setStyle("-fx-background-color: #D9A13B; -fx-text-fill: #000000; -fx-font-weight: bold; -fx-font-size: 11px;");
            panToolBtn.setStyle("-fx-background-color: #1E2634; -fx-text-fill: #CBD5E1; -fx-font-size: 11px;");
        }
    }

    private Button createToolbarBtn(String text, String tooltip, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().addAll("button-sm", "button-secondary");
        if (tooltip != null) b.setTooltip(new Tooltip(tooltip));
        if (action != null) b.setOnAction(e -> action.run());
        return b;
    }

    private void setZoom(double z) {
        this.zoom = Math.max(0.3, Math.min(2.5, z));
        scaleGroup.setScaleX(zoom);
        scaleGroup.setScaleY(zoom);
        zoomLabel.setText((int) Math.round(zoom * 100) + "%");
        updateCenterWrapperSize();
    }

    private void updateCenterWrapperSize() {
        if (centerWrapper == null) return;
        PageConfig page = template.getPage();
        double pageW = page.getWidth() * MM_PX;
        double pageH = page.getHeight() * MM_PX;

        double scaledW = pageW * zoom;
        double scaledH = pageH * zoom;

        // Generous margin so canvas can pan horizontally and vertically freely past viewport bounds
        double margin = 260;
        double totalW = scaledW + (margin * 2);
        double totalH = scaledH + (margin * 2);

        centerWrapper.setPrefSize(totalW, totalH);
        centerWrapper.setMinSize(totalW, totalH);
    }

    private Node createCanvasArea() {
        canvasScrollPane = new ScrollPane();
        canvasScrollPane.setStyle("-fx-background: #0B0E13; -fx-background-color: #0B0E13;");
        canvasScrollPane.setFitToWidth(false);
        canvasScrollPane.setFitToHeight(false);
        canvasScrollPane.setPannable(false); // Do not let JavaFX override cursor to pan hand!
        canvasScrollPane.setCursor(Cursor.DEFAULT);

        centerWrapper = new StackPane(scaleGroup);
        centerWrapper.setStyle("-fx-background-color: #0B0E13;");
        centerWrapper.setCursor(Cursor.DEFAULT);

        canvasScrollPane.setContent(centerWrapper);
        updateCenterWrapperSize();

        // Deselect when clicking on empty canvas in Select mode
        canvas.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown() && !isPanMode && !isSpaceDown) {
                selectedElement = null;
                updateSelectionOverlay();
                updatePropertiesPanel();
                syncLayersListSelection();
            }
        });

        // Pan handling via Middle Mouse, Space+LeftDrag, Pan Tool drag, or Background drag
        final double[] panStart = new double[4];
        final boolean[] isPanning = new boolean[1];

        canvasScrollPane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            boolean middleBtn = e.getButton() == MouseButton.MIDDLE;
            boolean spaceDrag = isSpaceDown && e.getButton() == MouseButton.PRIMARY;
            boolean panToolDrag = isPanMode && e.getButton() == MouseButton.PRIMARY;
            boolean bgDrag = (e.getTarget() == centerWrapper) && e.getButton() == MouseButton.PRIMARY;

            if (middleBtn || spaceDrag || panToolDrag || bgDrag) {
                panStart[0] = e.getScreenX();
                panStart[1] = e.getScreenY();
                panStart[2] = canvasScrollPane.getHvalue();
                panStart[3] = canvasScrollPane.getVvalue();
                isPanning[0] = true;
                canvasScrollPane.setCursor(Cursor.CLOSED_HAND);
                canvas.setCursor(Cursor.CLOSED_HAND);
                e.consume();
            }
        });

        canvasScrollPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (isPanning[0]) {
                double dx = e.getScreenX() - panStart[0];
                double dy = e.getScreenY() - panStart[1];

                double contentW = centerWrapper.getPrefWidth();
                double contentH = centerWrapper.getPrefHeight();
                Bounds vp = canvasScrollPane.getViewportBounds();
                double viewW = vp.getWidth();
                double viewH = vp.getHeight();

                double scrollableW = contentW - viewW;
                double scrollableH = contentH - viewH;

                if (scrollableW > 0) {
                    double deltaH = dx / scrollableW;
                    double newH = panStart[2] - deltaH;
                    canvasScrollPane.setHvalue(Math.max(0.0, Math.min(1.0, newH)));
                }
                if (scrollableH > 0) {
                    double deltaV = dy / scrollableH;
                    double newV = panStart[3] - deltaV;
                    canvasScrollPane.setVvalue(Math.max(0.0, Math.min(1.0, newV)));
                }
                e.consume();
            }
        });

        canvasScrollPane.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (isPanning[0]) {
                isPanning[0] = false;
                Cursor normalCur = (isPanMode || isSpaceDown) ? Cursor.OPEN_HAND : Cursor.DEFAULT;
                canvasScrollPane.setCursor(normalCur);
                canvas.setCursor(normalCur);
                e.consume();
            }
        });

        // Failsafe mouse moved listener to ensure cursor never gets stuck on pan
        canvasScrollPane.setOnMouseMoved(e -> {
            if (!isPanMode && !isSpaceDown && !isPanning[0]) {
                if (canvasScrollPane.getCursor() != Cursor.DEFAULT) {
                    canvasScrollPane.setCursor(Cursor.DEFAULT);
                }
                if (canvas.getCursor() != Cursor.DEFAULT) {
                    canvas.setCursor(Cursor.DEFAULT);
                }
            }
        });

        // Zoom with Ctrl + Scroll Wheel
        canvasScrollPane.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown()) {
                if (e.getDeltaY() > 0) {
                    setZoom(zoom + 0.05);
                } else if (e.getDeltaY() < 0) {
                    setZoom(zoom - 0.05);
                }
                e.consume();
            }
        });

        return canvasScrollPane;
    }

    private Node createSidebar() {
        VBox side = new VBox();
        side.setMinWidth(360);
        side.setPrefWidth(420);
        side.setMaxWidth(800);
        side.setStyle("-fx-background-color: #12161D; -fx-border-color: #232B38; -fx-border-width: 0 0 0 1;");

        Tab propTab = new Tab("Properties");
        propTab.setClosable(false);
        ScrollPane propScroll = new ScrollPane(propBox);
        propScroll.setFitToWidth(true);
        propScroll.setStyle("-fx-background: #12161D; -fx-background-color: #12161D;");
        propBox.setStyle("-fx-background-color: #12161D;");
        propBox.setPadding(new Insets(16));
        propTab.setContent(propScroll);

        Tab layersTab = new Tab("Layers");
        layersTab.setClosable(false);
        VBox layersBox = new VBox(10);
        layersBox.setStyle("-fx-background-color: #12161D;");
        layersBox.setPadding(new Insets(14));

        layersList.setStyle("-fx-background-color: #12161D; -fx-control-inner-background: #12161D; -fx-border-color: #232B38; -fx-border-radius: 6; -fx-background-radius: 6;");
        layersList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TemplateElement item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String desc = item.getType().name();
                    if (item.getType() == ElementType.TEXT && item.getText() != null) {
                        String t = item.getText().replace("\n", " ");
                        desc += ": " + (t.length() > 22 ? t.substring(0, 22) + "..." : t);
                    }
                    setText(desc);
                    setStyle("-fx-text-fill: #E2E8F0; -fx-font-size: 11px;");
                }
            }
        });

        layersList.getSelectionModel().selectedItemProperty().addListener((obs, o, v) -> {
            if (isUpdatingLayersSelection) return;
            if (v != null) {
                selectedElement = v;
                updateSelectionOverlay();
                updatePropertiesPanel();
            }
        });

        HBox layerActions = new HBox(8);
        Button upBtn = createToolbarBtn("▲ Up", "Move layer upward", () -> moveLayer(1));
        Button downBtn = createToolbarBtn("▼ Down", "Move layer downward", () -> moveLayer(-1));
        Button delBtn = createToolbarBtn("🗑 Delete", "Delete selected element", this::deleteSelected);
        layerActions.getChildren().addAll(upBtn, downBtn, delBtn);

        layersBox.getChildren().addAll(layersList, layerActions);
        layersTab.setContent(layersBox);

        sideTabs.setStyle("-fx-background-color: #12161D;");
        sideTabs.getTabs().addAll(propTab, layersTab);
        side.getChildren().add(sideTabs);
        VBox.setVgrow(sideTabs, Priority.ALWAYS);
        return side;
    }

    private void refreshCanvas() {
        PageConfig page = template.getPage();
        double pageW = page.getWidth() * MM_PX;
        double pageH = page.getHeight() * MM_PX;

        canvas.setPrefSize(pageW, pageH);
        canvas.setMinSize(pageW, pageH);
        canvas.setMaxSize(pageW, pageH);
        canvas.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 24, 0, 0, 8);");

        updateCenterWrapperSize();

        // 1. Background Grid
        gridPane.getChildren().clear();
        gridPane.setPrefSize(pageW, pageH);
        if (showGrid) {
            for (double x = 10 * MM_PX; x < pageW; x += 10 * MM_PX) {
                Line l = new Line(x, 0, x, pageH);
                l.setStroke(Color.web("#ececec"));
                l.setStrokeWidth(0.5);
                gridPane.getChildren().add(l);
            }
            for (double y = 10 * MM_PX; y < pageH; y += 10 * MM_PX) {
                Line l = new Line(0, y, pageW, y);
                l.setStroke(Color.web("#ececec"));
                l.setStrokeWidth(0.5);
                gridPane.getChildren().add(l);
            }
        }

        // Margin Guides (Printable Boundary)
        PageConfig.Margins mg = page.getMargin();
        if (mg != null) {
            double mx = mg.getLeft() * MM_PX;
            double my = mg.getTop() * MM_PX;
            double mw = (page.getWidth() - mg.getLeft() - mg.getRight()) * MM_PX;
            double mh = (page.getHeight() - mg.getTop() - mg.getBottom()) * MM_PX;
            if (mw > 0 && mh > 0) {
                Rectangle marginBox = new Rectangle(mx, my, mw, mh);
                marginBox.setFill(Color.TRANSPARENT);
                marginBox.setStroke(Color.web("#3B82F6", 0.65));
                marginBox.setStrokeWidth(1.0);
                marginBox.getStrokeDashArray().addAll(4.0, 4.0);
                marginBox.setMouseTransparent(true);
                gridPane.getChildren().add(marginBox);

                Label mLabel = new Label(String.format("Printable: %.0f×%.0f mm  (Margin: T:%.0f B:%.0f L:%.0f R:%.0f)",
                        Math.max(0, page.getWidth() - mg.getLeft() - mg.getRight()),
                        Math.max(0, page.getHeight() - mg.getTop() - mg.getBottom()),
                        mg.getTop(), mg.getBottom(), mg.getLeft(), mg.getRight()));
                mLabel.setStyle("-fx-font-size: 9px; -fx-font-family: 'Segoe UI', sans-serif; -fx-text-fill: #3B82F6; -fx-background-color: rgba(59,130,246,0.12); -fx-padding: 1 5; -fx-background-radius: 3;");
                mLabel.setLayoutX(mx + 4);
                mLabel.setLayoutY(my + 3);
                mLabel.setMouseTransparent(true);
                gridPane.getChildren().add(mLabel);
            }
        }

        // 2. Elements Layer
        elementsPane.getChildren().clear();
        elementsPane.setPrefSize(pageW, pageH);
        RenderContext ctx = new RenderContext(null, settingsDao.getSettings(), 0, 1, 1);

        for (TemplateElement el : template.getElements()) {
            if (el.isHidden()) continue;
            Node node = createInteractiveElementNode(el, ctx);
            if (node != null) {
                elementsPane.getChildren().add(node);
            }
        }

        // 3. Selection Overlay (always on top of all elements)
        selectionPane.setPrefSize(pageW, pageH);
        updateSelectionOverlay();
    }

    private Rectangle createHandleShape(Cursor cursor) {
        Rectangle h = new Rectangle(9, 9);
        h.setFill(Color.WHITE);
        h.setStroke(Color.web("#D9A13B"));
        h.setStrokeWidth(1.5);
        h.setArcWidth(2);
        h.setArcHeight(2);
        h.setCursor(cursor);
        h.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 4, 0, 0, 1);");
        h.setOnMouseEntered(e -> h.setFill(Color.web("#D9A13B")));
        h.setOnMouseExited(e -> h.setFill(Color.WHITE));
        return h;
    }

    private Node createInteractiveElementNode(TemplateElement el, RenderContext ctx) {
        double x = el.getX() * MM_PX;
        double y = el.getY() * MM_PX;
        double w = el.getW() * MM_PX;
        double h = el.getH() * MM_PX;

        Pane wrapper = new Pane();
        wrapper.setLayoutX(x);
        wrapper.setLayoutY(y);
        wrapper.setPrefSize(w, h);
        wrapper.setMinSize(w, h);
        wrapper.setPickOnBounds(true);
        wrapper.setCursor(Cursor.DEFAULT); // Standard pointer, NOT pan cursor!

        Node visual = renderVisualElement(el, ctx, w, h);
        if (visual != null) {
            visual.setMouseTransparent(true);
            wrapper.getChildren().add(visual);
        }

        // Element selection and moving
        final double[] moveStart = new double[4];
        final boolean[] isMoved = new boolean[1];

        wrapper.setOnMousePressed(e -> {
            if (isPanMode || isSpaceDown || e.getButton() == MouseButton.MIDDLE) return;
            if (e.isPrimaryButtonDown()) {
                moveStart[0] = e.getScreenX();
                moveStart[1] = e.getScreenY();
                moveStart[2] = el.getX();
                moveStart[3] = el.getY();
                isMoved[0] = false;

                selectedElement = el;
                updateSelectionOverlay();
                updatePropertiesPanel();
                syncLayersListSelection();
                e.consume();
            }
        });

        wrapper.setOnMouseDragged(e -> {
            if (isPanMode || isSpaceDown || e.getButton() == MouseButton.MIDDLE) return;
            if (el.isLocked()) return;
            if (!e.isPrimaryButtonDown()) return;

            isMoved[0] = true;
            double dx = (e.getScreenX() - moveStart[0]) / zoom / MM_PX;
            double dy = (e.getScreenY() - moveStart[1]) / zoom / MM_PX;

            double newX = Math.max(0, moveStart[2] + dx);
            double newY = Math.max(0, moveStart[3] + dy);

            if (snapToGrid) {
                newX = Math.round(newX);
                newY = Math.round(newY);
                PageConfig.Margins smg = template.getPage().getMargin();
                if (smg != null) {
                    if (Math.abs(newX - smg.getLeft()) < 2.0) newX = smg.getLeft();
                    if (Math.abs((newX + el.getW()) - (template.getPage().getWidth() - smg.getRight())) < 2.0) {
                        newX = Math.max(0, template.getPage().getWidth() - smg.getRight() - el.getW());
                    }
                    if (Math.abs(newY - smg.getTop()) < 2.0) newY = smg.getTop();
                    if (Math.abs((newY + el.getH()) - (template.getPage().getHeight() - smg.getBottom())) < 2.0) {
                        newY = Math.max(0, template.getPage().getHeight() - smg.getBottom() - el.getH());
                    }
                }
            }

            el.setX(newX);
            el.setY(newY);

            wrapper.setLayoutX(newX * MM_PX);
            wrapper.setLayoutY(newY * MM_PX);
            updateSelectionOverlayPos(newX * MM_PX, newY * MM_PX);
            updatePropertiesPanel();
            e.consume();
        });

        wrapper.setOnMouseReleased(e -> {
            if (isPanMode || isSpaceDown || e.getButton() == MouseButton.MIDDLE) return;
            if (isMoved[0]) {
                saveState();
                refreshCanvas();
                updatePropertiesPanel();
            }
            e.consume();
        });

        return wrapper;
    }

    private Pane activeSelectionBox = null;

    private void updateSelectionOverlayPos(double px, double py) {
        if (activeSelectionBox != null) {
            activeSelectionBox.setLayoutX(px);
            activeSelectionBox.setLayoutY(py);
        }
    }

    private void updateSelectionOverlay() {
        selectionPane.getChildren().clear();
        activeSelectionBox = null;

        if (selectedElement == null || selectedElement.isHidden()) {
            return;
        }

        TemplateElement el = selectedElement;
        double x = el.getX() * MM_PX;
        double y = el.getY() * MM_PX;
        double w = el.getW() * MM_PX;
        double h = el.getH() * MM_PX;

        Pane selBox = new Pane();
        selBox.setLayoutX(x);
        selBox.setLayoutY(y);
        selBox.setPrefSize(w, h);
        selBox.setMinSize(w, h);
        selBox.setPickOnBounds(false);
        activeSelectionBox = selBox;

        // Selection Border: High-contrast gold dashed border with drop shadow
        Rectangle border = new Rectangle(w, h);
        border.setFill(Color.TRANSPARENT);
        border.setStroke(Color.web("#D9A13B"));
        border.setStrokeWidth(2.0);
        border.getStrokeDashArray().addAll(5.0, 4.0);
        border.setMouseTransparent(true);
        border.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 4, 0, 0, 1);");

        // 1. Bottom-Right Corner (SE) Resize Handle
        Rectangle handleSE = createHandleShape(Cursor.SE_RESIZE);
        handleSE.setLayoutX(w - 4.5);
        handleSE.setLayoutY(h - 4.5);

        // 2. Right-Center Edge (E) Resize Handle
        Rectangle handleE = createHandleShape(Cursor.E_RESIZE);
        handleE.setLayoutX(w - 4.5);
        handleE.setLayoutY((h / 2.0) - 4.5);

        // 3. Bottom-Center Edge (S) Resize Handle
        Rectangle handleS = createHandleShape(Cursor.S_RESIZE);
        handleS.setLayoutX((w / 2.0) - 4.5);
        handleS.setLayoutY(h - 4.5);

        // 4. Top-Left Corner (NW) Resize Handle
        Rectangle handleNW = createHandleShape(Cursor.NW_RESIZE);
        handleNW.setLayoutX(-4.5);
        handleNW.setLayoutY(-4.5);

        final double[] resizeStart = new double[6];

        // SE Handle Drag (Width & Height)
        handleSE.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX();
                resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW();
                resizeStart[3] = el.getH();
                e.consume();
            }
        });
        handleSE.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dx = (e.getScreenX() - resizeStart[0]) / zoom / MM_PX;
            double dy = (e.getScreenY() - resizeStart[1]) / zoom / MM_PX;
            double newW = Math.max(5.0, resizeStart[2] + dx);
            double newH = Math.max(3.0, resizeStart[3] + dy);
            if (snapToGrid) {
                newW = Math.round(newW);
                newH = Math.round(newH);
            }
            el.setW(newW);
            el.setH(newH);

            double nwPx = newW * MM_PX;
            double nhPx = newH * MM_PX;
            selBox.setPrefSize(nwPx, nhPx);
            selBox.setMinSize(nwPx, nhPx);
            border.setWidth(nwPx);
            border.setHeight(nhPx);

            handleSE.setLayoutX(nwPx - 4.5);
            handleSE.setLayoutY(nhPx - 4.5);
            handleE.setLayoutX(nwPx - 4.5);
            handleE.setLayoutY((nhPx / 2.0) - 4.5);
            handleS.setLayoutX((nwPx / 2.0) - 4.5);
            handleS.setLayoutY(nhPx - 4.5);

            updatePropertiesPanel();
            e.consume();
        });
        handleSE.setOnMouseReleased(e -> {
            saveState();
            refreshCanvas();
            updatePropertiesPanel();
            e.consume();
        });

        // E Handle Drag (Width only)
        handleE.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX();
                resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW();
                resizeStart[3] = el.getH();
                e.consume();
            }
        });
        handleE.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dx = (e.getScreenX() - resizeStart[0]) / zoom / MM_PX;
            double newW = Math.max(5.0, resizeStart[2] + dx);
            if (snapToGrid) newW = Math.round(newW);
            el.setW(newW);

            double nwPx = newW * MM_PX;
            double nhPx = el.getH() * MM_PX;
            selBox.setPrefSize(nwPx, nhPx);
            selBox.setMinSize(nwPx, nhPx);
            border.setWidth(nwPx);

            handleSE.setLayoutX(nwPx - 4.5);
            handleE.setLayoutX(nwPx - 4.5);
            handleS.setLayoutX((nwPx / 2.0) - 4.5);

            updatePropertiesPanel();
            e.consume();
        });
        handleE.setOnMouseReleased(e -> {
            saveState();
            refreshCanvas();
            updatePropertiesPanel();
            e.consume();
        });

        // S Handle Drag (Height only)
        handleS.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX();
                resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW();
                resizeStart[3] = el.getH();
                e.consume();
            }
        });
        handleS.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dy = (e.getScreenY() - resizeStart[1]) / zoom / MM_PX;
            double newH = Math.max(3.0, resizeStart[3] + dy);
            if (snapToGrid) newH = Math.round(newH);
            el.setH(newH);

            double nwPx = el.getW() * MM_PX;
            double nhPx = newH * MM_PX;
            selBox.setPrefSize(nwPx, nhPx);
            selBox.setMinSize(nwPx, nhPx);
            border.setHeight(nhPx);

            handleSE.setLayoutY(nhPx - 4.5);
            handleE.setLayoutY((nhPx / 2.0) - 4.5);
            handleS.setLayoutY(nhPx - 4.5);

            updatePropertiesPanel();
            e.consume();
        });
        handleS.setOnMouseReleased(e -> {
            saveState();
            refreshCanvas();
            updatePropertiesPanel();
            e.consume();
        });

        // NW Handle Drag (X, Y, W, H)
        handleNW.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX();
                resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW();
                resizeStart[3] = el.getH();
                resizeStart[4] = el.getX();
                resizeStart[5] = el.getY();
                e.consume();
            }
        });
        handleNW.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dx = (e.getScreenX() - resizeStart[0]) / zoom / MM_PX;
            double dy = (e.getScreenY() - resizeStart[1]) / zoom / MM_PX;

            double newW = Math.max(5.0, resizeStart[2] - dx);
            double newH = Math.max(3.0, resizeStart[3] - dy);
            double newX = Math.max(0, resizeStart[4] + (resizeStart[2] - newW));
            double newY = Math.max(0, resizeStart[5] + (resizeStart[3] - newH));

            if (snapToGrid) {
                newW = Math.round(newW);
                newH = Math.round(newH);
                newX = Math.round(newX);
                newY = Math.round(newY);
            }

            el.setW(newW);
            el.setH(newH);
            el.setX(newX);
            el.setY(newY);

            double nwPx = newW * MM_PX;
            double nhPx = newH * MM_PX;
            selBox.setLayoutX(newX * MM_PX);
            selBox.setLayoutY(newY * MM_PX);
            selBox.setPrefSize(nwPx, nhPx);
            selBox.setMinSize(nwPx, nhPx);
            border.setWidth(nwPx);
            border.setHeight(nhPx);

            handleSE.setLayoutX(nwPx - 4.5);
            handleSE.setLayoutY(nhPx - 4.5);
            handleE.setLayoutX(nwPx - 4.5);
            handleE.setLayoutY((nhPx / 2.0) - 4.5);
            handleS.setLayoutX((nwPx / 2.0) - 4.5);
            handleS.setLayoutY(nhPx - 4.5);

            updatePropertiesPanel();
            e.consume();
        });
        handleNW.setOnMouseReleased(e -> {
            saveState();
            refreshCanvas();
            updatePropertiesPanel();
            e.consume();
        });

        selBox.getChildren().addAll(border, handleSE, handleE, handleS, handleNW);
        selectionPane.getChildren().add(selBox);
    }

    private Node renderVisualElement(TemplateElement el, RenderContext ctx, double w, double h) {
        switch (el.getType()) {
            case RECT: {
                Rectangle r = new Rectangle(w, h);
                if (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg())) {
                    r.setFill(Color.web(el.getBg()));
                } else r.setFill(Color.TRANSPARENT);
                if (el.getBorderWidth() > 0 && el.getBorderColor() != null) {
                    r.setStroke(Color.web(el.getBorderColor()));
                    r.setStrokeWidth(el.getBorderWidth() * MM_PX);
                }
                if (el.getBorderRadius() > 0) {
                    r.setArcWidth(el.getBorderRadius() * MM_PX * 2);
                    r.setArcHeight(el.getBorderRadius() * MM_PX * 2);
                }
                return r;
            }
            case LINE: {
                Line l = new Line();
                if ("v".equalsIgnoreCase(el.getDirection())) {
                    l.setStartX(w / 2); l.setStartY(0);
                    l.setEndX(w / 2); l.setEndY(h);
                } else {
                    l.setStartX(0); l.setStartY(h / 2);
                    l.setEndX(w); l.setEndY(h / 2);
                }
                l.setStroke(Color.web(el.getBorderColor() != null ? el.getBorderColor() : "#1a1a1a"));
                l.setStrokeWidth(Math.max(1, el.getBorderWidth() * MM_PX));
                return l;
            }
            case TEXT:
            case PAGENO: {
                Label lbl = new Label(ctx.resolveText(el.getText()));
                lbl.setPrefSize(w, h);
                lbl.setWrapText(true);

                String colorHex = el.getColor() != null && !el.getColor().isBlank() ? el.getColor() : "#1a1a1a";
                String bgStyle = "";
                if (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg())) {
                    bgStyle = "-fx-background-color: " + el.getBg() + ";";
                }
                String borderStyle = "";
                if (el.getBorderWidth() > 0 && el.getBorderColor() != null) {
                    borderStyle = "-fx-border-color: " + el.getBorderColor() + "; -fx-border-width: " + (el.getBorderWidth() * MM_PX) + ";";
                }
                if (el.getBorderRadius() > 0) {
                    borderStyle += "-fx-background-radius: " + (el.getBorderRadius() * MM_PX) + "; -fx-border-radius: " + (el.getBorderRadius() * MM_PX) + ";";
                }

                String fw = el.getFontWeight() >= 700 ? "bold" : "normal";
                String fs = el.isItalic() ? "italic" : "normal";
                String family = el.getFontFamily() != null ? el.getFontFamily() : "Segoe UI";

                lbl.setStyle("-fx-text-fill: " + colorHex + "; -fx-fill: " + colorHex + "; -fx-font-size: " + (el.getFontSize() * 1.3) + "px; -fx-font-family: '" + family + "'; -fx-font-weight: " + fw + "; -fx-font-style: " + fs + "; " + bgStyle + " " + borderStyle);
                
                Pos alignment = Pos.TOP_LEFT;
                if ("center".equalsIgnoreCase(el.getAlign())) alignment = Pos.TOP_CENTER;
                else if ("right".equalsIgnoreCase(el.getAlign())) alignment = Pos.TOP_RIGHT;
                if ("middle".equalsIgnoreCase(el.getVAlign())) {
                    if ("center".equalsIgnoreCase(el.getAlign())) alignment = Pos.CENTER;
                    else if ("right".equalsIgnoreCase(el.getAlign())) alignment = Pos.CENTER_RIGHT;
                    else alignment = Pos.CENTER_LEFT;
                }
                lbl.setAlignment(alignment);
                return lbl;
            }
            case IMAGE: {
                ImageView iv = new ImageView();
                iv.setFitWidth(w);
                iv.setFitHeight(h);
                iv.setPreserveRatio(!"fill".equalsIgnoreCase(el.getObjectFit()));
                iv.setSmooth(true);

                Image img = null;
                if (el.isUseBusinessLogo()) {
                    Settings settings = settingsDao.getSettings();
                    String logo = settings != null && settings.getBusiness() != null ? settings.getBusiness().getLogo() : null;
                    if (logo != null && !logo.isBlank()) {
                        img = decodeFxImage(logo);
                    }
                    if (img == null) {
                        try {
                            img = new Image(getClass().getResourceAsStream("/icons/Invoicewhitebackground.png"));
                        } catch (Exception ignored) {}
                    }
                } else if (el.getSrc() != null && !el.getSrc().isBlank()) {
                    img = decodeFxImage(el.getSrc());
                }

                if (img != null) {
                    iv.setImage(img);
                    StackPane sp = new StackPane(iv);
                    sp.setPrefSize(w, h);
                    sp.setAlignment(Pos.CENTER);
                    return sp;
                } else {
                    StackPane sp = new StackPane();
                    sp.setPrefSize(w, h);
                    sp.setStyle("-fx-background-color: #f4f4f5; -fx-border-color: #cbd5e1; -fx-border-style: dashed; -fx-border-width: 1;");
                    Label lbl = new Label(el.isUseBusinessLogo() ? "📷 [Business Logo]" : "🖼 [No Image Selected]");
                    lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B; -fx-font-weight: bold;");
                    sp.getChildren().add(lbl);
                    return sp;
                }
            }
            case QRCODE: {
                StackPane sp = new StackPane();
                sp.setPrefSize(w, h);
                sp.setStyle("-fx-background-color: #f8f8f8; -fx-border-color: #1a1a1a;");
                Label lbl = new Label("QR CODE\n" + el.getQrSource());
                lbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #1a1a1a; -fx-text-alignment: center;");
                sp.getChildren().add(lbl);
                return sp;
            }
            case BARCODE: {
                StackPane sp = new StackPane();
                sp.setPrefSize(w, h);
                sp.setStyle("-fx-background-color: #ffffff; -fx-border-color: #333333;");
                Label lbl = new Label("||| || |||| ||||\n" + ctx.resolveText(el.getBarcodeData()));
                lbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #1a1a1a; -fx-text-alignment: center;");
                sp.getChildren().add(lbl);
                return sp;
            }
            case TABLE: {
                VBox box = new VBox(0);
                box.setPrefSize(w, h);
                box.setStyle("-fx-background-color: #ffffff; -fx-border-color: #c8c8c8; -fx-border-width: 1;");

                List<TableColumn> cols = el.getColumns();
                if (cols == null || cols.isEmpty()) cols = PresetTemplates.defaultItemColumns();

                HBox hRow = new HBox(0);
                hRow.setPrefHeight(22);
                String hBg = el.getHeaderBg() != null && !el.getHeaderBg().isBlank() ? el.getHeaderBg() : "#efe9db";
                String hCol = el.getHeaderColor() != null && !el.getHeaderColor().isBlank() ? el.getHeaderColor() : "#1a1a1a";
                hRow.setStyle("-fx-background-color: " + hBg + "; -fx-border-color: #c8c8c8; -fx-border-width: 0 0 1 0;");

                for (TableColumn c : cols) {
                    double cW = (c.getWidth() / 100.0) * w;
                    Label lbl = new Label(c.getLabel());
                    lbl.setPrefWidth(cW);
                    lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: " + hCol + "; -fx-padding: 0 4;");
                    lbl.setAlignment("right".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER_RIGHT : ("center".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER : Pos.CENTER_LEFT));
                    hRow.getChildren().add(lbl);
                }
                box.getChildren().add(hRow);

                for (int r = 1; r <= 2; r++) {
                    HBox row = new HBox(0);
                    row.setPrefHeight(20);
                    String rBg = el.isShowZebra() && (r % 2 == 0) ? "#f8f8f8" : "#ffffff";
                    row.setStyle("-fx-background-color: " + rBg + "; -fx-border-color: #e5e5e5; -fx-border-width: 0 0 0.5 0;");
                    for (TableColumn c : cols) {
                        double cW = (c.getWidth() / 100.0) * w;
                        String val = "sr".equalsIgnoreCase(c.getKey()) ? String.valueOf(r) :
                                     ("desc".equalsIgnoreCase(c.getKey()) ? "Sample Item " + r :
                                     ("hsn".equalsIgnoreCase(c.getKey()) ? "8471" :
                                     ("qty".equalsIgnoreCase(c.getKey()) ? "1.00" :
                                     ("unit".equalsIgnoreCase(c.getKey()) ? "PCS" :
                                     ("rate".equalsIgnoreCase(c.getKey()) ? "500.00" :
                                     ("gst".equalsIgnoreCase(c.getKey()) ? "18%" :
                                     ("amount".equalsIgnoreCase(c.getKey()) ? "590.00" : "—")))))));
                        Label lbl = new Label(val);
                        lbl.setPrefWidth(cW);
                        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #1a1a1a; -fx-padding: 0 4;");
                        lbl.setAlignment("right".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER_RIGHT : ("center".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER : Pos.CENTER_LEFT));
                        row.getChildren().add(lbl);
                    }
                    box.getChildren().add(row);
                }
                return box;
            }
        }
        return null;
    }

    private void updatePropertiesPanel() {
        propBox.getChildren().clear();

        if (selectedElement == null) {
            buildPageAndMarginProperties();
            return;
        }

        TemplateElement el = selectedElement;

        // Header Actions Row
        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label typeLbl = new Label(el.getType().name());
        typeLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #D9A13B; -fx-background-color: rgba(217,161,59,0.15); -fx-padding: 2 8; -fx-background-radius: 4;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button dupBtn = createToolbarBtn("❐", "Duplicate Element (Ctrl D)", this::duplicateSelected);
        Button upBtn = createToolbarBtn("▲", "Bring Forward", () -> moveLayer(1));
        Button downBtn = createToolbarBtn("▼", "Send Backward", () -> moveLayer(-1));
        Button delBtn = createToolbarBtn("🗑", "Delete Element (Delete key)", this::deleteSelected);

        headerRow.getChildren().addAll(typeLbl, sp, dupBtn, upBtn, downBtn, delBtn);
        propBox.getChildren().add(headerRow);

        // Position & Size Grid
        TitledPane geoPane = new TitledPane();
        geoPane.setText("Position & Size (mm)");
        geoPane.setExpanded(true);

        GridPane posGrid = new GridPane();
        posGrid.setHgap(8); posGrid.setVgap(8);
        posGrid.setPadding(new Insets(8));

        posGrid.add(new Label("X:"), 0, 0);
        Spinner<Double> xSpin = new Spinner<>(0.0, 500.0, el.getX(), 1.0);
        xSpin.setPrefWidth(85);
        xSpin.setEditable(true);
        xSpin.valueProperty().addListener((obs, o, v) -> { el.setX(v); refreshCanvas(); });
        posGrid.add(xSpin, 1, 0);

        posGrid.add(new Label("Y:"), 2, 0);
        Spinner<Double> ySpin = new Spinner<>(0.0, 500.0, el.getY(), 1.0);
        ySpin.setPrefWidth(85);
        ySpin.setEditable(true);
        ySpin.valueProperty().addListener((obs, o, v) -> { el.setY(v); refreshCanvas(); });
        posGrid.add(ySpin, 3, 0);

        posGrid.add(new Label("W:"), 0, 1);
        Spinner<Double> wSpin = new Spinner<>(1.0, 500.0, el.getW(), 1.0);
        wSpin.setPrefWidth(85);
        wSpin.setEditable(true);
        wSpin.valueProperty().addListener((obs, o, v) -> { el.setW(v); refreshCanvas(); });
        posGrid.add(wSpin, 1, 1);

        posGrid.add(new Label("H:"), 2, 1);
        Spinner<Double> hSpin = new Spinner<>(1.0, 500.0, el.getH(), 1.0);
        hSpin.setPrefWidth(85);
        hSpin.setEditable(true);
        hSpin.valueProperty().addListener((obs, o, v) -> { el.setH(v); refreshCanvas(); });
        posGrid.add(hSpin, 3, 1);

        geoPane.setContent(posGrid);
        propBox.getChildren().add(geoPane);

        // Specific Type Editors
        if (el.getType() == ElementType.TEXT || el.getType() == ElementType.PAGENO) {
            buildTextProperties(el);
        } else if (el.getType() == ElementType.RECT) {
            buildRectProperties(el);
        } else if (el.getType() == ElementType.LINE) {
            buildLineProperties(el);
        } else if (el.getType() == ElementType.IMAGE) {
            buildImageProperties(el);
        } else if (el.getType() == ElementType.QRCODE) {
            buildQrProperties(el);
        } else if (el.getType() == ElementType.BARCODE) {
            buildBarcodeProperties(el);
        } else if (el.getType() == ElementType.TABLE) {
            buildTableProperties(el);
        }

        // Behavior Toggles Box
        VBox toggles = new VBox(8);
        toggles.setPadding(new Insets(8));
        toggles.setStyle("-fx-background-color: #151B25; -fx-border-color: #232B38; -fx-border-radius: 6; -fx-background-radius: 6;");

        CheckBox repeatCb = new CheckBox("Repeat on multi-page bills");
        repeatCb.setSelected(el.isRepeatOnPages());
        repeatCb.selectedProperty().addListener((obs, o, v) -> el.setRepeatOnPages(v));

        CheckBox blankCb = new CheckBox("Hide when blank / empty");
        blankCb.setSelected(el.isHideWhenBlank());
        blankCb.selectedProperty().addListener((obs, o, v) -> el.setHideWhenBlank(v));

        CheckBox lockCb = new CheckBox("Lock position on canvas");
        lockCb.setSelected(el.isLocked());
        lockCb.selectedProperty().addListener((obs, o, v) -> el.setLocked(v));

        toggles.getChildren().addAll(repeatCb, blankCb, lockCb);
        propBox.getChildren().add(toggles);
    }

    private void buildTextProperties(TemplateElement el) {
        VBox sec = new VBox(8);

        Label textLbl = new Label("Text Content / Template Variables:");
        textLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #CBD5E1;");
        TextArea ta = new TextArea(el.getText());
        ta.setPrefRowCount(3);
        ta.setWrapText(true);
        ta.setStyle("-fx-control-inner-background: #0D1117; -fx-background-color: #0D1117; -fx-text-fill: #F4F4F5; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px; -fx-border-color: #232B38; -fx-border-radius: 6; -fx-background-radius: 6;");
        ta.textProperty().addListener((obs, o, v) -> { el.setText(v); refreshCanvas(); });

        VBox varSec = new VBox(8);
        varSec.setStyle("-fx-background-color: #121720; -fx-border-color: #232B38; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10;");

        Label varSecLbl = new Label("INSERT TEMPLATE VARIABLES:");
        varSecLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #D9A13B; -fx-letter-spacing: 1;");

        // 1. Direct Dropdown & + Add Button directly in property view
        ComboBox<VariableDef> varCombo = new ComboBox<>();
        varCombo.setPromptText("Choose variable to add...");
        varCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(varCombo, Priority.ALWAYS);
        List<VariableDef> allVars = getComprehensiveVariablesList();
        varCombo.setItems(FXCollections.observableArrayList(allVars));
        varCombo.setStyle("-fx-background-color: #0D1117; -fx-border-color: #232B38; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 11px;");
        varCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(VariableDef item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getLabel() + "  {{" + item.getKey() + "}}");
                }
            }
        });
        varCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(VariableDef item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Choose variable to add...");
                } else {
                    setText(item.getLabel() + "  {{" + item.getKey() + "}}");
                }
            }
        });

        Button dropInsertBtn = new Button("+ Add");
        dropInsertBtn.setStyle("-fx-background-color: #D9A13B; -fx-text-fill: #0D1117; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 5 12; -fx-cursor: hand; -fx-background-radius: 4;");
        dropInsertBtn.setTooltip(new Tooltip("Add chosen variable into text at cursor position"));
        dropInsertBtn.setOnAction(e -> {
            VariableDef sel = varCombo.getValue();
            if (sel != null && sel.getKey() != null) {
                insertVariableIntoTarget(ta, "{{" + sel.getKey() + "}}", el);
            } else {
                Toast.show(app.getRootPane(), "Select Variable", "Please choose a variable from dropdown first.", true);
            }
        });

        HBox dropRow = new HBox(6, varCombo, dropInsertBtn);
        dropRow.setAlignment(Pos.CENTER_LEFT);

        // 2. Browse All Button
        Button varPickerBtn = new Button("⚡ Browse & Search All Variables (35+)");
        varPickerBtn.setMaxWidth(Double.MAX_VALUE);
        varPickerBtn.setStyle("-fx-background-color: #1A2332; -fx-text-fill: #D9A13B; -fx-font-weight: bold; -fx-font-size: 11px; -fx-border-color: rgba(217,161,59,0.35); -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 10; -fx-cursor: hand;");
        varPickerBtn.setTooltip(new Tooltip("Open searchable popup with all available placeholders & custom buyer fields"));
        varPickerBtn.setOnAction(e -> showVariablePicker(ta, el));

        // 3. Quick Chips
        FlowPane quickChips = new FlowPane(5, 5);
        quickChips.setPrefWrapLength(380);
        String[] quickVars = {"buyer_name", "invoice_no", "invoice_date", "grand_total", "due_amount", "buyer_gstin"};
        for (String qv : quickVars) {
            Button qb = new Button("{{" + qv + "}}");
            qb.getStyleClass().add("var-chip-btn");
            qb.setTooltip(new Tooltip("Click to insert {{" + qv + "}}"));
            qb.setOnAction(e -> insertVariableIntoTarget(ta, "{{" + qv + "}}", el));
            quickChips.getChildren().add(qb);
        }

        varSec.getChildren().addAll(varSecLbl, dropRow, varPickerBtn, quickChips);

        // Typography: Family & Size
        HBox fontRow = new HBox(8);
        fontRow.setAlignment(Pos.CENTER_LEFT);

        List<String> fontList = new ArrayList<>(List.of("Segoe UI", "Arial", "Roboto", "Courier New", "Times New Roman", "Georgia", "Impact"));
        Settings curSet = settingsDao.getSettings();
        if (curSet != null && curSet.getCustomFonts() != null) {
            for (CustomFontDef cf : curSet.getCustomFonts()) {
                if (cf.getName() != null && !fontList.contains(cf.getName())) {
                    fontList.add(cf.getName());
                }
            }
        }
        ComboBox<String> fontCombo = new ComboBox<>(FXCollections.observableArrayList(fontList));
        fontCombo.setValue(el.getFontFamily() != null ? el.getFontFamily() : "Segoe UI");
        fontCombo.valueProperty().addListener((obs, o, v) -> { el.setFontFamily(v); refreshCanvas(); });

        Spinner<Double> fontSpin = new Spinner<>(5.0, 72.0, el.getFontSize(), 0.5);
        fontSpin.setEditable(true);
        fontSpin.valueProperty().addListener((obs, o, v) -> { el.setFontSize(v); refreshCanvas(); });

        fontRow.getChildren().addAll(new Label("Font:"), fontCombo, new Label("Size:"), fontSpin);

        // Styling Buttons: Bold, Italic, Alignment
        HBox styleRow = new HBox(8);
        styleRow.setAlignment(Pos.CENTER_LEFT);

        ToggleButton boldBtn = new ToggleButton("B");
        boldBtn.setSelected(el.getFontWeight() >= 700);
        boldBtn.setStyle("-fx-font-weight: bold;");
        boldBtn.setTooltip(new Tooltip("Bold"));
        boldBtn.setOnAction(e -> { el.setFontWeight(boldBtn.isSelected() ? 700 : 400); refreshCanvas(); });

        ToggleButton italicBtn = new ToggleButton("I");
        italicBtn.setSelected(el.isItalic());
        italicBtn.setStyle("-fx-font-style: italic;");
        italicBtn.setTooltip(new Tooltip("Italic"));
        italicBtn.setOnAction(e -> { el.setItalic(italicBtn.isSelected()); refreshCanvas(); });

        Button alignL = createToolbarBtn("⯇", "Align Left", () -> { el.setAlign("left"); refreshCanvas(); });
        Button alignC = createToolbarBtn("☰", "Align Center", () -> { el.setAlign("center"); refreshCanvas(); });
        Button alignR = createToolbarBtn("⯈", "Align Right", () -> { el.setAlign("right"); refreshCanvas(); });

        styleRow.getChildren().addAll(boldBtn, italicBtn, new Separator(javafx.geometry.Orientation.VERTICAL), alignL, alignC, alignR);

        // ColorPickers with quick palette chips
        GridPane colorGrid = new GridPane();
        colorGrid.setHgap(8); colorGrid.setVgap(8);

        ColorPicker textColorPicker = new ColorPicker(hexToColor(el.getColor(), Color.web("#1a1a1a")));
        textColorPicker.setTooltip(new Tooltip("Text Color Picker"));
        textColorPicker.setOnAction(e -> {
            String hex = colorToHex(textColorPicker.getValue());
            el.setColor(hex);
            refreshCanvas();
        });

        HBox colorPresetRow = new HBox(4);
        colorPresetRow.setAlignment(Pos.CENTER_LEFT);
        String[] presets = {"#1A1A1A", "#64748B", "#D9A13B", "#2563EB", "#DC2626", "#16A34A", "#7C3AED", "#FFFFFF"};
        for (String hex : presets) {
            Button chip = new Button();
            chip.setPrefSize(18, 18);
            chip.setMinSize(18, 18);
            chip.setMaxSize(18, 18);
            chip.setStyle("-fx-background-color: " + hex + "; -fx-border-color: #39445A; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4; -fx-cursor: hand; -fx-padding: 0;");
            chip.setTooltip(new Tooltip("Set text color: " + hex));
            chip.setOnAction(e -> {
                el.setColor(hex);
                textColorPicker.setValue(Color.web(hex));
                refreshCanvas();
            });
            colorPresetRow.getChildren().add(chip);
        }
        HBox textColBox = new HBox(8, textColorPicker, colorPresetRow);
        textColBox.setAlignment(Pos.CENTER_LEFT);

        colorGrid.add(new Label("Text Color:"), 0, 0);
        colorGrid.add(textColBox, 1, 0);

        ColorPicker bgColorPicker = new ColorPicker(hexToColor(el.getBg(), Color.TRANSPARENT));
        bgColorPicker.setTooltip(new Tooltip("Background Fill Color"));
        bgColorPicker.setOnAction(e -> { el.setBg(colorToHex(bgColorPicker.getValue())); refreshCanvas(); });

        CheckBox bgTransCb = new CheckBox("Transparent");
        bgTransCb.setSelected(el.getBg() == null || "transparent".equalsIgnoreCase(el.getBg()));
        bgTransCb.setOnAction(e -> {
            if (bgTransCb.isSelected()) {
                el.setBg("transparent");
            } else {
                el.setBg(colorToHex(bgColorPicker.getValue()));
            }
            refreshCanvas();
        });

        colorGrid.add(new Label("Background:"), 0, 1);
        colorGrid.add(new HBox(6, bgColorPicker, bgTransCb), 1, 1);

        sec.getChildren().addAll(textLbl, ta, varSec, fontRow, styleRow, colorGrid);
        propBox.getChildren().add(sec);
    }

    private void buildRectProperties(TemplateElement el) {
        VBox sec = new VBox(8);
        Label title = new Label("Shape Properties:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #CBD5E1;");

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);

        ColorPicker fillPicker = new ColorPicker(hexToColor(el.getBg(), Color.web("#f4f1ea")));
        fillPicker.setOnAction(e -> { el.setBg(colorToHex(fillPicker.getValue())); refreshCanvas(); });

        CheckBox transCb = new CheckBox("Transparent");
        transCb.setSelected(el.getBg() == null || "transparent".equalsIgnoreCase(el.getBg()));
        transCb.setOnAction(e -> {
            if (transCb.isSelected()) el.setBg("transparent");
            else el.setBg(colorToHex(fillPicker.getValue()));
            refreshCanvas();
        });

        grid.add(new Label("Fill Color:"), 0, 0);
        grid.add(new HBox(6, fillPicker, transCb), 1, 0);

        ColorPicker borderPicker = new ColorPicker(hexToColor(el.getBorderColor(), Color.web("#1a1a1a")));
        borderPicker.setOnAction(e -> { el.setBorderColor(colorToHex(borderPicker.getValue())); refreshCanvas(); });
        grid.add(new Label("Border Color:"), 0, 1);
        grid.add(borderPicker, 1, 1);

        Spinner<Double> borderW = new Spinner<>(0.0, 10.0, el.getBorderWidth(), 0.5);
        borderW.setEditable(true);
        borderW.valueProperty().addListener((obs, o, v) -> { el.setBorderWidth(v); refreshCanvas(); });
        grid.add(new Label("Border (mm):"), 0, 2);
        grid.add(borderW, 1, 2);

        Spinner<Double> borderR = new Spinner<>(0.0, 50.0, el.getBorderRadius(), 1.0);
        borderR.setEditable(true);
        borderR.valueProperty().addListener((obs, o, v) -> { el.setBorderRadius(v); refreshCanvas(); });
        grid.add(new Label("Corner Radius:"), 0, 3);
        grid.add(borderR, 1, 3);

        sec.getChildren().addAll(title, grid);
        propBox.getChildren().add(sec);
    }

    private void buildLineProperties(TemplateElement el) {
        VBox sec = new VBox(8);
        Label title = new Label("Line Properties:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #CBD5E1;");

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);

        ComboBox<String> dirCb = new ComboBox<>(FXCollections.observableArrayList("Horizontal", "Vertical"));
        dirCb.setValue("v".equalsIgnoreCase(el.getDirection()) ? "Vertical" : "Horizontal");
        dirCb.valueProperty().addListener((obs, o, v) -> {
            el.setDirection("Vertical".equals(v) ? "v" : "h");
            refreshCanvas();
        });
        grid.add(new Label("Direction:"), 0, 0);
        grid.add(dirCb, 1, 0);

        ColorPicker colorPicker = new ColorPicker(hexToColor(el.getBorderColor(), Color.web("#1a1a1a")));
        colorPicker.setOnAction(e -> { el.setBorderColor(colorToHex(colorPicker.getValue())); refreshCanvas(); });
        grid.add(new Label("Line Color:"), 0, 1);
        grid.add(colorPicker, 1, 1);

        Spinner<Double> thickSpin = new Spinner<>(0.2, 10.0, Math.max(0.5, el.getBorderWidth()), 0.5);
        thickSpin.setEditable(true);
        thickSpin.valueProperty().addListener((obs, o, v) -> { el.setBorderWidth(v); refreshCanvas(); });
        grid.add(new Label("Thickness (mm):"), 0, 2);
        grid.add(thickSpin, 1, 2);

        sec.getChildren().addAll(title, grid);
        propBox.getChildren().add(sec);
    }

    private void buildImageProperties(TemplateElement el) {
        VBox sec = new VBox(10);
        Label title = new Label("Image Properties & Source:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #CBD5E1; -fx-font-size: 12px;");

        // Thumbnail Preview Box
        StackPane previewContainer = new StackPane();
        previewContainer.setPrefHeight(90);
        previewContainer.setMaxWidth(Double.MAX_VALUE);
        previewContainer.setStyle("-fx-background-color: #0D1117; -fx-border-color: #232B38; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6;");

        ImageView thumbView = new ImageView();
        thumbView.setFitHeight(80);
        thumbView.setFitWidth(180);
        thumbView.setPreserveRatio(true);
        thumbView.setSmooth(true);

        Label noImgLbl = new Label("No Image Loaded");
        noImgLbl.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");

        Image currentImg = null;
        if (el.isUseBusinessLogo()) {
            Settings settings = settingsDao.getSettings();
            String logo = settings != null && settings.getBusiness() != null ? settings.getBusiness().getLogo() : null;
            if (logo != null && !logo.isBlank()) currentImg = decodeFxImage(logo);
            if (currentImg == null) {
                try {
                    currentImg = new Image(getClass().getResourceAsStream("/icons/Invoicewhitebackground.png"));
                } catch (Exception ignored) {}
            }
        } else if (el.getSrc() != null && !el.getSrc().isBlank()) {
            currentImg = decodeFxImage(el.getSrc());
        }

        if (currentImg != null) {
            thumbView.setImage(currentImg);
            previewContainer.getChildren().add(thumbView);
        } else {
            previewContainer.getChildren().add(noImgLbl);
        }

        CheckBox logoCb = new CheckBox("Use Company Logo from Settings");
        logoCb.setSelected(el.isUseBusinessLogo());
        logoCb.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 11px;");
        logoCb.setOnAction(e -> {
            el.setUseBusinessLogo(logoCb.isSelected());
            saveState();
            refreshCanvas();
            updatePropertiesPanel();
        });

        Button uploadBtn = createToolbarBtn("📁 Browse Image File...", "Choose PNG / JPG / WebP from disk", () -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Image");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images (*.png, *.jpg, *.jpeg, *.webp)", "*.png", "*.jpg", "*.jpeg", "*.webp"));
            File f = fc.showOpenDialog(app.getPrimaryStage());
            if (f != null) {
                try {
                    byte[] bytes = Files.readAllBytes(f.toPath());
                    String mime = f.getName().toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
                    String base64 = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
                    el.setSrc(base64);
                    el.setUseBusinessLogo(false);
                    saveState();
                    refreshCanvas();
                    updatePropertiesPanel();
                    Toast.show(app.getRootPane(), "Image Loaded", "Loaded " + f.getName(), false);
                } catch (Exception ex) {
                    Toast.show(app.getRootPane(), "Image Error", ex.getMessage(), true);
                }
            }
        });
        uploadBtn.setMaxWidth(Double.MAX_VALUE);

        HBox logoButtons = new HBox(6);
        Button appLogoWhiteBtn = new Button("Use App Logo (Light)");
        appLogoWhiteBtn.setStyle("-fx-background-color: #1A2332; -fx-text-fill: #D9A13B; -fx-font-size: 10px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand;");
        appLogoWhiteBtn.setOnAction(e -> {
            String b64 = loadResourceAsBase64("/icons/Invoicewhitebackground.png");
            if (b64 != null) {
                el.setSrc(b64);
                el.setUseBusinessLogo(false);
                saveState();
                refreshCanvas();
                updatePropertiesPanel();
                Toast.show(app.getRootPane(), "App Logo Applied", "Set light app logo", false);
            }
        });

        Button appLogoDarkBtn = new Button("Use App Logo (Dark)");
        appLogoDarkBtn.setStyle("-fx-background-color: #1A2332; -fx-text-fill: #D9A13B; -fx-font-size: 10px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand;");
        appLogoDarkBtn.setOnAction(e -> {
            String b64 = loadResourceAsBase64("/icons/Invoice black background.png");
            if (b64 != null) {
                el.setSrc(b64);
                el.setUseBusinessLogo(false);
                saveState();
                refreshCanvas();
                updatePropertiesPanel();
                Toast.show(app.getRootPane(), "App Logo Applied", "Set dark app logo", false);
            }
        });

        logoButtons.getChildren().addAll(appLogoWhiteBtn, appLogoDarkBtn);

        Button clearBtn = new Button("🗑 Clear Image");
        clearBtn.setStyle("-fx-background-color: #2D1515; -fx-text-fill: #EF4444; -fx-font-size: 10px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> {
            el.setSrc(null);
            el.setUseBusinessLogo(false);
            saveState();
            refreshCanvas();
            updatePropertiesPanel();
        });

        HBox fitRow = new HBox(8);
        fitRow.setAlignment(Pos.CENTER_LEFT);
        Label fitLbl = new Label("Object Fit:");
        fitLbl.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 11px;");
        ComboBox<String> fitCb = new ComboBox<>(FXCollections.observableArrayList("contain", "cover", "fill"));
        fitCb.setValue(el.getObjectFit() != null ? el.getObjectFit() : "contain");
        fitCb.setStyle("-fx-font-size: 11px;");
        fitCb.valueProperty().addListener((obs, o, v) -> {
            el.setObjectFit(v);
            saveState();
            refreshCanvas();
        });
        fitRow.getChildren().addAll(fitLbl, fitCb, clearBtn);

        Label sizeHint = new Label(String.format("Size: %.1f × %.1f mm", el.getW(), el.getH()));
        sizeHint.setStyle("-fx-text-fill: #8E9EB5; -fx-font-size: 10px;");

        sec.getChildren().addAll(title, previewContainer, logoCb, uploadBtn, logoButtons, fitRow, sizeHint);
        propBox.getChildren().add(sec);
    }

    private Image decodeFxImage(String src) {
        if (src == null || src.isBlank()) return null;
        try {
            if (src.startsWith("data:image")) {
                int comma = src.indexOf(",");
                if (comma != -1) {
                    byte[] bytes = Base64.getDecoder().decode(src.substring(comma + 1));
                    BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
                    return SwingFXUtils.toFXImage(bi, null);
                }
            } else if (src.startsWith("/") || src.startsWith("classpath:")) {
                String path = src.startsWith("classpath:") ? src.substring(10) : src;
                var in = getClass().getResourceAsStream(path);
                if (in != null) return new Image(in);
            } else {
                File f = new File(src);
                if (f.exists()) return new Image(f.toURI().toString());
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String loadResourceAsBase64(String resourcePath) {
        try (var in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void buildQrProperties(TemplateElement el) {
        VBox sec = new VBox(8);
        Label title = new Label("UPI QR Code Properties:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #CBD5E1;");

        ComboBox<String> srcCb = new ComboBox<>(FXCollections.observableArrayList("upi_amount", "upi", "custom"));
        srcCb.setValue(el.getQrSource() != null ? el.getQrSource() : "upi_amount");
        srcCb.valueProperty().addListener((obs, o, v) -> { el.setQrSource(v); refreshCanvas(); });

        TextField customTf = new TextField(el.getQrCustom());
        customTf.setPromptText("Custom UPI payload / URL");
        customTf.textProperty().addListener((obs, o, v) -> { el.setQrCustom(v); refreshCanvas(); });

        sec.getChildren().addAll(title, new Label("Data Source:"), srcCb, new Label("Custom URL:"), customTf);
        propBox.getChildren().add(sec);
    }

    private void buildBarcodeProperties(TemplateElement el) {
        VBox sec = new VBox(8);
        Label title = new Label("Barcode Properties:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #CBD5E1;");

        TextField tf = new TextField(el.getBarcodeData());
        tf.setPromptText("e.g. {{invoice_no}} or {{po_no}}");
        tf.textProperty().addListener((obs, o, v) -> { el.setBarcodeData(v); refreshCanvas(); });

        CheckBox textCb = new CheckBox("Show text below barcode lines");
        textCb.setSelected(el.isBarcodeShowText());
        textCb.setOnAction(e -> { el.setBarcodeShowText(textCb.isSelected()); refreshCanvas(); });

        sec.getChildren().addAll(title, new Label("Payload:"), tf, textCb);
        propBox.getChildren().add(sec);
    }

    private void buildTableProperties(TemplateElement el) {
        VBox sec = new VBox(10);
        Label title = new Label("Itemized Table Settings & Colors:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #CBD5E1;");

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);

        ColorPicker hBgPicker = new ColorPicker(hexToColor(el.getHeaderBg(), Color.web("#efe9db")));
        hBgPicker.setTooltip(new Tooltip("Header Background Color"));
        hBgPicker.setOnAction(e -> { el.setHeaderBg(colorToHex(hBgPicker.getValue())); refreshCanvas(); });
        grid.add(new Label("Header BG:"), 0, 0);
        grid.add(hBgPicker, 1, 0);

        ColorPicker hTextPicker = new ColorPicker(hexToColor(el.getHeaderColor(), Color.web("#1a1a1a")));
        hTextPicker.setTooltip(new Tooltip("Header Text Color"));
        hTextPicker.setOnAction(e -> { el.setHeaderColor(colorToHex(hTextPicker.getValue())); refreshCanvas(); });
        grid.add(new Label("Header Text:"), 0, 1);
        grid.add(hTextPicker, 1, 1);

        Spinner<Double> rowH = new Spinner<>(4.0, 25.0, el.getRowHeight() > 0 ? el.getRowHeight() : 7.0, 0.5);
        rowH.setEditable(true);
        rowH.valueProperty().addListener((obs, o, v) -> { el.setRowHeight(v); refreshCanvas(); });
        grid.add(new Label("Row Height (mm):"), 0, 2);
        grid.add(rowH, 1, 2);

        Spinner<Double> fontS = new Spinner<>(5.0, 20.0, el.getFontSize() > 0 ? el.getFontSize() : 8.5, 0.5);
        fontS.setEditable(true);
        fontS.valueProperty().addListener((obs, o, v) -> { el.setFontSize(v); refreshCanvas(); });
        grid.add(new Label("Font Size:"), 0, 3);
        grid.add(fontS, 1, 3);

        CheckBox zebraCb = new CheckBox("Zebra Row Striping");
        zebraCb.setSelected(el.isShowZebra());
        zebraCb.setOnAction(e -> { el.setShowZebra(zebraCb.isSelected()); refreshCanvas(); });

        // Columns Manager
        List<TableColumn> cols = el.getColumns();
        if (cols == null) {
            cols = PresetTemplates.defaultItemColumns();
            el.setColumns(cols);
        }

        Label colHeader = new Label("Table Columns (" + cols.size() + "):");
        colHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #CBD5E1; -fx-padding: 8 0 0 0;");

        VBox colsList = new VBox(6);
        for (int i = 0; i < cols.size(); i++) {
            TableColumn c = cols.get(i);
            int idx = i;

            HBox colRow = new HBox(6);
            colRow.setAlignment(Pos.CENTER_LEFT);
            colRow.setStyle("-fx-background-color: #141A24; -fx-padding: 4 6; -fx-background-radius: 4; -fx-border-color: #232B38; -fx-border-radius: 4;");

            TextField lblField = new TextField(c.getLabel());
            lblField.setMinWidth(75);
            HBox.setHgrow(lblField, Priority.ALWAYS);
            lblField.setTooltip(new Tooltip("Column Header Title"));
            lblField.textProperty().addListener((obs, o, v) -> { c.setLabel(v); refreshCanvas(); });

            ComboBox<String> keyCombo = new ComboBox<>(FXCollections.observableArrayList(
                    "sr", "desc", "hsn", "qty", "unit", "rate", "disc", "taxable", "gst", "amount",
                    "batch_no", "exp_date", "mrp", "serial_no", "part_no"
            ));
            keyCombo.setEditable(true);
            keyCombo.setValue(c.getKey() != null ? c.getKey() : "desc");
            keyCombo.setPrefWidth(95);
            keyCombo.setTooltip(new Tooltip("Data Field Binding (Pick preset or type custom key)"));
            keyCombo.valueProperty().addListener((obs, o, v) -> { c.setKey(v); refreshCanvas(); });
            keyCombo.getEditor().textProperty().addListener((obs, o, v) -> { c.setKey(v); refreshCanvas(); });

            Spinner<Double> widthSpin = new Spinner<>(1.0, 100.0, c.getWidth(), 1.0);
            widthSpin.setPrefWidth(65);
            widthSpin.setEditable(true);
            widthSpin.setTooltip(new Tooltip("Width percentage (%)"));
            widthSpin.valueProperty().addListener((obs, o, v) -> { c.setWidth(v); refreshCanvas(); });

            ComboBox<String> alignCombo = new ComboBox<>(FXCollections.observableArrayList("left", "center", "right"));
            alignCombo.setValue(c.getAlign() != null ? c.getAlign() : "left");
            alignCombo.setPrefWidth(60);
            alignCombo.valueProperty().addListener((obs, o, v) -> { c.setAlign(v); refreshCanvas(); });

            Button rmBtn = new Button("✕");
            rmBtn.getStyleClass().addAll("button-sm", "button-danger");
            rmBtn.setTooltip(new Tooltip("Remove column"));
            rmBtn.setOnAction(e -> {
                el.getColumns().remove(idx);
                updatePropertiesPanel();
                refreshCanvas();
            });

            colRow.getChildren().addAll(lblField, keyCombo, widthSpin, alignCombo, rmBtn);
            colsList.getChildren().add(colRow);
        }

        HBox colActions = new HBox(8);
        Button addColBtn = createToolbarBtn("+ Add Column", "Add a new column to the table", () -> {
            el.getColumns().add(new TableColumn("desc", "New Column", 15.0, "left"));
            updatePropertiesPanel();
            refreshCanvas();
        });

        Button presetGst = createToolbarBtn("GST Standard (8)", "Reset to standard 8-column GST table", () -> {
            el.setColumns(PresetTemplates.defaultItemColumns());
            updatePropertiesPanel();
            refreshCanvas();
        });

        Button presetSimple = createToolbarBtn("Simple (5)", "Reset to 5-column simple invoice table", () -> {
            List<TableColumn> sc = new ArrayList<>();
            sc.add(new TableColumn("sr", "Sr", 8, "center"));
            sc.add(new TableColumn("desc", "Description", 48, "left"));
            sc.add(new TableColumn("qty", "Qty", 12, "right"));
            sc.add(new TableColumn("rate", "Rate", 16, "right"));
            sc.add(new TableColumn("amount", "Amount", 16, "right"));
            el.setColumns(sc);
            updatePropertiesPanel();
            refreshCanvas();
        });

        colActions.getChildren().addAll(addColBtn, presetGst, presetSimple);

        sec.getChildren().addAll(title, grid, zebraCb, colHeader, colsList, colActions);
        propBox.getChildren().add(sec);
    }

    private String colorToHex(Color c) {
        if (c == null) return "#000000";
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private Color hexToColor(String hex, Color def) {
        try {
            if (hex == null || hex.isBlank() || "transparent".equalsIgnoreCase(hex)) return def;
            return Color.web(hex);
        } catch (Exception e) {
            return def;
        }
    }

    private void insertVariableIntoTarget(TextArea target, String placeholder, TemplateElement el) {
        if (placeholder == null || placeholder.isBlank() || target == null) return;
        try {
            IndexRange selection = target.getSelection();
            String cur = target.getText() != null ? target.getText() : "";
            int start = selection != null ? selection.getStart() : -1;
            int end = selection != null ? selection.getEnd() : -1;

            String updated;
            int newCaret;
            if (start >= 0 && end > start && end <= cur.length()) {
                updated = cur.substring(0, start) + placeholder + cur.substring(end);
                newCaret = start + placeholder.length();
            } else {
                int pos = target.getCaretPosition();
                if (pos < 0 || pos > cur.length()) pos = cur.length();
                updated = cur.substring(0, pos) + placeholder + cur.substring(pos);
                newCaret = pos + placeholder.length();
            }

            target.setText(updated);
            target.positionCaret(newCaret);

            if (el != null) {
                el.setText(updated);
            }
            saveState();
            refreshCanvas();
            target.requestFocus();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void showVariablePicker(TextArea target, TemplateElement el) {
        try {
            Stage dlg = new Stage();
            if (app != null && app.getPrimaryStage() != null) {
                dlg.initOwner(app.getPrimaryStage());
            }
            dlg.initModality(Modality.APPLICATION_MODAL);
            dlg.setTitle("Insert Template Variable");

            VBox root = new VBox(12);
            root.setPadding(new Insets(18));
            root.setPrefWidth(520);
            root.setStyle("-fx-background-color: #12161D; -fx-border-color: #D9A13B; -fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");

            Label titleLbl = new Label("Insert Template Variable");
            titleLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #F4F4F5;");

            Label subLbl = new Label("Choose a placeholder or double-click to insert directly into text:");
            subLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #8E9EB5;");

            TextField filterField = new TextField();
            filterField.setPromptText("Type to filter variables (e.g. buyer, total, gst, date, bank)...");
            filterField.setStyle("-fx-background-color: #0D1117; -fx-text-fill: #F4F4F5; -fx-font-size: 12px; -fx-border-color: #232B38; -fx-border-radius: 6; -fx-padding: 8 12;");

            List<VariableDef> vars = getComprehensiveVariablesList();
            FilteredList<VariableDef> filtered = new FilteredList<>(FXCollections.observableArrayList(vars), v -> true);
            filterField.textProperty().addListener((obs, o, v) -> {
                String q = v != null ? v.trim().toLowerCase() : "";
                filtered.setPredicate(item -> {
                    if (item == null) return false;
                    if (q.isEmpty()) return true;
                    return (item.getLabel() != null && item.getLabel().toLowerCase().contains(q)) ||
                           (item.getKey() != null && item.getKey().toLowerCase().contains(q)) ||
                           (item.getType() != null && item.getType().toLowerCase().contains(q));
                });
            });

            ListView<VariableDef> lv = new ListView<>(filtered);
            lv.setPrefHeight(280);
            lv.setStyle("-fx-background-color: #0D1117; -fx-control-inner-background: #0D1117; -fx-border-color: #232B38; -fx-border-radius: 6;");
            lv.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(VariableDef item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("-fx-background-color: transparent;");
                    } else {
                        HBox row = new HBox(8);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(4, 6, 4, 6));
                        row.setMouseTransparent(true);

                        Label nameLbl = new Label(item.getLabel() != null ? item.getLabel() : item.getKey());
                        nameLbl.setStyle("-fx-text-fill: #F4F4F5; -fx-font-weight: bold; -fx-font-size: 12px;");
                        HBox.setHgrow(nameLbl, Priority.ALWAYS);

                        Label keyPill = new Label("{{" + item.getKey() + "}}");
                        keyPill.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-text-fill: #D9A13B; -fx-background-color: #1A222D; -fx-border-color: rgba(217,161,59,0.3); -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2 6;");

                        Label typeBadge = new Label(item.getType() != null ? item.getType().toUpperCase() : "GENERAL");
                        typeBadge.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #8E9EB5; -fx-background-color: #161E2A; -fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 2 5;");

                        row.getChildren().addAll(nameLbl, keyPill, typeBadge);
                        setGraphic(row);
                        setText(null);
                    }
                }
            });

            Runnable doInsert = () -> {
                VariableDef sel = lv.getSelectionModel().getSelectedItem();
                if (sel == null && !filtered.isEmpty()) {
                    sel = filtered.get(0);
                }
                if (sel != null && sel.getKey() != null) {
                    insertVariableIntoTarget(target, "{{" + sel.getKey() + "}}", el);
                    dlg.close();
                }
            };

            lv.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    doInsert.run();
                }
            });

            HBox btnBar = new HBox(10);
            btnBar.setAlignment(Pos.CENTER_RIGHT);

            Button cancelBtn = new Button("Cancel");
            cancelBtn.setStyle("-fx-background-color: #1E2530; -fx-text-fill: #CBD5E1; -fx-font-size: 12px; -fx-padding: 7 16; -fx-cursor: hand; -fx-border-radius: 4; -fx-background-radius: 4;");
            cancelBtn.setOnAction(e -> dlg.close());

            Button insertBtn = new Button("Insert Variable");
            insertBtn.setStyle("-fx-background-color: #D9A13B; -fx-text-fill: #0D1117; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 7 18; -fx-cursor: hand; -fx-border-radius: 4; -fx-background-radius: 4;");
            insertBtn.setOnAction(e -> doInsert.run());

            btnBar.getChildren().addAll(cancelBtn, insertBtn);

            root.getChildren().addAll(titleLbl, subLbl, filterField, lv, btnBar);

            Scene scene = new Scene(root);
            DialogHelper.styleScene(scene);
            dlg.setScene(scene);
            dlg.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.show(app != null ? app.getRootPane() : this, "Variable Picker", "Could not open variable browser: " + ex.getMessage(), true);
        }
    }

    private List<VariableDef> getComprehensiveVariablesList() {
        Map<String, VariableDef> map = new LinkedHashMap<>();

        // Core Invoice & Paging
        map.put("invoice_no", new VariableDef("invoice_no", "Invoice Number", "INVOICE", true));
        map.put("invoice_date", new VariableDef("invoice_date", "Invoice Date", "INVOICE", true));
        map.put("due_date", new VariableDef("due_date", "Due Date", "INVOICE", true));
        map.put("doc_type", new VariableDef("doc_type", "Document Type Title", "INVOICE", true));
        map.put("copy_label", new VariableDef("copy_label", "Copy Label (Original/Duplicate)", "INVOICE", true));
        map.put("page_no", new VariableDef("page_no", "Current Page Number", "PAGING", true));
        map.put("page_count", new VariableDef("page_count", "Total Page Count", "PAGING", true));

        // Buyer / Customer
        map.put("buyer_name", new VariableDef("buyer_name", "Buyer / Customer Name", "BUYER", true));
        map.put("buyer_trade_name", new VariableDef("buyer_trade_name", "Buyer Trade Name", "BUYER", true));
        map.put("buyer_gstin", new VariableDef("buyer_gstin", "Buyer GSTIN", "BUYER", true));
        map.put("buyer_address", new VariableDef("buyer_address", "Buyer Billing Address", "BUYER", true));
        map.put("buyer_phone", new VariableDef("buyer_phone", "Buyer Phone", "BUYER", true));
        map.put("buyer_email", new VariableDef("buyer_email", "Buyer Email", "BUYER", true));
        map.put("buyer_state", new VariableDef("buyer_state", "Buyer State / Place of Supply", "BUYER", true));
        map.put("buyer_state_code", new VariableDef("buyer_state_code", "Buyer State Code (2-digit)", "BUYER", true));

        // Custom Buyer Fields (Configured under Settings!)
        try {
            if (settingsDao != null) {
                Settings set = settingsDao.getSettings();
                if (set != null && set.getBuyerFields() != null) {
                    for (BuyerFieldDef bf : set.getBuyerFields()) {
                        if (bf != null && bf.getKey() != null && !bf.getKey().isBlank()) {
                            String lbl = (bf.getLabel() != null && !bf.getLabel().isBlank()) ? bf.getLabel() : bf.getKey();
                            map.put(bf.getKey(), new VariableDef(bf.getKey(), lbl + " (Buyer Custom)", "BUYER CUSTOM", false));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Business Profile
        map.put("business_name", new VariableDef("business_name", "My Business Name", "BUSINESS", true));
        map.put("business_address", new VariableDef("business_address", "My Business Address", "BUSINESS", true));
        map.put("business_gst", new VariableDef("business_gst", "My Business GSTIN", "BUSINESS", true));
        map.put("business_phone", new VariableDef("business_phone", "My Business Phone", "BUSINESS", true));
        map.put("business_email", new VariableDef("business_email", "My Business Email", "BUSINESS", true));
        map.put("business_state", new VariableDef("business_state", "My Business State", "BUSINESS", true));
        map.put("terms", new VariableDef("terms", "Default Terms & Conditions", "BUSINESS", true));
        map.put("bank_name", new VariableDef("bank_name", "Bank Name", "BANK", true));
        map.put("bank_account", new VariableDef("bank_account", "Bank Account Number", "BANK", true));
        map.put("bank_ifsc", new VariableDef("bank_ifsc", "Bank IFSC Code", "BANK", true));
        map.put("bank_upi", new VariableDef("bank_upi", "Bank UPI ID", "BANK", true));
        map.put("notes", new VariableDef("notes", "Invoice Remarks / Notes", "INVOICE", true));

        // Computed Totals & Tax
        map.put("subtotal", new VariableDef("subtotal", "Subtotal (Before Tax)", "TOTALS", true));
        map.put("discount", new VariableDef("discount", "Total Discount", "TOTALS", true));
        map.put("taxable", new VariableDef("taxable", "Taxable Value", "TOTALS", true));
        map.put("cgst", new VariableDef("cgst", "CGST Amount", "TOTALS", true));
        map.put("sgst", new VariableDef("sgst", "SGST Amount", "TOTALS", true));
        map.put("igst", new VariableDef("igst", "IGST Amount", "TOTALS", true));
        map.put("round_off", new VariableDef("round_off", "Round Off", "TOTALS", true));
        map.put("grand_total", new VariableDef("grand_total", "Grand Total (Invoice Value)", "TOTALS", true));
        map.put("amount_in_words", new VariableDef("amount_in_words", "Amount in Words", "TOTALS", true));
        map.put("total_qty", new VariableDef("total_qty", "Total Item Quantity", "TOTALS", true));
        map.put("item_count", new VariableDef("item_count", "Total Items Count", "TOTALS", true));
        map.put("paid_amount", new VariableDef("paid_amount", "Amount Paid", "TOTALS", true));
        map.put("due_amount", new VariableDef("due_amount", "Balance Due", "TOTALS", true));
        map.put("payment_status", new VariableDef("payment_status", "Payment Status (Paid/Unpaid)", "TOTALS", true));

        // Transport & Logistics
        map.put("po_no", new VariableDef("po_no", "PO / Purchase Order No", "LOGISTICS", true));
        map.put("vehicle_no", new VariableDef("vehicle_no", "Vehicle Number", "LOGISTICS", true));
        map.put("transport_name", new VariableDef("transport_name", "Transporter Name", "LOGISTICS", true));
        map.put("e_way_bill", new VariableDef("e_way_bill", "E-Way Bill Number", "LOGISTICS", true));

        // User custom variables from DB
        try {
            if (variableDao != null) {
                List<VariableDef> userVars = variableDao.getAllVariables();
                if (userVars != null) {
                    for (VariableDef uv : userVars) {
                        if (uv != null && uv.getKey() != null && !uv.getKey().isBlank()) {
                            map.putIfAbsent(uv.getKey(), uv);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(map.values());
    }

    private void showPageSettingsDialog() {
        Dialog<Boolean> dlg = new Dialog<>();
        DialogHelper.styleDialog(dlg);
        dlg.setTitle("Page Configuration");
        dlg.setHeaderText("Page Dimensions & Margins");

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));

        ComboBox<PageSizeName> sizeCb = new ComboBox<>(FXCollections.observableArrayList(PageSizeName.values()));
        sizeCb.setValue(template.getPage().getSizeName());
        g.add(new Label("Page Size:"), 0, 0); g.add(sizeCb, 1, 0, 3, 1);

        TextField wField = new TextField(String.valueOf(template.getPage().getWidth()));
        TextField hField = new TextField(String.valueOf(template.getPage().getHeight()));
        g.add(new Label("Width (mm):"), 0, 1); g.add(wField, 1, 1);
        g.add(new Label("Height (mm):"), 2, 1); g.add(hField, 3, 1);

        sizeCb.setOnAction(e -> {
            PageSizeName s = sizeCb.getValue();
            wField.setText(String.valueOf(s.getDefaultWidth()));
            hField.setText(String.valueOf(s.getDefaultHeight()));
        });

        CheckBox autoH = new CheckBox("Continuous roll (Auto-height for POS thermal rolls)");
        autoH.setSelected(template.getPage().isAutoHeight());
        g.add(autoH, 0, 2, 4, 1);

        // Margins Header
        Label mgHeader = new Label("PAGE MARGINS (MM)");
        mgHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #D9A13B; -fx-font-size: 11px;");
        g.add(mgHeader, 0, 3, 4, 1);

        PageConfig.Margins currentMg = template.getPage().getMargin();
        if (currentMg == null) {
            currentMg = new PageConfig.Margins(8, 8, 8, 8);
            template.getPage().setMargin(currentMg);
        }

        TextField topField = new TextField(String.valueOf(currentMg.getTop()));
        TextField bottomField = new TextField(String.valueOf(currentMg.getBottom()));
        TextField leftField = new TextField(String.valueOf(currentMg.getLeft()));
        TextField rightField = new TextField(String.valueOf(currentMg.getRight()));

        g.add(new Label("Top:"), 0, 4); g.add(topField, 1, 4);
        g.add(new Label("Bottom:"), 2, 4); g.add(bottomField, 3, 4);
        g.add(new Label("Left:"), 0, 5); g.add(leftField, 1, 5);
        g.add(new Label("Right:"), 2, 5); g.add(rightField, 3, 5);

        HBox presetBox = new HBox(6);
        Button pStd = new Button("Standard (8mm)");
        pStd.setOnAction(e -> { topField.setText("8"); bottomField.setText("8"); leftField.setText("8"); rightField.setText("8"); });
        Button pCmp = new Button("Compact (5mm)");
        pCmp.setOnAction(e -> { topField.setText("5"); bottomField.setText("5"); leftField.setText("5"); rightField.setText("5"); });
        Button pWide = new Button("Wide (12mm)");
        pWide.setOnAction(e -> { topField.setText("12"); bottomField.setText("12"); leftField.setText("12"); rightField.setText("12"); });
        Button pZero = new Button("Zero (0mm)");
        pZero.setOnAction(e -> { topField.setText("0"); bottomField.setText("0"); leftField.setText("0"); rightField.setText("0"); });
        presetBox.getChildren().addAll(pStd, pCmp, pWide, pZero);
        g.add(presetBox, 0, 6, 4, 1);

        CheckBox shiftCb = new CheckBox("Shift elements when margins change");
        shiftCb.setSelected(shiftWithMargins);
        g.add(shiftCb, 0, 7, 4, 1);

        dlg.getDialogPane().setContent(g);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(btn -> btn == ButtonType.OK);
        dlg.showAndWait().ifPresent(ok -> {
            if (ok) {
                try {
                    double newTop = Double.parseDouble(topField.getText());
                    double newBottom = Double.parseDouble(bottomField.getText());
                    double newLeft = Double.parseDouble(leftField.getText());
                    double newRight = Double.parseDouble(rightField.getText());

                    shiftWithMargins = shiftCb.isSelected();
                    double oldLeft = template.getPage().getMargin().getLeft();
                    double oldTop = template.getPage().getMargin().getTop();
                    double deltaX = newLeft - oldLeft;
                    double deltaY = newTop - oldTop;

                    template.getPage().setSizeName(sizeCb.getValue());
                    template.getPage().setWidth(Double.parseDouble(wField.getText()));
                    template.getPage().setHeight(Double.parseDouble(hField.getText()));
                    template.getPage().setAutoHeight(autoH.isSelected());

                    template.getPage().getMargin().setTop(newTop);
                    template.getPage().getMargin().setBottom(newBottom);
                    template.getPage().getMargin().setLeft(newLeft);
                    template.getPage().getMargin().setRight(newRight);

                    if (shiftWithMargins) {
                        for (TemplateElement el : template.getElements()) {
                            if (!el.isLocked()) {
                                double nx = Math.round(Math.max(0, Math.min(template.getPage().getWidth() - el.getW(), el.getX() + deltaX)) * 10.0) / 10.0;
                                double ny = Math.round(Math.max(0, Math.min(template.getPage().getHeight() - el.getH(), el.getY() + deltaY)) * 10.0) / 10.0;
                                el.setX(nx);
                                el.setY(ny);
                            }
                        }
                    }

                    refreshCanvas();
                    updatePropertiesPanel();
                    saveState();
                    Toast.show(app.getRootPane(), "Page Configured", "Page dimensions and margins updated.", false);
                } catch (Exception ex) {
                    Toast.show(app.getRootPane(), "Invalid Values", "Width, height, and margins must be valid numbers.", true);
                }
            }
        });
    }

    private void buildPageAndMarginProperties() {
        PageConfig page = template.getPage();
        PageConfig.Margins mg = page.getMargin();
        if (mg == null) {
            mg = new PageConfig.Margins(8, 8, 8, 8);
            page.setMargin(mg);
        }

        // Header Title Card
        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        Label titleLbl = new Label("PAGE & MARGIN SETTINGS");
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #D9A13B; -fx-letter-spacing: 0.8;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label("CANVAS");
        badge.setStyle("-fx-font-size: 10px; -fx-text-fill: #8E9EB5; -fx-background-color: #1A222D; -fx-padding: 2 6; -fx-background-radius: 4;");
        headerRow.getChildren().addAll(titleLbl, spacer, badge);
        propBox.getChildren().add(headerRow);

        // Section 1: Dimensions
        TitledPane dimPane = new TitledPane();
        dimPane.setText("Page Dimensions");
        dimPane.setExpanded(true);

        GridPane dimGrid = new GridPane();
        dimGrid.setHgap(8); dimGrid.setVgap(8); dimGrid.setPadding(new Insets(8));

        dimGrid.add(new Label("Paper Size:"), 0, 0);
        ComboBox<PageSizeName> sizeCb = new ComboBox<>(FXCollections.observableArrayList(PageSizeName.values()));
        sizeCb.setValue(page.getSizeName());
        sizeCb.setStyle("-fx-font-size: 11px;");
        dimGrid.add(sizeCb, 1, 0, 3, 1);

        dimGrid.add(new Label("W (mm):"), 0, 1);
        Spinner<Double> wSpin = new Spinner<>(20.0, 1000.0, page.getWidth(), 1.0);
        wSpin.setEditable(true);
        wSpin.setPrefWidth(85);
        wSpin.valueProperty().addListener((obs, o, v) -> {
            page.setWidth(v);
            refreshCanvas();
        });
        dimGrid.add(wSpin, 1, 1);

        dimGrid.add(new Label("H (mm):"), 2, 1);
        Spinner<Double> hSpin = new Spinner<>(20.0, 2000.0, page.getHeight(), 1.0);
        hSpin.setEditable(true);
        hSpin.setPrefWidth(85);
        hSpin.valueProperty().addListener((obs, o, v) -> {
            page.setHeight(v);
            refreshCanvas();
        });
        dimGrid.add(hSpin, 3, 1);

        sizeCb.setOnAction(e -> {
            PageSizeName s = sizeCb.getValue();
            page.setSizeName(s);
            wSpin.getValueFactory().setValue(s.getDefaultWidth());
            hSpin.getValueFactory().setValue(s.getDefaultHeight());
            page.setWidth(s.getDefaultWidth());
            page.setHeight(s.getDefaultHeight());
            refreshCanvas();
            saveState();
        });

        CheckBox autoHCb = new CheckBox("Continuous roll (Auto-height for POS)");
        autoHCb.setSelected(page.isAutoHeight());
        autoHCb.setStyle("-fx-font-size: 11px; -fx-text-fill: #CBD5E1;");
        autoHCb.selectedProperty().addListener((obs, o, v) -> {
            page.setAutoHeight(v);
            refreshCanvas();
            saveState();
        });
        dimGrid.add(autoHCb, 0, 2, 4, 1);

        dimPane.setContent(dimGrid);
        propBox.getChildren().add(dimPane);

        // Section 2: Page Margins
        TitledPane mgPane = new TitledPane();
        mgPane.setText("Page Margins (mm)");
        mgPane.setExpanded(true);

        VBox mgBox = new VBox(8);
        mgBox.setPadding(new Insets(8));

        HBox shiftRow = new HBox(6);
        shiftRow.setAlignment(Pos.CENTER_LEFT);
        CheckBox shiftCb = new CheckBox("Shift elements when margins change");
        shiftCb.setSelected(shiftWithMargins);
        shiftCb.setStyle("-fx-font-size: 11px; -fx-text-fill: #CBD5E1;");
        shiftCb.selectedProperty().addListener((obs, o, v) -> shiftWithMargins = v);
        shiftRow.getChildren().add(shiftCb);
        mgBox.getChildren().add(shiftRow);

        GridPane mgGrid = new GridPane();
        mgGrid.setHgap(8); mgGrid.setVgap(8);

        // Top Spinner
        mgGrid.add(new Label("Top:"), 0, 0);
        Spinner<Double> topSpin = new Spinner<>(0.0, 60.0, mg.getTop(), 1.0);
        topSpin.setEditable(true); topSpin.setPrefWidth(85);
        topSpin.valueProperty().addListener((obs, o, v) -> updateMargin("top", v, shiftWithMargins));
        mgGrid.add(topSpin, 1, 0);

        // Bottom Spinner
        mgGrid.add(new Label("Bottom:"), 2, 0);
        Spinner<Double> botSpin = new Spinner<>(0.0, 60.0, mg.getBottom(), 1.0);
        botSpin.setEditable(true); botSpin.setPrefWidth(85);
        botSpin.valueProperty().addListener((obs, o, v) -> updateMargin("bottom", v, shiftWithMargins));
        mgGrid.add(botSpin, 3, 0);

        // Left Spinner
        mgGrid.add(new Label("Left:"), 0, 1);
        Spinner<Double> leftSpin = new Spinner<>(0.0, 60.0, mg.getLeft(), 1.0);
        leftSpin.setEditable(true); leftSpin.setPrefWidth(85);
        leftSpin.valueProperty().addListener((obs, o, v) -> updateMargin("left", v, shiftWithMargins));
        mgGrid.add(leftSpin, 1, 1);

        // Right Spinner
        mgGrid.add(new Label("Right:"), 2, 1);
        Spinner<Double> rightSpin = new Spinner<>(0.0, 60.0, mg.getRight(), 1.0);
        rightSpin.setEditable(true); rightSpin.setPrefWidth(85);
        rightSpin.valueProperty().addListener((obs, o, v) -> updateMargin("right", v, shiftWithMargins));
        mgGrid.add(rightSpin, 3, 1);

        mgBox.getChildren().add(mgGrid);

        // Quick Preset Chips
        Label presetLbl = new Label("QUICK PRESETS:");
        presetLbl.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
        FlowPane presetChips = new FlowPane(4, 4);

        Button pStd = createToolbarBtn("Standard (8mm)", "Set 8mm margins for all sides", () -> applyMarginPreset(8, 8, 8, 8));
        pStd.setStyle("-fx-font-size: 10px; -fx-padding: 3 8;");
        Button pCmp = createToolbarBtn("Compact (5mm)", "Set 5mm margins for all sides", () -> applyMarginPreset(5, 5, 5, 5));
        pCmp.setStyle("-fx-font-size: 10px; -fx-padding: 3 8;");
        Button pWide = createToolbarBtn("Wide (12mm)", "Set 12mm margins for all sides", () -> applyMarginPreset(12, 12, 12, 12));
        pWide.setStyle("-fx-font-size: 10px; -fx-padding: 3 8;");
        Button pZero = createToolbarBtn("Zero (0mm)", "Full bleed 0mm margins", () -> applyMarginPreset(0, 0, 0, 0));
        pZero.setStyle("-fx-font-size: 10px; -fx-padding: 3 8;");

        presetChips.getChildren().addAll(pStd, pCmp, pWide, pZero);
        mgBox.getChildren().addAll(presetLbl, presetChips);

        // Align Action Button
        Button alignBtn = new Button("⚡ Align All Elements to Margins");
        alignBtn.setMaxWidth(Double.MAX_VALUE);
        alignBtn.setStyle("-fx-background-color: #1A2332; -fx-text-fill: #D9A13B; -fx-font-weight: bold; -fx-font-size: 11px; -fx-border-color: rgba(217,161,59,0.4); -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 7 12; -fx-cursor: hand;");
        alignBtn.setTooltip(new Tooltip("Align outermost unlocked elements with margins and fit wide tables to printable width"));
        alignBtn.setOnAction(e -> alignElementsToMargins());
        mgBox.getChildren().add(alignBtn);

        // Open Dialog Button
        Button openDlgBtn = new Button("⚙ Open Full Page Dialog...");
        openDlgBtn.setMaxWidth(Double.MAX_VALUE);
        openDlgBtn.setStyle("-fx-background-color: #121720; -fx-text-fill: #CBD5E1; -fx-font-size: 11px; -fx-border-color: #232B38; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 12; -fx-cursor: hand;");
        openDlgBtn.setOnAction(e -> showPageSettingsDialog());
        mgBox.getChildren().add(openDlgBtn);

        // Helper guide description
        Label guideNote = new Label("Dashed blue guides on canvas show the printable margin boundary. All elements will respect these boundaries in bill preview, printing, and PDF export.");
        guideNote.setWrapText(true);
        guideNote.setStyle("-fx-font-size: 10px; -fx-text-fill: #8E9EB5; -fx-line-spacing: 2;");
        mgBox.getChildren().add(guideNote);

        mgPane.setContent(mgBox);
        propBox.getChildren().add(mgPane);
    }

    private void updateMargin(String side, double val, boolean shift) {
        PageConfig page = template.getPage();
        PageConfig.Margins mg = page.getMargin();
        if (mg == null) {
            mg = new PageConfig.Margins(8, 8, 8, 8);
            page.setMargin(mg);
        }

        double oldVal;
        switch (side.toLowerCase()) {
            case "top" -> oldVal = mg.getTop();
            case "bottom" -> oldVal = mg.getBottom();
            case "left" -> oldVal = mg.getLeft();
            case "right" -> oldVal = mg.getRight();
            default -> oldVal = 8;
        }

        double newVal = Math.max(0, Math.min(60, val));
        double delta = newVal - oldVal;
        if (Math.abs(delta) < 0.001) return;

        saveState();

        switch (side.toLowerCase()) {
            case "top" -> mg.setTop(newVal);
            case "bottom" -> mg.setBottom(newVal);
            case "left" -> mg.setLeft(newVal);
            case "right" -> mg.setRight(newVal);
        }

        if (shift) {
            if ("left".equalsIgnoreCase(side)) {
                for (TemplateElement el : template.getElements()) {
                    if (!el.isLocked()) {
                        double nx = Math.round(Math.max(0, Math.min(page.getWidth() - el.getW(), el.getX() + delta)) * 10.0) / 10.0;
                        el.setX(nx);
                    }
                }
            } else if ("top".equalsIgnoreCase(side)) {
                for (TemplateElement el : template.getElements()) {
                    if (!el.isLocked()) {
                        double ny = Math.round(Math.max(0, Math.min(page.getHeight() - el.getH(), el.getY() + delta)) * 10.0) / 10.0;
                        el.setY(ny);
                    }
                }
            }
        }

        refreshCanvas();
    }

    private void applyMarginPreset(double top, double bottom, double left, double right) {
        saveState();
        PageConfig page = template.getPage();
        PageConfig.Margins mg = page.getMargin();
        if (mg == null) {
            mg = new PageConfig.Margins(8, 8, 8, 8);
            page.setMargin(mg);
        }

        double oldLeft = mg.getLeft();
        double oldTop = mg.getTop();
        double deltaX = left - oldLeft;
        double deltaY = top - oldTop;

        mg.setTop(top);
        mg.setBottom(bottom);
        mg.setLeft(left);
        mg.setRight(right);

        if (shiftWithMargins) {
            for (TemplateElement el : template.getElements()) {
                if (!el.isLocked()) {
                    double nx = Math.round(Math.max(0, Math.min(page.getWidth() - el.getW(), el.getX() + deltaX)) * 10.0) / 10.0;
                    double ny = Math.round(Math.max(0, Math.min(page.getHeight() - el.getH(), el.getY() + deltaY)) * 10.0) / 10.0;
                    el.setX(nx);
                    el.setY(ny);
                }
            }
        }

        refreshCanvas();
        updatePropertiesPanel();
        Toast.show(app.getRootPane(), "Margins Applied",
                String.format("Margins set to T:%.0f B:%.0f L:%.0f R:%.0f mm", top, bottom, left, right), false);
    }

    private void alignElementsToMargins() {
        if (template.getElements().isEmpty()) return;
        saveState();

        PageConfig page = template.getPage();
        PageConfig.Margins mg = page.getMargin();
        if (mg == null) {
            mg = new PageConfig.Margins(8, 8, 8, 8);
            page.setMargin(mg);
        }

        List<TemplateElement> unlocked = template.getElements().stream()
                .filter(e -> !e.isLocked() && !e.isHidden())
                .toList();
        if (unlocked.isEmpty()) return;

        double minX = unlocked.stream().mapToDouble(TemplateElement::getX).min().orElse(0);
        double minY = unlocked.stream().mapToDouble(TemplateElement::getY).min().orElse(0);

        double shiftX = mg.getLeft() - minX;
        double shiftY = mg.getTop() - minY;
        double contentWidth = Math.max(10, page.getWidth() - mg.getLeft() - mg.getRight());

        for (TemplateElement el : unlocked) {
            double nx = Math.round(Math.max(0, Math.min(page.getWidth() - el.getW(), el.getX() + shiftX)) * 10.0) / 10.0;
            double ny = Math.round(Math.max(0, Math.min(page.getHeight() - el.getH(), el.getY() + shiftY)) * 10.0) / 10.0;
            el.setX(nx);
            el.setY(ny);

            if ((el.getType() == ElementType.TABLE || el.getType() == ElementType.LINE || el.getType() == ElementType.RECT)
                    && el.getW() >= page.getWidth() - 30) {
                el.setW(Math.round(contentWidth * 10.0) / 10.0);
                el.setX(mg.getLeft());
            }
        }

        refreshCanvas();
        updatePropertiesPanel();
        Toast.show(app.getRootPane(), "Elements Aligned",
                String.format("Elements aligned to margins (Shifted X:%+.1fmm, Y:%+.1fmm, Content Width: %.1fmm)",
                        shiftX, shiftY, contentWidth), false);
    }

    private void addElement(ElementType type) {
        TemplateElement el = new TemplateElement();
        el.setId("el_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        el.setType(type);
        el.setX(20);
        el.setY(20);
        el.setW(40);
        el.setH(10);

        switch (type) {
            case TEXT: el.setText("New text"); break;
            case RECT: el.setW(60); el.setH(30); el.setBg("#f4f1ea"); break;
            case LINE: el.setW(80); el.setH(1); el.setBorderWidth(0.5); break;
            case IMAGE: el.setW(30); el.setH(25); el.setUseBusinessLogo(true); break;
            case QRCODE: el.setW(24); el.setH(24); break;
            case BARCODE: el.setW(45); el.setH(14); break;
            case TABLE:
                el.setW(190); el.setH(30);
                el.setColumns(PresetTemplates.defaultItemColumns());
                break;
        }

        template.getElements().add(el);
        selectedElement = el;
        saveState();
        refreshCanvas();
        updatePropertiesPanel();
        refreshLayersList();
    }

    private void duplicateSelected() {
        if (selectedElement == null) return;
        TemplateElement copy = new TemplateElement();
        copy.setId("el_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        copy.setType(selectedElement.getType());
        copy.setX(selectedElement.getX() + 5);
        copy.setY(selectedElement.getY() + 5);
        copy.setW(selectedElement.getW());
        copy.setH(selectedElement.getH());
        copy.setText(selectedElement.getText());
        copy.setColor(selectedElement.getColor());
        copy.setBg(selectedElement.getBg());
        copy.setBorderColor(selectedElement.getBorderColor());
        copy.setBorderWidth(selectedElement.getBorderWidth());
        copy.setFontSize(selectedElement.getFontSize());
        copy.setFontWeight(selectedElement.getFontWeight());
        copy.setItalic(selectedElement.isItalic());
        copy.setAlign(selectedElement.getAlign());
        copy.setHeaderBg(selectedElement.getHeaderBg());
        copy.setHeaderColor(selectedElement.getHeaderColor());
        copy.setRowHeight(selectedElement.getRowHeight());
        copy.setShowZebra(selectedElement.isShowZebra());
        if (selectedElement.getColumns() != null) {
            List<TableColumn> copiedCols = new ArrayList<>();
            for (TableColumn c : selectedElement.getColumns()) {
                copiedCols.add(new TableColumn(c.getKey(), c.getLabel(), c.getWidth(), c.getAlign()));
            }
            copy.setColumns(copiedCols);
        }

        template.getElements().add(copy);
        selectedElement = copy;
        saveState();
        refreshCanvas();
        updatePropertiesPanel();
        refreshLayersList();
    }

    private void deleteSelected() {
        if (selectedElement == null) return;
        template.getElements().remove(selectedElement);
        selectedElement = null;
        saveState();
        refreshCanvas();
        updatePropertiesPanel();
        refreshLayersList();
    }

    private void moveLayer(int delta) {
        if (selectedElement == null) return;
        int idx = template.getElements().indexOf(selectedElement);
        int target = idx + delta;
        if (target >= 0 && target < template.getElements().size()) {
            Collections.swap(template.getElements(), idx, target);
            saveState();
            refreshCanvas();
            refreshLayersList();
        }
    }

    private void syncLayersListSelection() {
        isUpdatingLayersSelection = true;
        try {
            if (selectedElement != null) {
                layersList.getSelectionModel().select(selectedElement);
            } else {
                layersList.getSelectionModel().clearSelection();
            }
        } finally {
            isUpdatingLayersSelection = false;
        }
    }

    private void refreshLayersList() {
        layersList.setItems(FXCollections.observableArrayList(template.getElements()));
        syncLayersListSelection();
    }

    private void saveTemplate() {
        template.setUpdatedAt(Instant.now().toString());
        templateDao.saveTemplate(template);
        app.reloadAllData();
        Toast.show(app.getRootPane(), "Template Saved", "\"" + template.getName() + "\" saved successfully.", false);
    }

    private void saveState() {
        try {
            String json = mapper.writeValueAsString(template);
            if (!undoStack.isEmpty() && undoStack.peek().equals(json)) {
                return;
            }
            undoStack.push(json);
            if (undoStack.size() > 50) {
                undoStack.remove(0);
            }
            redoStack.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void undo() {
        if (undoStack.size() <= 1) {
            Toast.show(app.getRootPane(), "Undo", "Nothing to undo.", false);
            return;
        }
        try {
            String current = undoStack.pop();
            redoStack.push(current);
            String previous = undoStack.peek();
            this.template = mapper.readValue(previous, Template.class);
            this.selectedElement = null;
            nameField.setText(template.getName());
            refreshCanvas();
            updatePropertiesPanel();
            refreshLayersList();
            Toast.show(app.getRootPane(), "Undo", "Action undone.", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            Toast.show(app.getRootPane(), "Redo", "Nothing to redo.", false);
            return;
        }
        try {
            String next = redoStack.pop();
            undoStack.push(next);
            this.template = mapper.readValue(next, Template.class);
            this.selectedElement = null;
            nameField.setText(template.getName());
            refreshCanvas();
            updatePropertiesPanel();
            refreshLayersList();
            Toast.show(app.getRootPane(), "Redo", "Action redone.", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copySelected() {
        if (selectedElement == null) return;
        try {
            String json = mapper.writeValueAsString(selectedElement);
            clipboardElement = mapper.readValue(json, TemplateElement.class);
            Toast.show(app.getRootPane(), "Copied", "Element copied to clipboard.", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void pasteCopied() {
        if (clipboardElement == null) return;
        try {
            String json = mapper.writeValueAsString(clipboardElement);
            TemplateElement pasted = mapper.readValue(json, TemplateElement.class);
            pasted.setId("el_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            pasted.setX(pasted.getX() + 5);
            pasted.setY(pasted.getY() + 5);
            template.getElements().add(pasted);
            selectedElement = pasted;
            saveState();
            refreshCanvas();
            updatePropertiesPanel();
            refreshLayersList();
            Toast.show(app.getRootPane(), "Pasted", "Element pasted.", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final EventHandler<KeyEvent> sceneKeyFilter = this::handleGlobalKeyPress;
    private final EventHandler<KeyEvent> sceneKeyReleaseFilter = this::handleGlobalKeyRelease;

    private void setupKeyboardShortcuts() {
        setFocusTraversable(true);
        setOnMouseClicked(e -> requestFocus());

        sceneProperty().addListener((obs, oldS, newS) -> {
            if (oldS != null) {
                oldS.removeEventFilter(KeyEvent.KEY_PRESSED, sceneKeyFilter);
                oldS.removeEventFilter(KeyEvent.KEY_RELEASED, sceneKeyReleaseFilter);
            }
            if (newS != null) {
                newS.addEventFilter(KeyEvent.KEY_PRESSED, sceneKeyFilter);
                newS.addEventFilter(KeyEvent.KEY_RELEASED, sceneKeyReleaseFilter);
            }
        });
    }

    private void handleGlobalKeyPress(KeyEvent e) {
        if (!isVisible() || getScene() == null) return;

        boolean isTextInput = (e.getTarget() instanceof TextInputControl);

        if (e.isControlDown() && e.getCode() == KeyCode.S) {
            saveTemplate();
            e.consume();
            return;
        }

        // Spacebar holds pan tool
        if (e.getCode() == KeyCode.SPACE && !isTextInput) {
            if (!isSpaceDown) {
                isSpaceDown = true;
                if (canvasScrollPane != null) canvasScrollPane.setCursor(Cursor.OPEN_HAND);
                canvas.setCursor(Cursor.OPEN_HAND);
            }
            e.consume();
            return;
        }

        if (isTextInput) {
            return;
        }

        // Mode shortcuts
        if (e.getCode() == KeyCode.V && !e.isControlDown()) {
            setPanMode(false);
            e.consume();
            return;
        } else if (e.getCode() == KeyCode.H && !e.isControlDown()) {
            setPanMode(true);
            e.consume();
            return;
        }

        if (e.isControlDown() && e.getCode() == KeyCode.Z) {
            if (e.isShiftDown()) redo();
            else undo();
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.Y) {
            redo();
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.C) {
            copySelected();
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.V) {
            pasteCopied();
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.D) {
            duplicateSelected();
            e.consume();
        } else if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
            deleteSelected();
            e.consume();
        } else if (e.isControlDown() && (e.getCode() == KeyCode.DIGIT0 || e.getCode() == KeyCode.NUMPAD0)) {
            setZoom(1.0);
            e.consume();
        } else if (e.isControlDown() && (e.getCode() == KeyCode.EQUALS || e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.ADD)) {
            setZoom(zoom + 0.1);
            e.consume();
        } else if (e.isControlDown() && (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT)) {
            setZoom(zoom - 0.1);
            e.consume();
        } else if (e.getCode() == KeyCode.ESCAPE) {
            selectedElement = null;
            updateSelectionOverlay();
            updatePropertiesPanel();
            syncLayersListSelection();
            e.consume();
        } else if (selectedElement != null && !selectedElement.isLocked()) {
            double step = e.isShiftDown() ? 5.0 : 1.0;
            if (e.getCode() == KeyCode.LEFT) {
                selectedElement.setX(Math.max(0, selectedElement.getX() - step));
                refreshCanvas();
                updatePropertiesPanel();
                saveState();
                e.consume();
            } else if (e.getCode() == KeyCode.RIGHT) {
                selectedElement.setX(selectedElement.getX() + step);
                refreshCanvas();
                updatePropertiesPanel();
                saveState();
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                selectedElement.setY(Math.max(0, selectedElement.getY() - step));
                refreshCanvas();
                updatePropertiesPanel();
                saveState();
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN) {
                selectedElement.setY(selectedElement.getY() + step);
                refreshCanvas();
                updatePropertiesPanel();
                saveState();
                e.consume();
            }
        }
    }

    private void handleGlobalKeyRelease(KeyEvent e) {
        if (e.getCode() == KeyCode.SPACE) {
            isSpaceDown = false;
            Cursor normalCur = isPanMode ? Cursor.OPEN_HAND : Cursor.DEFAULT;
            if (canvasScrollPane != null) canvasScrollPane.setCursor(normalCur);
            if (centerWrapper != null) centerWrapper.setCursor(normalCur);
            canvas.setCursor(normalCur);
            e.consume();
        }
    }
}
