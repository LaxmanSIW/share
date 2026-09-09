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

    public static Label createIconLabel(String name, double size, String colorHex) {
        Label lbl = new Label(getFallbackGlyph(name));
        lbl.setStyle("-fx-font-size: " + (int) size + "px; -fx-text-fill: " + (colorHex != null ? colorHex : "#CBD5E1") + "; -fx-alignment: center;");
        return lbl;
    }

    public static Node createButtonGraphic(String iconName, String colorHex) {
        return getIcon(iconName, 14, colorHex);
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
            default: return "•";
        }
    }

    private static String getSvgPath(String name) {
        if (name == null) return null;
        switch (name.toLowerCase()) {
            case "dashboard":
                return "M3 3h7v7H3V3zm11 0h7v7h-7V3zm0 11h7v7h-7v-7zM3 14h7v7H3v-7z";
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
            default:
                return null;
        }
    }
}
