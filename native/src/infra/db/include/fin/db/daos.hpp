// fin/db/buyer_dao.hpp
#pragma once
#include "fin/db/database_manager.hpp"
#include "fin/model/buyer_supplier.hpp"
#include <optional>
#include <vector>
#include <string>

namespace fin::db {

class BuyerDao {
 public:
  explicit BuyerDao(DatabaseManager& db) : db_(db) {}
  std::vector<fin::model::Buyer> find_all();
  std::optional<fin::model::Buyer> find_by_id(std::string_view id);
  bool save(const fin::model::Buyer& b);
  bool erase(std::string_view id);
 private:
  DatabaseManager& db_;
};

class SupplierDao {
 public:
  explicit SupplierDao(DatabaseManager& db) : db_(db) {}
  std::vector<fin::model::Supplier> find_all();
  std::optional<fin::model::Supplier> find_by_id(std::string_view id);
  bool save(const fin::model::Supplier& s);
  bool erase(std::string_view id);
 private:
  DatabaseManager& db_;
};

class ItemDao {
 public:
  explicit ItemDao(DatabaseManager& db) : db_(db) {}
  std::vector<fin::model::ItemRecord> find_all();
  std::optional<fin::model::ItemRecord> find_by_id(std::string_view id);
  bool save(const fin::model::ItemRecord& it);
  bool erase(std::string_view id);
 private:
  DatabaseManager& db_;
};

class VariableDao {
 public:
  explicit VariableDao(DatabaseManager& db) : db_(db) {}
  std::vector<fin::model::VariableDef> find_all();
  bool save(const fin::model::VariableDef& v);
  bool erase(std::string_view key);
 private:
  DatabaseManager& db_;
};

class TemplateDao {
 public:
  explicit TemplateDao(DatabaseManager& db) : db_(db) {}
  std::vector<fin::model::Template> find_all();
  std::optional<fin::model::Template> find_by_id(std::string_view id);
  bool save(const fin::model::Template& t);
  bool erase(std::string_view id);
 private:
  DatabaseManager& db_;
};

class SettingsDao {
 public:
  explicit SettingsDao(DatabaseManager& db) : db_(db) {}
  /// Returns the user's settings JSON, or empty string if none.
  std::string load_json();
  bool save_json(std::string_view json_data);
 private:
  DatabaseManager& db_;
};

class AuthDao {
 public:
  explicit AuthDao(DatabaseManager& db) : db_(db) {}
  /// Save a Firebase session locally so it survives app restarts.
  bool save_session(const fin::model::UserSession& s);
  /// Load the most recent session (or nullopt if none).
  std::optional<fin::model::UserSession> load_session();
  /// Clear the session (logout).
  bool clear();
 private:
  DatabaseManager& db_;
};

} // namespace fin::db
