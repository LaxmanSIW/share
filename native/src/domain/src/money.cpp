// fin/money.cpp — Money implementation
#include "fin/money.hpp"
#include "fin/currency.hpp"

#include <cctype>
#include <cstdint>
#include <cstring>
#include <charconv>
#include <sstream>
#include <format>
#include <stdexcept>

// 128-bit intermediate for multiplication. On GCC/Clang we have __int128; on
// MSVC we fall back to boost (handled in CMakeLists). The skill explicitly
// recommends __int128 / boost::multiprecision::int128_t for the multiplication
// intermediates; we use the GCC/Clang __int128 here.
#if defined(__SIZEOF_INT128__)
namespace fin::detail {
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wpedantic"
__extension__ using int128_t = __int128;
#pragma GCC diagnostic pop
} // namespace fin::detail
#else
#error "Need 128-bit integer support (use Boost.Multiprecision::int128_t on MSVC)"
#endif

namespace fin {

namespace {

/// Add two int64 with overflow check.
std::int64_t add_checked(std::int64_t a, std::int64_t b) {
  if (b > 0 && a > INT64_MAX - b) throw MoneyOverflow("add overflow");
  if (b < 0 && a < INT64_MIN - b) throw MoneyOverflow("add underflow");
  return a + b;
}

std::int64_t sub_checked(std::int64_t a, std::int64_t b) {
  if (b < 0 && a > INT64_MAX + b) throw MoneyOverflow("sub overflow (b<0)");
  if (b > 0 && a < INT64_MIN + b) throw MoneyOverflow("sub underflow (b>0)");
  return a - b;
}

std::int64_t mul_checked(std::int64_t a, std::int64_t b) {
  detail::int128_t p = static_cast<detail::int128_t>(a) * static_cast<detail::int128_t>(b);
  if (p > INT64_MAX || p < INT64_MIN) throw MoneyOverflow("mul overflow");
  return static_cast<std::int64_t>(p);
}

} // namespace

Money Money::operator+(const Money& o) const {
  if (currency_ != o.currency_) throw MoneyMismatch("operator+: currency mismatch");
  return Money{add_checked(minor_, o.minor_), currency_};
}

Money Money::operator-(const Money& o) const {
  if (currency_ != o.currency_) throw MoneyMismatch("operator-: currency mismatch");
  return Money{sub_checked(minor_, o.minor_), currency_};
}

Money Money::apply(Rate r, Rounding mode) const {
  // Compute minor * num / denom using 128-bit intermediate.
  detail::int128_t num   = r.num;
  detail::int128_t denom = r.denom == 0 ? 1 : r.denom;
  detail::int128_t prod  = static_cast<detail::int128_t>(minor_) * num;
  // If denom == 1 (integer rate), no rounding needed.
  if (denom == 1) {
    if (prod > INT64_MAX || prod < INT64_MIN) throw MoneyOverflow("apply: integer rate overflow");
    return Money{static_cast<std::int64_t>(prod), currency_};
  }
  // Divide with remainder; round the remainder by the rounding mode.
  detail::int128_t whole = prod / denom;
  detail::int128_t rem   = prod - whole * denom;
  // Convert remainder to absolute value for rounding decisions
  bool neg = (rem < 0) != (denom < 0);
  detail::int128_t abs_rem = rem < 0 ? -rem : rem;
  detail::int128_t abs_den = denom < 0 ? -denom : denom;
  detail::int128_t half = abs_den / 2;
  bool is_half = (abs_den % 2 == 0) && (abs_rem == half);
  bool more_than_half = (abs_rem > half);

  detail::int128_t rounded = whole;
  switch (mode) {
    case Rounding::HalfAwayFromZero:
      if (more_than_half || is_half) rounded += (neg ? -1 : 1);
      break;
    case Rounding::HalfEven:
      if (more_than_half) rounded += (neg ? -1 : 1);
      else if (is_half) {
        // Round to even.
        detail::int128_t even_target = (whole % 2 == 0) ? whole : whole + (neg ? -1 : 1);
        rounded = even_target;
      }
      break;
    case Rounding::HalfUp:
      if (more_than_half || is_half) rounded += 1;
      break;
    case Rounding::HalfDown:
      if (more_than_half) rounded += (neg ? -1 : 1);
      break;
    case Rounding::TowardZero:
      // No rounding-up; `whole` already truncated toward zero because C++ integer
      // division truncates.
      break;
    case Rounding::AwayFromZero:
      if (abs_rem != 0) rounded += (neg ? -1 : 1);
      break;
  }
  if (rounded > INT64_MAX || rounded < INT64_MIN) throw MoneyOverflow("apply: result overflow");
  return Money{static_cast<std::int64_t>(rounded), currency_};
}

Money Money::scale(std::int64_t multiplier) const {
  return Money{mul_checked(minor_, multiplier), currency_};
}

Money Money::scale(std::int64_t multiplier, std::uint8_t extra_digits, Rounding mode) const {
  // Multiply by multiplier / 10^extra_digits, then round.
  detail::int128_t prod = static_cast<detail::int128_t>(minor_) * static_cast<detail::int128_t>(multiplier);
  detail::int128_t div = 1;
  for (std::uint8_t i = 0; i < extra_digits; ++i) div *= 10;
  detail::int128_t whole = prod / div;
  detail::int128_t rem = prod - whole * div;
  bool neg = (rem < 0) != (div < 0);
  detail::int128_t abs_rem = rem < 0 ? -rem : rem;
  detail::int128_t abs_div = div < 0 ? -div : div;
  detail::int128_t half = abs_div / 2;
  bool is_half = (abs_div % 2 == 0) && (abs_rem == half);
  bool more_than_half = (abs_rem > half);
  detail::int128_t rounded = whole;
  switch (mode) {
    case Rounding::HalfAwayFromZero:
      if (more_than_half || is_half) rounded += (neg ? -1 : 1);
      break;
    case Rounding::HalfEven:
      if (more_than_half) rounded += (neg ? -1 : 1);
      else if (is_half) rounded = (whole % 2 == 0) ? whole : whole + (neg ? -1 : 1);
      break;
    case Rounding::HalfUp:
      if (more_than_half || is_half) rounded += 1;
      break;
    case Rounding::HalfDown:
      if (more_than_half) rounded += (neg ? -1 : 1);
      break;
    case Rounding::TowardZero:
      break;
    case Rounding::AwayFromZero:
      if (abs_rem != 0) rounded += (neg ? -1 : 1);
      break;
  }
  if (rounded > INT64_MAX || rounded < INT64_MIN) throw MoneyOverflow("scale: result overflow");
  return Money{static_cast<std::int64_t>(rounded), currency_};
}

namespace {

/// Parse the integer portion of a decimal string into an int64, returning false
/// on overflow.
bool parse_int(std::string_view s, std::int64_t& out) {
  if (s.empty()) return false;
  std::int64_t v = 0;
  bool neg = false;
  std::size_t i = 0;
  if (s[0] == '+' || s[0] == '-') {
    neg = (s[0] == '-');
    i = 1;
    if (s.size() == 1) return false;
  }
  for (; i < s.size(); ++i) {
    char c = s[i];
    if (c < '0' || c > '9') return false;
    std::int64_t new_v = v * 10 + (c - '0');
    if (new_v / 10 != v) return false; // overflow
    v = new_v;
  }
  out = neg ? -v : v;
  return true;
}

} // namespace

Money Money::parse(std::string_view text, CurrencyId cur) {
  // Trim whitespace.
  while (!text.empty() && (text.front() == ' ' || text.front() == '\t')) text.remove_prefix(1);
  while (!text.empty() && (text.back()  == ' ' || text.back()  == '\t')) text.remove_suffix(1);
  if (text.empty()) throw MoneyParseError("empty money string");

  bool neg = false;
  if (text[0] == '+' || text[0] == '-') {
    neg = (text[0] == '-');
    text.remove_prefix(1);
    if (text.empty()) throw MoneyParseError("only sign");
  }

  std::size_t dot = text.find('.');
  std::string_view whole_part = (dot == std::string_view::npos) ? text : text.substr(0, dot);
  std::string_view frac_part  = (dot == std::string_view::npos) ? std::string_view{} : text.substr(dot + 1);

  if (whole_part.empty() && frac_part.empty()) throw MoneyParseError("no digits");
  if (whole_part.size() > 0 && whole_part[0] == '+') throw MoneyParseError("stray +");

  std::int64_t whole = 0;
  if (!whole_part.empty()) {
    if (!parse_int(whole_part, whole)) throw MoneyParseError("integer part overflow");
  }

  const CurrencyInfo& info = currency_of(cur);
  std::uint8_t exp = info.exponent;

  // Pad/truncate fractional part to currency exponent.
  if (!frac_part.empty() && frac_part.size() != exp) {
    if (frac_part.size() > exp) throw MoneyParseError("too many fractional digits; use parse_round for rounding");
    // Pad with zeros.
  }

  std::int64_t frac = 0;
  if (exp > 0) {
    std::string fp(frac_part);
    while (fp.size() < exp) fp.push_back('0');
    // Take exactly exp digits (extra digits are an error in `parse`).
    if (fp.size() > exp) throw MoneyParseError("too many fractional digits");
    if (!parse_int(fp, frac)) throw MoneyParseError("fractional part overflow");
  }

  std::int64_t scale = 1;
  for (std::uint8_t i = 0; i < exp; ++i) scale *= 10;
  std::int64_t minor = whole * scale + frac;
  if (neg) minor = -minor;
  return Money{minor, cur};
}

Money Money::parse_round(std::string_view text, CurrencyId cur, Rounding mode) {
  // Allow extra digits beyond the currency exponent; round them.
  while (!text.empty() && (text.front() == ' ' || text.front() == '\t')) text.remove_prefix(1);
  while (!text.empty() && (text.back()  == ' ' || text.back()  == '\t')) text.remove_suffix(1);
  if (text.empty()) throw MoneyParseError("empty money string");

  bool neg = false;
  if (text[0] == '+' || text[0] == '-') {
    neg = (text[0] == '-');
    text.remove_prefix(1);
    if (text.empty()) throw MoneyParseError("only sign");
  }

  std::size_t dot = text.find('.');
  std::string_view whole_part = (dot == std::string_view::npos) ? text : text.substr(0, dot);
  std::string_view frac_part  = (dot == std::string_view::npos) ? std::string_view{} : text.substr(dot + 1);

  std::int64_t whole = 0;
  if (!whole_part.empty()) {
    if (!parse_int(whole_part, whole)) throw MoneyParseError("integer part overflow");
  }

  const CurrencyInfo& info = currency_of(cur);
  std::uint8_t exp = info.exponent;

  std::string fp(frac_part);
  // Pad or truncate to extra_digits = max(fp.size(), exp) - exp.
  while (fp.size() < exp) fp.push_back('0');
  std::uint8_t extra = static_cast<std::uint8_t>(fp.size() - exp);

  // Parse fp as int64 → that gives us minor * 10^extra.
  std::int64_t fp_int = 0;
  if (!fp.empty()) {
    if (!parse_int(fp, fp_int)) throw MoneyParseError("fractional part overflow");
  }
  std::int64_t scale = 1;
  for (std::uint8_t i = 0; i < exp; ++i) scale *= 10;
  std::int64_t minor = whole * scale + fin::round_minor(fp_int, extra, mode);
  // Note: round_minor(fp_int, extra) only rounds the fp_int to its natural scale;
  // we then add it to whole*scale. But fp_int is in units of 10^-fp.size() which
  // equals 10^-(exp + extra) = 10^-exp * 10^-extra, so minor_units_from_fp = round_minor(fp_int, extra).
  if (neg) minor = -minor;
  return Money{minor, cur};
}

namespace {

void append_grouped(std::string& out, std::int64_t n, Money::Grouping g) {
  bool neg = n < 0;
  std::uint64_t mag = neg ? static_cast<std::uint64_t>(-(n + 1)) + 1 : static_cast<std::uint64_t>(n);
  if (mag == 0) { out.push_back('0'); return; }
  std::string digits;
  while (mag) {
    digits.push_back(static_cast<char>('0' + (mag % 10)));
    mag /= 10;
  }
  std::reverse(digits.begin(), digits.end());
  std::string grouped;
  if (g == Money::Grouping::None) {
    grouped = digits;
  } else if (g == Money::Grouping::Western) {
    // Group by 3 from the right.
    for (std::size_t i = 0; i < digits.size(); ++i) {
      std::size_t from_right = digits.size() - i;
      if (i > 0 && from_right % 3 == 0) grouped.push_back(',');
      grouped.push_back(digits[i]);
    }
  } else {
    // Indian: rightmost 3, then groups of 2.
    if (digits.size() <= 3) {
      grouped = digits;
    } else {
      grouped = digits.substr(0, digits.size() - 3);
      // Group from the right of the remaining prefix by 2.
      std::string head;
      std::size_t len = grouped.size();
      std::size_t first_group = len % 2 == 0 ? 2 : 1;
      std::size_t i = 0;
      if (first_group == 1) { head.push_back(grouped[0]); head.push_back(','); i = 1; }
      while (i < len) {
        head.push_back(grouped[i]); head.push_back(grouped[i+1]);
        i += 2;
        if (i < len) head.push_back(',');
      }
      grouped = head + digits.substr(digits.size() - 3);
    }
  }
  if (neg) out.push_back('-');
  out += grouped;
}

} // namespace

std::string Money::format(Grouping g, bool with_code) const {
  const CurrencyInfo& info = currency_of(currency_);
  std::uint8_t exp = info.exponent;
  std::int64_t scale = 1;
  for (std::uint8_t i = 0; i < exp; ++i) scale *= 10;
  std::int64_t whole = minor_ / scale;
  std::int64_t frac  = minor_ - whole * scale;
  if (frac < 0) frac = -frac;

  std::string out;
  if (with_code) {
    out.append(info.code);
    out.push_back(' ');
  }
  append_grouped(out, whole, g);
  if (exp > 0) {
    out.push_back('.');
    // Pad fractional part with leading zeros to exp digits.
    std::uint64_t f = static_cast<std::uint64_t>(frac);
    std::string fp;
    for (std::uint8_t i = 0; i < exp; ++i) fp = static_cast<char>('0' + (f % 10)) + fp, f /= 10;
    out += fp;
  }
  return out;
}

std::string Money::to_decimal_string() const {
  return format(Grouping::None, false);
}

} // namespace fin
