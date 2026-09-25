// fin/ui/auth/auth_view.cpp — Firebase auth view (login screen)
#include "fin/ui/auth/auth_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"

#include "fin/services/firebase_auth.hpp"
#include "fin/services/auth_session.hpp"

#include <QApplication>
#include <QFrame>
#include <QHBoxLayout>
#include <QVBoxLayout>
#include <QLabel>
#include <QLineEdit>
#include <QPushButton>
#include <QStackedWidget>
#include <QScrollArea>
#include <QTimer>

namespace fin::ui {

AuthView::AuthView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  setMinimumSize(800, 600);

  auto* scroll = new QScrollArea(this);
  scroll->setWidgetResizable(true);
  scroll->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
  scroll->setFrameShape(QFrame::NoFrame);

  auto* centering = new QFrame(scroll);
  centering->setLayout(new QVBoxLayout(centering));
  centering->layout()->setAlignment(Qt::AlignCenter);

  // Brand block at top.
  auto* brand_block = UiTheme::card(8, centering);
  brand_block->setMaximumWidth(440);
  brand_block->layout()->setAlignment(Qt::AlignCenter);

  auto* brand_row = UiTheme::row(12, brand_block);
  auto* icon_box = new QFrame(brand_row);
  icon_box->setFixedSize(48, 48);
  icon_box->setStyleSheet("background-color: #D9A13B; border-radius: 8px;");
  auto* icon_l = new QVBoxLayout(icon_box);
  icon_l->setContentsMargins(0, 0, 0, 0);
  auto* brand_icon = new QLabel(icon_box);
  brand_icon->setPixmap(IconHelper::pixmap(IconHelper::ICON_RECEIPT, 22, QColor("#0B0E13")));
  brand_icon->setAlignment(Qt::AlignCenter);
  icon_l->addWidget(brand_icon);
  brand_row->layout()->addWidget(icon_box);

  auto* brand_text = new QVBoxLayout();
  brand_text->setSpacing(0);
  auto* title = UiTheme::pageTitle("InvoiceStudio", brand_block);
  auto* subtitle = UiTheme::pageSubtitle("Sign in to continue", brand_block);
  brand_text->addWidget(title);
  brand_text->addWidget(subtitle);
  brand_row->layout()->addItem(brand_text);
  brand_row->layout()->addItem(UiTheme::hspacer());
  static_cast<QVBoxLayout*>(brand_block->layout())->addWidget(brand_row);

  // The stacked widget holds different form per state.
  stack_ = new QStackedWidget(brand_block);
  static_cast<QVBoxLayout*>(brand_block->layout())->addWidget(stack_);

  // Build each state's form.
  render_state_(State::SignIn);

  centering->layout()->addWidget(brand_block);
  scroll->setWidget(centering);

  auto* root_l = new QVBoxLayout(this);
  root_l->setContentsMargins(0, 0, 0, 0);
  root_l->addWidget(scroll);
}

void AuthView::set_state(State s) {
  state_ = s;
  render_state_(s);
}

