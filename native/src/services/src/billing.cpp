// fin/services/billing.cpp — Implementation
#include "fin/services/billing.hpp"
#include "fin/allocate.hpp"
#include "fin/app/formatters.hpp"

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <string>

namespace fin::services {

namespace {

/// Convert a percentage (0-100) to an exact-fraction Rate. e.g. 18.5 → {185, 1000}.
fin::Rate pct_to_rate(double pct) {
  if (pct < 0) pct = 0;
  if (pct > 100) pct = 100;
  // pct → exact-fraction Rate: 12.5% → {125, 1000} so 125/1000 = 0.125 = 12.5%.
  // Scale by 10 (not 1000) so {num, 1000} represents pct/100.
  std::int64_t num = static_cast<std::int64_t>(pct * 10.0 + 0.5);
  return fin::Rate{num, 1000};
}

} // namespace

fin::Money BillingService::compute_totals(BillingRequest req) {
  auto& bill = const_cast<fin::model::Bill&>(req.bill);
  auto cur = bill.totals.subtotal.currency();
  if (bill.items.empty()) {
    bill.totals = fin::model::BillTotals{};
    bill.totals.subtotal = fin::Money::zero(cur);
    return fin::Money::zero(cur);
  }

  // Step 1: per-line gross + net (no discount spreading yet).
  // Also compute the sum of all line nets for the header-discount spreading.
  std::vector<fin::Money> line_nets;
  line_nets.reserve(bill.items.size());
  fin::Money subtotal = fin::Money::zero(cur);
  fin::Money total_qty = fin::Money::zero(cur);
  for (auto& it : bill.items) {
    // Skill rule §2: line net already includes per-line discount via BillItem::net().
    auto n = it.net();
    line_nets.push_back(n);
    subtotal += n;
    total_qty += it.qty;
  }

  // Step 2: header discount spreads across lines (skill rule §2: use allocate()).
  // The Java original applied a flat `discountPct` to the subtotal; we do the same
  // but using exact fractions and allocate() to ensure the per-line discount sums
  // exactly to the header discount.
  auto header_disc_rate = pct_to_rate(bill.discount_pct);
  auto header_disc = subtotal.apply(header_disc_rate, req.rounding);
  // Spread header_disc across lines in proportion to their net values.
  // Skill note: never "fix the last line"; use allocate() instead.
  std::vector<fin::Money> line_discs;
  if (!header_disc.is_zero() && !line_nets.empty()) {
    std::vector<std::int64_t> weights;
    weights.reserve(line_nets.size());
    for (auto& n : line_nets) weights.push_back(std::max<std::int64_t>(0, n.minor()));
    line_discs = fin::allocate(header_disc, weights);
  } else {
    line_discs.assign(line_nets.size(), fin::Money::zero(cur));
  }

  // Taxable per line = line_nets[i] - line_discs[i]; GST = taxable × rate.
  fin::Money taxable_total = fin::Money::zero(cur);
  fin::Money cgst_total = fin::Money::zero(cur);
  fin::Money sgst_total = fin::Money::zero(cur);
  fin::Money igst_total = fin::Money::zero(cur);

  for (std::size_t i = 0; i < bill.items.size(); ++i) {
    auto taxable = line_nets[i] - line_discs[i];
    taxable_total += taxable;
    auto tax = taxable.apply(bill.items[i].gst_rate, req.rounding);
    if (req.intra_state) {
      // CGST + SGST = tax / 2 each. Use allocate to split exactly (handles odd amounts).
      auto halves = fin::allocate_equal(tax, 2);
      cgst_total += halves[0];
      sgst_total += halves[1];
    } else {
      igst_total += tax;
    }
  }

  // Compute grand total: taxable + cgst + sgst + igst; round to nearest whole
  // currency unit; the difference is the "Round off" line.
  auto raw_total = taxable_total + cgst_total + sgst_total + igst_total;
  // Round to whole currency units: divide by 10^exponent, round, multiply back.
  auto exp = fin::currency_of(cur).exponent;
  std::int64_t scale = 1;
  for (std::uint8_t i = 0; i < exp; ++i) scale *= 10;
  std::int64_t major = raw_total.minor() / scale;
  std::int64_t rem   = raw_total.minor() - major * scale;  // remainder
  bool neg = rem < 0;
  if (neg) rem = -rem;
  std::int64_t half = scale / 2;
  std::int64_t rounded_major = major;
  if (rem > half || (rem == half && req.rounding == fin::Rounding::HalfAwayFromZero)) {
    rounded_major += (neg ? -1 : 1);
  }
  std::int64_t rounded_minor = rounded_major * scale;
  auto round_off = fin::Money::from_minor(rounded_minor - raw_total.minor(), cur);
  auto grand_total = fin::Money::from_minor(rounded_minor, cur);

  bill.totals.subtotal    = subtotal;
  bill.totals.discount    = header_disc;
  bill.totals.taxable     = taxable_total;
  bill.totals.cgst        = cgst_total;
  bill.totals.sgst        = sgst_total;
  bill.totals.igst        = igst_total;
  bill.totals.round_off   = round_off;
  bill.totals.grand_total = grand_total;
  bill.totals.total_qty   = total_qty;
  bill.totals.item_count  = static_cast<int>(bill.items.size());

  return grand_total;
}

fin::Money BillingService::outstanding(const fin::model::Bill& bill) {
  auto grand = bill.totals.grand_total;
  fin::Money paid = fin::Money::zero(grand.currency());
  for (const auto& p : bill.payments) paid += p.amount;
  auto out = grand - paid;
  if (out.is_negative()) return fin::Money::zero(grand.currency());
  return out;
}

bool BillingService::mark_paid(fin::model::Bill& bill) {
  if (outstanding(bill).is_zero()) {
    bill.status = fin::model::BillStatus::Paid;
    bill.paid_at = fin::app::Formatters::iso_utc(std::chrono::utc_clock::now());
    return true;
  }
  return false;
}

std::string BillingService::next_draft_bill_no(const fin::model::Bill& template_bill) {
  // Format: <PREFIX>-YYYY-NNNN where NNNN is the next number.
  // Draft only — real gapless numbering must be allocated in the posting
  // transaction (skill §7).
  auto ymd = std::chrono::year_month_day{std::chrono::floor<std::chrono::days>(std::chrono::system_clock::now())};
  int year = static_cast<int>(ymd.year());
  char buf[16];
  std::snprintf(buf, sizeof(buf), "INV-%d-", year);
  std::string prefix = buf;
  // Append the bill's existing numeric suffix + 1, or start at 1.
  if (template_bill.bill_no.size() > prefix.size()) {
    auto suffix = template_bill.bill_no.substr(prefix.size());
    try {
      int n = std::stoi(suffix);
      std::snprintf(buf, sizeof(buf), "%04d", n + 1);
      return prefix + buf;
    } catch (...) {}
  }
  return prefix + "0001";
}

} // namespace fin::services
