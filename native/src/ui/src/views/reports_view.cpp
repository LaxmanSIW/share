// fin/ui/views/reports_view.cpp — Tabbed Reports view wiring ReportsBuilders
#include "fin/ui/views/reports_view.hpp"
#include "fin/ui/views/reports_builders.hpp"
#include "fin/ui/ui_theme.hpp"
#include <QVBoxLayout>
#include <QTabWidget>
#include <QLabel>

namespace fin::ui {

ReportsView::ReportsView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  l->addWidget(UiTheme::pageTitle("Reports"));
  l->addWidget(UiTheme::pageSubtitle("Trial balance, P&L, GST summary, ageing, stock valuation, custom builders"));

  tabs_ = new QTabWidget(this);
  tabs_->addTab(ReportsBuilders::build_trial_balance(tabs_), "Trial Balance");
  tabs_->addTab(ReportsBuilders::build_profit_loss(tabs_), "P&L");
  tabs_->addTab(ReportsBuilders::build_gst_summary(tabs_), "GST Summary");
  tabs_->addTab(ReportsBuilders::build_buyer_receivables(tabs_), "Buyer Receivables");
  tabs_->addTab(ReportsBuilders::build_supplier_payables(tabs_), "Supplier Payables");
  tabs_->addTab(ReportsBuilders::build_stock_valuation(tabs_), "Stock Valuation");
  tabs_->addTab(ReportsBuilders::build_sales_by_item(tabs_), "Sales by Item");
  tabs_->addTab(ReportsBuilders::build_expense_breakdown(tabs_), "Expense Breakdown");
  tabs_->addTab(ReportsBuilders::build_bank_cash_reconciliation(tabs_), "Bank/Cash Reconciliation");
  l->addWidget(tabs_, 1);
}

void ReportsView::refresh() {}

} // namespace fin::ui
