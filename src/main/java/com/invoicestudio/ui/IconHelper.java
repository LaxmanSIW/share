package com.invoicestudio.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

public class IconHelper {

    public static final String ICON_DASHBOARD = "dashboard";
    public static final String ICON_TEMPLATES = "templates";
    public static final String ICON_RECEIPT = "new";
    public static final String ICON_HISTORY = "history";
    public static final String ICON_USERS = "buyers";
    public static final String ICON_PACKAGE = "items";
    public static final String ICON_VARIABLE = "variables";
    public static final String ICON_SETTINGS = "settings";
    public static final String ICON_PLUS = "plus";
    public static final String ICON_TAG = "tag";
    public static final String ICON_TRENDING_UP = "trending";
    public static final String ICON_BAR_CHART = "chart";
    public static final String ICON_SPARKLES = "sparkles";
    public static final String ICON_SEARCH = "search";
    public static final String ICON_EDIT = "edit";
    public static final String ICON_TRASH = "delete";
    public static final String ICON_CHECK = "check";
    public static final String ICON_UPLOAD = "upload";
    public static final String ICON_DOWNLOAD = "download";
    public static final String ICON_CROSSHAIR = "crosshair";
    public static final String ICON_REPORTS = "reports";
    public static final String ICON_TRANSACTIONS = "transactions";
    public static final String ICON_TRANSPORT = "transport";
    public static final String ICON_CATEGORIES = "categories";
    public static final String ICON_DASHBOARD2 = "dashboard2";
    public static final String ICON_SHAPES = "shapes";
    public static final String ICON_RECT = "shape-rect";
    public static final String ICON_ROUND_RECT = "shape-round-rect";
    public static final String ICON_CIRCLE = "shape-circle";
    public static final String ICON_ELLIPSE = "shape-ellipse";
    public static final String ICON_LINE_H = "shape-line-h";
    public static final String ICON_LINE_V = "shape-line-v";
    public static final String ICON_ARROW = "shape-arrow";
    public static final String ICON_STAR = "shape-star";
    public static final String ICON_POLYGON = "shape-polygon";
    public static final String ICON_ARC = "shape-arc";
    public static final String ICON_PATH = "shape-path";
    public static final String ICON_PEN = "shape-pen";
    public static final String ICON_CURVE = "shape-curve";
    public static final String ICON_ANCHOR = "shape-anchor";
    public static final String ICON_DIVIDER = "shape-divider";
    public static final String ICON_SIGNATURE = "shape-signature";
    public static final String ICON_WATERMARK = "shape-watermark";
    public static final String ICON_MEDIA = "media";
    public static final String ICON_MEDIA_IMAGE = "media-image";
    public static final String ICON_MEDIA_SVG = "media-svg";
    public static final String ICON_CODE = "code";
    /** Anthropic-style MCP (Model Context Protocol) logo — server stack with connector nodes. */
    public static final String ICON_MCP = "mcp";
    public static final String ICON_CODE_QR = "code-qr";
    public static final String ICON_CODE_BARCODE = "code-barcode";
    public static final String ICON_BUSINESS = "business";
    public static final String ICON_BANK = "bank";
    public static final String ICON_BILLING = "billing";
    public static final String ICON_FIELDS = "tag";
    public static final String ICON_FONT = "font";
    public static final String ICON_PRINT = "print";
    public static final String ICON_BACKUP = "backup";
    /* ---- Designer toolbar ---- */
    public static final String ICON_TOOL_SELECT = "tool-select";
    public static final String ICON_TOOL_HAND = "tool-hand";
    public static final String ICON_TOOL_LINE = "tool-line";
    public static final String ICON_NAV_BACK = "nav-back";
    public static final String ICON_TABLE = "table";
    public static final String ICON_UNDO = "undo";
    public static final String ICON_REDO = "redo";
    public static final String ICON_HELP = "help";
    public static final String ICON_STRIP_PREVIEW = "strip-preview";
    public static final String ICON_LABEL_MODE = "label-mode";
    public static final String ICON_MAGNET = "magnet";
    /** Rounded chat bubble with three dots — the in-app AI chatbot. */
    public static final String ICON_CHAT = "chat";

