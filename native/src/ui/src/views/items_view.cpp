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
  static_cast<QHBoxLayout*>(header_row->layout())->addLayout(title_box);
  static_cast<QHBoxLayout*>(header_row->layout())->addItem(UiTheme::hspacer());
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
  auto* model = new QStandardItemModel(0, 7, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Name") << QString::fromLatin1("HSN") << QString::fromLatin1("Unit") << QString::fromLatin1("Rate") << QString::fromLatin1("GST %") << QString::fromLatin1("Stock") << QString::fromLatin1("Reorder"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Steel Rod 12mm")) << new QStandardItem(QString::fromLatin1("7214")) << new QStandardItem(QString::fromLatin1("KG")) << new QStandardItem(QString::fromLatin1("100.00")) << new QStandardItem(QString::fromLatin1("18")) << new QStandardItem(QString::fromLatin1("550")) << new QStandardItem(QString::fromLatin1("100")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Cement Bag 50kg")) << new QStandardItem(QString::fromLatin1("2523")) << new QStandardItem(QString::fromLatin1("BAG")) << new QStandardItem(QString::fromLatin1("350.00")) << new QStandardItem(QString::fromLatin1("28")) << new QStandardItem(QString::fromLatin1("1200")) << new QStandardItem(QString::fromLatin1("200")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Sand (ton)")) << new QStandardItem(QString::fromLatin1("2505")) << new QStandardItem(QString::fromLatin1("TON")) << new QStandardItem(QString::fromLatin1("1500.00")) << new QStandardItem(QString::fromLatin1("5")) << new QStandardItem(QString::fromLatin1("55")) << new QStandardItem(QString::fromLatin1("10")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Bricks (100s)")) << new QStandardItem(QString::fromLatin1("2521")) << new QStandardItem(QString::fromLatin1("100S")) << new QStandardItem(QString::fromLatin1("800.00")) << new QStandardItem(QString::fromLatin1("5")) << new QStandardItem(QString::fromLatin1("350")) << new QStandardItem(QString::fromLatin1("50")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Paint (ltr)")) << new QStandardItem(QString::fromLatin1("3208")) << new QStandardItem(QString::fromLatin1("LTR")) << new QStandardItem(QString::fromLatin1("250.00")) << new QStandardItem(QString::fromLatin1("18")) << new QStandardItem(QString::fromLatin1("120")) << new QStandardItem(QString::fromLatin1("20")); model->appendRow(row); }
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
