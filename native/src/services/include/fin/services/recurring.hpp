// fin/services/recurring.hpp — RecurringEngine (port of Java RecurringEngine.java)
//
// Generates repeated bills from a "template" bill with a RepeatCadence.
// Skill rule §7 (accounting): gapless numbering is allocated inside the
// posting transaction — but for a *recurring engine* we generate DRAFT bills
// (with a draft bill_no); the actual gapless number is allocated when the
// user finalises the bill in CreateBillView.
#pragma once
#include "fin/model/bill.hpp"

#include <chrono>
#include <vector>

namespace fin::services {

class RecurringEngine {
 public:
  /// Generate all due occurrences of the recurring template bill between
  /// `from` and `to`. Returns draft bills (status=Unpaid, bill_no = template
  /// bill_no + date suffix). The user is expected to review and finalise each.
  ///
  /// Skill rule §3.5 (responsive-ui §3.5): the engine is a pure function —
  /// the UI can run it off-thread and show drafts progressively.
  static std::vector<fin::model::Bill> generate_due(
      const fin::model::Bill& template_bill,
      std::chrono::sys_days from,
      std::chrono::sys_days to);
};

} // namespace fin::services
