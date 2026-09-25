// fin/allocate.cpp — Largest-remainder allocation
#include "fin/allocate.hpp"
#include "fin/money.hpp"
#include <algorithm>
#include <numeric>
#include <stdexcept>
#include <vector>

namespace fin {

namespace {
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wpedantic"
__extension__ using int128_t = __int128;
#pragma GCC diagnostic pop
}

std::vector<Money> allocate(const Money& total, const std::vector<std::int64_t>& weights) {
  if (weights.empty()) throw std::invalid_argument("allocate: empty weights");
  std::int64_t sum_w = 0;
  for (auto w : weights) {
    if (w < 0) throw std::invalid_argument("allocate: negative weight");
    sum_w += w;
  }
  if (sum_w == 0) throw std::invalid_argument("allocate: all weights zero");

  std::size_t n = weights.size();
  std::vector<Money> parts;
  parts.reserve(n);
  std::vector<std::pair<int128_t, std::size_t>> remainders; // (rem, idx)
  remainders.reserve(n);

  std::int64_t allocated_so_far = 0;
  for (std::size_t i = 0; i < n; ++i) {
    // Compute total.minor() * w_i using 128-bit intermediate.
    int128_t prod = static_cast<int128_t>(total.minor()) * static_cast<int128_t>(weights[i]);
    int128_t q = prod / sum_w;               // floor of the exact quotient (signs handled by C++ truncation)
    int128_t r = prod - q * sum_w;           // remainder
    // Push initial part (will be adjusted later for largest remainders).
    parts.push_back(Money::from_minor(static_cast<std::int64_t>(q), total.currency()));
    allocated_so_far += static_cast<std::int64_t>(q);
    remainders.emplace_back(r, i);
  }

  // Skill invariant: parts must sum to `total`. Distribute the residue (which
  // is total.minor() - allocated_so_far, a small non-negative number < n) to the
  // parts with the largest remainders. Ties: lowest index.
  std::int64_t residue = total.minor() - allocated_so_far;
  // If residue is positive, we need to bump that many parts by +1 minor.
  // If negative (possible if total was negative), we bump by -1 minor each.
  if (residue != 0) {
    std::sort(remainders.begin(), remainders.end(),
      [](const auto& a, const auto& b) {
        if (a.first != b.first) return a.first > b.first;
        return a.second < b.second; // tie: lower index first
      });
    std::int64_t step = residue > 0 ? 1 : -1;
    std::size_t count = static_cast<std::size_t>(residue > 0 ? residue : -residue);
    for (std::size_t k = 0; k < count; ++k) {
      std::size_t idx = remainders[k].second;
      std::int64_t cur = parts[idx].minor();
      // Overflow check (very small risk)
      if (step > 0 && cur > INT64_MAX - step) throw MoneyOverflow("allocate: part overflow");
      if (step < 0 && cur < INT64_MIN - step) throw MoneyOverflow("allocate: part underflow");
      parts[idx] = Money::from_minor(cur + step, total.currency());
    }
  }
  return parts;
}

std::vector<Money> allocate_equal(const Money& total, std::size_t count) {
  if (count == 0) throw std::invalid_argument("allocate_equal: count==0");
  std::vector<std::int64_t> w(count, 1);
  return allocate(total, w);
}

} // namespace fin
