// fin/model/bill.hpp — Bill model (port of Java Bill.java + BillItem + BillTotals + BillPayment)
//
// Skill §1 (accounting): store money as int64 minor units (fin::Money). The
// Java original uses `double`; in the C++ port we use fin::Money throughout.
// The json_data column is preserved verbatim so existing databases created
// by the Java app continue to work — we accept doubles on read and convert,
// and we write doubles back so we don't break the wire format.
#pragma once
#include "fin/money.hpp"
#include "fin/ids.hpp"
#include "fin/model/enums.hpp"

#include <cstdint>
#include <map>
#include <optional>
#include <string>
#include <vector>

namespace fin::model {

struct BillItem {
  std::string id;
  std::string desc;
  std::string hsn;
  fin::Money  qty;          // quantity in fixed-point (Money carries its own scale)
  std::string unit;         // "PCS", "KG", "LTR"...
  fin::Money  rate;         // unit price
  fin::Rate   gst_rate{18, 100};
  double      disc_pct{0.0};   // discount percentage 0-100
  std::map<std::string, std::string> custom;

  BillItem() : qty(fin::Money::zero(fin::CurrencyId::INR)), rate(fin::Money::zero(fin::CurrencyId::INR)) {}
  // Constructor parameter names use trailing underscore (C++ idiom) to avoid
  // -Wshadow warnings from member names.
  BillItem(std::string id_, std::string desc_, std::string hsn_, fin::Money qty_, std::string unit_, fin::Money rate_, fin::Rate gst_rate_, double disc_pct_)
    : id(std::move(id_)), desc(std::move(desc_)), hsn(std::move(hsn_)), qty(qty_), unit(std::move(unit_)), rate(rate_), gst_rate(gst_rate_), disc_pct(disc_pct_) {}

  /// Gross = qty × rate (skill: multiplication uses 128-bit intermediates).
  fin::Money gross() const;
  /// Net = gross after discount. Uses allocate() internally for line discount
  /// spreading across many lines (skill rule §2: never "fix the last line").
  fin::Money net() const;
};

struct BillTotals {
  fin::Money subtotal{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money discount{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money taxable {fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money cgst {fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money sgst {fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money igst{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money round_off{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money grand_total{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money total_qty{fin::Money::zero(fin::CurrencyId::INR)};
  int        item_count{0};
};

struct BillPayment {
  std::string id;
  std::string date;     // ISO yyyy-MM-dd
  fin::Money  amount{fin::Money::zero(fin::CurrencyId::INR)};
  PaymentMethod method{PaymentMethod::Cash};
  std::string reference;
  std::string note;
};

struct Bill {
  std::string id;
  std::string bill_no;
  std::string date;     // ISO yyyy-MM-dd
  std::string template_id;
  std::string template_name;
  std::map<std::string, std::string> variables;
  std::vector<BillItem> items;
  double discount_pct{0.0};
  BillTotals totals;
  std::string amount_in_words;
  std::string notes;
  int print_count{0};
  BillStatus status{BillStatus::Unpaid};
  std::string paid_at;    // ISO date or empty
  std::vector<BillPayment> payments;
  DocType doc_type{DocType::Invoice};
  RepeatCadence repeat{RepeatCadence::None};
  std::string repeat_end_date;
  bool repeat_skip_next{false};
  int parcel{1};
  std::string transaction_id;
  std::string created_at;
  std::string updated_at;

  // Convenience accessors.
  std::string buyer_name() const {
    auto it = variables.find("buyer_name");
    return it == variables.end() ? std::string{} : it->second;
  }
  void set_buyer_name(std::string name) { variables["buyer_name"] = std::move(name); }

  int get_parcel() const noexcept { return parcel > 0 ? parcel : 1; }
  void set_parcel(int n) {
    parcel = n > 0 ? n : 1;
    variables["parcel"]   = std::to_string(parcel);
    variables["parcels"]  = std::to_string(parcel);
  }
};

} // namespace fin::model
