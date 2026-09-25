// fin/app/log.hpp — Logging façade (port of Java AppLog.java)
//
// Wraps spdlog behind a stable fin::app::log interface so the rest of the app
// doesn't depend on spdlog headers directly. Skill rule §4.2: never swallow
// exceptions silently — route all catch sites through here.
#pragma once
#include <fmt/core.h>
#include <source_location>
#include <string_view>

namespace fin::app::log {

enum class Level { Debug, Info, Warn, Error };

/// Enable -Dapplog.debug=true behaviour. Off by default; on when env var
/// INVOICESTUDIO_DEBUG=1 or set via set_debug(true).
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

template <class... Args>
void debugf(fmt::format_string<Args...> fmt_str, Args&&... args) {
  if (debug_enabled()) write(Level::Debug, fmt::format(fmt_str, std::forward<Args>(args)...));
}
template <class... Args>
void infof(fmt::format_string<Args...> fmt_str, Args&&... args) {
  write(Level::Info, fmt::format(fmt_str, std::forward<Args>(args)...));
}
template <class... Args>
void warnf(fmt::format_string<Args...> fmt_str, Args&&... args) {
  write(Level::Warn, fmt::format(fmt_str, std::forward<Args>(args)...));
}
template <class... Args>
void errorf(fmt::format_string<Args...> fmt_str, Args&&... args) {
  write(Level::Error, fmt::format(fmt_str, std::forward<Args>(args)...));
}

} // namespace fin::app::log
