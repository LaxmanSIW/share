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
  static_cast<QHBoxLayout*>(header->layout())->addItem(tb);
  static_cast<QHBoxLayout*>(header->layout())->addItem(UiTheme::hspacer());
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
  auto* model = new QStandardItemModel(0, 4, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Date") << QString::fromLatin1("Template") << QString::fromLatin1("Printer") << QString::fromLatin1("Copies"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-25 10:30")) << new QStandardItem(QString::fromLatin1("Label 40x15mm")) << new QStandardItem(QString::fromLatin1("Zebra GK420d")) << new QStandardItem(QString::fromLatin1("100")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-24 14:15")) << new QStandardItem(QString::fromLatin1("Label 50x20mm")) << new QStandardItem(QString::fromLatin1("TSC TTP-244")) << new QStandardItem(QString::fromLatin1("50")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-23 09:00")) << new QStandardItem(QString::fromLatin1("A4 24-up Labels")) << new QStandardItem(QString::fromLatin1("HP LaserJet Pro")) << new QStandardItem(QString::fromLatin1("25")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-22 16:45")) << new QStandardItem(QString::fromLatin1("Roll 100x50mm")) << new QStandardItem(QString::fromLatin1("SATO CL4NX")) << new QStandardItem(QString::fromLatin1("500")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("2026-09-21 11:30")) << new QStandardItem(QString::fromLatin1("Label 40x15mm")) << new QStandardItem(QString::fromLatin1("Zebra GK420d")) << new QStandardItem(QString::fromLatin1("200")); model->appendRow(row); }
  table_->setModel(model);
}

} // namespace fin::ui
