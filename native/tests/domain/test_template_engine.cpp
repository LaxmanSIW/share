// tests/domain/test_template_engine.cpp — Template engine + negative-axis ruler tests
//
// USER REQUIREMENT #7: "in template design for ruler make sure it also can go
// to minus axis (only this change)". This test suite verifies the C++ port
// honours that requirement: ruler_ticks() must accept any range including
// negative values, and emit ticks at every 1mm in that range.
#include "fin/services/template_engine.hpp"

#include <cassert>
#include <chrono>
#include <iostream>
#include <string>
#include <vector>

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

// === USER REQUIREMENT #7: ruler supports negative axis ===

TEST(RulerPositiveRangeMatchesJavaOriginal) {
  // Java original: ruler_ticks(0, 100) → 101 ticks (0..100 inclusive)
  auto ticks = ruler_ticks(0, 100);
  CHECK_EQ(ticks.size(), std::size_t{101});
  CHECK_EQ(ticks[0].position_mm, std::int64_t{0});
  CHECK_EQ(ticks[100].position_mm, std::int64_t{100});
  // Major ticks at multiples of 10.
  CHECK_EQ(ticks[10].magnitude, std::int64_t{10});
  CHECK_EQ(ticks[50].magnitude, std::int64_t{10});
  CHECK_EQ(ticks[100].magnitude, std::int64_t{10});
  // Mid ticks at multiples of 5 (not also multiples of 10).
  CHECK_EQ(ticks[5].magnitude, std::int64_t{5});
  CHECK_EQ(ticks[25].magnitude, std::int64_t{5});
  CHECK_EQ(ticks[75].magnitude, std::int64_t{5});
  // Minor ticks everywhere else.
  CHECK_EQ(ticks[1].magnitude, std::int64_t{1});
  CHECK_EQ(ticks[7].magnitude, std::int64_t{1});
  CHECK_EQ(ticks[13].magnitude, std::int64_t{1});
}

TEST(RulerNegativeRangeIsAccepted) {
  // USER REQUIREMENT #7: ruler MUST accept negative ranges.
  auto ticks = ruler_ticks(-50, 50);
  CHECK_EQ(ticks.size(), std::size_t{101});
  CHECK_EQ(ticks[0].position_mm, std::int64_t{-50});
  CHECK_EQ(ticks[50].position_mm, std::int64_t{0});
  CHECK_EQ(ticks[100].position_mm, std::int64_t{50});
  // Major ticks at -40, -30, -20, -10, 0, 10, 20, 30, 40, 50.
  CHECK_EQ(ticks[10].magnitude, std::int64_t{10});  // position -40
  CHECK_EQ(ticks[50].magnitude, std::int64_t{10});  // position 0
  CHECK_EQ(ticks[100].magnitude, std::int64_t{10}); // position 50
}

TEST(RulerEntireNegativeRange) {
  // Pure-negative range (e.g. for templates that bleed off the top-left).
  auto ticks = ruler_ticks(-100, -1);
  CHECK_EQ(ticks.size(), std::size_t{100});
  CHECK_EQ(ticks[0].position_mm, std::int64_t{-100});
  CHECK_EQ(ticks[99].position_mm, std::int64_t{-1});
  CHECK_EQ(ticks[0].magnitude, std::int64_t{10});   // -100
  CHECK_EQ(ticks[50].magnitude, std::int64_t{10}); // -50
  CHECK_EQ(ticks[90].magnitude, std::int64_t{10}); // -10
}

TEST(RulerZeroIsMajorTickRegardlessOfRange) {
  // Zero is always a major tick (10 magnitude) — even when it's in a range
  // that includes negatives. This is what makes the negative axis visually
  // distinct: 0 is the gold origin line on the ruler.
  {
    auto ticks = ruler_ticks(-5, 5);
    // position 0 is at index 5
    CHECK_EQ(ticks[5].position_mm, std::int64_t{0});
    CHECK_EQ(ticks[5].magnitude, std::int64_t{10});
  }
  {
    auto ticks = ruler_ticks(-25, -5);
    // 0 is NOT in this range, but the major-tick math still works.
    // position -10 at index 15 should be major.
    CHECK_EQ(ticks[15].position_mm, std::int64_t{-10});
    CHECK_EQ(ticks[15].magnitude, std::int64_t{10});
  }
}

TEST(RulerSwapsWhenFromGreaterThanTo) {
  // Defensive: if caller passes (100, 0), we should swap to (0, 100).
  auto ticks = ruler_ticks(100, 0);
  CHECK_EQ(ticks.size(), std::size_t{101});
  CHECK_EQ(ticks[0].position_mm, std::int64_t{0});
  CHECK_EQ(ticks[100].position_mm, std::int64_t{100});
}

// === Layout engine ===

TEST(LayoutEmptyTemplateReturnsEmpty) {
  Template t;
  LayoutInput in; in.tpl = &t;
  auto r = run_layout(in);
  CHECK_EQ(r.display_list.size(), std::size_t{0});
}

TEST(LayoutTextEmitsGlyphRun) {
  Template t;
  TemplateElement e;
  e.type = ElementType::Text;
  e.text = "Hello";
  e.bounds.origin.x = Fixed::from_mm(10);
  e.bounds.origin.y = Fixed::from_mm(20);
  t.elements.push_back(e);
  LayoutInput in; in.tpl = &t;
  auto r = run_layout(in);
  CHECK_EQ(r.display_list.size(), std::size_t{1});
  CHECK_EQ(static_cast<int>(r.display_list[0].op), static_cast<int>(DisplayOp::DrawGlyphRun));
}

TEST(LayoutInvisibleElementSkipped) {
  Template t;
  TemplateElement e;
  e.type = ElementType::Text;
  e.text = "Hidden";
  e.visible = false;
  t.elements.push_back(e);
  LayoutInput in; in.tpl = &t;
  auto r = run_layout(in);
  CHECK_EQ(r.display_list.size(), std::size_t{0});
}

TEST(LayoutRectangleEmitsFillRect) {
  Template t;
  TemplateElement e;
  e.type = ElementType::Rectangle;
  e.bounds = Rect{Point{Fixed::from_mm(10), Fixed::from_mm(10)}, Size{Fixed::from_mm(50), Fixed::from_mm(30)}};
  t.elements.push_back(e);
  LayoutInput in; in.tpl = &t;
  auto r = run_layout(in);
  CHECK_EQ(r.display_list.size(), std::size_t{1});
  CHECK_EQ(static_cast<int>(r.display_list[0].op), static_cast<int>(DisplayOp::FillRect));
}

TEST(LayoutExpressionResolvedFromData) {
  Template t;
  TemplateElement e;
  e.type = ElementType::Text;
  e.expression = "invoice.buyer.name";
  t.elements.push_back(e);
  LayoutInput in;
  in.tpl = &t;
  in.data["invoice.buyer.name"] = "Acme Corp";
  auto r = run_layout(in);
  // Should emit a glyph run with the resolved text.
  CHECK_EQ(r.display_list.size(), std::size_t{1});
}

TEST(LayoutExpressionMissingProducesWarning) {
  Template t;
  TemplateElement e;
  e.type = ElementType::Text;
  e.expression = "invoice.nonexistent";
  t.elements.push_back(e);
  LayoutInput in;
  in.tpl = &t;
  auto r = run_layout(in);
  CHECK_EQ(r.warnings.size() >= std::size_t{1}, true);
}

int main() {
  std::cout << "OK — " << test_count << " template-engine tests passed.\n";
  return 0;
}
