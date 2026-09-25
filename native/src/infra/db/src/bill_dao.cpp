// fin/db/bill_dao.cpp
#include "fin/db/bill_dao.hpp"
#include "fin/db/json.hpp"
#include "fin/db/dao.hpp"
#include "fin/app/log.hpp"
#include "fin/app/formatters.hpp"
#include "fin/model/enums.hpp"

#include <nlohmann/json.hpp>
#include <sqlite3.h>

#include <chrono>
#include <string>

namespace fin::db {

using fin::model::Bill;
using fin::model::BillPayment;
using fin::model::BillStatus;
using fin::model::DocType;
using nlohmann::json;

namespace {

std::string iso_utc_helper() {
  // Formatters::iso_utc lives in fin::app::Formatters (formatters.hpp).
  return fin::app::Formatters::iso_utc(std::chrono::utc_clock::now());
}
} // namespace

std::vector<Bill> BillDao::find_all() {
  std::vector<Bill> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;

  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  const char* sql = "SELECT json_data FROM bills WHERE user_id = ? ORDER BY date DESC, bill_no DESC";
  if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    fin::app::log::errorf("BillDao::find_all: prepare failed: {}", sqlite3_errmsg(h));
    return list;
  }
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    auto* p = sqlite3_column_text(stmt, 0);
    if (!p) continue;
    int n = sqlite3_column_bytes(stmt, 0);
    std::string s(reinterpret_cast<const char*>(p), static_cast<std::size_t>(n));
    try {
      list.push_back(json::parse(s).get<Bill>());
    } catch (const std::exception& e) {
      fin::app::log::errorf("BillDao::find_all: parse failed: {}", e.what());
    }
  }
  sqlite3_finalize(stmt);
  return list;
}

std::optional<Bill> BillDao::find_by_id(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return std::nullopt;

  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  const char* sql = "SELECT json_data FROM bills WHERE id = ? AND user_id = ?";
  if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return std::nullopt;
  sqlite3_bind_text(stmt, 1, id.data(),  static_cast<int>(id.size()), SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
  std::optional<Bill> out;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    auto* p = sqlite3_column_text(stmt, 0);
    int n = sqlite3_column_bytes(stmt, 0);
    if (p) {
      std::string s(reinterpret_cast<const char*>(p), static_cast<std::size_t>(n));
      try { out = json::parse(s).get<Bill>(); } catch (...) {}
    }
  }
  sqlite3_finalize(stmt);
  return out;
}

std::optional<Bill> BillDao::find_by_bill_no(std::string_view bill_no) {
  std::string uid = effective_user_id();
  if (uid.empty() || bill_no.empty()) return std::nullopt;

  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  const char* sql = "SELECT json_data FROM bills WHERE bill_no = ? AND user_id = ?";
  if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return std::nullopt;
  sqlite3_bind_text(stmt, 1, bill_no.data(), static_cast<int>(bill_no.size()), SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
  std::optional<Bill> out;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    auto* p = sqlite3_column_text(stmt, 0);
    int n = sqlite3_column_bytes(stmt, 0);
    if (p) {
      std::string s(reinterpret_cast<const char*>(p), static_cast<std::size_t>(n));
      try { out = json::parse(s).get<Bill>(); } catch (...) {}
    }
  }
  sqlite3_finalize(stmt);
  return out;
}

bool BillDao::save(const Bill& bill_const) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;

  Bill bill = bill_const;
  std::string now = iso_utc_helper();
  if (bill.created_at.empty()) bill.created_at = now;
  bill.updated_at = now;

  // Compute indexed columns from the bill.
  auto grand = bill.totals.grand_total;
  fin::Money paid = fin::Money::zero(grand.currency());
  for (const auto& p : bill.payments) paid += p.amount;
  if (paid.is_zero() && bill.status == BillStatus::Paid) paid = grand;
  auto due = grand - paid;
  if (bill.status == BillStatus::Cancelled) due = fin::Money::zero(grand.currency());
  if (due.is_negative()) due = fin::Money::zero(grand.currency());

  std::string buyer = bill.buyer_name();
  std::string json_str = json(bill).dump();

  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql =
      "INSERT INTO bills (id, user_id, bill_no, date, doc_type, status, buyer_name, "
      "grand_total, due_amount, json_data, created_at, updated_at) "
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, bill_no = excluded.bill_no, "
      "date = excluded.date, doc_type = excluded.doc_type, status = excluded.status, "
      "buyer_name = excluded.buyer_name, grand_total = excluded.grand_total, "
      "due_amount = excluded.due_amount, json_data = excluded.json_data, "
      "updated_at = excluded.updated_at WHERE bills.user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1,  bill.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2,  uid.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3,  bill.bill_no.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4,  bill.date.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5,  fin::model::code(bill.doc_type), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 6,  fin::model::code(bill.status), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 7,  buyer.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_double(stmt, 8, static_cast<double>(grand.minor()) / 100.0);  // store as double for back-compat with Java
    sqlite3_bind_double(stmt, 9, static_cast<double>(due.minor())   / 100.0);
    sqlite3_bind_text(stmt, 10, json_str.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 11, bill.created_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 12, bill.updated_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool BillDao::erase(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM bills WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool BillDao::increment_print_count(std::string_view id) {
  auto b = find_by_id(id);
  if (!b) return false;
  b->print_count += 1;
  return save(*b);
}

} // namespace fin::db
