// fin/ui/views/designer_canvas.hpp — Template Designer canvas (port of Java TemplateDesigner.java)
//
// Skill rule (template-designer-and-rendering §6): canvas = display-list
// render + separate interaction overlay + R-tree hit testing.
//
// We use a plain QWidget (not QGraphicsView) because we want full control
// over the rendering pipeline: the same display list is rendered to screen,
// PDF, and printer. QGraphicsView's per-item painting would diverge from PDF.
#pragma once
#include <QWidget>
#include <memory>
#include <QImage>

namespace fin::services {
  struct Template;
  struct LayoutResult;
  class LayoutInput;
}

namespace fin::ui {

class DesignerCanvas : public QWidget {
  Q_OBJECT
 public:
  explicit DesignerCanvas(QWidget* parent = nullptr);

  /// Load a template. Re-renders immediately.
  void load_template(std::shared_ptr<fin::services::Template> tpl);

  /// Zoom (pixels per mm). Default 2.0 = 50 DPI screen preview.
  void set_zoom_ppm(double ppm);
  double zoom_ppm() const noexcept { return zoom_ppm_; }

  /// Set the origin offset so the canvas can scroll into negative coordinates.
  /// USER REQUIREMENT #7: this lets elements at negative positions be visible.
  void set_origin_offset_mm(double x_mm, double y_mm);

  /// Re-run the layout engine and repaint.
  void refresh();

 signals:
  /// Emitted when the user selects an element (clicks on it).
  void element_selected(const std::string& element_id);
  /// Emitted when an element is dragged/resized (for undo stack).
  void element_modified(const std::string& element_id);

 protected:
  void paintEvent(QPaintEvent* e) override;
  void mousePressEvent(QMouseEvent* e) override;
  void mouseMoveEvent(QMouseEvent* e) override;
  void mouseReleaseEvent(QMouseEvent* e) override;
  void wheelEvent(QWheelEvent* e) override;

 private:
  std::shared_ptr<fin::services::Template>      template_;
  std::unique_ptr<fin::services::LayoutResult>   layout_;
  double                                          zoom_ppm_{2.0};
  double                                          origin_x_mm_{0.0};
  double                                          origin_y_mm_{0.0};
  QImage                                          cached_bg_;   // cached page background (skill §6: paint cheap)
};

} // namespace fin::ui
