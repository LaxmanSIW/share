// fin/db/database_manager.cpp
#include "fin/db/database_manager.hpp"
#include "fin/app/app_dirs.hpp"
#include "fin/app/log.hpp"

#include <sqlite3.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

namespace fin::db {

namespace fs = std::filesystem;
using fin::app::log::Level;

namespace {

DatabaseManager* g_instance = nullptr;
std::once_flag g_instance_once;

std::uint64_t this_thread_id() {
  return static_cast<std::uint64_t>(std::hash<std::thread::id>{}(std::this_thread::get_id()));
}

void exec_nothrow(sqlite3* db, const char* sql, const char* context) {
  char* err = nullptr;
  int rc = sqlite3_exec(db, sql, nullptr, nullptr, &err);
  if (rc != SQLITE_OK) {
    if (err) {
      fin::app::log::debugf("sqlite ({}): {}", context, err);
      sqlite3_free(err);
    }
  }
}

void exec_or_throw(sqlite3* db, const char* sql, const char* context) {
  char* err = nullptr;
  int rc = sqlite3_exec(db, sql, nullptr, nullptr, &err);
  if (rc != SQLITE_OK) {
    std::string msg = err ? err : "(no error)";
    if (err) sqlite3_free(err);
    throw std::runtime_error(std::string("sqlite (") + context + "): " + msg);
  }
}

void exec_idempotent(sqlite3* db, const char* sql, const char* context) {
  // Run a migration SQL that may already have been applied (e.g. ALTER TABLE
  // ADD COLUMN on a fresh schema). Failure is logged at debug level only.
  char* err = nullptr;
  int rc = sqlite3_exec(db, sql, nullptr, nullptr, &err);
  if (rc != SQLITE_OK) {
    if (err) {
      // duplicate-column errors are expected here.
      fin::app::log::debugf("migration idempotent ({}): {}", context, err);
      sqlite3_free(err);
    }
  } else {
    fin::app::log::debugf("migration applied ({}): ok", context);
  }
}

} // namespace

DatabaseManager::DatabaseManager(fs::path db_file) : db_file_(std::move(db_file)) {}

DatabaseManager& DatabaseManager::instance() {
  std::call_once(g_instance_once, [] {
    fs::path p = fs::path{app::database_path_string()};
    g_instance = new DatabaseManager(p);
    try {
      g_instance->open();
    } catch (const std::exception& e) {
      fin::app::log::errorf("DatabaseManager::instance open failed: {}", e.what());
    }
  });
  return *g_instance;
}

DatabaseManager& DatabaseManager::init_custom(fs::path db_file) {
  // Reset singleton to a new instance (for tests).
  if (g_instance) {
    g_instance->close();
    delete g_instance;
    g_instance = nullptr;
  }
  g_instance = new DatabaseManager(std::move(db_file));
  g_instance->open();
  return *g_instance;
}

void DatabaseManager::open() {
  if (writer_handle_) return;
  fs::create_directories(db_file_.parent_path());

  int flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE;
  int rc = sqlite3_open_v2(db_file_.string().c_str(), &writer_handle_, flags, nullptr);
  if (rc != SQLITE_OK) {
    std::string err = sqlite3_errmsg(writer_handle_);
    sqlite3_close(writer_handle_);
    writer_handle_ = nullptr;
    throw std::runtime_error("Failed to open database: " + err);
  }
  run_pragmas_(writer_handle_);
  run_migrations_(writer_handle_);
  seed_variables_(writer_handle_);
}

void DatabaseManager::close() {
  {
    std::lock_guard lock(readers_mtx_);
    for (auto& e : readers_) {
      if (e.handle) sqlite3_close(e.handle);
    }
    readers_.clear();
  }
  std::lock_guard lock(writer_mtx_);
  if (writer_handle_) {
    exec_nothrow(writer_handle_, "PRAGMA optimize;", "optimize on close");
    sqlite3_close(writer_handle_);
    writer_handle_ = nullptr;
  }
}

sqlite3* DatabaseManager::reader_handle() {
  auto tid = this_thread_id();
  {
    std::lock_guard lock(readers_mtx_);
    for (auto& e : readers_) {
      if (e.tid == tid) return e.handle;
    }
    // Open a new reader connection for this thread.
    sqlite3* h = nullptr;
    int flags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE;
    int rc = sqlite3_open_v2(db_file_.string().c_str(), &h, flags, nullptr);
    if (rc != SQLITE_OK) {
      if (h) sqlite3_close(h);
      throw std::runtime_error("Failed to open reader connection");
    }
    run_pragmas_(h);
    readers_.push_back({tid, h});
    return h;
  }
}

void DatabaseManager::run_pragmas_(sqlite3* db) {
  // Skill §8: WAL + synchronous FULL + foreign_keys ON + busy_timeout.
  exec_or_throw(db, "PRAGMA journal_mode = WAL;",        "journal_mode");
  exec_or_throw(db, "PRAGMA synchronous = FULL;",        "synchronous");
  exec_or_throw(db, "PRAGMA foreign_keys = ON;",         "foreign_keys");
  exec_or_throw(db, "PRAGMA busy_timeout = 5000;",        "busy_timeout");
  exec_or_throw(db, "PRAGMA temp_store = MEMORY;",        "temp_store");
  exec_or_throw(db, "PRAGMA cache_size = -65536;",       "cache_size 64MB");
}

int DatabaseManager::schema_version() const noexcept {
  if (!writer_handle_) return 0;
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(writer_handle_, "PRAGMA user_version", -1, &stmt, nullptr) != SQLITE_OK) return 0;
  int v = 0;
  if (sqlite3_step(stmt) == SQLITE_ROW) v = static_cast<int>(sqlite3_column_int64(stmt, 0));
  sqlite3_finalize(stmt);
  return v;
}

