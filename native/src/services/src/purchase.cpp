// fin/services/purchase.cpp
#include "fin/services/purchase.hpp"
#include "fin/app/formatters.hpp"

#include <chrono>
#include <string>

namespace fin::services {

namespace {
/// pct → exact-fraction Rate: 12.5% → {125, 1000} so 125/1000 = 0.125 = 12.5%.
fin::Rate pct_to_rate(double pct) {
  if (pct < 0) pct = 0;
  if (pct > 100) pct = 100;
  std::int64_t num = static_cast<std::int64_t>(pct * 10.0 + 0.5);
  return fin::Rate{num, 1000};
}
} // namespace

fin::Money PurchaseService::compute_totals(fin::model::PurchaseBill& bill, fin::Rounding mode) {
  auto cur = bill.total.currency();
  fin::Money subtotal = fin::Money::zero(cur);
  fin::Money itc      = fin::Money::zero(cur);
  for (auto& it : bill.items) {
    auto gross = it.gross();
    auto net = it.net();
    subtotal += net;
    // ITC = GST paid on the purchase (we'll claim it back).
    auto tax = net.apply(it.gst_rate, mode);
    itc += tax;
  }
  bill.total = subtotal + itc;
  bill.itc = itc;
  bill.due = bill.total - bill.paid;
  if (bill.due.is_negative()) bill.due = fin::Money::zero(cur);
  return bill.total;
}

bool PurchaseService::mark_posted(fin::model::PurchaseBill& bill) {
  bill.status = "posted";
  return true;
}

fin::Money PurchaseService::outstanding(const fin::model::PurchaseBill& bill) {
  auto out = bill.total - bill.paid;
  return out.is_negative() ? fin::Money::zero(bill.total.currency()) : out;
}

} // namespace fin::services
