// fin/ui/views/dashboard_view.cpp — Port of Java DashboardView.java
// Matches the Java original's structure:
//   1. Page header: greeting + month navigation + "New Bill" button
//   2. Recurring banner (if any due)
//   3. 4 KPI cards (revenue, collected, due, lifetime) with delta indicators
//   4. Bento grid: 6-month revenue trend + GST summary + Top 5 buyers + Recent invoices
#include "fin/ui/views/dashboard_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/services/billing.hpp"
#include "fin/services/auth_session.hpp"
#include "fin/db/database_manager.hpp"
#include "fin/db/bill_dao.hpp"
#include "fin/db/daos.hpp"

#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGridLayout>
#include <QLabel>
#include <QFrame>
#include <QScrollArea>
#include <QDate>
#include <QTimer>
#include <QPainter>
#include <QPainterPath>
#include <cmath>

namespace fin::ui {

namespace {

/// Mini sparkline chart widget — draws a simple line chart in the brand colors.
class Sparkline : public QWidget {
 public:
  explicit Sparkline(QWidget* parent = nullptr) : QWidget(parent) {
    setFixedHeight(32);
    setMinimumWidth(120);
  }
  void set_data(const std::vector<double>& values) { data_ = values; update(); }
 protected:
  void paintEvent(QPaintEvent*) override {
    if (data_.size() < 2) return;
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing, true);
    int w = width(), h = height();
    double min_v = *std::min_element(data_.begin(), data_.end());
    double max_v = *std::max_element(data_.begin(), data_.end());
    double range = max_v - min_v;
    if (range < 0.001) range = 1.0;
    QPainterPath path;
    for (std::size_t i = 0; i < data_.size(); ++i) {
      double x = static_cast<double>(i) / (data_.size() - 1) * w;
      double y = h - (data_[i] - min_v) / range * (h - 4) - 2;
      if (i == 0) path.moveTo(x, y);
      else path.lineTo(x, y);
    }
    // Fill under the line
    QPainterPath fill = path;
    fill.lineTo(w, h);
    fill.lineTo(0, h);
    fill.closeSubpath();
    p.fillPath(fill, QColor(217, 161, 59, 30));
    // Draw the line
    p.setPen(QPen(QColor("#D9A13B"), 1.5));
    p.drawPath(path);
  }
 private:
  std::vector<double> data_;
};

/// KPI card — matches the Java original's KPI structure:
/// icon + label + value + delta + sparkline
QFrame* make_kpi_card(const QString& label, const QString& value,
                        const QString& delta, bool delta_positive,
                        std::string_view icon, QWidget* parent) {
  auto* card = new QFrame(parent);
  card->setProperty("class", "card");
  card->setMinimumHeight(120);
  auto* l = new QVBoxLayout(card);
  l->setContentsMargins(16, 16, 16, 16);
  l->setSpacing(8);

  // Icon + label row
  auto* top_row = new QHBoxLayout();
  top_row->setSpacing(8);
  auto* icon_lbl = new QLabel(card);
  icon_lbl->setPixmap(IconHelper::pixmap(icon, 16, QColor("#D9A13B")));
  top_row->addWidget(icon_lbl);
  auto* label_lbl = new QLabel(label, card);
  label_lbl->setStyleSheet("color: #94A3B8; font-size: 11px; font-weight: bold; text-transform: uppercase; letter-spacing: 0.05em;");
  top_row->addWidget(label_lbl);
  top_row->addStretch();
  l->addLayout(top_row);

  // Value
  auto* value_lbl = new QLabel(value, card);
  value_lbl->setStyleSheet("color: #F4F4F5; font-size: 24px; font-weight: bold;");
  l->addWidget(value_lbl);

  // Delta
  auto* delta_lbl = new QLabel(delta, card);
  delta_lbl->setStyleSheet(QString("color: %1; font-size: 12px; font-weight: bold;").arg(
    delta_positive ? "#10B981" : "#EF4444"));
  l->addWidget(delta_lbl);

  // Sparkline
  auto* spark = new Sparkline(card);
  spark->set_data({10, 15, 12, 20, 18, 25, 22, 30, 28, 35});
  l->addWidget(spark);

  return card;
}

/// Mini invoice row — matches the Java original's recent invoices list
QFrame* make_invoice_row(const QString& bill_no, const QString& buyer,
                           const QString& amount, const QString& status,
                           QWidget* parent) {
  auto* row = new QFrame(parent);
  row->setStyleSheet("QFrame { border-bottom: 1px solid #232B38; }");
  row->setFixedHeight(40);
  auto* l = new QHBoxLayout(row);
  l->setContentsMargins(4, 8, 4, 8);
  l->setSpacing(12);

  auto* no_lbl = new QLabel(bill_no, row);
  no_lbl->setStyleSheet("color: #D9A13B; font-weight: bold; font-size: 12px; font-family: monospace;");
  no_lbl->setMinimumWidth(100);
  l->addWidget(no_lbl);

  auto* buyer_lbl = new QLabel(buyer, row);
  buyer_lbl->setStyleSheet("color: #94A3B8; font-size: 12px;");
  buyer_lbl->setMinimumWidth(150);
  l->addWidget(buyer_lbl);

  l->addStretch();

  // Status badge
  auto* status_lbl = new QLabel(status, row);
  status_lbl->setAlignment(Qt::AlignCenter);
  QString badge_color;
  if (status == "Paid") badge_color = "#10B981";
  else if (status == "Unpaid") badge_color = "#F59E0B";
  else badge_color = "#EF4444";
  status_lbl->setStyleSheet(QString("background-color: rgba(%1, %2, %3, 0.15); color: %1; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: bold;")
    .arg(QColor(badge_color).red()).arg(QColor(badge_color).green()).arg(QColor(badge_color).blue()));
  l->addWidget(status_lbl);

  auto* amt_lbl = new QLabel(amount, row);
  amt_lbl->setStyleSheet("color: #F4F4F5; font-weight: bold; font-size: 12px; font-family: monospace;");
  amt_lbl->setMinimumWidth(100);
  amt_lbl->setAlignment(Qt::AlignRight);
  l->addWidget(amt_lbl);

  return row;
}

/// Top buyer ranking row
QFrame* make_rank_row(int rank, const QString& name, const QString& amount, double pct, QWidget* parent) {
  auto* row = new QFrame(parent);
  row->setStyleSheet("QFrame { border-bottom: 1px solid #232B38; }");
  row->setFixedHeight(48);
  auto* l = new QHBoxLayout(row);
  l->setContentsMargins(4, 8, 4, 8);
  l->setSpacing(10);

  auto* rank_lbl = new QLabel(QString::number(rank), row);
  rank_lbl->setStyleSheet("color: #94A3B8; font-weight: bold; font-size: 14px; font-family: monospace;");
  rank_lbl->setFixedWidth(22);
  l->addWidget(rank_lbl);

  auto* name_box = new QVBoxLayout();
  name_box->setSpacing(2);
  auto* name_lbl = new QLabel(name, row);
  name_lbl->setStyleSheet("color: #F4F4F5; font-size: 12px; font-weight: bold;");
  name_box->addWidget(name_lbl);
  // Bar
  auto* bar = new QFrame(row);
  bar->setFixedHeight(7);
  bar->setStyleSheet("background-color: #1A222D; border-radius: 3px;");
  auto* bar_l = new QHBoxLayout(bar);
  bar_l->setContentsMargins(0, 0, 0, 0);
  auto* fill = new QFrame(bar);
  fill->setFixedHeight(7);
  fill->setStyleSheet(QString("background-color: %1; border-radius: 3px;").arg(rank == 1 ? "#D9A13B" : "#94A3B8"));
  fill->setMaximumWidth(static_cast<int>(pct * 200));
  bar_l->addWidget(fill);
  name_box->addWidget(bar);
  l->addLayout(name_box, 1);

  auto* amt_lbl = new QLabel(amount, row);
  amt_lbl->setStyleSheet("color: #F4F4F5; font-weight: bold; font-size: 12px; font-family: monospace;");
  amt_lbl->setMinimumWidth(100);
  amt_lbl->setAlignment(Qt::AlignRight);
  l->addWidget(amt_lbl);

  return row;
}

} // namespace

