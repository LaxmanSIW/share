#include "fin/ui/views/expenses_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include "fin/ui/widgets/toast.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/db/daos2.hpp"
#include "fin/services/auth_session.hpp"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLineEdit>
#include <QPushButton>
#include <QStandardItemModel>
#include <QTimer>
#include <QDialog>
#include <QDialogButtonBox>
#include <QFormLayout>
#include <QDateEdit>
#include <QUuid>

namespace fin::ui {

ExpensesView::ExpensesView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  auto* header = UiTheme::row(12, this);
  auto* tb = new QVBoxLayout(); tb->setSpacing(2);
  tb->addWidget(UiTheme::pageTitle("Expenses"));
  tb->addWidget(UiTheme::pageSubtitle("Expense vouchers + payees"));
  static_cast<QHBoxLayout*>(header->layout())->addItem(tb);
  static_cast<QHBoxLayout*>(header->layout())->addItem(UiTheme::hspacer());
  add_btn_ = UiTheme::primaryIconButton(IconHelper::ICON_PLUS, "Add Expense");
  header->layout()->addWidget(add_btn_);
  l->addWidget(header);
  search_ = UiTheme::lineEdit("Search…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);
  table_ = new DataGrid(this);
  table_->set_columns({{"Date", 100}, {"Category", 160}, {"Amount", 120}, {"Mode", 100}, {"", -1}});
  l->addWidget(table_, 1);
  connect(add_btn_, &QPushButton::clicked, this, [this]{ add_record_(); });
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void ExpensesView::refresh() {
  auto* model = new QStandardItemModel(0, 4, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Date") << QString::fromLatin1("Category") << QString::fromLatin1("Amount") << QString::fromLatin1("Mode"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-25")) << new QStandardItem(QString::fromLatin1("Office Rent")) << new QStandardItem(QString::fromLatin1("Rs 15,000")) << new QStandardItem(QString::fromLatin1("Bank")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-24")) << new QStandardItem(QString::fromLatin1("Electricity")) << new QStandardItem(QString::fromLatin1("Rs 3,200")) << new QStandardItem(QString::fromLatin1("UPI")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-23")) << new QStandardItem(QString::fromLatin1("Internet")) << new QStandardItem(QString::fromLatin1("Rs 1,500")) << new QStandardItem(QString::fromLatin1("UPI")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-22")) << new QStandardItem(QString::fromLatin1("Fuel")) << new QStandardItem(QString::fromLatin1("Rs 2,800")) << new QStandardItem(QString::fromLatin1("Card")); model->appendRow(row); }
  table_->setModel(model);
}

void ExpensesView::add_record_() {
  QDialog dlg(this);
  dlg.setWindowTitle("Add Expense");
  dlg.setMinimumWidth(400);
  auto* form = new QFormLayout(&dlg);
  auto* date = new QDateEdit(QDate::currentDate(), &dlg); date->setDisplayFormat("yyyy-MM-dd");
  auto* category = new QLineEdit(&dlg);
  auto* amount = new QLineEdit(&dlg);
  auto* mode = new QLineEdit(&dlg); mode->setText("CASH");
  form->addRow("Date:", date);
  form->addRow("Category:", category);
  form->addRow("Amount:", amount);
  form->addRow("Mode:", mode);
  auto* btns = new QDialogButtonBox(QDialogButtonBox::Save | QDialogButtonBox::Cancel, &dlg);
  form->addRow(btns);
  connect(btns, &QDialogButtonBox::accepted, &dlg, &QDialog::accept);
  connect(btns, &QDialogButtonBox::rejected, &dlg, &QDialog::reject);
  if (dlg.exec() == QDialog::Accepted) {
    fin::model::Expense e;
    e.id = QUuid::createUuid().toString(QUuid::WithoutBraces).toStdString();
    e.date = date->date().toString("yyyy-MM-dd").toStdString();
    e.category = category->text().toStdString();
    e.amount = fin::Money::parse(amount->text().toStdString(), fin::CurrencyId::INR);
    e.payment_mode = mode->text().toStdString();
    try { fin::db::ExpenseDao(fin::db::DatabaseManager::instance()).save(e); Toast::success("Expense saved"); refresh(); }
    catch (const std::exception& ex) { Toast::error(QString("Save failed: %1").arg(ex.what())); }
  }
}

} // namespace fin::ui
