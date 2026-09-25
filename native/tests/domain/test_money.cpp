// tests/domain/test_money.cpp — exhaustive rounding, parsing, allocation tests
#include "fin/money.hpp"
#include "fin/allocate.hpp"
#include "fin/ledger.hpp"

#include <gtest/gtest.h>
#include <rapidcheck.h>
#include <rapidcheck/gtest.h>

using namespace fin;

// === Parse + format round-trip ===

TEST(Money, ParseRoundTrip) {
  auto m = Money::parse("1234.56", CurrencyId::INR);
  EXPECT_EQ(m.minor(), 123456);
  EXPECT_EQ(m.to_decimal_string(), "1234.56");
  EXPECT_EQ(m.format(Money::Grouping::Western, true), "INR 1,234.56");
  EXPECT_EQ(m.format(Money::Grouping::Indian,  true), "INR 1,234.56");
  EXPECT_EQ(m.format(Money::Grouping::None,    true), "INR 1234.56");
}

TEST(Money, ParseNegative) {
  auto m = Money::parse("-1234.56", CurrencyId::INR);
  EXPECT_EQ(m.minor(), -123456);
  EXPECT_EQ(m.to_decimal_string(), "-1234.56");
}

TEST(Money, ParseZero) {
  auto m = Money::parse("0.00", CurrencyId::INR);
  EXPECT_TRUE(m.is_zero());
  EXPECT_FALSE(m.is_negative());
}

TEST(Money, ParseJPYNoFraction) {
  auto m = Money::parse("12345", CurrencyId::JPY);
  EXPECT_EQ(m.minor(), 12345);
  EXPECT_EQ(m.to_decimal_string(), "12345");
}

TEST(Money, ParseKWDFraction) {
  auto m = Money::parse("123.456", CurrencyId::KWD);
  EXPECT_EQ(m.minor(), 123456);
}

TEST(Money, ParseRoundExtra) {
  // 1.234 with INR exponent 2 → rounds to 1.23 (HalfAwayFromZero, since 4 < 5)
  auto m = Money::parse_round("1.234", CurrencyId::INR, Rounding::HalfAwayFromZero);
  EXPECT_EQ(m.minor(), 123);
  auto m2 = Money::parse_round("1.235", CurrencyId::INR, Rounding::HalfAwayFromZero);
  EXPECT_EQ(m2.minor(), 124);
}

TEST(Money, ParseTrailingFractionNotAllowed) {
  EXPECT_THROW(Money::parse("1.234", CurrencyId::INR), MoneyParseError);
}

// === Arithmetic + overflow ===

TEST(Money, AddSameCurrency) {
  auto a = Money::parse("100.00", CurrencyId::INR);
  auto b = Money::parse("200.00", CurrencyId::INR);
  EXPECT_EQ((a + b).minor(), 30000);
}

TEST(Money, AddDifferentCurrencyThrows) {
  auto a = Money::parse("100.00", CurrencyId::INR);
  auto b = Money::parse("200.00", CurrencyId::USD);
  EXPECT_THROW(a + b, MoneyMismatch);
}

TEST(Money, OverflowThrows) {
  auto big = Money::from_minor(INT64_MAX - 1, CurrencyId::INR);
  EXPECT_THROW(big + Money::from_minor(10, CurrencyId::INR), MoneyOverflow);
}

// === Apply rate (tax) — the §2 case from the skill ===

TEST(Money, ApplyTax18PctRoundPerLine) {
  // Skill §2: 100 lines of 1 paisa @ 18% → 0 per line (HalfAwayFromZero).
  auto one_paisa = Money::from_minor(1, CurrencyId::INR);
  auto tax = one_paisa.apply(Rate{18, 100}, Rounding::HalfAwayFromZero);
  EXPECT_EQ(tax.minor(), 0);
}

TEST(Money, ApplyTax18PctRoundOnTotal) {
  // 100 paisa @ 18% → 18 paisa on total (same mode, different aggregation point).
  auto total = Money::from_minor(100, CurrencyId::INR);
  auto tax = total.apply(Rate{18, 100}, Rounding::HalfAwayFromZero);
  EXPECT_EQ(tax.minor(), 18);
}

TEST(Money, ApplyTaxHalfwayRounding) {
  // 5 paisa @ 18% = 0.9 paisa → HalfAwayFromZero rounds to 1, HalfEven rounds to 0.
  auto base = Money::from_minor(5, CurrencyId::INR);
  EXPECT_EQ(base.apply(Rate{18, 100}, Rounding::HalfAwayFromZero).minor(), 1);
  EXPECT_EQ(base.apply(Rate{18, 100}, Rounding::HalfEven).minor(),         0);
}

// === Allocate — exact sum invariant ===

TEST(Allocate, PartsSumExactlyToTotal) {
  auto total = Money::from_minor(100, CurrencyId::INR);
  auto parts = allocate(total, {1, 1, 1});  // 3 equal parts of 100 → 33, 33, 34
  ASSERT_EQ(parts.size(), 3u);
  std::int64_t sum = parts[0].minor() + parts[1].minor() + parts[2].minor();
  EXPECT_EQ(sum, 100);
}

