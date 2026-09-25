// fin/model/enums.hpp — Java enums ported to C++ enum classes
#pragma once
#include <cstdint>
#include <string>
#include <string_view>

namespace fin::model {

enum class BillStatus : std::uint8_t {
  Unpaid = 0,
  Paid = 1,
  Cancelled = 2,
};

enum class DocType : std::uint8_t {
  Invoice = 0,
  Proforma = 1,
  Quotation = 2,
  Challan = 3,
  CreditNote = 4,
};

enum class PaymentMethod : std::uint8_t {
  Cash = 0,
  UPI = 1,
  Card = 2,
  NetBanking = 3,
  Cheque = 4,
  Other = 5,
};

enum class RepeatCadence : std::uint8_t {
  None = 0,
  Daily = 1,
  Weekly = 2,
  Monthly = 3,
  Quarterly = 4,
  Yearly = 5,
};

enum class ElementType : std::uint8_t {
  Text = 0,
  RichText = 1,
  Image = 2,
  Line = 3,
  Rectangle = 4,
  Ellipse = 5,
  Table = 6,
  Barcode = 7,
  QR = 8,
  Chart = 9,
  SubReport = 10,
  Checkbox = 11,
};

enum class BillBookType : std::uint8_t {
  Sales = 0,
  Purchase = 1,
  Payment = 2,
};

// === Code <-> enum helpers (matches Java original's @JsonValue / @JsonCreator) ===

const char* code(BillStatus v) noexcept;
BillStatus  bill_status_from_code(std::string_view s) noexcept;

const char* code(DocType v) noexcept;
DocType     doc_type_from_code(std::string_view s) noexcept;

const char* code(PaymentMethod v) noexcept;
PaymentMethod payment_method_from_code(std::string_view s) noexcept;

const char* code(RepeatCadence v) noexcept;
RepeatCadence repeat_cadence_from_code(std::string_view s) noexcept;

const char* code(ElementType v) noexcept;
ElementType element_type_from_code(std::string_view s) noexcept;

} // namespace fin::model
