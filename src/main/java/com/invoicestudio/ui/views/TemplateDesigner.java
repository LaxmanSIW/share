package com.invoicestudio.ui.views;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.db.SettingsDao;
import com.invoicestudio.db.TemplateDao;
import com.invoicestudio.db.VariableDao;
import com.invoicestudio.model.*;
import com.invoicestudio.model.TableColumn;
import com.invoicestudio.service.BarcodeService;
import com.invoicestudio.service.CustomComponentManager;
import com.invoicestudio.service.LabelGeometryService;
import com.invoicestudio.service.LabelPresets;
import com.invoicestudio.service.RenderContext;
import com.invoicestudio.service.TsplPrintService;
import com.invoicestudio.service.VariableGrouper;
import com.invoicestudio.ui.CustomColorChooserDialog;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.event.EventHandler;
import javafx.util.Duration;
import javafx.geometry.Bounds;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import javafx.stage.Window;
import javafx.scene.Parent;
import javafx.embed.swing.SwingFXUtils;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class TemplateDesigner extends BorderPane {

    private final StudioApp app;
    private final TemplateDao templateDao;
    private final SettingsDao settingsDao;
    private final VariableDao variableDao;

    // Session cache for template variables: loaded fresh once when opening the design page
    private final List<VariableDef> cachedTableScopeVariables = new ArrayList<>();
    private final Map<String, String> cachedVariableLabels = new HashMap<>();
    private final List<VariableDef> cachedAllVariables = new ArrayList<>();
    private final ObservableList<String> cachedColumnKeys = FXCollections.observableArrayList();
    private final List<Node> propBuffer = new ArrayList<>();

    private void addPropertyNode(Node node) {
        if (node != null) {
            propBuffer.add(node);
        }
    }

    private void addPropertyNodes(Node... nodes) {
        if (nodes != null) {
            for (Node n : nodes) {
                if (n != null) propBuffer.add(n);
            }
        }
    }

    private Template template;
    private TemplateElement selectedElement;
    private final DesignerState designerState = new DesignerState();

    private final Pane canvasContainer = new Pane();
    private final Pane canvas = new Pane();
    private final Pane gridPane = new Pane();
    private final Pane elementsPane = new Pane();
    private final Pane guideLayer = new Pane();
    private final Pane penLayer = new Pane();
    /** Blue dashed printable-margin boundary + legend — kept ABOVE every
     *  element so objects can never cover the guides. */
    private final Pane marginLayer = new Pane();
    private final Pane selectionPane = new Pane();
    private final Pane rulerTop = new Pane();
    private final Pane rulerLeft = new Pane();
    private final Pane rulerCorner = new Pane();
    /** Plain wrapper Group: a Group's layoutBounds = union of its children's
     *  boundsInParent (transforms INCLUDED), so scaling canvasContainer makes
     *  the scaled size visible to layout — the StackPane/ScrollPane then centre
     *  and scroll by the real visual size. Scaling the Group ITSELF would hide
     *  the zoom from layout (Group.layoutBounds excludes its own transform),
     *  which is exactly what let the canvas drift off the reachable (positive)
     *  scroll range at high zoom, cutting off its left side. */
    private final Group scaleGroup = new Group(canvasContainer);

    private double zoom = 0.9;
    /** Incremented on every zoom/centre request; queued anchor corrections
     *  skip themselves when a newer request superseded them. */
    private int anchorGeneration = 0;
    private double renderedGridStepMm = -1;
    /** Coalescing timer for the ruler repaint during zoom gestures (skill rule
     *  6.1 "coalesce-then-refine"): one trailing-edge repaint ~150 ms after
     *  the last zoom change settles, instead of one full rebuild per wheel
     *  notch. */
    private PauseTransition rulerRepaintDebounce;
    private boolean rulerRepaintPending;
    /** Grey panning margin (px) around the scaled canvas inside the wrapper.
     *  At least half the viewport per side, so the scaled content ALWAYS
     *  overflows the viewport and horizontal/vertical scrolling never dead-ends
     *  — even for a small label cell on a wide monitor (the wrapper is laid out
     *  at exactly pref size; ScrollPane does NOT stretch it). */
    private static final double WRAPPER_MARGIN_MIN_PX = 260.0;
    private Canvas gridCanvasNode;
    private boolean snapToGrid = true;
    private boolean magnetSnapping = true;
    private boolean showGrid = true;
    private boolean isPanMode = false;
    private boolean isPenToolMode = false;
    private boolean penCurveMode = false;
    private boolean isSpaceDown = false;
    private boolean isUpdatingLayersSelection = false;
    private boolean shiftWithMargins = true;
    private boolean updatingProperties = false;

    private final List<Point2D> penPoints = new ArrayList<>();

    private ScrollPane canvasScrollPane;
    private StackPane centerWrapper;

    private Button selectToolBtn;
    private Button panToolBtn;
    private Button penToolBtn;

    /* ---- Barcode (label) mode ---- */
    private Button barcodeModeBtn;
    private Button pageSettingsBtn;
    private Button labelSettingsBtn;
    private Button stripPreviewBtn;
    private Button bulkPrintBtn;

    private Spinner<Double> geoXSpin;
    private Spinner<Double> geoYSpin;
    private Spinner<Double> geoWSpin;
    private Spinner<Double> geoHSpin;
    private Spinner<Double> geoRotSpin;
    private ComboBox<UnitConverter.Unit> geoUnitBox;

    private final TextField nameField = new TextField();
    private final Label zoomLabel = new Label("90%");
    private final VBox propBox = new VBox(12);
    private final ListView<TemplateElement> layersList = new ListView<>();
    private final javafx.collections.ObservableList<TemplateElement> layersData = FXCollections.observableArrayList();
    private final FilteredList<TemplateElement> filteredLayers = new FilteredList<>(layersData, p -> true);
    private final Label layerCountBadge = new Label("0");
    private final MenuButton compMenu = new MenuButton("📦 Components");
    private final TabPane sideTabs = new TabPane();
    private Label coordStatusLabel;
    private Label pageFormatLabel;
    private Slider zoomSlider;
    private boolean updatingZoom = false;
    private double currentCursorXMm = -1;
    private double currentCursorYMm = -1;

    private static final double MM_PX = 3.7795275591; // ~96 DPI screen pixels per mm
    private static final double RULER_SIZE = 22.0;

    public TemplateDesigner(StudioApp app, Template template) {
        this.app = app;
        this.templateDao = new TemplateDao(app.getDb());
        this.settingsDao = new SettingsDao(app.getDb());
        this.variableDao = new VariableDao(app.getDb());
        loadSessionVariables();
        this.template = template != null ? template : PresetTemplates.buildClassic();
        if (this.template.isLabelMode()) {
            // Opened a saved label template: canvas must equal the label cell.
            this.template.labelOrNew().sanitize();
            syncPageFromLabelConfig();
        }

        getStyleClass().add("bg-app");
        canvas.getStyleClass().add("bill-sheet-canvas");
        guideLayer.setMouseTransparent(true);
        penLayer.setMouseTransparent(true);
        canvas.getChildren().addAll(gridPane, elementsPane, guideLayer, penLayer, marginLayer, selectionPane);
        gridPane.setMouseTransparent(true);
        marginLayer.setMouseTransparent(true);
        selectionPane.setPickOnBounds(false);

        canvasContainer.getChildren().addAll(rulerTop, rulerLeft, rulerCorner, canvas);

        setTop(createToolbar());

        SplitPane mainSplit = new SplitPane();
        mainSplit.getStyleClass().add("bg-transparent");
        Node canvasArea = createCanvasArea();
        Node sidebar = createSidebar();
        mainSplit.getItems().addAll(canvasArea, sidebar);
        mainSplit.setDividerPositions(0.68);
        SplitPane.setResizableWithParent(sidebar, false);
        setCenter(mainSplit);
        setBottom(createFooterBar());

        setupKeyboardShortcuts();
        saveState();
        refreshCanvas();
        // Sync the visual scale with the zoom field — the wrapper/layout above
        // already assume this zoom, so the painted content must match from
        // frame one (previously the scale stayed 1.0 until the first zoom).
        canvasContainer.setScaleX(zoom);
        canvasContainer.setScaleY(zoom);
        updatePropertiesPanel();
        refreshLayersList();
    }

    /**
     * Loads fresh table-scope and all template variables from SQLite once upon opening the design page.
     * Caches keys, labels, and definitions in memory for instant O(1) lookups during the design session.
     */
    private void loadSessionVariables() {
        cachedTableScopeVariables.clear();
        cachedVariableLabels.clear();
        cachedAllVariables.clear();
        if (variableDao != null) {
            try {
                List<VariableDef> tableVars = variableDao.getTableScopeVariables();
                if (tableVars != null) {
                    cachedTableScopeVariables.addAll(tableVars);
                    for (VariableDef v : tableVars) {
                        if (v != null && v.getKey() != null) {
                            String keyNorm = v.getKey().toLowerCase().trim();
                            String label = v.getLabel() != null && !v.getLabel().isBlank() ? v.getLabel() : v.getKey();
                            cachedVariableLabels.put(keyNorm, label + " (" + v.getKey() + ")");
                        }
                    }
                }
                List<VariableDef> allVars = variableDao.getAllVariables();
                if (allVars != null) {
                    cachedAllVariables.addAll(allVars);
                }
            } catch (Exception e) {
                // Fallback gracefully on DB read error
            }
        }

        List<String> keys = new java.util.ArrayList<>(List.of(
            "sr", "desc", "hsn", "qty", "unit", "rate",
            "gst", "disc", "taxable", "amount",
            "batch_no", "exp_date", "mrp", "serial_no", "part_no"
        ));
        for (VariableDef v : cachedTableScopeVariables) {
            if (v != null && v.getKey() != null && !keys.contains(v.getKey())) {
                keys.add(v.getKey());
            }
        }
        cachedColumnKeys.setAll(keys);
    }

    private Node createToolbar() {
        VBox rootToolbar = new VBox(0);
        rootToolbar.getStyleClass().add("designer-toolbar");

        // 1. Top Title & Tab Navigation Bar
        HBox topBar = new HBox(8);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(5, 10, 4, 10));
        topBar.setStyle("-fx-background-color: #0B1120; -fx-border-color: #1E293B; -fx-border-width: 0 0 1 0;");

        Button backBtn = createIconToolBtn(IconHelper.ICON_NAV_BACK, "Back", "Return to Templates Directory", () -> app.showTemplates());

        nameField.setText(template.getName());
        nameField.setPrefWidth(160);
        nameField.setMinWidth(100);
        nameField.setTooltip(new Tooltip("Template Name"));
        nameField.textProperty().addListener((obs, old, val) -> template.setName(val));

        Separator topSep = new Separator(javafx.geometry.Orientation.VERTICAL);

        // Home Tab Pill
        Button homeTabBtn = new Button("Home");
        homeTabBtn.getStyleClass().addAll("button-sm", "tool-active");
        homeTabBtn.setStyle("-fx-font-weight: bold; -fx-padding: 4 16; -fx-cursor: hand;");
        homeTabBtn.setTooltip(new Tooltip("Home Tab: Core element creation, drawing tools, and page layout"));

        // Barcode Mode pill — switches the designer into thermal label design.
        barcodeModeBtn = new Button("Barcode Mode");
        barcodeModeBtn.getStyleClass().addAll("button-sm", "designer-icon-btn");
        barcodeModeBtn.setGraphic(IconHelper.getToolbarIcon(IconHelper.ICON_LABEL_MODE));
        barcodeModeBtn.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        barcodeModeBtn.setTooltip(new Tooltip("Barcode Mode: design ONE label cell and bulk-print it on label strip stock (e.g. TSC TA210) · Ctrl+Shift+L"));
        barcodeModeBtn.setOnAction(e -> toggleBarcodeMode());
        styleBarcodeModeButton();

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Button saveBtn = new Button("Save Template");
        saveBtn.getStyleClass().addAll("gold-btn");
        saveBtn.setMinWidth(Region.USE_PREF_SIZE);
        saveBtn.setTooltip(new Tooltip("Save template changes to database (Ctrl S)"));
        saveBtn.setOnAction(e -> saveTemplate());

        topBar.getChildren().addAll(backBtn, nameField, topSep, homeTabBtn, barcodeModeBtn, topSpacer, saveBtn);

        // 2. Home Tab Grouped Ribbon
        HBox ribbon = new HBox(6);
        ribbon.setAlignment(Pos.CENTER_LEFT);
        ribbon.setPadding(new Insets(6, 10, 6, 10));
        ribbon.setStyle("-fx-background-color: #0F172A;");

        // --- GROUP 1: Insert & Content (Text, Table, Shapes, Media, Code, Components) ---
        HBox insertGroup = new HBox(4);
        insertGroup.setAlignment(Pos.CENTER_LEFT);

        Button addText = createIconToolBtn(IconHelper.ICON_FONT, "Text", "Add dynamic or static text label", () -> addElement(ElementType.TEXT));
        Button addTable = createIconToolBtn(IconHelper.ICON_TABLE, "Table", "Add line-item billing table with GST", () -> addElement(ElementType.TABLE));

        // Shapes Dropdown MenuButton
        MenuButton shapesMenu = new MenuButton("Shapes");
        shapesMenu.setGraphic(IconHelper.getMenuIcon("shapes", "#CBD5E1"));
        shapesMenu.getStyleClass().addAll("button-sm", "menu-button", "designer-menubtn");
        shapesMenu.setTooltip(new Tooltip("Add shapes (Rectangles, Circles, Arrows, Paths, Stars, Polygons)"));
        shapesMenu.setMinWidth(Region.USE_PREF_SIZE);
        shapesMenu.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold;");

        MenuItem rectItem = createStyledMenuItem("Rectangle / Box", "shape-rect", () -> addElement(ElementType.RECT));
        MenuItem roundRectItem = createStyledMenuItem("Rounded Card", "shape-round-rect", this::addRoundedRect);
        MenuItem circleItem = createStyledMenuItem("Circle", "shape-circle", () -> addElement(ElementType.CIRCLE));
        MenuItem ellipseItem = createStyledMenuItem("Ellipse", "shape-ellipse", () -> addElement(ElementType.ELLIPSE));
        MenuItem lineHItem = createStyledMenuItem("Horizontal Line", "shape-line-h", () -> addLine("h"));
        MenuItem lineVItem = createStyledMenuItem("Vertical Line", "shape-line-v", () -> addLine("v"));
        MenuItem arrowItem = createStyledMenuItem("Arrow", "shape-arrow", () -> addElement(ElementType.ARROW));
        MenuItem starItem = createStyledMenuItem("Star Shape", "shape-star", () -> addElement(ElementType.STAR));
        MenuItem polyItem = createStyledMenuItem("Polygon Shape", "shape-polygon", () -> addElement(ElementType.POLYGON));
        MenuItem arcItem = createStyledMenuItem("Arc Shape", "shape-arc", () -> addElement(ElementType.ARC));
        MenuItem pathItem = createStyledMenuItem("Custom SVG Path", "shape-path", () -> addElement(ElementType.PATH));
        MenuItem penItem = createStyledMenuItem("Vector Pen Tool (P)", "shape-pen", this::activatePenTool);
        MenuItem divItem = createStyledMenuItem("Divider", "shape-divider", () -> addElement(ElementType.DIVIDER));
        MenuItem signItem = createStyledMenuItem("Freehand Signature", "shape-signature", () -> addElement(ElementType.FREEHAND));
        MenuItem wmItem = createStyledMenuItem("Watermark", "shape-watermark", () -> addElement(ElementType.WATERMARK));

        shapesMenu.getItems().addAll(rectItem, roundRectItem, circleItem, ellipseItem, lineHItem, lineVItem,
                arrowItem, starItem, polyItem, arcItem, pathItem, penItem, divItem, signItem, wmItem);

        // Media Dropdown MenuButton
        MenuButton mediaMenu = new MenuButton("Media");
        mediaMenu.setGraphic(IconHelper.getMenuIcon("media", "#CBD5E1"));
        mediaMenu.getStyleClass().addAll("button-sm", "menu-button", "designer-menubtn");
        mediaMenu.setTooltip(new Tooltip("Add image, SVG, or icons"));
        mediaMenu.setMinWidth(Region.USE_PREF_SIZE);
        mediaMenu.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold;");

        MenuItem imgItem = createStyledMenuItem("Business Logo / Image", "media-image", () -> addElement(ElementType.IMAGE));
        MenuItem svgItem = createStyledMenuItem("SVG Vector", "media-svg", () -> addElement(ElementType.SVG));
        MenuItem iconItem = createStyledMenuItem("Icon Glyph", "shape-star", () -> addElement(ElementType.ICON));

        mediaMenu.getItems().addAll(imgItem, svgItem, iconItem);

        // Components Dropdown MenuButton
        compMenu.getStyleClass().addAll("button-sm", "menu-button", "designer-menubtn");
        compMenu.setTooltip(new Tooltip("Add pre-built or custom saved template layout blocks"));
        compMenu.setMinWidth(Region.USE_PREF_SIZE);
        compMenu.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold;");
        rebuildComponentsMenu();

        // Codes Dropdown MenuButton
        MenuButton codeMenu = new MenuButton("Code");
        codeMenu.setGraphic(IconHelper.getMenuIcon("code", "#CBD5E1"));
        codeMenu.getStyleClass().addAll("button-sm", "menu-button", "designer-menubtn");
        codeMenu.setTooltip(new Tooltip("Add dynamic QR or Barcode"));
        codeMenu.setMinWidth(Region.USE_PREF_SIZE);
        codeMenu.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold;");

        MenuItem qrItem = createStyledMenuItem("UPI QR Code", "code-qr", () -> addElement(ElementType.QRCODE));
        MenuItem barItem = createStyledMenuItem("Invoice Barcode", "code-barcode", () -> addElement(ElementType.BARCODE));

        codeMenu.getItems().addAll(qrItem, barItem);

        insertGroup.getChildren().addAll(addText, addTable, shapesMenu, mediaMenu, compMenu, codeMenu);

        Separator s1 = new Separator(javafx.geometry.Orientation.VERTICAL);

        // --- GROUP 2: Vector & Drawing Tools (Select, Pan, Pen, Curve Toggle) ---
        HBox toolsGroup = new HBox(4);
        toolsGroup.setAlignment(Pos.CENTER_LEFT);

        selectToolBtn = createIconToolBtn(IconHelper.ICON_TOOL_SELECT, "Select", "Select & Move Tool (V)", () -> {
            if (isPenToolMode) cancelPenTool();
            setPanMode(false);
        });
        panToolBtn = createIconToolBtn(IconHelper.ICON_TOOL_HAND, "Pan", "Pan Canvas Tool (H / Space)", () -> {
            if (isPenToolMode) cancelPenTool();
            setPanMode(true);
        });
        penToolBtn = createIconToolBtn(IconHelper.ICON_EDIT, "Pen", "Vector Pen Tool (P) - Click canvas to place vertices", () -> {
            if (isPenToolMode) cancelPenTool();
            else activatePenTool();
        });
        Button penCurveBtn = createIconToolBtn(penCurveMode ? IconHelper.ICON_CURVE : IconHelper.ICON_TOOL_LINE,
                penCurveMode ? "Curve" : "Straight", "Toggle Pen Mode: Straight Lines vs Smooth Bezier Curves", null);
        penCurveBtn.setOnAction(e -> {
            penCurveMode = !penCurveMode;
            penCurveBtn.setText(penCurveMode ? "Curve" : "Straight");
            penCurveBtn.setGraphic(IconHelper.getToolbarIcon(penCurveMode ? IconHelper.ICON_CURVE : IconHelper.ICON_TOOL_LINE));
            if (isPenToolMode && !penPoints.isEmpty()) {
                renderPenPreview(penPoints.get(penPoints.size() - 1).getX(), penPoints.get(penPoints.size() - 1).getY());
            }
        });

        toolsGroup.getChildren().addAll(selectToolBtn, panToolBtn, penToolBtn, penCurveBtn);
        updateToolButtons();

        Separator s2 = new Separator(javafx.geometry.Orientation.VERTICAL);

        // --- GROUP 3: Layout & Page (Page Settings, Grid, Snap, Magnet) ---
        HBox pageGroup = new HBox(6);
        pageGroup.setAlignment(Pos.CENTER_LEFT);

        // In Barcode Mode the canvas page IS the label cell, so "Page" and
        // "Label Stock" were two buttons for the same dialog — "Page" hides
        // there and Label Stock is the single source of truth (user ask).
        pageSettingsBtn = createIconToolBtn(IconHelper.ICON_SETTINGS, "Page", "Configure page dimensions, paper size & margins",
                this::showPageSettingsDialog);

        // Barcode-mode-only controls (hidden on normal bill templates)
        labelSettingsBtn = createIconToolBtn(IconHelper.ICON_TAG, "Label Stock", "Label strip settings: columns, label size, gaps, margins, corners, orientation", this::showLabelSettingsDialog);
        stripPreviewBtn = createIconToolBtn(IconHelper.ICON_STRIP_PREVIEW, "Strip Preview", "Preview how the label strip looks (columns × rows, gaps & rounded corners)", this::showStripPreviewDialog);
        bulkPrintBtn = createIconToolBtn(IconHelper.ICON_PRINT, "Bulk Print", "Print hundreds of labels with different variable values (Ctrl+Shift+B)", this::showBulkPrintDialog);
        updateLabelButtonsVisibility();

        CheckBox gridCb = new CheckBox("Grid");
        gridCb.setSelected(true);
        gridCb.setMinWidth(Region.USE_PREF_SIZE);
        gridCb.setTooltip(new Tooltip("Toggle background alignment grid (adapts 10 → 5 → 2 → 1 mm as you zoom in)"));
        gridCb.selectedProperty().addListener((obs, old, val) -> {
            showGrid = val;
            refreshCanvas();
        });

        CheckBox snapCb = new CheckBox("Snap");
        snapCb.setSelected(true);
        snapCb.setMinWidth(Region.USE_PREF_SIZE);
        snapCb.setTooltip(new Tooltip("Snap element positioning to 1mm grid"));
        snapCb.selectedProperty().addListener((obs, old, val) -> snapToGrid = val);

        CheckBox magnetCb = new CheckBox("Magnet");
        magnetCb.setGraphic(IconHelper.getToolbarIcon(IconHelper.ICON_MAGNET));
        magnetCb.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        magnetCb.setSelected(true);
        magnetCb.setMinWidth(Region.USE_PREF_SIZE);
        magnetCb.setTooltip(new Tooltip("Snap object borders to align and collapse with other objects and margins"));
        magnetCb.selectedProperty().addListener((obs, old, val) -> magnetSnapping = val);

        pageGroup.getChildren().addAll(pageSettingsBtn, labelSettingsBtn, stripPreviewBtn, bulkPrintBtn, gridCb, snapCb, magnetCb);

        Separator s3 = new Separator(javafx.geometry.Orientation.VERTICAL);

        // --- GROUP 4: Help & Support ---
        HBox helpGroup = new HBox(4);
        helpGroup.setAlignment(Pos.CENTER_LEFT);
        Button helpBtn = createIconToolBtn(IconHelper.ICON_HELP, "Help", "Keyboard Shortcuts & Designer Guide (F1)", this::showShortcutsHelpDialog);
        helpGroup.getChildren().add(helpBtn);

        Separator s4 = new Separator(javafx.geometry.Orientation.VERTICAL);

        // --- GROUP 5: History View ---
        HBox viewGroup = new HBox(4);
        viewGroup.setAlignment(Pos.CENTER_LEFT);

        Button undoBtn = createIconToolBtn(IconHelper.ICON_UNDO, "Undo", "Undo last change (Ctrl Z)", this::undo);
        Button redoBtn = createIconToolBtn(IconHelper.ICON_REDO, "Redo", "Redo undone change (Ctrl Y)", this::redo);

        viewGroup.getChildren().addAll(undoBtn, redoBtn);

        ribbon.getChildren().addAll(
                insertGroup, s1,
                toolsGroup, s2,
                pageGroup, s3,
                helpGroup, s4,
                viewGroup
        );

        // The ribbon (2nd row) is the widest row — in Barcode Mode it grows by
        // Label Stock / Strip Preview / Bulk Print. If it dictated the width of
        // this VBox, the BorderPane would widen the WHOLE top area past the
        // viewport and the right-aligned "Save Template" button ended up off
        // screen. Scrolling the ribbon horizontally keeps the top bar (with
        // Save) pinned to the real window width; excess tools scroll instead.
        ScrollPane ribbonScroll = new ScrollPane(ribbon);
        ribbonScroll.getStyleClass().add("ribbon-scroll");
        ribbonScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        ribbonScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        ribbonScroll.setFitToWidth(false);
        ribbonScroll.setFitToHeight(true);
        ribbonScroll.setPannable(false);

        rootToolbar.getChildren().addAll(topBar, ribbonScroll);
        return rootToolbar;
    }

    private MenuItem createStyledMenuItem(String text, String iconName, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (iconName != null) {
            Node icon = IconHelper.getMenuIcon(iconName, "#94A3B8");
            item.setGraphic(icon);
        }
        if (action != null) {
            item.setOnAction(e -> action.run());
        }
        return item;
    }

    private void setPanMode(boolean pan) {
        if (pan && isPenToolMode) {
            cancelPenTool();
        }
        this.isPanMode = pan;
        updateToolButtons();
        Cursor cur = isPanMode ? Cursor.OPEN_HAND : (isPenToolMode ? Cursor.CROSSHAIR : Cursor.DEFAULT);
        if (canvasScrollPane != null) canvasScrollPane.setCursor(cur);
        if (centerWrapper != null) centerWrapper.setCursor(cur);
        canvas.setCursor(cur);
    }

    private void activatePenTool() {
        this.isPanMode = false;
        this.isPenToolMode = true;
        this.selectedElement = null;
        updateSelectionOverlay();
        updatePropertiesPanel();
        penPoints.clear();
        penLayer.getChildren().clear();
        updateToolButtons();
        Cursor cur = Cursor.CROSSHAIR;
        if (canvasScrollPane != null) canvasScrollPane.setCursor(cur);
        if (centerWrapper != null) centerWrapper.setCursor(cur);
        canvas.setCursor(cur);
        Toast.show(this, "Pen Tool: Click canvas to place vertices. Double-click or click start point to close. Esc to cancel.");
    }

    private void cancelPenTool() {
        this.isPenToolMode = false;
        penPoints.clear();
        penLayer.getChildren().clear();
        updateToolButtons();
        Cursor cur = isPanMode ? Cursor.OPEN_HAND : Cursor.DEFAULT;
        if (canvasScrollPane != null) canvasScrollPane.setCursor(cur);
        if (centerWrapper != null) centerWrapper.setCursor(cur);
        canvas.setCursor(cur);
    }

    private void updateToolButtons() {
        if (selectToolBtn == null || panToolBtn == null) return;
        if (isPenToolMode) {
            styleToolButton(selectToolBtn, false);
            styleToolButton(panToolBtn, false);
            if (penToolBtn != null) styleToolButton(penToolBtn, true);
        } else if (isPanMode) {
            styleToolButton(selectToolBtn, false);
            styleToolButton(panToolBtn, true);
            if (penToolBtn != null) styleToolButton(penToolBtn, false);
        } else {
            styleToolButton(selectToolBtn, true);
            styleToolButton(panToolBtn, false);
            if (penToolBtn != null) styleToolButton(penToolBtn, false);
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
        b.setMinWidth(Region.USE_PREF_SIZE);
        if (tooltip != null) b.setTooltip(new Tooltip(tooltip));
        if (action != null) b.setOnAction(e -> action.run());
        return b;
    }

    /** Toolbar button with a crisp SVG glyph (CSS-recolorable) + label — replaces emoji text buttons. */
    private Button createIconToolBtn(String iconName, String text, String tooltip, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().addAll("button-sm", "button-secondary", "designer-icon-btn");
        b.setGraphic(IconHelper.getToolbarIcon(iconName));
        b.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        b.setMinWidth(Region.USE_PREF_SIZE);
        if (tooltip != null) b.setTooltip(new Tooltip(tooltip));
        if (action != null) b.setOnAction(e -> action.run());
        return b;
    }

    private void setZoom(double z) {
        double oldZoom = this.zoom;
        this.zoom = Math.max(0.3, Math.min(4.0, z));

        // Viewport anchor: remember which content point currently sits at the
        // centre of the viewport (in designer px, canvas-origin convention) so
        // it can be re-centred after the zoom. This keeps zooming "around the
        // view centre": the canvas can never silently drift off the left/top
        // edge. The capture MEASURES the real on-screen canvas position
        // (localToScene) — exact, no dependence on layout-bounds quirks or
        // possibly-stale laid-out wrapper sizes (mixing those was what dragged
        // every zoom step towards the centre: "zoom always comes to centre").
        double anchorX = Double.NaN, anchorY = Double.NaN;
        if (canvasScrollPane != null && centerWrapper != null && oldZoom > 0) {
            // The measured positions still reflect the scale CURRENTLY applied
            // to the canvas (the old zoom) — divide by exactly that, not by the
            // new zoom, otherwise the capture and the correction cancel each
            // other out and zooming stops re-anchoring altogether.
            double scaleApplied = canvasContainer.getScaleX() > 0 ? canvasContainer.getScaleX() : oldZoom;
            double[] centre = designerPointAtViewportCentre(scaleApplied);
            if (centre != null) {
                anchorX = centre[0];
                anchorY = centre[1];
            }
        }

        // Scale the CONTENT (canvasContainer), not the wrapper Group — see the
        // field comment on scaleGroup: this makes the scaled size participate
        // in layout so centring and scroll ranges are always correct.
        canvasContainer.setScaleX(zoom);
        canvasContainer.setScaleY(zoom);
        zoomLabel.setText((int) Math.round(zoom * 100) + "%");
        if (zoomSlider != null && !updatingZoom) {
            updatingZoom = true;
            zoomSlider.setValue(zoom);
            updatingZoom = false;
        }
        updateCenterWrapperSize();
        // Re-render the grid on EVERY zoom change: the canvas is drawn at device
        // resolution so its lines always stay exactly 1 device px (hairline-sharp,
        // never blurry or thick). The adaptive step additionally refines
        // 10 -> 5 -> 2 -> 1 mm as you zoom in, keeping cells a usable size.
        if (showGrid) {
            rebuildGridForZoom();
        } else {
            buildMarginGuides(); // keep guide hairline scale in sync with zoom
        }

        // Rebuild the selection overlay so handles/border/rotate-stem rescale
        // with the new zoom (they are 1/zoom design px — screen-constant).
        // An open inline editor is committed first: its geometry is designed
        // for the zoom it was opened at, and typing across a zoom change is
        // not a real workflow.
        if (activeInlineEditor != null) {
            commitInlineTextEdit(true);
        }
        updateSelectionOverlay();

        // Re-centre the anchored content point once the layout pulse has run.
        // correctViewportAnchor computes the target scroll position ABSOLUTELY
        // from live measurements, so scheduling it after the pulse converges on
        // the exact position no matter when the pulse lands, and survives
        // scrollbar appearance at the new zoom.
        if (!Double.isNaN(anchorX)) {
            scheduleAnchorCorrection(anchorX, anchorY);
        }
    }

    /**
     * The designer-space point (canvas-origin px) currently under the viewport
     * centre, measured from live node positions — or null if not measurable.
     * Positions are interpreted at the scale currently APPLIED to the canvas
     * (which during a zoom transition is still the previous zoom).
     */
    private double[] designerPointAtViewportCentre(double scaleApplied) {
        Node viewportNode = canvasScrollPane.lookup(".viewport");
        if (viewportNode == null) return null;
        Bounds vp = canvasScrollPane.getViewportBounds();
        Point2D co = canvas.localToScene(0, 0);
        Point2D vo = viewportNode.localToScene(0, 0);
        if (co == null || vo == null || scaleApplied <= 0) return null;
        return new double[]{(vp.getWidth() / 2.0 - (co.getX() - vo.getX())) / scaleApplied,
                            (vp.getHeight() / 2.0 - (co.getY() - vo.getY())) / scaleApplied};
    }

    /**
     * Scrolls so the designer-space point (ax, ay) sits at the viewport
     * centre. Purely measurement-based and ABSOLUTE (never accumulates
     * deltas): it derives where the canvas actually sits inside the wrapper
     * from live node positions, then sets h/v to the exact value that puts
     * (ax, ay) under the viewport centre. Exact whenever the layout is
     * settled; if it fires mid-layout the next scheduled run recomputes from
     * scratch and converges.
     */
    private void correctViewportAnchor(int generation, double ax, double ay) {
        if (generation != anchorGeneration) return; // superseded by a newer zoom/centre
        if (canvasScrollPane == null || centerWrapper == null) return;
        Node viewportNode = canvasScrollPane.lookup(".viewport");
        if (viewportNode == null) return;
        Bounds vp = canvasScrollPane.getViewportBounds();
        Point2D co = canvas.localToScene(0, 0);
        Point2D vo = viewportNode.localToScene(0, 0);
        if (co == null || vo == null) return;
        double rangeW = centerWrapper.getWidth() - vp.getWidth();
        double rangeH = centerWrapper.getHeight() - vp.getHeight();
        if (rangeW > 0.5) {
            // canvas origin measured in viewport coords + current scroll offset
            // = its stable position inside the wrapper
            double canvasXInWrapper = (co.getX() - vo.getX()) + canvasScrollPane.getHvalue() * rangeW;
            double desiredRelX = vp.getWidth() / 2.0 - ax * zoom;
            double hv = clamp01((canvasXInWrapper - desiredRelX) / rangeW);
            if (Boolean.getBoolean("zoom.debug")) {
                System.out.printf(java.util.Locale.US,
                        "[CORR] g=%d relX=%.1f h=%.3f rangeW=%.1f cxInWrap=%.1f want=%.1f zoom=%.2f -> h'=%.3f%n",
                        generation, co.getX() - vo.getX(), canvasScrollPane.getHvalue(), rangeW,
                        canvasXInWrapper, desiredRelX, zoom, hv);
            }
            canvasScrollPane.setHvalue(hv);
        }
        if (rangeH > 0.5) {
            double canvasYInWrapper = (co.getY() - vo.getY()) + canvasScrollPane.getVvalue() * rangeH;
            double desiredRelY = vp.getHeight() / 2.0 - ay * zoom;
            double vv = clamp01((canvasYInWrapper - desiredRelY) / rangeH);
            if (Boolean.getBoolean("zoom.debug")) {
                System.out.printf(java.util.Locale.US,
                        "[CORR] g=%d relY=%.1f v=%.3f rangeH=%.1f cyInWrap=%.1f wantY=%.1f zoom=%.2f -> v'=%.3f%n",
                        generation, co.getY() - vo.getY(), canvasScrollPane.getVvalue(), rangeH,
                        canvasYInWrapper, desiredRelY, zoom, vv);
            }
            canvasScrollPane.setVvalue(vv);
        }
    }

    /** Schedules the anchor correction shortly after the zoom/centre change.
     *  Corrections run ONLY once the layout pulse has settled: ScrollPane
     *  preserves the PIXEL scroll offset (not the normalized h/v value) when
     *  the content is resized, so any value set before the layout would be
     *  silently re-scaled and lost. Two post-layout passes (50 ms, 150 ms)
     *  make the result deterministic and scrollbar-change-proof. */
    private void scheduleAnchorCorrection(double ax, double ay) {
        final int generation = ++anchorGeneration;
        for (double delayMs : new double[]{50, 150}) {
            PauseTransition t = new PauseTransition(javafx.util.Duration.millis(delayMs));
            t.setOnFinished(e -> correctViewportAnchor(generation, ax, ay));
            t.play();
        }
    }

    /** Centres the scaled canvas in the viewport (used after page-size changes, FIT, …). */
    private void centerView() {
        if (canvasScrollPane == null || centerWrapper == null) return;
        // Defer until the pending refreshCanvas/page-size layout has run, then
        // centre on the canvas (page) itself — canvas size is fresh right after
        // refreshCanvas set its pref sizes, no laid-out bounds involved.
        javafx.application.Platform.runLater(() -> {
            if (canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
                canvas.widthProperty().addListener(new javafx.beans.InvalidationListener() {
                    @Override public void invalidated(javafx.beans.Observable o) {
                        canvas.widthProperty().removeListener(this);
                        scheduleAnchorCorrection(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
                    }
                });
                return;
            }
            scheduleAnchorCorrection(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
        });
    }

    /** FIT: choose the largest zoom that still shows the whole page, then centre it. */
    private void fitToView() {
        if (canvasScrollPane == null) { setZoom(0.85); centerView(); return; }
        Bounds vp = canvasScrollPane.getViewportBounds();
        PageConfig page = template.getPage();
        double w = page.getWidth() * MM_PX + RULER_SIZE;
        double h = page.getHeight() * MM_PX + RULER_SIZE;
        double z = Math.min((vp.getWidth() - 70) / w, (vp.getHeight() - 70) / h);
        setZoom(z);
        centerView();
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * Adaptive background-grid step in millimetres, chosen from the zoom level:
     * up to 105% -> 10 mm, up to 205% -> 5 mm, up to 305% -> 2 mm, beyond -> 1 mm
     * (one step finer per +100% zoom, keeping on-screen cells roughly constant).
     */
    private double gridStepMm() {
        if (zoom <= 1.05) return 10.0;
        if (zoom <= 2.05) return 5.0;
        if (zoom <= 3.05) return 2.0;
        return 1.0;
    }

    /** Swaps only the grid canvas (cheap) on zoom changes; the rulers rebuild
     *  solely when the adaptive major step changed. */
    private void rebuildGridForZoom() {
        PageConfig page = template.getPage();
        double pageW = page.getWidth() * MM_PX;
        double pageH = page.getHeight() * MM_PX;
        if (gridCanvasNode != null) {
            gridPane.getChildren().remove(gridCanvasNode);
            gridCanvasNode = null;
        }
        buildGridCanvas(pageW, pageH);
        buildMarginGuides(); // guides keep hairline stroke & legend size in sync with zoom
        // Rulers are screen-constant (tick lengths & fonts are 1/zoom local px)
        // AND their 1-2-5 scale adapts to zoom — they must be redrawn on every
        // zoom change, but NOT once per wheel notch: a full rebuild clears and
        // recreates hundreds of Line/Label nodes mid-gesture, which is exactly
        // the jank Ctrl+scroll feels. Industry pattern (canvas editors, map
        // tiles): keep the scene valid during the gesture and schedule ONE
        // trailing-edge repaint after the last zoom change settles.
        scheduleRulerRepaint(pageW, pageH);
    }

    /**
     * Coalesces ruler repaints: at most one repaint stays pending, always for
     * the latest zoom/page metrics. The current rulers remain on screen (valid
     * for the previous zoom) until the gesture settles, then a single repaint
     * swaps them for the exact new scale. {@link #flushPendingRulerRepaint()}
     * forces the swap immediately (used by tests and full re-renders).
     */
    private void scheduleRulerRepaint(double pageW, double pageH) {
        rulerRepaintPending = true;
        if (rulerRepaintDebounce == null) {
            rulerRepaintDebounce = new PauseTransition(Duration.millis(150));
            rulerRepaintDebounce.setOnFinished(e -> {
                if (!rulerRepaintPending) return;
                rulerRepaintPending = false;
                PageConfig pg = template.getPage();
                buildRulers(pg.getWidth() * MM_PX, pg.getHeight() * MM_PX);
            });
        }
        rulerRepaintDebounce.playFromStart();
    }

    /** Runs a pending coalesced ruler repaint NOW (no-op if none pending). */
    void flushPendingRulerRepaint() {
        if (rulerRepaintPending && rulerRepaintDebounce != null) {
            rulerRepaintDebounce.stop();
            rulerRepaintDebounce.getOnFinished().handle(null);
        }
    }

    /**
     * (Re)draws the blue dashed printable-margin boundary + "Printable …"
     * legend on {@link #marginLayer}, which sits ABOVE every element so objects
     * can never cover the guides. Re-invoked on zoom changes so the stroke,
     * dash pattern and legend font stay hairline/screen-constant at any zoom.
     */
    private void buildMarginGuides() {
        marginLayer.getChildren().clear();
        PageConfig page = template.getPage();
        double pageW = page.getWidth() * MM_PX;
        double pageH = page.getHeight() * MM_PX;
        marginLayer.setPrefSize(pageW, pageH);
        PageConfig.Margins mg = page.getMargin();
        if (mg == null) return;
        double mx = mg.getLeft() * MM_PX;
        double my = mg.getTop() * MM_PX;
        double mw = (page.getWidth() - mg.getLeft() - mg.getRight()) * MM_PX;
        double mh = (page.getHeight() - mg.getTop() - mg.getBottom()) * MM_PX;
        if (mw <= 0 || mh <= 0) return;

        // Guides stay hairline-thin on screen at ANY zoom: a fixed 1.0 local-px
        // stroke would render zoom× thicker (blurry band at 300-400%). Dividing
        // by zoom keeps it 1 device px.
        double hair = 1.0 / Math.max(0.3, zoom);
        Rectangle marginBox = new Rectangle(mx, my, mw, mh);
        marginBox.setFill(Color.TRANSPARENT);
        marginBox.setStroke(Color.web("#3B82F6", 0.65));
        marginBox.setStrokeWidth(hair);
        marginBox.getStrokeDashArray().addAll(4.0 * hair, 4.0 * hair);
        marginBox.setMouseTransparent(true);
        marginLayer.getChildren().add(marginBox);

        // Legend font shrinks as zoom increases (9px / zoom in canvas space ≈
        // constant 9px on screen, ever smaller relative to the artwork) so it
        // never buries the elements underneath it.
        String legend = String.format(
                "Printable: %.0f×%.0f mm  (Margin: T:%.1f B:%.1f L:%.1f R:%.1f)",
                Math.max(0, page.getWidth() - mg.getLeft() - mg.getRight()),
                Math.max(0, page.getHeight() - mg.getTop() - mg.getBottom()),
                mg.getTop(), mg.getBottom(), mg.getLeft(), mg.getRight());
        if (template.isLabelMode()) {
            LabelConfig lc = template.labelOrNew();
            legend += String.format("  · Stock L:%.1f R:%.1f · %d across",
                    lc.getMarginL(), lc.getMarginR(), lc.getColumns());
        }
        Label mLabel = new Label(legend);
        mLabel.setStyle(String.format(java.util.Locale.US,
                "-fx-font-size: %.2fpx; -fx-font-family: 'Segoe UI', sans-serif; -fx-text-fill: #3B82F6;"
                        + " -fx-background-color: rgba(59,130,246,0.12); -fx-padding: %.2f %.2f; -fx-background-radius: %.2f;",
                9.0 * hair, 1.0 * hair, 5.0 * hair, 3.0 * hair));
        mLabel.setLayoutX(mx + 4 * hair);
        mLabel.setLayoutY(my + 3 * hair);
        mLabel.setMouseTransparent(true);
        marginLayer.getChildren().add(mLabel);
    }

    /**
     * Renders the background grid canvas at DEVICE resolution with the current
     * adaptive step. A Canvas is a bitmap: if it were left at 100% size, the
     * enclosing zoom transform would stretch that bitmap and the lines would
     * turn thick & blurry. Giving the canvas an inverse scale cancels the group
     * zoom for the raster, so every line maps 1:1 onto screen pixels, gets
     * snapped to the pixel grid (+0.5) and stays exactly 1 device px — thin and
     * sharp at any zoom. Every 5th line is drawn slightly darker for depth.
     */
    private void buildGridCanvas(double pageW, double pageH) {
        if (!showGrid) return;
        double stepMm = gridStepMm();
        renderedGridStepMm = stepMm;

        double deviceW = Math.max(1.0, Math.ceil(pageW * zoom));
        double deviceH = Math.max(1.0, Math.ceil(pageH * zoom));
        Canvas gridCanvas = new Canvas(deviceW, deviceH);
        if (Math.abs(zoom - 1.0) > 1e-9) {
            gridCanvas.getTransforms().add(new Scale(1.0 / zoom, 1.0 / zoom));
        }

        GraphicsContext gc = gridCanvas.getGraphicsContext2D();
        double stepPx = stepMm * MM_PX * zoom;   // cell size in device pixels
        gc.setLineWidth(1.0);                    // 1 device px — the thinnest possible
        Color minor = Color.web("#ececee");
        Color major = Color.web("#d9d9de");
        int i = 1;
        for (double x = stepPx; x < deviceW; x += stepPx) {
            gc.setStroke(i % 5 == 0 ? major : minor);
            double sx = Math.round(x) + 0.5;
            gc.strokeLine(sx, 0, sx, deviceH);
            i++;
        }
        i = 1;
        for (double y = stepPx; y < deviceH; y += stepPx) {
            gc.setStroke(i % 5 == 0 ? major : minor);
            double sy = Math.round(y) + 0.5;
            gc.strokeLine(0, sy, deviceW, sy);
            i++;
        }
        gridCanvas.setMouseTransparent(true);
        gridCanvasNode = gridCanvas;
        gridPane.getChildren().add(0, gridCanvas);
    }

    private void updateCenterWrapperSize() {
        if (centerWrapper == null) return;
        PageConfig page = template.getPage();
        double pageW = page.getWidth() * MM_PX;
        double pageH = page.getHeight() * MM_PX;

        double totalContainerW = pageW + RULER_SIZE;
        double totalContainerH = pageH + RULER_SIZE;

        canvasContainer.setPrefSize(totalContainerW, totalContainerH);
        canvasContainer.setMinSize(totalContainerW, totalContainerH);
        canvasContainer.setMaxSize(totalContainerW, totalContainerH);

        double scaledW = totalContainerW * zoom;
        double scaledH = totalContainerH * zoom;

        // Generous margin so canvas can pan horizontally and vertically freely
        // past viewport bounds — half the viewport per side guarantees the
        // wrapper always exceeds the viewport (scroll range = scaled size).
        double marginX = wrapperMarginX();
        double marginY = wrapperMarginY();
        double totalW = scaledW + (marginX * 2);
        double totalH = scaledH + (marginY * 2);

        centerWrapper.setPrefSize(totalW, totalH);
        centerWrapper.setMinSize(totalW, totalH);
    }

    /** Per-axis panning margin around the scaled canvas (never below the
     *  classic 260 px; grows to half the viewport so small canvases stay
     *  freely scrollable on wide windows). */
    private double wrapperMarginX() {
        if (canvasScrollPane != null) {
            Bounds vp = canvasScrollPane.getViewportBounds();
            if (vp != null && vp.getWidth() > 0) {
                return Math.max(WRAPPER_MARGIN_MIN_PX, vp.getWidth() / 2.0);
            }
        }
        return WRAPPER_MARGIN_MIN_PX;
    }

    private double wrapperMarginY() {
        if (canvasScrollPane != null) {
            Bounds vp = canvasScrollPane.getViewportBounds();
            if (vp != null && vp.getHeight() > 0) {
                return Math.max(WRAPPER_MARGIN_MIN_PX, vp.getHeight() / 2.0);
            }
        }
        return WRAPPER_MARGIN_MIN_PX;
    }

    private Node createFooterBar() {
        HBox footer = new HBox(12);
        footer.getStyleClass().add("designer-footer-bar");
        footer.setAlignment(Pos.CENTER_LEFT);

        pageFormatLabel = new Label();
        pageFormatLabel.getStyleClass().add("designer-footer-pageinfo");
        updatePageFormatLabel();

        coordStatusLabel = new Label();
        coordStatusLabel.getStyleClass().add("designer-footer-coords");
        updateStatusBarCoords();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button fitBtn = new Button("FIT");
        fitBtn.getStyleClass().add("designer-footer-btn");
        fitBtn.setTooltip(new Tooltip("Fit page in view (Ctrl 0)"));
        fitBtn.setOnAction(e -> fitToView());

        Button zoomMinusBtn = new Button("−");
        zoomMinusBtn.getStyleClass().add("designer-footer-btn");
        zoomMinusBtn.setTooltip(new Tooltip("Zoom out (Ctrl -)"));
        zoomMinusBtn.setOnAction(e -> setZoom(zoom - 0.1));

        zoomSlider = new Slider(0.3, 4.0, zoom);
        zoomSlider.getStyleClass().add("designer-footer-slider");
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingZoom && newVal != null) {
                updatingZoom = true;
                setZoom(newVal.doubleValue());
                updatingZoom = false;
            }
        });

        Button zoomPlusBtn = new Button("+");
        zoomPlusBtn.getStyleClass().add("designer-footer-btn");
        zoomPlusBtn.setTooltip(new Tooltip("Zoom in (Ctrl +)"));
        zoomPlusBtn.setOnAction(e -> setZoom(zoom + 0.1));

        zoomLabel.getStyleClass().clear();
        zoomLabel.getStyleClass().add("designer-footer-zoomlabel");
        zoomLabel.setText((int) Math.round(zoom * 100) + "%");

        footer.getChildren().addAll(
                pageFormatLabel,
                coordStatusLabel,
                spacer,
                fitBtn,
                zoomMinusBtn,
                zoomSlider,
                zoomPlusBtn,
                zoomLabel
        );

        return footer;
    }

    private void updatePageFormatLabel() {
        if (pageFormatLabel == null) return;
        if (template != null && template.getPage() != null) {
            PageConfig p = template.getPage();
            String name = p.getSizeName() != null ? p.getSizeName().name() : "CUSTOM";
            if (template.isLabelMode()) {
                com.invoicestudio.model.LabelConfig cfg = template.labelOrNew();
                pageFormatLabel.setText(String.format(Locale.US,
                        "LABEL %d × %d mm · %d across · strip %d mm",
                        (int) Math.round(cfg.getLabelWidth()),
                        (int) Math.round(cfg.getLabelHeight()),
                        cfg.getColumns(),
                        (int) Math.round(cfg.getStripWidth())));
            } else {
                pageFormatLabel.setText(String.format(Locale.US, "%s (%d × %d mm)",
                        name, (int) Math.round(p.getWidth()), (int) Math.round(p.getHeight())));
            }
        } else {
            pageFormatLabel.setText("A4 (210 × 297 mm)");
        }
    }

    private void updateStatusBarCoords() {
        if (coordStatusLabel == null) return;
        if (selectedElement != null) {
            coordStatusLabel.setText(String.format(Locale.US,
                    "X: %.1f mm   Y: %.1f mm   W: %.1f mm   H: %.1f mm   Rot: %.0f°",
                    selectedElement.getX(),
                    selectedElement.getY(),
                    selectedElement.getW(),
                    selectedElement.getH(),
                    selectedElement.getRotation()));
        } else if (currentCursorXMm >= 0 && currentCursorYMm >= 0) {
            coordStatusLabel.setText(String.format(Locale.US,
                    "X: %.1f mm   Y: %.1f mm",
                    currentCursorXMm,
                    currentCursorYMm));
        } else {
            coordStatusLabel.setText("X: -- mm   Y: -- mm");
        }
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

        // Event filters for Pen Tool mode so clicks anywhere on canvas (even over existing elements) register reliably
        canvas.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (isPenToolMode) {
                if (e.getButton() == MouseButton.PRIMARY) {
                    handlePenCanvasClick(e);
                    e.consume();
                } else if (e.getButton() == MouseButton.SECONDARY) {
                    cancelPenTool();
                    e.consume();
                }
            }
        });

        canvas.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (isPenToolMode) {
                handlePenCanvasMove(e);
            }
            Point2D pt = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            currentCursorXMm = Math.max(0, pt.getX() / MM_PX);
            currentCursorYMm = Math.max(0, pt.getY() / MM_PX);
            if (selectedElement == null) {
                updateStatusBarCoords();
            }
        });

        canvas.addEventFilter(MouseEvent.MOUSE_EXITED, e -> {
            currentCursorXMm = -1;
            currentCursorYMm = -1;
            if (selectedElement == null) {
                updateStatusBarCoords();
            }
        });

        // Deselect when clicking on empty canvas in Select mode
        canvas.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown() && !isPanMode && !isSpaceDown && !isPenToolMode) {
                if (activeInlineEditor != null) {
                    commitInlineTextEdit(true);
                }
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

        // Modern Header: Title, Elements Count Badge, and Toolbar
        HBox layersHeader = new HBox(8);
        layersHeader.setAlignment(Pos.CENTER_LEFT);
        Label layersTitle = new Label("Canvas Layers");
        layersTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #E2E8F0;");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        layerCountBadge.getStyleClass().add("layer-count-badge");
        layerCountBadge.setText(template.getElements().size() + " items");
        layersHeader.getChildren().addAll(layersTitle, headerSpacer, layerCountBadge);

        // Search / Filter Field
        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search layers by name, type, group...");
        searchField.getStyleClass().add("layer-search-field");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                filteredLayers.setPredicate(p -> true);
            } else {
                String filter = newVal.toLowerCase().trim();
                filteredLayers.setPredicate(el ->
                        (el.getDisplayName() != null && el.getDisplayName().toLowerCase().contains(filter)) ||
                        (el.getType() != null && el.getType().name().toLowerCase().contains(filter)) ||
                        (el.getGroupName() != null && el.getGroupName().toLowerCase().contains(filter))
                );
            }
        });

        // Layer Action Buttons (Group, Ungroup, Toggle All, Up, Down, Delete)
        HBox layerActions = new HBox(6);
        layerActions.setAlignment(Pos.CENTER_LEFT);
        Button grpBtn = createToolbarBtn("📁 Group", "Group selected layers or element (Ctrl+G)", this::groupSelected);
        Button ungrpBtn = createToolbarBtn("📂 Ungroup", "Ungroup selected elements (Ctrl+Shift+G)", this::ungroupSelected);
        Button togAllBtn = createToolbarBtn("👁 All", "Toggle visibility of all layers", this::toggleAllVisibility);
        Button upBtn = createToolbarBtn("▲", "Bring layer forward", () -> moveLayer(1));
        Button downBtn = createToolbarBtn("▼", "Send layer backward", () -> moveLayer(-1));
        Button delBtn = createToolbarBtn("🗑", "Delete selected element (Del)", this::deleteSelected);
        layerActions.getChildren().addAll(grpBtn, ungrpBtn, togAllBtn, upBtn, downBtn, delBtn);

        // Layers List with Card ListCell
        layersList.getStyleClass().add("layers-list");
        layersList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        layersList.setItems(filteredLayers);
        VBox.setVgrow(layersList, Priority.ALWAYS);

        layersList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TemplateElement item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(null);

                    HBox card = new HBox(8);
                    card.setAlignment(Pos.CENTER_LEFT);
                    card.getStyleClass().add("layer-card");
                    if (isSelected()) {
                        card.getStyleClass().add("layer-card-selected");
                    }

                    // Element Type Badge (TXT, TBL, IMG, SHP, etc.)
                    Label typeBadge = new Label(getTypeGlyph(item));
                    typeBadge.getStyleClass().add("layer-type-badge");
                    typeBadge.setStyle("-fx-background-color: " + getTypeBadgeBg(item) + ";");
                    typeBadge.setTooltip(new Tooltip("Element Type: " + item.getType().name()));

                    // Center Labels: Name and Optional Group indicator
                    VBox infoBox = new VBox(2);
                    infoBox.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(infoBox, Priority.ALWAYS);

                    Label nameLbl = new Label(item.getDisplayName());
                    nameLbl.getStyleClass().add("layer-cell-label");
                    if (item.isHidden()) {
                        nameLbl.setStyle("-fx-opacity: 0.45;");
                    } else if (item.isLocked()) {
                        nameLbl.setStyle("-fx-opacity: 0.85;");
                    }
                    infoBox.getChildren().add(nameLbl);

                    if (item.isGrouped()) {
                        Label groupLbl = new Label("📁 " + (item.getGroupName() != null ? item.getGroupName() : "Group"));
                        groupLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #D9A13B; -fx-font-weight: bold;");
                        infoBox.getChildren().add(groupLbl);
                    }

                    // Visibility Toggle
                    Button hideBtn = new Button(item.isHidden() ? "🚫" : "👁");
                    hideBtn.getStyleClass().addAll("button-xs", "layer-action-btn");
                    hideBtn.setTooltip(new Tooltip(item.isHidden() ? "Element is hidden. Click to show" : "Element is visible. Click to hide"));
                    hideBtn.setOnAction(e -> {
                        item.setHidden(!item.isHidden());
                        hideBtn.setText(item.isHidden() ? "🚫" : "👁");
                        saveState();
                        refreshCanvas();
                        if (item == selectedElement && item.isHidden()) {
                            updateSelectionOverlay();
                        }
                        refreshLayersList();
                        e.consume();
                    });

                    // Lock Toggle
                    Button lockBtn = new Button(item.isLocked() ? "🔒" : "🔓");
                    lockBtn.getStyleClass().addAll("button-xs", "layer-action-btn");
                    lockBtn.setTooltip(new Tooltip(item.isLocked() ? "Element is locked. Click to unlock" : "Element is unlocked. Click to lock position"));
                    lockBtn.setOnAction(e -> {
                        item.setLocked(!item.isLocked());
                        lockBtn.setText(item.isLocked() ? "🔒" : "🔓");
                        saveState();
                        updatePropertiesPanel();
                        refreshLayersList();
                        e.consume();
                    });

                    card.getChildren().addAll(typeBadge, infoBox, hideBtn, lockBtn);
                    setGraphic(card);
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

        layersBox.getChildren().addAll(layersHeader, searchField, layerActions, layersList);
        layersTab.setContent(layersBox);

        sideTabs.getStyleClass().add("bg-side");
        sideTabs.getTabs().addAll(propTab, layersTab);
        side.getChildren().add(sideTabs);
        VBox.setVgrow(sideTabs, Priority.ALWAYS);
        return side;
    }

    /**
     * Canonical design-tool ruler scale (researched: Photoshop/Illustrator
     * rulers, chart "nice ticks"): majors follow the 1-2-5 progression and are
     * sized so adjacent majors stay at least ~32 screen px apart; minor ticks
     * subdivide each major (÷10, ÷5 or ÷2 — the first that keeps minors at
     * least ~3 screen px apart). The result: zooming in progressively REVEALS
     * finer true-mm divisions instead of stretching the old ones, and numbers
     * always sit on the long major ticks with their real millimetre value.
     */
    private double rulerMajorStepMm() {
        double pxPerMm = MM_PX * zoom;
        for (double s : new double[]{1, 2, 5, 10, 20, 50, 100, 200, 500}) {
            if (s * pxPerMm >= 32) return s;
        }
        return 500;
    }

    private double rulerMinorStepMm(double major) {
        double pxPerMm = MM_PX * zoom;
        if (major / 10 * pxPerMm >= 3) return major / 10;
        if (major / 5 * pxPerMm >= 3) return major / 5;
        return major / 2;
    }

    private void buildRulers(double pageW, double pageH) {
        rulerRepaintPending = false; // a synchronous rebuild supersedes any queued one
        rulerTop.getChildren().clear();
        rulerLeft.getChildren().clear();
        rulerCorner.getChildren().clear();

        rulerTop.setPrefSize(pageW, RULER_SIZE);
        rulerTop.setMinSize(pageW, RULER_SIZE);
        rulerTop.setMaxSize(pageW, RULER_SIZE);
        rulerTop.setStyle("-fx-background-color: #121824; -fx-border-color: #2e3a4e; -fx-border-width: 0 0 1 0;");

        rulerLeft.setPrefSize(RULER_SIZE, pageH);
        rulerLeft.setMinSize(RULER_SIZE, pageH);
        rulerLeft.setMaxSize(RULER_SIZE, pageH);
        rulerLeft.setStyle("-fx-background-color: #121824; -fx-border-color: #2e3a4e; -fx-border-width: 0 1 0 0;");

        rulerCorner.setStyle("-fx-background-color: #0d121b; -fx-border-color: #2e3a4e; -fx-border-width: 0 1 1 0;");
        Label mmLbl = new Label("mm");
        mmLbl.setStyle("-fx-font-size: 8px; -fx-text-fill: #d9a13b; -fx-font-weight: bold; -fx-padding: 3 0 0 4;");
        rulerCorner.getChildren().add(mmLbl);

        double totalMmW = template.getPage().getWidth();
        double totalMmH = template.getPage().getHeight();

        double majorStep = rulerMajorStepMm();
        double minorStep = rulerMinorStepMm(majorStep);
        buildRulerTicks(rulerTop, true, totalMmW, majorStep, minorStep);
        buildRulerTicks(rulerLeft, false, totalMmH, majorStep, minorStep);
    }

    /**
     * Draws one ruler as a 3-tier tick system (researched standard): MINOR
     * ticks are short, MID ticks (major/2) medium, MAJOR ticks longest and the
     * only ones carrying numbers — so a long strip always means a labelled,
     * true-millimetre position. Positions snap to whole device pixels, keeping
     * every tick 1 device px hairline-sharp at any zoom; duplicate snapped
     * positions skip themselves so minors never pile up when zoomed far out.
     *
     * <p>Tick lengths and number size: SCREEN px = base × max(1, zoom).
     * Zoomed OUT (≤100%) keeps the previous screen-constant sizes (readability
     * floor, behavior unchanged). Zoomed IN the ruler strip itself scales with
     * the page (22 px → 22·zoom), so the old constant 10 px ticks / 8 px
     * numbers looked lost inside a 44–66 px strip — now they grow with it so
     * numbers and tick heights stay readable at every zoom level.</p>
     */
    private void buildRulerTicks(Pane ruler, boolean horizontal, double totalMm,
                                 double majorStep, double minorStep) {
        double z = Math.max(0.3, zoom);
        double k = Math.max(1.0, z) / z;   // local px = base·k  →  screen px = base·max(1, z)
        double lenMajor = 10.0 * k, lenMid = 6.5 * k, lenMinor = 4.0 * k;
        long sub = Math.round(majorStep / minorStep);
        boolean hasMid = sub % 2 == 0 && sub > 2;      // mid tier only if exactly halfway exists
        long midEvery = hasMid ? sub / 2 : -1;
        Color cMajor = Color.web("#94a3b8"), cMid = Color.web("#5b6b82"), cMinor = Color.web("#3d4a5e");
        double strokeWidth = 1.0 / z;
        double lastSnapped = -1e9;

        for (long i = 0; ; i++) {
            double v = i * minorStep;
            if (v > totalMm + 1e-9) break;
            boolean isMajor = i % sub == 0;
            boolean isMid = !isMajor && midEvery > 0 && i % midEvery == 0;

            // Snap to whole device pixels (local = device / zoom) for crisp hairlines
            double snapped = Math.round(v * MM_PX * zoom) / zoom;
            if (snapped <= lastSnapped + 1e-9 && i > 0) continue; // dedupe sub-pixel pile-up
            lastSnapped = snapped;

            double tickLen = isMajor ? lenMajor : (isMid ? lenMid : lenMinor);
            Color tickColor = isMajor ? cMajor : (isMid ? cMid : cMinor);
            Line tick = horizontal
                    ? new Line(snapped, RULER_SIZE - tickLen, snapped, RULER_SIZE)
                    : new Line(RULER_SIZE - tickLen, snapped, RULER_SIZE, snapped);
            tick.setStroke(tickColor);
            tick.setStrokeWidth(strokeWidth);
            ruler.getChildren().add(tick);

            // Numbers ONLY on major ticks — the exact measure the long strip marks
            if (isMajor && v > 1e-9) {
                String text = v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
                double fontPx = 8.0 * k;
                double extent = horizontal ? ruler.getPrefWidth() : ruler.getPrefHeight();
                // Space the label occupies along the ruler axis: width for the
                // top ruler, line-box height for the left ruler. Both track the
                // font scale (k) so the clip guard stays correct at any zoom.
                double labelExtent = horizontal
                        ? text.length() * 4.7 * k + 3.0 * k
                        : 11.5 * k;
                double pos = snapped + 2.0 * k;
                boolean isLastMajor = v + majorStep > totalMm + 1e-9;
                if (pos + labelExtent > extent) {
                    if (isLastMajor) {
                        // Page-end label: align flush with the edge instead of
                        // clipping, so the page's exact size stays readable.
                        pos = Math.max(0, extent - labelExtent);
                    } else {
                        // Interior label would clip past the edge — drop it;
                        // the major tick itself still marks the position.
                        continue;
                    }
                }
                Label lbl = new Label(text);
                lbl.setStyle(String.format(java.util.Locale.US,
                        "-fx-font-size: %.2fpx; -fx-text-fill: #94a3b8; -fx-font-family: 'Segoe UI', sans-serif;",
                        fontPx));
                if (horizontal) {
                    lbl.setLayoutX(pos);
                    lbl.setLayoutY(1);
                } else {
                    lbl.setLayoutX(1);
                    lbl.setLayoutY(pos);
                }
                ruler.getChildren().add(lbl);
            }
        }
    }

    private void refreshCanvas() {
        PageConfig page = template.getPage();
        double pageW = page.getWidth() * MM_PX;
        double pageH = page.getHeight() * MM_PX;

        canvas.setLayoutX(RULER_SIZE);
        canvas.setLayoutY(RULER_SIZE);
        canvas.setPrefSize(pageW, pageH);
        canvas.setMinSize(pageW, pageH);
        canvas.setMaxSize(pageW, pageH);
        canvas.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 24, 0, 0, 8);");

        rulerTop.setLayoutX(RULER_SIZE);
        rulerTop.setLayoutY(0);
        rulerLeft.setLayoutX(0);
        rulerLeft.setLayoutY(RULER_SIZE);
        rulerCorner.setLayoutX(0);
        rulerCorner.setLayoutY(0);
        rulerCorner.setPrefSize(RULER_SIZE, RULER_SIZE);
        rulerCorner.setMinSize(RULER_SIZE, RULER_SIZE);
        rulerCorner.setMaxSize(RULER_SIZE, RULER_SIZE);

        buildRulers(pageW, pageH);
        updateCenterWrapperSize();

        // 1. Background Grid (rendered on a single Canvas for maximum layout performance;
        //    the cell size adapts to zoom: 10 -> 5 -> 2 -> 1 mm)
        gridPane.getChildren().clear();
        gridPane.setPrefSize(pageW, pageH);
        buildGridCanvas(pageW, pageH);

        // Margin Guides (Printable Boundary) — drawn on the TOP margin layer
        buildMarginGuides();

        // 2. Elements Layer
        elementsPane.getChildren().clear();
        elementsPane.setPrefSize(pageW, pageH);
        RenderContext ctx = newDesignerRenderContext();

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

    /**
     * Selection-handle visual size in DESIGN px. The overlay lives inside the
     * zoom-scaled canvas container, so a fixed design px size would grow with
     * zoom (10 px handles became 40 px blobs at 400 %, swallowing small
     * elements). Dividing by the zoom keeps the handles a CONSTANT ~10 px ON
     * SCREEN at any zoom — they shrink as you zoom in and grow as you zoom
     * out, exactly like every professional design tool.
     */
    private double handleSizePx() {
        return Math.max(3.0, 10.0 / Math.max(0.3, zoom));
    }

    private Rectangle createHandleShape(Cursor cursor, double sizePx) {
        Rectangle h = new Rectangle(sizePx, sizePx);
        h.setFill(Color.web("#D9A13B"));
        // No dark outline — handles stay a small, clean gold dot the user can
        // still grab (the old 1.5 px black ring read as a heavy border and,
        // scaled by zoom, dominated small labels).
        h.setArcWidth(Math.max(1.0, sizePx * 0.2));
        h.setArcHeight(Math.max(1.0, sizePx * 0.2));
        h.setCursor(cursor);
        h.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), "
                + String.format(java.util.Locale.US, "%.2f, 0, 0, %.2f);",
                        3.0 / Math.max(0.3, zoom), 1.0 / Math.max(0.3, zoom)));
        h.setOnMouseEntered(e -> h.setFill(Color.WHITE));
        h.setOnMouseExited(e -> h.setFill(Color.web("#D9A13B")));
        return h;
    }

    private void setRecursivelyMouseTransparent(Node node) {
        if (node == null) return;
        node.setMouseTransparent(true);
        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                setRecursivelyMouseTransparent(child);
            }
        }
    }

    private double[] applySnapping(TemplateElement el, double rawX, double rawY) {
        double newX = rawX;
        double newY = rawY;
        guideLayer.getChildren().clear();

        PageConfig page = template.getPage();
        double pageW = page.getWidth();
        double pageH = page.getHeight();
        PageConfig.Margins mg = page.getMargin();

        double snappedGuideX = -1;
        double snappedGuideY = -1;

        if (magnetSnapping) {
            double snapThreshold = 1.5; // mm threshold for magnetic snapping
            double bestDistX = snapThreshold;
            double bestSnapX = newX;

            double bestDistY = snapThreshold;
            double bestSnapY = newY;

            // Candidates for X
            List<Double> xTargets = new ArrayList<>();
            if (mg != null) {
                xTargets.add(mg.getLeft());
                xTargets.add(Math.max(0, pageW - mg.getRight() - el.getW()));
            }
            xTargets.add(0.0);
            xTargets.add(Math.max(0, pageW - el.getW()));

            for (TemplateElement other : template.getElements()) {
                if (other == el || other.isHidden()) continue;
                xTargets.add(other.getX());
                xTargets.add(other.getX() - el.getW());
                xTargets.add(other.getX() + other.getW());
                xTargets.add(other.getX() + other.getW() - el.getW());
            }

            for (double targetX : xTargets) {
                double dist = Math.abs(newX - targetX);
                if (dist < bestDistX) {
                    bestDistX = dist;
                    bestSnapX = targetX;
                }
            }
            if (bestDistX < snapThreshold) {
                newX = Math.max(0, bestSnapX);
                snappedGuideX = newX;
            }

            // Candidates for Y
            List<Double> yTargets = new ArrayList<>();
            if (mg != null) {
                yTargets.add(mg.getTop());
                yTargets.add(Math.max(0, pageH - mg.getBottom() - el.getH()));
            }
            yTargets.add(0.0);
            yTargets.add(Math.max(0, pageH - el.getH()));

            for (TemplateElement other : template.getElements()) {
                if (other == el || other.isHidden()) continue;
                yTargets.add(other.getY());
                yTargets.add(other.getY() - el.getH());
                yTargets.add(other.getY() + other.getH());
                yTargets.add(other.getY() + other.getH() - el.getH());
            }

            for (double targetY : yTargets) {
                double dist = Math.abs(newY - targetY);
                if (dist < bestDistY) {
                    bestDistY = dist;
                    bestSnapY = targetY;
                }
            }
            if (bestDistY < snapThreshold) {
                newY = Math.max(0, bestSnapY);
                snappedGuideY = newY;
            }
        }

        if (snapToGrid && snappedGuideX < 0) {
            newX = Math.round(newX);
        }
        if (snapToGrid && snappedGuideY < 0) {
            newY = Math.round(newY);
        }

        if (snappedGuideX >= 0) {
            double gx = snappedGuideX * MM_PX;
            Line gLine = new Line(gx, 0, gx, pageH * MM_PX);
            gLine.setStroke(Color.web("#38BDF8"));
            gLine.setStrokeWidth(1.0);
            gLine.getStrokeDashArray().addAll(4.0, 3.0);
            guideLayer.getChildren().add(gLine);
        }
        if (snappedGuideY >= 0) {
            double gy = snappedGuideY * MM_PX;
            Line gLine = new Line(0, gy, pageW * MM_PX, gy);
            gLine.setStroke(Color.web("#38BDF8"));
            gLine.setStrokeWidth(1.0);
            gLine.getStrokeDashArray().addAll(4.0, 3.0);
            guideLayer.getChildren().add(gLine);
        }

        return new double[]{newX, newY};
    }

    private TextArea activeInlineEditor = null;
    private TemplateElement activeInlineEditingElement = null;

    private void startInlineTextEdit(TemplateElement el) {
        if (el == null || (el.getType() != ElementType.TEXT && el.getType() != ElementType.PAGENO)) return;
        if (activeInlineEditor != null) {
            commitInlineTextEdit(true);
        }

        if (selectedElement != el) {
            selectedElement = el;
            updatePropertiesPanel();
            syncLayersListSelection();
        }

        double x = el.getX() * MM_PX;
        double y = el.getY() * MM_PX;
        // Min editor size scales with 1/zoom too — a fixed design-space floor
        // made the editor dwarf the element it edits at high zoom.
        double w = Math.max(60.0 / Math.max(0.3, zoom), el.getW() * MM_PX);
        double h = Math.max(30.0 / Math.max(0.3, zoom), el.getH() * MM_PX);

        TextArea editor = new TextArea(el.getText() != null ? el.getText() : "");
        editor.setWrapText(true);
        editor.setLayoutX(x);
        editor.setLayoutY(y);
        editor.setPrefSize(w, h);
        editor.setMinSize(w, h);
        editor.setRotate(el.getRotation());

        String colorHex = el.getColor() != null && !el.getColor().isBlank() ? el.getColor() : "#1a1a1a";
        String family = el.getFontFamily() != null ? el.getFontFamily() : "Segoe UI";
        int weight = el.getFontWeight() > 0 ? el.getFontWeight() : (el.isBold() ? 700 : 400);
        String fs = el.isItalic() ? "italic" : "normal";
        // Same 1.3 pt→px factor the canvas renderer uses for TEXT elements, so
        // the text fills the editor exactly like the rendered element behind it.
        double fontSize = el.getFontSize() > 0 ? el.getFontSize() * 1.3 : 14.0;
        String bg = (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg())) ? el.getBg() : "#ffffff";

        // ZERO padding and a 1-screen-px border: the old 3×6 px padding + 2 px
        // border are design-space values that zoom blew up into a fat frame
        // around the text (reported: "padding which should not be there").
        double inv = 1.0 / Math.max(0.3, zoom);
        editor.setStyle(String.format(java.util.Locale.US,
                "-fx-font-family: '%s'; -fx-font-size: %.1fpx; -fx-font-weight: %d; -fx-font-style: %s; "
                + "-fx-text-fill: %s; -fx-background-color: %s; -fx-background-insets: 0; "
                + "-fx-border-color: #D9A13B; -fx-border-width: %.2fpx; -fx-border-radius: %.2fpx; "
                + "-fx-background-radius: %.2fpx; -fx-padding: 0;",
                family, fontSize, weight, fs, colorHex, bg, 1.0 * inv, 2.0 * inv, 2.0 * inv));

        editor.setOnKeyPressed(ke -> {
            if (ke.getCode() == KeyCode.ESCAPE) {
                commitInlineTextEdit(false);
                ke.consume();
            } else if (ke.getCode() == KeyCode.ENTER && !ke.isShiftDown()) {
                commitInlineTextEdit(true);
                ke.consume();
            }
        });

        editor.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && activeInlineEditor == editor) {
                commitInlineTextEdit(true);
            }
        });

        activeInlineEditor = editor;
        activeInlineEditingElement = el;

        selectionPane.getChildren().add(editor);

        // The TextArea skin's inner .content region carries its own opaque
        // background + padding from the user-agent stylesheet — it painted the
        // white frame around the text (and pushed the text down/up so the top
        // line could clip). Zero it so the editor shows exactly the element's
        // background and the text sits flush at the top-left, 1:1 with canvas.
        editor.applyCss();
        javafx.scene.Node content = editor.lookup(".content");
        if (content != null) {
            content.setStyle("-fx-padding: 0; -fx-background-color: transparent; "
                    + "-fx-background-radius: 0; -fx-background-insets: 0;");
        }

        javafx.application.Platform.runLater(() -> {
            if (activeInlineEditor == editor) {
                editor.requestFocus();
                editor.selectAll();
                // selectAll can leave the caret/viewport scrolled so the first
                // line hides above the clip (reported: text cut at the top).
                editor.setScrollTop(0);
                editor.setScrollLeft(0);
            }
        });
    }

    private void commitInlineTextEdit(boolean save) {
        if (activeInlineEditor == null) return;
        TextArea editor = activeInlineEditor;
        TemplateElement el = activeInlineEditingElement;
        activeInlineEditor = null;
        activeInlineEditingElement = null;

        if (selectionPane != null) {
            selectionPane.getChildren().remove(editor);
        }

        if (save && el != null) {
            String text = editor.getText();
            el.setText(text != null ? text : "");
            updateElementVisualInPlace(el);
            updatePropertiesPanel();
            saveState();
        }
        updateSelectionOverlay();
    }

    /**
     * Render context for the DESIGN CANVAS. Beyond the standard sample
     * values, every user-defined variable is resolved to its FIRST possible
     * value (fallback: its default value) so the canvas shows real content —
     * a barcode/size variable renders "28" instead of the raw
     * {@code {{size}}} placeholder. The element MODEL still stores the
     * {@code {{key}}} placeholder (inline editing and Bulk Print keep
     * working); only the painted preview resolves it.
     */
    private RenderContext newDesignerRenderContext() {
        RenderContext ctx = new RenderContext(null, settingsDao.getSettings(), 0, 1, 1);
        try {
            for (VariableDef v : variableDao.getAllVariables()) {
                if (v == null || v.getKey() == null || v.getKey().isBlank()) continue;
                List<String> choices = v.choicesList();
                if (!choices.isEmpty()) {
                    ctx.getValues().putIfAbsent(v.getKey(), choices.get(0));
                } else if (!v.getDefaultValue().isBlank()) {
                    ctx.getValues().putIfAbsent(v.getKey(), v.getDefaultValue());
                }
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored);
            // Variable source unavailable — canvas falls back to {{key}} text.
        }
        return ctx;
    }

    private Node createInteractiveElementNode(TemplateElement el, RenderContext ctx) {
        double x = el.getX() * MM_PX;
        double y = el.getY() * MM_PX;
        double w = el.getW() * MM_PX;
        double h = el.getH() * MM_PX;

        Pane wrapper = new Pane();
        wrapper.setUserData(el);
        wrapper.setLayoutX(x);
        wrapper.setLayoutY(y);
        wrapper.setPrefSize(w, h);
        wrapper.setMinSize(w, h);
        wrapper.setMaxSize(w, h);
        wrapper.setRotate(el.getRotation());
        wrapper.setPickOnBounds(true);
        wrapper.setCursor(Cursor.DEFAULT);
        wrapper.setStyle("-fx-background-color: rgba(255, 255, 255, 0.005);");

        // Explicit geometric hitArea so tables and transparent shapes capture clicks reliably
        Rectangle hitArea = new Rectangle(w, h);
        hitArea.setFill(Color.web("#FFFFFF", 0.005));
        hitArea.setPickOnBounds(true);
        hitArea.setCursor(Cursor.DEFAULT);
        wrapper.getChildren().add(hitArea);

        Node visual = renderVisualElement(el, ctx, w, h);
        if (visual != null) {
            setRecursivelyMouseTransparent(visual);
            wrapper.getChildren().add(visual);
        }

        // Element selection and moving (with synchronized multi-element group drag)
        final double[] moveStart = new double[4];
        final boolean[] isMoved = new boolean[1];
        final Map<TemplateElement, double[]> groupOrigins = new HashMap<>();

        wrapper.setOnMousePressed(e -> {
            if (isPanMode || isSpaceDown || e.getButton() == MouseButton.MIDDLE) return;
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY && (el.getType() == ElementType.TEXT || el.getType() == ElementType.PAGENO)) {
                startInlineTextEdit(el);
                e.consume();
                return;
            }
            if (e.isPrimaryButtonDown()) {
                moveStart[0] = e.getScreenX();
                moveStart[1] = e.getScreenY();
                moveStart[2] = el.getX();
                moveStart[3] = el.getY();
                isMoved[0] = false;
                groupOrigins.clear();

                if (el.isGrouped()) {
                    for (TemplateElement sibling : template.getElements()) {
                        if (Objects.equals(sibling.getGroupId(), el.getGroupId())) {
                            groupOrigins.put(sibling, new double[]{sibling.getX(), sibling.getY()});
                        }
                    }
                } else {
                    groupOrigins.put(el, new double[]{el.getX(), el.getY()});
                }

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

            double[] snapped = applySnapping(el, newX, newY);
            double finalDx = snapped[0] - moveStart[2];
            double finalDy = snapped[1] - moveStart[3];

            for (Map.Entry<TemplateElement, double[]> entry : groupOrigins.entrySet()) {
                TemplateElement sibling = entry.getKey();
                double[] orig = entry.getValue();
                double nx = Math.max(0, orig[0] + finalDx);
                double ny = Math.max(0, orig[1] + finalDy);
                sibling.setX(nx);
                sibling.setY(ny);
                for (Node n : elementsPane.getChildren()) {
                    if (n.getUserData() == sibling) {
                        n.setLayoutX(nx * MM_PX);
                        n.setLayoutY(ny * MM_PX);
                        break;
                    }
                }
            }

            updateSelectionOverlayPos(el.getX() * MM_PX, el.getY() * MM_PX);
            e.consume();
        });

        wrapper.setOnMouseReleased(e -> {
            guideLayer.getChildren().clear();
            if (isPanMode || isSpaceDown || e.getButton() == MouseButton.MIDDLE) return;
            if (isMoved[0]) {
                saveState();
                syncGeoSpinnersIfPresent();
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
        updateStatusBarCoords();
    }

    private void syncGeoSpinnersIfPresent() {
        updateStatusBarCoords();
        if (selectedElement == null || geoXSpin == null || geoUnitBox == null) return;
        updatingProperties = true;
        try {
            UnitConverter.Unit u = geoUnitBox.getValue() != null ? geoUnitBox.getValue() : UnitConverter.Unit.MM;
            double xVal = UnitConverter.fromMm(selectedElement.getX(), u);
            double yVal = UnitConverter.fromMm(selectedElement.getY(), u);
            double wVal = UnitConverter.fromMm(selectedElement.getW(), u);
            double hVal = UnitConverter.fromMm(selectedElement.getH(), u);
            if (geoXSpin.getValueFactory() != null) geoXSpin.getValueFactory().setValue(Math.round(xVal * 100.0) / 100.0);
            if (geoYSpin.getValueFactory() != null) geoYSpin.getValueFactory().setValue(Math.round(yVal * 100.0) / 100.0);
            if (geoWSpin.getValueFactory() != null) geoWSpin.getValueFactory().setValue(Math.round(wVal * 100.0) / 100.0);
            if (geoHSpin.getValueFactory() != null) geoHSpin.getValueFactory().setValue(Math.round(hVal * 100.0) / 100.0);
            if (geoRotSpin != null && geoRotSpin.getValueFactory() != null) {
                geoRotSpin.getValueFactory().setValue((double) Math.round(selectedElement.getRotation()));
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored);
        } finally {
            updatingProperties = false;
        }
    }

    private void updateElementVisualInPlace(TemplateElement el) {
        if (el == null) return;
        double wPx = el.getW() * MM_PX;
        double hPx = el.getH() * MM_PX;
        double xPx = el.getX() * MM_PX;
        double yPx = el.getY() * MM_PX;

        for (Node n : elementsPane.getChildren()) {
            if (n.getUserData() == el && n instanceof Pane wrapper) {
                wrapper.setLayoutX(xPx);
                wrapper.setLayoutY(yPx);
                wrapper.setPrefSize(wPx, hPx);
                wrapper.setMinSize(wPx, hPx);
                wrapper.setMaxSize(wPx, hPx);
                wrapper.setRotate(el.getRotation());
                wrapper.setOpacity(el.getOpacity());

                if (!wrapper.getChildren().isEmpty()) {
                    Node hitAreaNode = wrapper.getChildren().get(0);
                    if (hitAreaNode instanceof Rectangle hitArea) {
                        hitArea.setWidth(wPx);
                        hitArea.setHeight(hPx);
                    }
                }

                RenderContext ctx = newDesignerRenderContext();
                Node newVisual = renderVisualElement(el, ctx, wPx, hPx);
                if (newVisual != null) {
                    setRecursivelyMouseTransparent(newVisual);
                    if (wrapper.getChildren().size() > 1) {
                        wrapper.getChildren().set(1, newVisual);
                    } else {
                        wrapper.getChildren().add(newVisual);
                    }
                } else if (wrapper.getChildren().size() > 1) {
                    wrapper.getChildren().remove(1);
                }
                break;
            }
        }
    }

    private void updateLiveElementVisual(TemplateElement el, double newW, double newH, double newX, double newY) {
        double wPx = newW * MM_PX;
        double hPx = newH * MM_PX;
        for (Node n : elementsPane.getChildren()) {
            if (n.getUserData() == el && n instanceof Pane p) {
                p.setLayoutX(newX * MM_PX);
                p.setLayoutY(newY * MM_PX);
                p.setPrefSize(wPx, hPx);
                p.setMinSize(wPx, hPx);
                p.setMaxSize(wPx, hPx);
                for (Node child : p.getChildren()) {
                    if (child instanceof Rectangle rect) {
                        rect.setWidth(wPx);
                        rect.setHeight(hPx);
                    } else if (child instanceof Region r) {
                        r.setPrefSize(wPx, hPx);
                        r.setMinSize(wPx, hPx);
                        r.setMaxSize(wPx, hPx);
                        if (el.getType() == ElementType.TABLE && child instanceof VBox box) {
                            List<TableColumn> cols = el.getColumns();
                            if (cols == null || cols.isEmpty()) cols = PresetTemplates.defaultItemColumns();
                            for (Node rowNode : box.getChildren()) {
                                if (rowNode instanceof HBox row) {
                                    row.setPrefWidth(wPx);
                                    row.setMinWidth(wPx);
                                    row.setMaxWidth(wPx);
                                    List<Node> colLabels = row.getChildren();
                                    for (int ci = 0; ci < cols.size() && ci < colLabels.size(); ci++) {
                                        TableColumn col = cols.get(ci);
                                        double cW = (col.getWidth() / 100.0) * wPx;
                                        Node lblNode = colLabels.get(ci);
                                        if (lblNode instanceof Region colLbl) {
                                            colLbl.setPrefWidth(cW);
                                            colLbl.setMaxWidth(cW);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            }
        }
        updateStatusBarCoords();
    }

    private void updateSelBoxGeometry(Pane selBox, Rectangle border, Rectangle moveHitArea,
                                     Rectangle hNW, Rectangle hN, Rectangle hNE, Rectangle hE,
                                     Rectangle hSE, Rectangle hS, Rectangle hSW, Rectangle hW,
                                     Line rotateStem, Circle handleRotate,
                                     double nwPx, double nhPx) {
        selBox.setPrefSize(nwPx, nhPx);
        selBox.setMinSize(nwPx, nhPx);
        border.setWidth(nwPx);
        border.setHeight(nhPx);
        moveHitArea.setWidth(nwPx);
        moveHitArea.setHeight(nhPx);

        // Handle size scales inversely with zoom so handles stay ~10 px ON
        // SCREEN at every zoom (design-space 10 px became 40 px blobs at 400 %).
        double hSize = handleSizePx();
        double hHalf = hSize / 2.0;

        // Handles ALWAYS sit on the true corners of the selection box so they
        // match the dashed border exactly, even when the element extends past
        // the page edge (clamping here used to leave them stranded mid-air).
        double leftX = -hHalf;
        double rightX = nwPx - hHalf;
        double midX = (nwPx / 2.0) - hHalf;

        double topY = -hHalf;
        double bottomY = nhPx - hHalf;
        double midY = (nhPx / 2.0) - hHalf;

        hNW.setLayoutX(leftX);
        hNW.setLayoutY(topY);

        hN.setLayoutX(midX);
        hN.setLayoutY(topY);

        hNE.setLayoutX(rightX);
        hNE.setLayoutY(topY);

        hE.setLayoutX(rightX);
        hE.setLayoutY(midY);

        hSE.setLayoutX(rightX);
        hSE.setLayoutY(bottomY);

        hS.setLayoutX(midX);
        hS.setLayoutY(bottomY);

        hSW.setLayoutX(leftX);
        hSW.setLayoutY(bottomY);

        hW.setLayoutX(leftX);
        hW.setLayoutY(midY);

        double stemTopY = topY - (22.0 / Math.max(0.3, zoom));
        double stemCenterX = midX + hHalf;

        if (rotateStem != null) {
            rotateStem.setStartX(stemCenterX);
            rotateStem.setStartY(topY);
            rotateStem.setEndX(stemCenterX);
            rotateStem.setEndY(stemTopY);
        }
        if (handleRotate != null) {
            handleRotate.setCenterX(stemCenterX);
            handleRotate.setCenterY(stemTopY);
        }
    }

    private void updateSelectionOverlay() {
        if (activeInlineEditor != null && activeInlineEditingElement != selectedElement) {
            commitInlineTextEdit(true);
        }
        selectionPane.getChildren().clear();
        activeSelectionBox = null;
        updateStatusBarCoords();

        if (selectedElement == null || selectedElement.isHidden()) {
            return;
        }

        if (activeInlineEditor != null && activeInlineEditingElement == selectedElement) {
            selectionPane.getChildren().add(activeInlineEditor);
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
        selBox.setRotate(el.getRotation());
        selBox.setPickOnBounds(false); // Allows handles at negative coordinate offsets to be clicked
        activeSelectionBox = selBox;

        // Move Hit Area (invisible overlay to make dragging anywhere inside seamless)
        Rectangle moveHitArea = new Rectangle(w, h);
        moveHitArea.setFill(Color.web("#FFFFFF", 0.005));
        moveHitArea.setPickOnBounds(true);
        moveHitArea.setCursor(Cursor.MOVE);

        final double[] moveStart = new double[4];
        final boolean[] isMoved = new boolean[1];
        final Map<TemplateElement, double[]> groupOrigins = new HashMap<>();

        moveHitArea.setOnMousePressed(e -> {
            if (isPanMode || isSpaceDown || e.getButton() == MouseButton.MIDDLE) return;
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY && (el.getType() == ElementType.TEXT || el.getType() == ElementType.PAGENO)) {
                startInlineTextEdit(el);
                e.consume();
                return;
            }
            if (e.isPrimaryButtonDown()) {
                moveStart[0] = e.getScreenX();
                moveStart[1] = e.getScreenY();
                moveStart[2] = el.getX();
                moveStart[3] = el.getY();
                isMoved[0] = false;
                groupOrigins.clear();

                if (el.isGrouped()) {
                    for (TemplateElement sibling : template.getElements()) {
                        if (Objects.equals(sibling.getGroupId(), el.getGroupId())) {
                            groupOrigins.put(sibling, new double[]{sibling.getX(), sibling.getY()});
                        }
                    }
                } else {
                    groupOrigins.put(el, new double[]{el.getX(), el.getY()});
                }
                e.consume();
            }
        });

        moveHitArea.setOnMouseDragged(e -> {
            if (isPanMode || isSpaceDown || e.getButton() == MouseButton.MIDDLE) return;
            if (el.isLocked()) return;
            if (!e.isPrimaryButtonDown()) return;

            isMoved[0] = true;
            double dx = (e.getScreenX() - moveStart[0]) / zoom / MM_PX;
            double dy = (e.getScreenY() - moveStart[1]) / zoom / MM_PX;

            double newX = Math.max(0, moveStart[2] + dx);
            double newY = Math.max(0, moveStart[3] + dy);

            double[] snapped = applySnapping(el, newX, newY);
            double finalDx = snapped[0] - moveStart[2];
            double finalDy = snapped[1] - moveStart[3];

            for (Map.Entry<TemplateElement, double[]> entry : groupOrigins.entrySet()) {
                TemplateElement sibling = entry.getKey();
                double[] orig = entry.getValue();
                double nx = Math.max(0, orig[0] + finalDx);
                double ny = Math.max(0, orig[1] + finalDy);
                sibling.setX(nx);
                sibling.setY(ny);
                for (Node n : elementsPane.getChildren()) {
                    if (n.getUserData() == sibling) {
                        n.setLayoutX(nx * MM_PX);
                        n.setLayoutY(ny * MM_PX);
                        break;
                    }
                }
            }

            selBox.setLayoutX(el.getX() * MM_PX);
            selBox.setLayoutY(el.getY() * MM_PX);
            updateStatusBarCoords();
            e.consume();
        });

        moveHitArea.setOnMouseReleased(e -> {
            guideLayer.getChildren().clear();
            if (isPanMode || isSpaceDown || e.getButton() == MouseButton.MIDDLE) return;
            if (isMoved[0]) {
                saveState();
                syncGeoSpinnersIfPresent();
            }
            e.consume();
        });

        // Selection Border: high-contrast gold dashed border. Stroke width and
        // dash lengths are divided by the zoom so the border reads as the same
        // hairline weight ON SCREEN at any zoom (2/5/4 px design space became
        // 8/20/16 px chunks at 400 %).
        double inv = 1.0 / Math.max(0.3, zoom);
        Rectangle border = new Rectangle(w, h);
        border.setFill(Color.TRANSPARENT);
        border.setStroke(Color.web("#D9A13B"));
        border.setStrokeWidth(2.0 * inv);
        border.getStrokeDashArray().addAll(5.0 * inv, 4.0 * inv);
        border.setMouseTransparent(true);
        border.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), "
                + String.format(java.util.Locale.US, "%.2f, 0, 0, %.2f);", 4.0 * inv, 1.0 * inv) + "");

        // Rotation Handle and Stem — stem length/stroke shrink with zoom so the
        // whole assembly stays proportionate on screen; the rotate dot has NO
        // dark outline (the old black ring read as a heavy border at zoom).
        Line rotateStem = new Line();
        rotateStem.setStroke(Color.web("#D9A13B"));
        rotateStem.setStrokeWidth(1.5 * inv);
        rotateStem.getStrokeDashArray().addAll(3.0 * inv, 3.0 * inv);
        rotateStem.setMouseTransparent(true);

        Circle handleRotate = new Circle(5.5 * inv);
        handleRotate.setFill(Color.web("#D9A13B"));
        handleRotate.setCursor(Cursor.CROSSHAIR);
        handleRotate.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), "
                + String.format(java.util.Locale.US, "%.2f, 0, 0, %.2f);", 3.0 * inv, 1.0 * inv) + "");
        handleRotate.setOnMouseEntered(ev -> handleRotate.setFill(Color.WHITE));
        handleRotate.setOnMouseExited(ev -> handleRotate.setFill(Color.web("#D9A13B")));
        Tooltip.install(handleRotate, new Tooltip("Rotate shape (Drag to rotate, Shift to snap 15°)"));

        // 8 Resize Handles — created at the zoom-corrected size (see
        // handleSizePx): constant on-screen footprint, no black outline.
        double hs = handleSizePx();
        Rectangle handleNW = createHandleShape(Cursor.NW_RESIZE, hs);
        Rectangle handleN = createHandleShape(Cursor.N_RESIZE, hs);
        Rectangle handleNE = createHandleShape(Cursor.NE_RESIZE, hs);
        Rectangle handleE = createHandleShape(Cursor.E_RESIZE, hs);
        Rectangle handleSE = createHandleShape(Cursor.SE_RESIZE, hs);
        Rectangle handleS = createHandleShape(Cursor.S_RESIZE, hs);
        Rectangle handleSW = createHandleShape(Cursor.SW_RESIZE, hs);
        Rectangle handleW = createHandleShape(Cursor.W_RESIZE, hs);

        updateSelBoxGeometry(selBox, border, moveHitArea,
                handleNW, handleN, handleNE, handleE,
                handleSE, handleS, handleSW, handleW,
                rotateStem, handleRotate,
                w, h);

        final double[] rotCenter = new double[2];
        handleRotate.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                Point2D centerInScene = selBox.localToScene(w / 2.0, h / 2.0);
                rotCenter[0] = centerInScene.getX();
                rotCenter[1] = centerInScene.getY();
                e.consume();
            }
        });

        handleRotate.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dx = e.getSceneX() - rotCenter[0];
            double dy = e.getSceneY() - rotCenter[1];
            double rad = Math.atan2(dy, dx);
            double deg = Math.toDegrees(rad) + 90.0;
            if (deg < 0) deg += 360.0;
            if (deg >= 360.0) deg -= 360.0;

            if (e.isShiftDown()) {
                deg = Math.round(deg / 15.0) * 15.0;
            } else {
                deg = Math.round(deg * 10.0) / 10.0;
            }

            el.setRotation(deg);
            selBox.setRotate(deg);
            for (Node n : elementsPane.getChildren()) {
                if (n.getUserData() == el) {
                    n.setRotate(deg);
                    break;
                }
            }
            syncGeoSpinnersIfPresent();
            e.consume();
        });

        handleRotate.setOnMouseReleased(e -> {
            saveState();
            syncGeoSpinnersIfPresent();
            e.consume();
        });

        final double[] resizeStart = new double[6];

        // 1. SE Handle (Bottom-Right)
        handleSE.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX(); resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW(); resizeStart[3] = el.getH();
                e.consume();
            }
        });
        handleSE.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dx = (e.getScreenX() - resizeStart[0]) / zoom / MM_PX;
            double dy = (e.getScreenY() - resizeStart[1]) / zoom / MM_PX;
            double newW = Math.max(5.0, resizeStart[2] + dx);
            double newH = Math.max(3.0, resizeStart[3] + dy);
            if (snapToGrid) { newW = Math.round(newW); newH = Math.round(newH); }
            el.setW(newW); el.setH(newH);
            updateSelBoxGeometry(selBox, border, moveHitArea, handleNW, handleN, handleNE, handleE, handleSE, handleS, handleSW, handleW, rotateStem, handleRotate, newW * MM_PX, newH * MM_PX);
            updateLiveElementVisual(el, newW, newH, el.getX(), el.getY());
            e.consume();
        });
        handleSE.setOnMouseReleased(e -> { updateElementVisualInPlace(selectedElement); saveState(); syncGeoSpinnersIfPresent(); e.consume(); });

        // 2. E Handle (Right-Center)
        handleE.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX(); resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW(); resizeStart[3] = el.getH();
                e.consume();
            }
        });
        handleE.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dx = (e.getScreenX() - resizeStart[0]) / zoom / MM_PX;
            double newW = Math.max(5.0, resizeStart[2] + dx);
            if (snapToGrid) newW = Math.round(newW);
            el.setW(newW);
            updateSelBoxGeometry(selBox, border, moveHitArea, handleNW, handleN, handleNE, handleE, handleSE, handleS, handleSW, handleW, rotateStem, handleRotate, newW * MM_PX, el.getH() * MM_PX);
            updateLiveElementVisual(el, newW, el.getH(), el.getX(), el.getY());
            e.consume();
        });
        handleE.setOnMouseReleased(e -> { updateElementVisualInPlace(selectedElement); saveState(); syncGeoSpinnersIfPresent(); e.consume(); });

        // 3. S Handle (Bottom-Center)
        handleS.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX(); resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW(); resizeStart[3] = el.getH();
                e.consume();
            }
        });
        handleS.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dy = (e.getScreenY() - resizeStart[1]) / zoom / MM_PX;
            double newH = Math.max(3.0, resizeStart[3] + dy);
            if (snapToGrid) newH = Math.round(newH);
            el.setH(newH);
            updateSelBoxGeometry(selBox, border, moveHitArea, handleNW, handleN, handleNE, handleE, handleSE, handleS, handleSW, handleW, rotateStem, handleRotate, el.getW() * MM_PX, newH * MM_PX);
            updateLiveElementVisual(el, el.getW(), newH, el.getX(), el.getY());
            e.consume();
        });
        handleS.setOnMouseReleased(e -> { updateElementVisualInPlace(selectedElement); saveState(); syncGeoSpinnersIfPresent(); e.consume(); });

        // 4. W Handle (Left-Center with Left-Side Scaling)
        handleW.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX(); resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW(); resizeStart[3] = el.getH();
                resizeStart[4] = el.getX(); resizeStart[5] = el.getY();
                e.consume();
            }
        });
        handleW.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dx = (e.getScreenX() - resizeStart[0]) / zoom / MM_PX;
            double newW = Math.max(5.0, resizeStart[2] - dx);
            double newX = Math.max(0, resizeStart[4] + (resizeStart[2] - newW));
            if (snapToGrid) { newW = Math.round(newW); newX = Math.round(newX); }
            el.setW(newW); el.setX(newX);
            selBox.setLayoutX(newX * MM_PX);
            updateSelBoxGeometry(selBox, border, moveHitArea, handleNW, handleN, handleNE, handleE, handleSE, handleS, handleSW, handleW, rotateStem, handleRotate, newW * MM_PX, el.getH() * MM_PX);
            updateLiveElementVisual(el, newW, el.getH(), newX, el.getY());
            e.consume();
        });
        handleW.setOnMouseReleased(e -> { updateElementVisualInPlace(selectedElement); saveState(); syncGeoSpinnersIfPresent(); e.consume(); });

        // 5. N Handle (Top-Center)
        handleN.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX(); resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW(); resizeStart[3] = el.getH();
                resizeStart[4] = el.getX(); resizeStart[5] = el.getY();
                e.consume();
            }
        });
        handleN.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dy = (e.getScreenY() - resizeStart[1]) / zoom / MM_PX;
            double newH = Math.max(3.0, resizeStart[3] - dy);
            double newY = Math.max(0, resizeStart[5] + (resizeStart[3] - newH));
            if (snapToGrid) { newH = Math.round(newH); newY = Math.round(newY); }
            el.setH(newH); el.setY(newY);
            selBox.setLayoutY(newY * MM_PX);
            updateSelBoxGeometry(selBox, border, moveHitArea, handleNW, handleN, handleNE, handleE, handleSE, handleS, handleSW, handleW, rotateStem, handleRotate, el.getW() * MM_PX, newH * MM_PX);
            updateLiveElementVisual(el, el.getW(), newH, el.getX(), newY);
            e.consume();
        });
        handleN.setOnMouseReleased(e -> { updateElementVisualInPlace(selectedElement); saveState(); syncGeoSpinnersIfPresent(); e.consume(); });

        // 6. NW Handle (Top-Left)
        handleNW.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX(); resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW(); resizeStart[3] = el.getH();
                resizeStart[4] = el.getX(); resizeStart[5] = el.getY();
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
            if (snapToGrid) { newW = Math.round(newW); newH = Math.round(newH); newX = Math.round(newX); newY = Math.round(newY); }
            el.setW(newW); el.setH(newH); el.setX(newX); el.setY(newY);
            selBox.setLayoutX(newX * MM_PX); selBox.setLayoutY(newY * MM_PX);
            updateSelBoxGeometry(selBox, border, moveHitArea, handleNW, handleN, handleNE, handleE, handleSE, handleS, handleSW, handleW, rotateStem, handleRotate, newW * MM_PX, newH * MM_PX);
            updateLiveElementVisual(el, newW, newH, newX, newY);
            e.consume();
        });
        handleNW.setOnMouseReleased(e -> { updateElementVisualInPlace(selectedElement); saveState(); syncGeoSpinnersIfPresent(); e.consume(); });

        // 7. NE Handle (Top-Right)
        handleNE.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX(); resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW(); resizeStart[3] = el.getH();
                resizeStart[4] = el.getX(); resizeStart[5] = el.getY();
                e.consume();
            }
        });
        handleNE.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dx = (e.getScreenX() - resizeStart[0]) / zoom / MM_PX;
            double dy = (e.getScreenY() - resizeStart[1]) / zoom / MM_PX;
            double newW = Math.max(5.0, resizeStart[2] + dx);
            double newH = Math.max(3.0, resizeStart[3] - dy);
            double newY = Math.max(0, resizeStart[5] + (resizeStart[3] - newH));
            if (snapToGrid) { newW = Math.round(newW); newH = Math.round(newH); newY = Math.round(newY); }
            el.setW(newW); el.setH(newH); el.setY(newY);
            selBox.setLayoutY(newY * MM_PX);
            updateSelBoxGeometry(selBox, border, moveHitArea, handleNW, handleN, handleNE, handleE, handleSE, handleS, handleSW, handleW, rotateStem, handleRotate, newW * MM_PX, newH * MM_PX);
            updateLiveElementVisual(el, newW, newH, el.getX(), newY);
            e.consume();
        });
        handleNE.setOnMouseReleased(e -> { updateElementVisualInPlace(selectedElement); saveState(); syncGeoSpinnersIfPresent(); e.consume(); });

        // 8. SW Handle (Bottom-Left)
        handleSW.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                resizeStart[0] = e.getScreenX(); resizeStart[1] = e.getScreenY();
                resizeStart[2] = el.getW(); resizeStart[3] = el.getH();
                resizeStart[4] = el.getX(); resizeStart[5] = el.getY();
                e.consume();
            }
        });
        handleSW.setOnMouseDragged(e -> {
            if (el.isLocked()) return;
            double dx = (e.getScreenX() - resizeStart[0]) / zoom / MM_PX;
            double dy = (e.getScreenY() - resizeStart[1]) / zoom / MM_PX;
            double newW = Math.max(5.0, resizeStart[2] - dx);
            double newH = Math.max(3.0, resizeStart[3] + dy);
            double newX = Math.max(0, resizeStart[4] + (resizeStart[2] - newW));
            if (snapToGrid) { newW = Math.round(newW); newH = Math.round(newH); newX = Math.round(newX); }
            el.setW(newW); el.setH(newH); el.setX(newX);
            selBox.setLayoutX(newX * MM_PX);
            updateSelBoxGeometry(selBox, border, moveHitArea, handleNW, handleN, handleNE, handleE, handleSE, handleS, handleSW, handleW, rotateStem, handleRotate, newW * MM_PX, newH * MM_PX);
            updateLiveElementVisual(el, newW, newH, newX, el.getY());
            e.consume();
        });
        handleSW.setOnMouseReleased(e -> { updateElementVisualInPlace(selectedElement); saveState(); syncGeoSpinnersIfPresent(); e.consume(); });

        selBox.getChildren().addAll(moveHitArea, border, rotateStem, handleRotate, handleNW, handleN, handleNE, handleE, handleSE, handleS, handleSW, handleW);
        if (isPointEditable(el)) {
            addVertexAnchorHandles(selBox, el);
        }
        selectionPane.getChildren().add(selBox);
    }

    private Node renderVisualElement(TemplateElement el, RenderContext ctx, double w, double h) {
        if (el.getType() == ElementType.TABLE) {
            return renderTableVisual(el, w, h);
        }
        return com.invoicestudio.service.DesignObjectRenderer.render(el, ctx, w, h);
    }

    private Node renderTableVisual(TemplateElement el, double w, double h) {
        String bc = el.getTableBorderColor();
        double bwPx = Math.max(0.5, el.getTableBorderWidth() > 0 ? el.getTableBorderWidth() * MM_PX : 1.0);
        String bw = String.format(java.util.Locale.US, "%.2f", bwPx);
        String bStyle = el.getBorderStyle(); // grid, rows, outline, none
        boolean drawOuter = !"none".equals(bStyle);
        boolean innerLines = "grid".equals(bStyle) || "rows".equals(bStyle);

        VBox box = new VBox(0);
        box.setPrefSize(w, h);
        box.setMinSize(w, h);
        box.setMaxSize(w, h);
        if (drawOuter) {
            // Per-side outer border: hidden sides use transparent color
            box.setStyle("-fx-background-color: " + el.getRowBg() + "; -fx-border-color: "
                    + (el.isBorderTop() ? bc : "transparent") + " "
                    + (el.isBorderRight() ? bc : "transparent") + " "
                    + (el.isBorderBottom() ? bc : "transparent") + " "
                    + (el.isBorderLeft() ? bc : "transparent") + ";"
                    + " -fx-border-width: " + bw + ";");
        } else {
            box.setStyle("-fx-background-color: " + el.getRowBg() + ";");
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
        hRow.setMinHeight(headerPx);
        hRow.setMaxHeight(headerPx);
        // Header underline (grid / rows styles only)
        hRow.setStyle("-fx-background-color: " + hBg + ";"
                + (innerLines ? " -fx-border-color: transparent transparent " + bc + " transparent; -fx-border-width: 0 0 " + bw + " 0;" : ""));

        for (int ci = 0; ci < cols.size(); ci++) {
            TableColumn c = cols.get(ci);
            double cW = (c.getWidth() / 100.0) * w;
            boolean vSep = "grid".equals(bStyle) && ci < cols.size() - 1;
            Label lbl = new Label(c.getLabel());
            lbl.setPrefWidth(cW);
            lbl.setMinWidth(0);
            lbl.setMaxWidth(cW);
            lbl.setTextOverrun(OverrunStyle.ELLIPSIS);
            lbl.setEllipsisString("…");
            lbl.setPrefHeight(headerPx);
            lbl.setStyle("-fx-font-weight: bold; -fx-font-size: " + fontPx + "px; -fx-text-fill: " + hCol + "; -fx-padding: 0 4;"
                    + (vSep ? " -fx-border-color: transparent " + bc + " transparent transparent; -fx-border-width: 0 " + bw + " 0 0;" : ""));
            lbl.setAlignment("right".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER_RIGHT : ("center".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER : Pos.CENTER_LEFT));
            hRow.getChildren().add(lbl);
        }
        box.getChildren().add(hRow);

        int numRows = Math.max(1, Math.min(25, (int) Math.round((h - headerPx) / rowPx)));
        for (int r = 1; r <= numRows; r++) {
            HBox row = new HBox(0);
            row.setPrefHeight(rowPx);
            row.setMinHeight(rowPx);
            row.setMaxHeight(rowPx);
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
                lbl.setMinWidth(0);
                lbl.setMaxWidth(cW);
                lbl.setTextOverrun(OverrunStyle.ELLIPSIS);
                lbl.setEllipsisString("…");
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

    private void configureNumberSpinner(Spinner<Double> spinner) {
        spinner.setEditable(true);
        TextField editor = spinner.getEditor();
        editor.setStyle("-fx-alignment: center-left; -fx-padding: 0 4 0 6;");

        editor.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                javafx.application.Platform.runLater(editor::selectAll);
            } else {
                try {
                    String text = editor.getText();
                    if (text != null && !text.isBlank()) {
                        double val = Double.parseDouble(text.trim());
                        spinner.getValueFactory().setValue(val);
                    }
                } catch (Exception ignored) {
            AppLog.debug(ignored); }
            }
        });

        editor.setOnAction(e -> {
            try {
                String text = editor.getText();
                if (text != null && !text.isBlank()) {
                    double val = Double.parseDouble(text.trim());
                    spinner.getValueFactory().setValue(val);
                }
            } catch (Exception ignored) {
            AppLog.debug(ignored); }
        });
    }

    private Node createColorPickerButton(String currentHex, Consumer<String> onColorSelected) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);

        Region swatch = new Region();
        swatch.setPrefSize(20, 20);
        swatch.setMinSize(20, 20);
        swatch.setMaxSize(20, 20);
        String validHex = (currentHex != null && !currentHex.isBlank() && !"transparent".equalsIgnoreCase(currentHex)) ? currentHex : "#1A1A1A";
        swatch.setStyle(String.format("-fx-background-color: %s; -fx-border-color: #475569; -fx-border-width: 1.5; -fx-background-radius: 4; -fx-border-radius: 4;", validHex));

        Button btn = new Button(validHex.toUpperCase());
        btn.getStyleClass().addAll("button-sm", "custom-color-btn");
        btn.setOnAction(e -> {
            Window win = getScene() != null ? getScene().getWindow() : app.getPrimaryStage();
            CustomColorChooserDialog.show(win, "Choose Color", currentHex, newHex -> {
                btn.setText(newHex.toUpperCase());
                swatch.setStyle(String.format("-fx-background-color: %s; -fx-border-color: #475569; -fx-border-width: 1.5; -fx-background-radius: 4; -fx-border-radius: 4;", newHex));
                onColorSelected.accept(newHex);
            });
        });

        Button dropperBtn = new Button();
        dropperBtn.getStyleClass().addAll("button-sm", "button-icon-subtle");
        dropperBtn.setStyle("-fx-padding: 3 6; -fx-cursor: hand;");
        dropperBtn.setTooltip(new Tooltip("Pick Color from Screen / Image (Eyedropper)"));
        SVGPath dropIcon = new SVGPath();
        dropIcon.setContent(CustomColorChooserDialog.DROPPER_ICON_PATH);
        dropIcon.setFill(Color.web("#94A3B8"));
        dropIcon.setScaleX(0.65);
        dropIcon.setScaleY(0.65);
        dropperBtn.setGraphic(dropIcon);
        dropperBtn.setOnMouseEntered(ev -> dropIcon.setFill(Color.web("#F2CA6B")));
        dropperBtn.setOnMouseExited(ev -> dropIcon.setFill(Color.web("#94A3B8")));
        dropperBtn.setOnAction(e -> {
            Window win = getScene() != null ? getScene().getWindow() : app.getPrimaryStage();
            CustomColorChooserDialog.pickColorFromScreen(win, pickedColor -> {
                String hex = CustomColorChooserDialog.colorToHex(pickedColor);
                btn.setText(hex.toUpperCase());
                swatch.setStyle(String.format("-fx-background-color: %s; -fx-border-color: #475569; -fx-border-width: 1.5; -fx-background-radius: 4; -fx-border-radius: 4;", hex));
                onColorSelected.accept(hex);
            });
        });

        box.getChildren().addAll(swatch, btn, dropperBtn);
        return box;
    }

    private void updatePropertiesPanel() {
        updatingProperties = true;
        try {
            propBuffer.clear();
            geoXSpin = null;
            geoYSpin = null;
            geoWSpin = null;
            geoHSpin = null;
            geoRotSpin = null;
            geoUnitBox = null;

            if (selectedElement == null) {
                updateStatusBarCoords();
                buildPageAndMarginProperties();
                propBox.getChildren().setAll(propBuffer);
                return;
            }
            updateStatusBarCoords();

            TemplateElement el = selectedElement;

            // Header Actions Row
            HBox headerRow = new HBox(8);
            headerRow.setAlignment(Pos.CENTER_LEFT);

            Label typeLbl = new Label(el.getType().name());
            typeLbl.getStyleClass().add("element-type-badge");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Button saveCompBtn = createToolbarBtn("📦", "Save selected element or group as custom reusable component", this::saveSelectionAsComponent);
            Button dupBtn = createToolbarBtn("❐", "Duplicate Element (Ctrl D)", this::duplicateSelected);
            Button upBtn = createToolbarBtn("▲", "Bring Forward", () -> moveLayer(1));
            Button downBtn = createToolbarBtn("▼", "Send Backward", () -> moveLayer(-1));
            Button delBtn = createToolbarBtn("🗑", "Delete Element (Delete key)", this::deleteSelected);

            headerRow.getChildren().addAll(typeLbl, sp, saveCompBtn, dupBtn, upBtn, downBtn, delBtn);

            // Object Name Row
            HBox nameRow = new HBox(8);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            Label namePrompt = new Label("Name:");
            namePrompt.getStyleClass().add("cell-bold-secondary");
            TextField objNameField = new TextField(el.getName() != null ? el.getName() : "");
            objNameField.setPromptText(el.getDisplayName());
            objNameField.setTooltip(new Tooltip("Friendly name for this element in layers panel and inspector"));
            HBox.setHgrow(objNameField, Priority.ALWAYS);
            objNameField.textProperty().addListener((obs, o, v) -> {
                el.setName(v);
                refreshLayersList();
            });
            nameRow.getChildren().addAll(namePrompt, objNameField);

            addPropertyNodes(headerRow, nameRow);

            // Position & Size Grid
            TitledPane geoPane = new TitledPane();
            geoPane.setText("Position & Size");
            geoPane.setExpanded(true);

            GridPane posGrid = new GridPane();
            posGrid.setHgap(8); posGrid.setVgap(8);
            posGrid.setPadding(new Insets(8));

            Label unitLbl = new Label("Unit:");
            unitLbl.getStyleClass().add("cell-bold-secondary");
            ComboBox<UnitConverter.Unit> unitBox = new ComboBox<>(FXCollections.observableArrayList(UnitConverter.Unit.values()));
            unitBox.setValue(UnitConverter.Unit.MM);
            unitBox.getStyleClass().add("designer-combo");
            unitBox.setTooltip(new Tooltip("Measurement unit: Millimeters (mm), Points (pt), Centimeters (cm), Inches (in)"));
            HBox unitRow = new HBox(6, unitLbl, unitBox);
            unitRow.setAlignment(Pos.CENTER_LEFT);
            posGrid.add(unitRow, 0, 0, 4, 1);

            Label xLbl = new Label("X:");
            Label yLbl = new Label("Y:");
            Label wLbl = new Label("W:");
            Label hLbl = new Label("H:");

            Spinner<Double> xSpin = new Spinner<>(0.0, 5000.0, el.getX(), 1.0);
            xSpin.setPrefWidth(85);
            xSpin.setTooltip(new Tooltip("Horizontal position (X) from the left edge of the page"));
            configureNumberSpinner(xSpin);

            Spinner<Double> ySpin = new Spinner<>(0.0, 5000.0, el.getY(), 1.0);
            ySpin.setPrefWidth(85);
            ySpin.setTooltip(new Tooltip("Vertical position (Y) from the top edge of the page"));
            configureNumberSpinner(ySpin);

            Spinner<Double> wSpin = new Spinner<>(0.1, 5000.0, el.getW(), 1.0);
            wSpin.setPrefWidth(85);
            wSpin.setTooltip(new Tooltip("Element width in current measurement units"));
            configureNumberSpinner(wSpin);

            Spinner<Double> hSpin = new Spinner<>(0.1, 5000.0, el.getH(), 1.0);
            hSpin.setPrefWidth(85);
            hSpin.setTooltip(new Tooltip("Element height in current measurement units"));
            configureNumberSpinner(hSpin);

            geoXSpin = xSpin;
            geoYSpin = ySpin;
            geoWSpin = wSpin;
            geoHSpin = hSpin;
            geoUnitBox = unitBox;

            final boolean[] updatingGeo = {false};
            Consumer<UnitConverter.Unit> syncGeoSpinners = (u) -> {
                updatingGeo[0] = true;
                double xVal = UnitConverter.fromMm(el.getX(), u);
                double yVal = UnitConverter.fromMm(el.getY(), u);
                double wVal = UnitConverter.fromMm(el.getW(), u);
                double hVal = UnitConverter.fromMm(el.getH(), u);
                double step = (u == UnitConverter.Unit.IN || u == UnitConverter.Unit.INCH) ? 0.1 : (u == UnitConverter.Unit.CM ? 0.5 : 1.0);
                xSpin.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 5000.0, Math.round(xVal * 100.0) / 100.0, step));
                ySpin.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 5000.0, Math.round(yVal * 100.0) / 100.0, step));
                wSpin.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.1, 5000.0, Math.round(wVal * 100.0) / 100.0, step));
                hSpin.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.1, 5000.0, Math.round(hVal * 100.0) / 100.0, step));
                configureNumberSpinner(xSpin);
                configureNumberSpinner(ySpin);
                configureNumberSpinner(wSpin);
                configureNumberSpinner(hSpin);
                updatingGeo[0] = false;
            };

            xSpin.valueProperty().addListener((obs, o, v) -> {
                if (!updatingProperties && !updatingGeo[0] && v != null) {
                    el.setX(UnitConverter.toMm(v, unitBox.getValue()));
                    updateElementVisualInPlace(el);
                    updateSelectionOverlay();
                }
            });
            ySpin.valueProperty().addListener((obs, o, v) -> {
                if (!updatingProperties && !updatingGeo[0] && v != null) {
                    el.setY(UnitConverter.toMm(v, unitBox.getValue()));
                    updateElementVisualInPlace(el);
                    updateSelectionOverlay();
                }
            });
            wSpin.valueProperty().addListener((obs, o, v) -> {
                if (!updatingProperties && !updatingGeo[0] && v != null) {
                    el.setW(UnitConverter.toMm(v, unitBox.getValue()));
                    updateElementVisualInPlace(el);
                    updateSelectionOverlay();
                }
            });
            hSpin.valueProperty().addListener((obs, o, v) -> {
                if (!updatingProperties && !updatingGeo[0] && v != null) {
                    el.setH(UnitConverter.toMm(v, unitBox.getValue()));
                    updateElementVisualInPlace(el);
                    updateSelectionOverlay();
                }
            });

            unitBox.valueProperty().addListener((obs, o, v) -> {
                if (v != null) {
                    syncGeoSpinners.accept(v);
                }
            });

            posGrid.add(xLbl, 0, 1);
            posGrid.add(xSpin, 1, 1);
            posGrid.add(yLbl, 2, 1);
            posGrid.add(ySpin, 3, 1);
            posGrid.add(wLbl, 0, 2);
            posGrid.add(wSpin, 1, 2);
            posGrid.add(hLbl, 2, 2);
            posGrid.add(hSpin, 3, 2);

            geoPane.setContent(posGrid);
            addPropertyNode(geoPane);

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
            } else if (isShapeType(el.getType())) {
                buildShapeProperties(el);
            } else if (el.getType() == ElementType.SVG) {
                buildSvgProperties(el);
            } else if (el.getType() == ElementType.ICON) {
                buildIconProperties(el);
            } else if (el.getType() == ElementType.WATERMARK) {
                buildWatermarkProperties(el);
            }

            // Universal Effects, Transforms, and Data Binding
            buildEffectsAndTransformsProperties(el);
            buildDataBindingProperties(el);

            // Behavior Toggles Box
            VBox toggles = new VBox(8);
            toggles.setPadding(new Insets(8));
            toggles.getStyleClass().add("toggles-box");

            CheckBox repeatCb = new CheckBox("Repeat on multi-page bills");
            repeatCb.setSelected(el.isRepeatOnPages());
            repeatCb.setTooltip(new Tooltip("Print this element on every subsequent page of multi-page invoices"));
            repeatCb.selectedProperty().addListener((obs, o, v) -> el.setRepeatOnPages(v));

            CheckBox blankCb = new CheckBox("Hide when blank / empty");
            blankCb.setSelected(el.isHideWhenBlank());
            blankCb.setTooltip(new Tooltip("Automatically hide this element if its variable or text evaluates to empty"));
            blankCb.selectedProperty().addListener((obs, o, v) -> el.setHideWhenBlank(v));

            CheckBox lockCb = new CheckBox("Lock position on canvas");
            lockCb.setSelected(el.isLocked());
            lockCb.setTooltip(new Tooltip("Lock element position and dimensions to prevent accidental dragging on canvas"));
            lockCb.selectedProperty().addListener((obs, o, v) -> el.setLocked(v));

            toggles.getChildren().addAll(repeatCb, blankCb, lockCb);
            addPropertyNode(toggles);
            propBox.getChildren().setAll(propBuffer);
        } finally {
            updatingProperties = false;
        }
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

        // 1. Direct Dropdown & + Add Button directly in property view.
        //    Items are GROUPED by category (Invoice, Buyer, Totals, ...) with
        //    disabled section-header rows — headers are styled via .var-group-header
        //    and cannot be selected with the mouse; the + Add guard also skips
        //    them for keyboard navigation.
        ComboBox<VariableGrouper.Row> varCombo = new ComboBox<>();
        varCombo.setPromptText("Choose variable to add...");
        varCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(varCombo, Priority.ALWAYS);
        List<VariableGrouper.Row> groupedRows = VariableGrouper.group(getComprehensiveVariablesList());
        varCombo.setItems(FXCollections.observableArrayList(groupedRows));
        varCombo.getStyleClass().add("designer-combo");
        varCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(VariableGrouper.Row item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("var-group-header");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setDisable(false);
                    return;
                }
                if (item.isHeader()) {
                    getStyleClass().add("var-group-header");
                    setDisable(true); // ListView ignores clicks on disabled cells
                    setText(item.header());
                    setGraphic(null);
                } else {
                    setDisable(false);
                    setText(item.var().getLabel() + "  {{" + item.var().getKey() + "}}");
                    setGraphic(null);
                }
            }
        });
        varCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(VariableGrouper.Row item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("var-group-header");
                if (empty || item == null || item.isHeader()) {
                    setText("Choose variable to add...");
                    setGraphic(null);
                    setDisable(false);
                } else {
                    setDisable(false);
                    setText(item.var().getLabel() + "  {{" + item.var().getKey() + "}}");
                    setGraphic(null);
                }
            }
        });

        Button dropInsertBtn = new Button("+ Add");
        dropInsertBtn.getStyleClass().add("btn-gold-sm");
        dropInsertBtn.setMinWidth(Region.USE_PREF_SIZE); // never compress to "..." in narrow panels
        dropInsertBtn.setTooltip(new Tooltip("Add chosen variable into text at cursor position"));
        dropInsertBtn.setOnAction(e -> {
            VariableGrouper.Row sel = varCombo.getValue();
            if (sel != null && !sel.isHeader() && sel.var() != null && sel.var().getKey() != null) {
                insertVariableIntoTarget(ta, "{{" + sel.var().getKey() + "}}", el);
                varCombo.getSelectionModel().clearSelection(); // ready for the next pick
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
        varPickerBtn.setTooltip(new Tooltip("Open searchable popup with all available placeholders & custom buyer fields, grouped by category"));
        varPickerBtn.setOnAction(e -> showVariablePicker(ta, el));

        varSec.getChildren().addAll(varSecLbl, dropRow, varPickerBtn);

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
        fontCombo.setTooltip(new Tooltip("Font typeface family"));
        fontCombo.valueProperty().addListener((obs, o, v) -> { el.setFontFamily(v); refreshCanvas(); });

        Spinner<Double> fontSpin = new Spinner<>(5.0, 72.0, el.getFontSize(), 0.5);
        fontSpin.setTooltip(new Tooltip("Font size in points (pt)"));
        configureNumberSpinner(fontSpin);
        fontSpin.valueProperty().addListener((obs, o, v) -> { el.setFontSize(v); refreshCanvas(); });

        fontRow.getChildren().addAll(new Label("Font:"), fontCombo, new Label("Size:"), fontSpin);

        // Styling Buttons: Bold, Italic, Underline, Strikethrough, Weight, Alignment
        HBox styleRow = new HBox(6);
        styleRow.setAlignment(Pos.CENTER_LEFT);

        ToggleButton boldBtn = new ToggleButton("B");
        boldBtn.setSelected(el.getFontWeight() >= 700 || el.isBold());
        boldBtn.getStyleClass().add("text-bold");
        boldBtn.setTooltip(new Tooltip("Bold font weight (700)"));

        ToggleButton italicBtn = new ToggleButton("I");
        italicBtn.setSelected(el.isItalic());
        italicBtn.getStyleClass().add("text-italic");
        italicBtn.setTooltip(new Tooltip("Italic font style"));
        italicBtn.setOnAction(e -> { el.setItalic(italicBtn.isSelected()); refreshCanvas(); });

        ToggleButton underlineBtn = new ToggleButton("U");
        underlineBtn.setSelected(el.isUnderline());
        underlineBtn.setStyle("-fx-underline: true; -fx-font-weight: bold;");
        underlineBtn.setTooltip(new Tooltip("Underline text"));
        underlineBtn.setOnAction(e -> { el.setUnderline(underlineBtn.isSelected()); refreshCanvas(); });

        ToggleButton strikeBtn = new ToggleButton("S");
        strikeBtn.setSelected(el.isStrikethrough());
        strikeBtn.setStyle("-fx-strikethrough: true; -fx-font-weight: bold;");
        strikeBtn.setTooltip(new Tooltip("Strikethrough text"));
        strikeBtn.setOnAction(e -> { el.setStrikethrough(strikeBtn.isSelected()); refreshCanvas(); });

        String[] weights = {"100 - Thin", "200 - Extra Light", "300 - Light", "400 - Regular", "500 - Medium", "600 - Semi Bold", "700 - Bold", "800 - Extra Bold", "900 - Black"};
        ComboBox<String> weightCombo = new ComboBox<>(FXCollections.observableArrayList(weights));
        int curW = el.getFontWeight() > 0 ? el.getFontWeight() : (el.isBold() ? 700 : 400);
        int wIdx = Math.max(0, Math.min(8, (curW / 100) - 1));
        weightCombo.setValue(weights[wIdx]);
        weightCombo.setPrefWidth(125);
        weightCombo.setTooltip(new Tooltip("Typographic font weight (100–900)"));

        weightCombo.valueProperty().addListener((obs, o, v) -> {
            if (v != null && v.length() >= 3) {
                try {
                    int num = Integer.parseInt(v.substring(0, 3));
                    el.setFontWeight(num);
                    el.setBold(num >= 700);
                    boldBtn.setSelected(num >= 700);
                    refreshCanvas();
                } catch (Exception ignored) {
            AppLog.debug(ignored); }
            }
        });

        boldBtn.setOnAction(e -> {
            int newW = boldBtn.isSelected() ? 700 : 400;
            el.setFontWeight(newW);
            el.setBold(boldBtn.isSelected());
            weightCombo.setValue(weights[Math.max(0, (newW / 100) - 1)]);
            refreshCanvas();
        });

        Button alignL = createToolbarBtn("⯇", "Align Left", () -> { el.setAlign("left"); refreshCanvas(); });
        Button alignC = createToolbarBtn("☰", "Align Center", () -> { el.setAlign("center"); refreshCanvas(); });
        Button alignR = createToolbarBtn("⯈", "Align Right", () -> { el.setAlign("right"); refreshCanvas(); });

        styleRow.getChildren().addAll(boldBtn, italicBtn, underlineBtn, strikeBtn, weightCombo, new Separator(javafx.geometry.Orientation.VERTICAL), alignL, alignC, alignR);

        // Color controls with quick palette chips
        GridPane colorGrid = new GridPane();
        colorGrid.setHgap(8); colorGrid.setVgap(8);

        Node textColControl = createColorPickerButton(el.getColor() != null ? el.getColor() : "#1a1a1a", hex -> {
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
                refreshCanvas();
                updatePropertiesPanel();
            });
            colorPresetRow.getChildren().add(chip);
        }
        HBox textColBox = new HBox(8, textColControl, colorPresetRow);
        textColBox.setAlignment(Pos.CENTER_LEFT);

        colorGrid.add(new Label("Text Color:"), 0, 0);
        colorGrid.add(textColBox, 1, 0);

        Node bgColControl = createColorPickerButton(el.getBg() != null ? el.getBg() : "#ffffff", hex -> {
            el.setBg(hex);
            refreshCanvas();
        });

        CheckBox bgTransCb = new CheckBox("Transparent");
        bgTransCb.setSelected(el.getBg() == null || "transparent".equalsIgnoreCase(el.getBg()));
        bgTransCb.setTooltip(new Tooltip("Make text box background transparent"));
        bgTransCb.setOnAction(e -> {
            if (bgTransCb.isSelected()) {
                el.setBg("transparent");
            } else {
                el.setBg("#ffffff");
            }
            refreshCanvas();
        });

        colorGrid.add(new Label("Background:"), 0, 1);
        colorGrid.add(new HBox(6, bgColControl, bgTransCb), 1, 1);

        // Advanced Typography & Spacing
        TitledPane spacingPane = new TitledPane();
        spacingPane.setText("Typography & Spacing");
        spacingPane.setExpanded(true);

        GridPane spacingGrid = new GridPane();
        spacingGrid.setHgap(8); spacingGrid.setVgap(8);
        spacingGrid.setPadding(new Insets(8));

        double curLh = el.getLineHeight() > 0 ? el.getLineHeight() : 1.25;
        Spinner<Double> lineHeightSpin = new Spinner<>(0.8, 3.5, curLh, 0.05);
        lineHeightSpin.setPrefWidth(85);
        configureNumberSpinner(lineHeightSpin);
        lineHeightSpin.setTooltip(new Tooltip("Line height multiplier (e.g. 1.0x, 1.25x, 1.5x) - scales automatically with font size"));

        ComboBox<String> lhPresetCombo = new ComboBox<>(FXCollections.observableArrayList(
            "Auto (1.2x)", "Tight (1.0x)", "Normal (1.25x)", "Relaxed (1.5x)", "Double (2.0x)", "Custom"
        ));
        if (Math.abs(curLh - 1.2) < 0.02) lhPresetCombo.setValue("Auto (1.2x)");
        else if (Math.abs(curLh - 1.0) < 0.02) lhPresetCombo.setValue("Tight (1.0x)");
        else if (Math.abs(curLh - 1.25) < 0.02) lhPresetCombo.setValue("Normal (1.25x)");
        else if (Math.abs(curLh - 1.5) < 0.02) lhPresetCombo.setValue("Relaxed (1.5x)");
        else if (Math.abs(curLh - 2.0) < 0.02) lhPresetCombo.setValue("Double (2.0x)");
        else lhPresetCombo.setValue("Custom");
        lhPresetCombo.setPrefWidth(115);
        lhPresetCombo.getStyleClass().add("designer-combo");

        lhPresetCombo.valueProperty().addListener((obs, o, v) -> {
            if ("Auto (1.2x)".equals(v)) lineHeightSpin.getValueFactory().setValue(1.2);
            else if ("Tight (1.0x)".equals(v)) lineHeightSpin.getValueFactory().setValue(1.0);
            else if ("Normal (1.25x)".equals(v)) lineHeightSpin.getValueFactory().setValue(1.25);
            else if ("Relaxed (1.5x)".equals(v)) lineHeightSpin.getValueFactory().setValue(1.5);
            else if ("Double (2.0x)".equals(v)) lineHeightSpin.getValueFactory().setValue(2.0);
        });

        lineHeightSpin.valueProperty().addListener((obs, o, v) -> {
            el.setLineHeight(v);
            refreshCanvas();
        });

        HBox lhBox = new HBox(6, lhPresetCombo, lineHeightSpin);
        lhBox.setAlignment(Pos.CENTER_LEFT);

        Spinner<Double> letterSpacingSpin = new Spinner<>(-2.0, 25.0, el.getLetterSpacing(), 0.5);
        letterSpacingSpin.setPrefWidth(85);
        configureNumberSpinner(letterSpacingSpin);
        letterSpacingSpin.setTooltip(new Tooltip("Letter / character tracking spacing in points (pt)"));
        letterSpacingSpin.valueProperty().addListener((obs, o, v) -> { el.setLetterSpacing(v); refreshCanvas(); });

        Spinner<Double> wordSpacingSpin = new Spinner<>(0.0, 30.0, el.getWordSpacing(), 1.0);
        wordSpacingSpin.setPrefWidth(85);
        configureNumberSpinner(wordSpacingSpin);
        wordSpacingSpin.setTooltip(new Tooltip("Spacing between words in points (pt)"));
        wordSpacingSpin.valueProperty().addListener((obs, o, v) -> { el.setWordSpacing(v); refreshCanvas(); });

        ComboBox<String> transformCombo = new ComboBox<>(FXCollections.observableArrayList("None", "UPPERCASE", "lowercase", "Capitalize"));
        transformCombo.setValue(el.getTextTransform() != null && !el.getTextTransform().isBlank() ? el.getTextTransform() : "None");
        transformCombo.getStyleClass().add("designer-combo");
        transformCombo.setTooltip(new Tooltip("Text capitalization casing: None (original), UPPERCASE, lowercase, or Capitalize First Letters"));
        transformCombo.valueProperty().addListener((obs, o, v) -> {
            el.setTextTransform("None".equalsIgnoreCase(v) ? null : v);
            refreshCanvas();
        });

        spacingGrid.add(new Label("Line Height:"), 0, 0);
        spacingGrid.add(lhBox, 1, 0, 3, 1);

        spacingGrid.add(new Label("Letter Spacing:"), 0, 1);
        spacingGrid.add(letterSpacingSpin, 1, 1);
        spacingGrid.add(new Label("Word Spacing:"), 2, 1);
        spacingGrid.add(wordSpacingSpin, 3, 1);

        spacingGrid.add(new Label("Text Case:"), 0, 2);
        spacingGrid.add(transformCombo, 1, 2);

        spacingPane.setContent(spacingGrid);

        sec.getChildren().addAll(textLbl, ta, varSec, fontRow, styleRow, colorGrid, spacingPane);
        addPropertyNode(sec);
    }

    private void buildRectProperties(TemplateElement el) {
        VBox sec = new VBox(10);
        Label title = new Label("Shape Properties:");
        title.getStyleClass().add("prop-title");

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);

        Node fillControl = createColorPickerButton(el.getBg() != null ? el.getBg() : "#f4f1ea", hex -> {
            el.setBg(hex);
            refreshCanvas();
        });

        CheckBox transCb = new CheckBox("Transparent");
        transCb.setSelected(el.getBg() == null || "transparent".equalsIgnoreCase(el.getBg()));
        transCb.setOnAction(e -> {
            if (transCb.isSelected()) el.setBg("transparent");
            else el.setBg("#f4f1ea");
            refreshCanvas();
        });

        grid.add(new Label("Fill Color:"), 0, 0);
        grid.add(new HBox(8, fillControl, transCb), 1, 0);

        // Individual borders toggle
        CheckBox indCb = new CheckBox("Individual Border Controls (Per-Side)");
        indCb.setSelected(el.isIndividualBorders());
        indCb.selectedProperty().addListener((obs, o, v) -> {
            el.setIndividualBorders(v);
            updatePropertiesPanel();
            refreshCanvas();
        });

        grid.add(indCb, 0, 1, 2, 1);

        if (!el.isIndividualBorders()) {
            // Standard unified border
            Node borderColControl = createColorPickerButton(el.getBorderColor() != null ? el.getBorderColor() : "#1a1a1a", hex -> {
                el.setBorderColor(hex);
                refreshCanvas();
            });
            grid.add(new Label("Border Color:"), 0, 2);
            grid.add(borderColControl, 1, 2);

            Spinner<Double> borderW = new Spinner<>(0.0, 10.0, el.getBorderWidth(), 0.5);
            configureNumberSpinner(borderW);
            borderW.valueProperty().addListener((obs, o, v) -> { el.setBorderWidth(v); refreshCanvas(); });
            grid.add(new Label("Border (mm):"), 0, 3);
            grid.add(borderW, 1, 3);

            Spinner<Double> borderR = new Spinner<>(0.0, 50.0, el.getBorderRadius(), 1.0);
            configureNumberSpinner(borderR);
            borderR.valueProperty().addListener((obs, o, v) -> { el.setBorderRadius(v); refreshCanvas(); });
            grid.add(new Label("Corner Radius:"), 0, 4);
            grid.add(borderR, 1, 4);
        } else {
            // Per-side controls
            VBox sidesBox = new VBox(8);
            sidesBox.setStyle("-fx-background-color: #12161D; -fx-padding: 8; -fx-border-color: #232B38; -fx-border-radius: 4;");

            String[] sideKeys = {"top", "right", "bottom", "left"};
            String[] sideLabels = {"Top Border", "Right Border", "Bottom Border", "Left Border"};

            for (int i = 0; i < 4; i++) {
                String side = sideKeys[i];
                String sLabel = sideLabels[i];

                TitledPane sidePane = new TitledPane();
                sidePane.setText(sLabel);
                sidePane.setExpanded(el.isSideActive(side));

                GridPane sGrid = new GridPane();
                sGrid.setHgap(8); sGrid.setVgap(6);
                sGrid.setPadding(new Insets(6));

                CheckBox activeCb = new CheckBox("Active");
                activeCb.setSelected(el.isSideActive(side));
                activeCb.selectedProperty().addListener((obs, o, v) -> {
                    switch (side) {
                        case "top" -> el.setBorderTopActive(v);
                        case "right" -> el.setBorderRightActive(v);
                        case "bottom" -> el.setBorderBottomActive(v);
                        case "left" -> el.setBorderLeftActive(v);
                    }
                    refreshCanvas();
                });
                sGrid.add(activeCb, 0, 0, 2, 1);

                Spinner<Double> wSpin = new Spinner<>(0.2, 10.0, el.getEffectiveSideWidth(side), 0.5);
                configureNumberSpinner(wSpin);
                wSpin.valueProperty().addListener((obs, o, v) -> {
                    switch (side) {
                        case "top" -> el.setBorderTopWidth(v);
                        case "right" -> el.setBorderRightWidth(v);
                        case "bottom" -> el.setBorderBottomWidth(v);
                        case "left" -> el.setBorderLeftWidth(v);
                    }
                    refreshCanvas();
                });
                sGrid.add(new Label("Width:"), 0, 1);
                sGrid.add(wSpin, 1, 1);

                Node colBtn = createColorPickerButton(el.getEffectiveSideColor(side), hex -> {
                    switch (side) {
                        case "top" -> el.setBorderTopColor(hex);
                        case "right" -> el.setBorderRightColor(hex);
                        case "bottom" -> el.setBorderBottomColor(hex);
                        case "left" -> el.setBorderLeftColor(hex);
                    }
                    refreshCanvas();
                });
                sGrid.add(new Label("Color:"), 0, 2);
                sGrid.add(colBtn, 1, 2);

                ComboBox<String> styleCombo = new ComboBox<>(FXCollections.observableArrayList("solid", "dashed", "dotted"));
                styleCombo.setValue(el.getEffectiveSideStyle(side));
                styleCombo.valueProperty().addListener((obs, o, v) -> {
                    switch (side) {
                        case "top" -> el.setBorderTopStyle(v);
                        case "right" -> el.setBorderRightStyle(v);
                        case "bottom" -> el.setBorderBottomStyle(v);
                        case "left" -> el.setBorderLeftStyle(v);
                    }
                    refreshCanvas();
                });
                sGrid.add(new Label("Style:"), 0, 3);
                sGrid.add(styleCombo, 1, 3);

                sidePane.setContent(sGrid);
                sidesBox.getChildren().add(sidePane);
            }

            Spinner<Double> borderR = new Spinner<>(0.0, 50.0, el.getBorderRadius(), 1.0);
            configureNumberSpinner(borderR);
            borderR.valueProperty().addListener((obs, o, v) -> { el.setBorderRadius(v); refreshCanvas(); });
            HBox rBox = new HBox(8, new Label("Corner Radius:"), borderR);
            rBox.setAlignment(Pos.CENTER_LEFT);

            sec.getChildren().addAll(title, grid, sidesBox, rBox);
            addPropertyNode(sec);
            return;
        }

        sec.getChildren().addAll(title, grid);
        addPropertyNode(sec);
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

        Node colControl = createColorPickerButton(el.getBorderColor() != null ? el.getBorderColor() : "#1a1a1a", hex -> {
            el.setBorderColor(hex);
            refreshCanvas();
        });
        grid.add(new Label("Line Color:"), 0, 1);
        grid.add(colControl, 1, 1);

        Spinner<Double> thickSpin = new Spinner<>(0.2, 10.0, Math.max(0.5, el.getBorderWidth()), 0.5);
        configureNumberSpinner(thickSpin);
        thickSpin.valueProperty().addListener((obs, o, v) -> { el.setBorderWidth(v); refreshCanvas(); });
        grid.add(new Label("Thickness (mm):"), 0, 2);
        grid.add(thickSpin, 1, 2);

        sec.getChildren().addAll(title, grid);
        addPropertyNode(sec);
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
                } catch (Exception ignored) {
            AppLog.debug(ignored); }
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
        addPropertyNode(sec);
    }

    private Image decodeFxImage(String src) {
        return VectorGeometryUtil.decodeFxImage(src);
    }

    private String loadResourceAsBase64(String resourcePath) {
        return VectorGeometryUtil.loadResourceAsBase64(resourcePath);
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
        addPropertyNode(sec);
    }

    private void buildBarcodeProperties(TemplateElement el) {
        VBox sec = new VBox(8);
        Label title = new Label("Barcode Properties:");
        title.getStyleClass().add("prop-title");

        TextField tf = new TextField(el.getBarcodeData());
        tf.setPromptText("e.g. {{invoice_no}} or {{barcode}}");
        tf.textProperty().addListener((obs, o, v) -> { el.setBarcodeData(v); refreshCanvas(); });

        // Symbology — CODE_128 default keeps every pre-existing template identical.
        ComboBox<String> fmtCb = new ComboBox<>(FXCollections.observableArrayList(BarcodeService.FORMATS));
        fmtCb.setValue(el.getBarcodeFormat());
        fmtCb.setTooltip(new Tooltip("EAN_13/UPC_A need 13/12 digits, ITF needs an even digit count — otherwise Code 128 is used as a safe fallback"));
        fmtCb.valueProperty().addListener((obs, o, v) -> { if (v != null) { el.setBarcodeFormat(v); refreshCanvas(); } });

        CheckBox textCb = new CheckBox("Show text below barcode lines");
        textCb.setSelected(el.isBarcodeShowText());
        textCb.setOnAction(e -> { el.setBarcodeShowText(textCb.isSelected()); refreshCanvas(); });

        sec.getChildren().addAll(title, new Label("Payload:"), tf, new Label("Symbology:"), fmtCb, textCb);
        addPropertyNode(sec);
    }

    private void buildTableProperties(TemplateElement el) {
        VBox sec = new VBox(10);
        Label title = new Label("Itemized Table Settings & Colors:");
        title.getStyleClass().add("prop-title");

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);

        Node hBgControl = createColorPickerButton(el.getHeaderBg() != null ? el.getHeaderBg() : "#efe9db", hex -> {
            el.setHeaderBg(hex);
            updateElementVisualInPlace(el);
        });
        grid.add(new Label("Header BG:"), 0, 0);
        grid.add(hBgControl, 1, 0);

        Node hTextControl = createColorPickerButton(el.getHeaderColor() != null ? el.getHeaderColor() : "#1a1a1a", hex -> {
            el.setHeaderColor(hex);
            updateElementVisualInPlace(el);
        });
        grid.add(new Label("Header Text:"), 0, 1);
        grid.add(hTextControl, 1, 1);

        Spinner<Double> rowH = new Spinner<>(4.0, 25.0, el.getRowHeight() > 0 ? el.getRowHeight() : 7.0, 0.5);
        configureNumberSpinner(rowH);
        rowH.valueProperty().addListener((obs, o, v) -> {
            if (updatingProperties || v == null || Objects.equals(o, v)) return;
            el.setRowHeight(v);
            updateElementVisualInPlace(el);
            syncGeoSpinnersIfPresent();
            updateSelectionOverlay();
        });
        grid.add(new Label("Row Height (mm):"), 0, 2);
        grid.add(rowH, 1, 2);

        Spinner<Double> fontS = new Spinner<>(5.0, 20.0, el.getFontSize() > 0 ? el.getFontSize() : 8.5, 0.5);
        configureNumberSpinner(fontS);
        fontS.valueProperty().addListener((obs, o, v) -> {
            if (updatingProperties || v == null || Objects.equals(o, v)) return;
            el.setFontSize(v);
            updateElementVisualInPlace(el);
        });
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
            if (updatingProperties || v == null || Objects.equals(o, v)) return;
            el.setBorderStyle(switch (v) {
                case "Rows Only" -> "rows";
                case "Outline" -> "outline";
                case "None" -> "none";
                default -> "grid";
            });
            updateElementVisualInPlace(el);
        });
        grid.add(new Label("Border Type:"), 0, 4);
        grid.add(borderTypeCb, 1, 4);

        Node bColorControl = createColorPickerButton(el.getTableBorderColor() != null ? el.getTableBorderColor() : "#c8c8c8", hex -> {
            el.setTableBorderColor(hex);
            updateElementVisualInPlace(el);
        });
        grid.add(new Label("Border Color:"), 0, 5);
        grid.add(bColorControl, 1, 5);

        Spinner<Double> bWidth = new Spinner<>(0.0, 1.5, el.getTableBorderWidth() > 0 ? el.getTableBorderWidth() : 0.26, 0.05);
        configureNumberSpinner(bWidth);
        bWidth.setTooltip(new Tooltip("Border thickness in mm (~0.26mm = 1px on screen)"));
        bWidth.valueProperty().addListener((obs, o, v) -> {
            if (updatingProperties || v == null || Objects.equals(o, v)) return;
            el.setTableBorderWidth(v);
            updateElementVisualInPlace(el);
        });
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
        topCb.setOnAction(e -> { el.setBorderTop(topCb.isSelected()); updateElementVisualInPlace(el); });
        bottomCb.setOnAction(e -> { el.setBorderBottom(bottomCb.isSelected()); updateElementVisualInPlace(el); });
        leftCb.setOnAction(e -> { el.setBorderLeft(leftCb.isSelected()); updateElementVisualInPlace(el); });
        rightCb.setOnAction(e -> { el.setBorderRight(rightCb.isSelected()); updateElementVisualInPlace(el); });
        sidesBox.getChildren().addAll(topCb, bottomCb, leftCb, rightCb);
        grid.add(new Label("Sides:"), 0, 7);
        grid.add(sidesBox, 1, 7);

        // --- Data row (record) colors ---
        Node rowBgControl = createColorPickerButton(el.getRowBg() != null ? el.getRowBg() : "#ffffff", hex -> {
            el.setRowBg(hex);
            updateElementVisualInPlace(el);
        });
        grid.add(new Label("Row BG:"), 0, 8);
        grid.add(rowBgControl, 1, 8);

        Node rowTextControl = createColorPickerButton(el.getRowColor() != null ? el.getRowColor() : "#1a1a1a", hex -> {
            el.setRowColor(hex);
            updateElementVisualInPlace(el);
        });
        grid.add(new Label("Row Text:"), 0, 9);
        grid.add(rowTextControl, 1, 9);

        Node zebraControl = createColorPickerButton(el.getZebraColor() != null ? el.getZebraColor() : "#f8f8f8", hex -> {
            el.setZebraColor(hex);
            updateElementVisualInPlace(el);
        });
        grid.add(new Label("Zebra Color:"), 0, 10);
        grid.add(zebraControl, 1, 10);

        CheckBox zebraCb = new CheckBox("Zebra Row Striping");
        zebraCb.setSelected(el.isShowZebra());
        zebraCb.setOnAction(e -> { el.setShowZebra(zebraCb.isSelected()); updateElementVisualInPlace(el); });

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
            lblField.textProperty().addListener((obs, o, v) -> {
                if (updatingProperties || Objects.equals(o, v)) return;
                c.setLabel(v);
                updateElementVisualInPlace(el);
            });

            // Build a labelled combo that shows "Label (key)" for known columns
            ComboBox<String> keyCombo = new ComboBox<>(buildColumnKeyList());
            keyCombo.setEditable(true);
            keyCombo.setValue(c.getKey() != null ? c.getKey() : "desc");
            keyCombo.setPrefWidth(150);
            keyCombo.setTooltip(new Tooltip("Data Field Binding — pick a column or type a custom key"));
            // Display human-readable label in the list but store only the raw key
            keyCombo.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : columnKeyToLabel(item));
                }
            });
            keyCombo.setButtonCell(new ListCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : columnKeyToLabel(item));
                }
            });
            keyCombo.valueProperty().addListener((obs, o, v) -> {
                if (updatingProperties || v == null || Objects.equals(o, v)) return;
                c.setKey(v);
                updateElementVisualInPlace(el);
            });
            keyCombo.getEditor().textProperty().addListener((obs, o, v) -> {
                if (updatingProperties || v == null || Objects.equals(o, v)) return;
                c.setKey(v);
                updateElementVisualInPlace(el);
            });

            Spinner<Double> widthSpin = new Spinner<>(1.0, 100.0, c.getWidth(), 1.0);
            widthSpin.setPrefWidth(65);
            configureNumberSpinner(widthSpin);
            widthSpin.setTooltip(new Tooltip("Width percentage (%)"));
            widthSpin.valueProperty().addListener((obs, o, v) -> {
                if (updatingProperties || v == null || Objects.equals(o, v)) return;
                c.setWidth(v);
                updateElementVisualInPlace(el);
            });

            ComboBox<String> alignCombo = new ComboBox<>(FXCollections.observableArrayList("left", "center", "right"));
            alignCombo.setValue(c.getAlign() != null ? c.getAlign() : "left");
            alignCombo.setPrefWidth(60);
            alignCombo.valueProperty().addListener((obs, o, v) -> {
                if (updatingProperties || v == null || Objects.equals(o, v)) return;
                c.setAlign(v);
                updateElementVisualInPlace(el);
            });

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
                updateElementVisualInPlace(el);
                updatePropertiesPanel();
            });

            colRow.getChildren().addAll(moveStack, lblField, keyCombo, widthSpin, alignCombo, rmBtn);
            colsList.getChildren().add(colRow);
        }

        HBox colActions = new HBox(8);
        Button addColBtn = createToolbarBtn("+ Add Column", "Choose a column to add to the table", () -> {
            showColumnPickerDialog(el);
        });

        Button presetGst = createToolbarBtn("GST Standard (8)", "Reset to standard 8-column GST table", () -> {
            el.setColumns(PresetTemplates.defaultItemColumns());
            saveState();
            updateElementVisualInPlace(el);
            updatePropertiesPanel();
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
            updateElementVisualInPlace(el);
            updatePropertiesPanel();
        });

        colActions.getChildren().addAll(addColBtn, presetGst, presetSimple);

        sec.getChildren().addAll(title, grid, zebraCb, colHeader, colHint, colsList, colActions);
        addPropertyNode(sec);
    }

    private boolean isShapeType(ElementType type) {
        if (type == null) return false;
        return switch (type) {
            case CIRCLE, ELLIPSE, POLYLINE, POLYGON, ARC, PATH, STAR, ARROW, DIVIDER, FREEHAND -> true;
            default -> false;
        };
    }

    private void buildShapeProperties(TemplateElement el) {
        VBox sec = new VBox(10);
        Label title = new Label(el.getDisplayName() + " Properties:");
        title.getStyleClass().add("prop-title");

        // Fill & Gradients Section
        TitledPane fillPane = new TitledPane();
        fillPane.setText("Fill & Gradients");
        fillPane.setExpanded(true);

        VBox fillBox = new VBox(8);
        fillBox.setPadding(new Insets(6));

        ComboBox<String> fillTypeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Solid Color", "Linear Gradient", "Radial Gradient", "Transparent / None"));
        String curFill = el.getFillType() != null ? el.getFillType() : ("transparent".equalsIgnoreCase(el.getBg()) ? "none" : "solid");
        if ("linear".equalsIgnoreCase(curFill)) fillTypeCombo.setValue("Linear Gradient");
        else if ("radial".equalsIgnoreCase(curFill)) fillTypeCombo.setValue("Radial Gradient");
        else if ("none".equalsIgnoreCase(curFill) || "transparent".equalsIgnoreCase(el.getBg())) fillTypeCombo.setValue("Transparent / None");
        else fillTypeCombo.setValue("Solid Color");

        fillBox.getChildren().add(new HBox(8, new Label("Fill Type:"), fillTypeCombo));

        VBox fillOptionsBox = new VBox(6);
        Runnable updateFillUi = () -> {
            fillOptionsBox.getChildren().clear();
            String sel = fillTypeCombo.getValue();
            if ("Solid Color".equals(sel)) {
                el.setFillType("solid");
                Node colNode = createColorPickerButton(el.getEffectiveFillColor(), hex -> {
                    el.setBg(hex);
                    refreshCanvas();
                });
                fillOptionsBox.getChildren().add(new HBox(8, new Label("Color:"), colNode));
            } else if ("Linear Gradient".equals(sel)) {
                el.setFillType("linear");
                Node startCol = createColorPickerButton(el.getGradientStartColor() != null ? el.getGradientStartColor() : "#3b82f6", hex -> {
                    el.setGradientStartColor(hex);
                    refreshCanvas();
                });
                Node endCol = createColorPickerButton(el.getGradientEndColor() != null ? el.getGradientEndColor() : "#9333ea", hex -> {
                    el.setGradientEndColor(hex);
                    refreshCanvas();
                });
                Spinner<Double> angleSpin = new Spinner<>(0.0, 360.0, el.getGradientAngle(), 15.0);
                configureNumberSpinner(angleSpin);
                angleSpin.valueProperty().addListener((obs, o, v) -> { el.setGradientAngle(v); refreshCanvas(); });

                fillOptionsBox.getChildren().addAll(
                        new HBox(8, new Label("Start:"), startCol, new Label("End:"), endCol),
                        new HBox(8, new Label("Angle (°):"), angleSpin)
                );
            } else if ("Radial Gradient".equals(sel)) {
                el.setFillType("radial");
                Node startCol = createColorPickerButton(el.getGradientStartColor() != null ? el.getGradientStartColor() : "#38bdf8", hex -> {
                    el.setGradientStartColor(hex);
                    refreshCanvas();
                });
                Node endCol = createColorPickerButton(el.getGradientEndColor() != null ? el.getGradientEndColor() : "#1e40af", hex -> {
                    el.setGradientEndColor(hex);
                    refreshCanvas();
                });
                fillOptionsBox.getChildren().add(new HBox(8, new Label("Center:"), startCol, new Label("Outer:"), endCol));
            } else {
                el.setFillType("none");
                el.setBg("transparent");
            }
            refreshCanvas();
        };

        fillTypeCombo.valueProperty().addListener((obs, o, v) -> updateFillUi.run());
        updateFillUi.run();
        fillBox.getChildren().add(fillOptionsBox);
        fillPane.setContent(fillBox);

        // Stroke & Outline Section
        TitledPane strokePane = new TitledPane();
        strokePane.setText("Stroke & Outline");
        strokePane.setExpanded(true);

        VBox strokeBox = new VBox(8);
        strokeBox.setPadding(new Insets(6));

        CheckBox strokeCb = new CheckBox("Enable Stroke / Border");
        strokeCb.setSelected(el.isStrokeEnabled() || el.getBorderWidth() > 0);
        strokeCb.selectedProperty().addListener((obs, o, v) -> {
            el.setStrokeEnabled(v);
            refreshCanvas();
        });

        Node strokeColNode = createColorPickerButton(el.getEffectiveStrokeColor(), hex -> {
            el.setBorderColor(hex);
            refreshCanvas();
        });

        Spinner<Double> strokeWSpin = new Spinner<>(0.1, 20.0, el.getEffectiveStrokeWidth(), 0.5);
        configureNumberSpinner(strokeWSpin);
        strokeWSpin.valueProperty().addListener((obs, o, v) -> {
            el.setBorderWidth(v);
            refreshCanvas();
        });

        ComboBox<String> dashBox = new ComboBox<>(FXCollections.observableArrayList("Solid", "Dashed", "Dotted", "Dash-Dot"));
        String curDash = el.getDashPattern();
        if ("5,5".equals(curDash) || "dashed".equalsIgnoreCase(el.getStrokeType())) dashBox.setValue("Dashed");
        else if ("2,2".equals(curDash) || "dotted".equalsIgnoreCase(el.getStrokeType())) dashBox.setValue("Dotted");
        else if ("6,3,2,3".equals(curDash)) dashBox.setValue("Dash-Dot");
        else dashBox.setValue("Solid");

        dashBox.valueProperty().addListener((obs, o, v) -> {
            if ("Dashed".equals(v)) { el.setDashPattern("5,5"); el.setStrokeType("dashed"); }
            else if ("Dotted".equals(v)) { el.setDashPattern("2,2"); el.setStrokeType("dotted"); }
            else if ("Dash-Dot".equals(v)) { el.setDashPattern("6,3,2,3"); el.setStrokeType("dash-dot"); }
            else { el.setDashPattern(null); el.setStrokeType("solid"); }
            refreshCanvas();
        });

        ComboBox<String> capBox = new ComboBox<>(FXCollections.observableArrayList("BUTT", "ROUND", "SQUARE"));
        capBox.setValue(el.getLineCap() != null ? el.getLineCap().toUpperCase() : "BUTT");
        capBox.valueProperty().addListener((obs, o, v) -> { el.setLineCap(v); refreshCanvas(); });

        ComboBox<String> joinBox = new ComboBox<>(FXCollections.observableArrayList("MITER", "ROUND", "BEVEL"));
        joinBox.setValue(el.getLineJoin() != null ? el.getLineJoin().toUpperCase() : "MITER");
        joinBox.valueProperty().addListener((obs, o, v) -> { el.setLineJoin(v); refreshCanvas(); });

        GridPane strokeGrid = new GridPane();
        strokeGrid.setHgap(8); strokeGrid.setVgap(6);
        strokeGrid.add(strokeCb, 0, 0, 2, 1);
        strokeGrid.add(new Label("Color:"), 0, 1); strokeGrid.add(strokeColNode, 1, 1);
        strokeGrid.add(new Label("Width (mm):"), 0, 2); strokeGrid.add(strokeWSpin, 1, 2);
        strokeGrid.add(new Label("Dash Pattern:"), 0, 3); strokeGrid.add(dashBox, 1, 3);
        strokeGrid.add(new Label("Cap:"), 0, 4); strokeGrid.add(capBox, 1, 4);
        strokeGrid.add(new Label("Join:"), 0, 5); strokeGrid.add(joinBox, 1, 5);

        strokeBox.getChildren().add(strokeGrid);
        strokePane.setContent(strokeBox);

        sec.getChildren().addAll(title, fillPane, strokePane);
        buildShapeGeometryProperties(el, sec);
        if (el.getType() == ElementType.POLYGON || el.getType() == ElementType.POLYLINE || el.getType() == ElementType.PATH || el.getType() == ElementType.FREEHAND) {
            sec.getChildren().add(buildCurveAndAnchorPropertiesPane(el));
        }
        addPropertyNode(sec);
    }

    private void buildShapeGeometryProperties(TemplateElement el, VBox sec) {
        TitledPane geoPane = new TitledPane();
        geoPane.setText("Geometry Parameters");
        geoPane.setExpanded(true);

        GridPane g = new GridPane();
        g.setHgap(8); g.setVgap(6);
        g.setPadding(new Insets(6));

        if (el.getType() == ElementType.CIRCLE) {
            Spinner<Double> rSpin = new Spinner<>(1.0, 500.0, el.getRadius() > 0 ? el.getRadius() : el.getW() / 2.0, 1.0);
            configureNumberSpinner(rSpin);
            rSpin.valueProperty().addListener((obs, o, v) -> {
                el.setRadius(v);
                el.setW(v * 2);
                el.setH(v * 2);
                refreshCanvas();
            });
            g.add(new Label("Radius (mm):"), 0, 0);
            g.add(rSpin, 1, 0);
        } else if (el.getType() == ElementType.ELLIPSE) {
            Spinner<Double> rxSpin = new Spinner<>(1.0, 500.0, el.getRadiusX() > 0 ? el.getRadiusX() : el.getW() / 2.0, 1.0);
            configureNumberSpinner(rxSpin);
            rxSpin.valueProperty().addListener((obs, o, v) -> {
                el.setRadiusX(v);
                el.setW(v * 2);
                refreshCanvas();
            });
            Spinner<Double> rySpin = new Spinner<>(1.0, 500.0, el.getRadiusY() > 0 ? el.getRadiusY() : el.getH() / 2.0, 1.0);
            configureNumberSpinner(rySpin);
            rySpin.valueProperty().addListener((obs, o, v) -> {
                el.setRadiusY(v);
                el.setH(v * 2);
                refreshCanvas();
            });
            g.add(new Label("Radius X:"), 0, 0); g.add(rxSpin, 1, 0);
            g.add(new Label("Radius Y:"), 0, 1); g.add(rySpin, 1, 1);
        } else if (el.getType() == ElementType.STAR) {
            Spinner<Integer> ptsSpin = new Spinner<>(3, 20, el.getStarPoints() > 0 ? el.getStarPoints() : 5, 1);
            ptsSpin.valueProperty().addListener((obs, o, v) -> { el.setStarPoints(v); refreshCanvas(); });

            Spinner<Double> innerSpin = new Spinner<>(0.5, 200.0, el.getInnerRadius() > 0 ? el.getInnerRadius() : 6.0, 1.0);
            configureNumberSpinner(innerSpin);
            innerSpin.valueProperty().addListener((obs, o, v) -> { el.setInnerRadius(v); refreshCanvas(); });

            Spinner<Double> outerSpin = new Spinner<>(1.0, 300.0, el.getOuterRadius() > 0 ? el.getOuterRadius() : 15.0, 1.0);
            configureNumberSpinner(outerSpin);
            outerSpin.valueProperty().addListener((obs, o, v) -> { el.setOuterRadius(v); refreshCanvas(); });

            g.add(new Label("Points:"), 0, 0); g.add(ptsSpin, 1, 0);
            g.add(new Label("Inner Radius:"), 0, 1); g.add(innerSpin, 1, 1);
            g.add(new Label("Outer Radius:"), 0, 2); g.add(outerSpin, 1, 2);
        } else if (el.getType() == ElementType.ARROW) {
            Spinner<Double> headLen = new Spinner<>(1.0, 100.0, el.getArrowHeadLength() > 0 ? el.getArrowHeadLength() : 6.0, 1.0);
            configureNumberSpinner(headLen);
            headLen.valueProperty().addListener((obs, o, v) -> { el.setArrowHeadLength(v); refreshCanvas(); });

            Spinner<Double> headWid = new Spinner<>(1.0, 100.0, el.getArrowHeadWidth() > 0 ? el.getArrowHeadWidth() : 6.0, 1.0);
            configureNumberSpinner(headWid);
            headWid.valueProperty().addListener((obs, o, v) -> { el.setArrowHeadWidth(v); refreshCanvas(); });

            ComboBox<String> styleBox = new ComboBox<>(FXCollections.observableArrayList("TRIANGLE", "OPEN", "STEALTH"));
            styleBox.setValue(el.getArrowHeadStyle() != null ? el.getArrowHeadStyle().toUpperCase() : "TRIANGLE");
            styleBox.valueProperty().addListener((obs, o, v) -> { el.setArrowHeadStyle(v); refreshCanvas(); });

            g.add(new Label("Head Length:"), 0, 0); g.add(headLen, 1, 0);
            g.add(new Label("Head Width:"), 0, 1); g.add(headWid, 1, 1);
            g.add(new Label("Head Style:"), 0, 2); g.add(styleBox, 1, 2);
        } else if (el.getType() == ElementType.ARC) {
            Spinner<Double> startSpin = new Spinner<>(-360.0, 360.0, el.getStartAngle(), 15.0);
            configureNumberSpinner(startSpin);
            startSpin.valueProperty().addListener((obs, o, v) -> { el.setStartAngle(v); refreshCanvas(); });

            Spinner<Double> lenSpin = new Spinner<>(0.0, 360.0, el.getArcLength() > 0 ? el.getArcLength() : 270.0, 15.0);
            configureNumberSpinner(lenSpin);
            lenSpin.valueProperty().addListener((obs, o, v) -> { el.setArcLength(v); refreshCanvas(); });

            ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList("ROUND", "OPEN", "CHORD"));
            typeBox.setValue(el.getArcType() != null ? el.getArcType().toUpperCase() : "ROUND");
            typeBox.valueProperty().addListener((obs, o, v) -> { el.setArcType(v); refreshCanvas(); });

            g.add(new Label("Start Angle (°):"), 0, 0); g.add(startSpin, 1, 0);
            g.add(new Label("Arc Length (°):"), 0, 1); g.add(lenSpin, 1, 1);
            g.add(new Label("Arc Type:"), 0, 2); g.add(typeBox, 1, 2);
        } else if (el.getType() == ElementType.DIVIDER) {
            ComboBox<String> orientBox = new ComboBox<>(FXCollections.observableArrayList("HORIZONTAL", "VERTICAL"));
            orientBox.setValue(el.getDividerOrientation() != null ? el.getDividerOrientation().toUpperCase() : "HORIZONTAL");
            orientBox.valueProperty().addListener((obs, o, v) -> { el.setDividerOrientation(v); refreshCanvas(); });

            ComboBox<String> styleBox = new ComboBox<>(FXCollections.observableArrayList("SOLID", "DASHED", "DOTTED", "DOUBLE"));
            styleBox.setValue(el.getDividerStyle() != null ? el.getDividerStyle().toUpperCase() : "DASHED");
            styleBox.valueProperty().addListener((obs, o, v) -> { el.setDividerStyle(v); refreshCanvas(); });

            g.add(new Label("Orientation:"), 0, 0); g.add(orientBox, 1, 0);
            g.add(new Label("Style:"), 0, 1); g.add(styleBox, 1, 1);
        } else if (el.getType() == ElementType.PATH) {
            TextArea pathArea = new TextArea(el.getPathData() != null ? el.getPathData() : "");
            pathArea.setPrefRowCount(3);
            pathArea.setWrapText(true);
            pathArea.textProperty().addListener((obs, o, v) -> { el.setPathData(v); refreshCanvas(); });
            g.add(new Label("SVG Path (d):"), 0, 0);
            g.add(pathArea, 0, 1, 2, 1);
        } else if (el.getType() == ElementType.POLYGON) {
            int initialSides = 3;
            if (el.getPoints() != null && !el.getPoints().isBlank()) {
                String[] pts = el.getPoints().trim().split("\\s+");
                if (pts.length >= 3) {
                    initialSides = Math.min(20, pts.length);
                }
            }
            Spinner<Integer> sidesSpin = new Spinner<>(3, 20, initialSides, 1);
            sidesSpin.setPrefWidth(85);
            TextField ptsField = new TextField(el.getPoints() != null ? el.getPoints() : "");
            ptsField.setPromptText("x1,y1 x2,y2 x3,y3 ...");

            sidesSpin.valueProperty().addListener((obs, o, v) -> {
                if (v != null) {
                    String newPts = generateRegularPolygonPoints(v, el.getW(), el.getH());
                    el.setPoints(newPts);
                    ptsField.setText(newPts);
                    updateElementVisualInPlace(el);
                }
            });

            HBox chipsBox = new HBox(4);
            int[] presets = {3, 4, 5, 6, 8};
            String[] labels = {"3 △", "4 ◇", "5 ⬠", "6 ⬡", "8 ⯃"};
            for (int pi = 0; pi < presets.length; pi++) {
                int s = presets[pi];
                Button chip = new Button(labels[pi]);
                chip.getStyleClass().addAll("button-xs", "button-secondary");
                chip.setOnAction(ev -> sidesSpin.getValueFactory().setValue(s));
                chipsBox.getChildren().add(chip);
            }

            ptsField.textProperty().addListener((obs, o, v) -> {
                el.setPoints(v);
                updateElementVisualInPlace(el);
            });

            g.add(new Label("Edges / Sides:"), 0, 0);
            g.add(sidesSpin, 1, 0);
            g.add(new Label("Presets:"), 0, 1);
            g.add(chipsBox, 1, 1);
            g.add(new Label("Points (X,Y):"), 0, 2);
            g.add(ptsField, 1, 2);
        } else if (el.getType() == ElementType.POLYLINE || el.getType() == ElementType.FREEHAND) {
            TextField ptsField = new TextField(el.getPoints() != null ? el.getPoints() : "");
            ptsField.setPromptText("x1,y1 x2,y2 x3,y3 ...");
            ptsField.textProperty().addListener((obs, o, v) -> { el.setPoints(v); updateElementVisualInPlace(el); });
            g.add(new Label("Points (X,Y):"), 0, 0);
            g.add(ptsField, 1, 0);
        }

        geoPane.setContent(g);
        sec.getChildren().add(geoPane);
    }

    private String generateRegularPolygonPoints(int sides, double wMm, double hMm) {
        if (sides < 3) sides = 3;
        StringBuilder sb = new StringBuilder();
        double cx = wMm / 2.0;
        double cy = hMm / 2.0;
        double rx = wMm / 2.0;
        double ry = hMm / 2.0;
        for (int i = 0; i < sides; i++) {
            double angle = -Math.PI / 2.0 + (2.0 * Math.PI * i / sides);
            double x = cx + rx * Math.cos(angle);
            double y = cy + ry * Math.sin(angle);
            if (i > 0) sb.append(" ");
            sb.append(String.format(java.util.Locale.US, "%.1f,%.1f", x, y));
        }
        return sb.toString();
    }

    private void handlePenCanvasClick(MouseEvent e) {
        if (!isPenToolMode) return;
        if (e.getButton() != MouseButton.PRIMARY) {
            if (e.getButton() == MouseButton.SECONDARY) {
                cancelPenTool();
            }
            return;
        }

        double ptX = Math.max(0, e.getX() / MM_PX);
        double ptY = Math.max(0, e.getY() / MM_PX);
        if (snapToGrid) {
            ptX = Math.round(ptX);
            ptY = Math.round(ptY);
        }

        // Check if double click to finish
        if (e.getClickCount() >= 2 && penPoints.size() >= 3) {
            finishPenPath();
            return;
        }

        // Check if clicked close to start point (within 5mm) to close polygon
        if (penPoints.size() >= 3) {
            Point2D startPt = penPoints.get(0);
            double dist = Math.hypot(ptX - startPt.getX(), ptY - startPt.getY());
            if (dist <= 5.0) {
                finishPenPath();
                return;
            }
        }

        penPoints.add(new Point2D(ptX, ptY));
        renderPenPreview(ptX, ptY);
    }

    private void handlePenCanvasMove(MouseEvent e) {
        if (!isPenToolMode || penPoints.isEmpty()) return;
        double ptX = Math.max(0, e.getX() / MM_PX);
        double ptY = Math.max(0, e.getY() / MM_PX);
        if (snapToGrid) {
            ptX = Math.round(ptX);
            ptY = Math.round(ptY);
        }
        renderPenPreview(ptX, ptY);
    }

    private void renderPenPreview(double curMmX, double curMmY) {
        penLayer.getChildren().clear();
        if (penPoints.isEmpty()) return;

        if (penCurveMode && penPoints.size() >= 2) {
            List<Point2D> curvePts = new ArrayList<>(penPoints);
            curvePts.add(new Point2D(curMmX, curMmY));
            String bezierD = generateSmoothBezierPath(curvePts, 0.5, false);
            SVGPath curvePreview = new SVGPath();
            curvePreview.setContent(bezierD);
            curvePreview.getTransforms().add(new javafx.scene.transform.Scale(MM_PX, MM_PX, 0, 0));
            curvePreview.setStroke(Color.web("#3B82F6"));
            curvePreview.setStrokeWidth(2.0);
            curvePreview.setFill(Color.TRANSPARENT);
            curvePreview.getStrokeDashArray().addAll(4.0, 4.0);
            penLayer.getChildren().add(curvePreview);
        } else {
            // Draw existing connected segments
            Polyline polyline = new Polyline();
            polyline.setStroke(Color.web("#3B82F6"));
            polyline.setStrokeWidth(2.0);
            polyline.getStrokeDashArray().addAll(4.0, 4.0);

            for (Point2D pt : penPoints) {
                polyline.getPoints().addAll(pt.getX() * MM_PX, pt.getY() * MM_PX);
            }
            penLayer.getChildren().add(polyline);

            // Draw dynamic rubberband line to current mouse position
            Point2D lastPt = penPoints.get(penPoints.size() - 1);
            Line rubberBand = new Line(lastPt.getX() * MM_PX, lastPt.getY() * MM_PX, curMmX * MM_PX, curMmY * MM_PX);
            rubberBand.setStroke(Color.web("#60A5FA"));
            rubberBand.setStrokeWidth(1.5);
            rubberBand.getStrokeDashArray().addAll(2.0, 3.0);
            penLayer.getChildren().add(rubberBand);
        }

        // Draw anchor handles
        for (int i = 0; i < penPoints.size(); i++) {
            Point2D pt = penPoints.get(i);
            double r = (i == 0 && penPoints.size() >= 3) ? 6.0 : 4.0;
            Circle handle = new Circle(pt.getX() * MM_PX, pt.getY() * MM_PX, r);
            if (i == 0 && penPoints.size() >= 3) {
                // Highlight start point when closeable
                handle.setFill(Color.web("#10B981"));
                handle.setStroke(Color.web("#FFFFFF"));
                handle.setStrokeWidth(2.0);
            } else {
                handle.setFill(Color.web("#3B82F6"));
                handle.setStroke(Color.web("#FFFFFF"));
                handle.setStrokeWidth(1.5);
            }
            penLayer.getChildren().add(handle);
        }
    }

    private void finishPenPath() {
        if (penPoints.size() < 3) {
            cancelPenTool();
            return;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Point2D pt : penPoints) {
            if (pt.getX() < minX) minX = pt.getX();
            if (pt.getY() < minY) minY = pt.getY();
            if (pt.getX() > maxX) maxX = pt.getX();
            if (pt.getY() > maxY) maxY = pt.getY();
        }

        double w = Math.max(10.0, maxX - minX);
        double h = Math.max(10.0, maxY - minY);

        List<Point2D> relPts = new ArrayList<>();
        StringBuilder ptsBuilder = new StringBuilder();
        for (int i = 0; i < penPoints.size(); i++) {
            Point2D pt = penPoints.get(i);
            double relX = pt.getX() - minX;
            double relY = pt.getY() - minY;
            relPts.add(new Point2D(relX, relY));
            if (i > 0) ptsBuilder.append(" ");
            ptsBuilder.append(String.format(java.util.Locale.US, "%.1f,%.1f", relX, relY));
        }

        TemplateElement el = new TemplateElement();
        el.setId(UUID.randomUUID().toString());
        el.setName((penCurveMode ? "Curved Vector " : "Custom Vector ") + (template.getElements().size() + 1));
        el.setX(minX);
        el.setY(minY);
        el.setW(w);
        el.setH(h);
        el.setPoints(ptsBuilder.toString());
        el.setBg("#3B82F6");
        el.setFillType("solid");
        el.setBorderColor("#1E40AF");
        el.setBorderWidth(0.5);
        el.setStrokeEnabled(true);

        if (penCurveMode) {
            el.setType(ElementType.PATH);
            String curveD = generateSmoothBezierPath(relPts, 0.5, true);
            el.setPathData(curveD);
        } else {
            el.setType(ElementType.POLYGON);
        }

        template.getElements().add(el);
        cancelPenTool();
        saveState();
        refreshCanvas();
        selectedElement = el;
        updateSelectionOverlay();
        updatePropertiesPanel();
        refreshLayersList();
        Toast.show(this, (penCurveMode ? "Curved vector path" : "Custom vector shape") + " created successfully.");
    }

    private boolean isPointEditable(TemplateElement el) {
        return VectorGeometryUtil.isPointEditable(el);
    }

    private boolean isCurved(TemplateElement el) {
        return VectorGeometryUtil.isCurved(el);
    }

    /** Kept for the existing unit tests; logic now lives in {@link VectorGeometryUtil}. */
    public static List<Point2D> parseElementVertices(TemplateElement el) {
        return VectorGeometryUtil.parseElementVertices(el);
    }

    /** Kept for the existing unit tests; logic now lives in {@link VectorGeometryUtil}. */
    public static List<Point2D> extractPointsFromSvgPath(String d) {
        return VectorGeometryUtil.extractPointsFromSvgPath(d);
    }

    /** Kept for the existing unit tests; logic now lives in {@link VectorGeometryUtil}. */
    public static String generateSmoothBezierPath(List<Point2D> pts, double tension, boolean closed) {
        return VectorGeometryUtil.generateSmoothBezierPath(pts, tension, closed);
    }

    private void syncVerticesToElement(TemplateElement el, List<Point2D> curPts, boolean curved, double tension) {
        VectorGeometryUtil.syncVerticesToElement(el, curPts, curved, tension);
    }

    private void addVertexAnchorHandles(Pane selBox, TemplateElement el) {
        List<Point2D> pts = parseElementVertices(el);
        if (pts.isEmpty()) return;

        // Vertex anchors shrink with zoom like every other selection handle —
        // fixed design-space dots became huge blobs over small shapes at 400 %.
        double inv = 1.0 / Math.max(0.3, zoom);
        for (int idx = 0; idx < pts.size(); idx++) {
            final int vertexIdx = idx;
            Point2D pt = pts.get(idx);

            Circle handle = new Circle(pt.getX() * MM_PX, pt.getY() * MM_PX, 5.5 * inv);
            handle.setFill(Color.web("#0EA5E9"));
            handle.setStroke(Color.WHITE);
            handle.setStrokeWidth(1.8 * inv);
            handle.setCursor(Cursor.CROSSHAIR);
            handle.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), "
                    + String.format(java.util.Locale.US, "%.2f, 0, 0, %.2f);", 3.0 * inv, 1.0 * inv) + "");

            handle.setOnMouseEntered(e -> {
                handle.setScaleX(1.35);
                handle.setScaleY(1.35);
                handle.setFill(Color.web("#38BDF8"));
            });
            handle.setOnMouseExited(e -> {
                handle.setScaleX(1.0);
                handle.setScaleY(1.0);
                handle.setFill(Color.web("#0EA5E9"));
            });

            Tooltip.install(handle, new Tooltip("Anchor Point #" + (vertexIdx + 1) + " (Drag to reposition)"));

            final double[] startPos = new double[4];
            final boolean[] isVertexMoved = new boolean[1];

            handle.setOnMousePressed(e -> {
                if (e.isPrimaryButtonDown()) {
                    startPos[0] = e.getScreenX();
                    startPos[1] = e.getScreenY();
                    List<Point2D> currentPts = parseElementVertices(el);
                    if (vertexIdx < currentPts.size()) {
                        startPos[2] = currentPts.get(vertexIdx).getX();
                        startPos[3] = currentPts.get(vertexIdx).getY();
                    }
                    isVertexMoved[0] = false;
                    e.consume();
                }
            });

            handle.setOnMouseDragged(e -> {
                if (el.isLocked() || !e.isPrimaryButtonDown()) return;
                isVertexMoved[0] = true;

                double rad = Math.toRadians(-el.getRotation());
                double screenDx = (e.getScreenX() - startPos[0]) / zoom / MM_PX;
                double screenDy = (e.getScreenY() - startPos[1]) / zoom / MM_PX;

                double localDx = screenDx * Math.cos(rad) - screenDy * Math.sin(rad);
                double localDy = screenDx * Math.sin(rad) + screenDy * Math.cos(rad);

                double newMmX = startPos[2] + localDx;
                double newMmY = startPos[3] + localDy;
                if (snapToGrid) {
                    newMmX = Math.round(newMmX);
                    newMmY = Math.round(newMmY);
                }

                handle.setCenterX(newMmX * MM_PX);
                handle.setCenterY(newMmY * MM_PX);

                List<Point2D> currentPts = parseElementVertices(el);
                if (vertexIdx < currentPts.size()) {
                    currentPts.set(vertexIdx, new Point2D(newMmX, newMmY));

                    double maxMmX = el.getW();
                    double maxMmY = el.getH();
                    for (Point2D p : currentPts) {
                        if (p.getX() > maxMmX) maxMmX = p.getX();
                        if (p.getY() > maxMmY) maxMmY = p.getY();
                    }
                    if (maxMmX > el.getW()) el.setW(maxMmX);
                    if (maxMmY > el.getH()) el.setH(maxMmY);

                    syncVerticesToElement(el, currentPts, isCurved(el), 0.5);
                    updateElementVisualInPlace(el);
                }
                e.consume();
            });

            handle.setOnMouseReleased(e -> {
                if (isVertexMoved[0]) {
                    saveState();
                    syncGeoSpinnersIfPresent();
                    updatePropertiesPanel();
                }
                e.consume();
            });

            selBox.getChildren().add(handle);
        }
    }

    private TitledPane buildCurveAndAnchorPropertiesPane(TemplateElement el) {
        TitledPane pane = new TitledPane();
        pane.setText("Vector Curves & Anchor Points");
        pane.setExpanded(true);

        VBox box = new VBox(8);
        box.setPadding(new Insets(8));

        Label infoLbl = new Label("💡 Drag the cyan circle handles directly on the canvas to move any vertex.");
        infoLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #0EA5E9; -fx-wrap-text: true;");

        List<Point2D> pts = parseElementVertices(el);
        Label ptsCountLbl = new Label("Vertices: " + pts.size() + " anchor points");
        ptsCountLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        boolean curved = isCurved(el);
        Button toggleCurveBtn = new Button(curved ? "📐 Convert to Sharp Corners" : "〰 Convert to Smooth Bezier Curves");
        toggleCurveBtn.getStyleClass().addAll("button-xs", curved ? "button-secondary" : "button-primary");
        toggleCurveBtn.setMaxWidth(Double.MAX_VALUE);

        Label tensionLbl = new Label("Curve Tension (Curvature):");
        Slider tensionSlider = new Slider(0.1, 1.0, 0.5);
        tensionSlider.setShowTickMarks(true);
        tensionSlider.setMajorTickUnit(0.2);
        tensionSlider.setDisable(!curved);

        toggleCurveBtn.setOnAction(e -> {
            List<Point2D> curPts = parseElementVertices(el);
            if (curPts.size() < 2) {
                Toast.show(this, "Need at least 2 points to curve.");
                return;
            }
            boolean currentlyCurved = isCurved(el);
            if (currentlyCurved) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < curPts.size(); i++) {
                    Point2D p = curPts.get(i);
                    if (i > 0) sb.append(" ");
                    sb.append(String.format(java.util.Locale.US, "%.1f,%.1f", p.getX(), p.getY()));
                }
                el.setPoints(sb.toString());
                el.setPathData(null);
                if (el.getType() == ElementType.PATH) {
                    el.setType(ElementType.POLYGON);
                }
            } else {
                boolean closed = el.getType() != ElementType.POLYLINE && el.getType() != ElementType.FREEHAND;
                String bezierD = generateSmoothBezierPath(curPts, tensionSlider.getValue(), closed);
                el.setPathData(bezierD);
                if (el.getType() == ElementType.SVG) {
                    el.setSvgSource(bezierD);
                } else {
                    el.setType(ElementType.PATH);
                }
            }
            saveState();
            updateElementVisualInPlace(el);
            updateSelectionOverlay();
            updatePropertiesPanel();
        });

        tensionSlider.valueProperty().addListener((obs, o, v) -> {
            if (isCurved(el)) {
                List<Point2D> curPts = parseElementVertices(el);
                if (curPts.size() >= 2) {
                    boolean closed = el.getType() != ElementType.POLYLINE && el.getType() != ElementType.FREEHAND;
                    String bezierD = generateSmoothBezierPath(curPts, v.doubleValue(), closed);
                    el.setPathData(bezierD);
                    if (el.getType() == ElementType.SVG) {
                        el.setSvgSource(bezierD);
                    }
                    updateElementVisualInPlace(el);
                }
            }
        });
        tensionSlider.setOnMouseReleased(e -> saveState());

        Button addPtBtn = new Button("+ Add Vertex");
        addPtBtn.getStyleClass().addAll("button-xs", "button-secondary");
        addPtBtn.setOnAction(e -> {
            List<Point2D> curPts = parseElementVertices(el);
            if (curPts.isEmpty()) {
                curPts.add(new Point2D(0, 0));
                curPts.add(new Point2D(el.getW() / 2, el.getH()));
                curPts.add(new Point2D(el.getW(), 0));
            } else if (curPts.size() == 1) {
                curPts.add(new Point2D(curPts.get(0).getX() + 10, curPts.get(0).getY() + 10));
            } else {
                Point2D p1 = curPts.get(curPts.size() - 1);
                Point2D p2 = curPts.get(0);
                curPts.add(new Point2D((p1.getX() + p2.getX()) / 2.0, (p1.getY() + p2.getY()) / 2.0));
            }
            syncVerticesToElement(el, curPts, isCurved(el), tensionSlider.getValue());
            saveState();
            updateElementVisualInPlace(el);
            updateSelectionOverlay();
            updatePropertiesPanel();
        });

        Button removePtBtn = new Button("- Remove Last Vertex");
        removePtBtn.getStyleClass().addAll("button-xs", "button-secondary");
        removePtBtn.setDisable(pts.size() <= 3);
        removePtBtn.setOnAction(e -> {
            List<Point2D> curPts = parseElementVertices(el);
            if (curPts.size() > 3) {
                curPts.remove(curPts.size() - 1);
                syncVerticesToElement(el, curPts, isCurved(el), tensionSlider.getValue());
                saveState();
                updateElementVisualInPlace(el);
                updateSelectionOverlay();
                updatePropertiesPanel();
            }
        });

        HBox ptBtnRow = new HBox(6, addPtBtn, removePtBtn);

        box.getChildren().addAll(infoLbl, ptsCountLbl, toggleCurveBtn, tensionLbl, tensionSlider, ptBtnRow);
        pane.setContent(box);
        return pane;
    }

    private void buildSvgProperties(TemplateElement el) {
        VBox sec = new VBox(8);
        Label title = new Label("SVG Vector Properties:");
        title.getStyleClass().add("prop-title");

        // SVG Help / Guidance banner
        Label guideLbl = new Label("ℹ Supports full .svg vector files (<svg>, <path>, <rect>, <circle>, <polygon>) and raw path strings. Vectors scale sharply at any resolution.");
        guideLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #94A3B8; -fx-background-color: rgba(30, 41, 59, 0.6); -fx-padding: 6 8; -fx-background-radius: 4; -fx-wrap-text: true;");

        Button loadSvgBtn = createToolbarBtn("📁 Load SVG File...", "Import .svg vector file from your computer", () -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select SVG File");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SVG Files (*.svg)", "*.svg"));
            File f = fc.showOpenDialog(app.getPrimaryStage());
            if (f != null) {
                try {
                    String content = Files.readString(f.toPath());
                    el.setSvgSource(content);
                    saveState();
                    refreshCanvas();
                    updatePropertiesPanel();
                    Toast.show(app.getRootPane(), "SVG Loaded", "Loaded " + f.getName(), false);
                } catch (Exception ex) {
                    Toast.show(app.getRootPane(), "SVG Error", ex.getMessage(), true);
                }
            }
        });
        loadSvgBtn.setMaxWidth(Double.MAX_VALUE);

        TextArea svgArea = new TextArea(el.getSvgSource() != null ? el.getSvgSource() : "");
        svgArea.setPrefRowCount(4);
        svgArea.setWrapText(true);
        svgArea.setTooltip(new Tooltip("Raw SVG XML markup or SVG path commands ('M... Z')"));
        svgArea.textProperty().addListener((obs, o, v) -> { el.setSvgSource(v); refreshCanvas(); });

        // Optional Color Tint
        Node colControl = createColorPickerButton(el.getColor() != null ? el.getColor() : "#2563EB", hex -> {
            el.setColor(hex);
            refreshCanvas();
        });
        HBox colRow = new HBox(8, new Label("Color Tint / Fill:"), colControl);
        colRow.setAlignment(Pos.CENTER_LEFT);

        sec.getChildren().addAll(title, guideLbl, loadSvgBtn, new Label("SVG Source XML:"), svgArea, colRow);
        sec.getChildren().add(buildCurveAndAnchorPropertiesPane(el));
        addPropertyNode(sec);
    }

    private void buildIconProperties(TemplateElement el) {
        VBox sec = new VBox(8);
        Label title = new Label("Icon Properties:");
        title.getStyleClass().add("prop-title");

        GridPane g = new GridPane();
        g.setHgap(8); g.setVgap(6);

        ComboBox<String> iconCombo = new ComboBox<>(FXCollections.observableArrayList(
                "check", "star", "phone", "mail", "map-pin", "user", "globe", "file-text",
                "hash", "dollar-sign", "percent", "calendar", "clock", "shield", "alert-circle",
                "info", "heart", "shopping-cart", "truck", "zap", "tag", "award"
        ));
        iconCombo.setValue(el.getIconName() != null ? el.getIconName() : "check");
        iconCombo.setTooltip(new Tooltip("Vector icon glyph to display"));
        iconCombo.valueProperty().addListener((obs, o, v) -> { el.setIconName(v); refreshCanvas(); });

        Node colNode = createColorPickerButton(el.getColor() != null ? el.getColor() : "#2563eb", hex -> {
            el.setColor(hex);
            refreshCanvas();
        });

        g.add(new Label("Icon:"), 0, 0); g.add(iconCombo, 1, 0);
        g.add(new Label("Color:"), 0, 1); g.add(colNode, 1, 1);

        sec.getChildren().addAll(title, g);
        addPropertyNode(sec);
    }

    private void buildWatermarkProperties(TemplateElement el) {
        VBox sec = new VBox(8);
        Label title = new Label("Watermark Properties:");
        title.getStyleClass().add("prop-title");

        GridPane g = new GridPane();
        g.setHgap(8); g.setVgap(6);

        TextField wmField = new TextField(el.getText() != null ? el.getText() : "CONFIDENTIAL");
        wmField.setTooltip(new Tooltip("Watermark text printed across the page background"));
        wmField.textProperty().addListener((obs, o, v) -> { el.setText(v); refreshCanvas(); });

        Slider opacSlider = new Slider(0.02, 0.5, el.getOpacity() > 0 ? el.getOpacity() : 0.08);
        opacSlider.setTooltip(new Tooltip("Watermark transparency level"));
        opacSlider.valueProperty().addListener((obs, o, v) -> { el.setOpacity(v.doubleValue()); refreshCanvas(); });

        Slider rotSlider = new Slider(-90, 90, el.getRotation() != 0 ? el.getRotation() : -30);
        rotSlider.setTooltip(new Tooltip("Watermark diagonal tilt angle in degrees"));
        rotSlider.valueProperty().addListener((obs, o, v) -> { el.setRotation(v.doubleValue()); refreshCanvas(); });

        Node colNode = createColorPickerButton(el.getColor() != null ? el.getColor() : "#000000", hex -> {
            el.setColor(hex);
            refreshCanvas();
        });

        g.add(new Label("Text:"), 0, 0); g.add(wmField, 1, 0);
        g.add(new Label("Opacity:"), 0, 1); g.add(opacSlider, 1, 1);
        g.add(new Label("Angle (°):"), 0, 2); g.add(rotSlider, 1, 2);
        g.add(new Label("Color:"), 0, 3); g.add(colNode, 1, 3);

        sec.getChildren().addAll(title, g);
        addPropertyNode(sec);
    }

    private void buildEffectsAndTransformsProperties(TemplateElement el) {
        TitledPane fxPane = new TitledPane();
        fxPane.setText("Effects & Transforms");
        fxPane.setExpanded(false);

        VBox fxBox = new VBox(8);
        fxBox.setPadding(new Insets(8));

        // Opacity
        Label opacLbl = new Label(String.format("Opacity (%.0f%%):", el.getOpacity() * 100));
        Slider opacSlider = new Slider(0.0, 1.0, el.getOpacity());
        opacSlider.setTooltip(new Tooltip("Element layer opacity (0% to 100%)"));
        opacSlider.valueProperty().addListener((obs, o, v) -> {
            el.setOpacity(v.doubleValue());
            opacLbl.setText(String.format("Opacity (%.0f%%):", v.doubleValue() * 100));
            refreshCanvas();
        });
        fxBox.getChildren().addAll(opacLbl, opacSlider);

        // Rotation & Scale
        GridPane transGrid = new GridPane();
        transGrid.setHgap(8); transGrid.setVgap(6);

        Spinner<Double> rotSpin = new Spinner<>(-360.0, 360.0, el.getRotation(), 5.0);
        rotSpin.setTooltip(new Tooltip("Rotation angle in degrees (-360° to +360°)"));
        configureNumberSpinner(rotSpin);
        geoRotSpin = rotSpin;
        rotSpin.valueProperty().addListener((obs, o, v) -> {
            if (!updatingProperties && v != null) {
                el.setRotation(v);
                updateElementVisualInPlace(el);
                if (activeSelectionBox != null) {
                    activeSelectionBox.setRotate(v);
                }
            }
        });

        Spinner<Double> sxSpin = new Spinner<>(0.1, 5.0, el.getScaleX() > 0 ? el.getScaleX() : 1.0, 0.1);
        sxSpin.setTooltip(new Tooltip("Horizontal scaling factor (1.0 = 100%)"));
        configureNumberSpinner(sxSpin);
        sxSpin.valueProperty().addListener((obs, o, v) -> { el.setScaleX(v); refreshCanvas(); });

        Spinner<Double> sySpin = new Spinner<>(0.1, 5.0, el.getScaleY() > 0 ? el.getScaleY() : 1.0, 0.1);
        sySpin.setTooltip(new Tooltip("Vertical scaling factor (1.0 = 100%)"));
        configureNumberSpinner(sySpin);
        sySpin.valueProperty().addListener((obs, o, v) -> { el.setScaleY(v); refreshCanvas(); });

        CheckBox flipH = new CheckBox("Flip Horizontal");
        flipH.setSelected(el.isFlipHorizontal());
        flipH.setTooltip(new Tooltip("Mirror element horizontally"));
        flipH.selectedProperty().addListener((obs, o, v) -> { el.setFlipHorizontal(v); refreshCanvas(); });

        CheckBox flipV = new CheckBox("Flip Vertical");
        flipV.setSelected(el.isFlipVertical());
        flipV.setTooltip(new Tooltip("Mirror element vertically"));
        flipV.selectedProperty().addListener((obs, o, v) -> { el.setFlipVertical(v); refreshCanvas(); });

        transGrid.add(new Label("Rotation (°):"), 0, 0); transGrid.add(rotSpin, 1, 0);
        transGrid.add(new Label("Scale X:"), 0, 1); transGrid.add(sxSpin, 1, 1);
        transGrid.add(new Label("Scale Y:"), 0, 2); transGrid.add(sySpin, 1, 2);
        transGrid.add(flipH, 0, 3); transGrid.add(flipV, 1, 3);

        fxBox.getChildren().add(transGrid);

        // Drop Shadow
        CheckBox shadowCb = new CheckBox("Enable Drop Shadow");
        shadowCb.setSelected(el.isShadowEnabled());
        shadowCb.setTooltip(new Tooltip("Render a drop shadow behind this element"));
        shadowCb.selectedProperty().addListener((obs, o, v) -> { el.setShadowEnabled(v); refreshCanvas(); });

        Node shadowCol = createColorPickerButton(el.getShadowColor() != null ? el.getShadowColor() : "#000000", hex -> {
            el.setShadowColor(hex);
            refreshCanvas();
        });

        Spinner<Double> blurSpin = new Spinner<>(0.0, 50.0, el.getShadowBlur() > 0 ? el.getShadowBlur() : 4.0, 1.0);
        blurSpin.setTooltip(new Tooltip("Shadow blur softness radius in pixels"));
        configureNumberSpinner(blurSpin);
        blurSpin.valueProperty().addListener((obs, o, v) -> { el.setShadowBlur(v); refreshCanvas(); });

        Spinner<Double> offXSpin = new Spinner<>(-50.0, 50.0, el.getShadowOffsetX(), 1.0);
        offXSpin.setTooltip(new Tooltip("Horizontal shadow offset in pixels"));
        configureNumberSpinner(offXSpin);
        offXSpin.valueProperty().addListener((obs, o, v) -> { el.setShadowOffsetX(v); refreshCanvas(); });

        Spinner<Double> offYSpin = new Spinner<>(-50.0, 50.0, el.getShadowOffsetY(), 1.0);
        offYSpin.setTooltip(new Tooltip("Vertical shadow offset in pixels"));
        configureNumberSpinner(offYSpin);
        offYSpin.valueProperty().addListener((obs, o, v) -> { el.setShadowOffsetY(v); refreshCanvas(); });

        GridPane shadowGrid = new GridPane();
        shadowGrid.setHgap(8); shadowGrid.setVgap(6);
        shadowGrid.add(shadowCb, 0, 0, 2, 1);
        shadowGrid.add(new Label("Color:"), 0, 1); shadowGrid.add(shadowCol, 1, 1);
        shadowGrid.add(new Label("Blur Radius:"), 0, 2); shadowGrid.add(blurSpin, 1, 2);
        shadowGrid.add(new Label("Offset X:"), 0, 3); shadowGrid.add(offXSpin, 1, 3);
        shadowGrid.add(new Label("Offset Y:"), 0, 4); shadowGrid.add(offYSpin, 1, 4);

        fxBox.getChildren().add(shadowGrid);

        // Clipping
        CheckBox clipCb = new CheckBox("Clip to Container");
        clipCb.setSelected(el.isClipEnabled());
        clipCb.setTooltip(new Tooltip("Clip element rendering inside a geometric shape"));
        clipCb.selectedProperty().addListener((obs, o, v) -> { el.setClipEnabled(v); refreshCanvas(); });

        ComboBox<String> clipShape = new ComboBox<>(FXCollections.observableArrayList("RECTANGLE", "CIRCLE", "ROUNDED_RECT"));
        clipShape.setValue(el.getClipShape() != null ? el.getClipShape() : "RECTANGLE");
        clipShape.setTooltip(new Tooltip("Clipping container shape"));
        clipShape.valueProperty().addListener((obs, o, v) -> { el.setClipShape(v); refreshCanvas(); });

        Button clipHelpBtn = new Button("?");
        clipHelpBtn.setStyle("-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #FBBF24; -fx-font-weight: bold; -fx-background-radius: 12; -fx-min-width: 24; -fx-pref-width: 24; -fx-min-height: 24; -fx-pref-height: 24; -fx-cursor: hand; -fx-border-color: rgba(245, 158, 11, 0.4); -fx-border-radius: 12; -fx-font-size: 11px;");
        clipHelpBtn.setTooltip(new Tooltip("Learn how Clip to Container works (Guide & Examples)"));
        clipHelpBtn.setOnAction(e -> showClipHelpDialog());

        HBox clipRow = new HBox(8, clipCb, clipShape, clipHelpBtn);
        clipRow.setAlignment(Pos.CENTER_LEFT);
        fxBox.getChildren().add(clipRow);

        fxPane.setContent(fxBox);
        addPropertyNode(fxPane);
    }

    private void showClipHelpDialog() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Clip to Container — Guide & Examples");
        DialogHelper.styleDialog(dlg, 540, 520);

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        VBox headerBox = new VBox(4);
        Label headerTitle = new Label("✂ Clip to Container");
        headerTitle.getStyleClass().add("card-title");
        headerTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FBBF24;");

        Label headerSub = new Label("Constrain element rendering and sub-content strictly inside a geometric boundary.");
        headerSub.getStyleClass().add("text-muted");
        headerSub.setWrapText(true);
        headerBox.getChildren().addAll(headerTitle, headerSub);

        VBox body = new VBox(12);
        body.getChildren().addAll(
            createClipHelpSection("What is Clipping?",
                "When enabled, any visual drawing that extends beyond the element bounds is neatly masked off. It applies identically on canvas and in high-resolution PDF exports."),
            createClipHelpSection("Available Shapes:",
                "• RECTANGLE: Masks strictly to element width and height (useful for overflowing text or tables).\n" +
                "• CIRCLE: Masks the element inside an ellipse/circle centered at element bounds (perfect for circular company logos, user avatars, or round badges).\n" +
                "• ROUNDED_RECT: Clips with smooth corner curvature matching the element's Border Radius."),
            createClipHelpSection("Practical Examples:",
                "1. Circular Business Logo: Add an Image element with your logo, turn on 'Clip to Container', and choose 'CIRCLE'.\n" +
                "2. Clean Rounded Badges: Create a colored rectangle with text or barcode, enable clipping as 'ROUNDED_RECT' to ensure child highlights don't bleed outside rounded corners.\n" +
                "3. Table / Text Overflow Protection: Prevent lengthy variable descriptions or table rows from spilling beyond allocated container boundaries.")
        );

        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 4 0;");

        content.getChildren().addAll(headerBox, new Separator(), sp);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
    }

    private VBox createClipHelpSection(String title, String desc) {
        VBox sec = new VBox(4);
        Label t = new Label(title);
        t.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0; -fx-font-size: 12px;");
        Label d = new Label(desc);
        d.setWrapText(true);
        d.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px; -fx-line-spacing: 2px;");
        sec.getChildren().addAll(t, d);
        return sec;
    }

    private void buildDataBindingProperties(TemplateElement el) {
        TitledPane bindPane = new TitledPane();
        bindPane.setText("Data Binding & Conditions");
        bindPane.setExpanded(false);

        VBox contentBox = new VBox(8);
        contentBox.setPadding(new Insets(8));

        // Help & Syntax Guide banner with rich hover tooltip
        HBox helpBanner = new HBox(6);
        helpBanner.setAlignment(Pos.CENTER_LEFT);
        helpBanner.setStyle("-fx-background-color: rgba(59, 130, 246, 0.12); -fx-border-color: rgba(59, 130, 246, 0.35); -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 10;");

        Label helpIcon = new Label("ⓘ");
        helpIcon.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        Label helpText = new Label("How Data Binding works (Hover to view guide)");
        helpText.setStyle("-fx-font-size: 11px; -fx-text-fill: #93C5FD; -fx-cursor: hand; -fx-font-weight: bold;");

        String guideTooltip = """
                DATA BINDING GUIDE & SYNTAX REFERENCE:
                ─────────────────────────────────────────────────────────────
                1. Text Ingestion & Placeholders:
                   Embed variables directly inside any text object:
                   • {{invoice_no}}       - Invoice / Bill serial number
                   • {{invoice_date}}     - Date of invoice issue
                   • {{due_date}}         - Payment due date
                   • {{buyer_name}}       - Customer / Client legal name
                   • {{buyer_gstin}}      - Customer GST identification number
                   • {{grand_total}}      - Total bill amount payable
                   • {{due_amount}}       - Remaining balance due
                   • {{seller_bank_name}} - Business bank name for payment
                   • {{seller_acc_no}}    - Business bank account number
                   • {{seller_ifsc}}      - Business IFSC branch code

                2. Binding Path (Direct Model Field Access):
                   Binds this entire object directly to a data property:
                   • invoice.number, invoice.date, invoice.total, invoice.balance
                   • buyer.name, buyer.gstin, buyer.phone, buyer.address
                   • seller.name, seller.gstin, seller.bankName, seller.accountNo
                   • meta.pageNumber, meta.totalPages, meta.printDate

                3. Visible Condition (Conditional Logic):
                   Only render this element when the condition is TRUE:
                   • invoice.balance > 0      (Show 'UNPAID / BALANCE' stamp only if due)
                   • buyer.gstin != null      (Show B2B Tax details only if GSTIN present)
                   • invoice.taxTotal > 0     (Show Tax breakdown table only when taxed)
                   • seller.state == buyer.state (Show Intra-state CGST/SGST note)
                ─────────────────────────────────────────────────────────────""";

        Tooltip guideTip = new Tooltip(guideTooltip);
        guideTip.setStyle("-fx-font-size: 11px; -fx-font-family: 'Consolas', monospace; -fx-background-color: #0F172A; -fx-text-fill: #E2E8F0; -fx-border-color: #38BDF8; -fx-border-width: 1px; -fx-padding: 8;");
        guideTip.setShowDelay(javafx.util.Duration.millis(80));
        guideTip.setShowDuration(javafx.util.Duration.seconds(30));
        Tooltip.install(helpBanner, guideTip);

        helpBanner.getChildren().addAll(helpIcon, helpText);

        GridPane g = new GridPane();
        g.setHgap(8); g.setVgap(6);

        TextField bindField = new TextField(el.getBinding() != null ? el.getBinding() : "");
        bindField.setPromptText("e.g. buyer.name or invoice.number");
        bindField.setTooltip(new Tooltip("Direct context binding path (e.g. invoice.number, buyer.name, seller.bankName)"));
        bindField.textProperty().addListener((obs, o, v) -> el.setBinding(v));

        TextField condField = new TextField(el.getVisibleCondition() != null ? el.getVisibleCondition() : "");
        condField.setPromptText("e.g. invoice.balance > 0");
        condField.setTooltip(new Tooltip("Conditional expression: element renders only if condition is true (e.g. invoice.balance > 0)"));
        condField.textProperty().addListener((obs, o, v) -> el.setVisibleCondition(v));

        g.add(new Label("Binding Path:"), 0, 0); g.add(bindField, 1, 0);
        g.add(new Label("Visible Condition:"), 0, 1); g.add(condField, 1, 1);

        contentBox.getChildren().addAll(helpBanner, g);
        bindPane.setContent(contentBox);
        addPropertyNode(bindPane);
    }

    private void addComponent(ComponentPreset.PresetType type) {
        double startY = 30.0;
        if (template.getElements() != null && !template.getElements().isEmpty()) {
            double maxY = 0;
            for (TemplateElement e : template.getElements()) {
                if (e != null) maxY = Math.max(maxY, e.getY() + e.getH());
            }
            if (maxY < 220 && maxY > 10) {
                startY = maxY + 5.0;
            }
        }
        List<TemplateElement> compElements = ComponentPreset.createComponent(type, 15.0, startY);
        for (TemplateElement e : compElements) {
            template.getElements().add(e);
        }
        if (!compElements.isEmpty()) {
            selectedElement = compElements.get(0);
        }
        saveState();
        refreshCanvas();
        updatePropertiesPanel();
        refreshLayersList();
        Toast.show(app.getRootPane(), "Component Added", "Added " + type.getTitle() + " (" + compElements.size() + " elements)", false);
    }

    private void rebuildComponentsMenu() {
        compMenu.getItems().clear();

        MenuItem headerPreset = new MenuItem("── Built-in Presets ──");
        headerPreset.setDisable(true);
        compMenu.getItems().add(headerPreset);

        for (ComponentPreset.PresetType type : ComponentPreset.PresetType.values()) {
            MenuItem item = new MenuItem(type.getIcon() + "  " + type.getTitle());
            item.setOnAction(e -> addComponent(type));
            compMenu.getItems().add(item);
        }

        compMenu.getItems().add(new SeparatorMenuItem());

        MenuItem headerCustom = new MenuItem("── Saved Custom Components ──");
        headerCustom.setDisable(true);
        compMenu.getItems().add(headerCustom);

        List<CustomComponent> customList = CustomComponentManager.loadComponents();
        if (customList.isEmpty()) {
            MenuItem emptyItem = new MenuItem("(No custom components saved yet)");
            emptyItem.setDisable(true);
            compMenu.getItems().add(emptyItem);
        } else {
            for (CustomComponent cc : customList) {
                MenuItem ccItem = new MenuItem("🧩  " + cc.getName() + " (" + (cc.getElements() != null ? cc.getElements().size() : 0) + " items)");
                ccItem.setOnAction(e -> addCustomComponent(cc));
                compMenu.getItems().add(ccItem);
            }
        }

        compMenu.getItems().add(new SeparatorMenuItem());
        MenuItem saveCustomItem = new MenuItem("💾  Save Selected as Component...");
        saveCustomItem.setOnAction(e -> saveSelectionAsComponent());
        compMenu.getItems().add(saveCustomItem);
    }

    private void addCustomComponent(CustomComponent cc) {
        if (cc == null || cc.getElements() == null || cc.getElements().isEmpty()) return;
        double startY = 30.0;
        if (template.getElements() != null && !template.getElements().isEmpty()) {
            double maxY = 0;
            for (TemplateElement e : template.getElements()) {
                if (e != null) maxY = Math.max(maxY, e.getY() + e.getH());
            }
            if (maxY < 220 && maxY > 10) {
                startY = maxY + 5.0;
            }
        }
        List<TemplateElement> compElements = CustomComponentManager.instantiateComponent(cc, 15.0, startY);
        for (TemplateElement e : compElements) {
            template.getElements().add(e);
        }
        if (!compElements.isEmpty()) {
            selectedElement = compElements.get(0);
        }
        saveState();
        refreshCanvas();
        updatePropertiesPanel();
        refreshLayersList();
        Toast.show(app.getRootPane(), "Component Added", "Added \"" + cc.getName() + "\" (" + compElements.size() + " elements)", false);
    }

    private void saveSelectionAsComponent() {
        List<TemplateElement> toSave = new ArrayList<>();
        if (selectedElement != null) {
            if (selectedElement.isGrouped()) {
                for (TemplateElement el : template.getElements()) {
                    if (Objects.equals(el.getGroupId(), selectedElement.getGroupId())) {
                        toSave.add(el);
                    }
                }
            } else {
                ObservableList<TemplateElement> selectedLayers = layersList.getSelectionModel().getSelectedItems();
                if (selectedLayers != null && selectedLayers.size() > 1) {
                    toSave.addAll(selectedLayers);
                } else {
                    toSave.add(selectedElement);
                }
            }
        } else {
            ObservableList<TemplateElement> selectedLayers = layersList.getSelectionModel().getSelectedItems();
            if (selectedLayers != null && !selectedLayers.isEmpty()) {
                toSave.addAll(selectedLayers);
            }
        }

        if (toSave.isEmpty()) {
            Toast.show(app.getRootPane(), "Save Component", "Please select an element, group, or multiple layers to save as a component.", true);
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Save as Custom Component");
        dialog.setHeaderText("Save " + toSave.size() + " element(s) as reusable component:");
        if (getScene() != null && getScene().getWindow() != null) {
            dialog.initOwner(getScene().getWindow());
        }

        VBox box = new VBox(10);
        box.setPadding(new Insets(16));
        box.setPrefWidth(360);

        Label nameLbl = new Label("Component Name:");
        nameLbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-weight: bold;");
        TextField nameInput = new TextField(selectedElement != null && selectedElement.getGroupName() != null ? selectedElement.getGroupName() : "My Custom Block");

        Label descLbl = new Label("Description (optional):");
        descLbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-weight: bold;");
        TextField descInput = new TextField();
        descInput.setPromptText("e.g. Header with logo and metadata");

        box.getChildren().addAll(nameLbl, nameInput, descLbl, descInput);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                String name = nameInput.getText().trim();
                if (name.isEmpty()) name = "Untitled Component";
                String desc = descInput.getText().trim();
                try {
                    CustomComponentManager.saveComponent(name, desc, toSave);
                    rebuildComponentsMenu();
                    Toast.show(app.getRootPane(), "Component Saved", "Saved \"" + name + "\" to custom components.", false);
                } catch (Exception ex) {
                    Toast.show(app.getRootPane(), "Save Error", "Failed to save component: " + ex.getMessage(), true);
                }
            }
        });
    }

    private void groupSelected() {
        ObservableList<TemplateElement> selectedItems = layersList.getSelectionModel().getSelectedItems();
        List<TemplateElement> toGroup = new ArrayList<>();
        if (selectedItems != null && selectedItems.size() > 1) {
            toGroup.addAll(selectedItems);
        } else if (selectedElement != null) {
            toGroup.add(selectedElement);
        }

        if (toGroup.isEmpty()) {
            Toast.show(app.getRootPane(), "Group", "Select two or more elements in Layers or on Canvas to group.", true);
            return;
        }

        String defaultName = "Group " + (template.getElements().stream().map(TemplateElement::getGroupId).filter(Objects::nonNull).distinct().count() + 1);
        TextInputDialog d = new TextInputDialog(toGroup.size() == 1 && toGroup.get(0).getGroupName() != null ? toGroup.get(0).getGroupName() : defaultName);
        d.setTitle("Create Group");
        d.setHeaderText("Group " + toGroup.size() + " element(s) together:");
        d.setContentText("Group Name:");
        if (getScene() != null && getScene().getWindow() != null) {
            d.initOwner(getScene().getWindow());
        }

        Optional<String> res = d.showAndWait();
        if (res.isPresent()) {
            String gname = res.get().trim().isEmpty() ? defaultName : res.get().trim();
            String newGid = UUID.randomUUID().toString();
            for (TemplateElement elem : toGroup) {
                elem.setGroupId(newGid);
                elem.setGroupName(gname);
            }
            saveState();
            refreshCanvas();
            refreshLayersList();
            updatePropertiesPanel();
            Toast.show(app.getRootPane(), "Grouped", "Grouped " + toGroup.size() + " elements as \"" + gname + "\".", false);
        }
    }

    private void ungroupSelected() {
        ObservableList<TemplateElement> selectedItems = layersList.getSelectionModel().getSelectedItems();
        List<TemplateElement> targets = new ArrayList<>();
        if (selectedItems != null && !selectedItems.isEmpty()) {
            targets.addAll(selectedItems);
        }
        if (targets.isEmpty() && selectedElement != null) {
            targets.add(selectedElement);
        }
        if (targets.isEmpty()) return;

        Set<String> affectedGroups = new HashSet<>();
        for (TemplateElement elem : targets) {
            if (elem.isGrouped()) {
                affectedGroups.add(elem.getGroupId());
            }
        }

        if (affectedGroups.isEmpty()) {
            Toast.show(app.getRootPane(), "Ungroup", "Selected element(s) are not part of any group.", true);
            return;
        }

        int count = 0;
        for (TemplateElement elem : template.getElements()) {
            if (elem.getGroupId() != null && affectedGroups.contains(elem.getGroupId())) {
                elem.setGroupId(null);
                elem.setGroupName(null);
                count++;
            }
        }

        saveState();
        refreshCanvas();
        refreshLayersList();
        updatePropertiesPanel();
        Toast.show(app.getRootPane(), "Ungrouped", "Ungrouped " + count + " elements.", false);
    }

    private void toggleAllVisibility() {
        if (template.getElements().isEmpty()) return;
        boolean anyVisible = template.getElements().stream().anyMatch(e -> !e.isHidden());
        for (TemplateElement el : template.getElements()) {
            el.setHidden(anyVisible);
        }
        saveState();
        refreshCanvas();
        refreshLayersList();
        updateSelectionOverlay();
    }

    private String getTypeGlyph(TemplateElement item) {
        if (item == null || item.getType() == null) return "EL";
        return switch (item.getType()) {
            case TEXT, PAGENO -> "TXT";
            case TABLE -> "TBL";
            case IMAGE -> "IMG";
            case RECT, CIRCLE, ELLIPSE, STAR, POLYGON, ARROW, ARC -> "SHP";
            case LINE, DIVIDER -> "LIN";
            case SVG, PATH -> "SVG";
            case QRCODE -> "QR";
            case BARCODE -> "BAR";
            case ICON -> "ICO";
            case WATERMARK -> "WM";
            case FREEHAND -> "SIG";
            default -> "OBJ";
        };
    }

    private String getTypeBadgeBg(TemplateElement item) {
        if (item == null || item.getType() == null) return "rgba(148, 163, 184, 0.2)";
        return switch (item.getType()) {
            case TEXT, PAGENO -> "rgba(59, 130, 246, 0.25)";
            case TABLE -> "rgba(16, 185, 129, 0.25)";
            case IMAGE -> "rgba(236, 72, 153, 0.25)";
            case RECT, CIRCLE, ELLIPSE, STAR, POLYGON, ARROW, ARC -> "rgba(217, 161, 59, 0.25)";
            case LINE, DIVIDER -> "rgba(139, 92, 246, 0.25)";
            case SVG, PATH -> "rgba(6, 182, 212, 0.25)";
            case QRCODE, BARCODE -> "rgba(249, 115, 22, 0.25)";
            default -> "rgba(100, 116, 139, 0.25)";
        };
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
        updateElementVisualInPlace(el);
        updatePropertiesPanel();
    }

    // ─── Column Picker helpers ─────────────────────────────────────────────────

    /** Inner record describing one available table column option. */
    private record ColumnOption(String key, String label, String group,
                                double defaultWidth, String defaultAlign) {
        @Override public String toString() { return label + "  (" + key + ")"; }
    }

    /**
     * Returns an ordered list of all available column keys for the per-row
     * keyCombo (built-ins first, then user table-scope custom vars).
     */
    private ObservableList<String> buildColumnKeyList() {
        return FXCollections.observableArrayList(cachedColumnKeys);
    }

    /** Maps a raw column key to a human-readable display label without repeated SQL calls. */
    private String columnKeyToLabel(String key) {
        if (key == null) return "";
        String lower = key.toLowerCase().trim();
        return switch (lower) {
            case "sr", "index", "#", "s_no", "sno" -> "Sr. No. (" + key + ")";
            case "desc", "description", "name", "item_name" -> "Description (" + key + ")";
            case "hsn", "sac", "hsn_sac"             -> "HSN / SAC (" + key + ")";
            case "qty", "quantity"                    -> "Quantity (" + key + ")";
            case "unit"                               -> "Unit (" + key + ")";
            case "rate", "price", "unit_price"        -> "Rate / Price (" + key + ")";
            case "gst", "tax"                         -> "GST % (" + key + ")";
            case "disc", "discount"                   -> "Discount % (" + key + ")";
            case "taxable", "taxable_value"           -> "Taxable Value (" + key + ")";
            case "amount", "total", "total_amount"    -> "Amount (" + key + ")";
            case "batch_no"                           -> "Batch No. (" + key + ")";
            case "exp_date"                           -> "Expiry Date (" + key + ")";
            case "mrp"                                -> "MRP (" + key + ")";
            case "serial_no"                          -> "Serial No. (" + key + ")";
            case "part_no"                            -> "Part No. (" + key + ")";
            default -> {
                String cached = cachedVariableLabels.get(lower);
                yield cached != null ? cached : key;
            }
        };
    }

    /**
     * Opens the Column Picker dialog for the given table element.
     * Shows all available columns grouped by category. Columns already present
     * in the table are shown but disabled (de-dup enforcement).
     */
    private void showColumnPickerDialog(TemplateElement el) {
        // ── Build full column catalog ──
        List<ColumnOption> catalog = new java.util.ArrayList<>();
        // Core columns
        catalog.add(new ColumnOption("sr",      "Sr. No.",         "Core",   7,  "center"));
        catalog.add(new ColumnOption("desc",    "Description",     "Core",  37,  "left"));
        catalog.add(new ColumnOption("hsn",     "HSN / SAC",       "Core",  12,  "center"));
        catalog.add(new ColumnOption("qty",     "Quantity",        "Core",   9,  "right"));
        catalog.add(new ColumnOption("unit",    "Unit",            "Core",   9,  "center"));
        catalog.add(new ColumnOption("rate",    "Rate / Price",    "Core",  12,  "right"));
        catalog.add(new ColumnOption("gst",     "GST %",           "Core",   7,  "right"));
        catalog.add(new ColumnOption("disc",    "Discount %",      "Core",   7,  "right"));
        catalog.add(new ColumnOption("taxable", "Taxable Value",   "Core",  12,  "right"));
        catalog.add(new ColumnOption("amount",  "Amount",          "Core",  14,  "right"));
        // Common custom columns
        catalog.add(new ColumnOption("batch_no",   "Batch No.",    "Common",  12, "center"));
        catalog.add(new ColumnOption("exp_date",   "Expiry Date",  "Common",  12, "center"));
        catalog.add(new ColumnOption("mrp",        "MRP",          "Common",  10, "right"));
        catalog.add(new ColumnOption("serial_no",  "Serial No.",   "Common",  12, "left"));
        catalog.add(new ColumnOption("part_no",    "Part No.",     "Common",  12, "left"));
        // User-defined table-scope custom variables
        for (VariableDef v : cachedTableScopeVariables) {
            if (v != null && v.getKey() != null) {
                boolean already = catalog.stream().anyMatch(o -> o.key().equals(v.getKey()));
                if (!already) {
                    catalog.add(new ColumnOption(v.getKey(), v.getLabel(), "Your Custom", 12, "left"));
                }
            }
        }

        // ── Keys already in the table (for de-dup) ──
        java.util.Set<String> usedKeys = new java.util.HashSet<>();
        if (el.getColumns() != null) {
            for (TableColumn tc : el.getColumns()) {
                if (tc.getKey() != null) usedKeys.add(tc.getKey().toLowerCase().trim());
            }
        }

        // ── Build picker UI ──
        Dialog<ColumnOption> dlg = new Dialog<>();
        dlg.setTitle("Add Table Column");
        DialogHelper.styleDialog(dlg, 480, 520);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));

        VBox headerBox = new VBox(4);
        Label headerTitle = new Label("Add Table Column");
        headerTitle.getStyleClass().add("card-title");
        Label headerSub = new Label("Select a column to add to the table. Already-added columns are shown but cannot be re-added.");
        headerSub.getStyleClass().add("text-muted");
        headerSub.setWrapText(true);
        headerBox.getChildren().addAll(headerTitle, headerSub);

        TextField search = new TextField();
        search.setPromptText("Search columns…");

        ObservableList<ColumnOption> allItems = FXCollections.observableArrayList(catalog);
        FilteredList<ColumnOption> filtered = new FilteredList<>(allItems, p -> true);
        search.textProperty().addListener((obs, o, v) -> {
            String q = v == null ? "" : v.trim().toLowerCase();
            filtered.setPredicate(opt -> q.isEmpty()
                || opt.label().toLowerCase().contains(q)
                || opt.key().toLowerCase().contains(q)
                || opt.group().toLowerCase().contains(q));
        });

        ListView<ColumnOption> lv = new ListView<>(filtered);
        lv.setPrefHeight(340);
        lv.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ColumnOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setDisable(false);
                    setOpacity(1.0);
                    return;
                }
                boolean used = usedKeys.contains(item.key().toLowerCase().trim());
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);

                // Group badge
                Label groupBadge = new Label(item.group());
                groupBadge.getStyleClass().add("kpi-subtext");
                groupBadge.setMinWidth(80);

                Label nameLbl = new Label(item.label());
                nameLbl.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(nameLbl, Priority.ALWAYS);

                Label keyLbl = new Label("{{" + item.key() + "}}");
                keyLbl.getStyleClass().add("code-pill");

                if (used) {
                    Label usedBadge = new Label("✓ Added");
                    usedBadge.getStyleClass().add("kpi-subtext");
                    row.getChildren().addAll(groupBadge, nameLbl, keyLbl, usedBadge);
                } else {
                    row.getChildren().addAll(groupBadge, nameLbl, keyLbl);
                }

                setGraphic(row);
                setText(null);
                setDisable(used);
                setOpacity(used ? 0.45 : 1.0);
            }
        });

        content.getChildren().addAll(headerBox, search, lv);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Disable OK if selection is null or already used
        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        lv.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            boolean disabled = sel == null || usedKeys.contains(sel.key().toLowerCase().trim());
            okBtn.setDisable(disabled);
        });

        dlg.setResultConverter(bt -> bt == ButtonType.OK ? lv.getSelectionModel().getSelectedItem() : null);
        dlg.showAndWait().ifPresent(opt -> {
            if (opt == null) return;
            TableColumn newCol = new TableColumn(opt.key(), opt.label(), opt.defaultWidth(), opt.defaultAlign());
            el.getColumns().add(newCol);
            saveState();
            updateElementVisualInPlace(el);
            updatePropertiesPanel();
        });
    }

    private String colorToHex(Color c) {
        return VectorGeometryUtil.colorToHex(c);
    }

    private Color hexToColor(String hex, Color def) {
        return VectorGeometryUtil.hexToColor(hex, def);
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
            com.invoicestudio.service.AppLog.error(ex);
        }
    }

    private void showVariablePicker(TextArea target, TemplateElement el) {
        try {
            Stage dlg = new Stage();
            if (app != null && app.getPrimaryStage() != null) {
                dlg.initOwner(app.getPrimaryStage());
            }
            DialogHelper.applyAppIcon(dlg); // logo in title bar from the very first frame
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
            // Grouped rows: section headers per category; filtering re-groups so
            // headers of empty groups disappear automatically while typing.
            ObservableList<VariableGrouper.Row> rows = FXCollections.observableArrayList(
                    VariableGrouper.group(vars));
            filterField.textProperty().addListener((obs, o, v) ->
                    rows.setAll(VariableGrouper.groupFiltered(vars, v)));

            ListView<VariableGrouper.Row> lv = new ListView<>(rows);
            lv.setPrefHeight(320);
            lv.getStyleClass().add("designer-list");
            lv.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(VariableGrouper.Row item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("var-group-header");
                    if (empty || item == null) {
                        if (!getStyleClass().contains("cell-clear")) getStyleClass().add("cell-clear");
                        setText(null);
                        setGraphic(null);
                        setDisable(false);
                        return;
                    }
                    if (item.isHeader()) {
                        if (!getStyleClass().contains("cell-clear")) getStyleClass().add("cell-clear");
                        getStyleClass().add("var-group-header");
                        setDisable(true); // section title — not clickable / not selectable
                        setText(item.header());
                        setGraphic(null);
                        return;
                    }
                    if (!getStyleClass().contains("cell-clear")) getStyleClass().add("cell-clear");
                    setDisable(false);
                    VariableDef vd = item.var();

                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(4, 6, 4, 6));
                    row.setMouseTransparent(true);

                    Label nameLbl = new Label(vd.getLabel() != null ? vd.getLabel() : vd.getKey());
                    nameLbl.getStyleClass().add("card-title-sm");
                    HBox.setHgrow(nameLbl, Priority.ALWAYS);

                    Label keyPill = new Label("{{" + vd.getKey() + "}}");
                    keyPill.getStyleClass().add("key-pill");

                    Label typeBadge = new Label(vd.getType() != null ? vd.getType().toUpperCase() : "GENERAL");
                    typeBadge.getStyleClass().add("type-badge");

                    row.getChildren().addAll(nameLbl, keyPill, typeBadge);
                    setGraphic(row);
                    setText(null);
                }
            });

            Runnable doInsert = () -> {
                VariableGrouper.Row sel = lv.getSelectionModel().getSelectedItem();
                VariableDef pick = (sel != null && !sel.isHeader()) ? sel.var() : null;
                if (pick == null) pick = VariableGrouper.firstItem(rows); // header selected / nothing selected
                if (pick != null && pick.getKey() != null) {
                    insertVariableIntoTarget(target, "{{" + pick.getKey() + "}}", el);
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
            com.invoicestudio.service.AppLog.error(ex);
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
        } catch (Exception ignored) {
            AppLog.debug(ignored); }

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
        map.put("transport_phone", new VariableDef("transport_phone", "Transporter Phone", "LOGISTICS", true));
        map.put("parcel", new VariableDef("parcel", "Parcel Count", "LOGISTICS", true));
        map.put("parcels", new VariableDef("parcels", "Parcels / Packages", "LOGISTICS", true));
        map.put("e_way_bill", new VariableDef("e_way_bill", "E-Way Bill Number", "LOGISTICS", true));

        // User custom variables from cached session load
        if (cachedAllVariables != null) {
            for (VariableDef uv : cachedAllVariables) {
                if (uv != null && uv.getKey() != null && !uv.getKey().isBlank()) {
                    map.putIfAbsent(uv.getKey(), uv);
                }
            }
        }
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

                    // Barcode Mode: mirror the L/R margins into the stock so
                    // the canvas blue line, strip preview & print agree.
                    if (template.isLabelMode()) {
                        LabelConfig lc = template.labelOrNew();
                        lc.setMarginL(newLeft);
                        lc.setMarginR(newRight);
                        lc.sanitize();
                    }

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
                    centerView(); // page dimensions changed — keep canvas centred
                    updatePropertiesPanel();
                    saveState();
                    Toast.show(app.getRootPane(), "Page Configured", "Page dimensions and margins updated.", false);
                } catch (Exception ex) {
                    Toast.show(app.getRootPane(), "Invalid Values", "Width, height, and margins must be valid numbers.", true);
                }
            }
        });
    }

    // ==================================================================
    // Barcode (label) mode — thermal strip stock, e.g. TSC TA210
    // ==================================================================

    private double preLabelPageW = -1;
    private double preLabelPageH = -1;
    /** Bill-mode margins remembered while Barcode Mode mirrors the stock's L/R
     *  margins into the page, restored when leaving Barcode Mode. */
    private double preLabelMarginT = -1, preLabelMarginB = -1, preLabelMarginL = -1, preLabelMarginR = -1;

    /** Enters / leaves Barcode Mode, remembering the bill page size across the switch. */
    private void toggleBarcodeMode() {
        if (template.isLabelMode()) {
            // Leave label mode → restore the bill page we came from
            template.setMode("bill");
            if (preLabelPageW > 0 && preLabelPageH > 0) {
                template.getPage().setWidth(preLabelPageW);
                template.getPage().setHeight(preLabelPageH);
                template.getPage().setSizeName(PageSizeName.CUSTOM);
            }
            if (preLabelMarginT >= 0) {
                PageConfig.Margins pm = template.getPage().getMargin();
                if (pm == null) {
                    pm = new PageConfig.Margins(preLabelMarginT, preLabelMarginR, preLabelMarginB, preLabelMarginL);
                    template.getPage().setMargin(pm);
                } else {
                    pm.setTop(preLabelMarginT);
                    pm.setRight(preLabelMarginR);
                    pm.setBottom(preLabelMarginB);
                    pm.setLeft(preLabelMarginL);
                }
                preLabelMarginT = preLabelMarginB = preLabelMarginL = preLabelMarginR = -1;
            }
        } else {
            template.setMode("label");
            preLabelPageW = template.getPage().getWidth();
            preLabelPageH = template.getPage().getHeight();
            PageConfig.Margins preM = template.getPage().getMargin();
            preLabelMarginT = preM.getTop();
            preLabelMarginB = preM.getBottom();
            preLabelMarginL = preM.getLeft();
            preLabelMarginR = preM.getRight();

            LabelConfig cfg = template.labelOrNew();
            // First entry: seed the label cell from the current canvas when it
            // already looks like a label, else fall back to a common 50×25 tag.
            if (cfg.getLabelWidth() <= 0 || cfg.getLabelHeight() <= 0) {
                cfg.setLabelWidth(Math.max(20, Math.min(120, template.getPage().getWidth())));
                cfg.setLabelHeight(Math.max(15, Math.min(120, template.getPage().getHeight())));
            }
            cfg.sanitize();

            if (template.getElements().isEmpty()) {
                TemplateElement item = new TemplateElement();
                item.setId("el_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
                item.setType(ElementType.TEXT);
                item.setName("Item Line");
                item.setX(3); item.setY(2); item.setW(cfg.getLabelWidth() - 6); item.setH(7);
                item.setText("{{item_name}}");
                item.setFontSize(9); item.setBold(true); item.setAlign("left");
                template.getElements().add(item);

                TemplateElement code = new TemplateElement();
                code.setId("el_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
                code.setType(ElementType.BARCODE);
                code.setName("Barcode");
                code.setX(3); code.setY(10); code.setW(cfg.getLabelWidth() - 6); code.setH(11);
                code.setBarcodeData("{{barcode}}");
                template.getElements().add(code);
            }
            Toast.show(app.getRootPane(), "Barcode Mode",
                    "Design ONE label cell now. Create variables like {{item_name}} under Catalog → Variables "
                    + "with scope 'Barcode Label' so Bulk Print can ask for their values.", false);
        }

        if (template.isLabelMode()) syncPageFromLabelConfig();
        styleBarcodeModeButton();
        updateLabelButtonsVisibility();
        selectedElement = null;
        saveState();
        refreshCanvas();
        centerView(); // page size changed (label cell ↔ bill page) — keep canvas in view
        updatePropertiesPanel();
        refreshLayersList();
    }

    private void styleBarcodeModeButton() {
        if (barcodeModeBtn == null) return;
        boolean active = template.isLabelMode();
        barcodeModeBtn.setText(active ? "Barcode Mode ✓" : "Barcode Mode");
        styleToolButton(barcodeModeBtn, active);
    }

    /** Ctrl+Shift+L landing point: jump into Barcode Mode on the template being designed. */
    public void enterBarcodeModeFromShortcut() {
        if (!template.isLabelMode()) {
            toggleBarcodeMode();
        } else {
            Toast.show(app.getRootPane(), "Barcode Mode", "Already designing a label — canvas equals one label cell.", false);
        }
    }

    /** Ctrl+Shift+B landing point: straight into the Bulk Label Print window. */
    public void openBulkPrintFromShortcut() {
        if (!template.isLabelMode()) {
            Toast.show(app.getRootPane(), "Bulk Print",
                    "Bulk Label Print works in Barcode Mode. Press Ctrl+Shift+L to switch this template first.", false);
            return;
        }
        showBulkPrintDialog();
    }

    private void updateLabelButtonsVisibility() {
        boolean label = template.isLabelMode();
        // Barcode Mode: "Page" and "Label Stock" opened the SAME dialog — keep
        // only Label Stock there (the page is just the label cell mirrored).
        if (pageSettingsBtn != null) { pageSettingsBtn.setVisible(!label); pageSettingsBtn.setManaged(!label); }
        if (labelSettingsBtn != null) { labelSettingsBtn.setVisible(label); labelSettingsBtn.setManaged(label); }
        if (stripPreviewBtn != null) { stripPreviewBtn.setVisible(label); stripPreviewBtn.setManaged(label); }
        if (bulkPrintBtn != null) { bulkPrintBtn.setVisible(label); bulkPrintBtn.setManaged(label); }
    }

    /** Canvas (page) always equals the label design cell while in Barcode Mode.
     *  The stock's L/R margins are mirrored into the page margins so the blue
     *  dashed printable boundary + legend on the canvas react immediately when
     *  the user edits them in the Label Stock dialog. */
    private void syncPageFromLabelConfig() {
        LabelConfig cfg = template.labelOrNew();
        cfg.sanitize();
        template.getPage().setWidth(cfg.getLabelWidth());
        template.getPage().setHeight(cfg.getLabelHeight());
        template.getPage().setSizeName(PageSizeName.CUSTOM);
        PageConfig.Margins pm = template.getPage().getMargin();
        if (pm == null) {
            pm = new PageConfig.Margins(0, 0, 0, 0);
            template.getPage().setMargin(pm);
        }
        pm.setLeft(cfg.getMarginL());
        pm.setRight(cfg.getMarginR());
        pm.setTop(0);
        pm.setBottom(0);
        updatePageFormatLabel();
    }

    /** Strip-stock settings dialog — the "barcode page setting" (hidden in normal mode).
     *  BarTender-style stock-first setup: you describe the PHYSICAL roll (label size as
     *  it sits on the strip, columns, gaps, margins) plus how the canvas artwork lands
     *  on it, and a live diagram mirrors every change — no more "why is my label 35 mm
     *  wide when the paper is 75?" orientation confusion. */
    private void showLabelSettingsDialog() {
        LabelConfig cfg = template.labelOrNew();
        cfg.sanitize();

        // Stock-first seeds: PHYSICAL label size (on the strip) + the artwork
        // rotation that maps the canvas design onto it. Round-trip safe: a
        // dialog opened and confirmed unchanged rewrites the exact same config.
        final String[] theta = {cfg.getOrientation()};
        double physW0 = LabelGeometryService.physicalCellWidth(cfg);
        double physH0 = LabelGeometryService.physicalCellHeight(cfg);

        Dialog<Boolean> dlg = new Dialog<>();
        DialogHelper.styleDialog(dlg);
        dlg.setTitle("Label Stock Settings");
        dlg.setHeaderText("Barcode Mode — Describe Your Label Roll");

        // What the printer knows by itself vs what must be declared (researched:
        // TSPL manual AUTODETECT/GAPDETECT p.6 + TSC/Seagull driver docs).
        Label explainer = new Label("Describe the roll exactly as it is — the picture mirrors every change. "
                + "The printer only senses where each label ENDS along the feed (gap sensor, after Calibrate "
                + "Sensor); everything across the strip — label size, columns, margins — is declared here.");
        explainer.setWrapText(true);
        explainer.getStyleClass().add("text-muted");

        // ── Live strip diagram + BarTender-style numbers caption ──
        Pane diagram = new Pane();
        diagram.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 8;");
        diagram.setPrefSize(332, 230);
        diagram.setMinSize(332, 230);
        diagram.setMaxSize(332, 230);
        diagram.setClip(new Rectangle(332, 230));
        Label caption = new Label();
        caption.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #D9A13B;");
        caption.setAlignment(Pos.CENTER);
        caption.setMaxWidth(Double.MAX_VALUE);
        caption.setWrapText(true);

        VBox content = new VBox(10);
        content.setPadding(new Insets(14));

        Spinner<Integer> colsSpin = new Spinner<>(1, 8, cfg.getColumns());
        colsSpin.setPrefWidth(90);
        Spinner<Double> physWSpin = new Spinner<>(10.0, 200.0, physW0, 0.5);
        configureNumberSpinner(physWSpin); physWSpin.setPrefWidth(90);
        Spinner<Double> physHSpin = new Spinner<>(10.0, 200.0, physH0, 0.5);
        configureNumberSpinner(physHSpin); physHSpin.setPrefWidth(90);
        Spinner<Double> gapXSpin = new Spinner<>(0.0, 40.0, cfg.getGapX(), 0.5);
        configureNumberSpinner(gapXSpin); gapXSpin.setPrefWidth(90);
        Spinner<Double> gapYSpin = new Spinner<>(0.0, 40.0, cfg.getGapY(), 0.5);
        configureNumberSpinner(gapYSpin); gapYSpin.setPrefWidth(90);
        Spinner<Double> cornerSpin = new Spinner<>(0.0, 12.0, cfg.getCornerRadius(), 0.5);
        configureNumberSpinner(cornerSpin); cornerSpin.setPrefWidth(90);
        Spinner<Double> mlSpin = new Spinner<>(0.0, 60.0, cfg.getMarginL(), 0.5);
        configureNumberSpinner(mlSpin); mlSpin.setPrefWidth(90);
        Spinner<Double> mrSpin = new Spinner<>(0.0, 60.0, cfg.getMarginR(), 0.5);
        configureNumberSpinner(mrSpin); mrSpin.setPrefWidth(90);
        Spinner<Double> stripWSpin = new Spinner<>(20.0, 300.0, cfg.getStripWidth(), 0.5);
        configureNumberSpinner(stripWSpin); stripWSpin.setPrefWidth(90);

        // Paper width auto-fits the layout (BarTender computes its paper size
        // the same way) — editing the spinner manually flips the flag off, so
        // existing templates with a hand-set liner width are never changed.
        CheckBox autoPaper = new CheckBox("Auto-fit paper width to labels + gaps + margins");
        boolean auto0 = Math.abs(cfg.getStripWidth()
                - LabelGeometryService.requiredStripWidth(cfg)) < 0.05;
        autoPaper.setSelected(auto0);

        // Human labels for the print-time artwork rotation (codes 0/90/180/270).
        // Presented as rich rows: a monospace angle chip + title + what it does
        // (the old single-line strings truncated and were hard to tell apart).
        final String[] orientLabels = {
                "Prints as designed",
                "Rotates 90° clockwise at print",
                "Rotates 180° at print",
                "Rotates 270° clockwise at print"};
        final String[] orientSubs = {
                "No rotation — artwork prints exactly as drawn",
                "Tall designs land sideways on a wide label",
                "Upside-down — for rolls fed from the other end",
                "90° counter-clockwise — wide designs on a tall label"};
        final String[] orientCodes = {"0", "90", "180", "270"};
        final String[] orientChips = {"0°", "90°", "180°", "270°"};
        ComboBox<String> orientCb = new ComboBox<>(FXCollections.observableArrayList(orientLabels));
        orientCb.setMaxWidth(Double.MAX_VALUE);
        orientCb.setCellFactory(lv -> new ListCell<>() {
            private final Label chip = new Label();
            private final VBox box = new VBox(1);
            {
                chip.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 11px; "
                        + "-fx-font-weight: bold; -fx-text-fill: #D9A13B; -fx-background-color: rgba(217,161,59,0.12);"
                        + "-fx-background-radius: 5; -fx-border-color: rgba(217,161,59,0.4); -fx-border-radius: 5;"
                        + "-fx-border-width: 1; -fx-padding: 3 7; -fx-alignment: center;");
                box.setStyle("-fx-background-color: transparent;");
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                int i = getIndex() < 0 ? 0 : getIndex();
                chip.setText(i >= 0 && i < orientChips.length ? orientChips[i] : "");
                Label t = new Label(item);
                t.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #E6EAF0;");
                Label s = new Label(i >= 0 && i < orientSubs.length ? orientSubs[i] : "");
                s.setStyle("-fx-font-size: 10px; -fx-text-fill: #97A3B6;");
                s.setWrapText(true);
                box.getChildren().setAll(new HBox(8, chip, t), s);
                setText(null);
                setGraphic(box);
            }
        });
        orientCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setStyle("-fx-font-size: 12px; -fx-text-fill: #E6EAF0;");
            }
        });
        int oi = Arrays.asList(orientCodes).indexOf(theta[0]);
        orientCb.getSelectionModel().select(Math.max(0, oi));

        // Stock type — the physical roll. Rich rows: a mini strip-diagram glyph
        // (three die-cut labels vs one continuous strip) + title + what the
        // printer does with it. Selection stays INDEX-based exactly as before.
        final String[] stockTitles = {
                "Gap — die-cut roll",
                "Continuous — no gaps"};
        final String[] stockSubs = {
                "Sensor finds each label end · standard pre-cut labels",
                "Receipt-style strip · the printer cuts by length"};
        final String[] stockGlyphs = {
                "M5 2 h14 v5.2 h-14 z M5 9.4 h14 v5.2 h-14 z M5 16.6 h14 v5.2 h-14 z", // three labels + gaps
                "M5 2 h14 v20 h-14 z"};                                                 // one endless strip
        ComboBox<String> stockCb = new ComboBox<>(FXCollections.observableArrayList(stockTitles));
        stockCb.setMaxWidth(Double.MAX_VALUE);
        stockCb.setCellFactory(lv -> new ListCell<>() {
            private final SVGPath glyph = new SVGPath();
            private final VBox box = new VBox(1);
            {
                glyph.setFill(Color.web("#D9A13B"));
                glyph.setStyle("-fx-scale-x: 0.75; -fx-scale-y: 0.75;");
                box.setStyle("-fx-background-color: transparent;");
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                int i = getIndex() < 0 ? 0 : getIndex();
                glyph.setContent(i >= 0 && i < stockGlyphs.length ? stockGlyphs[i] : "");
                Label t = new Label(item);
                t.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #E6EAF0;");
                Label s = new Label(i >= 0 && i < stockSubs.length ? stockSubs[i] : "");
                s.setStyle("-fx-font-size: 10px; -fx-text-fill: #97A3B6;");
                s.setWrapText(true);
                box.getChildren().setAll(new HBox(9, glyph, t), s);
                setText(null);
                setGraphic(box);
            }
        });
        stockCb.setButtonCell(new ListCell<>() {
            private final SVGPath glyph = new SVGPath();
            {
                glyph.setFill(Color.web("#D9A13B"));
                glyph.setStyle("-fx-scale-x: 0.7; -fx-scale-y: 0.7;");
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                int i = Math.max(0, getIndex()); // button cell index = selected index
                glyph.setContent(i < stockGlyphs.length ? stockGlyphs[i] : "");
                setText(item);
                setStyle("-fx-font-size: 12px; -fx-text-fill: #E6EAF0;");
                setGraphic(glyph);
            }
        });
        stockCb.getSelectionModel().select("continuous".equalsIgnoreCase(cfg.getStockType()) ? 1 : 0);

        Label needLbl = new Label();
        needLbl.getStyleClass().add("text-muted");

        // The number that decides whether one PRINT feeds ONE physical label
        // or several: the declared feed pitch (label height + gap) must equal
        // the roll's real label-to-label distance. The TSPL manual defines
        // PRINT's feed as exactly this declared length — a config that is a
        // multiple of the real pitch makes the printer output 2-3 die-cut
        // labels per record, only the first carrying content (the reported
        // "printed one, got three — last two empty").
        Label pitchLbl = new Label();
        pitchLbl.setWrapText(true);
        pitchLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #D9A13B;");

        Label artCaption = new Label();
        artCaption.setWrapText(true);
        artCaption.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        // ── TSC TA210 preset selector (gold-rectangle chips → compact combo) ──
        // Display format per user spec: [W×H] | L/R: xmm | Row Gap: ymm | Col Gap: zmm.
        // Selecting a preset FILLS the spinners (suggestions, never hard bindings);
        // all fields stay editable and custom sizes are validated against the
        // TA210 hardware envelope.
        final boolean[] applyingPreset = {false};
        ComboBox<LabelPresets.Preset> presetCb = new ComboBox<>(
                FXCollections.observableArrayList(LabelPresets.TA210));
        presetCb.setMaxWidth(Double.MAX_VALUE);
        presetCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(LabelPresets.Preset p, boolean empty) {
                super.updateItem(p, empty);
                if (p == null || empty) { setText(null); setGraphic(null); return; }
                VBox box = new VBox(1);
                Label spec = new Label(p.spec());
                spec.setStyle("-fx-font-size: 12px; -fx-text-fill: #E6EAF0; -fx-font-family: 'Consolas','Courier New',monospace;");
                box.getChildren().add(spec);
                if (p.note() != null && !p.note().isBlank()) {
                    Label note = new Label(p.note());
                    note.setStyle("-fx-font-size: 10px; -fx-text-fill: #97A3B6;");
                    box.getChildren().add(note);
                }
                setText(null); setGraphic(box);
            }
        });
        presetCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(LabelPresets.Preset p, boolean empty) {
                super.updateItem(p, empty);
                setText(p == null || empty ? "Preset label size — TSC TA210…" : p.spec());
                setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 12px;");
            }
        });
        presetCb.setPromptText("Preset label size — TSC TA210…");

        Label presetWarn = new Label();
        presetWarn.setWrapText(true);
        presetWarn.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        VBox presetSection = new VBox(6, presetCb, presetWarn);
        presetSection.setStyle("-fx-background-color: rgba(217,161,59,0.07); -fx-background-radius: 8;"
                + "-fx-border-color: rgba(217,161,59,0.45); -fx-border-radius: 8; -fx-border-width: 1;"
                + "-fx-padding: 8;");

        // Live recompute: derives the design canvas from the PHYSICAL spinners
        // + artwork rotation, auto-fits the paper width, refreshes every number
        // and redraws the strip diagram.
        final boolean[] syncingPaper = {false};
        Runnable upd = () -> {
            String code = theta[0];
            double pw = physWSpin.getValue(), ph = physHSpin.getValue();
            LabelConfig tmp = new LabelConfig();
            tmp.setOrientation(code);
            tmp.setLabelWidth(LabelGeometryService.designWidthFor(pw, ph, code));
            tmp.setLabelHeight(LabelGeometryService.designHeightFor(pw, ph, code));
            tmp.setColumns(colsSpin.getValue());
            tmp.setGapX(gapXSpin.getValue());
            tmp.setGapY(gapYSpin.getValue());
            tmp.setMarginL(mlSpin.getValue());
            tmp.setMarginR(mrSpin.getValue());
            tmp.setStockType(stockCb.getSelectionModel().getSelectedIndex() == 1 ? "continuous" : "gap");
            tmp.setStripWidth(stripWSpin.getValue());
            tmp.sanitize();
            if (autoPaper.isSelected()) {
                double need = LabelGeometryService.requiredStripWidth(tmp);
                syncingPaper[0] = true;
                stripWSpin.getValueFactory().setValue(Math.round(need * 10.0) / 10.0);
                syncingPaper[0] = false;
                tmp.setStripWidth(stripWSpin.getValue());
            }
            boolean fits = LabelGeometryService.fitsStrip(tmp);
            needLbl.setText(String.format(java.util.Locale.US,
                    "%s Labels need %.1f mm; paper is %.1f mm wide · feed pitch %.1f mm",
                    fits ? "✓" : "⚠", LabelGeometryService.requiredStripWidth(tmp),
                    tmp.getStripWidth(), LabelGeometryService.feedPitchMm(tmp)));
            needLbl.setStyle(fits ? "-fx-text-fill: #16a34a;" : "-fx-text-fill: #dc2626;");
            // Keep the selector in sync with hand edits: highlight the matching
            // preset, or blank it for custom dimensions.
            applyingPreset[0] = true;
            LabelPresets.Preset match = null;
            for (LabelPresets.Preset p : LabelPresets.TA210) {
                if (Math.abs(p.w() - pw) < 0.05 && Math.abs(p.h() - ph) < 0.05
                        && Math.abs(p.marginLR() - mlSpin.getValue()) < 0.05
                        && Math.abs(p.rowGap() - gapYSpin.getValue()) < 0.05
                        && Math.abs(p.colGap() - gapXSpin.getValue()) < 0.05) {
                    match = p; break;
                }
            }
            presetCb.setValue(match);
            applyingPreset[0] = false;
            // TA210 hardware-envelope validation for hand-typed dimensions.
            // The gap advisory only applies to CUSTOM dims — the curated
            // presets carry real-world commercial gaps (1.5/1.0/0.5 mm on
            // small labels) that must not warn.
            String hwErr = LabelPresets.validate(pw, ph);
            if (hwErr != null) {
                presetWarn.setText("⚠ " + hwErr);
                presetWarn.setStyle("-fx-font-size: 11px; -fx-text-fill: #dc2626;");
            } else if (match == null && tmp.getGapY() > 0 && tmp.getGapY() < LabelPresets.GAP_MIN_TYPICAL) {
                presetWarn.setText(String.format(java.util.Locale.US,
                        "Feed gap %.1f mm is below the typical 2 mm die-cut gap — check your roll.",
                        tmp.getGapY()));
                presetWarn.setStyle("-fx-font-size: 11px; -fx-text-fill: #D9A13B;");
            } else {
                presetWarn.setText("✓ Within TA210 media envelope (25.4–118 mm wide · 10–2794 mm long · 203 dpi)");
                presetWarn.setStyle("-fx-font-size: 11px; -fx-text-fill: #16a34a;");
            }
            caption.setText(String.format(java.util.Locale.US,
                    "Paper (liner) %.1f mm wide   ·   Label on strip %.1f × %.1f mm (W × H)   ·   Feed pitch %.1f mm/row",
                    tmp.getStripWidth(), pw, ph, LabelGeometryService.feedPitchMm(tmp)));
            double dw = tmp.getLabelWidth(), dh = tmp.getLabelHeight();
            switch (code) {
                case "90" -> artCaption.setText(String.format(java.util.Locale.US,
                        "Canvas designs %.1f × %.1f mm — at print the artwork rotates 90° clockwise "
                                + "into the %.1f × %.1f mm label above.", dw, dh, pw, ph));
                case "270" -> artCaption.setText(String.format(java.util.Locale.US,
                        "Canvas designs %.1f × %.1f mm — at print the artwork rotates 270° clockwise "
                                + "(90° counter-clockwise) into the %.1f × %.1f mm label above.", dw, dh, pw, ph));
                case "180" -> artCaption.setText(String.format(java.util.Locale.US,
                        "Canvas designs %.1f × %.1f mm — at print the artwork rotates 180° "
                                + "into the %.1f × %.1f mm label above.", dw, dh, pw, ph));
                default -> artCaption.setText(String.format(java.util.Locale.US,
                        "Canvas designs %.1f × %.1f mm — artwork prints as designed, no rotation.", dw, dh));
            }
            double cellH = LabelGeometryService.physicalCellHeight(tmp);
            double pitch = LabelGeometryService.feedPitchMm(tmp);
            int dpm = TsplPrintService.dotsPerMm((String) null);
            pitchLbl.setText(String.format(java.util.Locale.US,
                    "FEED PITCH — what the printer feeds per printed label: %.1f mm "
                    + "(%.1f mm label + %.1f mm gap = SIZE %d + GAP %d dots at %d dots/mm). "
                    + "This MUST equal the roll's label-to-label distance — measure one label + one gap with a ruler. "
                    + "If one record feeds several labels (first has content, the rest blank), this pitch is bigger "
                    + "than the roll's real pitch: fix Label Height / Feed Gap, or Rotate the design.",
                    pitch, cellH, Math.max(0, tmp.getGapY()),
                    (int) Math.round(cellH * dpm), (int) Math.round(tmp.getGapY() * dpm), dpm));
            drawStripDiagram(diagram, tmp);
        };

        // ── Modern card layout: grouped sections instead of a 4-column wall ──
        SettingsCard stockCard = new SettingsCard("STOCK — THE PHYSICAL ROLL");
        stockCard.add("Label width on strip (mm)", physWSpin);
        stockCard.add("Label height on strip (mm)", physHSpin);
        stockCard.add("Columns across", colsSpin);
        stockCard.add("Feed gap between rows (mm)", gapYSpin);
        stockCard.add("Gap between columns (mm)", gapXSpin);
        stockCard.add("Corner radius (mm)", cornerSpin);
        stockCard.box.getChildren().add(1, presetSection); // gold selector above the field grid

        SettingsCard linerCard = new SettingsCard("LINER, MARGINS & STOCK TYPE");
        linerCard.add("Paper (liner) width (mm)", stripWSpin);
        linerCard.addFull(autoPaper);
        linerCard.add("Stock type", stockCb);
        linerCard.add("Left margin (mm)", mlSpin);
        linerCard.add("Right margin (mm)", mrSpin);

        presetCb.valueProperty().addListener((obs, o, p) -> {
            if (p == null || applyingPreset[0]) return;
            // Fill W, H, L/R margin, row gap, col gap (spec behaviour) — fields
            // stay fully editable afterwards.
            physWSpin.getValueFactory().setValue(p.w());
            physHSpin.getValueFactory().setValue(p.h());
            mlSpin.getValueFactory().setValue(p.marginLR());
            mrSpin.getValueFactory().setValue(p.marginLR());
            gapYSpin.getValueFactory().setValue(p.rowGap());
            gapXSpin.getValueFactory().setValue(p.colGap());
            autoPaper.setSelected(true); // presets imply a fitted liner
            upd.run();
        });

        colsSpin.valueProperty().addListener((o, a, b) -> upd.run());
        for (Spinner<Double> s : List.of(physWSpin, physHSpin, gapXSpin, gapYSpin, mlSpin, mrSpin)) {
            s.valueProperty().addListener((o, a, b) -> upd.run());
        }
        stripWSpin.valueProperty().addListener((o, a, b) -> {
            if (!syncingPaper[0]) autoPaper.setSelected(false); // manual edit = manual width
            upd.run();
        });
        autoPaper.setOnAction(e -> upd.run());
        orientCb.setOnAction(e -> {
            theta[0] = orientCodes[Math.max(0, orientCb.getSelectionModel().getSelectedIndex())];
            upd.run();
        });
        upd.run();

        // ── ARTWORK card: how the canvas design lands on the physical label ──
        SettingsCard artCard = new SettingsCard("ARTWORK — CANVAS ON THE ROLL");
        artCard.add("Artwork direction", orientCb);
        Button rotBtn = new Button("↻  Rotate Design 90° into print orientation");
        rotBtn.getStyleClass().addAll("button-sm", "button-secondary");
        rotBtn.setMaxWidth(Double.MAX_VALUE);
        rotBtn.setTooltip(new Tooltip(
                "Spins the whole design 90° clockwise: label width/height swap, every element moves and "
                + "rotates with it, and Artwork direction resets to none — canvas, preview and print all match."));
        rotBtn.setOnAction(e -> {
            rotateLabelDesign90();
            // keep the stock-first editors consistent with the rotated design
            theta[0] = cfg.getOrientation(); // "0" after the bake
            physWSpin.getValueFactory().setValue(LabelGeometryService.physicalCellWidth(cfg));
            physHSpin.getValueFactory().setValue(LabelGeometryService.physicalCellHeight(cfg));
            orientCb.getSelectionModel().select(Math.max(0, Arrays.asList(orientCodes).indexOf(theta[0])));
            upd.run();
        });
        artCard.addFull(rotBtn);
        Label hint = new Label("The canvas is ONE label cell — the design size shown under Artwork direction. "
                + "The picture and Strip Preview always show the physical roll. Rotate the design for a true "
                + "WYSIWYG canvas: every element spins 90° and Artwork direction resets to none.");
        hint.setWrapText(true); hint.getStyleClass().add("text-muted");
        artCard.addFull(hint);

        // Amber diagnostic note: the feed-pitch rule that prevents blank labels.
        VBox pitchNote = new VBox(pitchLbl);
        pitchNote.setStyle("-fx-background-color: rgba(217,161,59,0.10); -fx-background-radius: 8;"
                + "-fx-border-color: rgba(217,161,59,0.40); -fx-border-radius: 8; -fx-border-width: 1;"
                + "-fx-padding: 8 10 8 10;");

        // LEFT — live roll preview; RIGHT — grouped setting cards
        VBox leftCol = new VBox(8, diagram, needLbl, caption, artCaption, pitchNote);
        leftCol.setPrefWidth(332);
        leftCol.setFillWidth(true);
        VBox rightCol = new VBox(10, stockCard.box, linerCard.box, artCard.box);
        rightCol.setFillWidth(true);
        HBox body = new HBox(14, leftCol, rightCol);
        HBox.setHgrow(rightCol, javafx.scene.layout.Priority.ALWAYS);

        content.getChildren().addAll(explainer, body);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResultConverter(btn -> btn == ButtonType.OK);
        dlg.showAndWait().ifPresent(ok -> {
            if (ok) {
                String code = theta[0];
                double pw = physWSpin.getValue(), ph = physHSpin.getValue();
                cfg.setColumns(colsSpin.getValue());
                cfg.setStripWidth(stripWSpin.getValue());
                cfg.setLabelWidth(LabelGeometryService.designWidthFor(pw, ph, code));
                cfg.setLabelHeight(LabelGeometryService.designHeightFor(pw, ph, code));
                cfg.setOrientation(code);
                cfg.setGapX(gapXSpin.getValue());
                cfg.setGapY(gapYSpin.getValue());
                cfg.setCornerRadius(cornerSpin.getValue());
                cfg.setMarginL(mlSpin.getValue());
                cfg.setMarginR(mrSpin.getValue());
                cfg.setStockType(stockCb.getSelectionModel().getSelectedIndex() == 1 ? "continuous" : "gap");
                cfg.sanitize();

                syncPageFromLabelConfig();
                refreshCanvas();
                centerView(); // label cell resized — keep it centred in view
                updatePropertiesPanel();
                saveState();
                Toast.show(app.getRootPane(), "Label Stock Updated",
                        "Roll " + (int) Math.round(cfg.getStripWidth()) + " mm · " + cfg.getColumns()
                        + " across · label " + (int) Math.round(pw) + "×" + (int) Math.round(ph)
                        + " mm on the strip"
                        + ("0".equals(cfg.getOrientation())
                                ? ""
                                : " · canvas " + (int) Math.round(cfg.getLabelWidth()) + "×"
                                  + (int) Math.round(cfg.getLabelHeight()) + " prints rotated "
                                  + cfg.getOrientation() + "°"), false);
            }
        });
    }

    /** Dark rounded "card" with a gold small-caps header and a 2-column
     *  label/control grid — the building block of the modern Label Stock
     *  dialog layout. Pure presentation; no behaviour. */
    private static final class SettingsCard {
        final VBox box = new VBox(8);
        final GridPane grid = new GridPane();

        SettingsCard(String title) {
            box.setStyle("-fx-background-color: #151C29; -fx-background-radius: 8;"
                    + "-fx-border-color: #273245; -fx-border-radius: 8; -fx-border-width: 1;"
                    + "-fx-padding: 12;");
            Label head = new Label(title);
            head.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #D9A13B;");
            grid.setHgap(10);
            grid.setVgap(8);
            box.getChildren().addAll(head, grid);
        }

        void add(String labelText, Node control) {
            Label l = new Label(labelText);
            l.setWrapText(true);
            l.setStyle("-fx-font-size: 12px; -fx-text-fill: #B9C4D6;");
            int r = grid.getRowCount();
            GridPane.setHgrow(control, javafx.scene.layout.Priority.ALWAYS);
            grid.add(l, 0, r);
            grid.add(control, 1, r);
        }

        void addFull(Node node) {
            grid.add(node, 0, grid.getRowCount(), 2, 1);
        }
    }

    /**
     * Draws the BarTender-style page thumbnail for the Label Stock dialog:
     * the liner with its die-cut labels (row 1 full, row 2 peeking below the
     * feed gap) using the PHYSICAL layout — exactly what the printer sees.
     * Pure UI; every number comes from the live LabelConfig snapshot.
     */
    private void drawStripDiagram(Pane bed, LabelConfig tmp) {
        bed.getChildren().clear();
        final double W = bed.getPrefWidth() > 0 ? bed.getPrefWidth() : 588;
        final double H = bed.getPrefHeight() > 0 ? bed.getPrefHeight() : 218;
        boolean continuous = "continuous".equalsIgnoreCase(tmp.getStockType());
        double linerW = Math.max(1, tmp.getStripWidth());
        double rowH = LabelGeometryService.physicalCellHeight(tmp);
        double gapY = continuous ? 0 : Math.max(0, tmp.getGapY());
        double pitch = rowH + gapY;
        double peek = rowH * 0.35;                        // second-row hint
        double totalH = pitch + peek;
        double s = Math.min((W - 32) / linerW, (H - 32) / totalH);
        s = Math.max(0.5, s);
        double x0 = (W - linerW * s) / 2.0;
        double y0 = (H - totalH * s) / 2.0;

        // liner — the backing paper the die-cuts sit on
        Rectangle liner = new Rectangle(x0, y0, linerW * s, totalH * s);
        liner.setFill(Color.web("#64748B"));
        bed.getChildren().add(liner);

        double[] xs = LabelGeometryService.columnOffsets(tmp);
        double cellW = LabelGeometryService.physicalCellWidth(tmp);
        double arc = Math.max(0, Math.min(2.0 * tmp.getCornerRadius() * s, rowH * s * 0.5));

        // row 1 — the labels that carry content
        for (int i = 0; i < xs.length; i++) {
            Rectangle lab = new Rectangle(x0 + xs[i] * s, y0, cellW * s, rowH * s);
            lab.setArcHeight(arc); lab.setArcWidth(arc);
            lab.setFill(Color.web("#F8FAFC"));
            lab.setStroke(Color.web("#0F172A"));
            bed.getChildren().add(lab);
            if (xs.length > 1) {
                Text num = new Text(String.valueOf(i + 1));
                num.setFont(Font.font(11));
                num.setFill(Color.web("#94A3B8"));
                num.setX(x0 + xs[i] * s + 5);
                num.setY(y0 + 15);
                bed.getChildren().add(num);
            }
        }

        // feed gap marker (the dashed line the gap sensor hunts for)
        if (gapY > 0) {
            Line gapLine = new Line(x0, y0 + (rowH + gapY / 2.0) * s,
                    x0 + linerW * s, y0 + (rowH + gapY / 2.0) * s);
            gapLine.setStroke(Color.web("#D9A13B"));
            gapLine.setStrokeWidth(1.2);
            gapLine.getStrokeDashArray().addAll(4d, 3d);
            bed.getChildren().add(gapLine);
        }

        // row 2 peeking below — shows the roll continues at the same pitch
        for (int i = 0; i < xs.length; i++) {
            Rectangle lab = new Rectangle(x0 + xs[i] * s, y0 + pitch * s, cellW * s, peek * s);
            lab.setArcHeight(arc); lab.setArcWidth(arc);
            lab.setFill(Color.web("#F8FAFC", 0.45));
            lab.setStroke(Color.web("#CBD5E1"));
            bed.getChildren().add(lab);
        }
    }

    /**
     * One-shot 90° CLOCKWISE rotation of the whole label design into the
     * physical print orientation: label width/height swap, every element is
     * moved and spun with the canvas, and the print-time rotation resets to
     * 0°. Afterwards the designer canvas, the strip preview, the bulk print
     * preview and the printed label all show the SAME picture (WYSIWYG).
     */
    private void rotateLabelDesign90() {
        if (!template.isLabelMode()) return;
        LabelConfig cfg = template.labelOrNew();
        cfg.sanitize();
        double designW = cfg.getLabelWidth();
        double designH = cfg.getLabelHeight();
        if (template.getElements() != null) {
            for (TemplateElement el : template.getElements()) {
                if (el == null) continue;
                double[] geo = LabelGeometryService.rotateElement90CW(
                        el.getX(), el.getY(), el.getW(), el.getH(), designH);
                el.setX(geo[0]);
                el.setY(geo[1]);
                el.setW(geo[2]);
                el.setH(geo[3]);
                el.setRotation(LabelGeometryService.rotateElementRotation90CW(el.getRotation()));
            }
        }
        cfg.setLabelWidth(designH);
        cfg.setLabelHeight(designW);
        cfg.setOrientation("0");
        syncPageFromLabelConfig();
        saveState();
        refreshCanvas();
        centerView(); // canvas dims changed — keep it centred in view
        updatePropertiesPanel();
        Toast.show(app.getRootPane(), "Design Rotated 90°",
                String.format(java.util.Locale.US,
                        "Canvas is now %.1f × %.1f mm — exactly what the printer outputs.",
                        cfg.getLabelWidth(), cfg.getLabelHeight()), false);
    }

    /** Shows how the strip looks — columns × 5 rows with gaps & rounded corners. */
    private void showStripPreviewDialog() {
        if (!template.isLabelMode()) return;
        template.labelOrNew().sanitize();
        try {
            new com.invoicestudio.ui.LabelStripPreviewDialog(
                    app.getPrimaryStage(), template, settingsDao.getSettings(), buildSampleLabelValues()).showAndWait();
        } catch (Exception ex) {
            Toast.show(app.getRootPane(), "Strip Preview", "Could not open preview: " + ex.getMessage(), true);
        }
    }

    /** Opens the keyboard-first bulk print popup. */
    private void showBulkPrintDialog() {
        if (!template.isLabelMode()) return;
        template.labelOrNew().sanitize();

        // Only variables this template ACTUALLY uses get a column — defining
        // other barcode variables in the Variables page must not flood the
        // print grid with empty columns.
        Set<String> used = new LinkedHashSet<>(collectTemplatePlaceholders());
        List<VariableDef> vars = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            for (VariableDef v : variableDao.getBarcodeScopeVariables()) {
                if (v != null && used.contains(v.getKey()) && seen.add(v.getKey())) {
                    vars.add(v);
                }
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored); }

        // Any placeholder typed on the label but not defined as a barcode
        // variable still gets a column, so a run is never blocked.
        for (String key : used) {
            if (seen.add(key)) {
                vars.add(new VariableDef(key, key, "text", false));
            }
        }

        try {
            new com.invoicestudio.ui.LabelBulkPrintDialog(
                    app.getPrimaryStage(), template, settingsDao.getSettings(), vars,
                    template.labelOrNew()).showAndWait();
        } catch (Exception ex) {
            Toast.show(app.getRootPane(), "Bulk Print", "Could not open: " + ex.getMessage(), true);
        }
    }

    /** All {{keys}} used anywhere on this label (text payloads + barcode data). */
    private List<String> collectTemplatePlaceholders() {
        Set<String> keys = new LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}").matcher("");
        if (template.getElements() != null) {
            for (TemplateElement el : template.getElements()) {
                String[] sources = { el.getText(), el.getBarcodeData(), el.getQrCustom() };
                for (String src : sources) {
                    if (src == null) continue;
                    m.reset(src);
                    while (m.find()) keys.add(m.group(1));
                }
            }
        }
        // Not variables of the bill pipeline — labels want their own dynamic set.
        keys.removeAll(List.of("invoice_no", "invoice_date", "buyer_name", "buyer_gst",
                "business_name", "business_gst", "page_no", "page_count", "grand_total"));
        return new ArrayList<>(keys);
    }

    /** Cross-product sample values so the strip preview shows variety. */
    private List<Map<String, String>> buildSampleLabelValues() {
        List<Map<String, String>> out = new ArrayList<>();
        List<VariableDef> vars = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>(collectTemplatePlaceholders());
        try {
            for (VariableDef v : variableDao.getBarcodeScopeVariables()) {
                if (v != null && used.contains(v.getKey())) vars.add(v);
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored); }
        if (vars.isEmpty()) {
            out.add(new LinkedHashMap<>());
            return out;
        }
        int tiles = Math.max(1, template.labelOrNew().getColumns()) * 5;
        for (int i = 0; i < tiles; i++) {
            Map<String, String> vals = new LinkedHashMap<>();
            for (VariableDef v : vars) {
                List<String> choices = v.choicesList();
                if (choices.isEmpty()) {
                    vals.put(v.getKey(), v.getKey());
                } else {
                    vals.put(v.getKey(), choices.get(i % choices.size()));
                }
            }
            out.add(vals);
        }
        return out;
    }

    private void buildPageAndMarginProperties() {
        PageConfig page = template.getPage();
        PageConfig.Margins mg = page.getMargin();
        if (mg == null) {
            mg = new PageConfig.Margins(8, 8, 8, 8);
            page.setMargin(mg);
        }
        updatePageFormatLabel();
        updateStatusBarCoords();

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
        addPropertyNode(headerRow);

        // Barcode Mode: the canvas is the label cell — point users to the right dialog.
        if (template.isLabelMode()) {
            Label note = new Label("Barcode Mode active — this canvas is ONE label cell. "
                    + "Use 🏷 Label Stock to edit strip columns, gaps, margins, corners & print orientation.");
            note.setWrapText(true);
            note.setStyle("-fx-text-fill: #b45309; -fx-background-color: rgba(245,158,11,0.12); -fx-padding: 6 10; -fx-background-radius: 4;");
            addPropertyNode(note);
        }

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
        configureNumberSpinner(wSpin);
        wSpin.setPrefWidth(85);
        wSpin.valueProperty().addListener((obs, o, v) -> {
            page.setWidth(v);
            refreshCanvas();
        });
        dimGrid.add(wSpin, 1, 1);

        dimGrid.add(new Label("H (mm):"), 2, 1);
        Spinner<Double> hSpin = new Spinner<>(20.0, 2000.0, page.getHeight(), 1.0);
        configureNumberSpinner(hSpin);
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
        addPropertyNode(dimPane);

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
        configureNumberSpinner(topSpin);
        topSpin.setPrefWidth(85);
        topSpin.valueProperty().addListener((obs, o, v) -> updateMargin("top", v, shiftWithMargins));
        mgGrid.add(topSpin, 1, 0);

        // Bottom Spinner
        mgGrid.add(new Label("Bottom:"), 2, 0);
        Spinner<Double> botSpin = new Spinner<>(0.0, 60.0, mg.getBottom(), 1.0);
        configureNumberSpinner(botSpin);
        botSpin.setPrefWidth(85);
        botSpin.valueProperty().addListener((obs, o, v) -> updateMargin("bottom", v, shiftWithMargins));
        mgGrid.add(botSpin, 3, 0);

        // Left Spinner
        mgGrid.add(new Label("Left:"), 0, 1);
        Spinner<Double> leftSpin = new Spinner<>(0.0, 60.0, mg.getLeft(), 1.0);
        configureNumberSpinner(leftSpin);
        leftSpin.setPrefWidth(85);
        leftSpin.valueProperty().addListener((obs, o, v) -> updateMargin("left", v, shiftWithMargins));
        mgGrid.add(leftSpin, 1, 1);

        // Right Spinner
        mgGrid.add(new Label("Right:"), 2, 1);
        Spinner<Double> rightSpin = new Spinner<>(0.0, 60.0, mg.getRight(), 1.0);
        configureNumberSpinner(rightSpin);
        rightSpin.setPrefWidth(85);
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
        addPropertyNode(mgPane);
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

        // Barcode Mode: mirror L/R edits back into the stock geometry so the
        // canvas guides, strip preview and the actual print all agree.
        if (template.isLabelMode()) {
            LabelConfig lc = template.labelOrNew();
            if ("left".equalsIgnoreCase(side)) {
                lc.setMarginL(newVal);
            } else if ("right".equalsIgnoreCase(side)) {
                lc.setMarginR(newVal);
            }
            lc.sanitize();
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

        // Barcode Mode: keep the stock geometry in sync (see updateMargin).
        if (template.isLabelMode()) {
            LabelConfig lc = template.labelOrNew();
            lc.setMarginL(left);
            lc.setMarginR(right);
            lc.sanitize();
        }

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
            case RECT: el.setW(60); el.setH(30); el.setBg("#f4f1ea"); el.setBorderColor("#1a1a1a"); el.setBorderWidth(0.5); break;
            case CIRCLE: el.setW(30); el.setH(30); el.setRadius(15); el.setBg("#e0e7ff"); el.setBorderColor("#4f46e5"); el.setBorderWidth(0.5); break;
            case ELLIPSE: el.setW(50); el.setH(30); el.setRadiusX(25); el.setRadiusY(15); el.setBg("#fef3c7"); el.setBorderColor("#d97706"); el.setBorderWidth(0.5); break;
            case LINE: el.setW(80); el.setH(1); el.setBorderWidth(0.5); el.setBorderColor("#1a1a1a"); break;
            case ARROW: el.setW(60); el.setH(15); el.setBorderColor("#2563eb"); el.setBorderWidth(1.0); el.setBg("#2563eb"); el.setArrowHeadLength(5.0); el.setArrowHeadWidth(5.0); break;
            case STAR: el.setW(35); el.setH(35); el.setStarPoints(5); el.setInnerRadius(6.0); el.setOuterRadius(15.0); el.setBg("#f59e0b"); el.setBorderColor("#b45309"); el.setBorderWidth(0.5); break;
            case POLYGON: el.setW(40); el.setH(35); el.setPoints("20,0 40,35 0,35"); el.setBg("#dcfce7"); el.setBorderColor("#16a34a"); el.setBorderWidth(0.5); break;
            case POLYLINE: el.setW(50); el.setH(25); el.setPoints("0,20 15,5 35,20 50,0"); el.setBorderColor("#0284c7"); el.setBorderWidth(1.0); el.setStrokeEnabled(true); break;
            case ARC: el.setW(40); el.setH(40); el.setStartAngle(0); el.setArcLength(270); el.setArcType("ROUND"); el.setBg("#fce7f3"); el.setBorderColor("#db2777"); el.setBorderWidth(0.5); break;
            case PATH: el.setW(40); el.setH(40); el.setPathData("M 10,30 A 20,20 0 0,1 50,30 A 20,20 0 0,1 90,30 Q 90,60 50,90 Q 10,60 10,30 Z"); el.setBg("#f43f5e"); el.setBorderColor("#e11d48"); el.setBorderWidth(0.5); break;
            case DIVIDER: el.setW(170); el.setH(4); el.setDividerOrientation("HORIZONTAL"); el.setDividerStyle("DASHED"); el.setBorderColor("#cbd5e1"); el.setBorderWidth(0.8); break;
            case FREEHAND: el.setW(50); el.setH(25); el.setPoints("5,20 15,10 25,18 40,5 45,15"); el.setBorderColor("#1e293b"); el.setBorderWidth(1.2); break;
            case WATERMARK: el.setW(160); el.setH(60); el.setWatermarkText("ORIGINAL FOR RECIPIENT"); el.setWatermarkOpacity(0.12); el.setWatermarkAngle(-30.0); el.setText("ORIGINAL FOR RECIPIENT"); break;
            case SVG: el.setW(30); el.setH(30); el.setSvgSource("<svg viewBox=\"0 0 24 24\"><circle cx=\"12\" cy=\"12\" r=\"10\" fill=\"#3b82f6\"/><path d=\"M9 12l2 2 4-4\" stroke=\"white\" stroke-width=\"2\" fill=\"none\"/></svg>"); break;
            case ICON: el.setW(12); el.setH(12); el.setIconName("check"); el.setColor("#2563eb"); break;
            case IMAGE: el.setW(30); el.setH(25); el.setUseBusinessLogo(true); break;
            case QRCODE: el.setW(24); el.setH(24); break;
            case BARCODE: el.setW(45); el.setH(14); break;
            case TABLE:
                el.setW(190); el.setH(30);
                el.setColumns(PresetTemplates.defaultItemColumns());
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

    private void addRoundedRect() {
        TemplateElement el = new TemplateElement();
        el.setId("el_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        el.setType(ElementType.RECT);
        el.setName("Rounded Card");
        el.setX(20);
        el.setY(20);
        el.setW(60);
        el.setH(30);
        el.setBg("#ffffff");
        el.setBorderRadius(4.0);
        el.setBorderWidth(0.3);
        el.setBorderColor("#c9c4b8");

        template.getElements().add(el);
        selectedElement = el;
        saveState();
        refreshCanvas();
        updatePropertiesPanel();
        refreshLayersList();
    }

    private void addLine(String dir) {
        TemplateElement el = new TemplateElement();
        el.setId("el_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        el.setType(ElementType.LINE);
        el.setName("v".equalsIgnoreCase(dir) ? "Vertical Line" : "Horizontal Line");
        el.setDirection(dir);
        el.setX(20);
        el.setY(20);
        if ("v".equalsIgnoreCase(dir)) {
            el.setW(1);
            el.setH(60);
        } else {
            el.setW(80);
            el.setH(1);
        }
        el.setBorderWidth(0.5);
        el.setBorderColor("#1a1a1a");

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
                layersList.getSelectionModel().clearSelection();
                layersList.getSelectionModel().select(selectedElement);
                layersList.scrollTo(selectedElement);
            } else {
                layersList.getSelectionModel().clearSelection();
            }
        } finally {
            isUpdatingLayersSelection = false;
        }
    }

    private void refreshLayersList() {
        layersData.setAll(template.getElements());
        layerCountBadge.setText(template.getElements().size() + " items");
        syncLayersListSelection();
    }

    private void saveTemplate() {
        template.setUpdatedAt(Instant.now().toString());
        templateDao.saveTemplate(template);
        app.reloadAllData();
        Toast.show(app.getRootPane(), "Template Saved", "\"" + template.getName() + "\" saved successfully.", false);
    }

    private void saveState() {
        designerState.saveState(template);
    }

    private void undo() {
        Template previous = designerState.undo();
        if (previous == null) {
            Toast.show(app.getRootPane(), "Undo", "Nothing to undo.", false);
            return;
        }
        this.template = previous;
        this.selectedElement = null;
        nameField.setText(template.getName());
        refreshCanvas();
        updatePropertiesPanel();
        refreshLayersList();
        Toast.show(app.getRootPane(), "Undo", "Action undone.", false);
    }

    private void redo() {
        Template next = designerState.redo();
        if (next == null) {
            Toast.show(app.getRootPane(), "Redo", "Nothing to redo.", false);
            return;
        }
        this.template = next;
        this.selectedElement = null;
        nameField.setText(template.getName());
        refreshCanvas();
        updatePropertiesPanel();
        refreshLayersList();
        Toast.show(app.getRootPane(), "Redo", "Action redone.", false);
    }

    private void copySelected() {
        if (selectedElement == null) return;
        designerState.copy(selectedElement);
        Toast.show(app.getRootPane(), "Copied", "Element copied to clipboard.", false);
    }

    private void pasteCopied() {
        TemplateElement pasted = designerState.pasteSource();
        if (pasted == null) return;
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

    private void showShortcutsHelpDialog() {
        Stage dlg = new Stage();
        Window owner = getScene() != null ? getScene().getWindow() : app.getPrimaryStage();
        if (owner instanceof Stage s) dlg.initOwner(s);
        DialogHelper.applyAppIcon(dlg); // logo in title bar from the very first frame
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("InvoiceStudio Designer Shortcuts & Guide");

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.getStyleClass().addAll("bg-base", "root-container");
        root.setPrefWidth(620);
        root.setMaxHeight(700);

        Label titleLbl = new Label("InvoiceStudio 4.0 Designer Reference & Shortcuts");
        titleLbl.getStyleClass().add("heading-l");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(8);
        grid.setPadding(new Insets(8, 0, 8, 0));

        String[][] shortcuts = {
                {"Ctrl + Shift + L", "Toggle Barcode Mode (label designer) — also opens Label Designer from anywhere"},
                {"Ctrl + Shift + B", "Bulk Label Print window (Barcode Mode)"},
                {"Double-Click (Text)", "Enter inline text editing mode directly on canvas"},
                {"Enter (Editing)", "Commit and save inline text changes"},
                {"Shift + Enter (Editing)", "Insert newline in text editor"},
                {"Escape", "Cancel text edit / Cancel pen tool / Deselect element"},
                {"V", "Switch to Select & Move tool"},
                {"H  /  Space (Hold)", "Pan canvas freely with Hand tool"},
                {"P", "Switch to Vector Pen tool (plot points / curves)"},
                {"Ctrl + Mouse Wheel", "Zoom canvas in and out (up to 400%)"},
                {"Ctrl + +  /  Ctrl + -", "Zoom in / Zoom out (30% – 400%)"},
                {"Ctrl + 0", "Reset canvas zoom to 100%"},
                {"Grid", "Background grid adapts to zoom: 10 → 5 → 2 → 1 mm"},
                {"Ctrl + S", "Save template changes to database"},
                {"Ctrl + Z", "Undo last designer action"},
                {"Ctrl + Y  /  Ctrl + Shift + Z", "Redo previously undone action"},
                {"Ctrl + C  /  Ctrl + V", "Copy and paste selected element"},
                {"Ctrl + D", "Duplicate selected element with offset"},
                {"Ctrl + G", "Group selected elements together"},
                {"Ctrl + Shift + G", "Ungroup selected elements"},
                {"Delete  /  Backspace", "Delete selected canvas element (safe while typing)"},
                {"Arrow Keys", "Nudge selected element by 1 mm (Shift + Arrow for 5 mm)"}
        };

        int r = 0;
        for (String[] sc : shortcuts) {
            Label keyLbl = new Label(sc[0]);
            keyLbl.getStyleClass().add("key-pill");
            Label descLbl = new Label(sc[1]);
            descLbl.getStyleClass().add("text-sm");
            grid.add(keyLbl, 0, r);
            grid.add(descLbl, 1, r);
            r++;
        }

        VBox featuresBox = new VBox(6);
        featuresBox.setStyle("-fx-background-color: #151B25; -fx-padding: 12; -fx-background-radius: 6; -fx-border-color: #232B38; -fx-border-radius: 6;");
        Label featTitle = new Label("Designer Features & Capabilities (v4.0):");
        featTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #D9A13B; -fx-font-size: 13px;");
        Label f1 = new Label("• Inline Canvas Text Editing: Double-click any text element to type and edit text directly on canvas with live preview.");
        Label f2 = new Label("• Selection-Aware Cursors: Default arrow pointer for clean hovering; move & directional resize handles activate upon selection.");
        Label f3 = new Label("• Container Clipping (✂): Geometric masking into Rectangles, Circles (for logos/badges), and Rounded Rectangles in both Canvas and PDF export.");
        Label f4 = new Label("• Modern Web Range Bars: Dark slate slider tracks with amber-gold glowing thumbs across all controls.");
        Label f5 = new Label("• Vector Pen Tool (P): Draw custom polygons and smooth bezier curves with interactive vertex handles.");
        Label f6 = new Label("• Metric Canvas Rulers & Snapping (🧲): Millimeter scale with magnetic alignment to element borders and page margins.");
        Label f7 = new Label("• 8-Point Resize Handles: Full 8-direction scaling including left-side scaling (W handle).");
        Label f8 = new Label("• Interactive Layers: Direct hide/show (👁), lock/unlock (🔒), and custom element naming.");
        for (Label fl : new Label[]{f1, f2, f3, f4, f5, f6, f7, f8}) {
            fl.setWrapText(true);
            fl.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");
        }
        featuresBox.getChildren().addAll(featTitle, f1, f2, f3, f4, f5, f6, f7, f8);

        VBox scrollContent = new VBox(14, grid, featuresBox);
        ScrollPane sp = new ScrollPane(scrollContent);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 2;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().addAll("button-primary");
        closeBtn.setOnAction(e -> dlg.close());
        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(titleLbl, sp, btnBox);

        Scene scene = new Scene(root);
        if (getScene() != null) {
            scene.getStylesheets().addAll(getScene().getStylesheets());
        }
        dlg.setScene(scene);
        dlg.showAndWait();
    }

    private boolean isInputFieldActive(KeyEvent e) {
        if (e.getTarget() instanceof TextInputControl) return true;
        if (e.getTarget() instanceof Node targetNode) {
            Node curr = targetNode;
            while (curr != null) {
                if (curr instanceof TextInputControl || curr instanceof Spinner<?>) {
                    return true;
                }
                curr = curr.getParent();
            }
        }
        Scene scene = getScene();
        if (scene != null) {
            Node focusOwner = scene.getFocusOwner();
            if (focusOwner instanceof TextInputControl) return true;
            if (focusOwner != null) {
                Node curr = focusOwner;
                while (curr != null) {
                    if (curr instanceof TextInputControl || curr instanceof Spinner<?>) {
                        return true;
                    }
                    curr = curr.getParent();
                }
            }
        }
        return false;
    }

    private void handleGlobalKeyPress(KeyEvent e) {
        if (!isVisible() || getScene() == null) return;

        boolean isTextInput = isInputFieldActive(e);

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
            if (isPenToolMode) cancelPenTool();
            setPanMode(false);
            e.consume();
            return;
        } else if (e.getCode() == KeyCode.H && !e.isControlDown()) {
            if (isPenToolMode) cancelPenTool();
            setPanMode(true);
            e.consume();
            return;
        } else if (e.getCode() == KeyCode.P && !e.isControlDown()) {
            if (isPenToolMode) cancelPenTool();
            else activatePenTool();
            e.consume();
            return;
        }

        // Barcode (label) mode shortcuts — consumed here so the global
        // accelerators in StudioApp don't double-fire while designing.
        if (e.isControlDown() && e.isShiftDown() && e.getCode() == KeyCode.L) {
            toggleBarcodeMode();
            e.consume();
            return;
        }
        if (e.isControlDown() && e.isShiftDown() && e.getCode() == KeyCode.B) {
            openBulkPrintFromShortcut();
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
        } else if (e.isControlDown() && e.getCode() == KeyCode.G) {
            if (e.isShiftDown()) {
                ungroupSelected();
            } else {
                groupSelected();
            }
            e.consume();
        } else if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
            deleteSelected();
            e.consume();
        } else if (e.isControlDown() && (e.getCode() == KeyCode.DIGIT0 || e.getCode() == KeyCode.NUMPAD0)) {
            fitToView();
            e.consume();
        } else if (e.isControlDown() && (e.getCode() == KeyCode.EQUALS || e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.ADD)) {
            setZoom(zoom + 0.1);
            e.consume();
        } else if (e.isControlDown() && (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT)) {
            setZoom(zoom - 0.1);
            e.consume();
        } else if (e.getCode() == KeyCode.ESCAPE) {
            if (isPenToolMode) {
                cancelPenTool();
                e.consume();
                return;
            }
            selectedElement = null;
            updateSelectionOverlay();
            updatePropertiesPanel();
            syncLayersListSelection();
            e.consume();
        } else if (selectedElement != null && !selectedElement.isLocked()) {
            double step = e.isShiftDown() ? 5.0 : 1.0;
            if (e.getCode() == KeyCode.LEFT) {
                selectedElement.setX(Math.max(0, selectedElement.getX() - step));
                updateElementVisualInPlace(selectedElement);
                updateSelectionOverlay();
                syncGeoSpinnersIfPresent();
                saveState();
                e.consume();
            } else if (e.getCode() == KeyCode.RIGHT) {
                selectedElement.setX(selectedElement.getX() + step);
                updateElementVisualInPlace(selectedElement);
                updateSelectionOverlay();
                syncGeoSpinnersIfPresent();
                saveState();
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                selectedElement.setY(Math.max(0, selectedElement.getY() - step));
                updateElementVisualInPlace(selectedElement);
                updateSelectionOverlay();
                syncGeoSpinnersIfPresent();
                saveState();
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN) {
                selectedElement.setY(selectedElement.getY() + step);
                updateElementVisualInPlace(selectedElement);
                updateSelectionOverlay();
                syncGeoSpinnersIfPresent();
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
