// fin/ui/auth/auth_widgets.cpp
#include "fin/ui/auth/auth_widgets.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/widgets/dialog_helper.hpp"
#include "fin/ui/icon_helper.hpp"

#include <QHBoxLayout>
#include <QVBoxLayout>
#include <QApplication>
#include <QDialog>
#include <QDialogButtonBox>
#include <QFormLayout>
#include <QLabel>
#include <QPushButton>

namespace fin::ui {

// === GoogleSignInButton ===
// Google "G" SVG path (multicolor); we render a simplified version in a single color.
GoogleSignInButton::GoogleSignInButton(QWidget* parent) : QPushButton(parent) {
  setText("  Continue with Google");
  setProperty("class", "ghost-button");
  setCursor(Qt::PointingHandCursor);
  setMinimumHeight(40);
  setIcon(IconHelper::icon(IconHelper::ICON_PERSON, 16, QColor("#F4F4F5")));
  setIconSize(QSize(16, 16));
  connect(this, &QPushButton::clicked, this, [this]{
    if (on_clicked_) on_clicked_();
  });
}

// === LogoutDialog ===
void LogoutDialog::open(std::function<void(bool)> on_done) {
  DialogHelper::confirm("Sign out?",
    "You'll be signed out of InvoiceStudio. Your local data will be cleared on next launch.",
    std::move(on_done));
}

// === PasswordFieldWithToggle ===
PasswordFieldWithToggle::PasswordFieldWithToggle(QWidget* parent) : QLineEdit(parent) {
  setEchoMode(QLineEdit::Password);
  setMinimumHeight(36);
  // Add the eye toggle button to the right side of the line edit.
  toggle_btn_ = new QPushButton(this);
  toggle_btn_->setCursor(Qt::PointingHandCursor);
  toggle_btn_->setFlat(true);
  toggle_btn_->setIcon(IconHelper::icon(IconHelper::ICON_SEARCH, 14, QColor("#94A3B8")));
  toggle_btn_->setIconSize(QSize(14, 14));
  toggle_btn_->setFixedSize(24, 24);
  // Position the toggle button inside the field on the right.
  auto* layout_action = addAction(toggle_btn_, QLineEdit::TrailingPosition);
  (void)layout_action;
  connect(toggle_btn_, &QPushButton::clicked, this, &PasswordFieldWithToggle::toggle_visibility_);
}

void PasswordFieldWithToggle::toggle_visibility_() {
  visible_ = !visible_;
  setEchoMode(visible_ ? QLineEdit::Normal : QLineEdit::Password);
  toggle_btn_->setIcon(IconHelper::icon(visible_ ? IconHelper::ICON_SEARCH : IconHelper::ICON_SEARCH,
                                         14, QColor(visible_ ? "#D9A13B" : "#94A3B8")));
}

// === PasswordStrengthMeter ===
PasswordStrengthMeter::PasswordStrengthMeter(QWidget* parent) : QFrame(parent) {
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(0, 4, 0, 0);
  l->setSpacing(4);
  auto* seg_row = new QHBoxLayout();
  seg_row->setContentsMargins(0, 0, 0, 0);
  seg_row->setSpacing(4);
  for (int i = 0; i < 4; ++i) {
    segments_[i] = new QFrame(this);
    segments_[i]->setFixedHeight(4);
    segments_[i]->setStyleSheet("background-color: #232B38; border-radius: 2px;");
    seg_row->addWidget(segments_[i]);
  }
  l->addLayout(seg_row);
  label_ = new QLabel("", this);
  label_->setStyleSheet("color: #64748B; font-size: 10px;");
  l->addWidget(label_);
}

void PasswordStrengthMeter::update_password(const QString& password) {
  // Score: length (up to 4) + char class count (up to 4) → 0..8.
  int score = 0;
  if (password.length() >= 8)  score++;
  if (password.length() >= 12) score++;
  if (password.length() >= 16) score++;
  if (password.length() >= 20) score++;
  bool has_lower = false, has_upper = false, has_digit = false, has_sym = false;
  for (QChar c : password) {
    if (c.isLower()) has_lower = true;
    else if (c.isUpper()) has_upper = true;
    else if (c.isDigit()) has_digit = true;
    else if (!c.isSpace()) has_sym = true;
  }
  if (has_lower) score++;
  if (has_upper) score++;
  if (has_digit) score++;
  if (has_sym)   score++;

  int level = (score >= 7) ? 4 : (score >= 5) ? 3 : (score >= 3) ? 2 : (score >= 1) ? 1 : 0;
  // Strength tier: 0=empty, 1=weak (red), 2=fair (orange), 3=good (yellow), 4=strong (green).
  const char* colors[] = {"#232B38", "#EF4444", "#F59E0B", "#84CC16", "#10B981"};
  const char* labels[] = {"", "Weak", "Fair", "Good", "Strong"};
  for (int i = 0; i < 4; ++i) {
    segments_[i]->setStyleSheet(QString("background-color: %1; border-radius: 2px;").arg(
      i < level ? colors[level] : "#232B38"));
  }
  label_->setText(labels[level]);
  label_->setStyleSheet(QString("color: %1; font-size: 10px;").arg(colors[level == 0 ? 4 : level]));
}

} // namespace fin::ui
