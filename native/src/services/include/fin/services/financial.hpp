// fin/services/financial.hpp — FinancialService (port of Java FinancialService.java)
//
// Trial balance, P&L, GST/VAT summaries. Per skill §6 (accounting): aggregate
// in SQL from maintained running balances; reports run on the DB pool with
// LatestOnly + cancel; results are immutable snapshot objects handed to the UI.
#pragma once
#include "fin/money.hpp"
#include "fin/model/bill.hpp"

#include <chrono>
#include <map>
#include <string>
#include <vector>

namespace fin::services {

struct TrialBalanceRow {
  std::string account_code;
  std::string account_name;
  fin::Money  debit{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money  credit{fin::Money::zero(fin::CurrencyId::INR)};
};

struct ProfitLossRow {
  std::string section;       // "Income" / "Expense" / "Cost of Goods Sold"
  std::string account;
  fin::Money  amount{fin::Money::zero(fin::CurrencyId::INR)};
};

struct GstSummaryRow {
  std::string gst_rate_label;   // "5%", "12%", "18%", "28%"
  fin::Rate   gst_rate;
  fin::Money  taxable{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money  cgst{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money  sgst{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money  igst{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money  total_tax{fin::Money::zero(fin::CurrencyId::INR)};
  int         invoice_count{0};
};

struct DateRange {
  std::chrono::sys_days from;
  std::chrono::sys_days to;
};

class FinancialService {
 public:
  /// Compute trial balance from all posted sales and purchase bills in the
  /// period. Returns rows for each account (Buyer receivables, Supplier
  /// payables, Income accounts, Expense accounts, GST Payable).
  static std::vector<TrialBalanceRow> trial_balance(DateRange period);

  /// Compute profit & loss statement for the period:
  ///   Revenue (sales invoices − credit notes) − COGS (purchase bills) − Expenses
  /// Returns rows grouped by section.
  static std::vector<ProfitLossRow> profit_and_loss(DateRange period);

  /// Compute GST summary (GSTR-1 style): per-rate tax breakdown across all
  /// sales invoices in the period. Used by Reports view.
  static std::vector<GstSummaryRow> gst_summary(DateRange period);

  /// Net profit = total revenue − total expenses − COGS.
  /// Pure function over a ProfitLoss result — no DB.
  static fin::Money net_profit_from_pl(const std::vector<ProfitLossRow>& pl);
};

} // namespace fin::services
