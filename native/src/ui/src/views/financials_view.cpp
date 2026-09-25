#include "fin/ui/views/financials_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include "fin/services/financial.hpp"
#include "fin/services/auth_session.hpp"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QDateEdit>
#include <QPushButton>
#include <QStandardItemModel>
#include <QTimer>
#include <chrono>

namespace fin::ui {

FinancialsView::FinancialsView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  auto* header = UiTheme::row(12, this);
  auto* tb = new QVBoxLayout(); tb->setSpacing(2);
  tb->addWidget(UiTheme::pageTitle("Financials"));
  tb->addWidget(UiTheme::pageSubtitle("Bank/cash ledgers and reconciliations"));
  static_cast<QHBoxLayout*>(header->layout())->addItem(tb);
  static_cast<QHBoxLayout*>(header->layout())->addItem(UiTheme::hspacer());
  l->addWidget(header);

  auto* filter_row = UiTheme::row(8, this);
  auto* from = new QDateEdit(QDate::currentDate().addMonths(-1), this); from->setDisplayFormat("yyyy-MM-dd");
  auto* to = new QDateEdit(QDate::currentDate(), this); to->setDisplayFormat("yyyy-MM-dd");
  auto* refresh_btn = UiTheme::ghostButton("Refresh");
  filter_row->layout()->addWidget(UiTheme::muted("From:", filter_row));
  filter_row->layout()->addWidget(from);
  filter_row->layout()->addWidget(UiTheme::muted("To:", filter_row));
  filter_row->layout()->addWidget(to);
  static_cast<QHBoxLayout*>(filter_row->layout())->addItem(UiTheme::hspacer());
  filter_row->layout()->addWidget(refresh_btn);
  l->addWidget(filter_row);

  table_ = new DataGrid(this);
  table_->set_columns({{"Date", 100}, {"Account", 180}, {"Type", 100}, {"Debit", 120}, {"Credit", 120}, {"", -1}});
  l->addWidget(table_, 1);

  connect(refresh_btn, &QPushButton::clicked, this, [this]{ refresh(); });
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void FinancialsView::refresh() {
  auto* model = new QStandardItemModel(0, 5, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Date") << QString::fromLatin1("Account") << QString::fromLatin1("Type") << QString::fromLatin1("Debit") << QString::fromLatin1("Credit"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-25")) << new QStandardItem(QString::fromLatin1("Sales Revenue")) << new QStandardItem(QString::fromLatin1("Income")) << new QStandardItem(QString::fromLatin1("")) << new QStandardItem(QString::fromLatin1("Rs 48,650")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-25")) << new QStandardItem(QString::fromLatin1("Accounts Receivable")) << new QStandardItem(QString::fromLatin1("Asset")) << new QStandardItem(QString::fromLatin1("Rs 48,650")) << new QStandardItem(QString::fromLatin1("")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-24")) << new QStandardItem(QString::fromLatin1("Purchase")) << new QStandardItem(QString::fromLatin1("COGS")) << new QStandardItem(QString::fromLatin1("Rs 32,100")) << new QStandardItem(QString::fromLatin1("")); model->appendRow(row); }
  table_->setModel(model);
}

} // namespace fin::ui
