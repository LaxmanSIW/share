#include "fin/ui/views/designer_property_inspector.hpp"
#include "fin/ui/ui_theme.hpp"
#include <QFormLayout>
#include <QLineEdit>
#include <QLabel>
namespace fin::ui {
DesignerPropertyInspector::DesignerPropertyInspector(QWidget* parent) : QFrame(parent) {
  setProperty("class", "card");
  setFixedWidth(280);
  setStyleSheet("background-color: #151B25; border-left: 1px solid #232B38;");
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(12, 12, 12, 12);
  l->setSpacing(8);
  l->addWidget(UiTheme::cardTitle("Properties", this));

  form_ = new QFormLayout();
  form_->setSpacing(8);
  x_field_ = new QLineEdit(this); x_field_->setPlaceholderText("0");
  y_field_ = new QLineEdit(this); y_field_->setPlaceholderText("0");
  w_field_ = new QLineEdit(this); w_field_->setPlaceholderText("50");
  h_field_ = new QLineEdit(this); h_field_->setPlaceholderText("20");
  form_->addRow("X (mm):", x_field_);
  form_->addRow("Y (mm):", y_field_);
  form_->addRow("W (mm):", w_field_);
  form_->addRow("H (mm):", h_field_);
  l->addLayout(form_);
  l->addStretch();
}
void DesignerPropertyInspector::load_element(const std::string& /*element_id*/) {
  // Phase 5c: load bounds + style + visibility + lock from the selected element.
}
} // namespace fin::ui
