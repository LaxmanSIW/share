// fin/model/enums.cpp — Code <-> enum mapping (matches Java @JsonValue/@JsonCreator)
#include "fin/model/enums.hpp"
#include <algorithm>
#include <cctype>
#include <cstring>

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

const char* code(BillStatus v) noexcept {
  switch (v) {
    case BillStatus::Unpaid:    return "unpaid";
    case BillStatus::Paid:      return "paid";
    case BillStatus::Cancelled: return "cancelled";
  }
  return "unpaid";
}
BillStatus bill_status_from_code(std::string_view s) noexcept {
  if (eq_ci(s, "paid"))      return BillStatus::Paid;
  if (eq_ci(s, "cancelled")) return BillStatus::Cancelled;
  return BillStatus::Unpaid;
}

const char* code(DocType v) noexcept {
  switch (v) {
    case DocType::Invoice:    return "invoice";
    case DocType::Proforma:   return "proforma";
    case DocType::Quotation:  return "quotation";
    case DocType::Challan:    return "challan";
    case DocType::CreditNote:return "creditnote";
  }
  return "invoice";
}
DocType doc_type_from_code(std::string_view s) noexcept {
  if (eq_ci(s, "proforma"))    return DocType::Proforma;
  if (eq_ci(s, "quotation"))   return DocType::Quotation;
  if (eq_ci(s, "challan"))    return DocType::Challan;
  if (eq_ci(s, "creditnote")) return DocType::CreditNote;
  return DocType::Invoice;
}

const char* code(PaymentMethod v) noexcept {
  switch (v) {
    case PaymentMethod::Cash:       return "CASH";
    case PaymentMethod::UPI:         return "UPI";
    case PaymentMethod::Card:        return "CARD";
    case PaymentMethod::NetBanking:  return "NET_BANKING";
    case PaymentMethod::Cheque:      return "CHEQUE";
    case PaymentMethod::Other:       return "OTHER";
  }
  return "CASH";
}
PaymentMethod payment_method_from_code(std::string_view s) noexcept {
  if (eq_ci(s, "UPI"))         return PaymentMethod::UPI;
  if (eq_ci(s, "CARD"))        return PaymentMethod::Card;
  if (eq_ci(s, "NET_BANKING")) return PaymentMethod::NetBanking;
  if (eq_ci(s, "CHEQUE"))      return PaymentMethod::Cheque;
  if (eq_ci(s, "OTHER"))       return PaymentMethod::Other;
  return PaymentMethod::Cash;
}

const char* code(RepeatCadence v) noexcept {
  switch (v) {
    case RepeatCadence::None:      return "none";
    case RepeatCadence::Daily:     return "daily";
    case RepeatCadence::Weekly:    return "weekly";
    case RepeatCadence::Monthly:   return "monthly";
    case RepeatCadence::Quarterly: return "quarterly";
    case RepeatCadence::Yearly:     return "yearly";
  }
  return "none";
}
RepeatCadence repeat_cadence_from_code(std::string_view s) noexcept {
  if (eq_ci(s, "daily"))     return RepeatCadence::Daily;
  if (eq_ci(s, "weekly"))    return RepeatCadence::Weekly;
  if (eq_ci(s, "monthly"))   return RepeatCadence::Monthly;
  if (eq_ci(s, "quarterly")) return RepeatCadence::Quarterly;
  if (eq_ci(s, "yearly"))    return RepeatCadence::Yearly;
  return RepeatCadence::None;
}

const char* code(ElementType v) noexcept {
  switch (v) {
    case ElementType::Text:      return "text";
    case ElementType::RichText:  return "rich_text";
    case ElementType::Image:     return "image";
    case ElementType::Line:      return "line";
    case ElementType::Rectangle: return "rectangle";
    case ElementType::Ellipse:   return "ellipse";
    case ElementType::Table:     return "table";
    case ElementType::Barcode:  return "barcode";
    case ElementType::QR:        return "qr";
    case ElementType::Chart:     return "chart";
    case ElementType::SubReport: return "sub_report";
    case ElementType::Checkbox: return "checkbox";
  }
  return "text";
}
ElementType element_type_from_code(std::string_view s) noexcept {
  if (eq_ci(s, "rich_text"))  return ElementType::RichText;
  if (eq_ci(s, "image"))      return ElementType::Image;
  if (eq_ci(s, "line"))       return ElementType::Line;
  if (eq_ci(s, "rectangle")) return ElementType::Rectangle;
  if (eq_ci(s, "ellipse"))   return ElementType::Ellipse;
  if (eq_ci(s, "table"))     return ElementType::Table;
  if (eq_ci(s, "barcode"))   return ElementType::Barcode;
  if (eq_ci(s, "qr"))         return ElementType::QR;
  if (eq_ci(s, "chart"))      return ElementType::Chart;
  if (eq_ci(s, "sub_report"))return ElementType::SubReport;
  if (eq_ci(s, "checkbox"))  return ElementType::Checkbox;
  return ElementType::Text;
}

} // namespace fin::model
