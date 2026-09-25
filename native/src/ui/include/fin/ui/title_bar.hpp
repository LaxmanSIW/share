// fin/ui/title_bar.hpp — Custom frameless title bar (port of Java TitleBarTheme.java)
//
// The Java original uses native Win32 DwmSetWindowAttribute to recolor the OS
// title bar to match the "Obsidian & Gold" theme. For Qt we take the simpler
// approach: make the window frameless and draw our own title bar with
// brand colors + custom min/max/close buttons. This is cross-platform
// (Windows/macOS/Linux) and avoids any native chrome bleed.
#pragma once
#include <QFrame>
#include <QLabel>
#include <QPushButton>
#include <QString>

class QPoint;
class QMainWindow;

namespace fin::ui {

class TitleBar : public QFrame {
  Q_OBJECT
 public:
  explicit TitleBar(QMainWindow* parent = nullptr);

  void setTitle(const QString& title);

 protected:
  void mousePressEvent(QMouseEvent* e) override;
  void mouseMoveEvent(QMouseEvent* e) override;
  void mouseDoubleClickEvent(QMouseEvent* e) override;

 private slots:
  void onMinimize();
  void onMaximizeRestore();
  void onClose();

 private:
  QMainWindow* window_;
  QLabel*      title_label_;
  QPushButton* min_btn_;
  QPushButton* max_btn_;
  QPushButton* close_btn_;
  QPoint       drag_offset_;
  bool         dragging_{false};
};

} // namespace fin::ui
