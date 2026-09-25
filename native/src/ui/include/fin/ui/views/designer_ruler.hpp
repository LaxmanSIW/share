// fin/ui/views/designer_ruler.hpp — Ruler widget (USER REQUIREMENT #7: negative axis)
//
// The Java original pinned the ruler origin to the top-left of the page; the
// C++ port removes that restriction so the user can drag the origin anywhere
// (including off-page), enabling:
//   - Negative coordinates for print registration marks that bleed off-page
//   - Multi-up label sheets where labels start at -5mm (calibration offset)
//   - Centerline-anchored templates (elements sit at negative coords)
//
// Skill §8: rulers in mm/inch/pt; zoom (wheel toward cursor, fit width/page, 100%).
#pragma once
#include <QWidget>
#include <QColor>

namespace fin::ui {

class DesignerRuler : public QWidget {
  Q_OBJECT
 public:
  enum class Orientation { Horizontal, Vertical };

  explicit DesignerRuler(Orientation o, QWidget* parent = nullptr);

  /// Set the visible range in mm. Can include NEGATIVE values per
  /// USER REQUIREMENT #7.
  void set_range_mm(std::int64_t from_mm, std::int64_t to_mm);

  /// Pixels per mm (zoom factor).
  void set_pixels_per_mm(double ppm);

  /// Origin offset in mm (where 0 is on the ruler). 0 = top-left of page.
  /// Negative values shift the visible range to the right; positive to the left.
  void set_origin_offset_mm(std::int64_t offset_mm);

  /// Tick color (defaults to #64748B).
  void set_tick_color(QColor c) { tick_color_ = c; update(); }

  /// Major tick color (defaults to #94A3B8).
  void set_major_color(QColor c) { major_color_ = c; update(); }

 signals:
  /// Emitted when the user clicks on the ruler (creates a guide at this position).
  void clicked_at_mm(std::int64_t mm);

 protected:
  void paintEvent(QPaintEvent* e) override;
  void mousePressEvent(QMouseEvent* e) override;

 private:
  Orientation   orientation_;
  std::int64_t  from_mm_{0};
  std::int64_t  to_mm_{210};
  std::int64_t  origin_offset_mm_{0};
  double        pixels_per_mm_{2.0};  // 2px/mm = 1:1 at 100% zoom, but renders as 50 DPI
  QColor        tick_color_{QColor("#64748B")};
  QColor        major_color_{QColor("#94A3B8")};
};

} // namespace fin::ui
