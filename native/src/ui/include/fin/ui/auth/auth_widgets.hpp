// fin/ui/auth/auth_widgets.hpp — Auth UI components (consolidated)
//
// Port of Java auth/GoogleSignInButton, auth/LogoutDialog, auth/PasswordFieldWithToggle,
// auth/PasswordStrengthMeter. Consolidated into one header because they're
// tightly coupled and small.
#pragma once
#include <QLineEdit>
#include <QPushButton>
#include <QLabel>
#include <QFrame>
#include <functional>
#include <QString>

namespace fin::ui {

/// Google sign-in button — Google "G" logo + "Continue with Google" label.
/// On click, calls the on_clicked callback (which triggers the OAuth device flow).
class GoogleSignInButton : public QPushButton {
  Q_OBJECT
 public:
  explicit GoogleSignInButton(QWidget* parent = nullptr);
  void set_on_clicked(std::function<void()> cb) { on_clicked_ = std::move(cb); }
 private:
  std::function<void()> on_clicked_;
};

/// Logout confirmation dialog. Asks the user "Sign out?" with Yes/Cancel.
class LogoutDialog {
 public:
  static void open(std::function<void(bool)> on_done);
};

/// Password field with show/hide toggle (eye icon).
class PasswordFieldWithToggle : public QLineEdit {
  Q_OBJECT
 public:
  explicit PasswordFieldWithToggle(QWidget* parent = nullptr);
 private slots:
  void toggle_visibility_();
 private:
  QPushButton* toggle_btn_;
  bool visible_{false};
};

/// Password strength meter — 4-segment bar that fills green/yellow/red
/// based on the password's strength (length + char class count).
class PasswordStrengthMeter : public QFrame {
  Q_OBJECT
 public:
  explicit PasswordStrengthMeter(QWidget* parent = nullptr);
  /// Update the meter based on the given password.
  void update_password(const QString& password);
 private:
  QFrame* segments_[4];
  QLabel* label_;
};

} // namespace fin::ui
