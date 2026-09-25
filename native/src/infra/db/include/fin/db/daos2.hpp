// fin/db/daos2.hpp — Remaining DAOs (Phase 2c)
//
//   TransportDao        — transports table (id, name, phone, vehicle_number)
//   CategoryDao         — categories table (id, name, user_id; UNIQUE(user_id, name))
//   ExpenseDao          — expenses table (id, date, category, amount, payment_mode, json_data)
//   ExpenseAccountDao   — expense_accounts table (id, name, archived, json_data)
//   PurchaseBillDao     — purchase_bills table (id, bill_no, supplier_bill_no, date, supplier_id, total, itc, status, json_data)
//   StockLedgerDao      — stock_ledger table (immutable append-only movements; item current_stock = sum)
//   TransactionDao      — transactions table (financial book entries: sales/purchase/payment)
//   LabelPrintHistoryDao — label_print_history table (info-only audit of label runs)
//
// All follow the same conventions as BillDao: parameterised SQL, json_data column
// for the full model + indexed columns for fast listing, scoped by user_id.
#pragma once
#include "fin/db/database_manager.hpp"
#include "fin/model/buyer_supplier.hpp"
#include "fin/model/purchase_stock.hpp"

#include <optional>
#include <string>
#include <vector>

namespace fin::db {

class TransportDao {
 public:
  explicit TransportDao(DatabaseManager& db) : db_(db) {}
  std::vector<fin::model::Transport> find_all();
  std::optional<fin::model::Transport> find_by_id(std::string_view id);
  bool save(const fin::model::Transport& t);
  bool erase(std::string_view id);
 private:
  DatabaseManager& db_;
};

class CategoryDao {
 public:
  explicit CategoryDao(DatabaseManager& db) : db_(db) {}
  std::vector<fin::model::ItemCategory> find_all();
  std::optional<fin::model::ItemCategory> find_by_id(std::string_view id);
  bool save(const fin::model::ItemCategory& c);
  bool erase(std::string_view id);
 private:
  DatabaseManager& db_;
};

class ExpenseDao {
 public:
  explicit ExpenseDao(DatabaseManager& db) : db_(db) {}
  std::vector<fin::model::Expense> find_all();
  std::optional<fin::model::Expense> find_by_id(std::string_view id);
  bool save(const fin::model::Expense& e);
  bool erase(std::string_view id);
 private:
  DatabaseManager& db_;
};

class ExpenseAccountDao {
 public:
  explicit ExpenseAccountDao(DatabaseManager& db) : db_(db) {}
  std::vector<fin::model::ExpenseAccount> find_all();
  std::optional<fin::model::ExpenseAccount> find_by_id(std::string_view id);
  bool save(const fin::model::ExpenseAccount& a);
  bool erase(std::string_view id);
 private:
  DatabaseManager& db_;
};

class PurchaseBillDao {
 public:
  explicit PurchaseBillDao(DatabaseManager& db) : db_(db) {}
  std::vector<fin::model::PurchaseBill> find_all();
  std::optional<fin::model::PurchaseBill> find_by_id(std::string_view id);
  bool save(const fin::model::PurchaseBill& p);
  bool erase(std::string_view id);
 private:
  DatabaseManager& db_;
};

class StockLedgerDao {
 public:
  explicit StockLedgerDao(DatabaseManager& db) : db_(db) {}

  /// Append-only ledger insert. Skill rule §4: posted entries are immutable.
  /// Posting = one transaction (entry + item current_stock update + audit).
  bool append(const fin::model::StockLedgerEntry& e);

  /// Returns all movements for an item, oldest first (for stock card view).
  std::vector<fin::model::StockLedgerEntry> find_by_item(std::string_view item_id);

  /// Returns the current stock balance for an item:
  /// opening_stock + Σ qty_in − Σ qty_out.
  /// Reads from items.current_stock (maintained by triggers / write path).
  fin::Money current_stock(std::string_view item_id);

  /// Recompute current_stock from raw ledger lines (verifier; skill §6.6).
  fin::Money recompute_stock_from_ledger(std::string_view item_id);
 private:
  DatabaseManager& db_;
};

class TransactionDao {
 public:
  explicit TransactionDao(DatabaseManager& db) : db_(db) {}
  std::vector<fin::model::Transaction> find_all();
  std::optional<fin::model::Transaction> find_by_id(std::string_view id);
  /// Find by bill_id (transactions are 1:1 with bills in the sales book).
  std::optional<fin::model::Transaction> find_by_bill_id(std::string_view bill_id);
  bool save(const fin::model::Transaction& t);
  /// Soft-delete (sets deleted=true; never actually erases — audit trail).
  bool soft_delete(std::string_view id, std::string_view reason);
  bool erase(std::string_view id);
 private:
  DatabaseManager& db_;
};

class LabelPrintHistoryDao {
 public:
  explicit LabelPrintHistoryDao(DatabaseManager& db) : db_(db) {}
  /// Insert a print-run audit row. Returns row id.
  bool append(const fin::model::LabelPrintHistory& h);
  /// All history for the current user, newest first.
  std::vector<fin::model::LabelPrintHistory> find_all();
 private:
  DatabaseManager& db_;
};

} // namespace fin::db
