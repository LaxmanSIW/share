// fin/ui/ui_theme.cpp
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"

#include <QApplication>
#include <QFile>
#include <QTextStream>
#include <QSizePolicy>
#include <QObject>

namespace fin::ui {

// === Layout ===

QFrame* UiTheme::page(QWidget* parent) {
  auto* page = new QFrame(parent);
  page->setProperty("class", "view-page");
  page->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(page);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(20);
  return page;
}

QFrame* UiTheme::card(int spacing, QWidget* parent) {
  auto* card = new QFrame(parent);
  card->setProperty("class", "card");
  card->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Preferred);
  auto* l = new QVBoxLayout(card);
  l->setContentsMargins(16, 16, 16, 16);
  l->setSpacing(spacing);
  return card;
}

QWidget* UiTheme::row(int spacing, QWidget* parent) {
  auto* row = new QWidget(parent);
  row->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Preferred);
  auto* l = new QHBoxLayout(row);
  l->setContentsMargins(0, 0, 0, 0);
  l->setSpacing(spacing);
  return row;
}

QSpacerItem* UiTheme::hspacer() {
  return new QSpacerItem(0, 0, QSizePolicy::Expanding, QSizePolicy::Minimum);
}

QSpacerItem* UiTheme::vspacer() {
  return new QSpacerItem(0, 0, QSizePolicy::Minimum, QSizePolicy::Expanding);
}

// === Typography ===

QLabel* UiTheme::pageTitle(const QString& text, QWidget* parent) {
  auto* l = new QLabel(text, parent);
  l->setProperty("class", "view-title");
  return l;
}

QLabel* UiTheme::pageSubtitle(const QString& text, QWidget* parent) {
  auto* l = new QLabel(text, parent);
  l->setProperty("class", "view-subtitle");
  return l;
}

QLabel* UiTheme::headingLarge(const QString& text, QWidget* parent) {
  auto* l = new QLabel(text, parent);
  l->setProperty("class", "heading-l");
  return l;
}

QLabel* UiTheme::cardTitle(const QString& text, QWidget* parent) {
  auto* l = new QLabel(text, parent);
  l->setProperty("class", "card-title");
  return l;
}

QLabel* UiTheme::cardValue(const QString& text, QWidget* parent) {
  auto* l = new QLabel(text, parent);
  l->setProperty("class", "card-value");
  return l;
}

QLabel* UiTheme::muted(const QString& text, QWidget* parent) {
  auto* l = new QLabel(text, parent);
  l->setProperty("class", "muted-label");
  return l;
}

QLabel* UiTheme::micro(const QString& text, QWidget* parent) {
  auto* l = new QLabel(text, parent);
  l->setProperty("class", "micro-label");
  return l;
}

QLabel* UiTheme::bold(const QString& text, QWidget* parent) {
  auto* l = new QLabel(text, parent);
  l->setProperty("class", "bold-label");
  return l;
}

// === Badges ===

QLabel* UiTheme::badge(const QString& text, Badge kind, QWidget* parent) {
  auto* l = new QLabel(text, parent);
  l->setAlignment(Qt::AlignCenter);
  l->setProperty("class", "badge");
  switch (kind) {
    case Badge::Warning: l->setProperty("badge", "warning"); break;
    case Badge::Success: l->setProperty("badge", "success"); break;
    case Badge::Error:   l->setProperty("badge", "error");   break;
    case Badge::Accent:  l->setProperty("badge", "accent");  break;
    case Badge::Neutral: l->setProperty("badge", "neutral"); break;
  }
  // Force re-evaluation of QSS by re-polishing.
  l->style()->unpolish(l);
  l->style()->polish(l);
  return l;
}

// === Buttons ===

QPushButton* UiTheme::primaryButton(const QString& text, QWidget* parent) {
  auto* b = new QPushButton(text, parent);
  b->setProperty("class", "accent-gold");
  b->setCursor(Qt::PointingHandCursor);
  return b;
}

QPushButton* UiTheme::ghostButton(const QString& text, QWidget* parent) {
  auto* b = new QPushButton(text, parent);
  b->setProperty("class", "ghost-button");
  b->setCursor(Qt::PointingHandCursor);
  return b;
}

QPushButton* UiTheme::iconButton(std::string_view icon_name, int size,
                                  const QString& tooltip, QWidget* parent) {
  auto* b = new QPushButton(parent);
  b->setProperty("class", "icon-button");
  b->setIcon(fin::ui::IconHelper::icon(icon_name, size, QColor("#94A3B8")));
  b->setIconSize(QSize(size, size));
  b->setFixedSize(size + 12, size + 12);
  b->setCursor(Qt::PointingHandCursor);
  if (!tooltip.isEmpty()) b->setToolTip(tooltip);
  return b;
}

QPushButton* UiTheme::primaryIconButton(std::string_view icon_name, const QString& text, QWidget* parent) {
  auto* b = new QPushButton(text, parent);
  b->setProperty("class", "accent-gold");
  b->setIcon(fin::ui::IconHelper::icon(icon_name, 14, QColor("#0B0E13")));
  b->setIconSize(QSize(14, 14));
  b->setCursor(Qt::PointingHandCursor);
  return b;
}

// === Inputs ===

QLineEdit* UiTheme::lineEdit(const QString& placeholder, QWidget* parent) {
  auto* e = new QLineEdit(parent);
  if (!placeholder.isEmpty()) e->setPlaceholderText(placeholder);
  e->setMinimumHeight(36);
  return e;
}

// === Apply theme ===

void UiTheme::apply_theme(QApplication* app) {
  // Try embedded Qt resource first, then filesystem.
  QFile qss_file(":/qss/globalfile.qss");
  if (!qss_file.open(QIODevice::ReadOnly | QIODevice::Text)) {
    // Fall back to filesystem (development without resources).
    qss_file.setFileName("resources/css/globalfile.qss");
    if (!qss_file.open(QIODevice::ReadOnly | QIODevice::Text)) {
      return;
    }
  }
  QTextStream ts(&qss_file);
  app->setStyleSheet(ts.readAll());
}

} // namespace fin::ui
