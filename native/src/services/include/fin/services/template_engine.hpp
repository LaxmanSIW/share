// fin/services/template_engine.hpp — Pure-C++ layout engine + display list
//
// Skill rule (template-designer-and-rendering §1-5):
//   1. ONE pipeline: (template, data, fonts) → layout → display list → screen|PDF|printer
//   2. Layout is pure, deterministic, GUI-free; runs off UI thread; cacheable by hash
//   3. Geometry in fixed-point integers (1/1000 mm); stable element IDs; no device px in model
//   4. Expressions compiled once, type-checked, sandboxed (templates are untrusted input)
//   5. Display list = serializable vector of commands, renderable by any backend
//
// This file is the pure-C++ core (no Qt). The Qt canvas widget (TemplateDesignerView)
// wraps this for screen rendering + interaction.
#pragma once
#include "fin/money.hpp"

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <variant>
#include <vector>

namespace fin::services {

// === Geometry: fixed-point integers in 1/1000 mm ===
// Skill §2: never store device pixels in the model. Deterministic, diff-friendly.
//
// USER REQUIREMENT #7: ruler must support negative axis. The Java original's
// ruler is anchored at (0, 0) at the top-left of the page; the user requested
// that we allow the origin to move so the ruler can extend into negative
// coordinates (useful for templates that bleed off-page or are anchored at
// a corner of a multi-up label sheet).

/// Fixed-point coordinate in 1/1000 mm. Range: ~9.2 × 10^12 mm each side — enough
/// for any document or label layout. Use `from_mm(double)` / `to_mm()` for I/O.
struct Fixed {
  std::int64_t value{0};  // 1/1000 mm
  constexpr Fixed() = default;
  constexpr explicit Fixed(std::int64_t v) : value(v) {}
  static Fixed from_mm(double mm) { return Fixed{static_cast<std::int64_t>(mm * 1000.0)}; }
  static Fixed from_mm_int(std::int64_t mm) { return Fixed{mm * 1000}; }
  double to_mm() const noexcept { return static_cast<double>(value) / 1000.0; }
  constexpr Fixed operator+(Fixed o) const { return Fixed{value + o.value}; }
  constexpr Fixed operator-(Fixed o) const { return Fixed{value - o.value}; }
  constexpr Fixed operator-() const { return Fixed{-value}; }
  constexpr bool operator<(Fixed o) const { return value < o.value; }
  constexpr bool operator>(Fixed o) const { return value > o.value; }
  constexpr bool operator<=(Fixed o) const { return value <= o.value; }
  constexpr bool operator>=(Fixed o) const { return value >= o.value; }
  constexpr bool operator==(Fixed o) const { return value == o.value; }
  constexpr bool operator!=(Fixed o) const { return value != o.value; }
};

struct Point { Fixed x; Fixed y; };
struct Size  { Fixed w; Fixed h; };
struct Rect  { Point origin; Size size; };
inline constexpr Rect rect_from_corners(Point a, Point b) {
  Fixed x0 = a.x < b.x ? a.x : b.x;
  Fixed y0 = a.y < b.y ? a.y : b.y;
  Fixed x1 = a.x < b.x ? b.x : a.x;
  Fixed y1 = a.y < b.y ? b.y : a.y;
  return Rect{Point{x0, y0}, Size{x1 - x0, y1 - y0}};
}

// === Element types (matches Java ElementType enum) ===

enum class ElementType : std::uint8_t {
  Text = 0, RichText, Image, Line, Rectangle, Ellipse,
  Table, Barcode, QR, Chart, SubReport, Checkbox
};

// === Element model ===

struct TemplateElement {
  std::string id;              // stable UUID; survives undo/redo + clipboard
  ElementType type{ElementType::Text};
  Rect bounds;                  // in 1/1000 mm
  std::string style_ref;        // named style (looked up in Template::styles)
  std::string text;              // for Text/RichText elements
  std::string expression;        // for bound elements: "invoice.buyer.name"
  std::string image_resource_id;// for Image elements: SHA256 hash → resources/<sha>.<ext>
  std::uint8_t z_order{0};
  bool        locked{false};
  bool        visible{true};
  std::unordered_map<std::string, std::string> properties;  // type-specific
};

// === Page setup ===

enum class Orientation : std::uint8_t { Portrait = 0, Landscape = 1 };

struct PageSetup {
  Fixed width{Fixed::from_mm(210.0)};   // A4 default
  Fixed height{Fixed::from_mm(297.0)};
  Fixed margin_top{Fixed::from_mm(15.0)};
  Fixed margin_bottom{Fixed::from_mm(15.0)};
  Fixed margin_left{Fixed::from_mm(15.0)};
  Fixed margin_right{Fixed::from_mm(15.0)};
  Orientation orientation{Orientation::Portrait};
};

// === Template model ===

struct Template {
  std::string id;
  std::string name;
  std::string version{"1.0"};
  PageSetup page;
  std::vector<TemplateElement> elements;
  std::unordered_map<std::string, std::string> styles;  // named styles → CSS-like strings
  std::unordered_map<std::string, std::string> data_schema;  // field → type, for expression validation
};

// === Display list (skill §5) ===
//
// A compact, serializable vector of commands in device-independent units.
// Backends: QPainter (screen), QPdfWriter (PDF), QPrinter (printer).
// Text is emitted as glyph runs (already shaped), so backends do not re-shape.

enum class DisplayOp : std::uint8_t {
  SaveState = 0, RestoreState, Transform, ClipRect, ClipPath,
  FillRect, StrokePath, FillPath, DrawGlyphRun, DrawImage, DrawBarcodeModules
};

struct DisplayCommand {
  DisplayOp op;
  // Union of payload variants; we use std::variant for type safety.
  struct Transform_ { double m11, m12, m21, m22, dx, dy; };
  struct Rect_      { Fixed x, y, w, h; };
  struct Path_      { std::string path_d; };
  struct GlyphRun_  { std::string font_id; std::vector<std::uint32_t> glyph_ids; std::vector<Point> positions; double size; std::uint32_t color_argb; };
  struct Image_     { std::string resource_id; Rect dst; std::optional<Rect> src; bool smooth; };
  struct Barcode_   { std::vector<std::uint8_t> modules; Rect dst; };

