// fin/ui/views/create_bill_view.hpp — Full Create Bill view header.
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

#include "fin/model/bill.hpp"
#include "fin/services/template_engine.hpp"

namespace fin::app { class Debouncer; }

namespace fin::ui {

class DesignerCanvas;
class DataGrid;

class CreateBillView : public QFrame {
  Q_OBJECT
 public:
  explicit CreateBillView(QWidget* parent = nullptr);
  void load_bill(std::shared_ptr<fin::model::Bill> bill);
 private slots:
  void on_save_clicked();
  void on_save_print_clicked();
  void on_save_pdf_clicked();
  void on_back_clicked();
  void on_add_line_item();
  void on_template_changed();
  void on_buyer_changed();
  void on_field_changed();
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
  QFrame*      items_box_{nullptr};
  QPushButton* add_line_btn_{nullptr};
  QLineEdit*  discount_pct_field_{nullptr};
  QPlainTextEdit* notes_field_{nullptr};
  QComboBox*  status_combo_{nullptr};
  QComboBox*  repeat_combo_{nullptr};
  QDateEdit*   repeat_end_field_{nullptr};
  QLabel* subtotal_lbl_{nullptr};
  QLabel* discount_lbl_{nullptr};
  QLabel* taxable_lbl_{nullptr};
  QLabel* cgst_lbl_{nullptr};
  QLabel* sgst_lbl_{nullptr};
  QLabel* igst_lbl_{nullptr};
  QLabel* round_off_lbl_{nullptr};
  QLabel* grand_total_lbl_{nullptr};
  QLabel* amount_in_words_lbl_{nullptr};
  DesignerCanvas* preview_canvas_{nullptr};
  fin::app::Debouncer* preview_debouncer_{nullptr};
  std::shared_ptr<fin::model::Bill> editing_bill_;
  std::shared_ptr<fin::services::Template> current_template_;
};

} // namespace fin::ui