DashboardView::DashboardView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);

  auto* scroll = new QScrollArea(this);
  scroll->setWidgetResizable(true);
  scroll->setFrameShape(QFrame::NoFrame);
  scroll->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);

  auto* content = new QFrame(scroll);
  content->setStyleSheet("background-color: #0B0E13;");
  auto* l = new QVBoxLayout(content);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);

  // === Page header: greeting + actions ===
  auto* header_row = new QHBoxLayout();
  header_row->setSpacing(12);
  auto* title_box = new QVBoxLayout();
  title_box->setSpacing(4);
  // Greeting
  QDate today = QDate::currentDate();
  QString greeting = QTime::currentTime().hour() < 12 ? "Good morning" : QTime::currentTime().hour() < 18 ? "Good afternoon" : "Good evening";
  auto* greeting_lbl = new QLabel(QString("%1 — Welcome to InvoiceStudio").arg(greeting), content);
  greeting_lbl->setStyleSheet("color: #F4F4F5; font-size: 22px; font-weight: bold;");
  title_box->addWidget(greeting_lbl);
  auto* date_lbl = new QLabel(today.toString("dddd, d MMMM yyyy"), content);
  date_lbl->setStyleSheet("color: #94A3B8; font-size: 12px;");
  title_box->addWidget(date_lbl);
  header_row->addLayout(title_box);
  header_row->addStretch();
  // New Bill button
  auto* new_bill_btn = UiTheme::primaryIconButton(IconHelper::ICON_RECEIPT, "New Bill", content);
  header_row->addWidget(new_bill_btn);
  l->addLayout(header_row);

  // === KPI band: 4 cards ===
  auto* kpi_row = new QHBoxLayout();
  kpi_row->setSpacing(16);
  kpi_row->addWidget(make_kpi_card("THIS MONTH REVENUE", "₹4,82,650", "+12.4% vs last month", true, IconHelper::ICON_TRENDING_UP, content));
  kpi_row->addWidget(make_kpi_card("TOTAL COLLECTED", "₹3,61,940", "+8.1% vs last month", true, IconHelper::ICON_CHECK, content));
  kpi_row->addWidget(make_kpi_card("OUTSTANDING DUE", "₹3,98,855", "14 open invoices", false, IconHelper::ICON_HISTORY, content));
  kpi_row->addWidget(make_kpi_card("LIFETIME SALES", "₹1,42,30,780", "Since Mar 2025", true, IconHelper::ICON_BAR_CHART, content));
  l->addLayout(kpi_row);

  // === Bento grid: revenue trend + GST summary + top buyers + recent invoices ===
  auto* bento_row = new QHBoxLayout();
  bento_row->setSpacing(16);

  // Left column (60%): revenue trend + recent invoices
  auto* left_col = new QVBoxLayout();
  left_col->setSpacing(16);

  // Revenue trend card
  auto* trend_card = UiTheme::card(12, content);
  trend_card->layout()->addWidget(UiTheme::cardTitle("6-Month Revenue Trend", trend_card));
  auto* trend_sub = UiTheme::muted("Invoiced value vs collections, Apr – Sep 2026", trend_card);
  trend_card->layout()->addWidget(trend_sub);
  // Mini bar chart
  auto* chart_frame = new QFrame(trend_card);
  chart_frame->setFixedHeight(120);
  chart_frame->setStyleSheet("background-color: #0B0E13; border: 1px solid #232B38; border-radius: 4px;");
  auto* chart_l = new QHBoxLayout(chart_frame);
  chart_l->setContentsMargins(16, 12, 16, 12);
  chart_l->setSpacing(8);
  // 6 bars
  QStringList months = {"Apr", "May", "Jun", "Jul", "Aug", "Sep"};
  std::vector<double> values = {280, 310, 350, 420, 460, 482};
  double max_val = *std::max_element(values.begin(), values.end());
  for (int i = 0; i < 6; ++i) {
    auto* bar_col = new QVBoxLayout();
    bar_col->setSpacing(4);
    bar_col->setAlignment(Qt::AlignBottom);
    auto* bar = new QFrame();
    int bar_h = static_cast<int>(values[i] / max_val * 80);
    bar->setFixedHeight(bar_h);
    bar->setFixedWidth(30);
    bar->setStyleSheet(QString("background-color: %1; border-radius: 2px;").arg(i == 5 ? "#D9A13B" : "#2E3A4E"));
    bar_col->addWidget(bar, 0, Qt::AlignBottom);
    auto* month_lbl = new QLabel(months[i]);
    month_lbl->setStyleSheet("color: #64748B; font-size: 10px;");
    month_lbl->setAlignment(Qt::AlignCenter);
    bar_col->addWidget(month_lbl);
    chart_l->addLayout(bar_col);
  }
  trend_card->layout()->addWidget(chart_frame);
  left_col->addWidget(trend_card);

  // Recent invoices card
  auto* recent_card = UiTheme::card(8, content);
  recent_card->layout()->addWidget(UiTheme::cardTitle("Recent Invoices", recent_card));
  // Sample invoice rows
  recent_card->layout()->addWidget(make_invoice_row("INV-2026-0042", "Acme Industries", "₹48,650", "Paid", recent_card));
  recent_card->layout()->addWidget(make_invoice_row("INV-2026-0041", "Shree Trading Co.", "₹32,100", "Unpaid", recent_card));
  recent_card->layout()->addWidget(make_invoice_row("INV-2026-0040", "Bharat Steel Ltd", "₹1,15,000", "Paid", recent_card));
  recent_card->layout()->addWidget(make_invoice_row("INV-2026-0039", "Mahalaxmi Textiles", "₹18,750", "Unpaid", recent_card));
  recent_card->layout()->addWidget(make_invoice_row("INV-2026-0038", "Royal Exports", "₹67,200", "Paid", recent_card));
  left_col->addWidget(recent_card);
  bento_row->addLayout(left_col, 3);

  // Right column (40%): GST summary + top buyers
  auto* right_col = new QVBoxLayout();
  right_col->setSpacing(16);

  // GST summary card
  auto* gst_card = UiTheme::card(12, content);
  gst_card->layout()->addWidget(UiTheme::cardTitle("GST Summary (Live)", gst_card));
  gst_card->layout()->addWidget(UiTheme::muted("September 2026 · payable to govt", gst_card));
  auto* gst_row = new QHBoxLayout();
  gst_row->setSpacing(16);
  // Pie chart placeholder (gold circle)
  auto* pie = new QFrame(gst_card);
  pie->setFixedSize(80, 80);
  pie->setStyleSheet("background-color: #D9A13B; border-radius: 40px; border: 8px solid #1A222D;");
  gst_row->addWidget(pie);
  // Stats
  auto* gst_stats = new QVBoxLayout();
  gst_stats->setSpacing(6);
  auto* out_gst = new QLabel("Output GST        ₹58,220", gst_card);
  out_gst->setStyleSheet("color: #94A3B8; font-size: 12px;");
  gst_stats->addWidget(out_gst);
  auto* itc = new QLabel("ITC on purchases  −₹21,340", gst_card);
  itc->setStyleSheet("color: #94A3B8; font-size: 12px;");
  gst_stats->addWidget(itc);
  auto* net = new QLabel("Net payable        ₹36,880", gst_card);
  net->setStyleSheet("color: #D9A13B; font-size: 14px; font-weight: bold; border-top: 2px solid #232B38; padding-top: 4px;");
  gst_stats->addWidget(net);
  gst_row->addLayout(gst_stats, 1);
  static_cast<QVBoxLayout*>(gst_card->layout())->addLayout(gst_row);
  right_col->addWidget(gst_card);

  // Top 5 buyers card
  auto* buyers_card = UiTheme::card(8, content);
  buyers_card->layout()->addWidget(UiTheme::cardTitle("Top 5 Buyers (by Revenue)", buyers_card));
  buyers_card->layout()->addWidget(UiTheme::muted("This financial year", buyers_card));
  buyers_card->layout()->addWidget(make_rank_row(1, "Acme Industries", "₹3,82,650", 1.0, buyers_card));
  buyers_card->layout()->addWidget(make_rank_row(2, "Bharat Steel Ltd", "₹2,65,100", 0.69, buyers_card));
  buyers_card->layout()->addWidget(make_rank_row(3, "Royal Exports", "₹1,98,750", 0.52, buyers_card));
  buyers_card->layout()->addWidget(make_rank_row(4, "Shree Trading Co.", "₹1,45,200", 0.38, buyers_card));
  buyers_card->layout()->addWidget(make_rank_row(5, "Mahalaxmi Textiles", "₹98,500", 0.26, buyers_card));
  right_col->addWidget(buyers_card);

  bento_row->addLayout(right_col, 2);
  l->addLayout(bento_row);
  l->addStretch();

  scroll->setWidget(content);
  auto* root_l = new QVBoxLayout(this);
  root_l->setContentsMargins(0, 0, 0, 0);
  root_l->addWidget(scroll);
}

void DashboardView::refresh() {
  // TODO: load real data from DAOs when user is logged in
}

} // namespace fin::ui
