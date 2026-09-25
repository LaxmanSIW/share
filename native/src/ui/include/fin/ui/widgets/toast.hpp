// fin/ui/widgets/toast.hpp — Toast notification widget (port of Java Toast.java)
//
// Non-intrusive notification that slides in from the bottom-right, auto-
// dismisses after 4 seconds, supports success/warning/error/info variants.
// Skill rule §8: NEVER use exec() — Toast is modeless; uses QTimer::singleShot
// for auto-dismiss.
#pragma once
#include <QFrame>
#include <QLabel>
#include <QString>
#include <QTimer>

namespace fin::ui {

class Toast : public QFrame {
  Q_OBJECT
 public:
  enum class Kind { Success, Warning, Error, Info };

  static void success(const QString& message, int duration_ms = 4000);
  static void warning(const QString& message, int duration_ms = 4000);
  static void error(const QString& message,   int duration_ms = 6000);
  static void info(const QString& message,    int duration_ms = 4000);

  Toast(Kind kind, const QString& message, int duration_ms, QWidget* parent = nullptr);

 protected:
  void paintEvent(QPaintEvent* e) override;

 private:
  Kind     kind_;
  QString  message_;
  QTimer   dismiss_timer_;
};

} // namespace fin::ui