    public static Node getIcon(String name, double size, String colorHex) {
        String path = getSvgPath(name);
        if (path != null) {
            SVGPath svg = new SVGPath();
            svg.setContent(path);
            svg.setFill(Color.web(colorHex != null ? colorHex : "#CBD5E1"));
            return svg;
        }

        // Fallback to text glyph
        Label lbl = new Label(getFallbackGlyph(name));
        lbl.setStyle("-fx-font-size: " + (int) size + "px; -fx-text-fill: " + (colorHex != null ? colorHex : "#CBD5E1") + ";");
        return lbl;
    }

    public static Node getMenuIcon(String name, String colorHex) {
        String path = getSvgPath(name);
        if (path != null) {
            SVGPath svg = new SVGPath();
            svg.setContent(path);
            svg.setFill(Color.web(colorHex != null ? colorHex : "#94A3B8"));
            svg.setScaleX(14.0 / 24.0);
            svg.setScaleY(14.0 / 24.0);
            javafx.scene.Group grp = new javafx.scene.Group(svg);
            javafx.scene.layout.StackPane box = new javafx.scene.layout.StackPane(grp);
            box.setPrefSize(18, 18);
            box.setMinSize(18, 18);
            box.setMaxSize(18, 18);
            box.setAlignment(javafx.geometry.Pos.CENTER);
            return box;
        }
        Label lbl = new Label(getFallbackGlyph(name));
        lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (colorHex != null ? colorHex : "#94A3B8") + "; -fx-alignment: center;");
        javafx.scene.layout.StackPane box = new javafx.scene.layout.StackPane(lbl);
        box.setPrefSize(18, 18);
        box.setMinSize(18, 18);
        box.setMaxSize(18, 18);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        return box;
    }

    public static Label createIconLabel(String name, double size, String colorHex) {
        Label lbl = new Label(getFallbackGlyph(name));
        lbl.setStyle("-fx-font-size: " + (int) size + "px; -fx-text-fill: " + (colorHex != null ? colorHex : "#CBD5E1") + "; -fx-alignment: center;");
        return lbl;
    }

    public static Node createButtonGraphic(String iconName, String colorHex) {
        return getIcon(iconName, 14, colorHex);
    }

    public static Node createTabGraphic(String name, javafx.beans.value.ObservableValue<Boolean> selectedProp) {
        String path = getSvgPath(name);
        if (path != null) {
            SVGPath svg = new SVGPath();
            svg.setContent(path);
            String normalColor = "#94A3B8";
            String activeColor = "#F2CA6B";
            svg.setFill(Color.web(normalColor));
            svg.setScaleX(13.0 / 24.0);
            svg.setScaleY(13.0 / 24.0);
            javafx.scene.Group grp = new javafx.scene.Group(svg);
            javafx.scene.layout.StackPane box = new javafx.scene.layout.StackPane(grp);
            box.setPrefSize(14, 14);
            box.setMinSize(14, 14);
            box.setMaxSize(14, 14);
            box.setAlignment(javafx.geometry.Pos.CENTER);
            if (selectedProp != null) {
                selectedProp.addListener((obs, wasSelected, isSelected) -> {
                    svg.setFill(Color.web(isSelected ? activeColor : normalColor));
                });
                if (Boolean.TRUE.equals(selectedProp.getValue())) {
                    svg.setFill(Color.web(activeColor));
                }
            }
            return box;
        }
        Label lbl = new Label(getFallbackGlyph(name));
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8; -fx-alignment: center;");
        if (selectedProp != null) {
            selectedProp.addListener((obs, wasSelected, isSelected) -> {
                lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isSelected ? "#F2CA6B" : "#94A3B8") + "; -fx-alignment: center;");
            });
            if (Boolean.TRUE.equals(selectedProp.getValue())) {
                lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #F2CA6B; -fx-alignment: center;");
            }
        }
        javafx.scene.layout.StackPane box = new javafx.scene.layout.StackPane(lbl);
        box.setPrefSize(14, 14);
        box.setMinSize(14, 14);
        box.setMaxSize(14, 14);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        return box;
    }

    /**
     * 15px toolbar glyph whose fill is driven purely by CSS (".toolbar-icon"),
     * so hover / active states recolor it without any JavaFX style surgery.
     */
    public static Node getToolbarIcon(String name) {
        SVGPath svg = new SVGPath();
        String path = getSvgPath(name);
        svg.setContent(path != null ? path : "M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16z");
        svg.getStyleClass().add("toolbar-icon");
        svg.setScaleX(15.0 / 24.0);
        svg.setScaleY(15.0 / 24.0);
        javafx.scene.Group grp = new javafx.scene.Group(svg);
        javafx.scene.layout.StackPane box = new javafx.scene.layout.StackPane(grp);
        box.setPrefSize(16, 16);
        box.setMinSize(16, 16);
        box.setMaxSize(16, 16);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        return box;
    }

    private static String getFallbackGlyph(String name) {
        if (name == null) return "•";
        switch (name.toLowerCase()) {
            case "dashboard": return "⊞";
            case "templates": return "❐";
            case "new":
            case "bill": return "📄";
            case "history": return "⏱";
            case "buyers": return "👥";
            case "items": return "📦";
            case "variables": return "χ";
            case "settings": return "⚙";
            case "plus": return "+";
            case "print": return "🖨";
            case "pdf": return "⬇";
            case "edit": return "✎";
            case "delete": return "🗑";
            case "search": return "🔍";
            case "copy": return "⎘";
            case "eye": return "👁";
            case "check": return "✓";
            case "whatsapp": return "💬";
            case "tag": return "🏷";
            case "trending": return "📈";
            case "chart": return "📊";
            case "sparkles": return "✨";
            case "upload": return "⬆";
            case "download": return "⬇";
            case "crosshair": return "⌖";
            case "reports": return "📊";
            case "transactions": return "💳";
            case "transport": return "🚚";
            case "categories": return "🗂";
            case "dashboard2": return "📈";
            case "business": return "🏢";
            case "bank": return "🏛";
            case "billing": return "📄";
            case "font": return "🔤";
            case "backup": return "💾";
            default: return "•";
        }
    }

    private static String getSvgPath(String name) {
        if (name == null) return null;
        switch (name.toLowerCase()) {
            case "chat":
                // Rounded chat bubble with three dots (readable at 20px+).
                return "M12 3C6.9 3 2.8 6.4 2.8 10.6c0 2.4 1.3 4.5 3.4 5.9-.1 1-.5 2.2-1.6 3.2 1.9-.1 3.5-.8 4.6-1.6 .9.2 1.8.3 2.8.3 5.1 0 9.2-3.4 9.2-7.7S17.1 3 12 3zM8.6 12.1c-.7 0-1.3-.6-1.3-1.3s.6-1.3 1.3-1.3 1.3.6 1.3 1.3-.6 1.3-1.3 1.3zm3.4 0c-.7 0-1.3-.6-1.3-1.3s.6-1.3 1.3-1.3 1.3.6 1.3 1.3-.6 1.3-1.3 1.3zm3.4 0c-.7 0-1.3-.6-1.3-1.3s.6-1.3 1.3-1.3 1.3.6 1.3 1.3-.6 1.3-1.3 1.3z";
            case "mcp":
                // MCP mark: stacked server layers joined by a T connector (readable at 13px).
                return "M3 3 H21 V6.5 H3 Z M3 8.5 H21 V12 H3 Z M11 12 H13 V16 H18.5 V19 H5.5 V16 H11 Z";
            case "dashboard":
                return "M3 3h7v7H3V3zm11 0h7v7h-7V3zm0 11h7v7h-7v-7zM3 14h7v7H3v-7z";
            case "dashboard2":
                return "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z";
            case "templates":
                return "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 14H6v-4h6v4zm0-6H6V7h6v4zm6 6h-4v-4h4v4zm0-6h-4V7h4v4z";
            case "new":
            case "bill":
                return "M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z";
            case "history":
                return "M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z";
            case "buyers":
                return "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z";
            case "items":
                return "M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4";
            case "variables":
                return "M4 7h4v2H4V7zm0 4h10v2H4v-2zm0 4h7v2H4v-2zm12-4l3 3 3-3-1.4-1.4-1.6 1.6-1.6-1.6L16 11z";
            case "settings":
                return "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";
            case "reports":
                return "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-5h2v5zm4 0h-2v-9h2v9zm4 0h-2v-4h2v4z";
            case "transactions":
                return "M20 4H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z";
            case "transport":
                return "M20 8h-3V4H3c-1.1 0-2 .9-2 2v11h2c0 1.66 1.34 3 3 3s3-1.34 3-3h6c0 1.66 1.34 3 3 3s3-1.34 3-3h2v-5l-3-4zM6 18.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm13.5-9l1.96 2.5H17V9.5h2.5zm-1.5 9c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z";
            case "categories":
                return "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z";
            case "plus":
                return "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
            case "search":
                return "M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";
            case "print":
                return "M19 8H5c-1.66 0-3 1.34-3 3v6h4v4h12v-4h4v-6c0-1.66-1.34-3-3-3zm-3 11H8v-5h8v5zm3-7c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1zm-1-9H6v4h12V3z";
            case "edit":
                return "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z";
            case "delete":
                return "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";
            case "check":
                return "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
            case "upload":
                return "M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z";
            case "download":
                return "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z";
            case "crosshair":
                return "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17.93c-3.95-.49-7-3.85-7.93-7.93H7v-2H5.07c.93-4.08 3.98-7.44 7.93-7.93V7h2V5.07c3.95.49 7 3.85 7.93 7.93H17v2h1.93c-.93 4.08-3.98-7.44-7.93 7.93V17h-2v2.93z";
            case "shapes":
            case "tool-shapes":
                return "M4 3h7v7H4V3zm9 0h7v7h-7V3zM4 12h7v7H4v-7zm12.5 0a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7z";
            case "shape-rect":
                return "M3 5h18v14H3V5zm2 2v10h14V7H5z";
            case "shape-round-rect":
                return "M6 5h12a4 4 0 0 1 4 4v6a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V9a4 4 0 0 1 4-4zm0 2a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2H6z";
            case "shape-circle":
                return "M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16zm-6 8a6 6 0 1 1 12 0 6 6 0 0 1-12 0z";
            case "shape-ellipse":
                return "M12 6c5.52 0 10 2.69 10 6s-4.48 6-10 6S2 15.31 2 12s4.48-6 10-6zm0 2c-4.41 0-8 1.79-8 4s3.59 4 8 4 8-1.79 8-4-3.59-4-8-4z";
            case "shape-line-h":
                return "M3 11h18v2H3v-2z";
            case "shape-line-v":
                return "M11 3h2v18h-2V3z";
            case "shape-arrow":
                return "M4 11h11.17l-4.58-4.59L12 5l7 7-7 7-1.41-1.41L15.17 13H4v-2z";
            case "shape-star":
                return "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z";
            case "shape-polygon":
                return "M12 3l9.5 16.5h-19L12 3zm0 4.3L5.3 17.5h13.4L12 7.3z";
            case "shape-arc":
                return "M4 18A8 8 0 0 1 20 18h-2a6 6 0 0 0-12 0H4z";
            case "shape-path":
                return "M17 3a2.83 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3zm2.58 2.58a1 1 0 0 0-1.41 0L17 6.76l1.41 1.41 1.17-1.18a1 1 0 0 0 0-1.41z";
            case "shape-pen":
                return "M12 2L4 10v4l4 4h4l10-10L12 2zm0 3.83l6.17 6.17-8 8H7v-3.17l8-8z";
            case "shape-curve":
                return "M3 17c3-7 7-10 11-10s5 4 7 10h-2c-1.7-5.3-3.6-8-5-8s-6.5 2.7-9.2 8H3z";
            case "shape-anchor":
                return "M12 2a4 4 0 0 0-4 4c0 1.9 1.3 3.5 3 3.9V15H7v2h4v5h2v-5h4v-2h-4V9.9c1.7-.4 3-2 3-3.9a4 4 0 0 0-4-4zm0 2a2 2 0 1 1 0 4 2 2 0 0 1 0-4z";
            case "shape-divider":
                return "M3 11h4v2H3v-2zm7 0h4v2h-4v-2zm7 0h4v2h-4v-2z";
            case "shape-signature":
                return "M2.5 19.5c3-1 6-4 8-7s4-7 7-6 3 4 1 7-7 4-10 4-5-1-6 2zm13-10c-1 0-2 .5-2 1.5s1 2 2 2 2-1 2-2-1-1.5-2-1.5z";
            case "shape-watermark":
                return "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14zM8 8h8v2H8V8zm0 4h5v2H8v-2z";
            case "media":
            case "tool-media":
            case "media-image":
                return "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z";
            case "media-svg":
                return "M17 3a2.83 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3zm2.58 2.58a1 1 0 0 0-1.41 0L17 6.76l1.41 1.41 1.17-1.18a1 1 0 0 0 0-1.41z";
            case "code":
            case "tool-code":
            case "code-barcode":
                return "M2 4h3v16H2V4zm5 0h1v16H7V4zm3 0h3v16h-3V4zm5 0h2v16h-2V4zm4 0h1v16h-1V4zm3 0h2v16h-2V4z";
            case "code-qr":
                return "M3 3h8v8H3V3zm2 2v4h4V5H5zm8-2h8v8h-8V3zm2 2v4h4V5h-4zM3 13h8v8H3v-8zm2 2v4h4v-4H5zm13-2h3v2h-3v-2zm-5 0h3v2h-3v-2zm2 2h2v3h-2v-3zm3 0h3v3h-3v-3zm-5 3h2v3h-2v-3zm3 2h5v2h-5v-2z";
            case "business":
                return "M12 7V3H2v18h20V7H12zM6 19H4v-2h2v2zm0-4H4v-2h2v2zm0-4H4V9h2v2zm0-4H4V5h2v2zm4 12H8v-2h2v2zm0-4H8v-2h2v2zm0-4H8V9h2v2zm0-4H8V5h2v2zm10 12h-8v-2h2v-2h-2v-2h2v-2h-2V9h8v10zm-2-8h-2v2h2v-2zm0 4h-2v2h2v-2z";
            case "bank":
                return "M4 10v7h3v-7H4zm6 0v7h3v-7h-3zM2 22h19v-3H2v3zm14-12v7h3v-7h-3zm-4.5-9L2 6v2h19V6l-9.5-5z";
            case "billing":
                return "M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z";
            case "tag":
                return "M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7 4.67 7 5.5 6.33 7 5.5 7z";
            case "font":
                return "M9 4v3h5v12h3V7h5V4H9zm-6 8h3v7h3v-7h3V9H3v3z";
            case "backup":
                return "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z";
            case "tool-select":
                return "M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71L12 2z";
            case "tool-hand":
                return "M23 5.5V20c0 2.2-1.8 4-4 4h-7.3c-1.08 0-2.1-.43-2.85-1.19L1 14.83s1.26-1.23 1.3-1.25c.22-.19.49-.29.79-.29.22 0 .42.06.6.16.04.01 4.31 2.46 4.31 2.46V4c0-.83.67-1.5 1.5-1.5S11 3.17 11 4v7h1V1.5c0-.83.67-1.5 1.5-1.5S15 .67 15 1.5V11h1V2.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5V11h1V5.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5z";
            case "tool-line":
                return "M18.4 4.2L4.2 18.4l1.4 1.4L19.8 5.6l-1.4-1.4z";
            case "nav-back":
                return "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z";
            case "table":
                return "M3 3h18v18H3V3zm2 2v4h14V5H5zm0 6v6h6v-6H5zm8 0v6h6v-6h-6z";
            case "undo":
                return "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z";
            case "redo":
                return "M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.06-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z";
            case "help":
                return "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z";
            case "strip-preview":
                return "M2 4h6v7H2V4zm7 0h6v7H9V4zm7 0h6v7h-6V4zM2 13h6v7H2v-7zm7 0h6v7H9v-7zm7 0h6v7h-6v-7z";
            case "label-mode":
            case "tool-barcode":
                return "M2 4h3v16H2V4zm5 0h1v16H7V4zm3 0h3v16h-3V4zm5 0h2v16h-2V4zm4 0h1v16h-1V4zm3 0h2v16h-2V4z";
            case "magnet":
                return "M6 21v-8a6 6 0 0 1 12 0v8h-4v-8a2 2 0 0 0-4 0v8H6z";
            default:
                return null;
        }
    }
}
