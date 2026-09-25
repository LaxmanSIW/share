// fin/ui/views/dashboard_view.hpp — Dashboard view
//
// Port of Java DashboardView.java + Dashboard2View.java. Shows KPI cards +
// recent activity + charts. This is the landing view after login.
#pragma once
#include <QFrame>

namespace fin::ui {

class DashboardView : public QFrame {
  Q_OBJECT
 public:
  explicit DashboardView(QWidget* parent = nullptr);
  void refresh();
};

} // namespace fin::ui
