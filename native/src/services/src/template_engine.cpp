// fin/services/template_engine.cpp — Layout engine implementation.
//
// Phase 5c skeleton: the layout engine currently emits one DisplayCommand
// per element (FillRect for shapes, DrawGlyphRun for text). Real impl will
// add: HarfBuzz shaping + ICU line breaking (UAX #14), BiDi (UAX #9),
// pagination, text measure cache (skill §4), expression evaluation
// (skill §3), incremental layout (skill §4 incremental).
//
// USER REQUIREMENT #7 (ruler supports negative axis) is FULLY implemented
// below — ruler_ticks() accepts any range including negative numbers.
#include "fin/services/template_engine.hpp"

#include <algorithm>
#include <cmath>

namespace fin::services {

LayoutResult run_layout(const LayoutInput& in) {
  LayoutResult out;
  if (!in.tpl) {
    out.errors.push_back("null template");
    return out;
  }

  for (const auto& el : in.tpl->elements) {
    if (!el.visible) continue;

    DisplayCommand cmd;
    switch (el.type) {
      case ElementType::Text:
      case ElementType::RichText: {
        // Resolve the text: if expression is set, evaluate against `data`.
        // Phase 5c skeleton: just use el.text directly.
        std::string text = el.text;
        if (!el.expression.empty()) {
          auto it = in.data.find(el.expression);
          if (it != in.data.end()) text = it->second;
          else out.warnings.push_back("field not found: " + el.expression);
        }
        if (text.empty()) continue;
        cmd.op = DisplayOp::DrawGlyphRun;
        // Phase 5c: real impl shapes via HarfBuzz. For now we emit the raw
        // text as a single glyph run; the screen backend (QPainter) re-shapes
        // on the fly using QFontMetrics — same as the Java original's first
        // implementation.
        DisplayCommand::GlyphRun_ gr;
        gr.font_id = el.style_ref.empty() ? "default" : el.style_ref;
        gr.size = 12.0;
        gr.color_argb = 0xFFF4F4F5;
        // Pack the text as ASCII code points (placeholder for real glyph ids).
        for (char c : text) gr.glyph_ids.push_back(static_cast<std::uint32_t>(c));
        // Lay out characters left-to-right starting at bounds.origin.
        double cursor_x = el.bounds.origin.x.to_mm();
        double cursor_y = el.bounds.origin.y.to_mm();
        double advance = 7.0;  // approximated char width in mm at 12pt
        for (auto _ : gr.glyph_ids) {
          (void)_;
          gr.positions.push_back(Point{Fixed::from_mm(cursor_x), Fixed::from_mm(cursor_y)});
          cursor_x += advance;
        }
        cmd.payload = std::move(gr);
        out.display_list.push_back(std::move(cmd));
        break;
      }
      case ElementType::Rectangle: {
        cmd.op = DisplayOp::FillRect;
        DisplayCommand::Rect_ r{el.bounds.origin.x, el.bounds.origin.y,
                                 el.bounds.size.w, el.bounds.size.h};
        cmd.payload = r;
        out.display_list.push_back(std::move(cmd));
        break;
      }
      case ElementType::Image: {
        if (el.image_resource_id.empty()) continue;
        cmd.op = DisplayOp::DrawImage;
        DisplayCommand::Image_ img;
        img.resource_id = el.image_resource_id;
        img.dst = el.bounds;
        img.smooth = true;
        cmd.payload = std::move(img);
        out.display_list.push_back(std::move(cmd));
        break;
      }
      // Phase 5c: Line / Ellipse / Table / Barcode / QR / Chart / SubReport / Checkbox
      // will be added as the designer canvas needs them. The display-list
      // protocol above already supports them via the Path_ / Barcode_ variants.
      default:
        break;
    }
  }
  return out;
}

// USER REQUIREMENT #7: ruler supports negative axis.
//
// The Java original's RulerVerify test (share-java/src/test/java/RulerVerify.java)
// has asserts like `ruler_ticks(0, 100)` returning the expected pattern. In the
// C++ port we generalise: `ruler_ticks(-50, 50)` returns ticks from -50mm to
// +50mm, with major ticks at every 10mm (including -10, 0, 10, 20, ...).
//
// Why this matters per user requirement: real-world templates sometimes need
// negative coordinates for:
//   - Print registration marks that bleed off-page
//   - Multi-up label sheets where the first label starts above/left of the
//     page's printable area (calibration offset)
//   - Fold/bleed lines that extend past the trim
//   - Designers who prefer to anchor elements relative to a centerline (0,0)
//     rather than top-left, so elements can sit at negative coordinates
//
// The function takes any range [from_mm, to_mm] (including negative from_mm)
// and emits ticks at 1mm minor, 5mm mid, 10mm major.
std::vector<RulerTick> ruler_ticks(std::int64_t from_mm, std::int64_t to_mm) {
  std::vector<RulerTick> out;
  if (from_mm > to_mm) std::swap(from_mm, to_mm);
  out.reserve(static_cast<std::size_t>(to_mm - from_mm + 1));
  for (std::int64_t p = from_mm; p <= to_mm; ++p) {
    std::int64_t mag = 1;
    if (p % 10 == 0)      mag = 10;
    else if (p % 5 == 0)  mag = 5;
    out.push_back({p, mag});
  }
  return out;
}

} // namespace fin::services
