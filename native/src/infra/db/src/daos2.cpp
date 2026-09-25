// fin/db/daos2.cpp — Implementations for the 8 remaining DAOs.
//
// All follow the same shape as BillDao: parameterised SQL, json_data column
// for full model + indexed columns for listing, scoped by user_id.
// Skill rule §8: no string-concatenated SQL anywhere in this file.
#include "fin/db/daos2.hpp"
#include "fin/db/dao.hpp"
#include "fin/db/json.hpp"
#include "fin/app/log.hpp"
#include "fin/app/formatters.hpp"

#include <nlohmann/json.hpp>
#include <sqlite3.h>

#include <chrono>
#include <string>

namespace fin::db {

namespace {
std::string iso_now() {
  return fin::app::Formatters::iso_utc(std::chrono::utc_clock::now());
}
} // namespace

// ============================================================
// TransportDao
// ============================================================

std::vector<fin::model::Transport> TransportDao::find_all() {
  std::vector<fin::model::Transport> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, name, phone, vehicle_number, created_at, updated_at FROM transports WHERE user_id = ? ORDER BY name", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::Transport t;
    auto get = [&](int i){ const char* p = (const char*)sqlite3_column_text(stmt, i); return p ? std::string(p) : std::string{}; };
    t.id = get(0); t.name = get(1); t.phone = get(2); t.vehicle_number = get(3);
    t.created_at = get(4); t.updated_at = get(5);
    list.push_back(std::move(t));
  }
  sqlite3_finalize(stmt);
  return list;
}

std::optional<fin::model::Transport> TransportDao::find_by_id(std::string_view id) {
  auto all = find_all();
  for (auto& t : all) if (t.id == id) return std::move(t);
  return std::nullopt;
}

bool TransportDao::save(const fin::model::Transport& t_const) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;
  auto t = t_const;
  std::string now = iso_now();
  if (t.created_at.empty()) t.created_at = now;
  t.updated_at = now;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO transports (id, user_id, name, phone, vehicle_number, created_at, updated_at) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?) "
                      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, "
                      "phone = excluded.phone, vehicle_number = excluded.vehicle_number, updated_at = excluded.updated_at "
                      "WHERE transports.user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, t.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, t.name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, t.phone.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, t.vehicle_number.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 6, t.created_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 7, t.updated_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool TransportDao::erase(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM transports WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// ============================================================
// CategoryDao (unique by (user_id, name) — MCP race backstop at DB level)
// ============================================================

std::vector<fin::model::ItemCategory> CategoryDao::find_all() {
  std::vector<fin::model::ItemCategory> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, name, created_at, updated_at FROM categories WHERE user_id = ? ORDER BY name COLLATE NOCASE", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::ItemCategory c;
    auto get = [&](int i){ const char* p = (const char*)sqlite3_column_text(stmt, i); return p ? std::string(p) : std::string{}; };
    c.id = get(0); c.name = get(1); c.created_at = get(2); c.updated_at = get(3);
    list.push_back(std::move(c));
  }
  sqlite3_finalize(stmt);
  return list;
}

std::optional<fin::model::ItemCategory> CategoryDao::find_by_id(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return std::nullopt;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, name, created_at, updated_at FROM categories WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return std::nullopt;
  sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
  std::optional<fin::model::ItemCategory> out;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::ItemCategory c;
    auto get = [&](int i){ const char* p = (const char*)sqlite3_column_text(stmt, i); return p ? std::string(p) : std::string{}; };
    c.id = get(0); c.name = get(1); c.created_at = get(2); c.updated_at = get(3);
    out = std::move(c);
  }
  sqlite3_finalize(stmt);
  return out;
}

bool CategoryDao::save(const fin::model::ItemCategory& c_const) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;
  auto c = c_const;
  std::string now = iso_now();
  if (c.created_at.empty()) c.created_at = now;
  c.updated_at = now;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO categories (id, user_id, name, created_at, updated_at) "
                      "VALUES (?, ?, ?, ?, ?) "
                      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, updated_at = excluded.updated_at "
                      "WHERE categories.user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, c.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, c.name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, c.created_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, c.updated_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool CategoryDao::erase(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM categories WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// ============================================================
// ExpenseDao
// ============================================================

