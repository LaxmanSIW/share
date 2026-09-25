#include "fin/ui/views/variables_view.hpp"
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
#include <QComboBox>
#include <QUuid>

namespace fin::ui {

VariablesView::VariablesView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);

  auto* header = UiTheme::row(12, this);
  auto* tb = new QVBoxLayout();
  tb->setSpacing(2);
  tb->addWidget(UiTheme::pageTitle("Variables"));
  tb->addWidget(UiTheme::pageSubtitle("Bindable text/number fields for templates"));
  header->layout()->addLayout(tb);
  header->layout()->addItem(UiTheme::hspacer());
  add_btn_ = UiTheme::primaryIconButton(IconHelper::ICON_PLUS, "Add Variable");
  header->layout()->addWidget(add_btn_);
  l->addWidget(header);

  search_ = UiTheme::lineEdit("Search by key or label…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);

  table_ = new DataGrid(this);
  table_->set_columns({{"Key", 140}, {"Label", 180}, {"Type", 80}, {"Scope", 100}, {"Builtin", 70}, {"", -1}});
  l->addWidget(table_, 1);

  connect(add_btn_, &QPushButton::clicked, this, [this]{ add_record_(); });
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void VariablesView::refresh() {
  auto* model = new QStandardItemModel(0, 6, this);
  model->setHorizontalHeaderLabels({"Key", "Label", "Type", "Scope", "Builtin", ""});
  try {
    fin::db::VariableDao dao(fin::db::DatabaseManager::instance());
    auto vars = dao.find_all();
    for (const auto& v : vars) {
      QList<QStandardItem*> row;
      row << new QStandardItem(QString::fromStdString(v.key))
          << new QStandardItem(QString::fromStdString(v.label))
          << new QStandardItem(QString::fromStdString(v.type))
          << new QStandardItem(QString::fromStdString(v.scope))
          << new QStandardItem(v.builtin ? "Yes" : "No")
          << new QStandardItem("");
      model->appendRow(row);
    }
  } catch (...) {}
  table_->setModel(model);
}

void VariablesView::add_record_() {
  QDialog dlg(this);
  dlg.setWindowTitle("Add Variable");
  dlg.setMinimumWidth(400);
  auto* form = new QFormLayout(&dlg);
  auto* key = new QLineEdit(&dlg);
  auto* label = new QLineEdit(&dlg);
  auto* type = new QComboBox(&dlg); type->addItems({"text", "number"});
  auto* scope = new QComboBox(&dlg); scope->addItems({"fixed", "transactional"});
  auto* default_value = new QLineEdit(&dlg);
  auto* choices = new QLineEdit(&dlg); choices->setPlaceholderText("comma-separated");
  form->addRow("Key:", key);
  form->addRow("Label:", label);
  form->addRow("Type:", type);
  form->addRow("Scope:", scope);
  form->addRow("Default:", default_value);
  form->addRow("Choices:", choices);
  auto* btns = new QDialogButtonBox(QDialogButtonBox::Save | QDialogButtonBox::Cancel, &dlg);
  form->addRow(btns);
  connect(btns, &QDialogButtonBox::accepted, &dlg, &QDialog::accept);
  connect(btns, &QDialogButtonBox::rejected, &dlg, &QDialog::reject);
  if (dlg.exec() == QDialog::Accepted) {
    fin::model::VariableDef v;
    v.key = key->text().toStdString();
    v.label = label->text().toStdString();
    v.type = type->currentText().toStdString();
    v.scope = scope->currentText().toStdString();
    v.default_value = default_value->text().toStdString();
    v.choices = choices->text().toStdString();
    try { fin::db::VariableDao(fin::db::DatabaseManager::instance()).save(v); Toast::success("Variable saved"); refresh(); }
    catch (const std::exception& e) { Toast::error(QString("Save failed: %1").arg(e.what())); }
  }
}

} // namespace fin::ui
