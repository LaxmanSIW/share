// fin/app/log.hpp — Logging façade (port of Java AppLog.java)
//
// Wraps logging behind a stable fin::app::log interface. Uses plain std::string
// + snprintf (no fmt dependency). Skill rule §4.2: never swallow exceptions
// silently — route all catch sites through here.
#pragma once
#include <string>
#include <string_view>
#include <source_location>

namespace fin::app::log {

enum class Level { Debug, Info, Warn, Error };

/// Enable INVOICESTUDIO_DEBUG=1 behaviour.
void set_debug(bool enabled);
bool debug_enabled() noexcept;

void write(Level lvl, std::string_view msg, std::source_location loc = std::source_location::current());

inline void debug(std::string_view msg, std::source_location loc = std::source_location::current()) {
  if (debug_enabled()) write(Level::Debug, msg, loc);
}
inline void info(std::string_view msg, std::source_location loc = std::source_location::current()) {
  write(Level::Info, msg, loc);
}
inline void warn(std::string_view msg, std::source_location loc = std::source_location::current()) {
  write(Level::Warn, msg, loc);
}
inline void error(std::string_view msg, std::source_location loc = std::source_location::current()) {
  write(Level::Error, msg, loc);
}

// printf-style formatting helpers.
template <typename... Args>
void debugf(const char* fmt, Args&&... args) {
  if (debug_enabled()) {
    char buf[4096];
    std::snprintf(buf, sizeof(buf), fmt, std::forward<Args>(args)...);
    write(Level::Debug, buf);
  }
}
template <typename... Args>
void infof(const char* fmt, Args&&... args) {
  char buf[4096];
  std::snprintf(buf, sizeof(buf), fmt, std::forward<Args>(args)...);
  write(Level::Info, buf);
}
template <typename... Args>
void warnf(const char* fmt, Args&&... args) {
  char buf[4096];
  std::snprintf(buf, sizeof(buf), fmt, std::forward<Args>(args)...);
  write(Level::Warn, buf);
}
template <typename... Args>
void errorf(const char* fmt, Args&&... args) {
  char buf[4096];
  std::snprintf(buf, sizeof(buf), fmt, std::forward<Args>(args)...);
  write(Level::Error, buf);
}

} // namespace fin::app::log
