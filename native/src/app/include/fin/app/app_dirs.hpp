// fin/app/app_dirs.hpp — Per-user writable data dir (port of Java AppDirs.java)
//
// Resolution order (matches the Java original):
//   1. Environment variable INVOICESTUDIO_DATA_DIR (tests / portable)
//   2. Windows: %APPDATA%/InvoiceStudio
//      macOS:  ~/Library/Application Support/InvoiceStudio
//      Linux:  $XDG_DATA_HOME/InvoiceStudio (default ~/.local/share/InvoiceStudio)
//   3. Fallback: current working directory (legacy)
//
// On first run, copies a legacy working-directory DB into the data dir so users
// upgrading from "run the jar" keep their records.
#pragma once
#include <filesystem>
#include <string_view>

namespace fin::app {

namespace fs = std::filesystem;

/// Per-user writable application data directory (creates if missing).
fs::path data_dir();

/// The user's Downloads folder (used for export / import browsing).
fs::path downloads_dir();

/// SQLite connection string for the app database in the data dir.
/// Form: "invoicestudio.db" absolute path with forward slashes.
/// Migration of a legacy CWD DB happens here, once, best-effort.
std::string database_path_string();

/// Override the data dir (for tests / portable deployments).
void set_data_dir_override(fs::path dir);

/// Override the downloads dir (for tests).
void set_downloads_dir_override(fs::path dir);

} // namespace fin::app
