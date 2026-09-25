#include <QHBoxLayout>
// fin/ui/widgets/bill_preview_pane.cpp
#include "fin/ui/widgets/bill_preview_pane.hpp"
#include "fin/ui/views/designer_canvas.hpp"
#include "fin/services/template_engine.hpp"
#include "fin/model/bill.hpp"

#include <QVBoxLayout>

namespace fin::ui {

BillPreviewPane::BillPreviewPane(QWidget* parent) : QFrame(parent) {
  setProperty("class", "card");
  setStyleSheet("background-color: #151B25; border: 1px solid #232B38; border-radius: 8px;");
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(0, 0, 0, 0);
  l->setSpacing(0);

  canvas_ = new DesignerCanvas(this);
  l->addWidget(canvas_);
}

void BillPreviewPane::update_preview(std::shared_ptr<fin::model::Bill> bill,
                                       std::shared_ptr<fin::services::Template> tpl) {
  if (!tpl) return;
  // Inject the bill's variables into the layout's data field map.
  fin::services::LayoutInput in;
  in.tpl = tpl.get();
  if (bill) {
    for (const auto& [k, v] : bill->variables) {
      in.data[k] = v;
    }
    in.data["bill_no"] = bill->bill_no;
    in.data["date"]    = bill->date;
    in.data["grand_total"] = bill->totals.grand_total.to_decimal_string();
    in.data["subtotal"] = bill->totals.subtotal.to_decimal_string();
  }
  auto result = fin::services::run_layout(in);
  // The canvas_ takes ownership of the display list; we use a tiny adapter
  // here that re-renders via the canvas's load_template which re-runs layout
  // internally. (Phase 6 simplification: real impl keeps the LayoutResult on
  // the canvas for direct display_list swap.)
  canvas_->load_template(tpl);
}

void BillPreviewPane::set_zoom(double ppm) {
  canvas_->set_zoom_ppm(ppm);
}

double BillPreviewPane::zoom() const noexcept {
  return canvas_->zoom_ppm();
}

} // namespace fin::ui
