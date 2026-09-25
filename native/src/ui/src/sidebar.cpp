// fin/ui/sidebar.cpp
#include "fin/ui/sidebar.hpp"
#include "fin/ui/icon_helper.hpp"

#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QFrame>
#include <QToolButton>
#include <QString>
#include <QSizePolicy>

namespace fin::ui {

Sidebar::Sidebar(QWidget* parent)
  : QFrame(parent) {
  setProperty("class", "app-sidebar");
  setFixedWidth(220);
  setSizePolicy(QSizePolicy::Fixed, QSizePolicy::Expanding);

  auto* root = new QVBoxLayout(this);
  root->setContentsMargins(0, 0, 0, 0);
  root->setSpacing(0);

  // Brand block
  brand_block_ = new QFrame(this);
  brand_block_->setFixedHeight(56);
  auto* brand_l = new QHBoxLayout(brand_block_);
  brand_l->setContentsMargins(16, 8, 16, 8);
  brand_l->setSpacing(10);

  brand_icon_box_ = new QFrame(brand_block_);
  brand_icon_box_->setFixedSize(34, 34);
  brand_icon_box_->setStyleSheet("background-color: #D9A13B; border-radius: 6px;");
  auto* brand_icon_l = new QVBoxLayout(brand_icon_box_);
  brand_icon_l->setContentsMargins(0, 0, 0, 0);
  brand_icon_ = new QLabel(brand_icon_box_);
  brand_icon_->setPixmap(IconHelper::pixmap(IconHelper::ICON_RECEIPT, 17, QColor("#0B0E13")));
  brand_icon_->setAlignment(Qt::AlignCenter);
  brand_icon_l->addWidget(brand_icon_);
  brand_l->addWidget(brand_icon_box_);

  auto* title_box = new QVBoxLayout();
  title_box->setSpacing(0);
  brand_text_ = new QLabel("InvoiceStudio", brand_block_);
  brand_text_->setStyleSheet("color: #F4F4F5; font-size: 14px; font-weight: bold;");
  auto* subtitle = new QLabel("Native", brand_block_);
  subtitle->setStyleSheet("color: #94A3B8; font-size: 10px;");
  title_box->addWidget(brand_text_);
  title_box->addWidget(subtitle);
  brand_l->addLayout(title_box);
  brand_l->addStretch();
  root->addWidget(brand_block_);

  // Nav container
  nav_container_ = new QFrame(this);
  nav_container_->setStyleSheet("background-color: transparent;");
  auto* nav_l = new QVBoxLayout(nav_container_);
  nav_l->setContentsMargins(8, 8, 8, 8);
  nav_l->setSpacing(2);
  nav_l->addStretch();
  root->addWidget(nav_container_, 1);

  // User pill at the bottom
  user_pill_ = new QFrame(this);
  user_pill_->setProperty("class", "user-pill");
  user_pill_->setFixedHeight(56);
  auto* pill_l = new QHBoxLayout(user_pill_);
  pill_l->setContentsMargins(12, 8, 12, 8);
  pill_l->setSpacing(10);

  user_initials_ = new QLabel("?", user_pill_);
  user_initials_->setFixedSize(32, 32);
  user_initials_->setAlignment(Qt::AlignCenter);
  user_initials_->setStyleSheet("background-color: #D9A13B; color: #0B0E13; border-radius: 16px; font-weight: bold; font-size: 12px;");
  pill_l->addWidget(user_initials_);

  auto* name_box = new QVBoxLayout();
  name_box->setSpacing(0);
  user_name_ = new QLabel("Not signed in", user_pill_);
  user_name_->setStyleSheet("color: #F4F4F5; font-size: 12px; font-weight: bold;");
  user_email_ = new QLabel("", user_pill_);
  user_email_->setStyleSheet("color: #94A3B8; font-size: 11px;");
  name_box->addWidget(user_name_);
  name_box->addWidget(user_email_);
  pill_l->addLayout(name_box);
  pill_l->addStretch();
  root->addWidget(user_pill_);
}

void Sidebar::add_item(const NavItem& item, std::function<void()> on_activate) {
  auto* btn = new QPushButton(nav_container_);
  btn->setProperty("class", "nav-button");
  btn->setCheckable(true);
  btn->setIcon(IconHelper::icon(item.icon_name, 16, QColor("#94A3B8")));
  btn->setIconSize(QSize(16, 16));
  btn->setText(QString::fromStdString(item.label));
  btn->setCursor(Qt::PointingHandCursor);
  btn->setToolTip(QString::fromStdString(item.label));
  // Add the button to the nav_container_'s layout, BEFORE the stretch.
  auto* l = qobject_cast<QVBoxLayout*>(nav_container_->layout());
  if (l) {
    l->insertWidget(l->count() - 1, btn);  // insert before the stretch (last item)
  }
  nav_buttons_.insert(QString::fromStdString(item.id), btn);

  connect(btn, &QPushButton::clicked, this, [this, id = item.id, on_activate]() {
    set_active(QString::fromStdString(id));
    if (on_activate) on_activate();
    emit view_activated(QString::fromStdString(id));
  });
}

void Sidebar::set_active(const QString& id) {
  current_view_ = id;
  for (auto it = nav_buttons_.begin(); it != nav_buttons_.end(); ++it) {
    it.value()->setChecked(it.key() == id);
  }
}

void Sidebar::set_user(const QString& display_name, const QString& email) {
  user_name_->setText(display_name);
  user_email_->setText(email);
  // Initials from display name (first 2 letters of first 2 words).
  QStringList parts = display_name.split(' ', Qt::SkipEmptyParts);
  QString initials;
  for (int i = 0; i < std::min(2, parts.size()); ++i) {
    if (!parts[i].isEmpty()) initials.append(parts[i][0].toUpper());
  }
  if (initials.isEmpty()) initials = "?";
  user_initials_->setText(initials);
}

} // namespace fin::ui
