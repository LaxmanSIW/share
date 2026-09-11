package com.invoicestudio.ui.views;

import com.invoicestudio.db.SettingsDao;
import com.invoicestudio.db.TemplateDao;
import com.invoicestudio.db.VariableDao;
import com.invoicestudio.model.*;
import com.invoicestudio.model.TableColumn;
import com.invoicestudio.service.BarcodeService;
import com.invoicestudio.service.RenderContext;
import com.invoicestudio.ui.ColorPickerButton;
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
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
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
    private final Pane guidesPane = new Pane();  // magenta “magnet” snap guides
    private final Pane rulerPane = new Pane();   // top + left mm rulers
    private final Pane selectionPane = new Pane();
    private final Group scaleGroup = new Group(canvas);

    private double zoom = 0.9;
    private boolean snapToGrid = true;
    private boolean snapToObjects = true; // “Magnet”: snap edges to other elements / page center
    private boolean showGrid = true;
    private boolean showRulers = true;
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

    // Lightweight geometry sync during drags: instead of rebuilding the whole
    // properties panel every drag frame (expensive + focus-stealing), the four
    // position/size spinners are updated in place.
    private Spinner<Double> gxSpin, gySpin, gwSpin, ghSpin;
    private boolean syncingGeometry = false;
    private TextField namePropField;

    private static final double MM_PX = 3.7795275591; // ~96 DPI screen pixels per mm

    public TemplateDesigner(StudioApp app, Template template) {
        this.app = app;
        this.templateDao = new TemplateDao(app.getDb());
        this.settingsDao = new SettingsDao(app.getDb());
        this.variableDao = new VariableDao(app.getDb());
        this.template = template != null ? template : PresetTemplates.buildClassic();

        getStyleClass().add("bg-app");
        canvas.getStyleClass().add("bill-sheet-canvas");
        canvas.getChildren().addAll(gridPane, elementsPane, guidesPane, rulerPane, selectionPane);
        guidesPane.getStyleClass().add("guides-pane");
        rulerPane.getStyleClass().add("ruler-pane");
        gridPane.setMouseTransparent(true);
        guidesPane.setMouseTransparent(true);
        rulerPane.setMouseTransparent(true);
        selectionPane.setPickOnBounds(false);

        setTop(createToolbar());

        SplitPane mainSplit = new SplitPane();
        mainSplit.getStyleClass().add("bg-transparent");
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
        bar.getStyleClass().add("designer-toolbar");

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
        Button addEllipse = createToolbarBtn("+ Ellipse", "Add ellipse / circle shape", () -> addElement(ElementType.ELLIPSE));
        Button addStar = createToolbarBtn("+ Star", "Add star shape (adjustable points)", () -> addElement(ElementType.STAR));
        Button addArrow = createToolbarBtn("+ Arrow", "Add parametric arrow", () -> addElement(ElementType.ARROW));
        Button addQr = createToolbarBtn("+ QR Code", "Add dynamic UPI payment QR code", () -> addElement(ElementType.QRCODE));
        Button addBarcode = createToolbarBtn("+ Barcode", "Add Code 128 invoice barcode", () -> addElement(ElementType.BARCODE));

        Separator s2 = new Separator(javafx.geometry.Orientation.VERTICAL);

        // Zoom Controls
        Button zoomOut = createToolbarBtn("−", "Zoom Out (Ctrl -)", () -> setZoom(zoom - 0.1));
        
        zoomLabel.getStyleClass().add("zoom-value");
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

        CheckBox magnetCb = new CheckBox("Magnet");
        magnetCb.setSelected(true);
        magnetCb.setTooltip(new Tooltip("Magnet: snap object edges to other objects / page centre (M)"));
        magnetCb.selectedProperty().addListener((obs, old, val) -> {
            snapToObjects = val;
            if (!val) clearSnapGuides();
        });

        CheckBox rulerCb = new CheckBox("Rulers");
        rulerCb.setSelected(true);
        rulerCb.setTooltip(new Tooltip("Show horizontal & vertical mm rulers (R)"));
        rulerCb.selectedProperty().addListener((obs, old, val) -> {
            showRulers = val;
            refreshCanvas();
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button pageBtn = createToolbarBtn("Page Settings", "Configure page dimensions, paper size & margins", this::showPageSettingsDialog);

        Button helpBtn = createToolbarBtn("? Help", "All keyboard shortcuts & mouse controls (F1)", this::showShortcutsDialog);

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
                addText, addImage, addTable, addLine, addRect, addEllipse, addStar, addArrow, addQr, addBarcode, s2,
                selectToolBtn, panToolBtn, undoBtn, redoBtn,
                zoomOut, zoomLabel, zoomIn, zoom100, zoomFit, gridCb, snapCb, magnetCb, rulerCb, sp,
                pageBtn, helpBtn, saveBtn
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
            styleToolButton(selectToolBtn, false);
            styleToolButton(panToolBtn, true);
        } else {
            styleToolButton(selectToolBtn, true);
            styleToolButton(panToolBtn, false);
        }
    }

    /** Toggle tool state via CSS classes only (inline styles would kill hover). */
    private void styleToolButton(Button b, boolean active) {
        b.getStyleClass().removeAll("tool-active", "tool-idle");
        b.getStyleClass().add(active ? "tool-active" : "tool-idle");
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
        canvasScrollPane.getStyleClass().add("scroll-base");
        canvasScrollPane.setFitToWidth(false);
        canvasScrollPane.setFitToHeight(false);
        canvasScrollPane.setPannable(false); // Do not let JavaFX override cursor to pan hand!
        canvasScrollPane.setCursor(Cursor.DEFAULT);

        centerWrapper = new StackPane(scaleGroup);
        centerWrapper.getStyleClass().add("bg-base");
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
        side.getStyleClass().add("designer-side");

        Tab propTab = new Tab("Properties");
        propTab.setClosable(false);
        ScrollPane propScroll = new ScrollPane(propBox);
        propScroll.setFitToWidth(true);
        propScroll.getStyleClass().add("scroll-side");
        propBox.getStyleClass().add("bg-side");
        propBox.setPadding(new Insets(16));
        propTab.setContent(propScroll);

        Tab layersTab = new Tab("Layers");
        layersTab.setClosable(false);
        VBox layersBox = new VBox(10);
        layersBox.getStyleClass().add("bg-side");
        layersBox.setPadding(new Insets(14));

        layersList.getStyleClass().add("layers-list");
        layersList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TemplateElement item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);

                    HBox row = new HBox(6);
                    row.setAlignment(Pos.CENTER_LEFT);

                    // Eye — show/hide directly in the list
                    Button eyeBtn = new Button(item.isHidden() ? "🚫" : "👁");
                    eyeBtn.getStyleClass().add("layer-icon-btn");
                    eyeBtn.setTooltip(new Tooltip(item.isHidden() ? "Show object" : "Hide object"));
                    eyeBtn.setOnAction(e -> {
                        item.setHidden(!item.isHidden());
                        if (item == selectedElement && item.isHidden()) {
                            selectedElement = null;
                            updateSelectionOverlay();
                            updatePropertiesPanel();
                        }
                        saveState();
                        refreshCanvas();
                        refreshLayersList();
                    });

                    // Padlock — lock/unlock directly in the list
                    Button lockBtn = new Button(item.isLocked() ? "🔒" : "🔓");
                    lockBtn.getStyleClass().add("layer-icon-btn");
                    lockBtn.setTooltip(new Tooltip(item.isLocked() ? "Unlock object" : "Lock object"));
                    lockBtn.setOnAction(e -> {
                        item.setLocked(!item.isLocked());
                        saveState();
                        refreshLayersList();
                    });

                    VBox labels = new VBox(0);
                    Label nameLbl = new Label(defaultLayerName(item));
                    nameLbl.getStyleClass().add("layer-name");
                    Label typeLbl = new Label(item.getType().name());
                    typeLbl.getStyleClass().add("layer-type");
                    labels.getChildren().addAll(nameLbl, typeLbl);

                    // Double-click the name to rename inline
                    nameLbl.setOnMouseClicked(e -> {
                        if (e.getClickCount() != 2) return;
                        TextField edit = new TextField(item.getName() != null ? item.getName() : "");
                        edit.setPromptText("Object name");
                        edit.getStyleClass().add("layer-rename-field");
                        labels.getChildren().set(0, edit);
                        edit.requestFocus();
                        edit.selectAll();
                        Runnable commit = () -> {
                            item.setName(edit.getText() != null ? edit.getText().trim() : null);
                            if (item.getName() != null && item.getName().isEmpty()) item.setName(null);
                            saveState();
                            refreshLayersList();
                        };
                        edit.setOnAction(ev -> commit.run());
                        edit.focusedProperty().addListener((o, was, is) -> { if (!is) commit.run(); });
                    });

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    row.getChildren().addAll(eyeBtn, lockBtn, labels, spacer);
                    setGraphic(row);
                    if (!getStyleClass().contains("layer-cell-label")) getStyleClass().add("layer-cell-label");
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

        sideTabs.getStyleClass().add("bg-side");
        sideTabs.getTabs().addAll(propTab, layersTab);
        side.getChildren().add(sideTabs);
        VBox.setVgrow(sideTabs, Priority.ALWAYS);
        return side;
    }

    /** Layer row label: custom name when set, otherwise a readable type + snippet. */
    private String defaultLayerName(TemplateElement el) {
        if (el.getName() != null && !el.getName().isBlank()) return el.getName();
        String t = el.getType().name();
        if (el.getType() == ElementType.TEXT && el.getText() != null && !el.getText().isBlank()) {
            String s = el.getText().replace("\n", " ").trim();
            if (s.startsWith("{{") && s.contains("}}")) s = s.substring(2, s.indexOf("}}")) + " …";
            t += ": " + (s.length() > 18 ? s.substring(0, 18) + "…" : s);
        }
        return t;
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

        // mm Rulers (top & left overlay strips — inside the canvas coordinate
        // space, so ticks stay perfectly aligned with the page at any zoom)
        drawRulers(pageW, pageH);

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

    /** Draws the horizontal & vertical mm rulers as overlay strips (top/left of the page). */
    private void drawRulers(double pageW, double pageH) {
        rulerPane.getChildren().clear();
        rulerPane.setPrefSize(pageW, pageH);
        if (!showRulers) return;

        final double R = 16; // strip thickness (screen px, scales with zoom like the page)
        Color tickMinor = Color.web("#C3CBD9");
        Color tickMajor = Color.web("#64748B");

        Rectangle top = new Rectangle(pageW, R);
        top.setStyle("-fx-background-color: rgba(255,255,255,0.92);");
        rulerPane.getChildren().add(top);
        Rectangle left = new Rectangle(R, pageH);
        left.setStyle("-fx-background-color: rgba(255,255,255,0.92);");
        rulerPane.getChildren().add(left);

        for (int mm = 0; mm * MM_PX <= pageW; mm++) {
            double x = mm * MM_PX;
            boolean major = mm % 10 == 0;
            double len = major ? 11 : (mm % 5 == 0 ? 7 : 4);
            Line t = new Line(x, R - len, x, R);
            t.setStroke(major ? tickMajor : tickMinor);
            t.setStrokeWidth(major ? 1.0 : 0.5);
            rulerPane.getChildren().add(t);
            if (major && mm > 0) {
                Label lbl = new Label(String.valueOf(mm));
                lbl.setStyle("-fx-font-size: 7.5px; -fx-text-fill: #475569; -fx-font-family: 'Segoe UI', sans-serif;");
                lbl.setLayoutX(x + 1.5);
                lbl.setLayoutY(0);
                rulerPane.getChildren().add(lbl);
            }
        }
        for (int mm = 0; mm * MM_PX <= pageH; mm++) {
            double y = mm * MM_PX;
            boolean major = mm % 10 == 0;
            double len = major ? 11 : (mm % 5 == 0 ? 7 : 4);
            Line t = new Line(R - len, y, R, y);
            t.setStroke(major ? tickMajor : tickMinor);
            t.setStrokeWidth(major ? 1.0 : 0.5);
            rulerPane.getChildren().add(t);
            if (major && mm > 0) {
                Label lbl = new Label(String.valueOf(mm));
                lbl.setStyle("-fx-font-size: 7.5px; -fx-text-fill: #475569; -fx-font-family: 'Segoe UI', sans-serif;");
                lbl.setLayoutX(0.5);
                lbl.setLayoutY(y - 4);
                rulerPane.getChildren().add(lbl);
            }
        }
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

            // Magnet: snap object edges/centres onto other objects, margins, page centre
            if (snapToObjects) {
                double[] s = snapMove(el, newX, newY);
                newX = Math.max(0, s[0]);
                newY = Math.max(0, s[1]);
                showSnapGuides(s[2] >= 0 ? s[2] : null, s[3] >= 0 ? s[3] : null);
            } else {
                clearSnapGuides();
            }

            el.setX(newX);
            el.setY(newY);

            wrapper.setLayoutX(newX * MM_PX);
            wrapper.setLayoutY(newY * MM_PX);
            updateSelectionOverlayPos(newX * MM_PX, newY * MM_PX);
            syncGeometrySpinners();
            e.consume();
        });

        wrapper.setOnMouseReleased(e -> {
            if (isPanMode || isSpaceDown || e.getButton() == MouseButton.MIDDLE) return;
            clearSnapGuides();
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
        double wPx = el.getW() * MM_PX;
        double hPx = el.getH() * MM_PX;

        Pane selBox = new Pane();
        selBox.setLayoutX(el.getX() * MM_PX);
        selBox.setLayoutY(el.getY() * MM_PX);
        selBox.setPrefSize(wPx, hPx);
        selBox.setMinSize(wPx, hPx);
        selBox.setPickOnBounds(false);
        activeSelectionBox = selBox;

        // Selection Border: High-contrast gold dashed border with drop shadow
        Rectangle border = new Rectangle(wPx, hPx);
        border.setFill(Color.TRANSPARENT);
        border.setStroke(Color.web("#D9A13B"));
        border.setStrokeWidth(2.0);
        border.getStrokeDashArray().addAll(5.0, 4.0);
        border.setMouseTransparent(true);
        border.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 4, 0, 0, 1);");

        // Six resize anchors: NW, N, W, E, S, SE.
        // v3.0.0 adds the LEFT (W) and TOP (N) edge handles — previously objects
        // could only be resized from the right/bottom, with no point to scale from.
        Rectangle hNW = createHandleShape(Cursor.NW_RESIZE);
        Rectangle hN  = createHandleShape(Cursor.N_RESIZE);
        Rectangle hW  = createHandleShape(Cursor.W_RESIZE);
        Rectangle hE  = createHandleShape(Cursor.E_RESIZE);
        Rectangle hS  = createHandleShape(Cursor.S_RESIZE);
        Rectangle hSE = createHandleShape(Cursor.SE_RESIZE);
        hNW.getStyleClass().add("sel-handle");
        hN.getStyleClass().add("sel-handle");
        hW.getStyleClass().add("sel-handle");
        hE.getStyleClass().add("sel-handle");
        hS.getStyleClass().add("sel-handle");
        hSE.getStyleClass().add("sel-handle");
        List<Rectangle> handles = List.of(hNW, hN, hW, hE, hS, hSE);

        final double[] rs = new double[6]; // screenX, screenY, w, h, x, y
        java.util.function.Consumer<MouseEvent> press = ev -> {
            if (ev.isPrimaryButtonDown()) {
                rs[0] = ev.getScreenX();
                rs[1] = ev.getScreenY();
                rs[2] = el.getW();
                rs[3] = el.getH();
                rs[4] = el.getX();
                rs[5] = el.getY();
                ev.consume();
            }
        };

        attachResize(hSE, el, rs, press, 1, 0, 1, 0, selBox, border, handles);
        attachResize(hE,  el, rs, press, 1, 0, 0, 0, selBox, border, handles);
        attachResize(hS,  el, rs, press, 0, 0, 1, 0, selBox, border, handles);
        attachResize(hW,  el, rs, press, 0, 1, 0, 0, selBox, border, handles);
        attachResize(hN,  el, rs, press, 0, 0, 0, 1, selBox, border, handles);
        attachResize(hNW, el, rs, press, 0, 1, 0, 1, selBox, border, handles);

        positionHandles(handles, wPx, hPx);
        selBox.getChildren().add(border);
        selBox.getChildren().addAll(handles);
        selectionPane.getChildren().add(selBox);
    }

    /**
     * Generic edge/corner resize driver. growRight/growBottom stretch the
     * right/bottom edge; growLeft/growTop move the left/top edge (and the
     * object with it). Exactly one of growLeft/growRight (and one of
     * growTop/growBottom) is set per handle.
     */
    private void attachResize(Rectangle handle, TemplateElement el, double[] rs,
                              java.util.function.Consumer<MouseEvent> press,
                              int growRight, int growLeft, int growBottom, int growTop,
                              Pane selBox, Rectangle border, List<Rectangle> handles) {
        handle.setOnMousePressed(e -> {
            if (isPanMode || isSpaceDown) return;
            press.accept(e);
        });
        handle.setOnMouseDragged(e -> {
            if (isPanMode || isSpaceDown) return;
            if (el.isLocked()) return;

            double dx = (e.getScreenX() - rs[0]) / zoom / MM_PX;
            double dy = (e.getScreenY() - rs[1]) / zoom / MM_PX;

            double newW = rs[2], newH = rs[3], newX = rs[4], newY = rs[5];

            if (growRight == 1)  newW = Math.max(5.0, rs[2] + dx);
            if (growBottom == 1) newH = Math.max(3.0, rs[3] + dy);
            if (growLeft == 1) {
                newW = Math.max(5.0, rs[2] - dx);
                newX = Math.max(0, rs[4] + (rs[2] - newW));
            }
            if (growTop == 1) {
                newH = Math.max(3.0, rs[3] - dy);
                newY = Math.max(0, rs[5] + (rs[3] - newH));
            }

            // Magnet: snap the dragged edges to nearby object edges/centres
            if (snapToObjects) {
                Double snapV = null, snapH = null;
                if (growRight == 1) {
                    double s = nearestObjectEdgeX(el, rs[4] + newW);
                    if (s >= 0) { newW = Math.max(5.0, s - newX); snapV = s; }
                } else if (growLeft == 1) {
                    double s = nearestObjectEdgeX(el, newX);
                    if (s >= 0) { newW = Math.max(5.0, rs[4] + rs[2] - s); newX = s; snapV = s; }
                }
                if (growBottom == 1) {
                    double s = nearestObjectEdgeY(el, rs[5] + newH);
                    if (s >= 0) { newH = Math.max(3.0, s - newY); snapH = s; }
                } else if (growTop == 1) {
                    double s = nearestObjectEdgeY(el, newY);
                    if (s >= 0) { newH = Math.max(3.0, rs[5] + rs[3] - s); newY = s; snapH = s; }
                }
                showSnapGuides(snapV, snapH);
            }

            if (snapToGrid) {
                if (growRight == 1 || growLeft == 1) newW = Math.round(newW);
                if (growBottom == 1 || growTop == 1) newH = Math.round(newH);
                if (growLeft == 1) newX = Math.round(newX);
                if (growTop == 1) newY = Math.round(newY);
            }

            el.setW(newW);
            el.setH(newH);
            el.setX(newX);
            el.setY(newY);

            double wPx = newW * MM_PX, hPx = newH * MM_PX;
            selBox.setLayoutX(newX * MM_PX);
            selBox.setLayoutY(newY * MM_PX);
            selBox.setPrefSize(wPx, hPx);
            selBox.setMinSize(wPx, hPx);
            border.setWidth(wPx);
            border.setHeight(hPx);
            positionHandles(handles, wPx, hPx);
            syncGeometrySpinners();
            e.consume();
        });
        handle.setOnMouseReleased(e -> {
            if (isPanMode || isSpaceDown) return;
            clearSnapGuides();
            saveState();
            refreshCanvas();
            updatePropertiesPanel();
            e.consume();
        });
    }

    /** Fixed screen positions of the six handles inside the selection box. */
    private static void positionHandles(List<Rectangle> hs, double wPx, double hPx) {
        hs.get(0).setLayoutX(-4.5);             hs.get(0).setLayoutY(-4.5);            // NW
        hs.get(1).setLayoutX(wPx / 2.0 - 4.5);  hs.get(1).setLayoutY(-4.5);            // N
        hs.get(2).setLayoutX(-4.5);             hs.get(2).setLayoutY(hPx / 2.0 - 4.5); // W
        hs.get(3).setLayoutX(wPx - 4.5);        hs.get(3).setLayoutY(hPx / 2.0 - 4.5); // E
        hs.get(4).setLayoutX(wPx / 2.0 - 4.5);  hs.get(4).setLayoutY(hPx - 4.5);       // S
        hs.get(5).setLayoutX(wPx - 4.5);        hs.get(5).setLayoutY(hPx - 4.5);       // SE
    }

    /** Builds up to 4 edge lines (T/R/B/L) honouring per-side enabled/color/width + stroke style. */
    private List<Line> perSideStrokeLines(TemplateElement el, double w, double h) {
        List<Line> out = new ArrayList<>();
        if (el.isBorderTop()) {
            out.add(styledLine(0, 0, w, 0, el.sideColorHex('T'), el.sideWidthMm('T') * MM_PX, el.getStrokeStyle()));
        }
        if (el.isBorderRight()) {
            out.add(styledLine(w, 0, w, h, el.sideColorHex('R'), el.sideWidthMm('R') * MM_PX, el.getStrokeStyle()));
        }
        if (el.isBorderBottom()) {
            out.add(styledLine(0, h, w, h, el.sideColorHex('B'), el.sideWidthMm('B') * MM_PX, el.getStrokeStyle()));
        }
        if (el.isBorderLeft()) {
            out.add(styledLine(0, 0, 0, h, el.sideColorHex('L'), el.sideWidthMm('L') * MM_PX, el.getStrokeStyle()));
        }
        return out;
    }

    /** Convenience line factory with themed color/width and dash styling. */
    private Line styledLine(double x1, double y1, double x2, double y2, String hex, double widthPx, String style) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(Color.web(hex != null && !hex.isBlank() ? hex : "#1a1a1a"));
        l.setStrokeWidth(Math.max(0.75, widthPx));
        applyStrokeStyle(l, style);
        return l;
    }

    /** Shared canvas stroke styling: solid / dashed / dotted. */
    private void applyStrokeStyle(javafx.scene.shape.Shape s, String style) {
        String st = style != null ? style : "solid";
        switch (st) {
            case "dashed" -> { s.getStrokeDashArray().clear(); s.getStrokeDashArray().addAll(6.0, 3.0); }
            case "dotted" -> {
                s.getStrokeDashArray().clear();
                s.getStrokeDashArray().addAll(0.1, 3.0);
                s.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            }
            default -> s.getStrokeDashArray().clear();
        }
    }

    private Node renderVisualElement(TemplateElement el, RenderContext ctx, double w, double h) {
        switch (el.getType()) {
            case RECT: {
                Rectangle r = new Rectangle(w, h);
                if (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg())) {
                    r.setFill(Color.web(el.getBg()));
                } else r.setFill(Color.TRANSPARENT);
                if (el.getBorderRadius() > 0) {
                    r.setArcWidth(el.getBorderRadius() * MM_PX * 2);
                    r.setArcHeight(el.getBorderRadius() * MM_PX * 2);
                }
                if (el.hasPerSideStroke()) {
                    // per-side color/width/enabled: 4 independent edge lines
                    // (note: rounded corners are approximated by straight edges here)
                    r.setStroke(Color.TRANSPARENT);
                    r.setStrokeWidth(0);
                    Pane p = new Pane(r);
                    p.setPrefSize(w, h);
                    p.getChildren().addAll(perSideStrokeLines(el, w, h));
                    return p;
                }
                if (el.getBorderWidth() > 0 && el.getBorderColor() != null) {
                    r.setStroke(Color.web(el.getBorderColor()));
                    r.setStrokeWidth(el.getBorderWidth() * MM_PX);
                    applyStrokeStyle(r, el.getStrokeStyle());
                    if ("double".equals(el.getStrokeStyle())) {
                        double sw = Math.max(1, el.getBorderWidth() * MM_PX);
                        Rectangle inner = new Rectangle(
                                Math.max(0, w - 2 * (sw * 1.6 + 1)), Math.max(0, h - 2 * (sw * 1.6 + 1)));
                        inner.setLayoutX(sw * 1.6 + 1);
                        inner.setLayoutY(sw * 1.6 + 1);
                        inner.setFill(Color.TRANSPARENT);
                        inner.setStroke(Color.web(el.getBorderColor()));
                        inner.setStrokeWidth(sw * 0.6);
                        if (el.getBorderRadius() > 0) {
                            inner.setArcWidth(Math.max(0, r.getArcWidth() - 2 * (sw * 1.6 + 1)));
                            inner.setArcHeight(Math.max(0, r.getArcHeight() - 2 * (sw * 1.6 + 1)));
                        }
                        Pane p = new Pane(r, inner);
                        p.setPrefSize(w, h);
                        return p;
                    }
                }
                return r;
            }
            case ELLIPSE: {
                Ellipse e = new Ellipse(w / 2.0, h / 2.0, Math.max(1, w / 2.0 - 1), Math.max(1, h / 2.0 - 1));
                if (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg())) {
                    e.setFill(Color.web(el.getBg()));
                } else e.setFill(Color.TRANSPARENT);
                if (el.getBorderWidth() > 0 && el.getBorderColor() != null) {
                    e.setStroke(Color.web(el.getBorderColor()));
                    e.setStrokeWidth(el.getBorderWidth() * MM_PX);
                    applyStrokeStyle(e, el.getStrokeStyle());
                }
                return e;
            }
            case STAR: {
                int n = Math.max(3, el.getStarPoints());
                double cx = w / 2.0, cy = h / 2.0;
                double roX = Math.max(1, w / 2.0 - 1), roY = Math.max(1, h / 2.0 - 1);
                double riX = roX * el.getStarInnerRatio(), riY = roY * el.getStarInnerRatio();
                List<Double> pts = new ArrayList<>();
                for (int i = 0; i < n * 2; i++) {
                    double ang = Math.PI * i / n - Math.PI / 2.0;
                    boolean outer = i % 2 == 0;
                    pts.add(cx + (outer ? roX : riX) * Math.cos(ang));
                    pts.add(cy + (outer ? roY : riY) * Math.sin(ang));
                }
                Polygon poly = new Polygon();
                poly.getPoints().addAll(pts);
                if (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg())) {
                    poly.setFill(Color.web(el.getBg()));
                } else poly.setFill(Color.TRANSPARENT);
                if (el.getBorderWidth() > 0 && el.getBorderColor() != null) {
                    poly.setStroke(Color.web(el.getBorderColor()));
                    poly.setStrokeWidth(el.getBorderWidth() * MM_PX);
                    applyStrokeStyle(poly, el.getStrokeStyle());
                }
                return poly;
            }
            case ARROW: {
                double shaftY = h / 2.0;
                double headLen = Math.min(w * 0.4, h * 0.9);
                double headW = Math.min(h, Math.max(2, w * 0.5));
                String hex = el.getBorderColor() != null ? el.getBorderColor() : "#1a1a1a";
                Line shaft = styledLine(0, shaftY, Math.max(0.1, w - headLen), shaftY,
                        hex, el.getBorderWidth() * MM_PX, el.getStrokeStyle());
                Polygon head = new Polygon(
                        w, shaftY,
                        w - headLen, shaftY - headW / 2.0,
                        w - headLen, shaftY + headW / 2.0);
                head.setFill(Color.web(hex));
                Pane p = new Pane(shaft, head);
                p.setPrefSize(w, h);
                return p;
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
                applyStrokeStyle(l, el.getStrokeStyle());
                if ("double".equals(el.getStrokeStyle())) {
                    double sw = Math.max(1, el.getBorderWidth() * MM_PX);
                    Line l2 = new Line();
                    if ("v".equalsIgnoreCase(el.getDirection())) {
                        l2.setStartX(w / 2 + sw * 1.4); l2.setStartY(0);
                        l2.setEndX(w / 2 + sw * 1.4); l2.setEndY(h);
                    } else {
                        l2.setStartX(0); l2.setStartY(h / 2 + sw * 1.4);
                        l2.setEndX(w); l2.setEndY(h / 2 + sw * 1.4);
                    }
                    l2.setStroke(l.getStroke());
                    l2.setStrokeWidth(Math.max(0.75, sw * 0.6));
                    Pane p = new Pane(l, l2);
                    p.setPrefSize(w, h);
                    return p;
                }
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
                // Shared table skin values (kept identical to BillPreviewPane + PdfExportService)
                String bc = el.getTableBorderColor();
                double bwPx = Math.max(0.5, el.getTableBorderWidth() > 0 ? el.getTableBorderWidth() * MM_PX : 1.0);
                String bw = String.format(java.util.Locale.US, "%.2f", bwPx);
                String bStyle = el.getBorderStyle(); // grid, rows, outline, none
                boolean drawOuter = !"none".equals(bStyle);
                boolean innerLines = "grid".equals(bStyle) || "rows".equals(bStyle);

                VBox box = new VBox(0);
                box.setPrefSize(w, h);
                if (drawOuter) {
                    // Per-side outer border: hidden sides use transparent color
                    box.setStyle("-fx-background-color: #ffffff; -fx-border-color: "
                            + (el.isBorderTop() ? bc : "transparent") + " "
                            + (el.isBorderRight() ? bc : "transparent") + " "
                            + (el.isBorderBottom() ? bc : "transparent") + " "
                            + (el.isBorderLeft() ? bc : "transparent") + ";"
                            + " -fx-border-width: " + bw + ";");
                } else {
                    box.setStyle("-fx-background-color: #ffffff;");
                }

                List<TableColumn> cols = el.getColumns();
                if (cols == null || cols.isEmpty()) cols = PresetTemplates.defaultItemColumns();

                double rowPx = (el.getRowHeight() > 0 ? el.getRowHeight() : 7.0) * MM_PX;
                double headerPx = Math.max(22, rowPx);
                String fontPx = String.format(java.util.Locale.US, "%.1f", 10.0 * el.tableFontScale());

                String hBg = el.getHeaderBg() != null && !el.getHeaderBg().isBlank() ? el.getHeaderBg() : "#efe9db";
                String hCol = el.getHeaderColor() != null && !el.getHeaderColor().isBlank() ? el.getHeaderColor() : "#1a1a1a";
                String rowBgCol = el.getRowBg();
                String rowTextCol = el.getRowColor();
                String zebraCol = el.getZebraColor();

                HBox hRow = new HBox(0);
                hRow.setPrefHeight(headerPx);
                // Header underline (grid / rows styles only)
                hRow.setStyle("-fx-background-color: " + hBg + ";"
                        + (innerLines ? " -fx-border-color: transparent transparent " + bc + " transparent; -fx-border-width: 0 0 " + bw + " 0;" : ""));

                for (int ci = 0; ci < cols.size(); ci++) {
                    TableColumn c = cols.get(ci);
                    double cW = (c.getWidth() / 100.0) * w;
                    boolean vSep = "grid".equals(bStyle) && ci < cols.size() - 1;
                    Label lbl = new Label(c.getLabel());
                    lbl.setPrefWidth(cW);
                    lbl.setPrefHeight(headerPx);
                    lbl.setStyle("-fx-font-weight: bold; -fx-font-size: " + fontPx + "px; -fx-text-fill: " + hCol + "; -fx-padding: 0 4;"
                            + (vSep ? " -fx-border-color: transparent " + bc + " transparent transparent; -fx-border-width: 0 " + bw + " 0 0;" : ""));
                    lbl.setAlignment("right".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER_RIGHT : ("center".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER : Pos.CENTER_LEFT));
                    hRow.getChildren().add(lbl);
                }
                box.getChildren().add(hRow);

                for (int r = 1; r <= 2; r++) {
                    HBox row = new HBox(0);
                    row.setPrefHeight(rowPx);
                    String rBg = el.isShowZebra() && (r % 2 == 0) ? zebraCol : rowBgCol;
                    row.setStyle("-fx-background-color: " + rBg + ";"
                            + (innerLines ? " -fx-border-color: transparent transparent " + bc + " transparent; -fx-border-width: 0 0 " + bw + " 0;" : ""));
                    for (int ci = 0; ci < cols.size(); ci++) {
                        TableColumn c = cols.get(ci);
                        double cW = (c.getWidth() / 100.0) * w;
                        boolean vSep = "grid".equals(bStyle) && ci < cols.size() - 1;
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
                        lbl.setPrefHeight(rowPx);
                        lbl.setStyle("-fx-font-size: " + fontPx + "px; -fx-text-fill: " + rowTextCol + "; -fx-padding: 0 4;"
                                + (vSep ? " -fx-border-color: transparent " + bc + " transparent transparent; -fx-border-width: 0 " + bw + " 0 0;" : ""));
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
        typeLbl.getStyleClass().add("element-type-badge");

        namePropField = new TextField(el.getName() != null ? el.getName() : "");
        namePropField.setPromptText("Object name");
        namePropField.setPrefWidth(130);
        namePropField.setTooltip(new Tooltip("Object name shown in the Layers list (double-click a layer row to rename too)"));
        namePropField.textProperty().addListener((obs, o, v) -> el.setName(v != null && !v.isBlank() ? v : null));

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button dupBtn = createToolbarBtn("❐", "Duplicate Element (Ctrl D)", this::duplicateSelected);
        Button upBtn = createToolbarBtn("▲", "Bring Forward", () -> moveLayer(1));
        Button downBtn = createToolbarBtn("▼", "Send Backward", () -> moveLayer(-1));
        Button delBtn = createToolbarBtn("🗑", "Delete Element (Delete key)", this::deleteSelected);

        headerRow.getChildren().addAll(typeLbl, namePropField, sp, dupBtn, upBtn, downBtn, delBtn);
        propBox.getChildren().add(headerRow);

        // Position & Size Grid
        TitledPane geoPane = new TitledPane();
        geoPane.setText("Position & Size (mm)");
        geoPane.setExpanded(true);

        GridPane posGrid = new GridPane();
        posGrid.setHgap(8); posGrid.setVgap(8);
        posGrid.setPadding(new Insets(8));

        posGrid.add(new Label("X:"), 0, 0);
        gxSpin = new Spinner<>(0.0, 500.0, el.getX(), 1.0);
        gxSpin.setPrefWidth(85);
        gxSpin.setEditable(true);
        gxSpin.valueProperty().addListener((obs, o, v) -> { if (!syncingGeometry) { el.setX(v); refreshCanvas(); } });
        posGrid.add(gxSpin, 1, 0);

        posGrid.add(new Label("Y:"), 2, 0);
        gySpin = new Spinner<>(0.0, 500.0, el.getY(), 1.0);
        gySpin.setPrefWidth(85);
        gySpin.setEditable(true);
        gySpin.valueProperty().addListener((obs, o, v) -> { if (!syncingGeometry) { el.setY(v); refreshCanvas(); } });
        posGrid.add(gySpin, 3, 0);

        posGrid.add(new Label("W:"), 0, 1);
        gwSpin = new Spinner<>(1.0, 500.0, el.getW(), 1.0);
        gwSpin.setPrefWidth(85);
        gwSpin.setEditable(true);
        gwSpin.valueProperty().addListener((obs, o, v) -> { if (!syncingGeometry) { el.setW(v); refreshCanvas(); } });
        posGrid.add(gwSpin, 1, 1);

        posGrid.add(new Label("H:"), 2, 1);
        ghSpin = new Spinner<>(1.0, 500.0, el.getH(), 1.0);
        ghSpin.setPrefWidth(85);
        ghSpin.setEditable(true);
        ghSpin.valueProperty().addListener((obs, o, v) -> { if (!syncingGeometry) { el.setH(v); refreshCanvas(); } });
        posGrid.add(ghSpin, 3, 1);

        geoPane.setContent(posGrid);
        propBox.getChildren().add(geoPane);

        // Specific Type Editors
        if (el.getType() == ElementType.TEXT || el.getType() == ElementType.PAGENO) {
            buildTextProperties(el);
        } else if (el.getType() == ElementType.RECT || el.getType() == ElementType.ELLIPSE
                || el.getType() == ElementType.STAR || el.getType() == ElementType.ARROW) {
            buildShapeProperties(el);
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
        toggles.getStyleClass().add("toggles-box");

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
        textLbl.getStyleClass().add("cell-bold-secondary");
        TextArea ta = new TextArea(el.getText());
        ta.setPrefRowCount(3);
        ta.setWrapText(true);
        ta.getStyleClass().add("designer-textarea");
        ta.textProperty().addListener((obs, o, v) -> { el.setText(v); refreshCanvas(); });

        VBox varSec = new VBox(8);
        varSec.getStyleClass().add("var-section");

        Label varSecLbl = new Label("INSERT TEMPLATE VARIABLES:");
        varSecLbl.getStyleClass().add("overline-accent");

        // 1. Direct Dropdown & + Add Button directly in property view
        ComboBox<VariableDef> varCombo = new ComboBox<>();
        varCombo.setPromptText("Choose variable to add...");
        varCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(varCombo, Priority.ALWAYS);
        List<VariableDef> allVars = getComprehensiveVariablesList();
        varCombo.setItems(FXCollections.observableArrayList(allVars));
        varCombo.getStyleClass().add("designer-combo");
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
        dropInsertBtn.getStyleClass().add("btn-gold-sm");
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
        varPickerBtn.getStyleClass().add("btn-outline-gold");
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
        boldBtn.getStyleClass().add("text-bold");
        boldBtn.setTooltip(new Tooltip("Bold"));
        boldBtn.setOnAction(e -> { el.setFontWeight(boldBtn.isSelected() ? 700 : 400); refreshCanvas(); });

        ToggleButton italicBtn = new ToggleButton("I");
        italicBtn.setSelected(el.isItalic());
        italicBtn.getStyleClass().add("text-italic");
        italicBtn.setTooltip(new Tooltip("Italic"));
        italicBtn.setOnAction(e -> { el.setItalic(italicBtn.isSelected()); refreshCanvas(); });

        Button alignL = createToolbarBtn("⯇", "Align Left", () -> { el.setAlign("left"); refreshCanvas(); });
        Button alignC = createToolbarBtn("☰", "Align Center", () -> { el.setAlign("center"); refreshCanvas(); });
        Button alignR = createToolbarBtn("⯈", "Align Right", () -> { el.setAlign("right"); refreshCanvas(); });

        styleRow.getChildren().addAll(boldBtn, italicBtn, new Separator(javafx.geometry.Orientation.VERTICAL), alignL, alignC, alignR);

        // ColorPickers with quick palette chips
        GridPane colorGrid = new GridPane();
        colorGrid.setHgap(8); colorGrid.setVgap(8);

        ColorPickerButton textColorPicker = new ColorPickerButton(el.getColor(), false, hex -> {
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
                textColorPicker.setHex(hex);
                refreshCanvas();
            });
            colorPresetRow.getChildren().add(chip);
        }
        HBox textColBox = new HBox(8, textColorPicker, colorPresetRow);
        textColBox.setAlignment(Pos.CENTER_LEFT);

        colorGrid.add(new Label("Text Color:"), 0, 0);
        colorGrid.add(textColBox, 1, 0);

        ColorPickerButton bgColorPicker = new ColorPickerButton(el.getBg(), true, hex -> {
            el.setBg(hex);
            refreshCanvas();
        });

        CheckBox bgTransCb = new CheckBox("Transparent");
        bgTransCb.setSelected(el.getBg() == null || "transparent".equalsIgnoreCase(el.getBg()));
        bgTransCb.setOnAction(e -> {
            if (bgTransCb.isSelected()) {
                el.setBg("transparent");
            } else {
                el.setBg(bgColorPicker.getHex());
            }
            refreshCanvas();
        });

        colorGrid.add(new Label("Background:"), 0, 1);
        colorGrid.add(new HBox(6, bgColorPicker, bgTransCb), 1, 1);

        sec.getChildren().addAll(textLbl, ta, varSec, fontRow, styleRow, colorGrid);
        propBox.getChildren().add(sec);
    }

    /** Unified editor for RECT / ELLIPSE / STAR / ARROW shapes. */
    private void buildShapeProperties(TemplateElement el) {
        VBox sec = new VBox(8);
        Label title = new Label("Shape Properties:");
        title.getStyleClass().add("prop-title");

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);

        boolean isArrow = el.getType() == ElementType.ARROW;
        int row = 0;

        if (!isArrow) {
            ColorPickerButton fillPicker = new ColorPickerButton(el.getBg(), true, hex -> {
                el.setBg(hex);
                refreshCanvas();
            });
            CheckBox transCb = new CheckBox("Transparent");
            transCb.setSelected(el.getBg() == null || "transparent".equalsIgnoreCase(el.getBg()));
            transCb.setOnAction(e -> {
                if (transCb.isSelected()) el.setBg("transparent");
                else el.setBg(fillPicker.getHex());
                refreshCanvas();
            });
            grid.add(new Label("Fill Color:"), 0, row);
            grid.add(new HBox(6, fillPicker, transCb), 1, row);
            row++;
        }

        ColorPickerButton borderPicker = new ColorPickerButton(el.getBorderColor(), false, hex -> {
            el.setBorderColor(hex);
            refreshCanvas();
        });
        grid.add(new Label("Stroke Color:"), 0, row);
        grid.add(borderPicker, 1, row);
        row++;

        Spinner<Double> borderW = new Spinner<>(0.0, 10.0, el.getBorderWidth(), 0.5);
        borderW.setEditable(true);
        borderW.valueProperty().addListener((obs, o, v) -> { el.setBorderWidth(v); refreshCanvas(); });
        grid.add(new Label("Stroke Width (mm):"), 0, row);
        grid.add(borderW, 1, row);
        row++;

        ComboBox<String> styleCb = new ComboBox<>(FXCollections.observableArrayList("solid", "dashed", "dotted", "double"));
        styleCb.setValue(el.getStrokeStyle());
        styleCb.valueProperty().addListener((obs, o, v) -> { if (v != null) el.setStrokeStyle(v); refreshCanvas(); });
        grid.add(new Label("Stroke Type:"), 0, row);
        grid.add(styleCb, 1, row);
        row++;

        if (el.getType() == ElementType.RECT) {
            Spinner<Double> borderR = new Spinner<>(0.0, 50.0, el.getBorderRadius(), 1.0);
            borderR.setEditable(true);
            borderR.valueProperty().addListener((obs, o, v) -> { el.setBorderRadius(v); refreshCanvas(); });
            grid.add(new Label("Corner Radius:"), 0, row);
            grid.add(borderR, 1, row);
            row++;
        }

        if (el.getType() == ElementType.STAR) {
            Spinner<Integer> pts = new Spinner<>(3, 24, el.getStarPoints(), 1);
            pts.setEditable(true);
            pts.valueProperty().addListener((obs, o, v) -> { if (v != null) el.setStarPoints(v); refreshCanvas(); });
            grid.add(new Label("Star Points:"), 0, row);
            grid.add(pts, 1, row);
            row++;

            Spinner<Double> inner = new Spinner<>(0.1, 0.9, el.getStarInnerRatio(), 0.05);
            inner.setEditable(true);
            inner.valueProperty().addListener((obs, o, v) -> { if (v != null) el.setStarInnerRatio(v); refreshCanvas(); });
            grid.add(new Label("Inner Radius Ratio:"), 0, row);
            grid.add(inner, 1, row);
            row++;
        }

        sec.getChildren().addAll(title, grid);
        propBox.getChildren().add(sec);

        if (el.getType() == ElementType.RECT) {
            buildPerSideStrokeSection(el);
        }
    }

    /** Per-side stroke editor (RECT): enable + color + width for Top/Bottom/Left/Right. */
    private void buildPerSideStrokeSection(TemplateElement el) {
        TitledPane sidePane = new TitledPane();
        sidePane.setText("Individual Sides (Top / Bottom / Left / Right)");
        sidePane.setExpanded(false);

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(6);
        grid.setPadding(new Insets(6));

        String[][] sides = {{"T", "Top"}, {"B", "Bottom"}, {"L", "Left"}, {"R", "Right"}};
        int r = 0;
        for (String[] side : sides) {
            char s = side[0].charAt(0);
            boolean enabled = switch (s) {
                case 'T' -> el.isBorderTop();
                case 'B' -> el.isBorderBottom();
                case 'L' -> el.isBorderLeft();
                default -> el.isBorderRight();
            };
            CheckBox en = new CheckBox(side[1]);
            en.setSelected(enabled);
            en.getStyleClass().add("check-plain");
            en.setOnAction(e -> {
                switch (s) {
                    case 'T' -> el.setBorderTop(en.isSelected());
                    case 'B' -> el.setBorderBottom(en.isSelected());
                    case 'L' -> el.setBorderLeft(en.isSelected());
                    default -> el.setBorderRight(en.isSelected());
                }
                refreshCanvas();
            });
            grid.add(en, 0, r);

            ColorPickerButton colBtn = new ColorPickerButton(el.sideColorHex(s), false, hex -> {
                switch (s) {
                    case 'T' -> el.setBorderTopColor(hex);
                    case 'B' -> el.setBorderBottomColor(hex);
                    case 'L' -> el.setBorderLeftColor(hex);
                    default -> el.setBorderRightColor(hex);
                }
                refreshCanvas();
            });
            grid.add(colBtn, 1, r);

            Spinner<Double> wSpin = new Spinner<>(0.0, 10.0, el.sideWidthMm(s), 0.25);
            wSpin.setEditable(true);
            wSpin.setPrefWidth(80);
            wSpin.valueProperty().addListener((obs, o, v) -> {
                switch (s) {
                    case 'T' -> el.setBorderTopWidth(v);
                    case 'B' -> el.setBorderBottomWidth(v);
                    case 'L' -> el.setBorderLeftWidth(v);
                    default -> el.setBorderRightWidth(v);
                }
                refreshCanvas();
            });
            grid.add(wSpin, 2, r);

            Button reset = createToolbarBtn("↺", "Reset this side to inherit the global stroke", () -> {
                switch (s) {
                    case 'T' -> { el.setBorderTopWidth(null); el.setBorderTopColor(null); }
                    case 'B' -> { el.setBorderBottomWidth(null); el.setBorderBottomColor(null); }
                    case 'L' -> { el.setBorderLeftWidth(null); el.setBorderLeftColor(null); }
                    default -> { el.setBorderRightWidth(null); el.setBorderRightColor(null); }
                }
                refreshCanvas();
                updatePropertiesPanel();
            });
            grid.add(reset, 3, r);
            r++;
        }

        Label hint = new Label("Untouched sides inherit the global Stroke Color / Width above.");
        hint.getStyleClass().add("guide-note");
        sidePane.setContent(new VBox(6, grid, hint));
        propBox.getChildren().add(sidePane);
    }

    private void buildLineProperties(TemplateElement el) {
        VBox sec = new VBox(8);
        Label title = new Label("Line Properties:");
        title.getStyleClass().add("prop-title");

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

        ColorPickerButton colorPicker = new ColorPickerButton(el.getBorderColor(), false, hex -> {
            el.setBorderColor(hex);
            refreshCanvas();
        });
        grid.add(new Label("Line Color:"), 0, 1);
        grid.add(colorPicker, 1, 1);

        Spinner<Double> thickSpin = new Spinner<>(0.2, 10.0, Math.max(0.5, el.getBorderWidth()), 0.5);
        thickSpin.setEditable(true);
        thickSpin.valueProperty().addListener((obs, o, v) -> { el.setBorderWidth(v); refreshCanvas(); });
        grid.add(new Label("Thickness (mm):"), 0, 2);
        grid.add(thickSpin, 1, 2);

        ComboBox<String> styleCb = new ComboBox<>(FXCollections.observableArrayList("solid", "dashed", "dotted", "double"));
        styleCb.setValue(el.getStrokeStyle());
        styleCb.valueProperty().addListener((obs, o, v) -> { if (v != null) el.setStrokeStyle(v); refreshCanvas(); });
        grid.add(new Label("Line Style:"), 0, 3);
        grid.add(styleCb, 1, 3);

        sec.getChildren().addAll(title, grid);
        propBox.getChildren().add(sec);
    }

    private void buildImageProperties(TemplateElement el) {
        VBox sec = new VBox(10);
        Label title = new Label("Image Properties & Source:");
        title.getStyleClass().add("prop-title");

        // Thumbnail Preview Box
        StackPane previewContainer = new StackPane();
        previewContainer.setPrefHeight(90);
        previewContainer.setMaxWidth(Double.MAX_VALUE);
        previewContainer.getStyleClass().add("image-preview-box");

        ImageView thumbView = new ImageView();
        thumbView.setFitHeight(80);
        thumbView.setFitWidth(180);
        thumbView.setPreserveRatio(true);
        thumbView.setSmooth(true);

        Label noImgLbl = new Label("No Image Loaded");
        noImgLbl.getStyleClass().add("text-dim");

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
        logoCb.getStyleClass().add("check-plain");
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
        appLogoWhiteBtn.getStyleClass().add("btn-gold-chip");
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
        appLogoDarkBtn.getStyleClass().add("btn-gold-chip");
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
        clearBtn.getStyleClass().add("btn-danger-chip");
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
        fitLbl.getStyleClass().add("check-plain");
        ComboBox<String> fitCb = new ComboBox<>(FXCollections.observableArrayList("contain", "cover", "fill"));
        fitCb.setValue(el.getObjectFit() != null ? el.getObjectFit() : "contain");
        fitCb.getStyleClass().add("fs-11");
        fitCb.valueProperty().addListener((obs, o, v) -> {
            el.setObjectFit(v);
            saveState();
            refreshCanvas();
        });
        fitRow.getChildren().addAll(fitLbl, fitCb, clearBtn);

        Label sizeHint = new Label(String.format("Size: %.1f × %.1f mm", el.getW(), el.getH()));
        sizeHint.getStyleClass().add("text-dim");

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
        title.getStyleClass().add("prop-title");

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
        title.getStyleClass().add("prop-title");

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
        title.getStyleClass().add("prop-title");

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);

        ColorPickerButton hBgPicker = new ColorPickerButton(el.getHeaderBg(), false, hex -> {
            el.setHeaderBg(hex);
            refreshCanvas();
        });
        grid.add(new Label("Header BG:"), 0, 0);
        grid.add(hBgPicker, 1, 0);

        ColorPickerButton hTextPicker = new ColorPickerButton(el.getHeaderColor(), false, hex -> {
            el.setHeaderColor(hex);
            refreshCanvas();
        });
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

        // --- Border properties (type / color / width / sides) ---
        ComboBox<String> borderTypeCb = new ComboBox<>(FXCollections.observableArrayList(
                "Grid", "Rows Only", "Outline", "None"));
        borderTypeCb.setTooltip(new Tooltip("Grid = all cell lines, Rows Only = horizontal lines, Outline = outer frame only, None = no borders"));
        String curStyle = el.getBorderStyle();
        borderTypeCb.setValue(switch (curStyle) {
            case "rows" -> "Rows Only";
            case "outline" -> "Outline";
            case "none" -> "None";
            default -> "Grid";
        });
        borderTypeCb.valueProperty().addListener((obs, o, v) -> {
            el.setBorderStyle(switch (v == null ? "" : v) {
                case "Rows Only" -> "rows";
                case "Outline" -> "outline";
                case "None" -> "none";
                default -> "grid";
            });
            refreshCanvas();
        });
        grid.add(new Label("Border Type:"), 0, 4);
        grid.add(borderTypeCb, 1, 4);

        ColorPickerButton bColorPicker = new ColorPickerButton(el.getTableBorderColor(), false, hex -> {
            el.setTableBorderColor(hex);
            refreshCanvas();
        });
        grid.add(new Label("Border Color:"), 0, 5);
        grid.add(bColorPicker, 1, 5);

        Spinner<Double> bWidth = new Spinner<>(0.0, 1.5, el.getTableBorderWidth() > 0 ? el.getTableBorderWidth() : 0.26, 0.05);
        bWidth.setEditable(true);
        bWidth.setTooltip(new Tooltip("Border thickness in mm (~0.26mm = 1px on screen)"));
        bWidth.valueProperty().addListener((obs, o, v) -> { el.setTableBorderWidth(v); refreshCanvas(); });
        grid.add(new Label("Border Width (mm):"), 0, 6);
        grid.add(bWidth, 1, 6);

        HBox sidesBox = new HBox(8);
        CheckBox topCb = new CheckBox("T");
        CheckBox bottomCb = new CheckBox("B");
        CheckBox leftCb = new CheckBox("L");
        CheckBox rightCb = new CheckBox("R");
        topCb.setTooltip(new Tooltip("Show top border"));
        bottomCb.setTooltip(new Tooltip("Show bottom border"));
        leftCb.setTooltip(new Tooltip("Show left border"));
        rightCb.setTooltip(new Tooltip("Show right border"));
        topCb.setSelected(el.isBorderTop());
        bottomCb.setSelected(el.isBorderBottom());
        leftCb.setSelected(el.isBorderLeft());
        rightCb.setSelected(el.isBorderRight());
        topCb.setOnAction(e -> { el.setBorderTop(topCb.isSelected()); refreshCanvas(); });
        bottomCb.setOnAction(e -> { el.setBorderBottom(bottomCb.isSelected()); refreshCanvas(); });
        leftCb.setOnAction(e -> { el.setBorderLeft(leftCb.isSelected()); refreshCanvas(); });
        rightCb.setOnAction(e -> { el.setBorderRight(rightCb.isSelected()); refreshCanvas(); });
        sidesBox.getChildren().addAll(topCb, bottomCb, leftCb, rightCb);
        grid.add(new Label("Sides:"), 0, 7);
        grid.add(sidesBox, 1, 7);

        // --- Data row (record) colors ---
        ColorPickerButton rowBgPicker = new ColorPickerButton(el.getRowBg(), false, hex -> {
            el.setRowBg(hex);
            refreshCanvas();
        });
        grid.add(new Label("Row BG:"), 0, 8);
        grid.add(rowBgPicker, 1, 8);

        ColorPickerButton rowTextPicker = new ColorPickerButton(el.getRowColor(), false, hex -> {
            el.setRowColor(hex);
            refreshCanvas();
        });
        grid.add(new Label("Row Text:"), 0, 9);
        grid.add(rowTextPicker, 1, 9);

        ColorPickerButton zebraPicker = new ColorPickerButton(el.getZebraColor(), false, hex -> {
            el.setZebraColor(hex);
            refreshCanvas();
        });
        grid.add(new Label("Zebra Color:"), 0, 10);
        grid.add(zebraPicker, 1, 10);

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
        colHeader.getStyleClass().add("prop-title");
        colHeader.setPadding(new Insets(8, 0, 0, 0));

        Label colHint = new Label("Tip: use ▲ ▼ on a column to reorder it");
        colHint.getStyleClass().add("guide-note");

        VBox colsList = new VBox(6);
        for (int i = 0; i < cols.size(); i++) {
            TableColumn c = cols.get(i);
            int idx = i;

            HBox colRow = new HBox(6);
            colRow.setAlignment(Pos.CENTER_LEFT);
            colRow.getStyleClass().add("col-row");

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

            // Reorder controls (▲▼) — reposition a column without remove/re-add.
            // New order flows straight into canvas, live preview and PDF export,
            // which all iterate el.getColumns() in list order.
            Button upBtn = new Button("▲");
            Button downBtn = new Button("▼");
            upBtn.getStyleClass().add("col-move-btn");
            downBtn.getStyleClass().add("col-move-btn");
            upBtn.setTooltip(new Tooltip("Move column up (earlier in table)"));
            downBtn.setTooltip(new Tooltip("Move column down (later in table)"));
            upBtn.setDisable(idx == 0);
            downBtn.setDisable(idx == cols.size() - 1);
            upBtn.setFocusTraversable(false);
            downBtn.setFocusTraversable(false);
            upBtn.setOnAction(e -> moveColumn(el, idx, idx - 1));
            downBtn.setOnAction(e -> moveColumn(el, idx, idx + 1));

            VBox moveStack = new VBox(0, upBtn, downBtn);
            moveStack.getStyleClass().add("col-move-stack");

            Button rmBtn = new Button("✕");
            rmBtn.getStyleClass().addAll("button-sm", "button-danger");
            rmBtn.setTooltip(new Tooltip("Remove column"));
            rmBtn.setOnAction(e -> {
                el.getColumns().remove(idx);
                saveState();
                updatePropertiesPanel();
                refreshCanvas();
            });

            colRow.getChildren().addAll(moveStack, lblField, keyCombo, widthSpin, alignCombo, rmBtn);
            colsList.getChildren().add(colRow);
        }

        HBox colActions = new HBox(8);
        Button addColBtn = createToolbarBtn("+ Add Column", "Add a new column to the table", () -> {
            el.getColumns().add(new TableColumn("desc", "New Column", 15.0, "left"));
            saveState();
            updatePropertiesPanel();
            refreshCanvas();
        });

        Button presetGst = createToolbarBtn("GST Standard (8)", "Reset to standard 8-column GST table", () -> {
            el.setColumns(PresetTemplates.defaultItemColumns());
            saveState();
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
            saveState();
            updatePropertiesPanel();
            refreshCanvas();
        });

        colActions.getChildren().addAll(addColBtn, presetGst, presetSimple);

        sec.getChildren().addAll(title, grid, zebraCb, colHeader, colHint, colsList, colActions);
        propBox.getChildren().add(sec);
    }

    /**
     * Moves a table column from position {@code from} to {@code to} by swapping
     * the two entries in the column list, then snapshots undo state and rebuilds
     * the properties panel + canvas. Bounds-checked so a stale click can never
     * throw. Canvas, live preview and the PDF exporter all iterate
     * {@link TemplateElement#getColumns()} in order, so the reorder is reflected
     * everywhere automatically.
     */
    private void moveColumn(TemplateElement el, int from, int to) {
        List<TableColumn> cols = el.getColumns();
        if (cols == null || from < 0 || to < 0
                || from >= cols.size() || to >= cols.size() || from == to) {
            return;
        }
        Collections.swap(cols, from, to);
        saveState();
        updatePropertiesPanel();
        refreshCanvas();
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
            root.getStyleClass().add("picker-root");

            Label titleLbl = new Label("Insert Template Variable");
            titleLbl.getStyleClass().add("picker-title");

            Label subLbl = new Label("Choose a placeholder or double-click to insert directly into text:");
            subLbl.getStyleClass().add("picker-sub");

            TextField filterField = new TextField();
            filterField.setPromptText("Type to filter variables (e.g. buyer, total, gst, date, bank)...");
            filterField.getStyleClass().add("designer-field");

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
            lv.getStyleClass().add("designer-list");
            lv.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(VariableDef item, boolean empty) {
                    super.updateItem(item, empty);
                    if (!getStyleClass().contains("cell-clear")) getStyleClass().add("cell-clear");
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        HBox row = new HBox(8);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(4, 6, 4, 6));
                        row.setMouseTransparent(true);

                        Label nameLbl = new Label(item.getLabel() != null ? item.getLabel() : item.getKey());
                        nameLbl.getStyleClass().add("card-title-sm");
                        HBox.setHgrow(nameLbl, Priority.ALWAYS);

                        Label keyPill = new Label("{{" + item.getKey() + "}}");
                        keyPill.getStyleClass().add("key-pill");

                        Label typeBadge = new Label(item.getType() != null ? item.getType().toUpperCase() : "GENERAL");
                        typeBadge.getStyleClass().add("type-badge");

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
            cancelBtn.getStyleClass().add("btn-ghost");
            cancelBtn.setOnAction(e -> dlg.close());

            Button insertBtn = new Button("Insert Variable");
            insertBtn.getStyleClass().add("btn-gold");
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
        mgHeader.getStyleClass().add("section-eyebrow");
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
        titleLbl.getStyleClass().add("section-eyebrow");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label("CANVAS");
        badge.getStyleClass().add("type-badge");
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
        sizeCb.getStyleClass().add("fs-11");
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
        autoHCb.getStyleClass().add("check-plain");
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
        shiftCb.getStyleClass().add("check-plain");
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
        presetLbl.getStyleClass().add("overline-xs");
        FlowPane presetChips = new FlowPane(4, 4);

        Button pStd = createToolbarBtn("Standard (8mm)", "Set 8mm margins for all sides", () -> applyMarginPreset(8, 8, 8, 8));
        pStd.getStyleClass().add("button-xs");
        Button pCmp = createToolbarBtn("Compact (5mm)", "Set 5mm margins for all sides", () -> applyMarginPreset(5, 5, 5, 5));
        pCmp.getStyleClass().add("button-xs");
        Button pWide = createToolbarBtn("Wide (12mm)", "Set 12mm margins for all sides", () -> applyMarginPreset(12, 12, 12, 12));
        pWide.getStyleClass().add("button-xs");
        Button pZero = createToolbarBtn("Zero (0mm)", "Full bleed 0mm margins", () -> applyMarginPreset(0, 0, 0, 0));
        pZero.getStyleClass().add("button-xs");

        presetChips.getChildren().addAll(pStd, pCmp, pWide, pZero);
        mgBox.getChildren().addAll(presetLbl, presetChips);

        // Align Action Button
        Button alignBtn = new Button("⚡ Align All Elements to Margins");
        alignBtn.setMaxWidth(Double.MAX_VALUE);
        alignBtn.getStyleClass().add("btn-outline-gold");
        alignBtn.setTooltip(new Tooltip("Align outermost unlocked elements with margins and fit wide tables to printable width"));
        alignBtn.setOnAction(e -> alignElementsToMargins());
        mgBox.getChildren().add(alignBtn);

        // Open Dialog Button
        Button openDlgBtn = new Button("⚙ Open Full Page Dialog...");
        openDlgBtn.setMaxWidth(Double.MAX_VALUE);
        openDlgBtn.getStyleClass().add("btn-ghost-sm");
        openDlgBtn.setOnAction(e -> showPageSettingsDialog());
        mgBox.getChildren().add(openDlgBtn);

        // Helper guide description
        Label guideNote = new Label("Dashed blue guides on canvas show the printable margin boundary. All elements will respect these boundaries in bill preview, printing, and PDF export.");
        guideNote.setWrapText(true);
        guideNote.getStyleClass().add("guide-note");
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
            case TEXT: el.setText("New text"); el.setName("Text"); break;
            case RECT: el.setW(60); el.setH(30); el.setBg("#f4f1ea"); el.setName("Rectangle"); el.setBorderWidth(0.4); break;
            case ELLIPSE: el.setW(30); el.setH(30); el.setBg("#f4f1ea"); el.setName("Ellipse"); el.setBorderWidth(0.4); break;
            case STAR: el.setW(24); el.setH(24); el.setBg("#f4f1ea"); el.setName("Star"); el.setBorderWidth(0.4); break;
            case ARROW: el.setW(40); el.setH(8); el.setName("Arrow"); el.setBorderWidth(0.8); break;
            case LINE: el.setW(80); el.setH(1); el.setBorderWidth(0.5); el.setName("Line"); break;
            case IMAGE: el.setW(30); el.setH(25); el.setUseBusinessLogo(true); el.setName("Image"); break;
            case QRCODE: el.setW(24); el.setH(24); el.setName("QR Code"); break;
            case BARCODE: el.setW(45); el.setH(14); el.setName("Barcode"); break;
            case TABLE:
                el.setW(190); el.setH(30);
                el.setColumns(PresetTemplates.defaultItemColumns());
                el.setName("Item Table");
                break;
            default: break;
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
        TemplateElement copy;
        try {
            // Full-fidelity JSON round-trip: every property (incl. new shape /
            // per-side stroke fields) is copied without maintaining a field list.
            copy = mapper.readValue(mapper.writeValueAsString(selectedElement), TemplateElement.class);
        } catch (Exception ex) {
            ex.printStackTrace();
            copy = selectedElement; // unreachable in practice; prevents partial adds below on failure
            return;
        }
        copy.setId("el_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        if (copy.getName() != null) copy.setName(copy.getName() + " copy");
        copy.setX(selectedElement.getX() + 5);
        copy.setY(selectedElement.getY() + 5);

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
        // Guard the WHOLE refresh: setItems() makes the selection model retain the
        // old selected index and re-emit the (old) selected item as a change event.
        // Without the guard that event re-selected the previously selected element
        // right after add/duplicate/delete — silently discarding the new selection.
        isUpdatingLayersSelection = true;
        try {
            layersList.setItems(FXCollections.observableArrayList(template.getElements()));
            syncLayersListSelection();
        } finally {
            isUpdatingLayersSelection = false;
        }
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

    /** Header “? Help” / F1: all keyboard shortcuts & mouse controls. */
    private void showShortcutsDialog() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Keyboard Shortcuts");
        dlg.setHeaderText("Template Designer — Shortcuts & Mouse Controls");
        DialogHelper.styleDialog(dlg, 560, 560);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        String[][] shortcuts = {
                {"V", "Select tool"}, {"H / Space (hold)", "Pan tool"},
                {"Mouse drag on object", "Move object (magnet snaps to edges)"},
                {"Drag ▣ handles", "Resize — 6 anchors incl. left (W) & top (N)"},
                {"Arrow keys", "Nudge object 1mm (Shift = 5mm)"},
                {"Ctrl Z / Ctrl Shift Z / Ctrl Y", "Undo / Redo"},
                {"Ctrl C / Ctrl V / Ctrl D", "Copy / Paste / Duplicate object"},
                {"Delete / Backspace", "Delete selected object (safe while typing)"},
                {"Esc", "Deselect object"},
                {"M", "Toggle Magnet (snap to objects / page centre)"},
                {"G", "Toggle 1mm grid"}, {"R", "Toggle mm rulers"},
                {"Ctrl S", "Save template"},
                {"Ctrl 0 / Ctrl + / Ctrl -", "Zoom 100% / in / out"},
                {"Ctrl + Scroll", "Zoom at pointer"},
                {"Middle-drag / Pan tool drag", "Pan the canvas"},
                {"Double-click layer name", "Rename object"},
                {"Layers 👁 / 🔒", "Hide-show / lock objects directly in the list"},
                {"F1", "This help"}
        };

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(7);
        grid.setPadding(new Insets(8));
        int r = 0;
        for (String[] sc : shortcuts) {
            Label key = new Label(sc[0]);
            key.getStyleClass().add("shortcut-key");
            Label val = new Label(sc[1]);
            val.getStyleClass().add("shortcut-desc");
            grid.add(key, 0, r);
            grid.add(val, 1, r);
            r++;
        }

        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("scroll-side");
        sp.setPrefSize(540, 430);
        dlg.getDialogPane().setContent(sp);
        dlg.showAndWait();
    }

    private void setupKeyboardShortcuts() {
        setFocusTraversable(true);
        // Root grabs focus only when clicking non-text areas — clicking a text
        // field/spinner/combo must NOT steal its focus (stealing it made the
        // next Backspace fall through to the scene filter and DELETE the object).
        setOnMouseClicked(e -> {
            if (e.getTarget() instanceof Node tn && isInsideTextEditor(tn)) return;
            if (e.getPickResult().getIntersectedNode() instanceof Node pn && isInsideTextEditor(pn)) return;
            requestFocus();
        });

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

    /** True when node is (or lives inside) a text-editing control (TextField, Spinner editor, ComboBox…). */
    private static boolean isInsideTextEditor(Node n) {
        while (n != null) {
            if (n instanceof TextInputControl || n instanceof ComboBox || n instanceof Spinner) return true;
            n = n.getParent();
        }
        return false;
    }

    /** Lightweight in-place refresh of the X/Y/W/H spinners (used every drag frame — no rebuild). */
    private void syncGeometrySpinners() {
        if (selectedElement == null || gxSpin == null) return;
        syncingGeometry = true;
        try {
            gxSpin.getValueFactory().setValue(selectedElement.getX());
            gySpin.getValueFactory().setValue(selectedElement.getY());
            gwSpin.getValueFactory().setValue(selectedElement.getW());
            ghSpin.getValueFactory().setValue(selectedElement.getH());
        } catch (Exception ignored) {
        } finally {
            syncingGeometry = false;
        }
    }

    /* ===================== Magnet — snap to other objects / page ===================== */

    private static final double SNAP_TOLERANCE_MM = 2.0;

    private void clearSnapGuides() {
        guidesPane.getChildren().clear();
    }

    /** Draws the magenta magnet guide lines (mm positions; null = hide that axis). */
    private void showSnapGuides(Double vmMm, Double hmMm) {
        clearSnapGuides();
        if (template == null || template.getPage() == null) return;
        PageConfig page = template.getPage();
        if (vmMm != null) {
            Line l = new Line(vmMm * MM_PX, 0, vmMm * MM_PX, page.getHeight() * MM_PX);
            l.setStroke(Color.web("#EC4899"));
            l.setStrokeWidth(1.2);
            l.getStrokeDashArray().addAll(4.0, 3.0);
            guidesPane.getChildren().add(l);
        }
        if (hmMm != null) {
            Line l = new Line(0, hmMm * MM_PX, page.getWidth() * MM_PX, hmMm * MM_PX);
            l.setStroke(Color.web("#EC4899"));
            l.setStrokeWidth(1.2);
            l.getStrokeDashArray().addAll(4.0, 3.0);
            guidesPane.getChildren().add(l);
        }
    }

    /** All vertical magnet candidate lines (mm): page centre, margins, other objects' L/C/R. */
    private List<Double> magnetLinesX(TemplateElement moving) {
        List<Double> lines = new ArrayList<>();
        PageConfig page = template.getPage();
        lines.add(page.getWidth() / 2.0);
        PageConfig.Margins mg = page.getMargin();
        if (mg != null) {
            lines.add(mg.getLeft());
            lines.add(page.getWidth() - mg.getRight());
        }
        for (TemplateElement o : template.getElements()) {
            if (o == moving || o.isHidden()) continue;
            lines.add(o.getX());
            lines.add(o.getX() + o.getW() / 2.0);
            lines.add(o.getX() + o.getW());
        }
        return lines;
    }

    /** All horizontal magnet candidate lines (mm): page centre, margins, other objects' T/M/B. */
    private List<Double> magnetLinesY(TemplateElement moving) {
        List<Double> lines = new ArrayList<>();
        PageConfig page = template.getPage();
        lines.add(page.getHeight() / 2.0);
        PageConfig.Margins mg = page.getMargin();
        if (mg != null) {
            lines.add(mg.getTop());
            lines.add(page.getHeight() - mg.getBottom());
        }
        for (TemplateElement o : template.getElements()) {
            if (o == moving || o.isHidden()) continue;
            lines.add(o.getY());
            lines.add(o.getY() + o.getH() / 2.0);
            lines.add(o.getY() + o.getH());
        }
        return lines;
    }

    /**
     * Magnet snap for a MOVING object: aligns its left/centre/right edge to the
     * nearest vertical magnet line and top/middle/bottom to the nearest horizontal
     * line, within {@link #SNAP_TOLERANCE_MM}.
     *
     * @return {snappedX, snappedY, guideVmM(-1 when none), guideHmM(-1 when none)}
     */
    public double[] snapMove(TemplateElement moving, double x, double y) {
        double w = moving.getW(), h = moving.getH();
        double bestX = x, bestY = y;
        double guideV = -1, guideH = -1;
        double bestDX = SNAP_TOLERANCE_MM + 1e-6, bestDY = SNAP_TOLERANCE_MM + 1e-6;

        double[] myX = {x, x + w / 2.0, x + w};
        double[] myY = {y, y + h / 2.0, y + h};

        for (double line : magnetLinesX(moving)) {
            for (double mx : myX) {
                double d = Math.abs(mx - line);
                if (d < bestDX) {
                    bestDX = d;
                    bestX = x + (line - mx);
                    guideV = line;
                }
            }
        }
        for (double line : magnetLinesY(moving)) {
            for (double my : myY) {
                double d = Math.abs(my - line);
                if (d < bestDY) {
                    bestDY = d;
                    bestY = y + (line - my);
                    guideH = line;
                }
            }
        }
        return new double[]{Math.max(0, bestX), Math.max(0, bestY), guideV, guideH};
    }

    /** Nearest vertical object/margin/centre edge (mm) to a dragging edge; -1 when out of tolerance. */
    private double nearestObjectEdgeX(TemplateElement moving, double edgeXmM) {
        double best = -1, bestD = SNAP_TOLERANCE_MM + 1e-6;
        for (double line : magnetLinesX(moving)) {
            double d = Math.abs(edgeXmM - line);
            if (d < bestD) { bestD = d; best = line; }
        }
        return best;
    }

    /** Nearest horizontal object/margin/centre edge (mm) to a dragging edge; -1 when out of tolerance. */
    private double nearestObjectEdgeY(TemplateElement moving, double edgeYmM) {
        double best = -1, bestD = SNAP_TOLERANCE_MM + 1e-6;
        for (double line : magnetLinesY(moving)) {
            double d = Math.abs(edgeYmM - line);
            if (d < bestD) { bestD = d; best = line; }
        }
        return best;
    }

    private void handleGlobalKeyPress(KeyEvent e) {
        if (!isVisible() || getScene() == null) return;

        // Text-input safety: any key typed while editing a text control belongs
        // to that control. Check BOTH the event target and the scene focus owner
        // — the filter runs before the target, and a stale/steal focus scenario
        // previously let Backspace reach the delete shortcut and remove the
        // object being edited.
        boolean isTextInput = (e.getTarget() instanceof TextInputControl)
                || (getScene().getFocusOwner() instanceof TextInputControl);

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

        // View / snap toggles
        if (!e.isControlDown() && e.getCode() == KeyCode.M) {
            snapToObjects = !snapToObjects;
            if (!snapToObjects) clearSnapGuides();
            Toast.show(app.getRootPane(), "Magnet " + (snapToObjects ? "ON" : "OFF"),
                    snapToObjects ? "Edges snap to other objects & page centre." : "Free positioning — no object snapping.", false);
            e.consume();
            return;
        } else if (!e.isControlDown() && e.getCode() == KeyCode.G) {
            showGrid = !showGrid;
            refreshCanvas();
            e.consume();
            return;
        } else if (!e.isControlDown() && e.getCode() == KeyCode.R) {
            showRulers = !showRulers;
            refreshCanvas();
            e.consume();
            return;
        } else if (e.getCode() == KeyCode.F1) {
            showShortcutsDialog();
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
