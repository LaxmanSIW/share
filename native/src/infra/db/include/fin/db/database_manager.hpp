// fin/db/database_manager.hpp — SQLite connection manager + schema (port of Java DatabaseManager)
//
// Skill rules §8 (database):
//   - PRAGMA journal_mode = WAL; readers never block the writer
//   - PRAGMA synchronous = FULL (durable commits for postings)
//   - PRAGMA foreign_keys = ON
//   - PRAGMA busy_timeout = 5000
//   - PRAGMA temp_store = MEMORY; cache_size = 64 MB
//   - One writer connection, N reader connections (one per thread)
//   - Schema migrations are append-only, idempotent, run in a transaction
//     after an automatic backup (VACUUM INTO)
//   - PreparedStatement always — no string-concatenated SQL
//
// Thread model:
//   - The writer connection is owned by DatabaseManager and guarded by a mutex.
//   - Reader connections are created per-thread via open_reader() and
//     must be used only in the thread that created them (SQLite + QSqlDatabase
//     thread-affinity rule).
#pragma once
#include <filesystem>
#include <memory>
#include <mutex>
#include <string>
#include <string_view>

struct sqlite3;
struct sqlite3_stmt;

namespace fin::db {

namespace fs = std::filesystem;

class DatabaseManager {
 public:
  /// Returns the process-wide instance. Lazily initialised with the path
  /// returned by fin::app::data_dir() / "invoicestudio.db".
  static DatabaseManager& instance();

  /// Initialise a custom instance (for tests).
  static DatabaseManager& init_custom(fs::path db_file);

  /// Open the database and run migrations. Throws on failure.
  void open();
  /// Close all connections. Safe to call multiple times.
  void close();

  /// Returns the SQLite connection for use on the CURRENT thread.
  /// Each thread gets its own reader connection (named "fin-reader-<tid>").
  /// Use this for read-only queries.
  /// IMPORTANT: never share the returned raw pointer across threads.
  sqlite3* reader_handle();

  /// Execute a write under the writer mutex. The closure receives the writer
  /// sqlite3* and is responsible for BEGIN/COMMIT.
  /// Used by the writer-actor pattern (skill responsive-ui §4.5).
  template <class F>
  auto write(F&& f) -> decltype(f(std::declval<sqlite3*>())) {
    std::lock_guard lock(writer_mtx_);
    return f(writer_handle_);
  }

  /// Convenience: run a one-shot write statement (no parameters). Returns
  /// number of rows affected, or -1 on error.
  int exec_write(std::string_view sql);

  /// Path to the on-disk database file.
  fs::path file_path() const noexcept { return db_file_; }

  /// Schema version (PRAGMA user_version). 0 before migrations, latest after.
  int schema_version() const noexcept;

 private:
  explicit DatabaseManager(fs::path db_file);

  void run_pragmas_(sqlite3* db);
  void run_migrations_(sqlite3* db);
  void seed_variables_(sqlite3* db);

  fs::path db_file_;
  sqlite3* writer_handle_ = nullptr;
  std::mutex writer_mtx_;

  // Reader pool keyed by thread id.
  struct ReaderEntry { std::uint64_t tid; sqlite3* handle; };
  std::mutex readers_mtx_;
  std::vector<ReaderEntry> readers_;
};

/// Helper: prepare a statement, bind parameters, step, finalize.
/// Use the macro DEF_PREPARE for terseness inside DAOs.

/// Bind helpers — wrap sqlite3_bind_* with type-safe overloads.
void bind_param(sqlite3_stmt* stmt, int idx, std::int64_t v);
void bind_param(sqlite3_stmt* stmt, int idx, double v);
void bind_param(sqlite3_stmt* stmt, int idx, std::string_view v);
void bind_param(sqlite3_stmt* stmt, int idx, const char* v);
void bind_param(sqlite3_stmt* stmt, int idx, bool v);
void bind_param_null(sqlite3_stmt* stmt, int idx);

/// Read column helpers.
std::int64_t column_int64(sqlite3_stmt* stmt, int idx);
double      column_double(sqlite3_stmt* stmt, int idx);
std::string column_string(sqlite3_stmt* stmt, int idx);
bool        column_bool(sqlite3_stmt* stmt, int idx);

} // namespace fin::db
