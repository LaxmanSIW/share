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
  header->layout()->addLayout(tb);
  header->layout()->addItem(UiTheme::hspacer());
  l->addWidget(header);
  table_ = new DataGrid(this);
  table_->set_columns({{"Item", 200}, {"Opening", 100}, {"In", 100}, {"Out", 100}, {"Closing", 100}, {"Value", 120}, {"", -1}});
  l->addWidget(table_, 1);
  QTimer::singleShot(0, this, [this]{ refresh(); });
}

void StockAnalysisView::refresh() {
  auto* model = new QStandardItemModel(0, 7, this);
  model->setHorizontalHeaderLabels({"Item", "Opening", "In", "Out", "Closing", "Value", ""});
  try {
    fin::db::ItemDao dao(fin::db::DatabaseManager::instance());
    auto items = dao.find_all();
    for (const auto& it : items) {
      QList<QStandardItem*> row;
      row << new QStandardItem(QString::fromStdString(it.name))
          << new QStandardItem(QString::fromStdString(it.opening_stock.to_decimal_string()))
          << new QStandardItem("0")
          << new QStandardItem("0")
          << new QStandardItem(QString::fromStdString(it.current_stock.to_decimal_string()))
          << new QStandardItem(QString::fromStdString(it.current_stock.to_decimal_string()))
          << new QStandardItem("");
      model->appendRow(row);
    }
  } catch (...) {}
  table_->setModel(model);
}

} // namespace fin::ui
