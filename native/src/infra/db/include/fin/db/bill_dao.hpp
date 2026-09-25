// fin/db/bill_dao.hpp — BillDao (port of Java BillDao.java)
//
// All operations partition data by current_user_id (set by AuthSessionManager
// after login). Skill rule §8: SQL is parameterised at every site; we never
// string-concat values into SQL.
#pragma once
#include "fin/db/database_manager.hpp"
#include "fin/model/bill.hpp"

#include <memory>
#include <optional>
#include <string>
#include <vector>

namespace fin::db {

class BillDao {
 public:
  explicit BillDao(DatabaseManager& db) : db_(db) {}

  /// Get all bills for the current user, ordered by date DESC, bill_no DESC.
  /// Returns an empty vector if no user is signed in.
  std::vector<fin::model::Bill> find_all();

  /// Get one bill by id, scoped to the current user. Returns nullopt if not found.
  std::optional<fin::model::Bill> find_by_id(std::string_view id);

  /// Get one bill by bill_no, scoped to the current user.
  std::optional<fin::model::Bill> find_by_bill_no(std::string_view bill_no);

  /// Upsert: insert or replace. Computes grand_total, due_amount, buyer_name
  /// from the bill and writes them to the indexed columns + json_data.
  /// Sets created_at / updated_at if missing.
  bool save(const fin::model::Bill& bill);

  /// Delete by id, scoped to the current user.
  bool erase(std::string_view id);

  /// Increment print_count for a bill (load + save round-trip).
  bool increment_print_count(std::string_view id);

 private:
  DatabaseManager& db_;
};

} // namespace fin::db
