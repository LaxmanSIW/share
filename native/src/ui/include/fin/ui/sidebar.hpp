// fin/ui/sidebar.hpp — Sidebar navigation (port of Java SidebarController.java)
//
// Skill rule 5.2 role 2: owns nav buttons, active-view highlighting, and the
// user profile pill. View construction is delegated back to the shell's
// public navigation API (StudioApp::show_view).
#pragma once
#include <QFrame>
#include <QLabel>
#include <QPushButton>
#include <QHash>
#include <QString>
#include <functional>
#include <string_view>

namespace fin::ui {

struct NavItem {
  std::string id;
  std::string label;
  std::string icon_name;
};

class Sidebar : public QFrame {
  Q_OBJECT
 public:
  explicit Sidebar(QWidget* parent = nullptr);

  /// Set the active nav item; updates the gold indicator on the left.
  void set_active(const QString& id);

  /// Register a navigation item. Items appear in registration order.
  void add_item(const NavItem& item, std::function<void()> on_activate);

  /// Set the user profile pill at the bottom (name + email + avatar).
  void set_user(const QString& display_name, const QString& email);

 signals:
  void view_activated(const QString& id);

 private:
  QFrame*    brand_block_;
  QLabel*    brand_text_;
  QFrame*    brand_icon_box_;
  QLabel*    brand_icon_;
  QFrame*    nav_container_;
  QHash<QString, QPushButton*> nav_buttons_;
  QString    current_view_;

  // User pill
  QFrame*    user_pill_;
  QLabel*    user_initials_;
  QLabel*    user_name_;
  QLabel*    user_email_;
};

} // namespace fin::ui
