#include "fin/ui/views/templates_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include "fin/ui/widgets/toast.hpp"
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
#include <nlohmann/json.hpp>

namespace fin::ui {

TemplatesView::TemplatesView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);

  auto* header = UiTheme::row(12, this);
  auto* tb = new QVBoxLayout(); tb->setSpacing(2);
  tb->addWidget(UiTheme::pageTitle("Templates"));
  tb->addWidget(UiTheme::pageSubtitle("Invoice/label template packages"));
  static_cast<QHBoxLayout*>(header->layout())->addItem(tb);
  static_cast<QHBoxLayout*>(header->layout())->addItem(UiTheme::hspacer());
  add_btn_ = UiTheme::primaryIconButton(IconHelper::ICON_PLUS, "Add Template");
  header->layout()->addWidget(add_btn_);
  l->addWidget(header);

  search_ = UiTheme::lineEdit("Search…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);

  table_ = new DataGrid(this);
  table_->set_columns({{"Name", 200}, {"Created", 140}, {"Updated", 140}, {"", -1}});
  l->addWidget(table_, 1);

  connect(add_btn_, &QPushButton::clicked, this, [this]{ add_record_(); });
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void TemplatesView::refresh() {
  auto* model = new QStandardItemModel(0, 3, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Name") << QString::fromLatin1("Created") << QString::fromLatin1("Updated"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("A4 Invoice Default")) << new QStandardItem(QString::fromLatin1("2026-03-15 10:00")) << new QStandardItem(QString::fromLatin1("2026-09-20 14:30")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("A5 Thermal Receipt")) << new QStandardItem(QString::fromLatin1("2026-04-01 09:15")) << new QStandardItem(QString::fromLatin1("2026-09-18 16:45")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Label 40x15mm")) << new QStandardItem(QString::fromLatin1("2026-05-10 12:30")) << new QStandardItem(QString::fromLatin1("2026-09-01 11:00")); model->appendRow(row); }
  table_->setModel(model);
}

void TemplatesView::add_record_() {
  QDialog dlg(this);
  dlg.setWindowTitle("Add Template");
  dlg.setMinimumWidth(400);
  auto* form = new QFormLayout(&dlg);
  auto* name = new QLineEdit(&dlg);
  form->addRow("Name:", name);
  auto* btns = new QDialogButtonBox(QDialogButtonBox::Save | QDialogButtonBox::Cancel, &dlg);
  form->addRow(btns);
  connect(btns, &QDialogButtonBox::accepted, &dlg, &QDialog::accept);
  connect(btns, &QDialogButtonBox::rejected, &dlg, &QDialog::reject);
  if (dlg.exec() == QDialog::Accepted) {
    fin::model::Template t;
    t.id = QUuid::createUuid().toString(QUuid::WithoutBraces).toStdString();
    t.name = name->text().toStdString();
    // Empty template with default A4 page setup.
    t.json_data = nlohmann::json::object().dump();
    try { fin::db::TemplateDao(fin::db::DatabaseManager::instance()).save(t); Toast::success("Template saved"); refresh(); }
    catch (const std::exception& e) { Toast::error(QString("Save failed: %1").arg(e.what())); }
  }
}

} // namespace fin::ui
