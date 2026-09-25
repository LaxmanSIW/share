// fin/ui/views/buyers_view.cpp
#include "fin/ui/views/buyers_view.hpp"
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

BuyersView::BuyersView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);

  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);

  // Header row
  auto* header_row = UiTheme::row(12, this);
  auto* title_box = new QVBoxLayout();
  title_box->setSpacing(2);
  title_box->addWidget(UiTheme::pageTitle("Buyers"));
  title_box->addWidget(UiTheme::pageSubtitle("Manage your customer directory"));
  static_cast<QHBoxLayout*>(header_row->layout())->addLayout(title_box);
  static_cast<QHBoxLayout*>(header_row->layout())->addItem(UiTheme::hspacer());

  add_btn_ = UiTheme::primaryIconButton(IconHelper::ICON_PLUS, "Add Buyer");
  header_row->layout()->addWidget(add_btn_);
  edit_btn_ = UiTheme::ghostButton("Edit");
  header_row->layout()->addWidget(edit_btn_);
  del_btn_ = UiTheme::ghostButton("Delete");
  del_btn_->setStyleSheet("color: #EF4444;");
  header_row->layout()->addWidget(del_btn_);
  l->addWidget(header_row);

  // Search row
  auto* search_row = UiTheme::row(8, this);
  search_ = UiTheme::lineEdit("Search by name, phone, GST…", search_row);
  search_->setClearButtonEnabled(true);
  search_row->layout()->addWidget(search_);
  l->addWidget(search_row);

  // Table
  table_ = new DataGrid(this);
  table_->set_columns({
    {"Name", 180}, {"Phone", 120}, {"GST", 160}, {"State", 100},
    {"City", 100}, {"Credit Limit", 120}, {"Risk", 60}, {"", -1}
  });
  // Auto-refresh on first show (skill §3.11: instant feedback).
  l->addWidget(table_, 1);

  // Wire actions
  connect(add_btn_, &QPushButton::clicked, this, &BuyersView::add_buyer_);
  connect(edit_btn_, &QPushButton::clicked, this, [this]{
    int row = table_->selected_row();
    if (row >= 0) edit_buyer_(row);
  });
  connect(del_btn_, &QPushButton::clicked, this, &BuyersView::delete_buyer_);
  table_->set_row_double_click([this](int row){ edit_buyer_(row); });

  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void BuyersView::refresh() {
  auto* model = new QStandardItemModel(0, 4, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Name") << QString::fromLatin1("Phone") << QString::fromLatin1("GST") << QString::fromLatin1("State"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Acme Industries")) << new QStandardItem(QString::fromLatin1("+91 98765 43210")) << new QStandardItem(QString::fromLatin1("27ABCDE1234F1Z5")) << new QStandardItem(QString::fromLatin1("Maharashtra")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Shree Trading Co.")) << new QStandardItem(QString::fromLatin1("+91 98111 22222")) << new QStandardItem(QString::fromLatin1("27XYZAB6789G1H2I")) << new QStandardItem(QString::fromLatin1("Gujarat")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Bharat Steel Ltd")) << new QStandardItem(QString::fromLatin1("+91 98222 33333")) << new QStandardItem(QString::fromLatin1("27PQRST4567J1K3L")) << new QStandardItem(QString::fromLatin1("Maharashtra")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Mahalaxmi Textiles")) << new QStandardItem(QString::fromLatin1("+91 98333 44444")) << new QStandardItem(QString::fromLatin1("27LMNOP8901M1N4O")) << new QStandardItem(QString::fromLatin1("Karnataka")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Royal Exports")) << new QStandardItem(QString::fromLatin1("+91 98444 55555")) << new QStandardItem(QString::fromLatin1("27UVWXY2345P1Q6R")) << new QStandardItem(QString::fromLatin1("Delhi")); model->appendRow(row); }
  table_->setModel(model);
}

void BuyersView::add_buyer_() {
  QDialog dlg(this);
  dlg.setWindowTitle("Add Buyer");
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
    fin::model::Buyer b;
    b.id = QUuid::createUuid().toString(QUuid::WithoutBraces).toStdString();
    b.name = name->text().toStdString();
    b.phone = phone->text().toStdString();
    b.gst = gst->text().toStdString();
    b.state = state->text().toStdString();
    b.city = city->text().toStdString();
    try {
      fin::db::BuyerDao dao(fin::db::DatabaseManager::instance());
      dao.save(b);
      refresh();
    } catch (const std::exception& e) {
      qWarning("BuyersView::add_buyer: %s", e.what());
    }
  }
}

void BuyersView::edit_buyer_(int /*row*/) {
  // Phase 5: load selected buyer from model, open edit dialog pre-populated.
}

void BuyersView::delete_buyer_() {
  int row = table_->selected_row();
  if (row < 0) return;
  // Phase 5: confirm + delete via BuyerDao.
  (void)row;
}

} // namespace fin::ui
