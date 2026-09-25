#include <QHBoxLayout>
#include "fin/ui/views/settings_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include <QVBoxLayout>
#include <QTabWidget>
#include <QLabel>
#include <QFormLayout>
#include <QLineEdit>

namespace fin::ui {

SettingsView::SettingsView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  l->addWidget(UiTheme::pageTitle("Settings"));
  l->addWidget(UiTheme::pageSubtitle("Business profile, billing defaults, chatbot, MCP, backup"));

  tabs_ = new QTabWidget(this);

  // Business profile tab
  auto* biz_tab = UiTheme::card(8, tabs_);
  auto* biz_form = new QFormLayout(biz_tab);
  biz_form->addRow("Business name:", new QLineEdit(biz_tab));
  biz_form->addRow("GSTIN:", new QLineEdit(biz_tab));
  biz_form->addRow("Email:", new QLineEdit(biz_tab));
  biz_form->addRow("Phone:", new QLineEdit(biz_tab));
  biz_form->addRow("Address:", new QLineEdit(biz_tab));
  tabs_->addTab(biz_tab, "Business");

  // Chatbot tab
  auto* chat_tab = UiTheme::card(8, tabs_);
  chat_tab->layout()->addWidget(new QLabel("Chatbot — provider, default model, system prompt, enabled tools", chat_tab));
  tabs_->addTab(chat_tab, "Chatbot");

  // Backup tab
  auto* backup_tab = UiTheme::card(8, tabs_);
  backup_tab->layout()->addWidget(new QLabel("Backup & restore — auto backup before migration, restore wizard", backup_tab));
  tabs_->addTab(backup_tab, "Backup");

  // MCP tab
  auto* mcp_tab = UiTheme::card(8, tabs_);
  mcp_tab->layout()->addWidget(new QLabel("MCP server — port, enabled tools, audit log", mcp_tab));
  tabs_->addTab(mcp_tab, "MCP Server");

  l->addWidget(tabs_, 1);
}

} // namespace fin::ui
