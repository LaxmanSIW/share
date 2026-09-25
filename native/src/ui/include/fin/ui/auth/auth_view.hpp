// fin/ui/auth/auth_view.hpp — Auth view (port of Java AuthView.java)
//
// Login / sign-up / forgot-password / set-new-password / logged-out — 5 states.
// Uses FirebaseAuthService (Phase 3b) for the actual auth, delivered on the
// UI thread via QTimer::singleShot(0). On success, calls on_success callback.
#pragma once
#include <QFrame>
#include <functional>

namespace fin::ui {

class AuthView : public QFrame {
  Q_OBJECT
 public:
  enum class State {
    SignIn,
    SignUp,
    ForgotPassword,
    CheckEmail,
    SetNewPassword,
    LoggedOut,
  };

  explicit AuthView(QWidget* parent = nullptr);

  void set_on_success(std::function<void()> cb) { on_success_ = std::move(cb); }
  void set_state(State s);

 private:
  void render_state_(State s);
  void attempt_sign_in_();
  void attempt_sign_up_();

  State                state_{State::SignIn};
  std::function<void()> on_success_;
  class QStackedWidget* stack_;
  QString              last_reset_email_;
};

} // namespace fin::ui
