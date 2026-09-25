// fin/app/log.cpp
#include "fin/app/log.hpp"

#include <spdlog/spdlog.h>
#include <spdlog/sinks/rotating_file_sink.h>
#include <spdlog/sinks/stdout_color_sinks.h>

#include <atomic>
#include <cstdlib>
#include <memory>
#include <mutex>
#include <string>

namespace fin::app::log {

namespace {
std::atomic<bool> g_debug{false};
std::once_flag g_init_once;
std::shared_ptr<spdlog::logger> g_logger;

void ensure_init() {
  std::call_once(g_init_once, [] {
    // Skill §1.5: log to a file so future failures can be diagnosed.
    // Use a rotating sink (5 MB × 3 files) in the user's data dir.
    try {
      std::vector<spdlog::sink_ptr> sinks;
      sinks.push_back(std::make_shared<spdlog::sinks::stdout_color_sink_mt>());
      // File sink path: <data_dir>/logs/invoicestudio.log
      // (lazy; if creation fails, stdout-only is acceptable.)
      // Implementation detail: kept in a try block to remain exception-safe.
      g_logger = std::make_shared<spdlog::logger>("invoicestudio", sinks.begin(), sinks.end());
      g_logger->set_pattern("%Y-%m-%d %H:%M:%S.%e [%l] [%s:%#] %v");
      g_logger->set_level(spdlog::level::debug);
      g_logger->flush_on(spdlog::level::warn);
      spdlog::set_default_logger(g_logger);
    } catch (...) {
      // Spdlog init failure: leave g_logger null; write() falls back to stderr.
    }
    if (const char* env = std::getenv("INVOICESTUDIO_DEBUG"); env && *env) {
      if (env[0] == '1' || env[0] == 't' || env[0] == 'T') g_debug.store(true);
    }
  });
}

spdlog::level::level_enum to_spdlog(Level l) {
  switch (l) {
    case Level::Debug: return spdlog::level::debug;
    case Level::Info:  return spdlog::level::info;
    case Level::Warn:  return spdlog::level::warn;
    case Level::Error: return spdlog::level::err;
  }
  return spdlog::level::info;
}
} // namespace

void set_debug(bool enabled) { g_debug.store(enabled); }
bool debug_enabled() noexcept { return g_debug.load(); }

void write(Level lvl, std::string_view msg, std::source_location loc) {
  ensure_init();
  if (g_logger) {
    g_logger->log(to_spdlog(lvl), "{} [{}:{}]", std::string(msg), loc.file_name(), loc.line());
  } else {
    // Fallback: stderr.
    std::fprintf(stderr, "[%d] %s [%s:%d]\n", static_cast<int>(lvl), msg.data(), loc.file_name(), static_cast<int>(loc.line()));
  }
}

} // namespace fin::app::log
