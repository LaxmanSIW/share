#include "fin/ui/views/label_history_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/db/daos2.hpp"
#include "fin/services/auth_session.hpp"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLineEdit>
#include <QPushButton>
#include <QStandardItemModel>
#include <QTimer>

namespace fin::ui {

LabelHistoryView::LabelHistoryView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  auto* header = UiTheme::row(12, this);
  auto* tb = new QVBoxLayout(); tb->setSpacing(2);
  tb->addWidget(UiTheme::pageTitle("Label History"));
  tb->addWidget(UiTheme::pageSubtitle("Audit of label print runs"));
  header->layout()->addLayout(tb);
  header->layout()->addItem(UiTheme::hspacer());
  l->addWidget(header);
  search_ = UiTheme::lineEdit("Search…", this);
  search_->setClearButtonEnabled(true);
  l->addWidget(search_);
  table_ = new DataGrid(this);
  table_->set_columns({{"Date", 140}, {"Template", 160}, {"Printer", 120}, {"Copies", 80}, {"", -1}});
  l->addWidget(table_, 1);
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void LabelHistoryView::refresh() {
  auto* model = new QStandardItemModel(0, 5, this);
  model->setHorizontalHeaderLabels({"Date", "Template", "Printer", "Copies", ""});
  try {
    fin::db::LabelPrintHistoryDao dao(fin::db::DatabaseManager::instance());
    auto runs = dao.find_all();
    for (const auto& h : runs) {
      QList<QStandardItem*> row;
      row << new QStandardItem(QString::fromStdString(h.created_at))
          << new QStandardItem(QString::fromStdString(h.template_name))
          << new QStandardItem(QString::fromStdString(h.printer_name))
          << new QStandardItem(QString::number(h.total_copies))
          << new QStandardItem("");
      model->appendRow(row);
    }
  } catch (...) {}
  table_->setModel(model);
}

} // namespace fin::ui
