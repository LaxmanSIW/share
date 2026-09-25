// fin/ui/views/create_bill_view.hpp — Full Create Bill view (port of Java CreateBillView.java, 1,384 lines)
//
// Sections:
//   1. Top toolbar: Back / Title / Save / Save & Print / Save & PDF
//   2. SplitPane:
//      LEFT FORM:
//        a. Document picker: Template combo + Doc Type combo + Bill No + Date
//        b. Buyer section: name, address, gst, phone, state, state_code, city,
//           contact_person, save-to-directory checkbox
//        c. Logistics & Extra: po_no, transport, vehicle_no, e_way_bill, parcel
//        d. Line items table: pick item + sr# / desc / hsn / qty / unit /
//           rate / gst% / disc% / taxable / amount + "Add Line" button
//        e. Options: discount%, notes, status, repeat cadence + end date
//        f. Totals: subtotal / discount / taxable / cgst / sgst / igst /
//           round-off / grand_total / amount_in_words
//      RIGHT PREVIEW:
//        Live bill preview using the assigned template (BillPreviewPane →
//        DesignerCanvas with the template + current bill data).
//
// Skill rule §3.5: 150ms debounce on preview updates (PauseTransition in
// Java → Debouncer in C++).
#pragma once
#include <QFrame>
#include <QSplitter>
#include <QLineEdit>
#include <QPlainTextEdit>
#include <QComboBox>
#include <QDateEdit>
#include <QCheckBox>
#include <QLabel>
#include <QPushButton>
#include <memory>

namespace fin::ui {
class DataGrid;
class DesignerCanvas;
class Debouncer;

class CreateBillView : public QFrame {
  Q_OBJECT
 public:
  explicit CreateBillView(QWidget* parent = nullptr);

  /// Load an existing bill for editing (or null for a new bill).
  void load_bill(std::shared_ptr<class fin::model::Bill> bill);

 private slots:
  void on_save_clicked();
  void on_save_print_clicked();
  void on_save_pdf_clicked();
  void on_back_clicked();
  void on_add_line_item();
  void on_template_changed();
  void on_buyer_changed();
  void on_field_changed();   // any form field change → debounce → preview refresh

 private:
  void build_toolbar_();
  void build_form_(QSplitter* split);
  void build_preview_(QSplitter* split);
  void build_doc_section_();
  void build_buyer_section_();
  void build_logistics_section_();
  void build_line_items_section_();
  void build_options_section_();
  void build_totals_section_();
  void refresh_preview_();
  void compute_totals_();
  void save_bill_(bool print_after, bool pdf_after);

  // Form controls
  QComboBox*   template_combo_{nullptr};
  QComboBox*   doc_type_combo_{nullptr};
  QLineEdit*  bill_no_field_{nullptr};
  QDateEdit*   date_field_{nullptr};

  QLineEdit*  buyer_name_field_{nullptr};
  QPlainTextEdit* buyer_address_field_{nullptr};
  QLineEdit*  buyer_gst_field_{nullptr};
  QLineEdit*  buyer_phone_field_{nullptr};
  QLineEdit*  buyer_state_field_{nullptr};
  QLineEdit*  buyer_state_code_field_{nullptr};
  QLineEdit*  buyer_city_field_{nullptr};
  QLineEdit*  buyer_contact_person_field_{nullptr};
  QCheckBox*  save_buyer_cb_{nullptr};

  QLineEdit*  po_no_field_{nullptr};
  QLineEdit*  transport_field_{nullptr};
  QLineEdit*  vehicle_field_{nullptr};
  QLineEdit*  eway_field_{nullptr};
  QLineEdit*  parcel_field_{nullptr};

  // Line items
  QFrame*      items_box_{nullptr};
  QPushButton* add_line_btn_{nullptr};

  // Options
  QLineEdit*  discount_pct_field_{nullptr};
  QPlainTextEdit* notes_field_{nullptr};
  QComboBox*  status_combo_{nullptr};
  QComboBox*  repeat_combo_{nullptr};
  QDateEdit*   repeat_end_field_{nullptr};

  // Totals labels
  QLabel* subtotal_lbl_{nullptr};
  QLabel* discount_lbl_{nullptr};
  QLabel* taxable_lbl_{nullptr};
  QLabel* cgst_lbl_{nullptr};
  QLabel* sgst_lbl_{nullptr};
  QLabel* igst_lbl_{nullptr};
  QLabel* round_off_lbl_{nullptr};
  QLabel* grand_total_lbl_{nullptr};
  QLabel* amount_in_words_lbl_{nullptr};

  // Preview
  DesignerCanvas* preview_canvas_{nullptr};
  Debouncer*     preview_debouncer_{nullptr};

  // State
  std::shared_ptr<fin::model::Bill> editing_bill_;
  std::shared_ptr<class fin::services::Template> current_template_;
};

} // namespace fin::ui
