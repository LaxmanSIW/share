// fin/db/json.hpp — JSON (de)serialization for models using nlohmann::json
//
// The Java original used Jackson with @JsonIgnoreProperties(ignoreUnknown=true)
// to be forward-compatible. nlohmann::json ignores unknown fields by default,
// so we get the same behaviour for free.
//
// Each to_json / from_json overload is a free function in namespace fin::model
// (ADL) so that nlohmann::json finds them automatically.
#pragma once
#include "fin/money.hpp"
#include "fin/model/enums.hpp"
#include "fin/model/bill.hpp"
#include "fin/model/buyer_supplier.hpp"

#include <nlohmann/json.hpp>
#include <string>

// === Money — must be in the `fin` namespace for ADL to pick up to_json/from_json
// when nlohmann::json sees a fin::Money value. ===
namespace fin {
inline void to_json(nlohmann::json& j, const fin::Money& m) {
  j = nlohmann::json::object();
  j["currency"] = std::string(fin::currency_of(m.currency()).code);
  j["minor"] = m.minor();
}
inline void from_json(const nlohmann::json& j, fin::Money& m) {
  if (j.is_object()) {
    auto cur = fin::find_currency(j.value("currency", "INR"));
    m = fin::Money::from_minor(j.value("minor", std::int64_t{0}), cur.id);
  } else if (j.is_number()) {
    double v = j.get<double>();
    char buf[32];
    std::snprintf(buf, sizeof(buf), "%.6f", v);
    m = fin::Money::parse_round(buf, fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
  } else if (j.is_string()) {
    m = fin::Money::parse_round(j.get<std::string>(), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
  }
}
} // namespace fin

namespace fin::model {

// === Enums ===
inline void to_json(nlohmann::json& j, BillStatus v)         { j = code(v); }
inline void from_json(const nlohmann::json& j, BillStatus& v){ v = bill_status_from_code(j.get<std::string>()); }
inline void to_json(nlohmann::json& j, DocType v)             { j = code(v); }
inline void from_json(const nlohmann::json& j, DocType& v)    { v = doc_type_from_code(j.get<std::string>()); }
inline void to_json(nlohmann::json& j, PaymentMethod v)        { j = code(v); }
inline void from_json(const nlohmann::json& j, PaymentMethod& v){ v = payment_method_from_code(j.get<std::string>()); }
inline void to_json(nlohmann::json& j, RepeatCadence v)        { j = code(v); }
inline void from_json(const nlohmann::json& j, RepeatCadence& v){ v = repeat_cadence_from_code(j.get<std::string>()); }

// === BillItem ===
inline void to_json(nlohmann::json& j, const BillItem& b) {
  j["id"] = b.id;
  j["desc"] = b.desc;
  j["hsn"] = b.hsn;
  j["qty"] = b.qty;
  j["unit"] = b.unit;
  j["rate"] = b.rate;
  j["gst"] = (b.gst_rate.num * 100.0 / b.gst_rate.denom);
  j["discPct"] = b.disc_pct;
  j["custom"] = b.custom;
}
inline void from_json(const nlohmann::json& j, BillItem& b) {
  b.id = j.value("id", "");
  b.desc = j.value("desc", j.value("description", ""));
  b.hsn = j.value("hsn", "");
  if (j.contains("qty")) j.at("qty").get_to(b.qty); else b.qty = fin::Money::zero(fin::CurrencyId::INR);
  b.unit = j.value("unit", "PCS");
  if (j.contains("rate")) j.at("rate").get_to(b.rate); else b.rate = fin::Money::zero(fin::CurrencyId::INR);
  double gst_pct = j.value("gst", 18.0);
  // pct → exact-fraction Rate: 12.5% → {125, 1000} so 125/1000 = 0.125 = 12.5%.
  std::int64_t num = static_cast<std::int64_t>(gst_pct * 10.0 + 0.5);
  b.gst_rate = fin::Rate{num, 1000};
  b.disc_pct = j.value("discPct", 0.0);
  b.custom = j.value("custom", std::map<std::string, std::string>{});
}

// === BillPayment ===
inline void to_json(nlohmann::json& j, const BillPayment& p) {
  j["id"] = p.id;
  j["date"] = p.date;
  j["amount"] = p.amount;
  j["method"] = p.method;
  j["reference"] = p.reference;
  j["note"] = p.note;
}
inline void from_json(const nlohmann::json& j, BillPayment& p) {
  p.id = j.value("id", "");
  p.date = j.value("date", "");
  if (j.contains("amount")) j.at("amount").get_to(p.amount); else p.amount = fin::Money::zero(fin::CurrencyId::INR);
  p.method = j.value("method", PaymentMethod::Cash);
  p.reference = j.value("reference", "");
  p.note = j.value("note", "");
}

// === BillTotals ===
inline void to_json(nlohmann::json& j, const BillTotals& t) {
  j["subtotal"] = t.subtotal;
  j["discount"] = t.discount;
  j["taxable"] = t.taxable;
  j["cgst"] = t.cgst;
  j["sgst"] = t.sgst;
  j["igst"] = t.igst;
  j["roundOff"] = t.round_off;
  j["grandTotal"] = t.grand_total;
  j["totalQty"] = t.total_qty;
  j["itemCount"] = t.item_count;
}
inline void from_json(const nlohmann::json& j, BillTotals& t) {
  if (j.contains("subtotal")) j.at("subtotal").get_to(t.subtotal); else t.subtotal = fin::Money::zero(fin::CurrencyId::INR);
  if (j.contains("discount")) j.at("discount").get_to(t.discount); else t.discount = fin::Money::zero(fin::CurrencyId::INR);
  if (j.contains("taxable")) j.at("taxable").get_to(t.taxable); else t.taxable = fin::Money::zero(fin::CurrencyId::INR);
  if (j.contains("cgst")) j.at("cgst").get_to(t.cgst); else t.cgst = fin::Money::zero(fin::CurrencyId::INR);
  if (j.contains("sgst")) j.at("sgst").get_to(t.sgst); else t.sgst = fin::Money::zero(fin::CurrencyId::INR);
  if (j.contains("igst")) j.at("igst").get_to(t.igst); else t.igst = fin::Money::zero(fin::CurrencyId::INR);
  if (j.contains("roundOff")) j.at("roundOff").get_to(t.round_off); else t.round_off = fin::Money::zero(fin::CurrencyId::INR);
  if (j.contains("grandTotal")) j.at("grandTotal").get_to(t.grand_total); else t.grand_total = fin::Money::zero(fin::CurrencyId::INR);
  if (j.contains("totalQty")) j.at("totalQty").get_to(t.total_qty); else t.total_qty = fin::Money::zero(fin::CurrencyId::INR);
  t.item_count  = j.value("itemCount",    0);
}

// === Bill ===
inline void to_json(nlohmann::json& j, const Bill& b) {
  j["id"] = b.id;
  j["billNo"] = b.bill_no;
  j["date"] = b.date;
  j["templateId"] = b.template_id;
  j["templateName"] = b.template_name;
  j["variables"] = b.variables;
  j["items"] = b.items;
  j["discountPct"] = b.discount_pct;
  j["totals"] = b.totals;
  j["amountInWords"] = b.amount_in_words;
  j["notes"] = b.notes;
  j["printCount"] = b.print_count;
  j["status"] = b.status;
  j["paidAt"] = b.paid_at;
  j["payments"] = b.payments;
  j["docType"] = b.doc_type;
  j["repeat"] = b.repeat;
  j["repeatEndDate"] = b.repeat_end_date;
  j["repeatSkipNext"] = b.repeat_skip_next;
  j["parcel"] = b.parcel;
  j["transactionId"] = b.transaction_id;
  j["createdAt"] = b.created_at;
  j["updatedAt"] = b.updated_at;
}
inline void from_json(const nlohmann::json& j, Bill& b) {
  b.id = j.value("id", "");
  b.bill_no = j.value("billNo", "");
  b.date = j.value("date", "");
  b.template_id = j.value("templateId", "");
  b.template_name = j.value("templateName", "");
  b.variables = j.value("variables", std::map<std::string, std::string>{});
  b.items = j.value("items", std::vector<BillItem>{});
  b.discount_pct = j.value("discountPct", 0.0);
  b.totals = j.value("totals", BillTotals{});
  b.amount_in_words = j.value("amountInWords", "");
  b.notes = j.value("notes", "");
  b.print_count = j.value("printCount", 0);
  b.status = j.value("status", BillStatus::Unpaid);
  b.paid_at = j.value("paidAt", "");
  b.payments = j.value("payments", std::vector<BillPayment>{});
  b.doc_type = j.value("docType", DocType::Invoice);
  b.repeat = j.value("repeat", RepeatCadence::None);
  b.repeat_end_date = j.value("repeatEndDate", "");
  b.repeat_skip_next = j.value("repeatSkipNext", false);
  b.parcel = j.value("parcel", 1);
  b.transaction_id = j.value("transactionId", "");
  b.created_at = j.value("createdAt", "");
  b.updated_at = j.value("updatedAt", "");
}

// === Buyer ===
inline void to_json(nlohmann::json& j, const Buyer& b) {
  j["id"] = b.id;
  j["name"] = b.name;
  j["address"] = b.address;
  j["gst"] = b.gst;
  j["phone"] = b.phone;
  j["state"] = b.state;
  j["stateCode"] = b.state_code;
  j["contactPerson"] = b.contact_person;
  j["city"] = b.city;
  j["creditLimit"] = b.credit_limit;
  j["riskScore"] = b.risk_score;
  j["defaultTransportId"] = b.default_transport_id;
  j["openingBalance"] = b.opening_balance;
  j["custom"] = b.custom;
  j["createdAt"] = b.created_at;
  j["updatedAt"] = b.updated_at;
}
inline void from_json(const nlohmann::json& j, Buyer& b) {
  b.id = j.value("id", "");
  b.name = j.value("name", "");
  b.address = j.value("address", "");
  b.gst = j.value("gst", "");
  b.phone = j.value("phone", "");
  b.state = j.value("state", "");
  b.state_code = j.value("stateCode", "");
  b.contact_person = j.value("contactPerson", "");
  b.city = j.value("city", "");
  if (j.contains("creditLimit")) j.at("creditLimit").get_to(b.credit_limit); else b.credit_limit = fin::Money::from_minor(10000000, fin::CurrencyId::INR);
  b.risk_score = j.value("riskScore", 8);
  b.default_transport_id = j.value("defaultTransportId", "");
  if (j.contains("openingBalance")) j.at("openingBalance").get_to(b.opening_balance); else b.opening_balance = fin::Money::zero(fin::CurrencyId::INR);
  b.custom = j.value("custom", std::map<std::string, std::string>{});
  b.created_at = j.value("createdAt", "");
  b.updated_at = j.value("updatedAt", "");
}

// === Supplier (same shape as Buyer) ===
inline void to_json(nlohmann::json& j, const Supplier& b) {
  j["id"] = b.id;
  j["name"] = b.name;
  j["address"] = b.address;
  j["gst"] = b.gst;
  j["phone"] = b.phone;
  j["state"] = b.state;
  j["stateCode"] = b.state_code;
  j["contactPerson"] = b.contact_person;
  j["city"] = b.city;
  j["creditLimit"] = b.credit_limit;
  j["defaultTransportId"] = b.default_transport_id;
  j["openingBalance"] = b.opening_balance;
  j["custom"] = b.custom;
  j["createdAt"] = b.created_at;
  j["updatedAt"] = b.updated_at;
}
inline void from_json(const nlohmann::json& j, Supplier& b) {
  b.id = j.value("id", "");
  b.name = j.value("name", "");
  b.address = j.value("address", "");
  b.gst = j.value("gst", "");
  b.phone = j.value("phone", "");
  b.state = j.value("state", "");
  b.state_code = j.value("stateCode", "");
  b.contact_person = j.value("contactPerson", "");
  b.city = j.value("city", "");
  if (j.contains("creditLimit")) j.at("creditLimit").get_to(b.credit_limit); else b.credit_limit = fin::Money::from_minor(10000000, fin::CurrencyId::INR);
  b.default_transport_id = j.value("defaultTransportId", "");
  if (j.contains("openingBalance")) j.at("openingBalance").get_to(b.opening_balance); else b.opening_balance = fin::Money::zero(fin::CurrencyId::INR);
  b.custom = j.value("custom", std::map<std::string, std::string>{});
  b.created_at = j.value("createdAt", "");
  b.updated_at = j.value("updatedAt", "");
}

// === ItemRecord ===
inline void to_json(nlohmann::json& j, const ItemRecord& it) {
  j["id"] = it.id;
  j["name"] = it.name;
  j["hsn"] = it.hsn;
  j["unit"] = it.unit;
  j["rate"] = it.rate;
  j["gst"] = (it.gst_rate.num * 100.0 / it.gst_rate.denom);
  j["purchaseRate"] = it.purchase_rate;
  j["currentStock"] = it.current_stock;
  j["openingStock"] = it.opening_stock;
  j["reorderLevel"] = it.reorder_level;
  j["categoryId"] = it.category_id;
  j["categoryName"] = it.category_name;
  j["createdAt"] = it.created_at;
  j["updatedAt"] = it.updated_at;
}
inline void from_json(const nlohmann::json& j, ItemRecord& it) {
  it.id = j.value("id", "");
  it.name = j.value("name", "");
  it.hsn = j.value("hsn", "");
  it.unit = j.value("unit", "PCS");
  if (j.contains("rate")) j.at("rate").get_to(it.rate); else it.rate = fin::Money::zero(fin::CurrencyId::INR);
  double gst_pct = j.value("gst", 18.0);
  // pct → exact-fraction Rate: 12.5% → {125, 1000} so 125/1000 = 0.125 = 12.5%.
  it.gst_rate = fin::Rate{static_cast<std::int64_t>(gst_pct * 10.0 + 0.5), 1000};
  if (j.contains("purchaseRate")) j.at("purchaseRate").get_to(it.purchase_rate); else it.purchase_rate = fin::Money::zero(fin::CurrencyId::INR);
  if (j.contains("currentStock")) j.at("currentStock").get_to(it.current_stock); else it.current_stock = fin::Money::zero(fin::CurrencyId::INR);
  if (j.contains("openingStock")) j.at("openingStock").get_to(it.opening_stock); else it.opening_stock = fin::Money::zero(fin::CurrencyId::INR);
  if (j.contains("reorderLevel")) j.at("reorderLevel").get_to(it.reorder_level); else it.reorder_level = fin::Money::zero(fin::CurrencyId::INR);
  it.category_id = j.value("categoryId", "");
  it.category_name = j.value("categoryName", "");
  it.created_at = j.value("createdAt", "");
  it.updated_at = j.value("updatedAt", "");
}

// === VariableDef ===
inline void to_json(nlohmann::json& j, const VariableDef& v) {
  j["key"] = v.key;
  j["label"] = v.label;
  j["type"] = v.type;
  j["builtin"] = v.builtin;
  j["scope"] = v.scope;
  j["defaultValue"] = v.default_value;
  j["choices"] = v.choices;
}
inline void from_json(const nlohmann::json& j, VariableDef& v) {
  v.key = j.value("key", "");
  v.label = j.value("label", "");
  v.type = j.value("type", "text");
  v.builtin = j.value("builtin", false);
  v.scope = j.value("scope", "fixed");
  v.default_value = j.value("defaultValue", "");
  v.choices = j.value("choices", "");
}

} // namespace fin::model
