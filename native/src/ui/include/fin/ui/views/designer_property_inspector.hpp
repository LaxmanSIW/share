#pragma once
#include <QFrame>
class QFormLayout;
class QLineEdit;
namespace fin::ui {
class DesignerPropertyInspector : public QFrame {
  Q_OBJECT
 public:
  explicit DesignerPropertyInspector(QWidget* parent = nullptr);
  /// Load the properties of the element with the given id (empty = none selected).
  void load_element(const std::string& element_id);
 private:
  QFormLayout* form_;
  QLineEdit* x_field_;
  QLineEdit* y_field_;
  QLineEdit* w_field_;
  QLineEdit* h_field_;
};
} // namespace fin::ui
