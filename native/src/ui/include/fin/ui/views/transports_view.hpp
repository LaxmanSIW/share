#pragma once
#include <QFrame>
class QLineEdit;
class QPushButton;
namespace fin::ui {
class DataGrid;
class TransportsView : public QFrame {
  Q_OBJECT
 public:
  explicit TransportsView(QWidget* parent = nullptr);
  void refresh();
 private:
  void add_record_();
  DataGrid*    table_{nullptr};
  QLineEdit*  search_{nullptr};
  QPushButton* add_btn_{nullptr};
};
} // namespace fin::ui
