// fin/ui/views/template_designer.cpp
#include "fin/ui/views/template_designer.hpp"
#include "fin/ui/views/designer_canvas.hpp"
#include "fin/ui/views/designer_ruler.hpp"
#include "fin/ui/views/designer_toolbar.hpp"
#include "fin/ui/views/designer_property_inspector.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/services/template_engine.hpp"

#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGridLayout>
#include <QLabel>
#include <QFrame>

namespace fin::ui {

TemplateDesignerView::TemplateDesignerView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);

  auto* root = new QVBoxLayout(this);
  root->setContentsMargins(0, 0, 0, 0);
  root->setSpacing(0);

  // Toolbar across the top.
  toolbar_ = new DesignerToolbar(this);
  root->addWidget(toolbar_);

  // Body: [center_grid (corner + h-ruler + v-ruler + canvas) | property_inspector]
  auto* body = new QFrame(this);
  body->setStyleSheet("background-color: transparent;");
  auto* body_l = new QHBoxLayout(body);
  body_l->setContentsMargins(0, 0, 0, 0);
  body_l->setSpacing(0);

  auto* center_grid = new QGridLayout();
  center_grid->setContentsMargins(0, 0, 0, 0);
  center_grid->setSpacing(0);

  // Top-left corner cell (24×24) where the two rulers meet.
  auto* corner = new QFrame();
  corner->setFixedSize(24, 24);
  corner->setStyleSheet("background-color: #1A222D; border: none;");
  center_grid->addWidget(corner, 0, 0);

  // Horizontal ruler (top row, 24px tall).
  h_ruler_ = new DesignerRuler(DesignerRuler::Orientation::Horizontal);
  // USER REQUIREMENT #7: pass a range that includes negative values so the
  // ruler extends past the page origin. We default to [-50, +250] so a
  // standard A4 (210mm wide) plus 50mm of negative bleed is fully visible.
  h_ruler_->set_range_mm(-50, 250);
  h_ruler_->set_origin_offset_mm(50);   // shift origin 50px right so -50mm shows
  center_grid->addWidget(h_ruler_, 0, 1);

  // Vertical ruler (left column, 24px wide).
  v_ruler_ = new DesignerRuler(DesignerRuler::Orientation::Vertical);
  v_ruler_->set_range_mm(-50, 300);     // negative axis (USER REQUIREMENT #7)
  v_ruler_->set_origin_offset_mm(50);
  center_grid->addWidget(v_ruler_, 1, 0);

  // Canvas (main cell).
  canvas_ = new DesignerCanvas();
  canvas_->set_origin_offset_mm(50.0, 50.0);   // visible page sits 50mm inside the canvas
  center_grid->addWidget(canvas_, 1, 1);
  center_grid->setColumnStretch(1, 1);
  center_grid->setRowStretch(1, 1);

  body_l->addLayout(center_grid, 1);

  // Property inspector on the right.
  inspector_ = new DesignerPropertyInspector(body);
  body_l->addWidget(inspector_);

  root->addWidget(body, 1);

  // Status bar at the bottom.
  auto* status_bar = new QFrame(this);
  status_bar->setFixedHeight(24);
  status_bar->setStyleSheet("background-color: #0B0E13; border-top: 1px solid #232B38;");
  auto* sb_l = new QHBoxLayout(status_bar);
  sb_l->setContentsMargins(12, 0, 12, 0);
  status_label_ = new QLabel("Ready — ruler supports negative axis (USER REQ #7)", status_bar);
  status_label_->setStyleSheet("color: #94A3B8; font-size: 11px;");
  sb_l->addWidget(status_label_);
  sb_l->addStretch();
  auto* zoom_label = new QLabel("100%", status_bar);
  zoom_label->setStyleSheet("color: #94A3B8; font-size: 11px;");
  sb_l->addWidget(zoom_label);
  root->addWidget(status_bar);
}

void TemplateDesignerView::load_template(std::shared_ptr<fin::services::Template> tpl) {
  template_ = std::move(tpl);
  if (canvas_) canvas_->load_template(template_);
}

} // namespace fin::ui
