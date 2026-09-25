#pragma once
#include <QFrame>
class QLineEdit;
class QPushButton;
namespace fin::ui {
class DataGrid;
class ItemsView : public QFrame {
  Q_OBJECT
 public:
  explicit ItemsView(QWidget* parent = nullptr);
  void refresh();
 private:
  void add_item_();
  DataGrid*    table_;
  QLineEdit*  search_;
  QPushButton* add_btn_;
};
} // namespace fin::ui
