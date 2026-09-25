#pragma once
#include <QFrame>
class QLineEdit;
class QPushButton;
namespace fin::ui {
class DataGrid;
class BillsView : public QFrame {
  Q_OBJECT
 public:
  explicit BillsView(QWidget* parent = nullptr);
  void refresh();
 private:
  DataGrid*    table_;
  QLineEdit*  search_;
  QPushButton* create_btn_;
};
} // namespace fin::ui
