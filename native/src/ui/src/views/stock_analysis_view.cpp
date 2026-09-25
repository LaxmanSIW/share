#include "fin/ui/views/stock_analysis_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/db/daos.hpp"
#include "fin/services/auth_session.hpp"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QStandardItemModel>
#include <QTimer>

namespace fin::ui {

StockAnalysisView::StockAnalysisView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  auto* header = UiTheme::row(12, this);
  auto* tb = new QVBoxLayout(); tb->setSpacing(2);
  tb->addWidget(UiTheme::pageTitle("Stock & Profit"));
  tb->addWidget(UiTheme::pageSubtitle("Stock movement history + profit margins"));
  static_cast<QHBoxLayout*>(header->layout())->addItem(tb);
  static_cast<QHBoxLayout*>(header->layout())->addItem(UiTheme::hspacer());
  l->addWidget(header);
  table_ = new DataGrid(this);
  table_->set_columns({{"Item", 200}, {"Opening", 100}, {"In", 100}, {"Out", 100}, {"Closing", 100}, {"Value", 120}, {"", -1}});
  l->addWidget(table_, 1);
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void StockAnalysisView::refresh() {
  auto* model = new QStandardItemModel(0, 6, this);
  model->setHorizontalHeaderLabels(QStringList() << QString::fromLatin1("Item") << QString::fromLatin1("Opening") << QString::fromLatin1("In") << QString::fromLatin1("Out") << QString::fromLatin1("Closing") << QString::fromLatin1("Value"));
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Steel Rod 12mm")) << new QStandardItem(QString::fromLatin1("500")) << new QStandardItem(QString::fromLatin1("200")) << new QStandardItem(QString::fromLatin1("150")) << new QStandardItem(QString::fromLatin1("550")) << new QStandardItem(QString::fromLatin1("Rs 55,000")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Cement Bag 50kg")) << new QStandardItem(QString::fromLatin1("1000")) << new QStandardItem(QString::fromLatin1("500")) << new QStandardItem(QString::fromLatin1("300")) << new QStandardItem(QString::fromLatin1("1200")) << new QStandardItem(QString::fromLatin1("Rs 60,000")); model->appendRow(row); }
  { QList<QStandardItem*> row; row << new QStandardItem(QString::fromLatin1("Sand (ton)")) << new QStandardItem(QString::fromLatin1("50")) << new QStandardItem(QString::fromLatin1("20")) << new QStandardItem(QString::fromLatin1("15")) << new QStandardItem(QString::fromLatin1("55")) << new QStandardItem(QString::fromLatin1("Rs 82,500")); model->appendRow(row); }
  table_->setModel(model);
}

} // namespace fin::ui
