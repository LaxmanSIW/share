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
  header_row->layout()->addLayout(title_box);
  header_row->layout()->addItem(UiTheme::hspacer());
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
  auto* model = new QStandardItemModel(0, 6, this);
  model->setHorizontalHeaderLabels({"Name", "Phone", "GST", "State", "City", ""});
  auto uid = fin::services::AuthSessionManager::current_user_id();
  if (uid.valid()) {
    try {
      fin::db::SupplierDao dao(fin::db::DatabaseManager::instance());
      auto sup = dao.find_all();
      for (const auto& s : sup) {
        QList<QStandardItem*> row;
        row << new QStandardItem(QString::fromStdString(s.name))
            << new QStandardItem(QString::fromStdString(s.phone))
            << new QStandardItem(QString::fromStdString(s.gst))
            << new QStandardItem(QString::fromStdString(s.state))
            << new QStandardItem(QString::fromStdString(s.city))
            << new QStandardItem("");
        model->appendRow(row);
      }
    } catch (...) {}
  }
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
