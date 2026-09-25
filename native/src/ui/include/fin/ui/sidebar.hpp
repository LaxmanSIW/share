// fin/ui/sidebar.hpp — Sidebar navigation (port of Java SidebarController.java)
#pragma once
#include <QFrame>
#include <QLabel>
#include <QPushButton>
#include <QHash>
#include <QString>
#include <functional>
#include <string_view>
#include <vector>

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

  void set_active(const QString& id);
  void add_item(const NavItem& item, std::function<void()> on_activate);
  void set_user(const QString& display_name, const QString& email);

  /// Add a Catalog button that opens a popup menu (matches Java showCatalogPopup).
  void add_catalog_button(const std::vector<NavItem>& catalog_items,
                           std::function<void(const std::string& id)> on_catalog_click);

  /// Add a "+ New Bill" gold button at the bottom (matches Java sidebar-cta).
  void add_new_bill_button(std::function<void()> on_click);

 signals:
  void view_activated(const QString& id);

 private:
  QFrame*    brand_block_{nullptr};
  QLabel*    brand_text_{nullptr};
  QFrame*    brand_icon_box_{nullptr};
  QLabel*    brand_icon_{nullptr};
  QFrame*    nav_container_{nullptr};
  QHash<QString, QPushButton*> nav_buttons_;
  QString    current_view_;

  QFrame*    user_pill_{nullptr};
  QLabel*    user_initials_{nullptr};
  QLabel*    user_name_{nullptr};
  QLabel*    user_email_{nullptr};
};

} // namespace fin::ui
