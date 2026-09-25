#include "fin/ui/views/transactions_view.hpp"
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

TransactionsView::TransactionsView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  auto* header = UiTheme::row(12, this);
  auto* tb = new QVBoxLayout(); tb->setSpacing(2);
  tb->addWidget(UiTheme::pageTitle("Transactions"));
  tb->addWidget(UiTheme::pageSubtitle("Financial book entries (sales/purchase/payment)"));
  header->layout()->addLayout(tb);
  header->layout()->addItem(UiTheme::hspacer());
  l->addWidget(header);
  search_ = UiTheme::lineEdit("Search…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);
  table_ = new DataGrid(this);
  table_->set_columns({{"Date", 100}, {"Type", 120}, {"Book", 100}, {"Buyer", 180}, {"Amount", 120}, {"", -1}});
  l->addWidget(table_, 1);
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void TransactionsView::refresh() {
  auto* model = new QStandardItemModel(0, 6, this);
  model->setHorizontalHeaderLabels({"Date", "Type", "Book", "Buyer", "Amount", ""});
  try {
    fin::db::TransactionDao dao(fin::db::DatabaseManager::instance());
    auto txs = dao.find_all();
    for (const auto& t : txs) {
      QList<QStandardItem*> row;
      row << new QStandardItem(QString::fromStdString(t.transaction_date))
          << new QStandardItem(QString::fromStdString(t.transaction_type))
          << new QStandardItem(QString::fromStdString(t.book_type))
          << new QStandardItem(QString::fromStdString(t.buyer_name))
          << new QStandardItem(QString::fromStdString(t.amount.to_decimal_string()))
          << new QStandardItem("");
      model->appendRow(row);
    }
  } catch (...) {}
  table_->setModel(model);
}

} // namespace fin::ui
