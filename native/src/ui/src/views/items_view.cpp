#include "fin/ui/views/items_view.hpp"
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
#include <QLabel>
#include <QStandardItemModel>
#include <QTimer>
#include <QDialog>
#include <QDialogButtonBox>
#include <QFormLayout>
#include <QUuid>

namespace fin::ui {

ItemsView::ItemsView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);

  auto* header_row = UiTheme::row(12, this);
  auto* title_box = new QVBoxLayout();
  title_box->setSpacing(2);
  title_box->addWidget(UiTheme::pageTitle("Items"));
  title_box->addWidget(UiTheme::pageSubtitle("Products / services you sell"));
  header_row->layout()->addLayout(title_box);
  header_row->layout()->addItem(UiTheme::hspacer());
  add_btn_ = UiTheme::primaryIconButton(IconHelper::ICON_PLUS, "Add Item");
  header_row->layout()->addWidget(add_btn_);
  l->addWidget(header_row);

  search_ = UiTheme::lineEdit("Search by name, HSN, category…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);

  table_ = new DataGrid(this);
  table_->set_columns({
    {"Name", 200}, {"HSN", 100}, {"Unit", 60}, {"Rate", 100},
    {"GST %", 60}, {"Stock", 80}, {"Reorder", 80}, {"", -1}
  });
  l->addWidget(table_, 1);

  connect(add_btn_, &QPushButton::clicked, this, &ItemsView::add_item_);
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void ItemsView::refresh() {
  auto* model = new QStandardItemModel(0, 8, this);
  model->setHorizontalHeaderLabels({"Name", "HSN", "Unit", "Rate", "GST %", "Stock", "Reorder", ""});
  auto uid = fin::services::AuthSessionManager::current_user_id();
  if (uid.valid()) {
    try {
      fin::db::ItemDao dao(fin::db::DatabaseManager::instance());
      auto items = dao.find_all();
      for (const auto& it : items) {
        QList<QStandardItem*> row;
        row << new QStandardItem(QString::fromStdString(it.name))
            << new QStandardItem(QString::fromStdString(it.hsn))
            << new QStandardItem(QString::fromStdString(it.unit))
            << new QStandardItem(QString::fromStdString(it.rate.to_decimal_string()))
            << new QStandardItem(QString::number(it.gst_rate.num * 100.0 / it.gst_rate.denom))
            << new QStandardItem(QString::fromStdString(it.current_stock.to_decimal_string()))
            << new QStandardItem(QString::fromStdString(it.reorder_level.to_decimal_string()))
            << new QStandardItem("");
        model->appendRow(row);
      }
    } catch (...) {}
  }
  table_->setModel(model);
}

void ItemsView::add_item_() {
  QDialog dlg(this);
  dlg.setWindowTitle("Add Item");
  dlg.setMinimumWidth(400);
  auto* form = new QFormLayout(&dlg);
  auto* name = new QLineEdit(&dlg);
  auto* hsn  = new QLineEdit(&dlg);
  auto* unit = new QLineEdit(&dlg);
  auto* rate = new QLineEdit(&dlg);
  auto* gst  = new QLineEdit(&dlg); gst->setText("18");
  form->addRow("Name:", name);
  form->addRow("HSN:", hsn);
  form->addRow("Unit:", unit);
  form->addRow("Rate (₹):", rate);
  form->addRow("GST (%):", gst);
  auto* btns = new QDialogButtonBox(QDialogButtonBox::Save | QDialogButtonBox::Cancel, &dlg);
  form->addRow(btns);
  connect(btns, &QDialogButtonBox::accepted, &dlg, &QDialog::accept);
  connect(btns, &QDialogButtonBox::rejected, &dlg, &QDialog::reject);
  if (dlg.exec() == QDialog::Accepted) {
    fin::model::ItemRecord it;
    it.id = QUuid::createUuid().toString(QUuid::WithoutBraces).toStdString();
    it.name = name->text().toStdString();
    it.hsn = hsn->text().toStdString();
    it.unit = unit->text().toStdString();
    it.rate = fin::Money::parse(rate->text().toStdString(), fin::CurrencyId::INR);
    double g = gst->text().toDouble();
    it.gst_rate = fin::Rate{static_cast<std::int64_t>(g * 10.0 + 0.5), 1000};
    try { fin::db::ItemDao(fin::db::DatabaseManager::instance()).save(it); refresh(); } catch (...) {}
  }
}

} // namespace fin::ui
