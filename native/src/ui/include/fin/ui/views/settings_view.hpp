#pragma once
#include <QFrame>
class QTabWidget;
namespace fin::ui {
class SettingsView : public QFrame {
  Q_OBJECT
 public:
  explicit SettingsView(QWidget* parent = nullptr);
 private:
  QTabWidget* tabs_;
};
} // namespace fin::ui
