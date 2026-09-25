// fin/rounding.hpp — explicit rounding modes (skill §2: rounding is a business rule)
#pragma once
#include <cstdint>

namespace fin {

enum class Rounding : std::uint8_t {
  HalfAwayFromZero = 0, // commercial: 0.5 → 1, -0.5 → -1 (most tax regimes)
  HalfEven         = 1, // banker's:  0.5 → 0 or 2 (whichever is even)
  HalfUp           = 2, // 0.5 always rounds toward +inf
  HalfDown         = 3, // 0.5 always rounds toward 0
  TowardZero       = 4, // truncate (floor for +, ceil for -)
  AwayFromZero     = 5, // 0.5 → 1 magnitude (away from 0)
};

/// Round a signed minor-unit intermediate (scaled by `extra_digits` beyond the
/// currency's exponent) into a final minor-unit value.
///
/// `n`        : the signed integer to round
/// `extra_digits` : how many extra digits of precision `n` carries beyond the
///                  target scale (e.g. if you computed tax at 1e-4 precision
///                  but money is at 1e-2, extra_digits = 2).
///
/// Example: round_minor(12345, 2, HalfAwayFromZero)
///   n=12345 carries 2 extra digits → effective value is 123.45 of minor unit
///   → rounds to 123 (since the discarded part is 0.45 minor units → below .5)
///   → returns 123.
std::int64_t round_minor(std::int64_t n, std::uint8_t extra_digits, Rounding mode) noexcept;

} // namespace fin
