#include "fin/ui/views/create_purchase_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/services/purchase.hpp"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QLineEdit>
#include <QFormLayout>
#include <QDateEdit>

namespace fin::ui {

CreatePurchaseView::CreatePurchaseView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  auto* header = UiTheme::row(12, this);
  auto* tb = new QVBoxLayout(); tb->setSpacing(2);
  tb->addWidget(UiTheme::pageTitle("Create Purchase"));
  tb->addWidget(UiTheme::pageSubtitle("New purchase bill from a supplier"));
  header->layout()->addLayout(tb);
  header->layout()->addItem(UiTheme::hspacer());
  l->addWidget(header);

  auto* form_card = UiTheme::card(8, this);
  auto* form = new QFormLayout(form_card);
  auto* bill_no = new QLineEdit; bill_no->setPlaceholderText("PB-2026-0001");
  auto* supplier_bill_no = new QLineEdit;
  auto* date = new QDateEdit(QDate::currentDate()); date->setDisplayFormat("yyyy-MM-dd");
  auto* supplier = new QLineEdit; supplier->setPlaceholderText("Supplier name");
  auto* total = new QLineEdit; total->setText("0.00");
  form->addRow("Bill No:", bill_no);
  form->addRow("Supplier Bill No:", supplier_bill_no);
  form->addRow("Date:", date);
  form->addRow("Supplier:", supplier);
  form->addRow("Total:", total);
  l->addWidget(form_card);
  l->addStretch();
}

void CreatePurchaseView::refresh() {}

} // namespace fin::ui
