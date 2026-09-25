// tests/domain/test_billing.cpp — BillingService unit tests (pure C++, no Qt, no DB)
#include "fin/services/billing.hpp"
#include "fin/money.hpp"
#include "fin/allocate.hpp"
#include "fin/model/bill.hpp"
#include "fin/model/enums.hpp"

#include <cassert>
#include <chrono>
#include <iostream>
#include <string>

using namespace fin;
using namespace fin::model;
using namespace fin::services;

#define CHECK_EQ(a, b) do { \
  if (!((a) == (b))) { \
    std::cerr << "FAIL line " << __LINE__ << ": " #a " == " #b \
              << " (got: " << (a) << ", want: " << (b) << ")\n"; \
    std::exit(1); \
  } \
} while(0)

static int test_count = 0;
#define TEST(name) static void name(); \
  struct Register_##name { Register_##name() { test_count++; name(); } }; \
  static Register_##name reg_##name; \
  static void name()

// === Line totals: qty × rate ===
TEST(LineGrossSimple) {
  BillItem it;
  it.qty = Money::parse("5.00", CurrencyId::INR);
  it.rate = Money::parse("100.00", CurrencyId::INR);
  // 5 × 100 = 500.00 = 50000 minor
  CHECK_EQ(it.gross().minor(), 50000);
}

TEST(LineGrossFractionalQty) {
  BillItem it;
  it.qty = Money::parse("2.50", CurrencyId::INR);
  it.rate = Money::parse("40.00", CurrencyId::INR);
  // 2.5 × 40 = 100.00
  CHECK_EQ(it.gross().minor(), 10000);
}

TEST(LineDiscount) {
  BillItem it;
  it.qty = Money::parse("10.00", CurrencyId::INR);
  it.rate = Money::parse("100.00", CurrencyId::INR);
  it.disc_pct = 10.0;  // 10% discount
  // gross = 1000.00, net = 900.00
  CHECK_EQ(it.gross().minor(), 100000);
  CHECK_EQ(it.net().minor(), 90000);
}

// === Bill totals: simple invoice, intra-state GST ===
TEST(SimpleInvoiceIntraState) {
  Bill bill;
  bill.id = "test-1";
  bill.bill_no = "INV-2026-0001";
  bill.date = "2026-09-25";
  BillItem it;
  it.qty = Money::parse("1.00", CurrencyId::INR);
  it.rate = Money::parse("1000.00", CurrencyId::INR);
  it.gst_rate = Rate{18, 100};
  bill.items.push_back(it);

  BillingService::compute_totals({bill, true, Rounding::HalfAwayFromZero});

  // subtotal = 1000.00, taxable = 1000.00, CGST = 90.00, SGST = 90.00, grand = 1180.00
  CHECK_EQ(bill.totals.subtotal.minor(),    100000);
  CHECK_EQ(bill.totals.taxable.minor(),      100000);
  CHECK_EQ(bill.totals.cgst.minor(),         9000);
  CHECK_EQ(bill.totals.sgst.minor(),         9000);
  CHECK_EQ(bill.totals.igst.minor(),         0);
  CHECK_EQ(bill.totals.grand_total.minor(),  118000);
  CHECK_EQ(bill.totals.item_count,           1);
}

// === Inter-state: IGST instead of CGST+SGST ===
TEST(SimpleInvoiceInterState) {
  Bill bill;
  bill.id = "test-2";
  BillItem it;
  it.qty = Money::parse("1.00", CurrencyId::INR);
  it.rate = Money::parse("1000.00", CurrencyId::INR);
  it.gst_rate = Rate{18, 100};
  bill.items.push_back(it);

  BillingService::compute_totals({bill, false, Rounding::HalfAwayFromZero});

  CHECK_EQ(bill.totals.cgst.minor(),  0);
  CHECK_EQ(bill.totals.sgst.minor(),  0);
  CHECK_EQ(bill.totals.igst.minor(), 18000); // 18% of 1000
  CHECK_EQ(bill.totals.grand_total.minor(), 118000);
}

// === Round-off: grand total rounds to nearest whole rupee ===
TEST(RoundOffWholeRupee) {
  Bill bill;
  BillItem it;
  it.qty = Money::parse("1.00", CurrencyId::INR);
  it.rate = Money::parse("100.55", CurrencyId::INR);  // gross = 100.55
  it.gst_rate = Rate{18, 100};                         // tax = 18.099 → 18.10
  bill.items.push_back(it);

  BillingService::compute_totals({bill, true, Rounding::HalfAwayFromZero});

  // taxable = 100.55, CGST = 9.05, SGST = 9.05; raw_total = 118.65 → rounds to 119.00
  // round_off = 0.35 paisa (added)
  CHECK_EQ(bill.totals.grand_total.minor(), 11900);
  CHECK_EQ(bill.totals.round_off.minor(), 35);  // 119.00 - 118.65 = 0.35 → +35 minor
}

// === Outstanding balance ===
TEST(OutstandingAfterPartialPayment) {
  Bill bill;
  BillItem it;
  it.qty = Money::parse("1.00", CurrencyId::INR);
  it.rate = Money::parse("1000.00", CurrencyId::INR);
  it.gst_rate = Rate{18, 100};
  bill.items.push_back(it);
  BillingService::compute_totals({bill, true, Rounding::HalfAwayFromZero});

  BillPayment p;
  p.amount = Money::parse("500.00", CurrencyId::INR);
  bill.payments.push_back(p);

  auto out = BillingService::outstanding(bill);
  // grand = 1180.00, paid = 500.00, outstanding = 680.00
  CHECK_EQ(out.minor(), 68000);
}

// === Mark paid ===
TEST(MarkPaidWhenFullyPaid) {
  Bill bill;
  BillItem it;
  it.qty = Money::parse("1.00", CurrencyId::INR);
  it.rate = Money::parse("1000.00", CurrencyId::INR);
  it.gst_rate = Rate{18, 100};
  bill.items.push_back(it);
  BillingService::compute_totals({bill, true, Rounding::HalfAwayFromZero});

  BillPayment p;
  p.amount = bill.totals.grand_total;
  bill.payments.push_back(p);

  CHECK_EQ(BillingService::mark_paid(bill), true);
  CHECK_EQ(static_cast<int>(bill.status),  static_cast<int>(BillStatus::Paid));
}

TEST(MarkPaidFailsIfOutstanding) {
  Bill bill;
  BillItem it;
  it.qty = Money::parse("1.00", CurrencyId::INR);
  it.rate = Money::parse("1000.00", CurrencyId::INR);
  it.gst_rate = Rate{18, 100};
  bill.items.push_back(it);
  BillingService::compute_totals({bill, true, Rounding::HalfAwayFromZero});

  // No payment yet → mark_paid fails.
  CHECK_EQ(BillingService::mark_paid(bill), false);
  CHECK_EQ(static_cast<int>(bill.status),  static_cast<int>(BillStatus::Unpaid));
}

int main() {
  std::cout << "OK — " << test_count << " billing tests passed.\n";
  return 0;
}
