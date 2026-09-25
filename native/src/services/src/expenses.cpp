// fin/services/expenses.cpp
#include "fin/services/expenses.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/db/daos.hpp"

#include <QUuid>

namespace fin::services {

std::vector<fin::model::ExpenseAccount> ExpenseAccountService::find_active() {
  std::vector<fin::model::ExpenseAccount> out;
  // Phase 5d: filter on archived=false via the DAO; for now return all.
  try {
    fin::db::ExpenseAccountDao dao(fin::db::DatabaseManager::instance());
    auto all = dao.find_all();
    for (const auto& a : all) {
      if (!a.archived) out.push_back(a);
    }
  } catch (...) {}
  return out;
}

std::optional<fin::model::ExpenseAccount> ExpenseAccountService::find_by_id(std::string_view id) {
  try {
    fin::db::ExpenseAccountDao dao(fin::db::DatabaseManager::instance());
    return dao.find_by_id(id);
  } catch (...) { return std::nullopt; }
}

std::string ExpenseAccountService::create(const std::string& name) {
  fin::model::ExpenseAccount a;
  a.id = QUuid::createUuid().toString(QUuid::WithoutBraces).toStdString();
  a.name = name;
  try {
    fin::db::ExpenseAccountDao dao(fin::db::DatabaseManager::instance());
    dao.save(a);
    return a.id;
  } catch (...) { return ""; }
}

bool ExpenseAccountService::archive(const std::string& id) {
  // Phase 5d: real impl fetches, sets archived=true, saves via DAO.
  (void)id;
  return false;
}

std::vector<ExpenseSummary> ExpenseAnalytics::totals_by_category(std::chrono::sys_days /*from*/,
                                                                    std::chrono::sys_days /*to*/) {
  // Phase 5d: SQL GROUP BY category, SUM(amount) for the period.
  return {};
}

std::vector<ExpenseAnalytics::PayeeTotal> ExpenseAnalytics::top_payees(std::chrono::sys_days /*from*/,
                                                                          std::chrono::sys_days /*to*/,
                                                                          int /*top_n*/) {
  return {};
}

std::vector<ExpenseAnalytics::MonthTotal> ExpenseAnalytics::monthly_trend(std::chrono::sys_days /*end*/) {
  return {};
}

} // namespace fin::services
