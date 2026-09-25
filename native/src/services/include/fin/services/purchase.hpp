// fin/services/purchase.hpp — PurchaseService (port of Java PurchaseService.java)
//
// Computes purchase bill totals + ITC (input tax credit) eligibility.
// ITC = the GST paid on purchases that the business can claim back from the
// government, offsetting its output tax liability.
#pragma once
#include "fin/model/purchase_stock.hpp"
#include "fin/model/bill.hpp"

namespace fin::services {

class PurchaseService {
 public:
  /// Compute totals for a purchase bill: subtotal, GST, ITC, grand total.
  /// Same shape as BillingService but operating on PurchaseBill.
  /// ITC = sum of all GST paid on line items (since we're claiming it back).
  static fin::Money compute_totals(fin::model::PurchaseBill& bill, fin::Rounding mode = fin::Rounding::HalfAwayFromZero);

  /// Mark a purchase bill as posted (status = "posted"). Returns the bill's
  /// posting id (used by StockLedgerDao::append for the purchase voucher).
  static bool mark_posted(fin::model::PurchaseBill& bill);

  /// Compute the supplier's outstanding (total − paid).
  static fin::Money outstanding(const fin::model::PurchaseBill& bill);
};

} // namespace fin::services
