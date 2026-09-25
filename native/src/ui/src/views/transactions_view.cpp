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
  static_cast<QHBoxLayout*>(header->layout())->addItem(tb);
  static_cast<QHBoxLayout*>(header->layout())->addItem(UiTheme::hspacer());
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
  auto* model = new QStandardItemModel(0, 5, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Date") << QString::fromLatin1("Type") << QString::fromLatin1("Book") << QString::fromLatin1("Buyer") << QString::fromLatin1("Amount"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-25")) << new QStandardItem(QString::fromLatin1("Receipt")) << new QStandardItem(QString::fromLatin1("Sales")) << new QStandardItem(QString::fromLatin1("Acme Industries")) << new QStandardItem(QString::fromLatin1("Rs 48,650")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-24")) << new QStandardItem(QString::fromLatin1("Payment")) << new QStandardItem(QString::fromLatin1("Purchase")) << new QStandardItem(QString::fromLatin1("Shree Trading Co.")) << new QStandardItem(QString::fromLatin1("Rs 32,100")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-23")) << new QStandardItem(QString::fromLatin1("Receipt")) << new QStandardItem(QString::fromLatin1("Sales")) << new QStandardItem(QString::fromLatin1("Bharat Steel Ltd")) << new QStandardItem(QString::fromLatin1("Rs 1,15,000")); model->appendRow(row); }
  table_->setModel(model);
}

} // namespace fin::ui
