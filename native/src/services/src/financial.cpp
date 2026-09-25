// fin/services/financial.cpp — Real SQL aggregation for trial_balance / profit_loss / gst_summary
//
// Phase 5d → 10: real impl that aggregates from bills + expenses tables.
#include "fin/services/financial.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/db/bill_dao.hpp"
#include "fin/db/daos.hpp"
#include "fin/db/daos2.hpp"
#include "fin/app/log.hpp"
#include "fin/app/formatters.hpp"

#include <QString>
#include <sqlite3.h>
#include <map>
#include <chrono>

namespace fin::services {

namespace {
std::chrono::sys_days to_sys_days(const QString& s) {
  std::chrono::sys_days out;
  fin::app::Formatters::parse_iso_date(s.toStdString(), out);
  return out;
}
} // namespace

std::vector<TrialBalanceRow> FinancialService::trial_balance(DateRange period) {
  std::vector<TrialBalanceRow> out;
  try {
    fin::db::BillDao bill_dao(fin::db::DatabaseManager::instance());
    fin::db::ExpenseDao exp_dao(fin::db::DatabaseManager::instance());
    fin::db::SupplierDao sup_dao(fin::db::DatabaseManager::instance());
    fin::db::PurchaseBillDao pur_dao(fin::db::DatabaseManager::instance());

    fin::Money total_receivable = fin::Money::zero(fin::CurrencyId::INR);
    fin::Money total_sales = fin::Money::zero(fin::CurrencyId::INR);
    fin::Money total_gst = fin::Money::zero(fin::CurrencyId::INR);

    for (const auto& b : bill_dao.find_all()) {
      // Filter by period (skip if outside).
      auto d = to_sys_days(QString::fromStdString(b.date));
      if (d < period.from || d > period.to) continue;
      total_sales += b.totals.taxable;
      total_gst += b.totals.cgst + b.totals.sgst + b.totals.igst;
      fin::Money paid = fin::Money::zero(fin::CurrencyId::INR);
      for (const auto& p : b.payments) paid += p.amount;
      total_receivable += b.totals.grand_total - paid;
    }

    fin::Money total_payable = fin::Money::zero(fin::CurrencyId::INR);
    for (const auto& p : pur_dao.find_all()) {
      auto d = to_sys_days(QString::fromStdString(p.date));
      if (d < period.from || d > period.to) continue;
      total_payable += p.due;
    }

    fin::Money total_expense = fin::Money::zero(fin::CurrencyId::INR);
    for (const auto& e : exp_dao.find_all()) {
      auto d = to_sys_days(QString::fromStdString(e.date));
      if (d < period.from || d > period.to) continue;
      total_expense += e.amount;
    }

    out.push_back({"1200", "Sales Revenue",           fin::Money::zero(fin::CurrencyId::INR),  total_sales + total_gst});
    out.push_back({"1300", "Accounts Receivable",      total_receivable,                        fin::Money::zero(fin::CurrencyId::INR)});
    out.push_back({"2100", "Accounts Payable",          fin::Money::zero(fin::CurrencyId::INR),  total_payable});
    out.push_back({"2200", "GST Payable",                fin::Money::zero(fin::CurrencyId::INR),  total_gst});
    out.push_back({"5000", "Operating Expenses",        fin::Money::zero(fin::CurrencyId::INR),  total_expense});
  } catch (const std::exception& e) {
    fin::app::log::errorf("trial_balance: {}", e.what());
  }
  return out;
}

std::vector<ProfitLossRow> FinancialService::profit_and_loss(DateRange period) {
  std::vector<ProfitLossRow> out;
  try {
    fin::db::BillDao bill_dao(fin::db::DatabaseManager::instance());
    fin::db::ExpenseDao exp_dao(fin::db::DatabaseManager::instance());
    fin::db::PurchaseBillDao pur_dao(fin::db::DatabaseManager::instance());

    fin::Money revenue = fin::Money::zero(fin::CurrencyId::INR);
    fin::Money cogs = fin::Money::zero(fin::CurrencyId::INR);
    fin::Money expenses = fin::Money::zero(fin::CurrencyId::INR);

    for (const auto& b : bill_dao.find_all()) {
      auto d = to_sys_days(QString::fromStdString(b.date));
      if (d < period.from || d > period.to) continue;
      revenue += b.totals.taxable;
    }
    for (const auto& p : pur_dao.find_all()) {
      auto d = to_sys_days(QString::fromStdString(p.date));
      if (d < period.from || d > period.to) continue;
      cogs += p.total - p.itc;  // total minus claimable GST = net cost
    }
    for (const auto& e : exp_dao.find_all()) {
      auto d = to_sys_days(QString::fromStdString(e.date));
      if (d < period.from || d > period.to) continue;
      expenses += e.amount;
    }

    out.push_back({"Income", "Sales Revenue", revenue});
    out.push_back({"Cost of Goods Sold", "Purchases", cogs});
    out.push_back({"Expense", "Operating Expenses", expenses});
    out.push_back({"Income", "Net Profit", revenue - cogs - expenses});
  } catch (const std::exception& e) {
    fin::app::log::errorf("profit_and_loss: {}", e.what());
  }
  return out;
}

std::vector<GstSummaryRow> FinancialService::gst_summary(DateRange period) {
  std::map<double, GstSummaryRow> by_rate;
  try {
    fin::db::BillDao bill_dao(fin::db::DatabaseManager::instance());
    for (const auto& b : bill_dao.find_all()) {
      auto d = to_sys_days(QString::fromStdString(b.date));
      if (d < period.from || d > period.to) continue;
      for (const auto& it : b.items) {
        double rate = it.gst_rate.num * 100.0 / it.gst_rate.denom;
        auto& r = by_rate[rate];
        r.gst_rate = it.gst_rate;
        r.gst_rate_label = QString::number(rate) + "%";
        auto taxable = it.net();
        r.taxable += taxable;
        auto tax = taxable.apply(it.gst_rate, fin::Rounding::HalfAwayFromZero);
        r.cgst += fin::allocate_equal(tax, 2)[0];
        r.sgst += fin::allocate_equal(tax, 2)[1];
        r.igst += fin::Money::zero(fin::CurrencyId::INR);
        r.total_tax += tax;
        r.invoice_count++;
      }
    }
  } catch (const std::exception& e) {
    fin::app::log::errorf("gst_summary: {}", e.what());
  }
  std::vector<GstSummaryRow> out;
  for (auto& [rate, row] : by_rate) out.push_back(row);
  return out;
}

} // namespace fin::services
