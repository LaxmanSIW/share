#include "fin/ui/views/transports_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include "fin/ui/widgets/toast.hpp"
#include "fin/ui/widgets/dialog_helper.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/db/daos2.hpp"
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
#include <vector>

namespace fin::ui {

TransportsView::TransportsView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);

  auto* header_row = UiTheme::row(12, this);
  auto* title_box = new QVBoxLayout();
  title_box->setSpacing(2);
  title_box->addWidget(UiTheme::pageTitle("Transports"));
  title_box->addWidget(UiTheme::pageSubtitle("Transport partner directory"));
  static_cast<QHBoxLayout*>(header_row->layout())->addLayout(title_box);
  static_cast<QHBoxLayout*>(header_row->layout())->addItem(UiTheme::hspacer());
  add_btn_ = UiTheme::primaryIconButton(IconHelper::ICON_PLUS, "Add");
  header_row->layout()->addWidget(add_btn_);
  l->addWidget(header_row);

  search_ = UiTheme::lineEdit("Search…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);

  table_ = new DataGrid(this);
  table_->set_columns({ {"Name", 180}, {"Phone", 120}, {"Vehicle", 140}, {"", -1} });
  l->addWidget(table_, 1);

  connect(add_btn_, &QPushButton::clicked, this, [this]{ add_record_(); });
  connect(search_, &QLineEdit::textChanged, this, [this]{ refresh(); });
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void TransportsView::refresh() {
  auto* model = new QStandardItemModel(0, 3, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Name") << QString::fromLatin1("Phone") << QString::fromLatin1("Vehicle"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Speed Logistics")) << new QStandardItem(QString::fromLatin1("+91 98100 11111")) << new QStandardItem(QString::fromLatin1("MH-12-AB-1234")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Blue Dart Express")) << new QStandardItem(QString::fromLatin1("+91 98200 22222")) << new QStandardItem(QString::fromLatin1("DL-01-CD-5678")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("VRL Logistics")) << new QStandardItem(QString::fromLatin1("+91 98300 33333")) << new QStandardItem(QString::fromLatin1("KA-05-EF-9012")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("TNT Express")) << new QStandardItem(QString::fromLatin1("+91 98400 44444")) << new QStandardItem(QString::fromLatin1("TN-07-GH-3456")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("DTDC Courier")) << new QStandardItem(QString::fromLatin1("+91 98500 55555")) << new QStandardItem(QString::fromLatin1("GJ-01-IJ-7890")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Professional Couriers")) << new QStandardItem(QString::fromLatin1("+91 98600 66666")) << new QStandardItem(QString::fromLatin1("AP-09-KL-2345")); model->appendRow(row); }
  table_->setModel(model);
}

void TransportsView::add_record_() {
  QDialog dlg(this);
  dlg.setWindowTitle("Add Transports");
  dlg.setMinimumWidth(400);
  auto* form = new QFormLayout(&dlg);
  auto* name = new QLineEdit(&dlg);
  form->addRow("Name:", name);
  auto* phone = new QLineEdit(&dlg); auto* vehicle = new QLineEdit(&dlg);
   form->addRow("Phone:", phone); form->addRow("Vehicle:", vehicle);
   // (save: r.phone = phone->text().toStdString(); r.vehicle_number = vehicle->text().toStdString();)
  auto* btns = new QDialogButtonBox(QDialogButtonBox::Save | QDialogButtonBox::Cancel, &dlg);
  form->addRow(btns);
  connect(btns, &QDialogButtonBox::accepted, &dlg, &QDialog::accept);
  connect(btns, &QDialogButtonBox::rejected, &dlg, &QDialog::reject);
  if (dlg.exec() == QDialog::Accepted) {
    fin::model::Transport r;
    r.id = QUuid::createUuid().toString(QUuid::WithoutBraces).toStdString();
    r.name = name->text().toStdString();
    try {
      fin::db::TransportDao(fin::db::DatabaseManager::instance()).save(r);
      Toast::success("Transports saved");
      refresh();
    } catch (const std::exception& e) {
      Toast::error(QString("Save failed: %1").arg(e.what()));
    }
  }
}

} // namespace fin::ui
