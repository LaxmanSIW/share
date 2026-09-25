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
  // Phase 5 skeleton: load from BuyerDao asynchronously on the db pool,
  // populate a QStandardItemModel.
  auto* model = new QStandardItemModel(0, 8, this);
  model->setHorizontalHeaderLabels({"Name", "Phone", "GST", "State", "City", "Credit Limit", "Risk", ""});
  auto uid = fin::services::AuthSessionManager::current_user_id();
  if (uid.valid()) {
    try {
      fin::db::BuyerDao dao(fin::db::DatabaseManager::instance());
      auto buyers = dao.find_all();
      for (const auto& b : buyers) {
        QList<QStandardItem*> row;
        row << new QStandardItem(QString::fromStdString(b.name))
            << new QStandardItem(QString::fromStdString(b.phone))
            << new QStandardItem(QString::fromStdString(b.gst))
            << new QStandardItem(QString::fromStdString(b.state))
            << new QStandardItem(QString::fromStdString(b.city))
            << new QStandardItem(QString::fromStdString(b.credit_limit.to_decimal_string()))
            << new QStandardItem(QString::number(b.risk_score))
            << new QStandardItem("");
        model->appendRow(row);
      }
    } catch (...) {}
  }
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
