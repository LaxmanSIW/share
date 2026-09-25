// fin/db/dao.hpp — Base helper for DAOs that share the same shape (get-by-id,
// get-all, upsert, delete) operating on a single json_data column.
//
// Skill rule §8: PreparedStatement always. All DAOs funnel through this base,
// which means SQL injection is impossible by construction.
#pragma once
#include "fin/db/database_manager.hpp"
#include "fin/services/auth_session.hpp"

#include <sqlite3.h>

#include <memory>
#include <string>
#include <string_view>
#include <vector>

namespace fin::db {

/// Helper: open a prepared statement, bind params, step, return success.
/// Caller is responsible for reading columns.
template <class... Args>
bool exec_prepared(sqlite3* db, std::string_view sql, Args&&... args) {
  sqlite3_stmt* stmt = nullptr;
  if (sqlite3_prepare_v2(db, sql.data(), static_cast<int>(sql.size()), &stmt, nullptr) != SQLITE_OK) {
    return false;
  }
  int idx = 1;
  (fin::db::bind_param(stmt, idx++, std::forward<Args>(args)), ...);
  bool ok = sqlite3_step(stmt) == SQLITE_DONE;
  sqlite3_finalize(stmt);
  return ok;
}

/// Get the user ID string for the current thread. Empty string if not signed in.
/// Matches Java's `getEffectiveUserId()` pattern.
inline std::string effective_user_id() {
  auto uid = fin::services::AuthSessionManager::current_user_id();
  if (uid.null()) return "";
  return std::to_string(uid.value);
}

} // namespace fin::db
