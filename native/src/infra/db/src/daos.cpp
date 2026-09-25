// fin/db/daos.cpp — Implementations for BuyerDao, SupplierDao, ItemDao,
// VariableDao, TemplateDao, SettingsDao, AuthDao.
//
// All implementations follow the same pattern as BillDao: parameterised SQL,
// json_data column for the full model + indexed columns for queries, scoped
// by user_id. Skill rule §8: no string-concatenated SQL anywhere.
#include "fin/db/daos.hpp"
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

// === BuyerDao ===

std::vector<fin::model::Buyer> BuyerDao::find_all() {
  std::vector<fin::model::Buyer> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT json_data FROM buyers WHERE user_id = ? ORDER BY name", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    auto* p = sqlite3_column_text(stmt, 0);
    int n = sqlite3_column_bytes(stmt, 0);
    if (p) {
      std::string s(reinterpret_cast<const char*>(p), static_cast<std::size_t>(n));
      try { list.push_back(nlohmann::json::parse(s).get<fin::model::Buyer>()); } catch (...) {}
    }
  }
  sqlite3_finalize(stmt);
  return list;
}

std::optional<fin::model::Buyer> BuyerDao::find_by_id(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return std::nullopt;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT json_data FROM buyers WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return std::nullopt;
  sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
  std::optional<fin::model::Buyer> out;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    auto* p = sqlite3_column_text(stmt, 0);
    int n = sqlite3_column_bytes(stmt, 0);
    if (p) {
      std::string s(reinterpret_cast<const char*>(p), static_cast<std::size_t>(n));
      try { out = nlohmann::json::parse(s).get<fin::model::Buyer>(); } catch (...) {}
    }
  }
  sqlite3_finalize(stmt);
  return out;
}

bool BuyerDao::save(const fin::model::Buyer& b_const) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;
  auto b = b_const;
  std::string now = iso_now();
  if (b.created_at.empty()) b.created_at = now;
  b.updated_at = now;
  std::string json_str = nlohmann::json(b).dump();
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO buyers (id, user_id, name, phone, gst, state, json_data, created_at, updated_at) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, "
                      "phone = excluded.phone, gst = excluded.gst, state = excluded.state, "
                      "json_data = excluded.json_data, updated_at = excluded.updated_at WHERE buyers.user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, b.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, b.name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, b.phone.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, b.gst.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 6, b.state.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 7, json_str.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 8, b.created_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 9, b.updated_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool BuyerDao::erase(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM buyers WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// === SupplierDao (same shape as BuyerDao) ===

std::vector<fin::model::Supplier> SupplierDao::find_all() {
  std::vector<fin::model::Supplier> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT json_data FROM suppliers WHERE user_id = ? ORDER BY name", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    auto* p = sqlite3_column_text(stmt, 0);
    int n = sqlite3_column_bytes(stmt, 0);
    if (p) {
      std::string s(reinterpret_cast<const char*>(p), static_cast<std::size_t>(n));
      try { list.push_back(nlohmann::json::parse(s).get<fin::model::Supplier>()); } catch (...) {}
    }
  }
  sqlite3_finalize(stmt);
  return list;
}

std::optional<fin::model::Supplier> SupplierDao::find_by_id(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return std::nullopt;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT json_data FROM suppliers WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return std::nullopt;
  sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
  std::optional<fin::model::Supplier> out;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    auto* p = sqlite3_column_text(stmt, 0);
    int n = sqlite3_column_bytes(stmt, 0);
    if (p) {
      std::string s(reinterpret_cast<const char*>(p), static_cast<std::size_t>(n));
      try { out = nlohmann::json::parse(s).get<fin::model::Supplier>(); } catch (...) {}
    }
  }
  sqlite3_finalize(stmt);
  return out;
}

bool SupplierDao::save(const fin::model::Supplier& s_const) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;
  auto s = s_const;
  std::string now = iso_now();
  if (s.created_at.empty()) s.created_at = now;
  s.updated_at = now;
  std::string json_str = nlohmann::json(s).dump();
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO suppliers (id, user_id, name, phone, gst, state, json_data, created_at, updated_at) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, "
                      "phone = excluded.phone, gst = excluded.gst, state = excluded.state, "
                      "json_data = excluded.json_data, updated_at = excluded.updated_at WHERE suppliers.user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, s.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, s.name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, s.phone.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, s.gst.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 6, s.state.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 7, json_str.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 8, s.created_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 9, s.updated_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool SupplierDao::erase(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM suppliers WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// === ItemDao ===

