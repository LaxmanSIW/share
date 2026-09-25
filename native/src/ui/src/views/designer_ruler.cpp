#include <QHBoxLayout>
// fin/ui/designer_ruler.cpp
//
// Implementation note: this is the C++ port's first concrete fulfilment of
// USER REQUIREMENT #7 (template designer ruler supports negative axis).
// The ruler can show any range [from_mm, to_mm] including negative values.
#include "fin/ui/views/designer_ruler.hpp"
#include "fin/services/template_engine.hpp"

#include <QPainter>
#include <QPainterPath>
#include <QMouseEvent>
#include <QPaintEvent>
#include <QString>
#include <cmath>

namespace fin::ui {

DesignerRuler::DesignerRuler(Orientation o, QWidget* parent)
  : QWidget(parent), orientation_(o) {
  setAttribute(Qt::WA_TransparentForMouseEvents, false);
  setStyleSheet("background-color: #1A222D; border: none;");
  if (o == Orientation::Horizontal) setFixedHeight(24);
  else                              setFixedWidth(24);
}

void DesignerRuler::set_range_mm(std::int64_t from, std::int64_t to) {
  from_mm_ = from;
  to_mm_   = to;
  update();
}

void DesignerRuler::set_pixels_per_mm(double ppm) {
  pixels_per_mm_ = ppm > 0.0 ? ppm : 1.0;
  update();
}

void DesignerRuler::set_origin_offset_mm(std::int64_t offset) {
  origin_offset_mm_ = offset;
  update();
}

void DesignerRuler::paintEvent(QPaintEvent* /*e*/) {
  QPainter p(this);
  p.setRenderHint(QPainter::Antialiasing, true);

  // Background already painted by QSS; just paint ticks + labels.
  // Compute the ticks via the pure-C++ ruler_ticks() function. USER
  // REQUIREMENT #7: this can include negative values.
  auto ticks = fin::services::ruler_ticks(from_mm_, to_mm_);
  for (const auto& t : ticks) {
    // Convert mm position to pixels relative to the widget's top-left.
    // The ruler's 0 mark is at (origin_offset_mm_ * pixels_per_mm_) from
    // the left/top of the widget. Negative mm positions land to the left/above
    // of that origin point.
    double offset_px = (t.position_mm + origin_offset_mm_) * pixels_per_mm_;
    int tick_length = 4;  // minor (1mm)
    QColor tick_color = tick_color_;
    if (t.magnitude >= 10) {
      tick_length = 12;
      tick_color = major_color_;
    } else if (t.magnitude >= 5) {
      tick_length = 8;
      tick_color = major_color_.darker(120);
    }

    if (orientation_ == Orientation::Horizontal) {
      int x = static_cast<int>(std::round(offset_px));
      p.setPen(QPen(tick_color, 1));
      p.drawLine(x, height() - tick_length, x, height() - 1);
      // Major ticks get a label.
      if (t.magnitude >= 10) {
        p.setPen(QPen(tick_color, 1));
        p.drawText(x + 2, 10, QString::number(t.position_mm));
      }
    } else {
      int y = static_cast<int>(std::round(offset_px));
      p.setPen(QPen(tick_color, 1));
      p.drawLine(width() - tick_length, y, width() - 1, y);
      if (t.magnitude >= 10) {
        p.save();
        p.translate(8, y - 2);
        p.rotate(-90);
        p.drawText(0, 0, QString::number(t.position_mm));
        p.restore();
      }
    }
  }

  // Highlight the zero mark (origin) with a gold line — visible even when
  // it sits at a negative position. This is the visual cue that the ruler
  // supports negative coordinates.
  double zero_px = origin_offset_mm_ * pixels_per_mm_;
  p.setPen(QPen(QColor("#D9A13B"), 2));
  if (orientation_ == Orientation::Horizontal) {
    p.drawLine(static_cast<int>(zero_px), 0, static_cast<int>(zero_px), height());
  } else {
    p.drawLine(0, static_cast<int>(zero_px), width(), static_cast<int>(zero_px));
  }
}

void DesignerRuler::mousePressEvent(QMouseEvent* e) {
  // User clicked on the ruler → create a guide at this mm position.
  double pos_px = (orientation_ == Orientation::Horizontal) ? e->position().x() : e->position().y();
  std::int64_t mm = static_cast<std::int64_t>(pos_px / pixels_per_mm_) - origin_offset_mm_;
  emit clicked_at_mm(mm);
}

} // namespace fin::ui
