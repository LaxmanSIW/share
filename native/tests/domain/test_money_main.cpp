// tests/domain/test_money_main.cpp — standalone main with plain assertions
// (when GoogleTest is unavailable in the sandbox; CI uses test_money.cpp).
#include "fin/money.hpp"
#include "fin/allocate.hpp"
#include "fin/ledger.hpp"

#include <cassert>
#include <chrono>
#include <iostream>
#include <string>

using namespace fin;

#define CHECK_EQ(a, b) do { \
  if (!((a) == (b))) { \
    std::cerr << "FAIL line " << __LINE__ << ": " #a " == " #b \
              << " (got: " << (a) << ", want: " << (b) << ")\n"; \
    std::exit(1); \
  } \
} while(0)

#define CHECK_THROWS(expr) do { \
  bool threw = false; \
  try { expr; } catch (...) { threw = true; } \
  if (!threw) { std::cerr << "FAIL line " << __LINE__ << ": " #expr " did not throw\n"; std::exit(1); } \
} while(0)

static int test_count = 0;
#define TEST(name) static void name(); \
  struct Register_##name { Register_##name() { test_count++; name(); } }; \
  static Register_##name reg_##name; \
  static void name()

// --- Parse ---
TEST(ParseRoundTrip) {
  auto m = Money::parse("1234.56", CurrencyId::INR);
  CHECK_EQ(m.minor(), 123456);
  CHECK_EQ(m.to_decimal_string(), "1234.56");
  CHECK_EQ(m.format(Money::Grouping::Western, true), "INR 1,234.56");
  CHECK_EQ(m.format(Money::Grouping::Indian,  true), "INR 1,234.56");
}

TEST(ParseNegative) {
  auto m = Money::parse("-1234.56", CurrencyId::INR);
  CHECK_EQ(m.minor(), -123456);
}

TEST(ParseJPYNoFraction) {
  auto m = Money::parse("12345", CurrencyId::JPY);
  CHECK_EQ(m.minor(), 12345);
}

TEST(ParseKWDFraction) {
  auto m = Money::parse("123.456", CurrencyId::KWD);
  CHECK_EQ(m.minor(), 123456);
}

TEST(ParseRoundExtra) {
  auto m = Money::parse_round("1.234", CurrencyId::INR, Rounding::HalfAwayFromZero);
  CHECK_EQ(m.minor(), 123);
}

TEST(ParseTrailingFractionNotAllowed) {
  CHECK_THROWS(Money::parse("1.234", CurrencyId::INR));
}

// --- Arithmetic ---
TEST(AddSameCurrency) {
  auto a = Money::parse("100.00", CurrencyId::INR);
  auto b = Money::parse("200.00", CurrencyId::INR);
  CHECK_EQ((a + b).minor(), 30000);
}

TEST(AddDifferentCurrencyThrows) {
  auto a = Money::parse("100.00", CurrencyId::INR);
  auto b = Money::parse("200.00", CurrencyId::USD);
  CHECK_THROWS((void)(a + b));
}

// --- Tax (skill §2 case) ---
TEST(ApplyTax18PctRoundPerLine) {
  auto one_paisa = Money::from_minor(1, CurrencyId::INR);
  auto tax = one_paisa.apply(Rate{18, 100}, Rounding::HalfAwayFromZero);
  CHECK_EQ(tax.minor(), 0);
}

TEST(ApplyTax18PctRoundOnTotal) {
  auto total = Money::from_minor(100, CurrencyId::INR);
  auto tax = total.apply(Rate{18, 100}, Rounding::HalfAwayFromZero);
  CHECK_EQ(tax.minor(), 18);
}

TEST(ApplyTaxHalfwayRounding) {
  // 25 paisa × 18% = 4.5 paisa exactly (remainder = 50 = denom/2 → true tie).
  auto base = Money::from_minor(25, CurrencyId::INR);
  CHECK_EQ(base.apply(Rate{18, 100}, Rounding::HalfAwayFromZero).minor(), 5);   // 4.5 → 5 (away from zero)
  CHECK_EQ(base.apply(Rate{18, 100}, Rounding::HalfEven).minor(),         4);   // 4.5 → 4 (4 is even)
  // Sanity check the 5-paisa non-tie case (both modes agree because 0.9 > 0.5).
  auto b2 = Money::from_minor(5, CurrencyId::INR);
  CHECK_EQ(b2.apply(Rate{18, 100}, Rounding::HalfAwayFromZero).minor(), 1);
  CHECK_EQ(b2.apply(Rate{18, 100}, Rounding::HalfEven).minor(),         1);
}

// --- Allocate ---
TEST(AllocateEqual100Into3) {
  auto total = Money::from_minor(100, CurrencyId::USD);
  auto parts = allocate_equal(total, 3);
  std::int64_t sum = 0;
  for (auto& p : parts) sum += p.minor();
  CHECK_EQ(sum, 100);
}

TEST(AllocateWeightsRatio) {
  auto total = Money::from_minor(1000, CurrencyId::INR);
  auto parts = allocate(total, {1, 2, 3});
  std::int64_t sum = 0;
  for (auto& p : parts) sum += p.minor();
  CHECK_EQ(sum, 1000);
}

TEST(AllocateNegativeTotal) {
  auto total = Money::from_minor(-100, CurrencyId::INR);
  auto parts = allocate(total, {1, 1, 1});
  std::int64_t sum = 0;
  for (auto& p : parts) sum += p.minor();
  CHECK_EQ(sum, -100);
}

TEST(AllocateEmptyThrows) {
  CHECK_THROWS(allocate(Money::from_minor(100, CurrencyId::INR), {}));
}

TEST(AllocateAllZeroThrows) {
  CHECK_THROWS(allocate(Money::from_minor(100, CurrencyId::INR), {0, 0, 0}));
}

// --- Ledger ---
TEST(LedgerBalancedOK) {
  JournalEntry e;
  e.id = TransactionId{1};
  e.accounting_date = std::chrono::year{2026}/9/25;
  e.lines.push_back({AccountId{1}, LedgerLineSide::Debit,  Money::from_minor(1000, CurrencyId::INR), "AR"});
  e.lines.push_back({AccountId{2}, LedgerLineSide::Credit, Money::from_minor(1000, CurrencyId::INR), "Sales"});
  CHECK_EQ(entry_balances(e), true);
}

TEST(LedgerUnbalancedFails) {
  JournalEntry e;
  e.id = TransactionId{2};
  e.accounting_date = std::chrono::year{2026}/9/25;
  e.lines.push_back({AccountId{1}, LedgerLineSide::Debit,  Money::from_minor(1000, CurrencyId::INR), "AR"});
  e.lines.push_back({AccountId{2}, LedgerLineSide::Credit, Money::from_minor( 999, CurrencyId::INR), "Sales"});
  std::string err;
  CHECK_EQ(entry_balances(e, &err), false);
  CHECK_EQ(err.find("unbalanced") != std::string::npos, true);
}

int main() {
  // Empty body: each TEST macro auto-registers and runs at static-init time.
  std::cout << "OK — " << test_count << " domain tests passed.\n";
  return 0;
}
