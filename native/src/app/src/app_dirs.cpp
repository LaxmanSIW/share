// fin/app/app_dirs.cpp
#include "fin/app/app_dirs.hpp"

#include <cstdlib>
#include <fstream>
#include <system_error>

#ifdef _WIN32
  #include <shlobj.h>
  #pragma comment(lib, "shell32.lib")
#else
  #include <pwd.h>
  #include <unistd.h>
#endif

namespace fin::app {

namespace fs = std::filesystem;

namespace {
fs::path g_data_override;
fs::path g_downloads_override;

fs::path home_dir() {
#ifdef _WIN32
  if (const char* userprofile = std::getenv("USERPROFILE")) return userprofile;
  return fs::current_path();
#else
  if (const char* home = std::getenv("HOME"); home && *home) return home;
  if (auto* pw = getpwuid(getuid())) return pw->pw_dir;
  return fs::current_path();
#endif
}

bool dir_exists(const fs::path& p) {
  std::error_code ec;
  return fs::is_directory(p, ec);
}

void ensure_dir(const fs::path& p) {
  std::error_code ec;
  fs::create_directories(p, ec);
  // Silent: caller checks existence on next call.
}

fs::path legacy_cwd_db() {
  return fs::current_path() / "invoicestudio.db";
}

void copy_if_present(const fs::path& legacy, const fs::path& target, const char* suffix) {
  std::error_code ec;
  auto src = legacy;
  src += suffix;
  if (fs::is_regular_file(src, ec)) {
    auto dst = target;
    dst += suffix;
    fs::copy_file(src, dst, fs::copy_options::overwrite_existing, ec);
  }
}

void migrate_legacy_db(const fs::path& target) {
  std::error_code ec;
  if (fs::exists(target, ec)) return;
  auto legacy = legacy_cwd_db();
  if (!fs::is_regular_file(legacy, ec)) return;
  fs::create_directories(target.parent_path(), ec);
  fs::copy_file(legacy, target, fs::copy_options::overwrite_existing, ec);
  // SQLite side files (clean exits checkpoint them away; copy if present).
  copy_if_present(legacy, target, "-wal");
  copy_if_present(legacy, target, "-shm");
}

} // namespace

fs::path data_dir() {
  if (!g_data_override.empty()) return g_data_override;

  if (const char* env = std::getenv("INVOICESTUDIO_DATA_DIR"); env && *env) {
    return fs::path(env);
  }

#ifdef _WIN32
  fs::path base;
  if (const char* appdata = std::getenv("APPDATA"); appdata && *appdata) {
    base = appdata;
  } else {
    base = home_dir() / "AppData" / "Roaming";
  }
#elif defined(__APPLE__)
  fs::path base = home_dir() / "Library" / "Application Support";
#else
  fs::path base;
  if (const char* xdg = std::getenv("XDG_DATA_HOME"); xdg && *xdg) {
    base = xdg;
  } else {
    base = home_dir() / ".local" / "share";
  }
#endif
  fs::path dir = base / "InvoiceStudio";
  ensure_dir(dir);
  return dir;
}

fs::path downloads_dir() {
  if (!g_downloads_override.empty()) return g_downloads_override;
  if (const char* env = std::getenv("INVOICESTUDIO_DOWNLOADS_DIR"); env && *env) {
    return fs::path(env);
  }
  fs::path home = home_dir();
  fs::path downloads = home / "Downloads";
  if (dir_exists(downloads)) return downloads;
  return dir_exists(home) ? home : fs::current_path();
}

std::string database_path_string() {
  fs::path target = data_dir() / "invoicestudio.db";
  migrate_legacy_db(target);
  // Normalise to forward slashes for SQLite URI form.
  std::string s = target.string();
  for (char& c : s) if (c == '\\') c = '/';
  return s;
}

void set_data_dir_override(fs::path dir) { g_data_override = std::move(dir); }
void set_downloads_dir_override(fs::path dir) { g_downloads_override = std::move(dir); }

} // namespace fin::app
