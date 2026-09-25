// fin/ledger.cpp
#include "fin/ledger.hpp"
#include <sstream>

namespace fin {

bool entry_balances(const JournalEntry& e, std::string* err) {
  if (e.lines.empty()) {
    if (err) *err = "entry has no lines";
    return false;
  }
  CurrencyId cur = e.lines.front().amount.currency();
  Money debit_total  = Money::zero(cur);
  Money credit_total = Money::zero(cur);
  for (const auto& l : e.lines) {
    if (l.amount.currency() != cur) {
      if (err) *err = "multi-currency lines not allowed in a single entry";
      return false;
    }
    if (l.amount.is_negative()) {
      if (err) *err = "line amount must be non-negative";
      return false;
    }
    if (l.side == LedgerLineSide::Debit)  debit_total  += l.amount;
    else                                  credit_total += l.amount;
  }
  if (debit_total != credit_total) {
    if (err) {
      std::ostringstream os;
      os << "unbalanced: debit=" << debit_total.to_decimal_string()
         << " credit=" << credit_total.to_decimal_string();
      *err = os.str();
    }
    return false;
  }
  return true;
}

} // namespace fin
