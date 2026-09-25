// fin/ui/widgets/dialog_helper.cpp
#include <QApplication>
#include <QStyle>
#include <QVBoxLayout>
#include "fin/ui/widgets/dialog_helper.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"

#include <QDialog>
#include <QDialogButtonBox>
#include <QLabel>
#include <QLineEdit>
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QPushButton>
#include <QFrame>

namespace fin::ui {

namespace {

QDialog* make_base_dialog(const QString& title, QWidget* parent) {
  auto* dlg = new QDialog(parent);
  dlg->setWindowTitle(title);
  dlg->setModal(false);  // skill rule §8: modeless, open() instead of exec()
  dlg->setProperty("class", "auth-card");
  auto* l = new QVBoxLayout(dlg);
  l->setContentsMargins(20, 20, 20, 16);
  l->setSpacing(12);
  return dlg;
}

QDialogButtonBox* make_buttons(QDialog* dlg, QDialogButtonBox::StandardButtons buttons) {
  auto* bb = new QDialogButtonBox(buttons, dlg);
  // Style the OK button as accent-gold.
  auto* ok_btn = bb->button(QDialogButtonBox::Ok);
  if (ok_btn) ok_btn->setProperty("class", "accent-gold");
  auto* cancel_btn = bb->button(QDialogButtonBox::Cancel);
  if (cancel_btn) cancel_btn->setProperty("class", "ghost-button");
  return bb;
}

} // namespace

void DialogHelper::confirm(const QString& title, const QString& message,
                            std::function<void(bool)> on_done) {
  auto* parent = QApplication::activeWindow();
  auto* dlg = make_base_dialog(title, parent);
  auto* l = dlg->layout();

  auto* msg_row = new QHBoxLayout();
  msg_row->setSpacing(12);
  auto* icon = new QLabel(dlg);
  icon->setPixmap(IconHelper::pixmap(IconHelper::ICON_HELP, 24, QColor("#38BDF8")));
  msg_row->addWidget(icon, 0, Qt::AlignTop);

  auto* msg = new QLabel(message, dlg);
  msg->setWordWrap(true);
  msg->setStyleSheet("color: #F4F4F5; font-size: 13px;");
  msg->setMinimumWidth(360);
  msg_row->addWidget(msg, 1);
  static_cast<QVBoxLayout*>(l)->addLayout(msg_row);

  auto* bb = make_buttons(dlg, QDialogButtonBox::Ok | QDialogButtonBox::Cancel);
  l->addWidget(bb);

  QObject::connect(bb, &QDialogButtonBox::accepted, dlg, [dlg, on_done]() {
    dlg->accept();
    if (on_done) on_done(true);
  });
  QObject::connect(bb, &QDialogButtonBox::rejected, dlg, [dlg, on_done]() {
    dlg->reject();
    if (on_done) on_done(false);
  });
  dlg->setAttribute(Qt::WA_DeleteOnClose);
  dlg->open();
}

void DialogHelper::confirm_delete(const QString& title, const QString& message,
                                    std::function<void(bool)> on_done) {
  auto* parent = QApplication::activeWindow();
  auto* dlg = make_base_dialog(title, parent);
  auto* l = dlg->layout();

  auto* msg_row = new QHBoxLayout();
  msg_row->setSpacing(12);
  auto* icon = new QLabel(dlg);
  icon->setPixmap(IconHelper::pixmap(IconHelper::ICON_TRASH, 24, QColor("#EF4444")));
  msg_row->addWidget(icon, 0, Qt::AlignTop);

  auto* msg = new QLabel(message, dlg);
  msg->setWordWrap(true);
  msg->setStyleSheet("color: #F4F4F5; font-size: 13px;");
  msg->setMinimumWidth(360);
  msg_row->addWidget(msg, 1);
  static_cast<QVBoxLayout*>(l)->addLayout(msg_row);

  auto* bb = new QDialogButtonBox(dlg);
  auto* del_btn = bb->addButton("Delete", QDialogButtonBox::AcceptRole);
  del_btn->setStyleSheet("background-color: #EF4444; color: #F4F4F5; border: 1px solid #EF4444; "
                          "border-radius: 6px; padding: 8px 16px; font-weight: bold;");
  auto* cancel_btn = bb->addButton("Cancel", QDialogButtonBox::RejectRole);
  cancel_btn->setProperty("class", "ghost-button");
  QApplication::style()->unpolish(cancel_btn);
  QApplication::style()->polish(cancel_btn);
  l->addWidget(bb);

  QObject::connect(bb, &QDialogButtonBox::accepted, dlg, [dlg, on_done]() {
    dlg->accept();
    if (on_done) on_done(true);
  });
  QObject::connect(bb, &QDialogButtonBox::rejected, dlg, [dlg, on_done]() {
    dlg->reject();
    if (on_done) on_done(false);
  });
  dlg->setAttribute(Qt::WA_DeleteOnClose);
  dlg->open();
}

void DialogHelper::info(const QString& title, const QString& message, std::function<void()> on_done) {
  auto* parent = QApplication::activeWindow();
  auto* dlg = make_base_dialog(title, parent);
  auto* l = dlg->layout();
  auto* msg = new QLabel(message, dlg);
  msg->setWordWrap(true);
  msg->setStyleSheet("color: #F4F4F5; font-size: 13px;");
  msg->setMinimumWidth(360);
  l->addWidget(msg);

  auto* bb = make_buttons(dlg, QDialogButtonBox::Ok);
  l->addWidget(bb);

  QObject::connect(bb, &QDialogButtonBox::accepted, dlg, [dlg, on_done]() {
    dlg->accept();
    if (on_done) on_done();
  });
  dlg->setAttribute(Qt::WA_DeleteOnClose);
  dlg->open();
}

void DialogHelper::error(const QString& title, const QString& message, std::function<void()> on_done) {
  auto* parent = QApplication::activeWindow();
  auto* dlg = make_base_dialog(title, parent);
  auto* l = dlg->layout();
  auto* msg_row = new QHBoxLayout();
  msg_row->setSpacing(12);
  auto* icon = new QLabel(dlg);
  icon->setPixmap(IconHelper::pixmap(IconHelper::ICON_CLOSE, 24, QColor("#EF4444")));
  msg_row->addWidget(icon, 0, Qt::AlignTop);
  auto* msg = new QLabel(message, dlg);
  msg->setWordWrap(true);
  msg->setStyleSheet("color: #F4F4F5; font-size: 13px;");
  msg->setMinimumWidth(360);
  msg_row->addWidget(msg, 1);
  static_cast<QVBoxLayout*>(l)->addLayout(msg_row);

  auto* bb = make_buttons(dlg, QDialogButtonBox::Ok);
  l->addWidget(bb);

  QObject::connect(bb, &QDialogButtonBox::accepted, dlg, [dlg, on_done]() {
    dlg->accept();
    if (on_done) on_done();
  });
  dlg->setAttribute(Qt::WA_DeleteOnClose);
  dlg->open();
}

void DialogHelper::input(const QString& title, const QString& label, const QString& default_value,
                          std::function<void(QString)> on_done) {
  auto* parent = QApplication::activeWindow();
  auto* dlg = make_base_dialog(title, parent);
  auto* l = dlg->layout();
  auto* lbl = new QLabel(label, dlg);
  lbl->setStyleSheet("color: #94A3B8; font-size: 12px;");
  l->addWidget(lbl);
  auto* edit = new QLineEdit(default_value, dlg);
  edit->setMinimumHeight(36);
  l->addWidget(edit);
  auto* bb = make_buttons(dlg, QDialogButtonBox::Ok | QDialogButtonBox::Cancel);
  l->addWidget(bb);
  QObject::connect(bb, &QDialogButtonBox::accepted, dlg, [dlg, edit, on_done]() {
    dlg->accept();
    if (on_done) on_done(edit->text());
  });
  QObject::connect(bb, &QDialogButtonBox::rejected, dlg, [dlg, on_done]() {
    dlg->reject();
    if (on_done) on_done(QString());
  });
  dlg->setAttribute(Qt::WA_DeleteOnClose);
  dlg->open();
  edit->setFocus();
  edit->selectAll();
}

} // namespace fin::ui
