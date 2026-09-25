#pragma once
#include <QFrame>
class QTabWidget;
namespace fin::ui {
class ReportsView : public QFrame {
  Q_OBJECT
 public:
  explicit ReportsView(QWidget* parent = nullptr);
  void refresh();
 private:
  QTabWidget* tabs_;
};
} // namespace fin::ui
