// fin/app/formatters.cpp
#include "fin/app/formatters.hpp"
#include "fin/currency.hpp"

#include <charconv>
#include <chrono>
#include <cstdio>
#include <ctime>
#include <string>

namespace fin::app {

// Money formatting delegates to fin::Money::format which already handles
// Western, Indian, None groupings. We add the currency symbol prefix here.

std::string Formatters::money_inr(const fin::Money& m) {
  auto s = m.format(fin::Money::Grouping::Indian, false);
  const auto& info = fin::currency_of(m.currency());
  return std::string(info.symbol) + s;
}

std::string Formatters::money_western(const fin::Money& m) {
  auto s = m.format(fin::Money::Grouping::Western, false);
  const auto& info = fin::currency_of(m.currency());
  return std::string(info.symbol) + s;
}

std::string Formatters::money_plain(const fin::Money& m) {
  return m.to_decimal_string();
}

std::string Formatters::quantity(std::int64_t minor, std::uint8_t dp) {
  if (dp == 0) return std::to_string(minor);
  bool neg = minor < 0;
  std::uint64_t mag = neg ? static_cast<std::uint64_t>(-(minor + 1)) + 1 : static_cast<std::uint64_t>(minor);
  std::uint64_t scale = 1;
  for (std::uint8_t i = 0; i < dp; ++i) scale *= 10;
  std::uint64_t whole = mag / scale;
  std::uint64_t frac  = mag - whole * scale;
  std::string out;
  if (neg) out.push_back('-');
  out += std::to_string(whole);
  out.push_back('.');
  std::string fp = std::to_string(frac);
  // Pad fp to dp digits with leading zeros.
  while (fp.size() < dp) fp = "0" + fp;
  out += fp;
  return out;
}

std::string Formatters::iso_date(std::chrono::sys_days d) {
  // Use C++20 chrono year_month_day.
  auto ymd = std::chrono::year_month_day{d};
  auto y = static_cast<int>(ymd.year());
  auto m = static_cast<unsigned>(ymd.month());
  auto d_ = static_cast<unsigned>(ymd.day());
  char buf[16];
  std::snprintf(buf, sizeof(buf), "%04d-%02u-%02u", y, m, d_);
  return buf;
}

std::string Formatters::pretty_date(std::chrono::sys_days d) {
  auto ymd = std::chrono::year_month_day{d};
  auto y = static_cast<int>(ymd.year());
  auto m = static_cast<unsigned>(ymd.month());
  auto d_ = static_cast<unsigned>(ymd.day());
  static const char* months[] = {
    "Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"
  };
  const char* mon = m >= 1 && m <= 12 ? months[m - 1] : "???";
  char buf[24];
  std::snprintf(buf, sizeof(buf), "%u %s %d", d_, mon, y);
  return buf;
}

std::string Formatters::iso_utc(std::chrono::utc_clock::time_point tp) {
  auto s = std::chrono::duration_cast<std::chrono::seconds>(tp.time_since_epoch()).count();
  std::time_t t = static_cast<std::time_t>(s);
  std::tm tm{};
  #if defined(_WIN32)
    ::gmtime_s(&tm, &t);
  #else
    ::gmtime_r(&t, &tm);
  #endif
  char buf[32];
  std::strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &tm);
  return buf;
}

bool Formatters::parse_iso_date(std::string_view s, std::chrono::sys_days& out) {
  if (s.size() != 10) return false;
  if (s[4] != '-' || s[7] != '-') return false;
  int y, m, d;
  auto from_chars = [](std::string_view sv, int& v) {
    auto r = std::from_chars(sv.data(), sv.data() + sv.size(), v);
    return r.ec == std::errc{};
  };
  if (!from_chars(s.substr(0, 4), y)) return false;
  if (!from_chars(s.substr(5, 2), m)) return false;
  if (!from_chars(s.substr(8, 2), d)) return false;
  try {
    auto ymd = std::chrono::year{y} / m / d;
    if (!std::chrono::year_month_day{ymd}.ok()) return false;
    out = std::chrono::sys_days{ymd};
    return true;
  } catch (...) {
    return false;
  }
}

} // namespace fin::app
