#include "fin/ui/views/history_view.hpp"
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

HistoryView::HistoryView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  auto* header = UiTheme::row(12, this);
  auto* tb = new QVBoxLayout(); tb->setSpacing(2);
  tb->addWidget(UiTheme::pageTitle("History"));
  tb->addWidget(UiTheme::pageSubtitle("Recent bills + payments combined view"));
  static_cast<QHBoxLayout*>(header->layout())->addItem(tb);
  static_cast<QHBoxLayout*>(header->layout())->addItem(UiTheme::hspacer());
  l->addWidget(header);
  search_ = UiTheme::lineEdit("Search by bill no or buyer…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);
  table_ = new DataGrid(this);
  table_->set_columns({{"Bill No", 140}, {"Date", 100}, {"Buyer", 180}, {"Status", 90}, {"Grand Total", 120}, {"", -1}});
  l->addWidget(table_, 1);
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void HistoryView::refresh() {
  auto* model = new QStandardItemModel(0, 5, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Bill No") << QString::fromLatin1("Date") << QString::fromLatin1("Buyer") << QString::fromLatin1("Status") << QString::fromLatin1("Grand Total"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("INV-2026-0042")) << new QStandardItem(QString::fromLatin1("2026-09-25")) << new QStandardItem(QString::fromLatin1("Acme Industries")) << new QStandardItem(QString::fromLatin1("Paid")) << new QStandardItem(QString::fromLatin1("Rs 48,650")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("INV-2026-0041")) << new QStandardItem(QString::fromLatin1("2026-09-24")) << new QStandardItem(QString::fromLatin1("Shree Trading Co.")) << new QStandardItem(QString::fromLatin1("Unpaid")) << new QStandardItem(QString::fromLatin1("Rs 32,100")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("INV-2026-0040")) << new QStandardItem(QString::fromLatin1("2026-09-23")) << new QStandardItem(QString::fromLatin1("Bharat Steel Ltd")) << new QStandardItem(QString::fromLatin1("Paid")) << new QStandardItem(QString::fromLatin1("Rs 1,15,000")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("INV-2026-0039")) << new QStandardItem(QString::fromLatin1("2026-09-22")) << new QStandardItem(QString::fromLatin1("Mahalaxmi Textiles")) << new QStandardItem(QString::fromLatin1("Unpaid")) << new QStandardItem(QString::fromLatin1("Rs 18,750")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("INV-2026-0038")) << new QStandardItem(QString::fromLatin1("2026-09-21")) << new QStandardItem(QString::fromLatin1("Royal Exports")) << new QStandardItem(QString::fromLatin1("Paid")) << new QStandardItem(QString::fromLatin1("Rs 67,200")); model->appendRow(row); }
  table_->setModel(model);
}

} // namespace fin::ui
