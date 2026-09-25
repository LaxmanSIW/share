// fin/model/purchase_stock.cpp — enum code <-> string for StockVoucherType
#include "fin/model/purchase_stock.hpp"
#include <cstring>
#include <cctype>

namespace fin::model {

namespace {
bool eq_ci(std::string_view a, const char* b) noexcept {
  if (a.size() != std::strlen(b)) return false;
  for (std::size_t i = 0; i < a.size(); ++i) {
    char ca = static_cast<char>(std::tolower(static_cast<unsigned char>(a[i])));
    char cb = static_cast<char>(std::tolower(static_cast<unsigned char>(b[i])));
    if (ca != cb) return false;
  }
  return true;
}
} // namespace

const char* code(StockVoucherType v) noexcept {
  switch (v) {
    case StockVoucherType::Purchase:  return "PURCHASE";
    case StockVoucherType::Sale:       return "SALE";
    case StockVoucherType::DebitNote:  return "DEBIT_NOTE";
    case StockVoucherType::CreditNote: return "CREDIT_NOTE";
    case StockVoucherType::Adjustment: return "ADJUSTMENT";
  }
  return "ADJUSTMENT";
}
StockVoucherType stock_voucher_type_from_code(std::string_view s) noexcept {
  if (eq_ci(s, "PURCHASE"))    return StockVoucherType::Purchase;
  if (eq_ci(s, "SALE"))        return StockVoucherType::Sale;
  if (eq_ci(s, "DEBIT_NOTE"))  return StockVoucherType::DebitNote;
  if (eq_ci(s, "CREDIT_NOTE")) return StockVoucherType::CreditNote;
  return StockVoucherType::Adjustment;
}

} // namespace fin::model