int DatabaseManager::exec_write(std::string_view sql) {
  std::lock_guard lock(writer_mtx_);
  char* err = nullptr;
  std::string s(sql);
  int rc = sqlite3_exec(writer_handle_, s.c_str(), nullptr, nullptr, &err);
  if (rc != SQLITE_OK) {
    std::string msg = err ? err : "(no error)";
    if (err) sqlite3_free(err);
    fin::app::log::errorf("exec_write: {}", msg);
    return -1;
  }
  return static_cast<int>(sqlite3_changes(writer_handle_));
}

void DatabaseManager::run_migrations_(sqlite3* db) {
  // Schema bootstrap (matches Java original `initSchema`). Idempotent — uses
  // CREATE TABLE IF NOT EXISTS / ALTER TABLE inside try-catch wrappers.
  const char* bootstrap[] = {
    "CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY, json_data TEXT)",
    "CREATE TABLE IF NOT EXISTS templates (id TEXT PRIMARY KEY, name TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)",
    "CREATE TABLE IF NOT EXISTS bills (id TEXT PRIMARY KEY, bill_no TEXT, date TEXT, doc_type TEXT, status TEXT, buyer_name TEXT, grand_total REAL, due_amount REAL, json_data TEXT, created_at TEXT, updated_at TEXT)",
    "CREATE INDEX IF NOT EXISTS idx_bills_bill_no ON bills(bill_no)",
    "CREATE TABLE IF NOT EXISTS buyers (id TEXT PRIMARY KEY, name TEXT, phone TEXT, gst TEXT, state TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)",
    "CREATE TABLE IF NOT EXISTS suppliers (id TEXT PRIMARY KEY, name TEXT, phone TEXT, gst TEXT, state TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)",
    "CREATE TABLE IF NOT EXISTS purchase_bills (id TEXT PRIMARY KEY, bill_no TEXT, supplier_bill_no TEXT, date TEXT, supplier_id TEXT, supplier_name TEXT, total REAL, itc REAL, status TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)",
    "CREATE INDEX IF NOT EXISTS idx_purchases_supplier ON purchase_bills(supplier_id)",
    "CREATE INDEX IF NOT EXISTS idx_purchases_date ON purchase_bills(date)",
    "CREATE TABLE IF NOT EXISTS stock_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id TEXT NOT NULL, transaction_date TEXT NOT NULL, voucher_type TEXT NOT NULL, voucher_id TEXT NOT NULL, voucher_no TEXT, qty_in REAL DEFAULT 0, qty_out REAL DEFAULT 0, unit_price REAL, user_id TEXT DEFAULT '', created_at TEXT)",
    "CREATE INDEX IF NOT EXISTS idx_stock_item ON stock_ledger(item_id)",
    "CREATE INDEX IF NOT EXISTS idx_stock_voucher ON stock_ledger(voucher_id)",
    "CREATE TABLE IF NOT EXISTS expenses (id TEXT PRIMARY KEY, date TEXT, category TEXT, amount REAL, payment_mode TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)",
    "CREATE TABLE IF NOT EXISTS expense_accounts (id TEXT PRIMARY KEY, name TEXT, archived INTEGER DEFAULT 0, json_data TEXT, created_at TEXT, updated_at TEXT)",
    "CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY, name TEXT, hsn TEXT, unit TEXT, rate REAL, gst REAL, created_at TEXT, updated_at TEXT)",
    "CREATE TABLE IF NOT EXISTS variables (key TEXT PRIMARY KEY, label TEXT, type TEXT, builtin INTEGER)",
    "CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, val TEXT)",
    "CREATE TABLE IF NOT EXISTS auth_session (id INTEGER PRIMARY KEY, user_id TEXT, email TEXT, display_name TEXT, id_token TEXT, refresh_token TEXT, expires_at INTEGER, remember_me INTEGER, created_at TEXT)",
    "CREATE TABLE IF NOT EXISTS transports (id TEXT PRIMARY KEY, name TEXT, phone TEXT, vehicle_number TEXT, created_at TEXT, updated_at TEXT)",
    "CREATE TABLE IF NOT EXISTS categories (id TEXT PRIMARY KEY, name TEXT, created_at TEXT, updated_at TEXT)",
    "CREATE TABLE IF NOT EXISTS transactions (id TEXT PRIMARY KEY, buyer_id TEXT, buyer_name TEXT, book_type TEXT, transaction_type TEXT, transaction_date TEXT, due_date TEXT, amount REAL, total_quantity INTEGER, check_number TEXT, include_in_reporting INTEGER, parcel INTEGER, bill_id TEXT, bill_no TEXT, deleted INTEGER, deleted_reason TEXT, deleted_at TEXT, created_at TEXT, updated_at TEXT)",
    "CREATE INDEX IF NOT EXISTS idx_tx_buyer ON transactions(buyer_id)",
    "CREATE INDEX IF NOT EXISTS idx_tx_date ON transactions(transaction_date)",
    "CREATE INDEX IF NOT EXISTS idx_tx_book ON transactions(book_type)",
    "CREATE TABLE IF NOT EXISTS label_print_history (id TEXT PRIMARY KEY, template_id TEXT, template_name TEXT, printer_name TEXT, label_width REAL, label_height REAL, columns INTEGER, pages INTEGER, labels INTEGER, total_copies INTEGER, summary TEXT, lines_json TEXT, user_id TEXT DEFAULT '', created_at TEXT)",
    "CREATE INDEX IF NOT EXISTS idx_lph_user ON label_print_history(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_lph_created ON label_print_history(created_at)",
  };
  for (const char* sql : bootstrap) exec_or_throw(db, sql, "bootstrap");

  // === Idempotent ALTER TABLE migrations (matches Java original) ===
  // Skill rule: ALTER TABLE ADD COLUMN inside try-catch (duplicate-column is
  // expected on a fresh schema); better to check PRAGMA table_info first.
  const char* alters[] = {
    "ALTER TABLE items ADD COLUMN category_id TEXT",
    "ALTER TABLE items ADD COLUMN category_name TEXT",
    "ALTER TABLE variables ADD COLUMN scope TEXT DEFAULT 'fixed'",
    "ALTER TABLE variables ADD COLUMN default_value TEXT DEFAULT ''",
    "ALTER TABLE variables ADD COLUMN choices TEXT DEFAULT ''",
    "ALTER TABLE bills ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE buyers ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE items ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE templates ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE categories ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE transports ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE transactions ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE settings ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE variables ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE meta ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE suppliers ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE expenses ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE purchase_bills ADD COLUMN user_id TEXT DEFAULT ''",
    "ALTER TABLE items ADD COLUMN purchase_rate REAL DEFAULT 0",
    "ALTER TABLE items ADD COLUMN current_stock REAL DEFAULT 0",
    "ALTER TABLE items ADD COLUMN opening_stock REAL DEFAULT 0",
    "ALTER TABLE items ADD COLUMN reorder_level REAL DEFAULT 0",
    "ALTER TABLE expense_accounts ADD COLUMN user_id TEXT DEFAULT ''",
  };
  for (const char* sql : alters) exec_idempotent(db, sql, "alter");

  const char* indexes[] = {
    "CREATE INDEX IF NOT EXISTS idx_bills_user ON bills(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_buyers_user ON buyers(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_suppliers_user ON suppliers(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date)",
    "CREATE INDEX IF NOT EXISTS idx_expenses_user ON expenses(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_expense_accounts_user ON expense_accounts(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_items_user ON items(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_templates_user ON templates(user_id)",
    "CREATE INDEX IF NOT EXISTS idx_tx_user ON transactions(user_id)",
  };
  for (const char* sql : indexes) exec_idempotent(db, sql, "index");

  // Purge unauthenticated legacy orphan records (matches Java original).
  const char* purges[] = {
    "DELETE FROM categories WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM items WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM templates WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM settings WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM variables WHERE builtin = 0 AND (user_id = '' OR user_id IS NULL)",
    "DELETE FROM transports WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM buyers WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM suppliers WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM purchase_bills WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM stock_ledger WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM expenses WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM bills WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM transactions WHERE user_id = '' OR user_id IS NULL",
    "DELETE FROM label_print_history WHERE user_id = '' OR user_id IS NULL",
  };
  for (const char* sql : purges) exec_idempotent(db, sql, "purge");

  // Categories unique-by-name (within user) — MCP race backstop.
  exec_idempotent(db,
    "DELETE FROM categories WHERE rowid NOT IN (SELECT MIN(rowid) FROM categories GROUP BY user_id, LOWER(name))",
    "merge dup categories");
  exec_idempotent(db,
    "CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_user_name ON categories(user_id, name COLLATE NOCASE)",
    "unique categories");

  // Set schema version (we use a single integer; future migrations bump this).
  exec_idempotent(db, "PRAGMA user_version = 1", "user_version");
}