std::vector<fin::model::ItemRecord> ItemDao::find_all() {
  std::vector<fin::model::ItemRecord> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, name, hsn, unit, rate, gst, purchase_rate, current_stock, opening_stock, reorder_level, category_id, category_name, created_at, updated_at FROM items WHERE user_id = ? ORDER BY name", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::ItemRecord it;
    it.id = (const char*)sqlite3_column_text(stmt, 0);
    it.name = (const char*)sqlite3_column_text(stmt, 1);
    it.hsn = (const char*)sqlite3_column_text(stmt, 2);
    it.unit = (const char*)sqlite3_column_text(stmt, 3);
    // Back-compat: rate is stored as REAL (double); convert to Money in INR.
    double rate = sqlite3_column_double(stmt, 4);
    double gst_pct = sqlite3_column_double(stmt, 5);
    it.rate = fin::Money::parse_round(std::to_string(rate), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    it.gst_rate = fin::Rate{static_cast<std::int64_t>(gst_pct * 10.0 + 0.5), 1000};
    double pr = sqlite3_column_double(stmt, 6);
    double cs = sqlite3_column_double(stmt, 7);
    double os = sqlite3_column_double(stmt, 8);
    double rl = sqlite3_column_double(stmt, 9);
    it.purchase_rate = fin::Money::parse_round(std::to_string(pr), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    it.current_stock = fin::Money::parse_round(std::to_string(cs), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    it.opening_stock = fin::Money::parse_round(std::to_string(os), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    it.reorder_level = fin::Money::parse_round(std::to_string(rl), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
    const char* cat_id = (const char*)sqlite3_column_text(stmt, 10);
    const char* cat_nm  = (const char*)sqlite3_column_text(stmt, 11);
    if (cat_id) it.category_id = cat_id;
    if (cat_nm)  it.category_name = cat_nm;
    const char* ca = (const char*)sqlite3_column_text(stmt, 12);
    const char* ua = (const char*)sqlite3_column_text(stmt, 13);
    if (ca) it.created_at = ca;
    if (ua) it.updated_at = ua;
    list.push_back(std::move(it));
  }
  sqlite3_finalize(stmt);
  return list;
}

std::optional<fin::model::ItemRecord> ItemDao::find_by_id(std::string_view id) {
  auto all = find_all();
  for (auto& it : all) if (it.id == id) return std::move(it);
  return std::nullopt;
}

bool ItemDao::save(const fin::model::ItemRecord& it_const) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;
  auto it = it_const;
  std::string now = iso_now();
  if (it.created_at.empty()) it.created_at = now;
  it.updated_at = now;
  // Back-compat: store money as double (the Java original's column type is REAL).
  double rate_d = static_cast<double>(it.rate.minor()) / 100.0;
  double gst_d  = static_cast<double>(it.gst_rate.num) / static_cast<double>(it.gst_rate.denom) * 100.0;
  double pr_d   = static_cast<double>(it.purchase_rate.minor()) / 100.0;
  double cs_d   = static_cast<double>(it.current_stock.minor()) / 100.0;
  double os_d   = static_cast<double>(it.opening_stock.minor()) / 100.0;
  double rl_d   = static_cast<double>(it.reorder_level.minor()) / 100.0;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO items (id, user_id, name, hsn, unit, rate, gst, purchase_rate, current_stock, opening_stock, reorder_level, category_id, category_name, created_at, updated_at) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, hsn = excluded.hsn, unit = excluded.unit, "
                      "rate = excluded.rate, gst = excluded.gst, purchase_rate = excluded.purchase_rate, current_stock = excluded.current_stock, "
                      "opening_stock = excluded.opening_stock, reorder_level = excluded.reorder_level, category_id = excluded.category_id, "
                      "category_name = excluded.category_name, updated_at = excluded.updated_at WHERE items.user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, it.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, it.name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, it.hsn.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, it.unit.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_double(stmt, 6, rate_d);
    sqlite3_bind_double(stmt, 7, gst_d);
    sqlite3_bind_double(stmt, 8, pr_d);
    sqlite3_bind_double(stmt, 9, cs_d);
    sqlite3_bind_double(stmt, 10, os_d);
    sqlite3_bind_double(stmt, 11, rl_d);
    sqlite3_bind_text(stmt, 12, it.category_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 13, it.category_name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 14, it.created_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 15, it.updated_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool ItemDao::erase(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM items WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// === VariableDao ===

std::vector<fin::model::VariableDef> VariableDao::find_all() {
  std::vector<fin::model::VariableDef> list;
  std::string uid = effective_user_id();
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  // Built-ins have empty user_id (system); user-created variables have a user_id.
  const char* sql = "SELECT key, label, type, builtin, scope, default_value, choices FROM variables WHERE builtin = 1 OR user_id = ?";
  if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::VariableDef v;
    v.key = (const char*)sqlite3_column_text(stmt, 0);
    v.label = (const char*)sqlite3_column_text(stmt, 1);
    v.type = (const char*)sqlite3_column_text(stmt, 2);
    v.builtin = sqlite3_column_int(stmt, 3) != 0;
    const char* sc = (const char*)sqlite3_column_text(stmt, 4);
    if (sc) v.scope = sc;
    const char* dv = (const char*)sqlite3_column_text(stmt, 5);
    if (dv) v.default_value = dv;
    const char* ch = (const char*)sqlite3_column_text(stmt, 6);
    if (ch) v.choices = ch;
    list.push_back(std::move(v));
  }
  sqlite3_finalize(stmt);
  return list;
}

bool VariableDao::save(const fin::model::VariableDef& v) {
  std::string uid = effective_user_id();
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO variables (key, label, type, builtin, scope, default_value, choices, user_id) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                      "ON CONFLICT(key) DO UPDATE SET label = excluded.label, type = excluded.type, "
                      "builtin = excluded.builtin, scope = excluded.scope, default_value = excluded.default_value, "
                      "choices = excluded.choices, user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, v.key.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, v.label.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, v.type.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int (stmt, 4, v.builtin ? 1 : 0);
    sqlite3_bind_text(stmt, 5, v.scope.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 6, v.default_value.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 7, v.choices.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 8, v.builtin ? "" : uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool VariableDao::erase(std::string_view key) {
  std::string uid = effective_user_id();
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM variables WHERE key = ? AND user_id = ? AND builtin = 0", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, key.data(), static_cast<int>(key.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// === TemplateDao ===

std::vector<fin::model::Template> TemplateDao::find_all() {
  std::vector<fin::model::Template> list;
  std::string uid = effective_user_id();
  if (uid.empty()) return list;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, name, json_data, created_at, updated_at FROM templates WHERE user_id = ? ORDER BY name", -1, &stmt, nullptr) != SQLITE_OK) return list;
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  while (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::Template t;
    t.id = (const char*)sqlite3_column_text(stmt, 0);
    t.name = (const char*)sqlite3_column_text(stmt, 1);
    auto* p = sqlite3_column_text(stmt, 2);
    int n = sqlite3_column_bytes(stmt, 2);
    if (p) t.json_data = std::string(reinterpret_cast<const char*>(p), static_cast<std::size_t>(n));
    const char* ca = (const char*)sqlite3_column_text(stmt, 3);
    const char* ua = (const char*)sqlite3_column_text(stmt, 4);
    if (ca) t.created_at = ca;
    if (ua) t.updated_at = ua;
    t.user_id = uid;
    list.push_back(std::move(t));
  }
  sqlite3_finalize(stmt);
  return list;
}

std::optional<fin::model::Template> TemplateDao::find_by_id(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return std::nullopt;
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, name, json_data, created_at, updated_at FROM templates WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return std::nullopt;
  sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
  std::optional<fin::model::Template> out;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::Template t;
    t.id = (const char*)sqlite3_column_text(stmt, 0);
    t.name = (const char*)sqlite3_column_text(stmt, 1);
    auto* p = sqlite3_column_text(stmt, 2);
    int n = sqlite3_column_bytes(stmt, 2);
    if (p) t.json_data = std::string(reinterpret_cast<const char*>(p), static_cast<std::size_t>(n));
    const char* ca = (const char*)sqlite3_column_text(stmt, 3);
    const char* ua = (const char*)sqlite3_column_text(stmt, 4);
    if (ca) t.created_at = ca;
    if (ua) t.updated_at = ua;
    t.user_id = uid;
    out = std::move(t);
  }
  sqlite3_finalize(stmt);
  return out;
}

bool TemplateDao::save(const fin::model::Template& t_const) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;
  auto t = t_const;
  std::string now = iso_now();
  if (t.created_at.empty()) t.created_at = now;
  t.updated_at = now;
  t.user_id = uid;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO templates (id, user_id, name, json_data, created_at, updated_at) "
                      "VALUES (?, ?, ?, ?, ?, ?) "
                      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, "
                      "json_data = excluded.json_data, updated_at = excluded.updated_at WHERE templates.user_id = excluded.user_id";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, t.id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, t.user_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, t.name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, t.json_data.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, t.created_at.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 6, t.updated_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

bool TemplateDao::erase(std::string_view id) {
  std::string uid = effective_user_id();
  if (uid.empty() || id.empty()) return false;
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "DELETE FROM templates WHERE id = ? AND user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, id.data(), static_cast<int>(id.size()), SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// === SettingsDao ===

std::string SettingsDao::load_json() {
  std::string uid = effective_user_id();
  if (uid.empty()) return {};
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT json_data FROM settings WHERE user_id = ?", -1, &stmt, nullptr) != SQLITE_OK) return {};
  sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
  std::string out;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    auto* p = sqlite3_column_text(stmt, 0);
    int n = sqlite3_column_bytes(stmt, 0);
    if (p) out = std::string(reinterpret_cast<const char*>(p), static_cast<std::size_t>(n));
  }
  sqlite3_finalize(stmt);
  return out;
}

bool SettingsDao::save_json(std::string_view json_data) {
  std::string uid = effective_user_id();
  if (uid.empty()) return false;
  std::string now = iso_now();
  return db_.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO settings (id, user_id, json_data) VALUES ((COALESCE((SELECT id FROM settings WHERE user_id = ?), 0)), ?, ?) "
                      "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, json_data = excluded.json_data";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, uid.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, uid.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, json_data.data(), static_cast<int>(json_data.size()), SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

// === AuthDao ===

bool AuthDao::save_session(const fin::model::UserSession& s_const) {
  auto s = s_const;
  if (s.created_at.empty()) s.created_at = iso_now();
  return db_.write([&](sqlite3* h) -> int {
    // Delete any previous session, then insert.
    sqlite3_exec(h, "DELETE FROM auth_session", nullptr, nullptr, nullptr);
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "INSERT INTO auth_session (user_id, email, display_name, id_token, refresh_token, expires_at, remember_me, created_at) "
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    if (sqlite3_prepare_v2(h, sql, -1, &stmt, nullptr) != SQLITE_OK) return -1;
    sqlite3_bind_text(stmt, 1, s.user_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, s.email.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, s.display_name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, s.id_token.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 5, s.refresh_token.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int64(stmt, 6, s.expires_at);
    sqlite3_bind_int(stmt, 7, s.remember_me ? 1 : 0);
    sqlite3_bind_text(stmt, 8, s.created_at.c_str(), -1, SQLITE_TRANSIENT);
    int rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc == SQLITE_DONE ? 0 : -1;
  }) == 0;
}

std::optional<fin::model::UserSession> AuthDao::load_session() {
  sqlite3* h = db_.reader_handle();
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(h, "SELECT id, user_id, email, display_name, id_token, refresh_token, expires_at, remember_me, created_at FROM auth_session ORDER BY id DESC LIMIT 1", -1, &stmt, nullptr) != SQLITE_OK) return std::nullopt;
  std::optional<fin::model::UserSession> out;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    fin::model::UserSession s;
    s.id = sqlite3_column_int(stmt, 0);
    const char* c1 = (const char*)sqlite3_column_text(stmt, 1);
    const char* c2 = (const char*)sqlite3_column_text(stmt, 2);
    const char* c3 = (const char*)sqlite3_column_text(stmt, 3);
    const char* c4 = (const char*)sqlite3_column_text(stmt, 4);
    const char* c5 = (const char*)sqlite3_column_text(stmt, 5);
    s.expires_at = sqlite3_column_int64(stmt, 6);
    s.remember_me = sqlite3_column_int(stmt, 7) != 0;
    const char* c8 = (const char*)sqlite3_column_text(stmt, 8);
    if (c1) s.user_id = c1;
    if (c2) s.email = c2;
    if (c3) s.display_name = c3;
    if (c4) s.id_token = c4;
    if (c5) s.refresh_token = c5;
    if (c8) s.created_at = c8;
    out = std::move(s);
  }
  sqlite3_finalize(stmt);
  return out;
}

bool AuthDao::clear() {
  return db_.exec_write("DELETE FROM auth_session") >= 0;
}

} // namespace fin::db
