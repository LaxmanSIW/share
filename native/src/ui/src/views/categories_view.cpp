#include "fin/ui/views/categories_view.hpp"
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

CategoriesView::CategoriesView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);

  auto* header_row = UiTheme::row(12, this);
  auto* title_box = new QVBoxLayout();
  title_box->setSpacing(2);
  title_box->addWidget(UiTheme::pageTitle("Categories"));
  title_box->addWidget(UiTheme::pageSubtitle("Item category directory"));
  static_cast<QHBoxLayout*>(header_row->layout())->addLayout(title_box);
  static_cast<QHBoxLayout*>(header_row->layout())->addItem(UiTheme::hspacer());
  add_btn_ = UiTheme::primaryIconButton(IconHelper::ICON_PLUS, "Add");
  header_row->layout()->addWidget(add_btn_);
  l->addWidget(header_row);

  search_ = UiTheme::lineEdit("Search…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);

  table_ = new DataGrid(this);
  table_->set_columns({ {"Name", 200}, {"", -1} });
  l->addWidget(table_, 1);

  connect(add_btn_, &QPushButton::clicked, this, [this]{ add_record_(); });
  connect(search_, &QLineEdit::textChanged, this, [this]{ refresh(); });
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void CategoriesView::refresh() {
  auto* model = new QStandardItemModel(0, 1, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Name"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Construction Materials")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Steel & Metals")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Cement & Concrete")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Electrical")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Plumbing")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Paint & Finishing")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Hardware")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Tools & Equipment")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Safety Gear")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Transport")); model->appendRow(row); }
  table_->setModel(model);
}

void CategoriesView::add_record_() {
  QDialog dlg(this);
  dlg.setWindowTitle("Add Categories");
  dlg.setMinimumWidth(400);
  auto* form = new QFormLayout(&dlg);
  auto* name = new QLineEdit(&dlg);
  form->addRow("Name:", name);
  
  auto* btns = new QDialogButtonBox(QDialogButtonBox::Save | QDialogButtonBox::Cancel, &dlg);
  form->addRow(btns);
  connect(btns, &QDialogButtonBox::accepted, &dlg, &QDialog::accept);
  connect(btns, &QDialogButtonBox::rejected, &dlg, &QDialog::reject);
  if (dlg.exec() == QDialog::Accepted) {
    fin::model::ItemCategory r;
    r.id = QUuid::createUuid().toString(QUuid::WithoutBraces).toStdString();
    r.name = name->text().toStdString();
    try {
      fin::db::CategoryDao(fin::db::DatabaseManager::instance()).save(r);
      Toast::success("Categories saved");
      refresh();
    } catch (const std::exception& e) {
      Toast::error(QString("Save failed: %1").arg(e.what()));
    }
  }
}

} // namespace fin::ui
