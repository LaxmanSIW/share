// fin/rounding.cpp
#include "fin/rounding.hpp"
#include <climits>

namespace fin {

namespace {

/// Unsigned abs of an int64 with proper handling of INT64_MIN (which would
/// overflow when negated). Returns the absolute value and a sign flag.
struct Abs {
  std::uint64_t mag;
  bool neg;
};

inline Abs iabs(std::int64_t v) noexcept {
  if (v >= 0) return {static_cast<std::uint64_t>(v), false};
  if (v == INT64_MIN) return {static_cast<std::uint64_t>(1ULL << 63), true};
  return {static_cast<std::uint64_t>(-v), true};
}

} // namespace

std::int64_t round_minor(std::int64_t n, std::uint8_t extra_digits, Rounding mode) noexcept {
  if (extra_digits == 0) return n;

  // Compute divisor = 10^extra_digits (the part we're throwing away).
  std::uint64_t div = 1;
  for (std::uint8_t i = 0; i < extra_digits; ++i) div *= 10;

  Abs a = iabs(n);
  std::uint64_t whole = a.mag / div;
  std::uint64_t frac  = a.mag % div;

  // Halfway point (frac == div/2 exactly) for half-mode decisions.
  std::uint64_t half = div / 2;
  bool frac_is_half = (div % 2 == 0) && (frac == half);
  bool frac_more_than_half = (frac > half) || (div % 2 != 0 && frac > half);

  std::uint64_t rounded_mag = whole;
  switch (mode) {
    case Rounding::HalfAwayFromZero:
      if (frac_more_than_half || frac_is_half) rounded_mag = whole + 1;
      break;
    case Rounding::HalfEven:
      if (frac_more_than_half) rounded_mag = whole + 1;
      else if (frac_is_half) rounded_mag = whole + (whole & 1);
      break;
    case Rounding::HalfUp:
      // "up" = toward +infinity → for magnitude, "more" rounds up; "half" rounds up too.
      if (frac_more_than_half || frac_is_half) rounded_mag = whole + 1;
      break;
    case Rounding::HalfDown:
      if (frac_more_than_half) rounded_mag = whole + 1;
      break;
    case Rounding::TowardZero:
      // Always truncate the fractional part.
      break;
    case Rounding::AwayFromZero:
      // Round any non-zero fractional part up.
      if (frac != 0) rounded_mag = whole + 1;
      break;
  }

  // Reapply sign.
  if (a.neg) {
    // Overflow guard: rounded_mag must fit in int64 range for negative.
    if (rounded_mag > static_cast<std::uint64_t>(INT64_MAX) + 1ULL) return INT64_MIN;
    return -static_cast<std::int64_t>(rounded_mag);
  } else {
    if (rounded_mag > static_cast<std::uint64_t>(INT64_MAX)) return INT64_MAX;
    return static_cast<std::int64_t>(rounded_mag);
  }
}

} // namespace fin
