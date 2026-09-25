// fin/money.hpp — fixed-point money type (skill §1: int64 minor units, overflow-checked, 128-bit intermediates)
//
// INVARIANTS (enforced everywhere a Money is constructed):
//   - `minor_` is the amount in the smallest currency unit (paise, cents, ...)
//   - `currency_` is the same across both sides of any addition/subtraction (no implicit FX)
//   - No float/double ever crosses this API
//   - Multiplication uses __int128 / boost::multiprecision::int128_t intermediates
//   - Overflow throws `MoneyOverflow` (never silent truncation)
#pragma once
#include "fin/currency.hpp"
#include "fin/rate.hpp"
#include "fin/rounding.hpp"

#include <cstdint>
#include <optional>
#include <stdexcept>
#include <string>
#include <string_view>

namespace fin {

class Money;

struct MoneyOverflow : std::overflow_error {
  explicit MoneyOverflow(const std::string& what) : std::overflow_error(what) {}
};

struct MoneyMismatch : std::logic_error {
  explicit MoneyMismatch(const std::string& what) : std::logic_error(what) {}
};

struct MoneyParseError : std::runtime_error {
  explicit MoneyParseError(const std::string& what) : std::runtime_error(what) {}
};

class Money {
 public:
  /// Default ctor — zero INR. Required for std::pair/std::tuple/std::map[].
  Money() noexcept : minor_(0), currency_(CurrencyId::INR) {}

 public:
  // === Construction ===

  /// Construct from a known-good minor-unit amount + currency.
  /// Used by the DB layer after reading int64 columns; never throws.
  static constexpr Money from_minor(std::int64_t minor, CurrencyId cur) {
    return Money{minor, cur};
  }

  /// Construct from a major-unit decimal string, e.g. "1234.56" or "-12.00".
  /// Throws `MoneyParseError` on malformed input or wrong number of digits.
  /// Refuses to round silently — extra digits beyond the currency's exponent
  /// are an error (call `parse_round` if you want explicit rounding).
  static Money parse(std::string_view text, CurrencyId cur);
  static Money parse_round(std::string_view text, CurrencyId cur, Rounding mode);

  /// Zero in a given currency.
  static constexpr Money zero(CurrencyId cur) { return Money{0, cur}; }

  // === Accessors ===
  constexpr std::int64_t minor() const noexcept { return minor_; }
  constexpr CurrencyId currency() const noexcept { return currency_; }
  constexpr bool is_zero() const noexcept { return minor_ == 0; }
  constexpr bool is_negative() const noexcept { return minor_ < 0; }

  /// Returns the absolute value (preserves currency).
  constexpr Money abs() const noexcept {
    return Money{minor_ < 0 ? -minor_ : minor_, currency_};
  }
  /// Returns -x (preserves currency). Throws on INT64_MIN.
  constexpr Money neg() const {
    if (minor_ == INT64_MIN) throw MoneyOverflow("neg: INT64_MIN has no positive counterpart");
    return Money{-minor_, currency_};
  }

  // === Arithmetic — overflow-checked ===

  /// Addition. Throws `MoneyMismatch` if currencies differ, `MoneyOverflow` on overflow.
  Money operator+(const Money& o) const;
  Money operator-(const Money& o) const;
  Money& operator+=(const Money& o) { *this = *this + o; return *this; }
  Money& operator-=(const Money& o) { *this = *this - o; return *this; }

  /// Apply a rate (e.g. 18% tax, 5% discount). Uses 128-bit intermediate.
  /// Returns the rounded amount (skill §2: rounding is explicit at call site).
  Money apply(Rate r, Rounding mode) const;

  /// Multiply by an integer quantity (e.g. line total = unit_price × qty_minor).
  /// Throws on overflow. For fractional quantities use `scale`.
  Money scale(std::int64_t multiplier) const;

  /// Multiply by a fixed-point fractional multiplier: `multiplier` is
  /// interpreted as `multiplier / 10^extra_digits`. For example, to multiply
  /// by 1.5, pass {multiplier=15, extra_digits=1}. Used for qty × unit price
  /// where qty has 3-6 decimal places (skill §1: quantities use more dp than money).
  Money scale(std::int64_t multiplier, std::uint8_t extra_digits, Rounding mode) const;

  // === Comparison (same-currency only) ===
  constexpr bool operator==(const Money& o) const noexcept { return minor_ == o.minor_ && currency_ == o.currency_; }
  constexpr bool operator!=(const Money& o) const noexcept { return !(*this == o); }
  constexpr bool operator<(const Money& o) const  { require_same(o); return minor_ <  o.minor_; }
  constexpr bool operator<=(const Money& o) const { require_same(o); return minor_ <= o.minor_; }
  constexpr bool operator>(const Money& o) const  { require_same(o); return minor_ >  o.minor_; }
  constexpr bool operator>=(const Money& o) const { require_same(o); return minor_ >= o.minor_; }

  // === Formatting ===
  /// Format as `code value`, e.g. "INR 1,234.56" (Western grouping) or
  /// "INR 12,34,567.89" (Indian lakh/crore). See AppFormatters.cpp.
  /// No grouping for "raw" mode (used in CSV export).
  enum class Grouping { None, Western, Indian };
  std::string format(Grouping g = Grouping::Western, bool with_code = true) const;

  /// Decimal major-unit string (no grouping, no symbol). For storage / display
  /// where formatting is a presentation concern.
  std::string to_decimal_string() const;

 private:
  std::int64_t minor_;
  CurrencyId  currency_;

  constexpr Money(std::int64_t m, CurrencyId c) : minor_(m), currency_(c) {}

  constexpr void require_same(const Money& o) const {
    if (currency_ != o.currency_) throw MoneyMismatch("money comparison across currencies");
  }
};

// === Free helpers ===

/// Convenience: sum a range of same-currency Money values.
template <class Iter>
Money sum(Iter begin, Iter end, CurrencyId fallback_currency) {
  Money total = Money::zero(fallback_currency);
  for (auto it = begin; it != end; ++it) total += *it;
  return total;
}

} // namespace fin
