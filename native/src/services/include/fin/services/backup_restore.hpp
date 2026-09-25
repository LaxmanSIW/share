// fin/services/backup_restore.hpp — BackupRestoreService (port of Java BackupRestoreService.java)
//
// Skill rule §11 (accounting §11): backups are a feature. Automatic backup
// before every migration + before every version update + restore wizard +
// integrity check on open. Uses SQLite online backup API or VACUUM INTO.
#pragma once
#include <filesystem>
#include <string>
#include <string_view>
#include <chrono>

namespace fin::services {

class BackupRestoreService {
 public:
  /// Create a backup of the current database into `target_path` using
  /// VACUUM INTO (SQLite 3.27+, atomic, doesn't lock writers).
  /// Returns true on success. The backup is a single-file .db that can be
  /// copied elsewhere and restored by simply replacing the working file.
  static bool backup_to(const std::filesystem::path& target_path);

  /// Restore from a backup file: closes the live DB, copies the backup over
  /// the live DB, reopens. Returns true on success.
  static bool restore_from(const std::filesystem::path& backup_path);

  /// Verify database integrity via PRAGMA quick_check. Returns true if OK.
  static bool quick_check();

  /// Auto-backup helper: produces a timestamped .db in <data_dir>/backups/
  /// keeping at most `keep` most recent files. Used before migrations.
  static bool auto_backup(int keep = 10);

  /// List all backups in <data_dir>/backups/, newest first.
  struct BackupEntry {
    std::filesystem::path path;
    std::chrono::sys_days  date;
    std::uintmax_t        size_bytes;
  };
  static std::vector<BackupEntry> list_backups();
};

} // namespace fin::services
