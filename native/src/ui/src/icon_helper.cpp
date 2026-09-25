#include <QApplication>
// fin/ui/icon_helper.cpp — SVG icon factory implementation.
//
// Strategy: each named icon has an SVG path (Material Design 24×24 viewBox)
// stored as a std::string_view constant. We build a complete SVG document
// string from the path + color, render it via QSvgRenderer to a QPixmap at
// the requested size with proper devicePixelRatio for HiDPI, and cache
// the result in an unordered_map keyed by (name, size, color.name()).
//
// USER REQUIREMENT: all icons are SVG. No native Qt button icons anywhere.
#include "fin/ui/icon_helper.hpp"

#include <QSvgRenderer>
#include <QPainter>
#include <QPainterPath>
#include <QPixmapCache>
#include <QString>
#include <QByteArray>
#include <unordered_map>
#include <string>
#include <string_view>

namespace fin::ui {

namespace {

/// Material Design 24×24 SVG path constants. The Java original embeds these
/// in IconHelper.java's pathFor(name) switch; we keep them here.
struct IconDef {
  const char* name;
  const char* path;
};

// We use the same Material Design paths the Java original uses (and add a
// few of our own for new C++ UI features).
constexpr IconDef kIcons[] = {
  {"dashboard",       "M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z"},
  {"dashboard2",     "M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z"},
  {"templates",       "M3 5v14h18V5H3zm16 12H5V7h14v10zM7 9h6v6H7z"},
  {"new",             "M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H6V4h7v5h5v9zm-7-3l-1.41-1.41L7 14.17 10.41 11 9 9.59 6.59 12 9 14.41 11 12z"},
  {"history",         "M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A9 9 0 1 0 13 3zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"},
  {"buyers",          "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"},
  {"items",           "M20 6h-3V4c0-1.1-.9-2-2-2H9c-1.1 0-2 .9-2 2v2H4c-1.1 0-2 .9-2 2v11c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-9-2h6v2h-6V4zM4 8h16v11H4V8z"},
  {"variables",       "M3 6h18v2H3V6zm0 5h18v2H3v-2zm0 5h18v2H3v-2z"},
  {"settings",        "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96a7.03 7.03 0 0 0-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.56-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.07.62-.07.94 0 .32.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.47.41h3.84c.24 0 .43-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.49.49 0 0 0-.12-.61l-2.03-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"},
  {"plus",            "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"},
  {"tag",              "M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58s1.05-.22 1.41-.58l7-7c.37-.36.59-.86.59-1.41s-.23-1.06-.59-1.42zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7 4.67 7 5.5 6.33 7 5.5 7z"},
  {"trending",        "M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z"},
  {"chart",           "M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z"},
  {"sparkles",        "M9.5 2L11 7.5 16.5 9 11 10.5 9.5 16 8 10.5 2.5 9 8 7.5zM17 13l.7 2.3L20 16l-2.3.7L17 19l-.7-2.3L14 16l2.3-.7zM6 14l.7 2.3L9 17l-2.3.7L6 20l-.7-2.3L3 17l2.3-.7z"},
  {"search",          "M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z"},
  {"edit",            "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"},
  {"delete",          "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"},
  {"check",           "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"},
  {"upload",          "M5 4v3H2v2h3v3l4-4-4-4zm7 0v3h-3l4 4 4-4h-3V4h-2zm-5 9v2h12v-2H7zm0 5v2h12v-2H7z"},
  {"download",        "M5 20v-3H2v-2h3v-3l4 4-4 4zm7-13v3h3l-4 4-4-4h3V7h2zm-5 8v2h12v-2H7zm0 5v2h12v-2H7z"},
  {"crosshair",       "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-1-13h2v6h-2zm0 8h2v2h-2z"},
  {"reports",         "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm-1 7V3.5L18.5 9H13z"},
  {"transactions",    "M3 5v14h18V5H3zm16 12H5V7h14v10zM7 9h10v2H7zm0 4h7v2H7z"},
  {"transport",       "M20 8h-3V4H3c-1.1 0-2 .9-2 2v11h2a3 3 0 0 0 6 0h6a3 3 0 0 0 6 0v-7l-3-2zM6 19a2 2 0 1 1-2-2 2 2 0 0 1 2 2zm12 0a2 2 0 1 1-2-2 2 2 0 0 1 2 2zm-1-7v3h-5v-3h5z"},
  {"categories",      "M12 2l-5.5 9h11z M5.5 9a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zm13 0a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM12 14a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z"},
  {"shapes",          "M3 3h8v8H3V3zm10 0h8v8h-8V3zM3 13h8v8H3v-8zm10 0h8v8h-8v-8z"},
  {"business",        "M12 7V3H2v18h20V7H12zM6 19H4v-2h2v2zm0-4H4v-2h2v2zm0-4H4V9h2v2zm0-4H4V5h2v2zm4 12H8v-2h2v2zm0-4H8v-2h2v2zm0-4H8V9h2v2zm0-4H8V5h2v2zm10 12h-8v-2h2v-2h-2v-2h2v-2h-2V9h8v10zm-2-8h-2v2h2v-2zm0 4h-2v2h2v-2z"},
  {"bank",            "M11.5 1L1 7v1h2v8h1V8h3v8h1V8h3v8h1V8h3v8h1V8h2v8h1V8h1V7L11.5 1zM4 22h2v-2h7v2h2v-2H4v2zm-2-3h2v-2H2v2zm14 0h2v-2h-2v2zm-9-3h2v-2H7v2zm6 0h2v-2h-2v2z"},
  {"billing",         "M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H6V4h7v5h5v9zM8 12h8v2H8zm0 4h8v2H8z"},
  {"font",            "M9.93 13.5l-1.36 4.05L7 17.5l4.5-13h1L17 17.5l-1.57.05L14.07 13.5h-4.14zM12 6.32L10.38 11.5h3.24L12 6.32z"},
  {"print",           "M19 8H5c-1.66 0-3 1.34-3 3v6h4v4h12v-4h4v-6c0-1.66-1.34-3-3-3zm-3 11H8v-5h8v5zm3-7c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1zm-1-9H6v4h12V3z"},
  {"backup",          "M19 12v7H5v-7H3v7c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2v-7h-2zm-6 .67l2.59-2.59L17 11.5l-5 5-5-5 1.41-1.42L11 12.67V3h2z"},
  {"mcp",              "M3 3h8v4H3V3zm10 0h8v8h-8V3zM3 9h8v4H3V9zm10 6h8v6h-8v-6zM3 15h8v6H3v-6z"},
  {"code-qr",          "M3 3h8v8H3V3zm2 2v4h4V5H5zm10-2h6v6h-6V3zm2 2v2h2V5h-2zM3 13h8v8H3v-8zm2 2v4h4v-4H5zm10-2h2v2h-2v-2zm4 0h2v2h-2v-2zm-4 4h2v2h-2v-2zm4 0h2v2h-2v-2zm-4 4h2v2h-2v-2zm4 0h2v2h-2v-2z"},
  {"code-barcode",    "M1 4h2v16H1V4zm4 0h1v16H5V4zm2 0h2v16H7V4zm3 0h2v16h-2V4zm3 0h2v16h-2V4zm3 0h1v16h-1V4zm3 0h2v16h-2V4z"},
  {"table",           "M3 3v18h18V3H3zm6 16H5v-4h4v4zm0-6H5V9h4v4zm0-6H5V5h4v2zm6 12h-4v-4h4v4zm0-6h-4V9h4v4zm0-6h-4V5h4v2zm4 12h-2v-4h2v4zm0-6h-2V9h2v4zm0-6h-2V5h2v2z"},
  {"undo",             "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.55 5.5l2.37-.78C21.43 11.38 17.42 8 12.5 8z"},
  {"redo",            "M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.92 0-8.93 3.38-10.02 7.84l2.37.78C4.85 13.31 7.86 11 11.5 11c1.96 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z"},
  {"help",             "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z"},
  {"chat",            "M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"},
  {"send",            "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"},
  {"paperclip",       "M16.5 6v11.5a4 4 0 0 1-8 0V5a2.5 2.5 0 0 1 5 0v10.5a1 1 0 0 1-2 0V6H10v9.5a2.5 2.5 0 0 0 5 0V5a4 4 0 0 0-8 0v12.5a5.5 5.5 0 0 0 11 0V6h-1.5z"},
  {"close",            "M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"},
  {"expand",           "M6.41 6 5 7.41 9.58 12 5 16.59 6.41 18l6-6zM13 6l-1.41 1.41L16.17 12l-4.58 4.59L13 18l6-6z"},
  {"person",          "M12 12c2.2 0 4-1.8 4-4s-1.8-4-4-4-4 1.8-4 4 1.8 4 4 4zm0 2c-2.7 0-8 1.3-8 4v2h16v-2c0-2.7-5.3-4-8-4z"},
  {"copy",             "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"},
  {"terminal",         "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8h16v10zm-2-1h-6v-2h6v2zM7.5 17l-1.41-1.41L8.67 13l-2.58-2.59L7.5 9l4 4-4 4z"},
  {"envelope",         "M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z"},
  {"key",              "M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z"},
};

/// Find the IconDef for a name.
const IconDef* find_def(std::string_view name) {
  for (const auto& d : kIcons) {
    if (name == d.name) return &d;
  }
  return nullptr;
}

} // namespace

QPixmap IconHelper::pixmap(std::string_view name, int pixel_size, const QColor& color) {
  if (pixel_size <= 0) pixel_size = 16;
  // Cache key: "<name>:<size>:<color>"
  std::string key = std::string(name) + ":" + std::to_string(pixel_size) + ":" + color.name().toStdString();
  // QPixmapCache is process-wide and thread-safe; key limit is 256KB by default.
  QPixmap cached;
  if (QPixmapCache::find(QString::fromStdString(key), &cached)) return cached;

  const IconDef* def = find_def(name);
  if (!def) {
    // Unknown icon → render a placeholder dot so missing icons are visible.
    cached = QPixmap(pixel_size, pixel_size);
    cached.fill(Qt::transparent);
    QPainter p(&cached);
    p.setRenderHint(QPainter::Antialiasing, true);
    p.setBrush(QColor("#EF4444"));
    p.setPen(Qt::NoPen);
    p.drawEllipse(0, 0, pixel_size, pixel_size);
    return cached;
  }

  // Build the SVG document.
  std::string svg =
    "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" "
    "fill=\"" + color.name().toStdString() + "\">"
    "<path d=\"" + def->path + "\"/>"
    "</svg>";

  QSvgRenderer renderer(QByteArray::fromStdString(svg));
  if (!renderer.isValid()) {
    cached = QPixmap(pixel_size, pixel_size);
    cached.fill(Qt::transparent);
    return cached;
  }
  // Render at the device pixel ratio for crisp HiDPI.
  qreal dpr = qApp ? qApp->devicePixelRatio() : 1.0;
  int render_size = static_cast<int>(pixel_size * dpr);
  QPixmap out(render_size, render_size);
  out.setDevicePixelRatio(dpr);
  out.fill(Qt::transparent);
  QPainter p(&out);
  p.setRenderHint(QPainter::Antialiasing, true);
  renderer.render(&p);
  QPixmapCache::insert(QString::fromStdString(key), out);
  return out;
}

QIcon IconHelper::icon(std::string_view name, int pixel_size, const QColor& color) {
  return QIcon(pixmap(name, pixel_size, color));
}

std::string_view IconHelper::svg_path(std::string_view name) {
  const IconDef* def = find_def(name);
  return def ? std::string_view(def->path) : std::string_view{};
}

} // namespace fin::ui
