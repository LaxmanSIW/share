// fin/ui/views/dashboard_view.cpp
#include "fin/ui/views/dashboard_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"

#include "fin/services/billing.hpp"
#include "fin/services/auth_session.hpp"

#include <QHBoxLayout>
#include <QVBoxLayout>
#include <QLabel>
#include <QTimer>

namespace fin::ui {

namespace {

QFrame* make_kpi_card(const QString& title, const QString& value, std::string_view icon, QWidget* parent) {
  auto* card = UiTheme::card(8, parent);
  auto* header_row = UiTheme::row(8, card);
  auto* icon_lbl = new QLabel(card);
  icon_lbl->setPixmap(IconHelper::pixmap(icon, 16, QColor("#D9A13B")));
  header_row->layout()->addWidget(icon_lbl);
  header_row->layout()->addWidget(UiTheme::micro(title, card));
  static_cast<QHBoxLayout*>(header_row->layout())->addItem(UiTheme::hspacer());
  static_cast<QVBoxLayout*>(card->layout())->addWidget(header_row);
  static_cast<QVBoxLayout*>(card->layout())->addWidget(UiTheme::cardValue(value, card));
  return card;
}

} // namespace

DashboardView::DashboardView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);

  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(20);

  l->addWidget(UiTheme::pageTitle("Dashboard"));
  l->addWidget(UiTheme::pageSubtitle("Overview of your business performance"));

  // KPI row (4 cards)
  auto* kpi_row = UiTheme::row(16, this);
  kpi_row->layout()->addWidget(make_kpi_card("TOTAL REVENUE", "₹0.00", IconHelper::ICON_TRENDING_UP, kpi_row));
  kpi_row->layout()->addWidget(make_kpi_card("OUTSTANDING", "₹0.00", IconHelper::ICON_HISTORY, kpi_row));
  kpi_row->layout()->addWidget(make_kpi_card("GST PAYABLE", "₹0.00", IconHelper::ICON_BAR_CHART, kpi_row));
  kpi_row->layout()->addWidget(make_kpi_card("TOTAL BUYERS", "0", IconHelper::ICON_USERS, kpi_row));
  l->addWidget(kpi_row);

  // Recent invoices card
  auto* recent_card = UiTheme::card(12, this);
  recent_card->layout()->addWidget(UiTheme::cardTitle("Recent invoices", recent_card));
  auto* empty = UiTheme::muted("No invoices yet. Click \"Create Bill\" in the sidebar to get started.", recent_card);
  empty->setAlignment(Qt::AlignCenter);
  recent_card->layout()->addWidget(empty);
  l->addWidget(recent_card, 1);

  l->addStretch();

  // Defer refresh to first show (skill §3.11: instant feedback, then fill in).
  QTimer::singleShot(0, this, [this] { refresh(); });
}

void DashboardView::refresh() {
  // Phase 5 skeleton: real impl loads totals from BillingService + DAOs
  // asynchronously on the db pool, updates KPI values via LatestOnly.
  // For now, the cards show placeholders.
}

} // namespace fin::ui
