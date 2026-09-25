// fin/ui/widgets/bill_preview_pane.hpp — Live bill preview using the assigned template
//
// Port of Java BillPreviewPane.java. Renders the bill using the assigned
// template via fin::services::run_layout + DesignerCanvas::paintEvent.
#pragma once
#include <QFrame>
#include <memory>

namespace fin::services { struct Template; }
namespace fin::model { struct Bill; }

namespace fin::ui {

class DesignerCanvas;

class BillPreviewPane : public QFrame {
  Q_OBJECT
 public:
  explicit BillPreviewPane(QWidget* parent = nullptr);

  /// Update the preview with the given bill + template. Uses a 150ms
  /// Debouncer (skill §3.5) so rapid field changes don't thrash the layout
  /// engine.
  void update_preview(std::shared_ptr<fin::model::Bill> bill,
                       std::shared_ptr<fin::services::Template> tpl);

  /// Zoom controls (skill §8: zoom wheel toward cursor).
  void set_zoom(double ppm);
  double zoom() const noexcept;

 private:
  DesignerCanvas* canvas_;
};

} // namespace fin::ui
