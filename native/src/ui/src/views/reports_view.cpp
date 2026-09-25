#include "fin/ui/views/reports_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include <QVBoxLayout>
#include <QTabWidget>
#include <QLabel>
#include <QStandardItemModel>

namespace fin::ui {

ReportsView::ReportsView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  l->addWidget(UiTheme::pageTitle("Reports"));
  l->addWidget(UiTheme::pageSubtitle("Trial balance, P&L, GST summary, ageing, stock valuation"));

  tabs_ = new QTabWidget(this);
  tabs_->setStyleSheet(
    "QTabWidget::pane { background-color: transparent; border: 1px solid #232B38; border-radius: 6px; } "
    "QTabBar::tab { background-color: transparent; border: 1px solid transparent; "
    "border-bottom: none; border-top-left-radius: 6px; border-top-right-radius: 6px; "
    "color: #94A3B8; padding: 8px 16px; margin-right: 2px; } "
    "QTabBar::tab:hover { background-color: #1A222D; color: #F4F4F5; } "
    "QTabBar::tab:selected { background-color: #151B25; border-color: #232B38; color: #F4F4F5; "
    "border-bottom: 2px solid #D9A13B; }"
  );

  // Helper lambda to create a tab with a data grid + sample data
  auto make_tab = [&](const QString& title, QStringList headers, QList<QStringList> rows) {
    auto* grid = new DataGrid(tabs_);
    QList<DataGrid::ColumnSpec> cols;
    for (int i = 0; i < headers.size(); i++) {
      cols.append({headers[i], i == headers.size()-1 ? -1 : 120});
    }
    grid->set_columns(cols);
    auto* model = new QStandardItemModel(0, headers.size(), grid);
    model->setHorizontalHeaderLabels(headers);
    for (const auto& row : rows) {
      QList<QStandardItem*> items;
      for (const auto& v : row) items << new QStandardItem(v);
      model->appendRow(items);
    }
    grid->setModel(model);
    tabs_->addTab(grid, title);
  };

  // Trial Balance
  make_tab("Trial Balance",
    {"Account", "Debit", "Credit"},
    {{"Sales Revenue", "", "Rs 4,82,650"},
     {"Accounts Receivable", "Rs 3,98,855", ""},
     {"Accounts Payable", "", "Rs 2,15,000"},
     {"GST Payable", "", "Rs 58,220"},
     {"Operating Expenses", "", "Rs 85,200"}});

  // P&L
  make_tab("P&L",
    {"Section", "Account", "Amount"},
    {{"Income", "Sales Revenue", "Rs 4,82,650"},
     {"COGS", "Purchases", "Rs 2,15,000"},
     {"Expense", "Operating Expenses", "Rs 85,200"},
     {"Net Profit", "", "Rs 1,82,450"}});

  // GST Summary
  make_tab("GST Summary",
    {"GST %", "Taxable", "CGST", "SGST", "IGST", "Total Tax"},
    {{"5%", "Rs 50,000", "Rs 1,250", "Rs 1,250", "", "Rs 2,500"},
     {"12%", "Rs 80,000", "Rs 4,800", "Rs 4,800", "", "Rs 9,600"},
     {"18%", "Rs 3,00,000", "Rs 27,000", "Rs 27,000", "", "Rs 54,000"},
     {"28%", "Rs 52,650", "Rs 7,371", "Rs 7,371", "", "Rs 14,742"}});

  // Buyer Receivables
  make_tab("Receivables",
    {"Buyer", "Bill No", "Date", "Amount", "Days Overdue"},
    {{"Acme Industries", "INV-0041", "2026-09-24", "Rs 32,100", "1"},
     {"Mahalaxmi Textiles", "INV-0039", "2026-09-22", "Rs 18,750", "3"},
     {"Royal Exports", "INV-0036", "2026-09-18", "Rs 45,000", "7"}});

  // Stock Valuation
  make_tab("Stock",
    {"Item", "Stock", "Rate", "Value"},
    {{"Steel Rod 12mm", "550", "Rs 100", "Rs 55,000"},
     {"Cement Bag 50kg", "1200", "Rs 350", "Rs 4,20,000"},
     {"Sand (ton)", "55", "Rs 1500", "Rs 82,500"}});

  // Outstanding Report (matches Java buildOutstandingReport)
  make_tab("Outstanding",
    {"Buyer", "Bill No", "Date", "Amount", "Days Overdue"},
    {{"Acme Industries", "INV-0041", "2026-09-24", "Rs 32,100", "1"},
     {"Mahalaxmi Textiles", "INV-0039", "2026-09-22", "Rs 18,750", "3"},
     {"Royal Exports", "INV-0036", "2026-09-18", "Rs 45,000", "7"},
     {"Shree Trading Co.", "INV-0033", "2026-09-15", "Rs 22,500", "10"}});

  // Sales Trends (matches Java buildSalesTrendsReport)
  make_tab("Sales Trends",
    {"Month", "Invoiced", "Collected", "Growth %"},
    {{"Sep 2026", "Rs 4,82,650", "Rs 3,61,940", "+12.4%"},
     {"Aug 2026", "Rs 4,29,200", "Rs 3,35,100", "+8.1%"},
     {"Jul 2026", "Rs 3,97,100", "Rs 3,10,050", "+5.2%"},
     {"Jun 2026", "Rs 3,77,500", "Rs 2,95,000", "+3.1%"},
     {"May 2026", "Rs 3,66,200", "Rs 2,86,100", "+2.8%"}});

  // Item Movement (matches Java buildItemMovementReport)
  make_tab("Item Movement",
    {"Item", "Qty Sold", "Revenue", "Qty Purchased", "Current Stock"},
    {{"Steel Rod 12mm", "550", "Rs 55,000", "700", "550"},
     {"Cement Bag 50kg", "300", "Rs 1,05,000", "500", "1200"},
     {"Sand (ton)", "15", "Rs 22,500", "20", "55"}});

  // Category Breakdown (matches Java buildCategoryBreakdownReport)
  make_tab("Category",
    {"Category", "Items", "Qty Sold", "Revenue"},
    {{"Construction", "3", "865", "Rs 1,82,500"},
     {"Finishing", "1", "120", "Rs 30,000"},
     {"Raw Materials", "1", "350", "Rs 2,80,000"}});

  l->addWidget(tabs_, 1);
}

void ReportsView::refresh() {}

} // namespace fin::ui
