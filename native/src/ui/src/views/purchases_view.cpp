#include "fin/ui/views/purchases_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/db/daos2.hpp"
#include "fin/services/auth_session.hpp"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLineEdit>
#include <QPushButton>
#include <QStandardItemModel>
#include <QTimer>

namespace fin::ui {

PurchasesView::PurchasesView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  auto* header = UiTheme::row(12, this);
  auto* tb = new QVBoxLayout(); tb->setSpacing(2);
  tb->addWidget(UiTheme::pageTitle("Purchases"));
  tb->addWidget(UiTheme::pageSubtitle("Purchase bills register"));
  static_cast<QHBoxLayout*>(header->layout())->addItem(tb);
  static_cast<QHBoxLayout*>(header->layout())->addItem(UiTheme::hspacer());
  add_btn_ = UiTheme::primaryIconButton(IconHelper::ICON_PLUS, "Add Purchase");
  header->layout()->addWidget(add_btn_);
  l->addWidget(header);
  search_ = UiTheme::lineEdit("Search…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);
  table_ = new DataGrid(this);
  table_->set_columns({{"Bill No", 140}, {"Date", 100}, {"Supplier", 180}, {"Total", 120}, {"Status", 90}, {"", -1}});
  l->addWidget(table_, 1);
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void PurchasesView::refresh() {
  auto* model = new QStandardItemModel(0, 6, this);
  model->setHorizontalHeaderLabels({"Bill No", "Date", "Supplier", "Total", "Status", ""});
  try {
    fin::db::PurchaseBillDao dao(fin::db::DatabaseManager::instance());
    auto bills = dao.find_all();
    for (const auto& p : bills) {
      QList<QStandardItem*> row;
      row << new QStandardItem(QString::fromStdString(p.bill_no))
          << new QStandardItem(QString::fromStdString(p.date))
          << new QStandardItem(QString::fromStdString(p.supplier_name))
          << new QStandardItem(QString::fromStdString(p.total.to_decimal_string()))
          << new QStandardItem(QString::fromStdString(p.status))
          << new QStandardItem("");
      model->appendRow(row);
    }
  } catch (...) {}
  table_->setModel(model);
}

} // namespace fin::ui
