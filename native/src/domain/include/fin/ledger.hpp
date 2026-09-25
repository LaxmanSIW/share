// fin/ledger.hpp — Double-entry ledger invariants (skill §4: ledger model)
//
// Maps Java model: Transaction (entry), with debit/credit lines.
// Skill rules:
//   - Posted entries immutable; corrections are reversals
//   - Single line is either a debit OR a credit, never both
//   - Entries balance in base currency: sum(debit) == sum(credit)
//   - Posting = one DB transaction (entry + lines + balances + audit + number)
#pragma once
#include "fin/ids.hpp"
#include "fin/money.hpp"

#include <chrono>
#include <string>
#include <vector>
#include <cstdint>

namespace fin {

enum class LedgerLineSide : std::uint8_t { Debit = 0, Credit = 1 };

struct LedgerLine {
  AccountId  account_id;
  LedgerLineSide side;
  Money      amount;     // always in base currency; if multi-currency, orig_minor + fx_rate stored alongside
  std::string memo;
};

enum class EntryStatus : std::uint8_t { Draft = 0, Posted = 1, Reversed = 2 };

struct JournalEntry {
  TransactionId id;
  std::string   entry_no;       // gapless series; allocated in the posting transaction
  std::chrono::sys_days accounting_date;  // calendar date (skill §4.5)
  std::int64_t  period_id{0};
  std::string   memo;
  EntryStatus   status{EntryStatus::Draft};
  std::chrono::utc_clock::time_point created_at_utc{};  // audit instant (skill §4.5)
  UserId        created_by;
  std::optional<TransactionId> reversal_of; // if this entry reverses another
  std::int64_t  row_version{1};

  std::vector<LedgerLine> lines;
};

/// Verify that an entry's lines balance (skill §4 invariant 1).
/// Returns true if `sum(debit) == sum(credit)` in base currency AND no line
/// has both debit and credit non-zero AND all lines share the same currency.
bool entry_balances(const JournalEntry& e, std::string* err = nullptr);

} // namespace fin
