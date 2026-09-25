// fin/app/log.cpp — Simple stderr-based logging (no spdlog dependency).
#include "fin/app/log.hpp"

#include <cstdio>
#include <cstdlib>
#include <atomic>

namespace fin::app::log {

namespace {
std::atomic<bool> g_debug{false};
}

void set_debug(bool enabled) { g_debug.store(enabled); }
bool debug_enabled() noexcept { return g_debug.load(); }

void write(Level lvl, std::string_view msg, std::source_location loc) {
  const char* lvl_str = "?";
  switch (lvl) {
    case Level::Debug: lvl_str = "DEBUG"; break;
    case Level::Info:  lvl_str = "INFO";  break;
    case Level::Warn:  lvl_str = "WARN";  break;
    case Level::Error: lvl_str = "ERROR"; break;
  }
  std::fprintf(stderr, "[%s] %.*s [%s:%d]\n", lvl_str,
                static_cast<int>(msg.size()), msg.data(), loc.file_name(), static_cast<int>(loc.line()));
}

} // namespace fin::app::log
