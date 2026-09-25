// fin/model/bill.cpp — Money-based bill math (replaces Java's double math)
//
// Skill §1: no float/double for money. The Java original used double for
// qty/rate/totals and accumulated binary error. Here we use fin::Money
// (int64 minor + 128-bit intermediate) for all amounts, and fin::Rate for
// tax fractions. Rounding mode is explicit at every call site.
#include "fin/model/bill.hpp"
#include "fin/money.hpp"
#include "fin/currency.hpp"

namespace fin::model {

fin::Money BillItem::gross() const {
  // qty is stored as Money at the currency's natural scale (e.g. 1e-2 for INR).
  // rate is Money at the currency's scale.
  // gross = qty × rate = (qty.minor() * rate.minor()) / 10^exponent
  // Money::scale(multiplier, extra_digits, mode) computes:
  //   this.minor() * multiplier / 10^extra_digits, with explicit rounding.
  auto exp = fin::currency_of(rate.currency()).exponent;
  return rate.scale(qty.minor(), exp, fin::Rounding::HalfAwayFromZero);
}

fin::Money BillItem::net() const {
  auto g = gross();
  if (disc_pct <= 0.0) return g;
  double d = disc_pct > 100.0 ? 100.0 : disc_pct;
  // Convert percentage to a Rate: 12.5% → {125, 1000} so 125/1000 = 0.125 = 12.5%.
  // Scale by 10 (not 1000) so {num, 1000} represents pct/100.
  std::int64_t num = static_cast<std::int64_t>(d * 10.0 + 0.5);
  fin::Rate disc_rate{num, 1000};
  auto discount = g.apply(disc_rate, fin::Rounding::HalfAwayFromZero);
  return g - discount;
}

} // namespace fin::model
