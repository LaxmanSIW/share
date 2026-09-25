// fin/services/billing.hpp — BillingService (port of Java BillingService.java)
//
// Computes bill totals:
//   - Line totals: qty × rate (skill §1: 128-bit intermediate)
//   - Per-line discount: net = gross - gross × disc_pct
//   - Header discount: spread across lines via allocate() so parts sum exactly
//   - GST split: intra-state → CGST + SGST (rate/2 each); inter-state → IGST (rate)
//   - Round-off: total → nearest whole unit; difference goes to a Round off line
//
// Skill rules:
//   - Money is int64 minor units, no float
//   - Rounding mode is explicit at every call site (HalfAwayFromZero by default)
//   - allocate() for discount spreading (parts sum exactly)
//   - Pure function: no Qt, no DB; testable in milliseconds
#pragma once
#include "fin/model/bill.hpp"
#include "fin/money.hpp"

#include <optional>
#include <string>

namespace fin::services {

struct BillingRequest {
  const fin::model::Bill& bill;
  bool intra_state{true};          // true → CGST+SGST; false → IGST
  fin::Rounding rounding{fin::Rounding::HalfAwayFromZero};
};

class BillingService {
 public:
  /// Recompute totals in-place: subtotal, discount, taxable, cgst/sgst/igst,
  /// round_off, grand_total, total_qty, item_count. Sets amount_in_words.
  /// Idempotent: calling twice yields identical totals.
  /// Returns the computed grand total (also stored in bill.totals.grand_total).
  static fin::Money compute_totals(BillingRequest req);

  /// Returns the outstanding (grand - sum(payments)). Zero if bill is paid.
  static fin::Money outstanding(const fin::model::Bill& bill);

  /// Mark the bill as paid (sets status=Paid, paid_at=now). Returns true if
  /// the bill's outstanding was already zero (so PAID is valid).
  static bool mark_paid(fin::model::Bill& bill);

  /// Returns the next bill number in the series for the current user, e.g.
  /// "INV-2026-0001" if the last was "INV-2026-0000".
  /// NOTE: real gapless numbering must be allocated inside the posting
  /// transaction (skill §7); this helper is for drafts.
  static std::string next_draft_bill_no(const fin::model::Bill& template_bill);
};

} // namespace fin::services
