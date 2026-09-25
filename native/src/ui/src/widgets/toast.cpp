// fin/ui/widgets/toast.cpp
#include "fin/ui/widgets/toast.hpp"
#include "fin/ui/icon_helper.hpp"

#include <QApplication>
#include <QPainter>
#include <QHBoxLayout>
#include <QMainWindow>
#include <QScreen>
#include <QGuiApplication>
#include <QPropertyAnimation>
#include <QGraphicsEffect>

namespace fin::ui {

Toast::Toast(Kind kind, const QString& message, int duration_ms, QWidget* parent)
  : QFrame(parent), kind_(kind), message_(message) {
  // Apply QSS class based on kind.
  QString class_name = "toast";
  switch (kind) {
    case Kind::Success: class_name += " toast-success"; break;
    case Kind::Warning: class_name += " toast-warning"; break;
    case Kind::Error:   class_name += " toast-error";   break;
    case Kind::Info:    class_name += " toast-info";    break;
  }
  setProperty("class", class_name);
  setAttribute(Qt::WA_DeleteOnClose);
  setAttribute(Qt::WA_ShowWithoutActivating);
  setWindowFlags(Qt::ToolTip | Qt::FramelessWindowHint);

  auto* l = new QHBoxLayout(this);
  l->setContentsMargins(16, 12, 16, 12);
  l->setSpacing(10);

  // Icon based on kind.
  std::string_view icon_name;
  QColor           icon_color;
  switch (kind) {
    case Kind::Success: icon_name = IconHelper::ICON_CHECK;     icon_color = QColor("#10B981"); break;
    case Kind::Warning: icon_name = IconHelper::ICON_TAG;        icon_color = QColor("#F59E0B"); break;
    case Kind::Error:   icon_name = IconHelper::ICON_CLOSE;     icon_color = QColor("#EF4444"); break;
    case Kind::Info:    icon_name = IconHelper::ICON_HELP;       icon_color = QColor("#38BDF8"); break;
  }
  auto* icon_lbl = new QLabel(this);
  icon_lbl->setPixmap(IconHelper::pixmap(icon_name, 16, icon_color));
  l->addWidget(icon_lbl);

  auto* text_lbl = new QLabel(message, this);
  text_lbl->setStyleSheet("color: #F4F4F5; font-size: 13px;");
  text_lbl->setWordWrap(true);
  text_lbl->setMaximumWidth(400);
  l->addWidget(text_lbl, 1);

  // Auto-dismiss after duration.
  dismiss_timer_.setInterval(duration_ms);
  dismiss_timer_.setSingleShot(true);
  connect(&dismiss_timer_, &QTimer::timeout, this, [this] {
    auto* anim = new QPropertyAnimation(this, "windowOpacity", this);
    anim->setDuration(250);
    anim->setStartValue(1.0);
    anim->setEndValue(0.0);
    connect(anim, &QPropertyAnimation::finished, this, &Toast::close);
    anim->start();
  });
  dismiss_timer_.start();
}

void Toast::paintEvent(QPaintEvent* /*e*/) {
  // QSS handles the background; nothing else to do.
  QFrame::paintEvent(static_cast<QPaintEvent*>(nullptr));
}

void Toast::success(const QString& msg, int dur) {
  auto* t = new Toast(Kind::Success, msg, dur);
  // Position bottom-right of the active window.
  auto* parent = QApplication::activeWindow();
  if (!parent) parent = QApplication::topLevelWidgets().value(0);
  if (parent) {
    QPoint p = parent->geometry().bottomRight() - QPoint(t->sizeHint().width() + 24, -8);
    t->setParent(parent);
    t->move(p);
  }
  t->show();
}
void Toast::warning(const QString& msg, int dur) {
  auto* t = new Toast(Kind::Warning, msg, dur);
  auto* parent = QApplication::activeWindow();
  if (parent) {
    t->setParent(parent);
    t->move(parent->geometry().bottomRight() - QPoint(t->sizeHint().width() + 24, -8));
  }
  t->show();
}
void Toast::error(const QString& msg, int dur) {
  auto* t = new Toast(Kind::Error, msg, dur);
  auto* parent = QApplication::activeWindow();
  if (parent) {
    t->setParent(parent);
    t->move(parent->geometry().bottomRight() - QPoint(t->sizeHint().width() + 24, -8));
  }
  t->show();
}
void Toast::info(const QString& msg, int dur) {
  auto* t = new Toast(Kind::Info, msg, dur);
  auto* parent = QApplication::activeWindow();
  if (parent) {
    t->setParent(parent);
    t->move(parent->geometry().bottomRight() - QPoint(t->sizeHint().width() + 24, -8));
  }
  t->show();
}

} // namespace fin::ui
