// fin/services/backup_restore.cpp
#include "fin/services/backup_restore.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/app/app_dirs.hpp"
#include "fin/app/log.hpp"

#include <sqlite3.h>

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <string>
#include <vector>

namespace fin::services {

namespace fs = std::filesystem;

namespace {

/// Online backup using sqlite3_backup_* — does not lock the source DB and is
/// safe to use while the app is running.
bool online_backup(sqlite3* src, const fs::path& dest_path) {
  fs::create_directories(dest_path.parent_path());
  // Remove existing dest (sqlite3_open_v2 with CREATE will succeed but the
  // file already has data — we want a fresh copy).
  std::error_code ec;
  fs::remove(dest_path, ec);
  sqlite3* dest = nullptr;
  if (sqlite3_open_v2(dest_path.string().c_str(), &dest,
                      SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE, nullptr) != SQLITE_OK) {
    if (dest) sqlite3_close(dest);
    return false;
  }
  sqlite3_backup* bk = sqlite3_backup_init(dest, "main", src, "main");
  if (!bk) {
    sqlite3_close(dest);
    return false;
  }
  // -1 → copy everything in one go (no chunked progress).
  int rc = sqlite3_backup_step(bk, -1);
  sqlite3_backup_finish(bk);
  sqlite3_close(dest);
  // Verify dest exists and is non-empty.
  return rc == SQLITE_DONE && fs::exists(dest_path) && fs::file_size(dest_path) > 0;
}

} // namespace

bool BackupRestoreService::backup_to(const fs::path& target_path) {
  // Use the writer connection for the backup source.
  // Skill rule: the writer is single-threaded, so no concurrent mutation
  // while the backup runs. The backup itself is on the writer thread, so the
  // UI MUST call this from the writer actor (async).
  auto& db = fin::db::DatabaseManager::instance();
  // Use exec_write to get the writer mutex; do the backup inside.
  bool ok = false;
  db.write([&](sqlite3* h) -> int {
    ok = online_backup(h, target_path);
    return 0;
  });
  if (ok) {
    fin::app::log::infof("Backup created at {}", target_path.string());
  } else {
    fin::app::log::errorf("Backup failed for {}", target_path.string());
  }
  return ok;
}

bool BackupRestoreService::restore_from(const fs::path& backup_path) {
  std::error_code ec;
  if (!fs::exists(backup_path, ec)) {
    fin::app::log::errorf("Restore: backup file not found: {}", backup_path.string());
    return false;
  }
  auto& db = fin::db::DatabaseManager::instance();
  fs::path live = db.file_path();
  db.close();
  // Best-effort copy; on Windows may fail if file is locked.
  fs::copy_file(backup_path, live, fs::copy_options::overwrite_existing, ec);
  if (ec) {
    fin::app::log::errorf("Restore: copy failed: {}", ec.message());
    return false;
  }
  db.open();
  return true;
}

bool BackupRestoreService::quick_check() {
  auto& db = fin::db::DatabaseManager::instance();
  // Use writer for the pragma check (only one connection needed).
  bool ok = false;
  db.write([&](sqlite3* h) -> int {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(h, "PRAGMA quick_check", -1, &stmt, nullptr) != SQLITE_OK) return -1;
    if (sqlite3_step(stmt) == SQLITE_ROW) {
      const char* res = (const char*)sqlite3_column_text(stmt, 0);
      ok = res && std::string(res) == "ok";
    }
    sqlite3_finalize(stmt);
    return 0;
  });
  return ok;
}

bool BackupRestoreService::auto_backup(int keep) {
  namespace ch = std::chrono;
  auto now = ch::floor<ch::days>(ch::system_clock::now());
  auto ymd = ch::year_month_day{now};
  char stamp[24];
  std::snprintf(stamp, sizeof(stamp), "%04d-%02u-%02u_%05ld",
                static_cast<int>(ymd.year()),
                static_cast<unsigned>(ymd.month()),
                static_cast<unsigned>(ymd.day()),
                static_cast<long>(ch::duration_cast<ch::seconds>(
                    ch::system_clock::now() - ch::floor<ch::seconds>(ch::system_clock::now())
                 ).count()));
  fs::path backups_dir = fin::app::app_dirs().empty() ? fs::path{"backups"} : (fs::path{fin::app::data_dir()} / "backups");
  fs::create_directories(backups_dir);
  fs::path target = backups_dir / ("invoicestudio_" + std::string(stamp) + ".db");
  if (!backup_to(target)) return false;
  // Prune: keep only the `keep` most recent.
  auto entries = list_backups();
  if (entries.size() > static_cast<std::size_t>(keep)) {
    for (std::size_t i = keep; i < entries.size(); ++i) {
      std::error_code ec;
      fs::remove(entries[i].path, ec);
    }
  }
  return true;
}

std::vector<BackupRestoreService::BackupEntry> BackupRestoreService::list_backups() {
  std::vector<BackupEntry> out;
  fs::path backups_dir = fs::path{fin::app::data_dir()} / "backups";
  std::error_code ec;
  if (!fs::is_directory(backups_dir, ec)) return out;
  for (auto& entry : fs::directory_iterator(backups_dir, ec)) {
    if (!entry.is_regular_file()) continue;
    if (entry.path().extension() != ".db") continue;
    BackupEntry be;
    be.path = entry.path();
    be.size_bytes = entry.file_size(ec);
    // Parse date from filename "invoicestudio_YYYY-MM-DD_NNNNN.db"
    std::string name = entry.path().stem().string();
    // Best-effort date parse.
    auto pos = name.find('_');
    if (pos != std::string::npos && pos + 11 <= name.size()) {
      std::string d = name.substr(pos + 1, 10);
      fin::app::Formatters::parse_iso_date(d, be.date);
    }
    out.push_back(be);
  }
  std::sort(out.begin(), out.end(),
    [](const BackupEntry& a, const BackupEntry& b) {
      // Sort by filename (which contains timestamp), newest first.
      return a.path.filename() > b.path.filename();
    });
  return out;
}

} // namespace fin::services
