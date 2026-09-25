// fin/services/expenses.hpp — Expense services (port of Java ExpenseAccountService 200 + ExpenseAnalytics 300)
//
// Provides CRUD over expense accounts + expense vouchers, plus analytics
// (monthly totals by category, top payees, etc.).
#pragma once
#include "fin/money.hpp"
#include "fin/model/buyer_supplier.hpp"  // for Expense, ExpenseAccount

#include <chrono>
#include <map>
#include <string>
#include <vector>

namespace fin::services {

struct ExpenseSummary {
  std::string category;
  fin::Money  total{fin::Money::zero(fin::CurrencyId::INR)};
  int         count{0};
};

class ExpenseAccountService {
 public:
  /// List all active (non-archived) expense accounts for the current user.
  static std::vector<fin::model::ExpenseAccount> find_active();
  /// Find an account by id.
  static std::optional<fin::model::ExpenseAccount> find_by_id(std::string_view id);
  /// Create a new expense account.
  static std::string create(const std::string& name);
  /// Archive an account (skill §11: soft-delete, never hard-delete).
  static bool archive(const std::string& id);
};

class ExpenseAnalytics {
 public:
  /// Compute total expenses per category for the given period.
  static std::vector<ExpenseSummary> totals_by_category(std::chrono::sys_days from,
                                                          std::chrono::sys_days to);

  /// Top N payees by total expense in the period.
  struct PayeeTotal { std::string payee; fin::Money total; };
  static std::vector<PayeeTotal> top_payees(std::chrono::sys_days from,
                                              std::chrono::sys_days to,
                                              int top_n = 10);

  /// Month-over-month trend (12 months ending at `end`).
  struct MonthTotal { std::string ym; fin::Money total; };
  static std::vector<MonthTotal> monthly_trend(std::chrono::sys_days end);
};

} // namespace fin::services
