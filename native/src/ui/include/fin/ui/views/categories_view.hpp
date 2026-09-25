#pragma once
#include <QFrame>
class QLineEdit;
class QPushButton;
namespace fin::ui {
class DataGrid;
class CategoriesView : public QFrame {
  Q_OBJECT
 public:
  explicit CategoriesView(QWidget* parent = nullptr);
  void refresh();
 private:
  void add_record_();
  DataGrid*    table_{nullptr};
  QLineEdit*  search_{nullptr};
  QPushButton* add_btn_{nullptr};
};
} // namespace fin::ui