void DatabaseManager::seed_variables_(sqlite3* db) {
  // Built-in system variables. Matches Java original exactly.
  struct V { const char* key; const char* label; const char* type; bool builtin; };
  static const V builtins[] = {
    {"buyer_name",            "Buyer Name",                  "text",   true},
    {"buyer_address",         "Buyer Address",               "text",   true},
    {"buyer_gst",             "Buyer GSTIN",                 "text",   true},
    {"buyer_phone",           "Buyer Phone",                  "text",   true},
    {"buyer_state",           "Buyer State (Place of Supply)","text",  true},
    {"buyer_state_code",      "Buyer State Code",             "text",   true},
    {"buyer_city",            "Buyer City",                   "text",   true},
    {"buyer_contact_person",  "Buyer Contact Person",         "text",   true},
    {"po_no",                 "PO / Order No",                "text",   true},
    {"parcel",                "Parcels / Bales Count",         "number", true},
    {"parcels",               "Total Parcels",                "number", true},
    {"transport_name",        "Transport Name",               "text",   true},
    {"transport_phone",       "Transport Phone",              "text",   true},
    {"transport_contact",     "Transport Contact Person",     "text",   true},
    {"vehicle_no",            "Vehicle No",                   "text",   true},
    {"e_way_bill",            "E-Way Bill No",                "text",   true},
  };

  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(db,
      "INSERT OR IGNORE INTO variables (key, label, type, builtin) VALUES (?, ?, ?, ?)",
      -1, &stmt, nullptr) != SQLITE_OK) {
    fin::app::log::errorf("seed_variables: prepare failed: {}", sqlite3_errmsg(db));
    return;
  }
  for (const auto& v : builtins) {
    sqlite3_bind_text(stmt, 1, v.key,   -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, v.label, -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, v.type,  -1, SQLITE_TRANSIENT);
    sqlite3_bind_int (stmt, 4, v.builtin ? 1 : 0);
    sqlite3_step(stmt);
    sqlite3_reset(stmt);
  }
  sqlite3_finalize(stmt);
}

