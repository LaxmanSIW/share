// fin/ui/views/reports_builders.hpp — 9 report tab builders (port of Java ReportsBuilders 1,264 lines)
//
// Skill §5.2: extracted from ReportsView (which now stays small and just
// hosts tabs). Each builder is a pure function returning a QFrame that
// populates the tab content.
//
// Reports:
//   1. Trial Balance
//   2. Profit & Loss
//   3. GST Summary (GSTR-1)
//   4. Buyer Receivables (Ageing)
//   5. Supplier Payables (Ageing)
//   6. Stock Valuation
//   7. Sales by Item
//   8. Expense Breakdown
//   9. Bank/Cash Reconciliation
#pragma once
#include <QFrame>
#include <QDate>

namespace fin::ui {

class ReportsBuilders {
 public:
  static QFrame* build_trial_balance(QWidget* parent);
  static QFrame* build_profit_loss(QWidget* parent);
  static QFrame* build_gst_summary(QWidget* parent);
  static QFrame* build_buyer_receivables(QWidget* parent);
  static QFrame* build_supplier_payables(QWidget* parent);
  static QFrame* build_stock_valuation(QWidget* parent);
  static QFrame* build_sales_by_item(QWidget* parent);
  static QFrame* build_expense_breakdown(QWidget* parent);
  static QFrame* build_bank_cash_reconciliation(QWidget* parent);
};

} // namespace fin::ui
