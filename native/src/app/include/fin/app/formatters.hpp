// fin/app/formatters.hpp — Money / date / number formatting (port of Java AppFormatters.java)
//
// Skill §10 (accounting §10): use ICU / QLocale for display; cache per-thread.
// Skill rule §3.4 (responsive-ui): no per-call of_pattern / DecimalFormat
// allocations; cache static formatters.
#pragma once
#include "fin/money.hpp"

#include <chrono>
#include <string>
#include <string_view>

namespace fin::app {

enum class DigitGrouping { Western, Indian, None };

class Formatters {
 public:
  // === Money ===

  /// Format money with Indian lakh/crore grouping ("12,34,567.89") and
  /// the currency symbol. E.g. Money(1234567.89, INR) → "₹12,34,567.89".
  static std::string money_inr(const fin::Money& m);
  /// Western grouping ("1,234,567.89") + symbol.
  static std::string money_western(const fin::Money& m);
  /// Plain decimal ("1234567.89") — for CSV / JSON storage.
  static std::string money_plain(const fin::Money& m);

  // === Quantity (qty has 3-6 dp; money has 2 — these are different scales) ===

  /// Format a quantity given as int64 in 1e-4 minor units, e.g. qty=12500
  /// (4 dp) → "1.2500".
  static std::string quantity(std::int64_t minor, std::uint8_t dp);

  // === Dates ===

  /// ISO date string "yyyy-MM-dd".
  static std::string iso_date(std::chrono::sys_days d);
  /// Pretty "12 Sep 2026".
  static std::string pretty_date(std::chrono::sys_days d);
  /// UTC timestamp ISO 8601 "2026-09-25T14:30:00Z".
  static std::string iso_utc(std::chrono::utc_clock::time_point tp);

  // === Number parsing (money never goes through here — see fin::Money::parse) ===

  /// Parse an ISO date; returns false on failure.
  static bool parse_iso_date(std::string_view s, std::chrono::sys_days& out);
};

} // namespace fin::app
