// fin/rate.hpp — exact-fraction rate for tax, discounts, FX (skill §2: tax rates are exact fractions)
#pragma once
#include <cstdint>
#include <numeric>

namespace fin {

/// An exact rate as a rational `num / denom`. Stored as two int64s; no float.
/// Example: GST 18% = {18, 100}; 2.5% = {25, 1000}; 1/3 = {1, 3}.
struct Rate {
  std::int64_t num;
  std::int64_t denom;

  constexpr Rate() : num(0), denom(1) {}
  constexpr Rate(std::int64_t n, std::int64_t d) : num(n), denom(d == 0 ? 1 : d) {}

  /// Returns true if the rate is exactly zero (avoid division by zero in callers).
  constexpr bool zero() const noexcept { return num == 0; }

  /// Normalise to lowest terms (gcd-reduced); useful for hashing / display.
  constexpr Rate normalized() const noexcept {
    auto g = std::gcd(num < 0 ? -num : num, denom < 0 ? -denom : denom);
    if (g == 0) g = 1;
    Rate r{num, denom};
    r.num /= g;
    r.denom /= g;
    if (r.denom < 0) { r.num = -r.num; r.denom = -r.denom; }
    return r;
  }

  /// Equality (after normalization). Used by tests.
  constexpr bool equals(const Rate& o) const noexcept {
    auto a = this->normalized();
    auto b = o.normalized();
    return a.num == b.num && a.denom == b.denom;
  }
};

} // namespace fin
