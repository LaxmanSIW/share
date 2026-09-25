#include "fin/ui/views/bills_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/db/bill_dao.hpp"
#include "fin/services/auth_session.hpp"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLineEdit>
#include <QPushButton>
#include <QStandardItemModel>
#include <QTimer>

namespace fin::ui {

BillsView::BillsView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);

  auto* header_row = UiTheme::row(12, this);
  auto* title_box = new QVBoxLayout();
  title_box->setSpacing(2);
  title_box->addWidget(UiTheme::pageTitle("Invoices"));
  title_box->addWidget(UiTheme::pageSubtitle("All your bills in one place"));
  header_row->layout()->addLayout(title_box);
  header_row->layout()->addItem(UiTheme::hspacer());
  create_btn_ = UiTheme::primaryIconButton(IconHelper::ICON_RECEIPT, "Create Bill");
  header_row->layout()->addWidget(create_btn_);
  l->addWidget(header_row);

  search_ = UiTheme::lineEdit("Search by bill no, buyer…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);

  table_ = new DataGrid(this);
  table_->set_columns({
    {"Bill No", 140}, {"Date", 100}, {"Buyer", 180}, {"Status", 90},
    {"Grand Total", 120}, {"Due", 100}, {"", -1}
  });
  l->addWidget(table_, 1);

  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void BillsView::refresh() {
  auto* model = new QStandardItemModel(0, 7, this);
  model->setHorizontalHeaderLabels({"Bill No", "Date", "Buyer", "Status", "Grand Total", "Due", ""});
  auto uid = fin::services::AuthSessionManager::current_user_id();
  if (uid.valid()) {
    try {
      fin::db::BillDao dao(fin::db::DatabaseManager::instance());
      auto bills = dao.find_all();
      for (const auto& b : bills) {
        QList<QStandardItem*> row;
        row << new QStandardItem(QString::fromStdString(b.bill_no))
            << new QStandardItem(QString::fromStdString(b.date))
            << new QStandardItem(QString::fromStdString(b.buyer_name()))
            << new QStandardItem(QString::fromStdString(fin::model::code(b.status)))
            << new QStandardItem(QString::fromStdString(b.totals.grand_total.to_decimal_string()))
            << new QStandardItem(QString::fromStdString(b.totals.grand_total.to_decimal_string()))
            << new QStandardItem("");
        model->appendRow(row);
      }
    } catch (...) {}
  }
  table_->setModel(model);
}

} // namespace fin::ui
