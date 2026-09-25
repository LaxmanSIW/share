// fin/ui/icon_helper.hpp — SVG icon factory (port of Java IconHelper.java)
//
// Loads Material Design 24×24 SVG path data and renders them as QIcon/QPixmap
// in any color at any size. The Java original embeds ~80 SVG path constants
// in IconHelper.java; this file embeds the same paths (and adds new ones for
// features unique to the C++ port).
//
// USER REQUIREMENT: all icons throughout the application are SVG, not system
// defaults. Every icon used in the UI goes through IconHelper::icon() so we
// have a single point of control over the icon set.
#pragma once
#include <QIcon>
#include <QPixmap>
#include <QString>
#include <QColor>
#include <string_view>

namespace fin::ui {

class IconHelper {
 public:
  // === Icon name constants (matches Java IconHelper.ICON_*) ===
  // Sidebar nav
  static constexpr const char* ICON_DASHBOARD     = "dashboard";
  static constexpr const char* ICON_TEMPLATES     = "templates";
  static constexpr const char* ICON_RECEIPT        = "new";          // create bill
  static constexpr const char* ICON_HISTORY        = "history";
  static constexpr const char* ICON_USERS          = "buyers";
  static constexpr const char* ICON_PACKAGE         = "items";
  static constexpr const char* ICON_VARIABLE        = "variables";
  static constexpr const char* ICON_SETTINGS        = "settings";
  static constexpr const char* ICON_PLUS           = "plus";
  static constexpr const char* ICON_TAG            = "tag";
  static constexpr const char* ICON_TRENDING_UP    = "trending";
  static constexpr const char* ICON_BAR_CHART      = "chart";
  static constexpr const char* ICON_SPARKLES        = "sparkles";
  static constexpr const char* ICON_SEARCH         = "search";
  static constexpr const char* ICON_EDIT           = "edit";
  static constexpr const char* ICON_TRASH          = "delete";
  static constexpr const char* ICON_CHECK          = "check";
  static constexpr const char* ICON_UPLOAD         = "upload";
  static constexpr const char* ICON_DOWNLOAD       = "download";
  static constexpr const char* ICON_CROSSHAIR      = "crosshair";
  static constexpr const char* ICON_REPORTS        = "reports";
  static constexpr const char* ICON_TRANSACTIONS   = "transactions";
  static constexpr const char* ICON_TRANSPORT       = "transport";
  static constexpr const char* ICON_CATEGORIES      = "categories";
  static constexpr const char* ICON_DASHBOARD2     = "dashboard2";
  static constexpr const char* ICON_SHAPES         = "shapes";
  static constexpr const char* ICON_BUSINESS       = "business";
  static constexpr const char* ICON_BANK           = "bank";
  static constexpr const char* ICON_BILLING        = "billing";
  static constexpr const char* ICON_FIELDS         = "tag";
  static constexpr const char* ICON_FONT           = "font";
  static constexpr const char* ICON_PRINT          = "print";
  static constexpr const char* ICON_BACKUP         = "backup";
  static constexpr const char* ICON_MCP            = "mcp";
  static constexpr const char* ICON_CODE_QR       = "code-qr";
  static constexpr const char* ICON_CODE_BARCODE  = "code-barcode";
  static constexpr const char* ICON_TABLE         = "table";
  static constexpr const char* ICON_UNDO           = "undo";
  static constexpr const char* ICON_REDO           = "redo";
  static constexpr const char* ICON_HELP           = "help";
  static constexpr const char* ICON_CHAT           = "chat";
  static constexpr const char* ICON_SEND          = "send";
  static constexpr const char* ICON_PAPERCLIP     = "paperclip";
  static constexpr const char* ICON_CLOSE         = "close";
  static constexpr const char* ICON_EXPAND        = "expand";
  static constexpr const char* ICON_PERSON        = "person";
  static constexpr const char* ICON_COPY          = "copy";
  static constexpr const char* ICON_TERMINAL      = "terminal";
  static constexpr const char* ICON_ENVELOPE      = "envelope";
  static constexpr const char* ICON_KEY           = "key";

  /// Render the named SVG icon as a QPixmap of the given pixel size, in the
  /// given color. Caches the result for fast repeated lookups.
  /// Returns an empty QPixmap if the icon name is unknown.
  static QPixmap pixmap(std::string_view name, int pixel_size = 16,
                        const QColor& color = QColor("#94A3B8"));

  /// Convenience: wrap pixmap() in a QIcon (for use with QPushButton::setIcon,
  /// QAction::setIcon, QToolButton).
  static QIcon icon(std::string_view name, int pixel_size = 16,
                     const QColor& color = QColor("#94A3B8"));

  /// Returns the raw SVG path data ("d" attribute) for the named icon.
  /// Used by the template designer's vector canvas (skill: emit vector
  /// modules into the display list, not raster).
  static std::string_view svg_path(std::string_view name);
};

} // namespace fin::ui
