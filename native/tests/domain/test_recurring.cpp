// tests/domain/test_recurring.cpp — RecurringEngine tests
#include "fin/services/recurring.hpp"
#include "fin/model/bill.hpp"
#include "fin/model/enums.hpp"
#include "fin/app/formatters.hpp"

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

TEST(DailyFromBefore) {
  Bill tmpl;
  tmpl.id = "tmpl-1";
  tmpl.bill_no = "INV-2026-0001";
  tmpl.date = "2026-09-01";   // anchor
  tmpl.repeat = RepeatCadence::Daily;

  std::chrono::sys_days from = std::chrono::year{2026}/9/5;
  std::chrono::sys_days to   = std::chrono::year{2026}/9/8;

  auto bills = RecurringEngine::generate_due(tmpl, from, to);
  // 9/5, 9/6, 9/7, 9/8 → 4 bills
  CHECK_EQ(bills.size(), std::size_t{4});
  if (!bills.empty()) {
    CHECK_EQ(bills[0].date, std::string("2026-09-05"));
    CHECK_EQ(bills[3].date, std::string("2026-09-08"));
  }
  // Each bill has a draft id (empty) and a date-stamped bill_no.
  CHECK_EQ(bills[0].id.empty(), true);
  CHECK_EQ(bills[0].bill_no, std::string("INV-2026-0001-2026-09-05"));
}

TEST(WeeklyCadence) {
  Bill tmpl;
  tmpl.bill_no = "WEEKLY";
  tmpl.date = "2026-09-07";   // Monday (anchor)
  tmpl.repeat = RepeatCadence::Weekly;

  auto from = std::chrono::sys_days{std::chrono::year{2026}/9/1};
  auto to   = std::chrono::sys_days{std::chrono::year{2026}/9/30};
  auto bills = RecurringEngine::generate_due(tmpl, from, to);
  // 9/7, 9/14, 9/21, 9/28 → 4 bills
  CHECK_EQ(bills.size(), std::size_t{4});
}

TEST(MonthlyCadence) {
  Bill tmpl;
  tmpl.bill_no = "MONTHLY";
  tmpl.date = "2026-01-15";
  tmpl.repeat = RepeatCadence::Monthly;

  auto from = std::chrono::sys_days{std::chrono::year{2026}/2/1};
  auto to   = std::chrono::sys_days{std::chrono::year{2026}/5/31};
  auto bills = RecurringEngine::generate_due(tmpl, from, to);
  // 2/15, 3/15, 4/15, 5/15 → 4 bills
  CHECK_EQ(bills.size(), std::size_t{4});
}

TEST(RepeatSkipNextSkipsFirst) {
  Bill tmpl;
  tmpl.bill_no = "SKIP";
  tmpl.date = "2026-09-05";
  tmpl.repeat = RepeatCadence::Daily;
  tmpl.repeat_skip_next = true;

  auto from = std::chrono::sys_days{std::chrono::year{2026}/9/5};
  auto to   = std::chrono::sys_days{std::chrono::year{2026}/9/7};
  auto bills = RecurringEngine::generate_due(tmpl, from, to);
  // Skip 9/5 (the first due date); generate 9/6 + 9/7 = 2 bills
  CHECK_EQ(bills.size(), std::size_t{2});
  if (!bills.empty()) {
    CHECK_EQ(bills[0].date, std::string("2026-09-06"));
  }
}

TEST(RepeatEndDateCutoff) {
  Bill tmpl;
  tmpl.bill_no = "CUTOFF";
  tmpl.date = "2026-09-05";
  tmpl.repeat = RepeatCadence::Daily;
  tmpl.repeat_end_date = "2026-09-06";  // stop after 9/6

  auto from = std::chrono::sys_days{std::chrono::year{2026}/9/5};
  auto to   = std::chrono::sys_days{std::chrono::year{2026}/9/10};  // window goes past cutoff
  auto bills = RecurringEngine::generate_due(tmpl, from, to);
  // Should stop at 9/6 → only 9/5 + 9/6 = 2 bills
  CHECK_EQ(bills.size(), std::size_t{2});
}

TEST(NoRepeatReturnsEmpty) {
  Bill tmpl;
  tmpl.bill_no = "NONE";
  tmpl.date = "2026-09-05";
  tmpl.repeat = RepeatCadence::None;
  auto from = std::chrono::sys_days{std::chrono::year{2026}/9/5};
  auto to   = std::chrono::sys_days{std::chrono::year{2026}/9/10};
  auto bills = RecurringEngine::generate_due(tmpl, from, to);
  CHECK_EQ(bills.size(), std::size_t{0});
}

int main() {
  std::cout << "OK — " << test_count << " recurring tests passed.\n";
  return 0;
}
