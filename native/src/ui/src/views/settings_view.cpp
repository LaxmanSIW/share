// fin/ui/views/settings_view.cpp — Full Settings view with 11 tabs matching Java original
#include "fin/ui/views/settings_view.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/ui/widgets/toast.hpp"
#include "fin/services/backup_restore.hpp"

#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QScrollArea>
#include <QTabWidget>
#include <QFormLayout>
#include <QLineEdit>
#include <QTextEdit>
#include <QComboBox>
#include <QCheckBox>
#include <QLabel>
#include <QPushButton>
#include <QSpinBox>
#include <QFrame>
#include <QListWidget>

namespace fin::ui {

namespace {
QFrame* make_form_tab(QWidget* parent) {
  auto* page = new QFrame(parent);
  page->setStyleSheet("background-color: transparent;");
  auto* l = new QVBoxLayout(page);
  l->setContentsMargins(16, 16, 16, 16);
  l->setSpacing(12);
  auto* scroll = new QScrollArea(page);
  scroll->setWidgetResizable(true);
  scroll->setFrameShape(QFrame::NoFrame);
  scroll->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
  auto* form_card = UiTheme::card(12, scroll);
  scroll->setWidget(form_card);
  l->addWidget(scroll, 1);
  return page;
}
QFormLayout* add_form_to(QFrame* card) {
  auto* form = new QFormLayout();
  form->setSpacing(8);
  form->setLabelAlignment(Qt::AlignRight);
  static_cast<QVBoxLayout*>(card->layout())->addLayout(form);
  return form;
}
QLabel* section_label(const QString& text, QFrame* parent) {
  auto* lbl = new QLabel(text, parent);
  lbl->setStyleSheet("color: #D9A13B; font-size: 13px; font-weight: bold; padding: 4px 0;");
  return lbl;
}
}

SettingsView::SettingsView(QWidget* parent) : QFrame(parent) {
  setProperty("class", "view-page");
  setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(24, 24, 24, 24);
  l->setSpacing(16);
  l->addWidget(UiTheme::pageTitle("Settings"));
  l->addWidget(UiTheme::pageSubtitle("Business profile, billing defaults, chatbot, MCP, backup, shortcuts"));
  tabs_ = new QTabWidget(this);
  tabs_->setStyleSheet(
    "QTabWidget::pane { background-color: transparent; border: 1px solid #232B38; border-radius: 6px; } "
    "QTabBar::tab { background-color: transparent; border: 1px solid transparent; "
    "border-bottom: none; border-top-left-radius: 6px; border-top-right-radius: 6px; "
    "color: #94A3B8; padding: 8px 16px; margin-right: 2px; } "
    "QTabBar::tab:hover { background-color: #1A222D; color: #F4F4F5; } "
    "QTabBar::tab:selected { background-color: #151B25; border-color: #232B38; color: #F4F4F5; "
    "border-bottom: 2px solid #D9A13B; }"
  );

  // Tab 1: Profile
  {
    auto* page = make_form_tab(tabs_);
    auto* card = static_cast<QFrame*>(static_cast<QScrollArea*>(page->layout()->itemAt(0)->widget())->widget());
    auto* form = add_form_to(card);
    form->addRow("", section_label("Business Profile", card));
    form->addRow("Business name:", new QLineEdit("Leelamani Traders", card));
    form->addRow("GSTIN:", new QLineEdit("27ABCDE1234F1Z5", card));
    form->addRow("Email:", new QLineEdit("contact@leelamani.in", card));
    form->addRow("Phone:", new QLineEdit("+91 98765 43210", card));
    form->addRow("Address:", new QLineEdit("123 Industrial Estate, Mumbai 400001", card));
    form->addRow("State:", new QLineEdit("Maharashtra", card));
    form->addRow("State code:", new QLineEdit("27", card));
    form->addRow("", section_label("Invoice Defaults", card));
    form->addRow("Invoice prefix:", new QLineEdit("INV-", card));
    form->addRow("Starting number:", new QLineEdit("0001", card));
    auto* intra = new QCheckBox("Intra-state (CGST + SGST)", card); intra->setChecked(true);
    form->addRow("GST type:", intra);
    form->addRow("Default GST rate:", new QLineEdit("18", card));
    form->addRow("", UiTheme::primaryButton("Save Profile", card));
    tabs_->addTab(page, "Profile");
  }
  // Tab 2: Bank
  {
    auto* page = make_form_tab(tabs_);
    auto* card = static_cast<QFrame*>(static_cast<QScrollArea*>(page->layout()->itemAt(0)->widget())->widget());
    auto* form = add_form_to(card);
    form->addRow("", section_label("Bank Details", card));
    form->addRow("Bank name:", new QLineEdit("HDFC Bank", card));
    form->addRow("Account holder:", new QLineEdit("Leelamani Traders", card));
    form->addRow("Account number:", new QLineEdit("50200012345678", card));
    form->addRow("IFSC code:", new QLineEdit("HDFC0001234", card));
    form->addRow("Branch:", new QLineEdit("Andheri East, Mumbai", card));
    form->addRow("UPI ID:", new QLineEdit("leelamani@hdfc", card));
    tabs_->addTab(page, "Bank");
  }
  // Tab 3: Billing
  {
    auto* page = make_form_tab(tabs_);
    auto* card = static_cast<QFrame*>(static_cast<QScrollArea*>(page->layout()->itemAt(0)->widget())->widget());
    auto* form = add_form_to(card);
    form->addRow("", section_label("Billing Preferences", card));
    auto* round_mode = new QComboBox(card);
    round_mode->addItems({"Half away from zero (commercial)", "Half even (banker's)", "Truncate"});
    form->addRow("Rounding mode:", round_mode);
    form->addRow("Default discount %:", new QLineEdit("0", card));
    auto* auto_words = new QCheckBox("Auto-generate amount in words", card); auto_words->setChecked(true);
    form->addRow("", auto_words);
    auto* auto_seq = new QCheckBox("Auto-increment bill number", card); auto_seq->setChecked(true);
    form->addRow("", auto_seq);
    form->addRow("Place of supply:", new QLineEdit("Maharashtra (27)", card));
    form->addRow("Financial year start:", new QLineEdit("April", card));
    tabs_->addTab(page, "Billing");
  }
  // Tab 4: Fields
  {
    auto* page = make_form_tab(tabs_);
    auto* card = static_cast<QFrame*>(static_cast<QScrollArea*>(page->layout()->itemAt(0)->widget())->widget());
    auto* l2 = static_cast<QVBoxLayout*>(card->layout());
    l2->addWidget(section_label("Buyer Custom Fields", card));
    auto* list = new QListWidget(card);
    list->setStyleSheet("QListWidget { background-color: #0B0E13; border: 1px solid #232B38; border-radius: 6px; color: #F4F4F5; } QListWidget::item { padding: 8px; } QListWidget::item:hover { background-color: #1A222D; }");
    list->addItem("GST Registration Type (dropdown: Regular/Composition)");
    list->addItem("PAN Number (text)");
    list->addItem("Transport Default (text)");
    list->addItem("Credit Limit (number)");
    list->addItem("Risk Score (number 1-10)");
    l2->addWidget(list, 1);
    l2->addWidget(UiTheme::primaryIconButton(IconHelper::ICON_PLUS, "Add Field", card));
    tabs_->addTab(page, "Fields");
  }
  // Tab 5: Fonts
  {
    auto* page = make_form_tab(tabs_);
    auto* card = static_cast<QFrame*>(static_cast<QScrollArea*>(page->layout()->itemAt(0)->widget())->widget());
    auto* l2 = static_cast<QVBoxLayout*>(card->layout());
    l2->addWidget(section_label("Custom Fonts", card));
    auto* list = new QListWidget(card);
    list->setStyleSheet("QListWidget { background-color: #0B0E13; border: 1px solid #232B38; border-radius: 6px; color: #F4F4F5; } QListWidget::item { padding: 8px; }");
    list->addItem("Inter Regular — /path/to/Inter-Regular.ttf");
    list->addItem("Inter Bold — /path/to/Inter-Bold.ttf");
    list->addItem("JetBrains Mono — /path/to/JetBrainsMono-Regular.ttf");
    list->addItem("Noto Sans Devanagari — /path/to/NotoSansDevanagari.ttf");
    l2->addWidget(list, 1);
    l2->addWidget(UiTheme::primaryIconButton(IconHelper::ICON_PLUS, "Add Font", card));
    tabs_->addTab(page, "Fonts");
  }
  // Tab 6: Print
  {
    auto* page = make_form_tab(tabs_);
    auto* card = static_cast<QFrame*>(static_cast<QScrollArea*>(page->layout()->itemAt(0)->widget())->widget());
    auto* form = add_form_to(card);
    form->addRow("", section_label("Print Settings", card));
    auto* printer = new QComboBox(card);
    printer->addItems({"System default", "HP LaserJet Pro", "Epson L3210", "Zebra GK420d (labels)"});
    form->addRow("Printer:", printer);
    auto* paper = new QComboBox(card);
    paper->addItems({"A4 (210x297mm)", "A5 (148x210mm)", "Letter (8.5x11in)", "Legal (8.5x14in)"});
    form->addRow("Paper size:", paper);
    form->addRow("Top margin (mm):", new QLineEdit("15", card));
    form->addRow("Bottom margin (mm):", new QLineEdit("15", card));
    form->addRow("Left margin (mm):", new QLineEdit("15", card));
    form->addRow("Right margin (mm):", new QLineEdit("15", card));
    form->addRow("", new QCheckBox("Duplex (double-sided)", card));
    form->addRow("", new QCheckBox("Color printing", card));
    tabs_->addTab(page, "Print");
  }
  // Tab 7: Knowledge
  {
    auto* page = make_form_tab(tabs_);
    auto* card = static_cast<QFrame*>(static_cast<QScrollArea*>(page->layout()->itemAt(0)->widget())->widget());
    auto* l2 = static_cast<QVBoxLayout*>(card->layout());
    l2->addWidget(section_label("Knowledge Hub", card));
    l2->addWidget(UiTheme::muted("Browse how-to guides, FAQs, and tutorials.", card));
    auto* list = new QListWidget(card);
    list->setStyleSheet("QListWidget { background-color: #0B0E13; border: 1px solid #232B38; border-radius: 6px; color: #F4F4F5; } QListWidget::item { padding: 10px; } QListWidget::item:hover { background-color: #1A222D; }");
    list->addItem("Getting Started — First invoice in 5 minutes");
    list->addItem("Buyer Management — Import from CSV");
    list->addItem("GST Configuration — CGST/SGST/IGST setup");
    list->addItem("Template Designer — Create custom layouts");
    list->addItem("Label Printing — Bulk label print workflow");
    list->addItem("MCP Server — Connect AI assistants");
    list->addItem("Keyboard Shortcuts — Productivity tips");
    list->addItem("Backup & Restore — Data safety");
    l2->addWidget(list, 1);
    tabs_->addTab(page, "Knowledge");
  }
  // Tab 8: Backup
  {
    auto* page = make_form_tab(tabs_);
    auto* card = static_cast<QFrame*>(static_cast<QScrollArea*>(page->layout()->itemAt(0)->widget())->widget());
    auto* form = add_form_to(card);
    form->addRow("", section_label("Backup & Restore", card));
    auto* auto_backup = new QCheckBox("Auto-backup before every migration", card); auto_backup->setChecked(true);
    form->addRow("", auto_backup);
    auto* keep = new QSpinBox(card); keep->setRange(1, 100); keep->setValue(10);
    form->addRow("Keep most recent backups:", keep);
    form->addRow("", UiTheme::primaryButton("Backup Now", card));
    form->addRow("", section_label("Restore", card));
    form->addRow("", UiTheme::ghostButton("Choose backup file to restore...", card));
    form->addRow("", section_label("Backup History", card));
    auto* list = new QListWidget(card);
    list->setStyleSheet("QListWidget { background-color: #0B0E13; border: 1px solid #232B38; border-radius: 6px; color: #F4F4F5; } QListWidget::item { padding: 8px; }");
    list->addItem("invoicestudio_2026-09-25_001.db — 2.4 MB");
    list->addItem("invoicestudio_2026-09-24_001.db — 2.3 MB");
    list->addItem("invoicestudio_2026-09-23_001.db — 2.2 MB");
    form->addRow("", list);
    tabs_->addTab(page, "Backup");
  }
  // Tab 9: Shortcuts
  {
    auto* page = make_form_tab(tabs_);
    auto* card = static_cast<QFrame*>(static_cast<QScrollArea*>(page->layout()->itemAt(0)->widget())->widget());
    auto* form = add_form_to(card);
    form->addRow("", section_label("Keyboard Shortcuts", card));
    QStringList shortcuts = {
      "Dashboard\tCtrl+1", "Invoices\tCtrl+2", "Buyers\tCtrl+3", "Items\tCtrl+4",
      "Reports\tCtrl+5", "Settings\tCtrl+,", "Create Bill\tCtrl+N",
      "Save Document\tCtrl+S", "Save & Print\tCtrl+P", "Save & PDF\tCtrl+Shift+P",
      "Toggle Chatbot\tCtrl+J", "Search\tCtrl+F", "Undo\tCtrl+Z", "Redo\tCtrl+Y",
      "Copy\tCtrl+C", "Paste\tCtrl+V", "Zoom In\tCtrl++", "Zoom Out\tCtrl+-",
      "Actual Size\tCtrl+0", "Delete\tDelete"
    };
    for (const auto& s : shortcuts) {
      auto parts = s.split('\t');
      auto* row_widget = new QWidget(card);
      auto* row = new QHBoxLayout(row_widget);
      row->setContentsMargins(0, 0, 0, 0);
      row->addWidget(new QLabel(parts[0], card));
      row->addStretch();
      auto* key_lbl = new QLabel(parts[1], card);
      key_lbl->setStyleSheet("background-color: #1A222D; color: #94A3B8; padding: 2px 8px; border-radius: 4px; font-family: monospace; font-size: 11px;");
      row->addWidget(key_lbl);
      form->addRow("", row_widget);
    }
    tabs_->addTab(page, "Shortcuts");
  }
  // Tab 10: Chatbot
  {
    auto* page = make_form_tab(tabs_);
    auto* card = static_cast<QFrame*>(static_cast<QScrollArea*>(page->layout()->itemAt(0)->widget())->widget());
    auto* form = add_form_to(card);
    form->addRow("", section_label("Chatbot Configuration", card));
    auto* provider = new QComboBox(card);
    provider->addItems({"OpenAI", "Anthropic (Claude)", "Google Gemini", "Local Ollama"});
    form->addRow("Provider:", provider);
    auto* api_key = new QLineEdit(card); api_key->setEchoMode(QLineEdit::Password); api_key->setPlaceholderText("sk-...");
    form->addRow("API Key:", api_key);
    form->addRow("Default model:", new QLineEdit("gpt-4o-mini", card));
    form->addRow("Temperature:", new QLineEdit("0.7", card));
    form->addRow("Max tokens:", new QLineEdit("1024", card));
    auto* system_prompt = new QTextEdit(card);
    system_prompt->setMaximumHeight(100);
    system_prompt->setPlainText("You are InvoiceStudio Assistant. Answer questions about invoices, buyers, items, stock, accounting. Be concise and helpful.");
    form->addRow("System prompt:", system_prompt);
    form->addRow("", new QCheckBox("Auto-execute tools (skip confirmation)", card));
    form->addRow("", new QCheckBox("Log pipeline to disk", card));
    form->addRow("", UiTheme::primaryButton("Save Chatbot Settings", card));
    tabs_->addTab(page, "Chatbot");
  }
  // Tab 11: MCP Server
  {
    auto* page = make_form_tab(tabs_);
    auto* card = static_cast<QFrame*>(static_cast<QScrollArea*>(page->layout()->itemAt(0)->widget())->widget());
    auto* form = add_form_to(card);
    form->addRow("", section_label("MCP Server Configuration", card));
    auto* port = new QSpinBox(card); port->setRange(0, 65535); port->setValue(8765);
    form->addRow("Port:", port);
    form->addRow("Bind address:", new QLineEdit("127.0.0.1 (localhost only)", card));
    form->addRow("", new QCheckBox("Auto-start MCP server on launch", card));
    form->addRow("", section_label("Registered Tools", card));
    auto* tools_list = new QListWidget(card);
    tools_list->setStyleSheet("QListWidget { background-color: #0B0E13; border: 1px solid #232B38; border-radius: 6px; color: #F4F4F5; } QListWidget::item { padding: 6px; } QListWidget::item:hover { background-color: #1A222D; }");
    tools_list->addItem("list_bills — List all invoices (read-only)");
    tools_list->addItem("find_bill — Find a single bill by id (read-only)");
    tools_list->addItem("mark_bill_paid — Mark invoice as paid (write)");
    tools_list->addItem("list_buyers — List all buyers (read-only)");
    tools_list->addItem("create_buyer — Create a new buyer (write)");
    tools_list->addItem("list_items — List all items (read-only)");
    tools_list->addItem("list_suppliers — List all suppliers (read-only)");
    tools_list->addItem("current_stock — Get stock balance for item (read-only)");
    tools_list->addItem("list_expenses — List expense vouchers (read-only)");
    tools_list->addItem("trial_balance — Compute trial balance (read-only)");
    tools_list->addItem("list_templates — List templates (read-only)");
    tools_list->addItem("whoami — Current user id (read-only)");
    form->addRow("", tools_list);
    form->addRow("", UiTheme::ghostButton("Restart MCP Server", card));
    tabs_->addTab(page, "MCP Server");
  }

  l->addWidget(tabs_, 1);
}

} // namespace fin::ui
