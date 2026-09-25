#pragma once
#include <QFrame>
class QLineEdit;
class QPushButton;
namespace fin::ui {
class DataGrid;
class SuppliersView : public QFrame {
  Q_OBJECT
 public:
  explicit SuppliersView(QWidget* parent = nullptr);
  void refresh();
 private:
  void add_supplier_();
  DataGrid*    table_;
  QLineEdit*  search_;
  QPushButton* add_btn_;
};
} // namespace fin::ui
