#include "fin/ui/views/suppliers_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/db/daos.hpp"
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
#include <QUuid>

namespace fin::ui {

SuppliersView::SuppliersView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);

  auto* header_row = UiTheme::row(12, this);
  auto* title_box = new QVBoxLayout();
  title_box->setSpacing(2);
  title_box->addWidget(UiTheme::pageTitle("Suppliers"));
  title_box->addWidget(UiTheme::pageSubtitle("Manage your supplier directory"));
  static_cast<QHBoxLayout*>(header_row->layout())->addLayout(title_box);
  static_cast<QHBoxLayout*>(header_row->layout())->addItem(UiTheme::hspacer());
  add_btn_ = UiTheme::primaryIconButton(IconHelper::ICON_PLUS, "Add Supplier");
  header_row->layout()->addWidget(add_btn_);
  l->addWidget(header_row);

  search_ = UiTheme::lineEdit("Search by name, phone, GST…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);

  table_ = new DataGrid(this);
  table_->set_columns({
    {"Name", 200}, {"Phone", 120}, {"GST", 160}, {"State", 100},
    {"City", 100}, {"", -1}
  });
  l->addWidget(table_, 1);

  connect(add_btn_, &QPushButton::clicked, this, &SuppliersView::add_supplier_);
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void SuppliersView::refresh() {
  auto* model = new QStandardItemModel(0, 4, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Name") << QString::fromLatin1("Phone") << QString::fromLatin1("GST") << QString::fromLatin1("State"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Reliance Industries")) << new QStandardItem(QString::fromLatin1("+91 22333 44444")) << new QStandardItem(QString::fromLatin1("27AAACR5020K1Z5")) << new QStandardItem(QString::fromLatin1("Maharashtra")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Tata Steel")) << new QStandardItem(QString::fromLatin1("+91 22555 66666")) << new QStandardItem(QString::fromLatin1("27AAACT2727Q1ZX")) << new QStandardItem(QString::fromLatin1("Maharashtra")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Adani Enterprises")) << new QStandardItem(QString::fromLatin1("+91 22777 88888")) << new QStandardItem(QString::fromLatin1("27AAHCA3830C1Z6")) << new QStandardItem(QString::fromLatin1("Gujarat")); model->appendRow(row); }
  table_->setModel(model);
}

void SuppliersView::add_supplier_() {
  QDialog dlg(this);
  dlg.setWindowTitle("Add Supplier");
  dlg.setMinimumWidth(400);
  auto* form = new QFormLayout(&dlg);
  auto* name = new QLineEdit(&dlg);
  auto* phone = new QLineEdit(&dlg);
  auto* gst = new QLineEdit(&dlg);
  auto* state = new QLineEdit(&dlg);
  auto* city = new QLineEdit(&dlg);
  form->addRow("Name:", name);
  form->addRow("Phone:", phone);
  form->addRow("GST:", gst);
  form->addRow("State:", state);
  form->addRow("City:", city);
  auto* btns = new QDialogButtonBox(QDialogButtonBox::Save | QDialogButtonBox::Cancel, &dlg);
  form->addRow(btns);
  connect(btns, &QDialogButtonBox::accepted, &dlg, &QDialog::accept);
  connect(btns, &QDialogButtonBox::rejected, &dlg, &QDialog::reject);
  if (dlg.exec() == QDialog::Accepted) {
    fin::model::Supplier s;
    s.id = QUuid::createUuid().toString(QUuid::WithoutBraces).toStdString();
    s.name = name->text().toStdString();
    s.phone = phone->text().toStdString();
    s.gst = gst->text().toStdString();
    s.state = state->text().toStdString();
    s.city = city->text().toStdString();
    try { fin::db::SupplierDao(fin::db::DatabaseManager::instance()).save(s); refresh(); } catch (...) {}
  }
}

} // namespace fin::ui
