// fin/currency.cpp
#include "fin/currency.hpp"
#include <algorithm>
#include <array>

namespace fin {

namespace {
constexpr std::array<CurrencyInfo, 10> kCurrencies{{
  {CurrencyId::INR, "INR", 2, "\u20B9"}, // ₹
  {CurrencyId::USD, "USD", 2, "$"},
  {CurrencyId::EUR, "EUR", 2, "\u20AC"}, // €
  {CurrencyId::GBP, "GBP", 2, "\u00A3"}, // £
  {CurrencyId::JPY, "JPY", 0, "\u00A5"}, // ¥
  {CurrencyId::KWD, "KWD", 3, "\u062F.\u0643"},
  {CurrencyId::AED, "AED", 2, "\u062F.\u0625"},
  {CurrencyId::SGD, "SGD", 2, "S$"},
  {CurrencyId::AUD, "AUD", 2, "A$"},
  {CurrencyId::CAD, "CAD", 2, "C$"},
}};
}

const std::array<CurrencyInfo, 10>& currencies() { return kCurrencies; }

CurrencyInfo find_currency(std::string_view code) noexcept {
  for (const auto& c : kCurrencies) {
    if (c.code.size() == code.size()) {
      bool match = true;
      for (std::size_t i = 0; i < code.size(); ++i) {
        char a = static_cast<char>(code[i]);
        char b = static_cast<char>(c.code[i]);
        if (a >= 'a' && a <= 'z') a = static_cast<char>(a - 32);
        if (b >= 'a' && b <= 'z') b = static_cast<char>(b - 32);
        if (a != b) { match = false; break; }
      }
      if (match) return c;
    }
  }
  return {CurrencyId::Unknown, "???", 2, ""};
}

const CurrencyInfo& currency_of(CurrencyId id) noexcept {
  for (const auto& c : kCurrencies) {
    if (c.id == id) return c;
  }
  static const CurrencyInfo unknown{CurrencyId::Unknown, "???", 2, ""};
  return unknown;
}

} // namespace fin