  std::variant<std::monostate, Transform_, Rect_, Path_, GlyphRun_, Image_, Barcode_> payload;
};

using DisplayList = std::vector<DisplayCommand>;

// === Layout engine (skill §2 — pure, deterministic, GUI-free) ===

struct LayoutInput {
  const Template* tpl;
  std::unordered_map<std::string, std::string> data;  // field path → string value
  // fonts: bundled font resource ids
};

struct LayoutResult {
  DisplayList display_list;
  std::vector<std::string> warnings;  // e.g. "field X not found, used blank"
  std::vector<std::string> errors;
};

/// Run the layout engine. Pure function — same inputs → byte-identical output.
/// Cacheable by hash. Runnable on any thread.
LayoutResult run_layout(const LayoutInput& in);

// === Ruler ticks (USER REQUIREMENT #7: ruler must support negative axis) ===

struct RulerTick {
  std::int64_t position_mm;   // can be NEGATIVE — the user explicitly requested this
                              // so the ruler can extend beyond the page origin
                              // (e.g. for label sheets where labels start at -5mm
                              // because of a calibration offset, or for templates
                              // that bleed off-page for print registration marks).
  std::int64_t magnitude;      // 1=minor (1mm), 5=mid (5mm), 10=major (10mm)
};

/// Compute ruler ticks for the visible range [from_mm, to_mm].
/// USER REQUIREMENT #7: the range may include negative values. The Java original
/// only ever passed non-negative ranges because the origin was hard-pinned to
/// the top-left of the page; the C++ port removes that restriction.
std::vector<RulerTick> ruler_ticks(std::int64_t from_mm, std::int64_t to_mm);

} // namespace fin::services