std::vector<fin::model::Expense> ExpenseDao::find_all() {
  std::vector<fin::model::Expense> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, date, category, amount, payment_mode, json_data, created_at, updated_at FROM expenses WHERE user_id = ? ORDER BY date DESC", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::Expense e;
    auto get = [&](int i){ const char* p = (const char*)sqlite3_column_text(stmt, i); return p ? std::string(p) : std::string{}; };
    e.id = get(0); e.date = get(1); e.category = get(2);
    // Back-compat: amount stored as REAL (double); convert to Money.
    double amt = sqlite3_column_double(stmt, 3);
    e.amount = fin::Money::parse_round(std::to_string(amt), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    e.payment_mode = get(4);
    e.json_data = get(5);
    e.created_at = get(6); e.updated_at = get(7);
    e.user_id = uid;
    list.push_back(std::move(e));
  }
  sqlite3_finalize(stmt);
  return list;
}

std::optional<fin::model::Expense> ExpenseDao::find_by_id(std::string_view id) {
  auto all = find_all();
  for (auto& e : all) if (e.id == id) return std::move(e);
  return std::nullopt;
}

bool ExpenseDao::save(const fin::model::Expense& e_const) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;
  auto e = e_const;
  std::string now = iso_now();
  if (e.created_at.empty()) e.created_at = now;
  e.updated_at = now;
  e.user_id = uid;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO expenses (id, user_id, date, category, amount, payment_mode, json_data, created_at, updated_at) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, date = excluded.date, category = excluded.category, "
                      "amount = excluded.amount, payment_mode = excluded.payment_mode, json_data = excluded.json_data, updated_at = excluded.updated_at "
                      "WHERE expenses.user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    double amt_d = static_cast<double>(e.amount.minor()) / 100.0;
    sqlite3_bind_text(stmt, 1, e.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, e.user_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, e.date.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, e.category.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_double(stmt, 5, amt_d);
    sqlite3_bind_text(stmt, 6, e.payment_mode.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 7, e.json_data.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 8, e.created_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 9, e.updated_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool ExpenseDao::erase(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM expenses WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// ============================================================
// ExpenseAccountDao (light index; full fidelity in json_data)
// ============================================================

std::vector<fin::model::ExpenseAccount> ExpenseAccountDao::find_all() {
  std::vector<fin::model::ExpenseAccount> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, name, archived, json_data, created_at, updated_at FROM expense_accounts WHERE user_id = ? ORDER BY name COLLATE NOCASE", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::ExpenseAccount a;
    auto get = [&](int i){ const char* p = (const char*)sqlite3_column_text(stmt, i); return p ? std::string(p) : std::string{}; };
    a.id = get(0); a.name = get(1);
    a.archived = sqlite3_column_int(stmt, 2) != 0;
    a.json_data = get(3);
    a.created_at = get(4); a.updated_at = get(5);
    a.user_id = uid;
    list.push_back(std::move(a));
  }
  sqlite3_finalize(stmt);
  return list;
}

std::optional<fin::model::ExpenseAccount> ExpenseAccountDao::find_by_id(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return std::nullopt;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, name, archived, json_data, created_at, updated_at FROM expense_accounts WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return std::nullopt;
  sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
  std::optional<fin::model::ExpenseAccount> out;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::ExpenseAccount a;
    auto get = [&](int i){ const char* p = (const char*)sqlite3_column_text(stmt, i); return p ? std::string(p) : std::string{}; };
    a.id = get(0); a.name = get(1);
    a.archived = sqlite3_column_int(stmt, 2) != 0;
    a.json_data = get(3);
    a.created_at = get(4); a.updated_at = get(5);
    a.user_id = uid;
    out = std::move(a);
  }
  sqlite3_finalize(stmt);
  return out;
}

bool ExpenseAccountDao::save(const fin::model::ExpenseAccount& a_const) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;
  auto a = a_const;
  std::string now = iso_now();
  if (a.created_at.empty()) a.created_at = now;
  a.updated_at = now;
  a.user_id = uid;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO expense_accounts (id, user_id, name, archived, json_data, created_at, updated_at) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?) "
                      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, archived = excluded.archived, "
                      "json_data = excluded.json_data, updated_at = excluded.updated_at "
                      "WHERE expense_accounts.user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, a.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, a.user_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, a.name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int (stmt, 4, a.archived ? 1 : 0);
    sqlite3_bind_text(stmt, 5, a.json_data.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 6, a.created_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 7, a.updated_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool ExpenseAccountDao::erase(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM expense_accounts WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// ============================================================
// PurchaseBillDao (supplier inward supply register)
// ============================================================

namespace {

std::string purchase_bill_to_json(const fin::model::PurchaseBill& p) {
  nlohmann::json j;
  j["id"] = p.id;
  j["billNo"] = p.bill_no;
  j["supplierBillNo"] = p.supplier_bill_no;
  j["date"] = p.date;
  j["supplierId"] = p.supplier_id;
  j["supplierName"] = p.supplier_name;
  j["items"] = p.items;
  j["total"] = p.total;
  j["itc"] = p.itc;
  j["status"] = p.status;
  j["notes"] = p.notes;
  j["paid"] = p.paid;
  j["due"] = p.due;
  j["variables"] = p.variables;
  return j.dump();
}

fin::model::PurchaseBill purchase_bill_from_json(std::string_view s) {
  fin::model::PurchaseBill p;
  try {
    auto j = nlohmann::json::parse(s);
    p.id = j.value("id", "");
    p.bill_no = j.value("billNo", "");
    p.supplier_bill_no = j.value("supplierBillNo", "");
    p.date = j.value("date", "");
    p.supplier_id = j.value("supplierId", "");
    p.supplier_name = j.value("supplierName", "");
    p.items = j.value("items", std::vector<fin::model::BillItem>{});
    p.total = j.value("total", fin::Money::zero(fin::CurrencyId::INR));
    p.itc = j.value("itc", fin::Money::zero(fin::CurrencyId::INR));
    p.status = j.value("status", "draft");
    p.notes = j.value("notes", "");
    p.paid = j.value("paid", fin::Money::zero(fin::CurrencyId::INR));
    p.due = j.value("due", fin::Money::zero(fin::CurrencyId::INR));
    p.variables = j.value("variables", std::map<std::string, std::string>{});
  } catch (...) {}
  return p;
}

} // namespace

std::vector<fin::model::PurchaseBill> PurchaseBillDao::find_all() {
  std::vector<fin::model::PurchaseBill> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, bill_no, supplier_bill_no, date, supplier_id, supplier_name, total, itc, status, json_data, created_at, updated_at FROM purchase_bills WHERE user_id = ? ORDER BY date DESC, bill_no DESC", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    auto get = [&](int i){ const char* p = (const char*)sqlite3_column_text(stmt, i); return p ? std::string(p) : std::string{}; };
    fin::model::PurchaseBill p;
    p.id = get(0); p.bill_no = get(1); p.supplier_bill_no = get(2);
    p.date = get(3); p.supplier_id = get(4); p.supplier_name = get(5);
    // totals stored as REAL — convert at boundary (skill §1)
    double total = sqlite3_column_double(stmt, 6);
    double itc = sqlite3_column_double(stmt, 7);
    p.total = fin::Money::parse_round(std::to_string(total), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    p.itc = fin::Money::parse_round(std::to_string(itc), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    p.status = get(8);
    std::string json_str = get(9);
    if (!json_str.empty()) {
      auto full = purchase_bill_from_json(json_str);
      // Preserve scalar columns from query, but pull items/variables/notes from JSON.
      p.items = std::move(full.items);
      p.variables = std::move(full.variables);
      p.notes = std::move(full.notes);
      p.paid = std::move(full.paid);
      p.due = std::move(full.due);
    }
    p.created_at = get(10); p.updated_at = get(11);
    list.push_back(std::move(p));
  }
  sqlite3_finalize(stmt);
  return list;
}

std::optional<fin::model::PurchaseBill> PurchaseBillDao::find_by_id(std::string_view id) {
  auto all = find_all();
  for (auto& p : all) if (p.id == id) return std::move(p);
  return std::nullopt;
}

bool PurchaseBillDao::save(const fin::model::PurchaseBill& p_const) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;
  auto p = p_const;
  std::string now = iso_now();
  if (p.created_at.empty()) p.created_at = now;
  p.updated_at = now;
  std::string json_str = purchase_bill_to_json(p);
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO purchase_bills (id, user_id, bill_no, supplier_bill_no, date, supplier_id, supplier_name, total, itc, status, json_data, created_at, updated_at) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, bill_no = excluded.bill_no, supplier_bill_no = excluded.supplier_bill_no, "
                      "date = excluded.date, supplier_id = excluded.supplier_id, supplier_name = excluded.supplier_name, total = excluded.total, itc = excluded.itc, "
                      "status = excluded.status, json_data = excluded.json_data, updated_at = excluded.updated_at "
                      "WHERE purchase_bills.user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, p.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, p.bill_no.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, p.supplier_bill_no.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, p.date.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 6, p.supplier_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 7, p.supplier_name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_double(stmt, 8, static_cast<double>(p.total.minor()) / 100.0);
    sqlite3_bind_double(stmt, 9, static_cast<double>(p.itc.minor()) / 100.0);
    sqlite3_bind_text(stmt, 10, p.status.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 11, json_str.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 12, p.created_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 13, p.updated_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool PurchaseBillDao::erase(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM purchase_bills WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// ============================================================
// StockLedgerDao (immutable append-only ledger)
// ============================================================

bool StockLedgerDao::append(const fin::model::StockLedgerEntry& e_const) {
  std::string uid = effective_user_id();
  auto e = e_const;
  if (e.user_id.empty()) e.user_id = uid;
  if (e.created_at.empty()) e.created_at = iso_now();
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO stock_ledger (item_id, transaction_date, voucher_type, voucher_id, voucher_no, qty_in, qty_out, unit_price, user_id, created_at) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, e.item_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, e.transaction_date.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, fin::model::code(e.voucher_type), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, e.voucher_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, e.voucher_no.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_double(stmt, 6, static_cast<double>(e.qty_in.minor()) / 100.0);
    sqlite3_bind_double(stmt, 7, static_cast<double>(e.qty_out.minor()) / 100.0);
    sqlite3_bind_double(stmt, 8, static_cast<double>(e.unit_price.minor()) / 100.0);
    sqlite3_bind_text(stmt, 9, e.user_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 10, e.created_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

std::vector<fin::model::StockLedgerEntry> StockLedgerDao::find_by_item(std::string_view item_id) {
  std::vector<fin::model::StockLedgerEntry> list;
  if (item_id.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, item_id, transaction_date, voucher_type, voucher_id, voucher_no, qty_in, qty_out, unit_price, user_id, created_at FROM stock_ledger WHERE item_id = ? ORDER BY transaction_date ASC, id ASC", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, item_id.data(), static_cast<int>(item_id.size()), SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::StockLedgerEntry e;
    e.id = sqlite3_column_int64(stmt, 0);
    auto get = [&](int i){ const char* p = (const char*)sqlite3_column_text(stmt, i); return p ? std::string(p) : std::string{}; };
    e.item_id = get(1); e.transaction_date = get(2);
    e.voucher_type = fin::model::stock_voucher_type_from_code(get(3));
    e.voucher_id = get(4); e.voucher_no = get(5);
    e.qty_in = fin::Money::parse_round(std::to_string(sqlite3_column_double(stmt, 6)), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    e.qty_out = fin::Money::parse_round(std::to_string(sqlite3_column_double(stmt, 7)), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    e.unit_price = fin::Money::parse_round(std::to_string(sqlite3_column_double(stmt, 8)), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    e.user_id = get(9); e.created_at = get(10);
    list.push_back(std::move(e));
  }
  sqlite3_finalize(stmt);
  return list;
}

fin::Money StockLedgerDao::current_stock(std::string_view item_id) {
  if (item_id.empty()) return fin::Money::zero(fin::CurrencyId::INR);
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT current_stock FROM items WHERE id = ?", -1, &stmt, nullptr) != SQLITE_OK) return fin::Money::zero(fin::CurrencyId::INR);
  sqlite3_bind_text(stmt, 1, item_id.data(), static_cast<int>(item_id.size()), SQLITE_TRANSIENT);
  fin::Money out = fin::Money::zero(fin::CurrencyId::INR);
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    double v = sqlite3_column_double(stmt, 0);
    out = fin::Money::parse_round(std::to_string(v), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
  }
  sqlite3_finalize(stmt);
  return out;
}

fin::Money StockLedgerDao::recompute_stock_from_ledger(std::string_view item_id) {
  // Skill §6 (accounting §6): differential test — recompute from raw lines,
  // compare with maintained balance. Drift = critical bug.
  auto entries = find_by_item(item_id);
  if (entries.empty()) return fin::Money::zero(fin::CurrencyId::INR);
  fin::Money bal = fin::Money::zero(fin::CurrencyId::INR);
  for (const auto& e : entries) {
    bal += e.qty_in;
    bal -= e.qty_out;
  }
  return bal;
}

// ============================================================
// TransactionDao (financial book entries: sales/purchase/payment)
// ============================================================

namespace {

std::string transaction_to_json(const fin::model::Transaction& t) {
  nlohmann::json j;
  j["id"] = t.id;
  j["buyerId"] = t.buyer_id;
  j["buyerName"] = t.buyer_name;
  j["bookType"] = t.book_type;
  j["transactionType"] = t.transaction_type;
  j["transactionDate"] = t.transaction_date;
  j["dueDate"] = t.due_date;
  j["amount"] = t.amount;
  j["totalQuantity"] = t.total_quantity;
  j["checkNumber"] = t.check_number;
  j["includeInReporting"] = t.include_in_reporting;
  j["parcel"] = t.parcel;
  j["billId"] = t.bill_id;
  j["billNo"] = t.bill_no;
  j["deleted"] = t.deleted;
  j["deletedReason"] = t.deleted_reason;
  j["deletedAt"] = t.deleted_at;
  j["createdAt"] = t.created_at;
  j["updatedAt"] = t.updated_at;
  return j.dump();
}

fin::model::Transaction transaction_from_json(std::string_view s) {
  fin::model::Transaction t;
  try {
    auto j = nlohmann::json::parse(s);
    t.id = j.value("id", "");
    t.buyer_id = j.value("buyerId", "");
    t.buyer_name = j.value("buyerName", "");
    t.book_type = j.value("bookType", "");
    t.transaction_type = j.value("transactionType", "");
    t.transaction_date = j.value("transactionDate", "");
    t.due_date = j.value("dueDate", "");
    t.amount = j.value("amount", fin::Money::zero(fin::CurrencyId::INR));
    t.total_quantity = j.value("totalQuantity", 0);
    t.check_number = j.value("checkNumber", "");
    t.include_in_reporting = j.value("includeInReporting", true);
    t.parcel = j.value("parcel", 1);
    t.bill_id = j.value("billId", "");
    t.bill_no = j.value("billNo", "");
    t.deleted = j.value("deleted", false);
    t.deleted_reason = j.value("deletedReason", "");
    t.deleted_at = j.value("deletedAt", "");
    t.created_at = j.value("createdAt", "");
    t.updated_at = j.value("updatedAt", "");
  } catch (...) {}
  return t;
}

} // namespace

std::vector<fin::model::Transaction> TransactionDao::find_all() {
  std::vector<fin::model::Transaction> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, buyer_id, buyer_name, book_type, transaction_type, transaction_date, due_date, amount, total_quantity, check_number, include_in_reporting, parcel, bill_id, bill_no, deleted, deleted_reason, deleted_at, created_at, updated_at FROM transactions WHERE user_id = ? ORDER BY transaction_date DESC", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::Transaction t;
    auto get = [&](int i){ const char* p = (const char*)sqlite3_column_text(stmt, i); return p ? std::string(p) : std::string{}; };
    t.id = get(0); t.buyer_id = get(1); t.buyer_name = get(2);
    t.book_type = get(3); t.transaction_type = get(4);
    t.transaction_date = get(5); t.due_date = get(6);
    double amt = sqlite3_column_double(stmt, 7);
    t.amount = fin::Money::parse_round(std::to_string(amt), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    t.total_quantity = sqlite3_column_int(stmt, 8);
    t.check_number = get(9);
    t.include_in_reporting = sqlite3_column_int(stmt, 10) != 0;
    t.parcel = sqlite3_column_int(stmt, 11);
    t.bill_id = get(12); t.bill_no = get(13);
    t.deleted = sqlite3_column_int(stmt, 14) != 0;
    t.deleted_reason = get(15); t.deleted_at = get(16);
    t.created_at = get(17); t.updated_at = get(18);
    t.user_id = uid;
    list.push_back(std::move(t));
  }
  sqlite3_finalize(stmt);
  return list;
}

std::optional<fin::model::Transaction> TransactionDao::find_by_id(std::string_view id) {
  auto all = find_all();
  for (auto& t : all) if (t.id == id) return std::move(t);
  return std::nullopt;
}

std::optional<fin::model::Transaction> TransactionDao::find_by_bill_id(std::string_view bill_id) {
  auto all = find_all();
  for (auto& t : all) if (t.bill_id == bill_id) return std::move(t);
  return std::nullopt;
}

bool TransactionDao::save(const fin::model::Transaction& t_const) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;
  auto t = t_const;
  std::string now = iso_now();
  if (t.created_at.empty()) t.created_at = now;
  t.updated_at = now;
  t.user_id = uid;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO transactions (id, user_id, buyer_id, buyer_name, book_type, transaction_type, transaction_date, due_date, amount, total_quantity, check_number, include_in_reporting, parcel, bill_id, bill_no, deleted, deleted_reason, deleted_at, created_at, updated_at) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, buyer_id = excluded.buyer_id, buyer_name = excluded.buyer_name, book_type = excluded.book_type, transaction_type = excluded.transaction_type, transaction_date = excluded.transaction_date, due_date = excluded.due_date, amount = excluded.amount, total_quantity = excluded.total_quantity, check_number = excluded.check_number, include_in_reporting = excluded.include_in_reporting, parcel = excluded.parcel, bill_id = excluded.bill_id, bill_no = excluded.bill_no, deleted = excluded.deleted, deleted_reason = excluded.deleted_reason, deleted_at = excluded.deleted_at, updated_at = excluded.updated_at WHERE transactions.user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, t.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, t.user_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, t.buyer_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, t.buyer_name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, t.book_type.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 6, t.transaction_type.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 7, t.transaction_date.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 8, t.due_date.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_double(stmt, 9, static_cast<double>(t.amount.minor()) / 100.0);
    sqlite3_bind_int (stmt, 10, t.total_quantity);
    sqlite3_bind_text(stmt, 11, t.check_number.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int (stmt, 12, t.include_in_reporting ? 1 : 0);
    sqlite3_bind_int (stmt, 13, t.parcel);
    sqlite3_bind_text(stmt, 14, t.bill_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 15, t.bill_no.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int (stmt, 16, t.deleted ? 1 : 0);
    sqlite3_bind_text(stmt, 17, t.deleted_reason.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 18, t.deleted_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 19, t.created_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 20, t.updated_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool TransactionDao::soft_delete(std::string_view id, std::string_view reason) {
  // Skill rule §4 (ledger §4): posted entries are immutable; soft-delete for
  // financial audit trail — never erase a transaction; flag + reason + timestamp.
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  std::string now = iso_now();
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "UPDATE transactions SET deleted = 1, deleted_reason = ?, deleted_at = ?, updated_at = ? WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, reason.data(), static_cast<int>(reason.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, now.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, now.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool TransactionDao::erase(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM transactions WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// ============================================================
// LabelPrintHistoryDao (info-only audit of label runs)
// ============================================================

bool LabelPrintHistoryDao::append(const fin::model::LabelPrintHistory& h_const) {
  std::string uid = effective_user_id();
  auto h = h_const;
  if (h.user_id.empty()) h.user_id = uid;
  if (h.created_at.empty()) h.created_at = iso_now();
  return db_.write([&](sqlite3* h2) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO label_print_history (id, user_id, template_id, template_name, printer_name, label_width, label_height, columns, pages, labels, total_copies, summary, lines_json, created_at) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    if (sqlite3_prepare_v2(h2, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, h.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, h.user_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, h.template_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, h.template_name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, h.printer_name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_double(stmt, 6, h.label_width);
    sqlite3_bind_double(stmt, 7, h.label_height);
    sqlite3_bind_int (stmt, 8, h.columns);
    sqlite3_bind_int (stmt, 9, h.pages);
    sqlite3_bind_int (stmt, 10, h.labels);
    sqlite3_bind_int (stmt, 11, h.total_copies);
    sqlite3_bind_text(stmt, 12, h.summary.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 13, h.lines_json.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 14, h.created_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

std::vector<fin::model::LabelPrintHistory> LabelPrintHistoryDao::find_all() {
  std::vector<fin::model::LabelPrintHistory> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, template_id, template_name, printer_name, label_width, label_height, columns, pages, labels, total_copies, summary, lines_json, user_id, created_at FROM label_print_history WHERE user_id = ? ORDER BY created_at DESC", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::LabelPrintHistory lh;
    auto get = [&](int i){ const char* p = (const char*)sqlite3_column_text(stmt, i); return p ? std::string(p) : std::string{}; };
    lh.id = get(0); lh.template_id = get(1); lh.template_name = get(2);
    lh.printer_name = get(3); lh.label_width = sqlite3_column_double(stmt, 4);
    lh.label_height = sqlite3_column_double(stmt, 5); lh.columns = sqlite3_column_int(stmt, 6);
    lh.pages = sqlite3_column_int(stmt, 7); lh.labels = sqlite3_column_int(stmt, 8);
    lh.total_copies = sqlite3_column_int(stmt, 9); lh.summary = get(10);
    lh.lines_json = get(11); lh.user_id = get(12); lh.created_at = get(13);
    list.push_back(std::move(lh));
  }
  sqlite3_finalize(stmt);
  return list;
}

} // namespace fin::db