// === Bind helpers ===

void bind_param(sqlite3_stmt* stmt, int idx, std::int64_t v) { sqlite3_bind_int64(stmt, idx, v); }
void bind_param(sqlite3_stmt* stmt, int idx, double v)        { sqlite3_bind_double(stmt, idx, v); }
void bind_param(sqlite3_stmt* stmt, int idx, std::string_view v) {
  sqlite3_bind_text(stmt, idx, v.data(), static_cast<int>(v.size()), SQLITE_TRANSIENT);
}
void bind_param(sqlite3_stmt* stmt, int idx, const char* v) {
  sqlite3_bind_text(stmt, idx, v, -1, SQLITE_TRANSIENT);
}
void bind_param(sqlite3_stmt* stmt, int idx, bool v) {
  sqlite3_bind_int(stmt, idx, v ? 1 : 0);
}
void bind_param_null(sqlite3_stmt* stmt, int idx) {
  sqlite3_bind_null(stmt, idx);
}

// === Column readers ===

std::int64_t column_int64(sqlite3_stmt* stmt, int idx) {
  return sqlite3_column_int64(stmt, idx);
}
double column_double(sqlite3_stmt* stmt, int idx) {
  return sqlite3_column_double(stmt, idx);
}
std::string column_string(sqlite3_stmt* stmt, int idx) {
  auto* p = sqlite3_column_text(stmt, idx);
  if (!p) return {};
  auto  n = sqlite3_column_bytes(stmt, idx);
  return std::string(reinterpret_cast<const char*>(p), static_cast<std::size_t>(n));
}
bool column_bool(sqlite3_stmt* stmt, int idx) {
  return sqlite3_column_int(stmt, idx) != 0;
}

} // namespace fin::db
