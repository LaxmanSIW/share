// fin/services/csv.hpp — CsvService (port of Java CsvService.java)
//
// Streaming CSV import/export. Skill rule §9: stream parse on a worker,
// validate in batches, insert per-batch in one transaction, publish progress
// via atomic, support cancel = rollback of the current batch.
#pragma once
#include "fin/model/bill.hpp"
#include "fin/model/buyer_supplier.hpp"

#include <filesystem>
#include <functional>
#include <string>
#include <vector>

namespace fin::services {

struct CsvImportResult {
  int rows_read{0};
  int rows_imported{0};
  int rows_failed{0};
  std::vector<std::string> errors;     // up to 100 first errors
};

class CsvService {
 public:
  // === Items CSV ===
  /// Export all items to a CSV file. Returns true on success.
  static bool export_items(const std::filesystem::path& out_path,
                            const std::vector<fin::model::ItemRecord>& items);

  /// Import items from a CSV file. `on_progress` is called periodically with
  /// (rows_read, rows_imported). Returns the final import result.
  /// Streams the file; uses a prepared statement + batched transaction so a
  /// 1M-row import finishes in seconds, not minutes (skill §9 import/export).
  static CsvImportResult import_items(
      const std::filesystem::path& csv_path,
      std::function<void(int, int)> on_progress = {});

  // === Buyers CSV ===
  static bool export_buyers(const std::filesystem::path& out_path,
                             const std::vector<fin::model::Buyer>& buyers);
  static CsvImportResult import_buyers(
      const std::filesystem::path& csv_path,
      std::function<void(int, int)> on_progress = {});

  // === Generic CSV parse ===
  /// Parse a CSV file with optional header row, returning rows as vectors of
  /// strings. Handles RFC 4180 quoting. Used by the importer to validate
  /// header columns before the streaming path takes over.
  static std::vector<std::vector<std::string>> parse_csv(
      const std::filesystem::path& csv_path, bool has_header = true);
};

} // namespace fin::services