TEST(Allocate, Equal100Into3) {
  auto total = Money::from_minor(100, CurrencyId::USD);
  auto parts = allocate_equal(total, 3);
  // 100 / 3 = 33 remainder 1 → 34, 33, 33 (largest remainder, lowest index)
  std::int64_t sum = 0;
  for (auto& p : parts) sum += p.minor();
  EXPECT_EQ(sum, 100);
}

TEST(Allocate, WeightsRatio) {
  auto total = Money::from_minor(1000, CurrencyId::INR);
  auto parts = allocate(total, {1, 2, 3});  // 1+2+3=6; 1000/6 = 166r4
  // parts[0] = 166 +1 (r4 largest) = 167
  // parts[1] = 333 +1 = 334
  // parts[2] = 500 +1 = 501 → but only 4 remainders can be bumped, and 1000 - (166+333+500) = 1
  // Actually: 1000*1/6 = 166.66 → floor 166; 1000*2/6 = 333.33 → floor 333; 1000*3/6 = 500 → floor 500.
  // Sum = 999; residue = 1; bump the largest remainder (which is 1000*2 % 6 = 2 (from r=2))... see allocation.cpp.
  std::int64_t sum = 0;
  for (auto& p : parts) sum += p.minor();
  EXPECT_EQ(sum, 1000);
}

TEST(Allocate, NegativeTotal) {
  // Negative total → parts must sum to negative total.
  auto total = Money::from_minor(-100, CurrencyId::INR);
  auto parts = allocate(total, {1, 1, 1});
  std::int64_t sum = 0;
  for (auto& p : parts) sum += p.minor();
  EXPECT_EQ(sum, -100);
}

TEST(Allocate, EmptyWeightsThrows) {
  EXPECT_THROW(allocate(Money::from_minor(100, CurrencyId::INR), {}), std::invalid_argument);
}

TEST(Allocate, AllZeroWeightsThrows) {
  EXPECT_THROW(allocate(Money::from_minor(100, CurrencyId::INR), {0, 0, 0}), std::invalid_argument);
}

// === Property tests (RapidCheck) ===

RC_GTEST_PROP(Allocate, SumsToTotal, (std::int64_t total_minor, std::vector<std::int64_t> weights)) {
  RC_PRE(total_minor > 0 && total_minor < 1'000'000'000);
  RC_PRE(!weights.empty());
  for (auto w : weights) RC_PRE(w >= 0);
  std::int64_t sum_w = 0;
  for (auto w : weights) sum_w += w;
  RC_PRE(sum_w > 0);

  auto total = Money::from_minor(total_minor, CurrencyId::INR);
  auto parts = allocate(total, weights);
  std::int64_t sum = 0;
  for (auto& p : parts) sum += p.minor();
  RC_ASSERT(sum == total_minor);
}

RC_GTEST_PROP(Money, AddThenSubIsIdentity, (std::int64_t a, std::int64_t b)) {
  // a + b - b == a (overflow-protected)
  RC_PRE(a > INT64_MIN + 1000 && a < INT64_MAX - 1000);
  RC_PRE(b > INT64_MIN/2 && b < INT64_MAX/2);
  auto ma = Money::from_minor(a, CurrencyId::INR);
  auto mb = Money::from_minor(b, CurrencyId::INR);
  auto result = ((ma + mb) - mb);
  RC_ASSERT(result.minor() == a);
}

// === Ledger balance ===

TEST(Ledger, BalancedEntryOK) {
  JournalEntry e;
  e.id = TransactionId{1};
  e.accounting_date = std::chrono::year{2026}/9/25;
  e.lines.push_back({AccountId{1}, LedgerLineSide::Debit,  Money::from_minor(1000, CurrencyId::INR), "AR"});
  e.lines.push_back({AccountId{2}, LedgerLineSide::Credit, Money::from_minor(1000, CurrencyId::INR), "Sales"});
  EXPECT_TRUE(entry_balances(e));
}

TEST(Ledger, UnbalancedEntryFails) {
  JournalEntry e;
  e.id = TransactionId{2};
  e.accounting_date = std::chrono::year{2026}/9/25;
  e.lines.push_back({AccountId{1}, LedgerLineSide::Debit,  Money::from_minor(1000, CurrencyId::INR), "AR"});
  e.lines.push_back({AccountId{2}, LedgerLineSide::Credit, Money::from_minor( 999, CurrencyId::INR), "Sales"});
  std::string err;
  EXPECT_FALSE(entry_balances(e, &err));
  EXPECT_NE(err.find("unbalanced"), std::string::npos);
}

TEST(Ledger, EmptyEntryFails) {
  JournalEntry e;
  e.id = TransactionId{3};
  e.accounting_date = std::chrono::year{2026}/9/25;
  EXPECT_FALSE(entry_balances(e));
}

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
