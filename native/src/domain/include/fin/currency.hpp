// fin/currency.hpp — Currency code + exponent (skill §1: money as int64 minor units)
// Skill rule §1: strong types, no implicit conversions. CurrencyCode is a distinct
// type so `Money(INR) + Money(USD)` fails to compile.
#pragma once
#include <cstdint>
#include <string_view>
#include <array>

namespace fin {

/// ISO 4217 currency code, stored as a 3-letter upper-case string.
/// Interned by index for fast equality (single u8).
enum class CurrencyId : std::uint8_t {
  INR = 0,  // Indian rupee, exponent 2
  USD = 1,  // US dollar,    exponent 2
  EUR = 2,  // Euro,         exponent 2
  GBP = 3,  // Pound,        exponent 2
  JPY = 4,  // Japanese yen, exponent 0
  KWD = 5,  // Kuwaiti dinar,exponent 3
  AED = 6,  // UAE dirham,   exponent 2
  SGD = 7,  // Singapore $,  exponent 2
  AUD = 8,  // Australian $, exponent 2
  CAD = 9,  // Canadian $,   exponent 2
  Custom = 254,
  Unknown = 255
};

struct CurrencyInfo {
  CurrencyId id;
  std::string_view code;   // 3-letter ISO code, e.g. "INR"
  std::uint8_t exponent;   // minor units per major (INR=2 → 100 paise per rupee)
  std::string_view symbol; // "₹", "$", "€", ...
};

/// Lookup table (statically initialised, no allocations).
/// To add a currency: append to `currencies()` below; the rest is automatic.
const std::array<CurrencyInfo, 10>& currencies();

/// Find a currency by ISO code. Returns `Unknown` if not found.
CurrencyInfo find_currency(std::string_view code) noexcept;

/// Find a currency by id.
const CurrencyInfo& currency_of(CurrencyId id) noexcept;

} // namespace fin
