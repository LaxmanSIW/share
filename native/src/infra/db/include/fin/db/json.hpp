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

namespace fin::model {

// === Money ===
// Stored as JSON object {"currency": "INR", "minor": 123456} for type safety.
// (When writing into the bills.json_data column, we use the same shape as the
// Java original: a double. Conversion happens in the DAO at the boundary.)
inline void to_json(nlohmann::json& j, const fin::Money& m) {
  j = nlohmann::json{{"currency", std::string(fin::currency_of(m.currency()).code)},
                     {"minor",    m.minor()}};
}
inline void from_json(const nlohmann::json& j, fin::Money& m) {
  if (j.is_object()) {
    auto cur = fin::find_currency(j.value("currency", "INR"));
    m = fin::Money::from_minor(j.value("minor", std::int64_t{0}), cur.id);
  } else if (j.is_number()) {
    // Backward-compat: parse a double from the Java original.
    // Skill rule §1: never silently round; use HalfAwayFromZero.
    double v = j.get<double>();
    // Convert to string then parse_round for explicitness.
    char buf[32];
    std::snprintf(buf, sizeof(buf), "%.6f", v);
    m = fin::Money::parse_round(buf, fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
  } else if (j.is_string()) {
    m = fin::Money::parse_round(j.get<std::string>(), fin::CurrencyId::INR, fin::Rounding::HalfAwayFromZero);
  }
}

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
  j = nlohmann::json{
    {"id", b.id}, {"desc", b.desc}, {"hsn", b.hsn},
    {"qty", b.qty}, {"unit", b.unit}, {"rate", b.rate},
    {"gst", (b.gst_rate.num * 100.0 / b.gst_rate.denom)},  // back-compat with Java's double
    {"discPct", b.disc_pct},
    {"custom", b.custom},
  };
}
inline void from_json(const nlohmann::json& j, BillItem& b) {
  b.id = j.value("id", "");
  b.desc = j.value("desc", j.value("description", ""));
  b.hsn = j.value("hsn", "");
  b.qty = j.value("qty", fin::Money::zero(fin::CurrencyId::INR));
  b.unit = j.value("unit", "PCS");
  b.rate = j.value("rate", fin::Money::zero(fin::CurrencyId::INR));
  double gst_pct = j.value("gst", 18.0);
  // pct → exact-fraction Rate: 12.5% → {125, 1000} so 125/1000 = 0.125 = 12.5%.
  std::int64_t num = static_cast<std::int64_t>(gst_pct * 10.0 + 0.5);
  b.gst_rate = fin::Rate{num, 1000};
  b.disc_pct = j.value("discPct", 0.0);
  b.custom = j.value("custom", std::map<std::string, std::string>{});
}

// === BillPayment ===
inline void to_json(nlohmann::json& j, const BillPayment& p) {
  j = nlohmann::json{
    {"id", p.id}, {"date", p.date}, {"amount", p.amount},
    {"method", p.method}, {"reference", p.reference}, {"note", p.note},
  };
}
inline void from_json(const nlohmann::json& j, BillPayment& p) {
  p.id = j.value("id", "");
  p.date = j.value("date", "");
  p.amount = j.value("amount", fin::Money::zero(fin::CurrencyId::INR));
  p.method = j.value("method", PaymentMethod::Cash);
  p.reference = j.value("reference", "");
  p.note = j.value("note", "");
}

// === BillTotals ===
inline void to_json(nlohmann::json& j, const BillTotals& t) {
  j = nlohmann::json{
    {"subtotal", t.subtotal}, {"discount", t.discount}, {"taxable", t.taxable},
    {"cgst", t.cgst}, {"sgst", t.sgst}, {"igst", t.igst},
    {"roundOff", t.round_off}, {"grandTotal", t.grand_total},
    {"totalQty", t.total_qty}, {"itemCount", t.item_count},
  };
}
inline void from_json(const nlohmann::json& j, BillTotals& t) {
  t.subtotal    = j.value("subtotal",     fin::Money::zero(fin::CurrencyId::INR));
  t.discount    = j.value("discount",     fin::Money::zero(fin::CurrencyId::INR));
  t.taxable     = j.value("taxable",      fin::Money::zero(fin::CurrencyId::INR));
  t.cgst        = j.value("cgst",         fin::Money::zero(fin::CurrencyId::INR));
  t.sgst        = j.value("sgst",         fin::Money::zero(fin::CurrencyId::INR));
  t.igst        = j.value("igst",         fin::Money::zero(fin::CurrencyId::INR));
  t.round_off   = j.value("roundOff",     fin::Money::zero(fin::CurrencyId::INR));
  t.grand_total = j.value("grandTotal",   fin::Money::zero(fin::CurrencyId::INR));
  t.total_qty   = j.value("totalQty",     fin::Money::zero(fin::CurrencyId::INR));
  t.item_count  = j.value("itemCount",    0);
}

// === Bill ===
inline void to_json(nlohmann::json& j, const Bill& b) {
  j = nlohmann::json{
    {"id", b.id}, {"billNo", b.bill_no}, {"date", b.date},
    {"templateId", b.template_id}, {"templateName", b.template_name},
    {"variables", b.variables}, {"items", b.items},
    {"discountPct", b.discount_pct}, {"totals", b.totals},
    {"amountInWords", b.amount_in_words}, {"notes", b.notes},
    {"printCount", b.print_count}, {"status", b.status},
    {"paidAt", b.paid_at}, {"payments", b.payments},
    {"docType", b.doc_type}, {"repeat", b.repeat},
    {"repeatEndDate", b.repeat_end_date}, {"repeatSkipNext", b.repeat_skip_next},
    {"parcel", b.parcel}, {"transactionId", b.transaction_id},
    {"createdAt", b.created_at}, {"updatedAt", b.updated_at},
  };
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
  j = nlohmann::json{
    {"id", b.id}, {"name", b.name}, {"address", b.address},
    {"gst", b.gst}, {"phone", b.phone}, {"state", b.state},
    {"stateCode", b.state_code}, {"contactPerson", b.contact_person},
    {"city", b.city}, {"creditLimit", b.credit_limit},
    {"riskScore", b.risk_score}, {"defaultTransportId", b.default_transport_id},
    {"openingBalance", b.opening_balance}, {"custom", b.custom},
    {"createdAt", b.created_at}, {"updatedAt", b.updated_at},
  };
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
  b.credit_limit = j.value("creditLimit", fin::Money::from_minor(10000000, fin::CurrencyId::INR));
  b.risk_score = j.value("riskScore", 8);
  b.default_transport_id = j.value("defaultTransportId", "");
  b.opening_balance = j.value("openingBalance", fin::Money::zero(fin::CurrencyId::INR));
  b.custom = j.value("custom", std::map<std::string, std::string>{});
  b.created_at = j.value("createdAt", "");
  b.updated_at = j.value("updatedAt", "");
}

// === Supplier (same shape as Buyer) ===
inline void to_json(nlohmann::json& j, const Supplier& b) {
  j = nlohmann::json{
    {"id", b.id}, {"name", b.name}, {"address", b.address},
    {"gst", b.gst}, {"phone", b.phone}, {"state", b.state},
    {"stateCode", b.state_code}, {"contactPerson", b.contact_person},
    {"city", b.city}, {"creditLimit", b.credit_limit},
    {"defaultTransportId", b.default_transport_id},
    {"openingBalance", b.opening_balance}, {"custom", b.custom},
    {"createdAt", b.created_at}, {"updatedAt", b.updated_at},
  };
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
  b.credit_limit = j.value("creditLimit", fin::Money::from_minor(10000000, fin::CurrencyId::INR));
  b.default_transport_id = j.value("defaultTransportId", "");
  b.opening_balance = j.value("openingBalance", fin::Money::zero(fin::CurrencyId::INR));
  b.custom = j.value("custom", std::map<std::string, std::string>{});
  b.created_at = j.value("createdAt", "");
  b.updated_at = j.value("updatedAt", "");
}

// === ItemRecord ===
inline void to_json(nlohmann::json& j, const ItemRecord& it) {
  j = nlohmann::json{
    {"id", it.id}, {"name", it.name}, {"hsn", it.hsn}, {"unit", it.unit},
    {"rate", it.rate}, {"gst", (it.gst_rate.num * 100.0 / it.gst_rate.denom)},
    {"purchaseRate", it.purchase_rate}, {"currentStock", it.current_stock},
    {"openingStock", it.opening_stock}, {"reorderLevel", it.reorder_level},
    {"categoryId", it.category_id}, {"categoryName", it.category_name},
    {"createdAt", it.created_at}, {"updatedAt", it.updated_at},
  };
}
inline void from_json(const nlohmann::json& j, ItemRecord& it) {
  it.id = j.value("id", "");
  it.name = j.value("name", "");
  it.hsn = j.value("hsn", "");
  it.unit = j.value("unit", "PCS");
  it.rate = j.value("rate", fin::Money::zero(fin::CurrencyId::INR));
  double gst_pct = j.value("gst", 18.0);
  // pct → exact-fraction Rate: 12.5% → {125, 1000} so 125/1000 = 0.125 = 12.5%.
  it.gst_rate = fin::Rate{static_cast<std::int64_t>(gst_pct * 10.0 + 0.5), 1000};
  it.purchase_rate = j.value("purchaseRate", fin::Money::zero(fin::CurrencyId::INR));
  it.current_stock = j.value("currentStock", fin::Money::zero(fin::CurrencyId::INR));
  it.opening_stock = j.value("openingStock", fin::Money::zero(fin::CurrencyId::INR));
  it.reorder_level = j.value("reorderLevel", fin::Money::zero(fin::CurrencyId::INR));
  it.category_id = j.value("categoryId", "");
  it.category_name = j.value("categoryName", "");
  it.created_at = j.value("createdAt", "");
  it.updated_at = j.value("updatedAt", "");
}

// === VariableDef ===
inline void to_json(nlohmann::json& j, const VariableDef& v) {
  j = nlohmann::json{
    {"key", v.key}, {"label", v.label}, {"type", v.type},
    {"builtin", v.builtin}, {"scope", v.scope},
    {"defaultValue", v.default_value}, {"choices", v.choices},
  };
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
