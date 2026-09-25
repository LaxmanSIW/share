// fin/model/purchase_stock.hpp — Purchase + Stock ledger models
//
// Mirrors Java model: PurchaseBill (supplier inward supply voucher) +
// StockLedger (immutable append-only movement history).
#pragma once
#include "fin/money.hpp"
#include "fin/ids.hpp"
#include "fin/model/enums.hpp"
#include "fin/model/bill.hpp"  // for BillItem (PurchaseBill reuses line items)

#include <map>
#include <string>
#include <vector>
#include <chrono>

namespace fin::model {

struct PurchaseBill {
  std::string id;
  std::string bill_no;
  std::string supplier_bill_no;   // supplier's own bill number
  std::string date;               // ISO yyyy-MM-dd
  std::string supplier_id;
  std::string supplier_name;
  // Items: line-by-line as BillItem (reuse), but with GST for ITC eligibility
  std::vector<BillItem> items;
  fin::Money  total{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money  itc{fin::Money::zero(fin::CurrencyId::INR)};   // input tax credit
  std::string status;              // "draft" / "posted" / "cancelled"
  std::string notes;
  fin::Money  paid{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money  due{fin::Money::zero(fin::CurrencyId::INR)};
  std::string created_at;
  std::string updated_at;

  // JSON-ready extra fields (transport, e-way bill, etc.)
  std::map<std::string, std::string> variables;
};

/// Stock movement voucher types. Matches Java StockLedgerDao constants.
enum class StockVoucherType : std::uint8_t {
  Purchase = 0,    // qty_in
  Sale = 1,        // qty_out
  DebitNote = 2,   // qty_out (return to supplier)
  CreditNote = 3,  // qty_in  (return from buyer)
  Adjustment = 4,   // ± either direction
};

const char* code(StockVoucherType v) noexcept;
StockVoucherType stock_voucher_type_from_code(std::string_view s) noexcept;

struct StockLedgerEntry {
  std::int64_t id{0};             // AUTOINCREMENT primary key
  std::string  item_id;
  std::string  transaction_date;   // ISO yyyy-MM-dd
  StockVoucherType voucher_type;
  std::string  voucher_id;
  std::string  voucher_no;
  fin::Money   qty_in{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money   qty_out{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money   unit_price{fin::Money::zero(fin::CurrencyId::INR)};
  std::string  user_id;
  std::string  created_at;
};

} // namespace fin::model
