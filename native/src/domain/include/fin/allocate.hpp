// fin/allocate.hpp — Largest-remainder allocation (skill §2: parts sum exactly to total)
//
// Used for:
//   - Splitting a discount/invoice total across N lines (parts sum exactly to total)
//   - Splitting a tax amount across invoice lines at the same rate
//   - Splitting an invoice total into instalments by weights
//   - Currency conversion residue spread
//
// Algorithm: each part = floor(total * w_i / sum(w))  with the largest
// remainders bumped up by 1 until the parts sum to total. Ties broken by
// lowest index (deterministic, matches the Java original's behaviour).
#pragma once
#include "fin/money.hpp"
#include <vector>
#include <cstdint>

namespace fin {

/// Allocate `total` into `weights.size()` parts proportional to `weights`.
/// Returns parts that sum exactly to `total`. Weights must be non-negative
/// and at least one must be non-zero. Throws on empty weights or all-zero.
///
/// Skill note: we do NOT "fix the last line" — that approach (computing all
/// but the last part normally, then setting the last = total - sum(others))
/// hides rounding-policy mistakes. Largest-remainder is the correct method.
std::vector<Money> allocate(const Money& total, const std::vector<std::int64_t>& weights);

/// Allocate `total` into `count` equal parts. Convenience wrapper for
/// instalment splitting.
std::vector<Money> allocate_equal(const Money& total, std::size_t count);

} // namespace fin