void AuthView::render_state_(State s) {
  // Clear existing stack (rebuild each state fresh).
  while (stack_->count() > 0) {
    auto* w = stack_->widget(0);
    stack_->removeWidget(w);
    w->deleteLater();
  }

  auto* form = new QFrame(stack_);
  auto* l = new QVBoxLayout(form);
  l->setSpacing(12);

  if (s == State::SignIn) {
    auto* email = UiTheme::lineEdit("you@example.com", form);
    email->setObjectName("emailField");
    l->addWidget(UiTheme::muted("Email", form));
    l->addWidget(email);

    auto* pass = UiTheme::lineEdit("••••••••", form);
    pass->setObjectName("passwordField");
    pass->setEchoMode(QLineEdit::Password);
    l->addWidget(UiTheme::muted("Password", form));
    l->addWidget(pass);

    auto* row = UiTheme::row(8, form);
    auto* sign_in = UiTheme::primaryButton("Sign in", form);
    sign_in->setObjectName("signInButton");
    connect(sign_in, &QPushButton::clicked, this, &AuthView::attempt_sign_in_);
    row->layout()->addWidget(sign_in);
    row->layout()->addItem(UiTheme::hspacer());

    auto* forgot = UiTheme::ghostButton("Forgot password?", form);
    connect(forgot, &QPushButton::clicked, this, [this]{ set_state(State::ForgotPassword); });
    row->layout()->addWidget(forgot);
    l->addWidget(row);

    auto* row2 = UiTheme::row(8, form);
    auto* no_account = UiTheme::muted("Don't have an account?", form);
    row2->layout()->addWidget(no_account);
    auto* sign_up = UiTheme::ghostButton("Sign up", form);
    connect(sign_up, &QPushButton::clicked, this, [this]{ set_state(State::SignUp); });
    row2->layout()->addItem(UiTheme::hspacer());
    row2->layout()->addWidget(sign_up);
    l->addWidget(row2);
  } else if (s == State::SignUp) {
    auto* email = UiTheme::lineEdit("you@example.com", form);
    l->addWidget(UiTheme::muted("Email", form));
    l->addWidget(email);
    auto* pass = UiTheme::lineEdit("••••••••", form);
    pass->setEchoMode(QLineEdit::Password);
    l->addWidget(UiTheme::muted("Password", form));
    l->addWidget(pass);
    auto* row = UiTheme::row(8, form);
    auto* sign_up = UiTheme::primaryButton("Create account", form);
    connect(sign_up, &QPushButton::clicked, this, &AuthView::attempt_sign_up_);
    row->layout()->addWidget(sign_up);
    row->layout()->addItem(UiTheme::hspacer());
    auto* back = UiTheme::ghostButton("Back to sign in", form);
    connect(back, &QPushButton::clicked, this, [this]{ set_state(State::SignIn); });
    row->layout()->addWidget(back);
    l->addWidget(row);
  } else if (s == State::ForgotPassword) {
    auto* email = UiTheme::lineEdit("you@example.com", form);
    l->addWidget(UiTheme::muted("Enter your email — we'll send a reset link", form));
    l->addWidget(email);
    auto* row = UiTheme::row(8, form);
    auto* send = UiTheme::primaryButton("Send reset link", form);
    connect(send, &QPushButton::clicked, this, [this, email]{
      last_reset_email_ = email->text();
      set_state(State::CheckEmail);
    });
    row->layout()->addWidget(send);
    row->layout()->addItem(UiTheme::hspacer());
    auto* back = UiTheme::ghostButton("Back", form);
    connect(back, &QPushButton::clicked, this, [this]{ set_state(State::SignIn); });
    row->layout()->addWidget(back);
    l->addWidget(row);
  } else if (s == State::CheckEmail) {
    l->addWidget(new QLabel(QString("Reset link sent to %1. Check your inbox.").arg(last_reset_email_), form));
    auto* row = UiTheme::row(8, form);
    auto* back = UiTheme::primaryButton("Back to sign in", form);
    connect(back, &QPushButton::clicked, this, [this]{ set_state(State::SignIn); });
    row->layout()->addWidget(back);
    l->addWidget(row);
  } else { // LoggedOut
    l->addWidget(new QLabel("You have been signed out.", form));
    auto* row = UiTheme::row(8, form);
    auto* back = UiTheme::primaryButton("Sign in again", form);
    connect(back, &QPushButton::clicked, this, [this]{ set_state(State::SignIn); });
    row->layout()->addWidget(back);
    l->addWidget(row);
  }

  stack_->addWidget(form);
  stack_->setCurrentWidget(form);
}

void AuthView::attempt_sign_in_() {
  auto* email_field = stack_->currentWidget()->findChild<QLineEdit*>("emailField");
  auto* pass_field  = stack_->currentWidget()->findChild<QLineEdit*>("passwordField");
  if (!email_field || !pass_field) return;
  auto email = email_field->text().toStdString();
  auto pass  = pass_field->text().toStdString();
  if (email.empty() || pass.empty()) return;

  auto* sign_in_btn = stack_->currentWidget()->findChild<QPushButton*>("signInButton");
  if (sign_in_btn) {
    sign_in_btn->setEnabled(false);
    sign_in_btn->setText("Signing in…");
  }

  fin::services::FirebaseAuthService::sign_in_with_email_password(
      email, pass, /*remember_me=*/true,
      [this](fin::services::AuthResult result) {
        if (sign_in_btn = stack_->currentWidget()->findChild<QPushButton*>("signInButton"); sign_in_btn) {
          sign_in_btn->setEnabled(true);
          sign_in_btn->setText("Sign in");
        }
        if (result.ok) {
          fin::services::AuthSessionManager::set_current_user_id(
              fin::UserId{std::stoll(result.session.user_id)});
          if (on_success_) on_success_();
        } else {
          // Show error inline. Real impl: a Toast / inline label.
          qApp->beep();
        }
      });
}

void AuthView::attempt_sign_up_() {
  auto* email_field = stack_->currentWidget()->findChild<QLineEdit*>();
  // Simplified: would walk the form to grab email + password fields.
  // For now, just go back to sign-in after submit.
  set_state(State::SignIn);
}

} // namespace fin::ui
