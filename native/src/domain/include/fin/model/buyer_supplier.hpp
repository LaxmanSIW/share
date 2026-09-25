// fin/model/buyer_supplier.hpp — Buyer + Supplier models (port of Java Buyer.java + Supplier.java)
#pragma once
#include "fin/money.hpp"
#include "fin/ids.hpp"

#include <map>
#include <string>

namespace fin::model {

struct Buyer {
  std::string id;
  std::string name;
  std::string address;
  std::string gst;
  std::string phone;
  std::string state;
  std::string state_code;
  std::string contact_person;
  std::string city;
  fin::Money  credit_limit{fin::Money::zero(fin::CurrencyId::INR)};
  int         risk_score{8};             // 1-10 risk scale
  std::string default_transport_id;
  fin::Money  opening_balance{fin::Money::zero(fin::CurrencyId::INR)};
  std::map<std::string, std::string> custom;
  std::string created_at;
  std::string updated_at;

  std::string display_name() const { return name.empty() ? "Unnamed Buyer" : name; }
};

struct Supplier {
  std::string id;
  std::string name;
  std::string address;
  std::string gst;
  std::string phone;
  std::string state;
  std::string state_code;
  std::string contact_person;
  std::string city;
  fin::Money  credit_limit{fin::Money::zero(fin::CurrencyId::INR)};
  std::string default_transport_id;
  fin::Money  opening_balance{fin::Money::zero(fin::CurrencyId::INR)};
  std::map<std::string, std::string> custom;
  std::string created_at;
  std::string updated_at;

  std::string display_name() const { return name.empty() ? "Unnamed Supplier" : name; }
};

struct ItemCategory {
  std::string id;
  std::string name;
  std::string created_at;
  std::string updated_at;
};

struct ItemRecord {
  std::string id;
  std::string name;
  std::string hsn;
  std::string unit;
  fin::Money  rate{fin::Money::zero(fin::CurrencyId::INR)};       // sale rate
  fin::Rate   gst_rate{18, 100};
  fin::Money  purchase_rate{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money  current_stock{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money  opening_stock{fin::Money::zero(fin::CurrencyId::INR)};
  fin::Money  reorder_level{fin::Money::zero(fin::CurrencyId::INR)};
  std::string category_id;
  std::string category_name;
  std::string created_at;
  std::string updated_at;
};

struct Transport {
  std::string id;
  std::string name;
  std::string phone;
  std::string vehicle_number;
  std::string created_at;
  std::string updated_at;
};

struct VariableDef {
  std::string key;
  std::string label;
  std::string type;        // "text" or "number"
  bool        builtin{false};
  std::string scope{"fixed"}; // "fixed" or "transactional"
  std::string default_value;
  std::string choices;     // comma-separated quick-pick values
  std::string user_id;
};

struct Template {
  std::string id;
  std::string name;
  std::string json_data;     // full serialized template (elements, page setup, styles)
  std::string created_at;
  std::string updated_at;
  std::string user_id;
};

struct Settings {
  std::string user_id;
  std::string json_data;     // serialized settings (business profile, defaults, etc.)
  std::string created_at;
  std::string updated_at;
};

struct Transaction {
  std::string id;
  std::string buyer_id;
  std::string buyer_name;
  std::string book_type;       // matches BillBookType code
  std::string transaction_type;
  std::string transaction_date;
  std::string due_date;
  fin::Money  amount{fin::Money::zero(fin::CurrencyId::INR)};
  int         total_quantity{0};
  std::string check_number;
  bool        include_in_reporting{true};
  int         parcel{1};
  std::string bill_id;
  std::string bill_no;
  bool        deleted{false};
  std::string deleted_reason;
  std::string deleted_at;
  std::string created_at;
  std::string updated_at;
  std::string user_id;
};

struct ExpenseAccount {
  std::string id;
  std::string name;
  bool        archived{false};
  std::string json_data;
  std::string created_at;
  std::string updated_at;
  std::string user_id;
};

struct Expense {
  std::string id;
  std::string date;
  std::string category;
  fin::Money  amount{fin::Money::zero(fin::CurrencyId::INR)};
  std::string payment_mode;
  std::string json_data;
  std::string created_at;
  std::string updated_at;
  std::string user_id;
};

struct UserSession {
  int         id{0};
  std::string user_id;
  std::string email;
  std::string display_name;
  std::string id_token;
  std::string refresh_token;
  std::int64_t expires_at{0};
  bool        remember_me{false};
  std::string created_at;
};

struct LabelPrintHistory {
  std::string id;
  std::string template_id;
  std::string template_name;
  std::string printer_name;
  double      label_width{0.0};    // mm
  double      label_height{0.0};   // mm
  int         columns{1};
  int         pages{0};
  int         labels{0};
  int         total_copies{0};
  std::string summary;             // human-readable summary
  std::string lines_json;          // per-line details as JSON
  std::string user_id;
  std::string created_at;
};

} // namespace fin::model
