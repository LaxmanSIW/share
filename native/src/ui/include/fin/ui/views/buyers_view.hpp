// fin/ui/views/buyers_view.hpp — Buyers directory (port of Java BuyersView.java)
#pragma once
#include <QFrame>
class QLineEdit;
class QPushButton;

namespace fin::ui {
class DataGrid;
class BuyersView : public QFrame {
  Q_OBJECT
 public:
  explicit BuyersView(QWidget* parent = nullptr);
  void refresh();
 private:
  void add_buyer_();
  void edit_buyer_(int row);
  void delete_buyer_();
  DataGrid*    table_;
  QLineEdit*  search_;
  QPushButton* add_btn_;
  QPushButton* edit_btn_;
  QPushButton* del_btn_;
};
} // namespace fin::ui
