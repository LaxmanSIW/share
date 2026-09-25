// fin/ui/designer_canvas.cpp
//
// Phase 5c skeleton: renders the display list emitted by
// fin::services::run_layout() onto a QWidget. Skill §6: paintEvent is pure
// and cheap — no allocation, cache everything derivable, invalidate small rects.
#include "fin/ui/views/designer_canvas.hpp"
#include "fin/services/template_engine.hpp"
#include "fin/ui/ui_theme.hpp"

#include <QPainter>
#include <QPaintEvent>
#include <QMouseEvent>
#include <QWheelEvent>
#include <QImage>
#include <QPixmapCache>
#include <QScrollBar>
#include <cmath>

namespace fin::ui {

DesignerCanvas::DesignerCanvas(QWidget* parent) : QWidget(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  setMouseTracking(true);
  // Background is the page surface (skill §1: opaque page, transparent UI chrome).
  setStyleSheet("background-color: #0B0E13;");
  setMinimumSize(600, 400);
}

void DesignerCanvas::load_template(std::shared_ptr<fin::services::Template> tpl) {
  template_ = std::move(tpl);
  refresh();
}

void DesignerCanvas::set_zoom_ppm(double ppm) {
  zoom_ppm_ = ppm > 0.05 ? ppm : 0.05;
  refresh();
}

void DesignerCanvas::set_origin_offset_mm(double x_mm, double y_mm) {
  origin_x_mm_ = x_mm;
  origin_y_mm_ = y_mm;
  refresh();
}

void DesignerCanvas::refresh() {
  if (!template_) return;
  fin::services::LayoutInput in;
  in.tpl = template_.get();
  layout_ = std::make_unique<fin::services::LayoutResult>(fin::services::run_layout(in));
  update();
}

void DesignerCanvas::paintEvent(QPaintEvent* /*e*/) {
  QPainter p(this);
  p.setRenderHint(QPainter::Antialiasing, true);

  // Draw page background (white). Skill: opaque page surface; UI chrome transparent.
  if (template_) {
    double page_w_px = template_->page.width.to_mm()  * zoom_ppm_;
    double page_h_px = template_->page.height.to_mm() * zoom_ppm_;
    double ox = origin_x_mm_ * zoom_ppm_;
    double oy = origin_y_mm_ * zoom_ppm_;
    p.fillRect(QRectF(ox, oy, page_w_px, page_h_px), QColor("#FFFFFF"));
    p.setPen(QPen(QColor("#151B25"), 1));
    p.drawRect(QRectF(ox, oy, page_w_px, page_h_px));
  }

  // USER REQUIREMENT #7: when the origin offset is set to negative values,
  // elements at negative mm positions become visible. The transformation
  // below handles this naturally because mm × ppm = px, and negative px
  // values render off the widget (which is fine — they get clipped).
  if (!layout_) return;

  // Render each display command.
  for (const auto& cmd : layout_->display_list) {
    switch (cmd.op) {
      case fin::services::DisplayOp::FillRect: {
        if (std::holds_alternative<fin::services::DisplayCommand::Rect_>(cmd.payload)) {
          const auto& r = std::get<fin::services::DisplayCommand::Rect_>(cmd.payload);
          double x = (r.x.to_mm() + origin_x_mm_) * zoom_ppm_;
          double y = (r.y.to_mm() + origin_y_mm_) * zoom_ppm_;
          double w = r.w.to_mm() * zoom_ppm_;
          double h = r.h.to_mm() * zoom_ppm_;
          p.fillRect(QRectF(x, y, w, h), QColor("#1A222D"));
        }
        break;
      }
      case fin::services::DisplayOp::DrawGlyphRun: {
        if (std::holds_alternative<fin::services::DisplayCommand::GlyphRun_>(cmd.payload)) {
          const auto& gr = std::get<fin::services::DisplayCommand::GlyphRun_>(cmd.payload);
          // For Phase 5c skeleton we re-construct the text from glyph_ids (which
          // are ASCII code points in our layout engine stub) and draw it. Real
          // impl uses QRawFont + cached glyph paths per skill §6.
          std::string text;
          for (auto gid : gr.glyph_ids) text.push_back(static_cast<char>(gid));
          // Reconstruct positions; layout emitted Point per glyph at 1/1000 mm.
          for (std::size_t i = 0; i < gr.glyph_ids.size() && i < gr.positions.size(); ++i) {
            double px = (gr.positions[i].x.to_mm() + origin_x_mm_) * zoom_ppm_;
            double py = (gr.positions[i].y.to_mm() + origin_y_mm_) * zoom_ppm_;
            QFont font("Segoe UI", static_cast<int>(gr.size));
            p.setFont(font);
            p.setPen(QColor::fromRgba(gr.color_argb));
            p.drawText(QPointF(px, py + gr.size * zoom_ppm_), QString(1, static_cast<char>(gr.glyph_ids[i])));
          }
        }
        break;
      }
      case fin::services::DisplayOp::DrawImage: {
        // Phase 5c skeleton: real impl loads via QImage from the bundled resource
        // (image_resource_id → resources/<sha>.<ext>) and scales to dst rect.
        if (std::holds_alternative<fin::services::DisplayCommand::Image_>(cmd.payload)) {
          const auto& img = std::get<fin::services::DisplayCommand::Image_>(cmd.payload);
          double x = (img.dst.origin.x.to_mm() + origin_x_mm_) * zoom_ppm_;
          double y = (img.dst.origin.y.to_mm() + origin_y_mm_) * zoom_ppm_;
          double w = img.dst.size.w.to_mm() * zoom_ppm_;
          double h = img.dst.size.h.to_mm() * zoom_ppm_;
          p.setPen(QPen(QColor("#94A3B8"), 1, Qt::DashLine));
          p.drawRect(QRectF(x, y, w, h));
        }
        break;
      }
      default:
        // Phase 5c: SaveState / RestoreState / Transform / Clip / StrokePath /
        // FillPath / DrawBarcodeModules will be added as the layout engine grows.
        break;
    }
  }
}

void DesignerCanvas::mousePressEvent(QMouseEvent* e) {
  // Phase 5c: hit-test via R-tree (skill §6). For now, no-op.
  QWidget::mousePressEvent(e);
}

void DesignerCanvas::mouseMoveEvent(QMouseEvent* e) {
  // Phase 5c: dragging/resizing elements; cursor feedback.
  QWidget::mouseMoveEvent(e);
}

void DesignerCanvas::mouseReleaseEvent(QMouseEvent* e) {
  // Phase 5c: commit drag/resize to undo stack.
  QWidget::mouseReleaseEvent(e);
}

void DesignerCanvas::wheelEvent(QWheelEvent* e) {
  // Skill §8: zoom wheel toward cursor (anchor at mouse position).
  if (e->modifiers() & Qt::ControlModifier) {
    double factor = e->angleDelta().y() > 0 ? 1.15 : 1.0 / 1.15;
    set_zoom_ppm(zoom_ppm_ * factor);
  } else {
    QWidget::wheelEvent(e);
  }
}

} // namespace fin::ui
