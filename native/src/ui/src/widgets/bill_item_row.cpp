// fin/ui/widgets/bill_item_row.cpp
#include "fin/ui/widgets/bill_item_row.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/services/billing.hpp"
#include "fin/money.hpp"
#include "fin/currency.hpp"

#include <QHBoxLayout>
#include <QLineEdit>
#include <QLabel>
#include <QPushButton>
#include <QUuid>

namespace fin::ui {

BillItemRow::BillItemRow(QWidget* parent) : QFrame(parent) {
  setStyleSheet("QFrame { background-color: transparent; border-bottom: 1px solid #232B38; }"
                "QLineEdit { border: none; background-color: transparent; padding: 6px 4px; }"
                "QLineEdit:focus { border-bottom: 1px solid #D9A13B; }");
  setFixedHeight(36);

  auto* l = new QHBoxLayout(this);
  l->setContentsMargins(0, 0, 0, 0);
  l->setSpacing(0);

  pick_btn_ = new QPushButton(this);
  pick_btn_->setProperty("class", "icon-button");
  pick_btn_->setIcon(IconHelper::icon(IconHelper::ICON_SEARCH, 12, QColor("#94A3B8")));
  pick_btn_->setIconSize(QSize(12, 12));
  pick_btn_->setFixedSize(30, 30);
  pick_btn_->setCursor(Qt::PointingHandCursor);
  pick_btn_->setToolTip("Pick from item catalog");
  connect(pick_btn_, &QPushButton::clicked, this, [this]{ emit pick_requested(); });
  l->addWidget(pick_btn_);

  sr_field_    = new QLineEdit("1", this);     sr_field_->setMaximumWidth(30);  sr_field_->setReadOnly(true);
  desc_field_  = new QLineEdit(this);          desc_field_->setMinimumWidth(180);
  hsn_field_   = new QLineEdit(this);         hsn_field_->setMaximumWidth(65);
  qty_field_   = new QLineEdit("1", this);     qty_field_->setMaximumWidth(50);  qty_field_->setAlignment(Qt::AlignRight);
  unit_field_  = new QLineEdit("PCS", this);  unit_field_->setMaximumWidth(55); unit_field_->setAlignment(Qt::AlignCenter);
  rate_field_  = new QLineEdit("0", this);    rate_field_->setMaximumWidth(65);  rate_field_->setAlignment(Qt::AlignRight);
  gst_field_   = new QLineEdit("18", this);   gst_field_->setMaximumWidth(45);   gst_field_->setAlignment(Qt::AlignRight);
  disc_field_  = new QLineEdit("0", this);    disc_field_->setMaximumWidth(45);  disc_field_->setAlignment(Qt::AlignRight);
  amount_lbl_  = new QLabel("₹0.00", this);   amount_lbl_->setMinimumWidth(80);  amount_lbl_->setAlignment(Qt::AlignRight);
  amount_lbl_->setStyleSheet("color: #D9A13B; font-weight: bold;");

  for (auto* w : {sr_field_, desc_field_, hsn_field_, qty_field_, unit_field_, rate_field_, gst_field_, disc_field_}) {
    l->addWidget(w);
  }
  l->addWidget(amount_lbl_);

  l->addStretch();
  remove_btn_ = new QPushButton(this);
  remove_btn_->setProperty("class", "icon-button");
  remove_btn_->setIcon(IconHelper::icon(IconHelper::ICON_CLOSE, 12, QColor("#EF4444")));
  remove_btn_->setIconSize(QSize(12, 12));
  remove_btn_->setFixedSize(28, 28);
  remove_btn_->setCursor(Qt::PointingHandCursor);
  remove_btn_->setToolTip("Remove line");
  connect(remove_btn_, &QPushButton::clicked, this, [this]{ emit remove_requested(); });
  l->addWidget(remove_btn_);

  // Wire field changes to recompute amount + emit changed() for the totals.
  for (auto* w : {qty_field_, rate_field_, disc_field_, gst_field_}) {
    connect(w, &QLineEdit::textChanged, this, [this]{
      auto amt = compute_amount();
      amount_lbl_->setText(QString::fromStdString(amt.to_decimal_string()));
      emit changed();
    });
  }
}

void BillItemRow::load(const fin::model::BillItem& item) {
  sr_field_->setText(QString::number(std::stoi(item.id.substr(item.id.size() > 2 ? item.id.size() - 2 : 0))));
  desc_field_->setText(QString::fromStdString(item.desc));
  hsn_field_->setText(QString::fromStdString(item.hsn));
  qty_field_->setText(QString::fromStdString(item.qty.to_decimal_string()));
  unit_field_->setText(QString::fromStdString(item.unit));
  rate_field_->setText(QString::fromStdString(item.rate.to_decimal_string()));
  gst_field_->setText(QString::number(item.gst_rate.num * 100.0 / item.gst_rate.denom));
  disc_field_->setText(QString::number(item.disc_pct));
  auto amt = compute_amount();
  amount_lbl_->setText(QString::fromStdString(amt.to_decimal_string()));
}

fin::model::BillItem BillItemRow::to_bill_item() const {
  fin::model::BillItem it;
  it.id = sr_field_->text().toStdString();
  if (it.id.empty()) it.id = std::to_string(reinterpret_cast<std::uintptr_t>(this));
  it.desc = desc_field_->text().toStdString();
  it.hsn = hsn_field_->text().toStdString();
  it.qty = fin::Money::parse(qty_field_->text().toStdString(), fin::CurrencyId::INR);
  it.unit = unit_field_->text().toStdString();
  it.rate = fin::Money::parse(rate_field_->text().toStdString(), fin::CurrencyId::INR);
  double g = gst_field_->text().toDouble();
  it.gst_rate = fin::Rate{static_cast<std::int64_t>(g * 10.0 + 0.5), 1000};
  it.disc_pct = disc_field_->text().toDouble();
  return it;
}

fin::Money BillItemRow::compute_amount() const {
  auto item = to_bill_item();
  return item.net();
}

} // namespace fin::ui
