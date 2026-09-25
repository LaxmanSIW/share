// fin/services/csv.cpp
#include "fin/services/csv.hpp"
#include "fin/app/log.hpp"

#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>

namespace fin::services {

namespace {

/// Streaming RFC 4180 CSV parser: yields one row at a time.
class CsvReader {
 public:
  explicit CsvReader(std::istream& in) : in_(in) {}

  bool next_row(std::vector<std::string>& out) {
    out.clear();
    if (in_.eof() && buf_.empty()) return false;
    while (true) {
      if (buf_.empty()) {
        if (!std::getline(in_, line_)) return !out.empty();
        // Strip trailing \r if present (Windows line endings).
        if (!line_.empty() && line_.back() == '\r') line_.pop_back();
        buf_ = line_;
        pos_ = 0;
      }
      // Parse one field.
      std::string field;
      if (pos_ < buf_.size() && buf_[pos_] == '"') {
        // Quoted field: consume until closing quote (handling "" escapes).
        ++pos_;
        while (pos_ < buf_.size()) {
          if (buf_[pos_] == '"') {
            if (pos_ + 1 < buf_.size() && buf_[pos_ + 1] == '"') {
              field.push_back('"'); pos_ += 2; continue;
            }
            ++pos_; // consume closing quote
            break;
          }
          field.push_back(buf_[pos_++]);
        }
        // Skip until comma or end of line.
        while (pos_ < buf_.size() && buf_[pos_] != ',' && buf_[pos_] != '\n') ++pos_;
        if (pos_ < buf_.size() && buf_[pos_] == ',') { ++pos_; out.push_back(field); continue; }
        buf_.clear();
        out.push_back(field);
        return true;
      }
      // Unquoted field: consume until comma or end of line.
      while (pos_ < buf_.size() && buf_[pos_] != ',') {
        field.push_back(buf_[pos_++]);
      }
      if (pos_ < buf_.size() && buf_[pos_] == ',') {
        ++pos_;
        out.push_back(field);
        continue;
      }
      buf_.clear();
      out.push_back(field);
      return true;
    }
  }

 private:
  std::istream& in_;
  std::string line_;
  std::string buf_;
  std::size_t pos_{0};
};

} // namespace

std::vector<std::vector<std::string>> CsvService::parse_csv(
    const std::filesystem::path& csv_path, bool has_header) {
  std::vector<std::vector<std::string>> rows;
  std::ifstream in(csv_path);
  if (!in.is_open()) {
    fin::app::log::errorf("parse_csv: cannot open {}", csv_path.string());
    return rows;
  }
  CsvReader reader(in);
  std::vector<std::string> row;
  if (has_header) {
    if (!reader.next_row(row)) return rows; // skip header
  }
  while (reader.next_row(row)) {
    rows.push_back(row);
  }
  return rows;
}

bool CsvService::export_items(const std::filesystem::path& out_path,
                               const std::vector<fin::model::ItemRecord>& items) {
  std::ofstream out(out_path);
  if (!out.is_open()) return false;
  out << "id,name,hsn,unit,rate,gst,category,stock,purchase_rate,reorder_level\n";
  for (const auto& it : items) {
    out << it.id << ',' << '"' << it.name << '"' << ',' << it.hsn << ',' << it.unit << ','
        << '"' << it.rate.to_decimal_string() << '"' << ','
        << (it.gst_rate.num * 100.0 / it.gst_rate.denom) << ','
        << '"' << it.category_name << '"' << ','
        << '"' << it.current_stock.to_decimal_string() << '"' << ','
        << '"' << it.purchase_rate.to_decimal_string() << '"' << ','
        << '"' << it.reorder_level.to_decimal_string() << '"' << '\n';
  }
  return true;
}

CsvImportResult CsvService::import_items(
    const std::filesystem::path& csv_path,
    std::function<void(int, int)> on_progress) {
  CsvImportResult result;
  // Phase 3b skeleton: parse the CSV; for each row, validate columns; for now
  // we don't insert into the DB (the DAO will be called from the UI import
  // path with a transaction batch — skill §9).
  auto rows = parse_csv(csv_path, true);
  result.rows_read = static_cast<int>(rows.size());
  for (const auto& row : rows) {
    if (row.size() < 5) {
      ++result.rows_failed;
      continue;
    }
    ++result.rows_imported;
    if (on_progress && (result.rows_read % 1000) == 0) {
      on_progress(result.rows_read, result.rows_imported);
    }
  }
  return result;
}

bool CsvService::export_buyers(const std::filesystem::path& out_path,
                                const std::vector<fin::model::Buyer>& buyers) {
  std::ofstream out(out_path);
  if (!out.is_open()) return false;
  out << "id,name,address,gst,phone,state,state_code,city,credit_limit,risk_score\n";
  for (const auto& b : buyers) {
    out << b.id << ',' << '"' << b.name << '"' << ',' << '"' << b.address << '"' << ','
        << b.gst << ',' << b.phone << ',' << b.state << ',' << b.state_code << ',' << b.city << ','
        << '"' << b.credit_limit.to_decimal_string() << '"' << ',' << b.risk_score << '\n';
  }
  return true;
}

CsvImportResult CsvService::import_buyers(
    const std::filesystem::path& csv_path,
    std::function<void(int, int)> on_progress) {
  CsvImportResult result;
  auto rows = parse_csv(csv_path, true);
  result.rows_read = static_cast<int>(rows.size());
  for (const auto& row : rows) {
    if (row.size() < 5) {
      ++result.rows_failed;
      continue;
    }
    ++result.rows_imported;
    if (on_progress && (result.rows_read % 1000) == 0) {
      on_progress(result.rows_read, result.rows_imported);
    }
  }
  return result;
}

} // namespace fin::services
