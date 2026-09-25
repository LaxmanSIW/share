// fin/services/recurring.cpp
#include "fin/services/recurring.hpp"
#include "fin/app/formatters.hpp"

#include <chrono>
#include <string>
#include <unordered_set>

namespace fin::services {

namespace {

/// Returns the next date after `current` for the given cadence.
/// Returns sys_days{} if the cadence is None.
/// Implementation note: we use sys_days arithmetic (days added directly) for
/// Daily/Weekly, and calendar arithmetic for Monthly/Quarterly/Yearly with a
/// fallback to sys_days approximation when the resulting date is invalid
/// (e.g. Jan 31 + 1 month = Feb 31, which doesn't exist).
std::chrono::sys_days advance(std::chrono::sys_days current, fin::model::RepeatCadence cad) {
  using namespace std::chrono;
  switch (cad) {
    case fin::model::RepeatCadence::None:      return sys_days{};
    case fin::model::RepeatCadence::Daily:     return current + days{1};
    case fin::model::RepeatCadence::Weekly:    return current + days{7};
    case fin::model::RepeatCadence::Monthly: {
      auto ymd = year_month_day{current};
      // Construct next month's same day. If invalid (e.g. Jan 31 → Feb 31),
      // fall back to 30-day approximation.
      auto next = year_month_day{ymd.year(), ymd.month() + months{1}, ymd.day()};
      if (!next.ok()) {
        return current + days{30};
      }
      return sys_days{next};
    }
    case fin::model::RepeatCadence::Quarterly: {
      auto ymd = year_month_day{current};
      auto next = year_month_day{ymd.year(), ymd.month() + months{3}, ymd.day()};
      if (!next.ok()) return current + days{91};
      return sys_days{next};
    }
    case fin::model::RepeatCadence::Yearly: {
      auto ymd = year_month_day{current};
      auto next = year_month_day{ymd.year() + years{1}, ymd.month(), ymd.day()};
      if (!next.ok()) return current + days{365};
      return sys_days{next};
    }
  }
  return sys_days{};
}

/// Parse ISO yyyy-MM-dd into sys_days. Returns sys_days{} on failure.
std::chrono::sys_days parse_iso(std::string_view s) {
  std::chrono::sys_days out;
  if (!fin::app::Formatters::parse_iso_date(s, out)) return {};
  return out;
}

} // namespace

std::vector<fin::model::Bill> RecurringEngine::generate_due(
    const fin::model::Bill& template_bill,
    std::chrono::sys_days from,
    std::chrono::sys_days to) {
  std::vector<fin::model::Bill> result;
  if (template_bill.repeat == fin::model::RepeatCadence::None) return result;

  // Parse the template's date; the cadence is anchored at that date.
  auto anchor = parse_iso(template_bill.date);
  if (anchor == std::chrono::sys_days{}) anchor = from;

  // Parse repeat_end_date (if any) to stop generation.
  std::chrono::sys_days end_cutoff = to;
  if (!template_bill.repeat_end_date.empty()) {
    auto er = parse_iso(template_bill.repeat_end_date);
    if (er != std::chrono::sys_days{}) end_cutoff = er;
  }
  if (end_cutoff > to) end_cutoff = to;

  // Walk from the anchor forward at the cadence, emitting bills whose
  // effective date falls inside [from, to]. Skip the very first occurrence
  // if `repeatSkipNext` is set (one-time pause).
  bool skip_next = template_bill.repeat_skip_next;
  auto current = anchor;
  std::unordered_set<std::string> emitted_keys; // dedupe by date

  // If anchor is before `from`, walk forward to the first due date in [from, to].
  while (current < from) {
    current = advance(current, template_bill.repeat);
    if (current == std::chrono::sys_days{}) return result; // None cadence
  }

  // Cap iterations to prevent runaway loops on bad input.
  for (int i = 0; i < 365 * 4 && current <= end_cutoff; ++i) {
    if (skip_next) {
      skip_next = false;
      current = advance(current, template_bill.repeat);
      continue;
    }
    if (current >= from && current <= end_cutoff) {
      std::string date_str = fin::app::Formatters::iso_date(current);
      if (emitted_keys.insert(date_str).second) {
        fin::model::Bill b = template_bill;
        b.id.clear();   // new bill, not the template
        b.date = date_str;
        b.bill_no = template_bill.bill_no + "-" + date_str;
        b.print_count = 0;
        b.status = fin::model::BillStatus::Unpaid;
        b.paid_at.clear();
        b.payments.clear();
        b.created_at.clear();
        b.updated_at.clear();
        // Reset totals — they'll be recomputed when the user finalises.
        b.totals = fin::model::BillTotals{};
        result.push_back(std::move(b));
      }
    }
    current = advance(current, template_bill.repeat);
    if (current == std::chrono::sys_days{}) break;
  }
  return result;
}

} // namespace fin::services
