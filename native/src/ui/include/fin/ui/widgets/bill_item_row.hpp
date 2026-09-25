// fin/ui/widgets/bill_item_row.hpp — Per-line editor widget for CreateBillView
//
// Port of Java BillItemRow inner class (~200 lines embedded in CreateBillView).
// Each row: pick item button + sr# + desc + hsn + qty + unit + rate + gst% + disc% + amount
// Auto-computes amount on qty/rate/disc change.
// Auto-fills desc/hsn/unit/rate/gst when an item is picked from the catalog.
#pragma once
#include <QFrame>
#include <QLineEdit>
#include <QLabel>
#include <QPushButton>
#include <memory>
#include "fin/model/bill.hpp"

namespace fin::ui {

class BillItemRow : public QFrame {
  Q_OBJECT
 public:
  explicit BillItemRow(QWidget* parent = nullptr);

  /// Initialise from an existing BillItem (for edit mode).
  void load(const fin::model::BillItem& item);

  /// Export the row's contents as a BillItem (for save).
  fin::model::BillItem to_bill_item() const;

  /// Compute the line amount = qty × rate × (1 - disc%).
  fin::Money compute_amount() const;

 signals:
  /// Emitted when any field changes (qty, rate, disc). Used by CreateBillView
  /// to recompute totals (debounced per skill §3.5).
  void changed();
  /// Emitted when the user clicks the delete (×) button.
  void remove_requested();
  /// Emitted when the user clicks the catalog picker.
  void pick_requested();

 private:
  QLineEdit*  sr_field_;
  QLineEdit*  desc_field_;
  QLineEdit*  hsn_field_;
  QLineEdit*  qty_field_;
  QLineEdit*  unit_field_;
  QLineEdit*  rate_field_;
  QLineEdit*  gst_field_;
  QLineEdit*  disc_field_;
  QLabel*     amount_lbl_;
  QPushButton* pick_btn_;
  QPushButton* remove_btn_;
};

} // namespace fin::ui
