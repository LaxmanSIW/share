// fin/services/knowledge_seed.cpp
#include "fin/services/knowledge_seed.hpp"
#include "fin/app/app_dirs.hpp"
#include "fin/app/log.hpp"

#include <filesystem>
#include <fstream>
#include <system_error>

namespace fin::services {

namespace fs = std::filesystem;

bool KnowledgeSeed::seed_if_missing() {
  fs::path target = fs::path{fin::app::data_dir()} / "knowledge-hub.json";
  std::error_code ec;
  if (fs::exists(target, ec)) return true;
  // Try the bundled copy next to the executable first.
  fs::path bundled = fs::current_path() / "resources" / "knowledge" / "knowledge-hub.json";
  if (!fs::exists(bundled, ec)) {
    // Also check /usr/share/invoicestudio (Linux packaging).
    bundled = "/usr/share/invoicestudio/knowledge/knowledge-hub.json";
  }
  if (!fs::exists(bundled, ec)) {
    fin::app::log::warnf("KnowledgeSeed: no bundled knowledge-hub.json found");
    return false;
  }
  fs::create_directories(target.parent_path(), ec);
  fs::copy_file(bundled, target, fs::copy_options::overwrite_existing, ec);
  if (ec) {
    fin::app::log::errorf("KnowledgeSeed: copy failed: {}", ec.message());
    return false;
  }
  return true;
}

bool KnowledgeSeed::force_reseed() {
  fs::path target = fs::path{fin::app::data_dir()} / "knowledge-hub.json";
  fs::path bundled = fs::current_path() / "resources" / "knowledge" / "knowledge-hub.json";
  if (!fs::exists(bundled)) return false;
  std::error_code ec;
  fs::copy_file(bundled, target, fs::copy_options::overwrite_existing, ec);
  return !ec;
}

} // namespace fin::services
