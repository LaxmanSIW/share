#include <QTimer>
// fin/ui/chat/chatbot_panel.cpp
#include "fin/ui/chat/chatbot_panel.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"

#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFrame>
#include <QLabel>
#include <QPushButton>
#include <QPlainTextEdit>
#include <QSizePolicy>
#include <QScrollBar>

namespace fin::ui {

ChatbotPanel::ChatbotPanel(QWidget* parent) : QFrame(parent) {
  setProperty("class", "card");
  setFixedWidth(380);
  setSizePolicy(QSizePolicy::Fixed, QSizePolicy::Expanding);
  setStyleSheet("background-color: #10161F; border-left: 1px solid #273245;");

  auto* root_l = new QVBoxLayout(this);
  root_l->setContentsMargins(0, 0, 0, 0);
  root_l->setSpacing(0);

  build_header_();
  build_messages_area_();
  build_input_area_();

  // Wire close.
  connect(close_btn_, &QPushButton::clicked, this, [this] {
    hide();
    emit close_requested();
  });
  // Wire send — includes local AI fallback response
  connect(send_btn_, &QPushButton::clicked, this, [this] {
    auto text = input_->toPlainText().trimmed();
    if (text.isEmpty()) return;
    append_message("user", text);
    input_->clear();
    emit send_requested(text);
    // Local AI fallback: respond with a helpful message
    // (Real impl would call AiChatClient::send_streaming)
    QTimer::singleShot(500, this, [this, text] {
      QString response;
      QString lower = text.toLower();
      if (lower.contains("invoice") || lower.contains("bill")) {
        response = "You can create a new invoice by clicking the \"+ New Bill\" button "
                  "in the sidebar (or press Ctrl+N). To view existing invoices, "
                  "click \"Invoices\" in the sidebar.";
      } else if (lower.contains("buyer") || lower.contains("customer")) {
        response = "To manage buyers, click \"Catalog\" in the sidebar and select \"Buyers\". "
                  "You can add, edit, or delete buyer records from there.";
      } else if (lower.contains("item") || lower.contains("product")) {
        response = "Items are managed under Catalog → Items. You can set HSN codes, "
                  "GST rates, stock levels, and reorder points for each item.";
      } else if (lower.contains("gst") || lower.contains("tax")) {
        response = "GST is automatically split into CGST + SGST for intra-state transactions "
                  "and IGST for inter-state. You can configure this in Settings → Billing.";
      } else if (lower.contains("report") || lower.contains("summary")) {
        response = "Reports are available in the Reports & Ledger tab. You can view "
                  "Trial Balance, P&L, GST Summary, Outstanding, Sales Trends, and more.";
      } else if (lower.contains("template") || lower.contains("designer")) {
        response = "The Template Designer lets you create custom invoice and label layouts. "
                  "Access it from Catalog → Templates, then click \"Template Designer\".\n"
                  "Features: text, images, shapes, tables, barcodes, QR codes, and more.";
      } else if (lower.contains("label") || lower.contains("print")) {
        response = "Label printing supports thermal printers via TSPL commands. "
                  "Use the Label Print History to track print runs.";
      } else if (lower.contains("backup") || lower.contains("restore")) {
        response = "Backup & restore is in Settings → Backup. Auto-backup runs before "
                  "every migration. You can also backup manually.";
      } else if (lower.contains("mcp")) {
        response = "The MCP (Model Context Protocol) server exposes InvoiceStudio data "
                  "and operations as tools that AI assistants can call. Configure it "
                  "in Settings → MCP Server.";
      } else if (lower.contains("help") || lower.contains("how")) {
        response = "I can help with invoices, buyers, items, GST, reports, templates, "
                  "label printing, backups, and MCP. Ask me about any feature!";
      } else {
        response = "I understand you're asking about: \"" + text + "\".\n\n"
                  "I'm running in local mode (no API key configured). To get full AI "
                  "responses, configure an API key in Settings → Chatbot.\n\n"
                  "In the meantime, I can help with: invoices, buyers, items, GST, "
                  "reports, templates, labels, backups, and MCP.";
      }
      append_message("assistant", response);
    });
  });

  // Welcome message.
  append_message("assistant",
    "Hi! I'm InvoiceStudio Assistant. Ask me about your invoices, buyers, "
    "items, stock, or any accounting question you have.");
}

void ChatbotPanel::build_header_() {
  header_ = new QFrame(this);
  header_->setFixedHeight(56);
  header_->setStyleSheet("background-color: transparent; border-bottom: 1px solid #273245;");
  auto* l = new QHBoxLayout(header_);
  l->setContentsMargins(16, 8, 8, 8);
  l->setSpacing(10);

  // Avatar (gold square with sparkles icon)
  auto* avatar = new QFrame(header_);
  avatar->setFixedSize(32, 32);
  avatar->setStyleSheet("background-color: #D9A13B; border-radius: 16px;");
  auto* avatar_l = new QVBoxLayout(avatar);
  avatar_l->setContentsMargins(0, 0, 0, 0);
  auto* avatar_icon = new QLabel(avatar);
  avatar_icon->setPixmap(IconHelper::pixmap(IconHelper::ICON_SPARKLES, 16, QColor("#0B0E13")));
  avatar_icon->setAlignment(Qt::AlignCenter);
  avatar_l->addWidget(avatar_icon);
  l->addWidget(avatar);

  auto* text_box = new QVBoxLayout();
  text_box->setSpacing(0);
  title_ = new QLabel("Assistant", header_);
  title_->setStyleSheet("color: #F4F4F5; font-size: 14px; font-weight: bold;");
  subtitle_ = new QLabel("GPT-4o", header_);
  subtitle_->setStyleSheet("color: #94A3B8; font-size: 11px;");
  text_box->addWidget(title_);
  text_box->addWidget(subtitle_);
  l->addLayout(text_box);
  l->addItem(UiTheme::hspacer());

  // Icon-only action buttons
  auto* expand_btn = UiTheme::iconButton(IconHelper::ICON_EXPAND, 16, "Expand", header_);
  l->addWidget(expand_btn);
  auto* clear_btn = UiTheme::iconButton(IconHelper::ICON_TRASH, 16, "Clear conversation", header_);
  l->addWidget(clear_btn);
  close_btn_ = UiTheme::iconButton(IconHelper::ICON_CLOSE, 16, "Close", header_);
  l->addWidget(close_btn_);

  static_cast<QVBoxLayout*>(layout())->addWidget(header_);
}

void ChatbotPanel::build_messages_area_() {
  messages_ = new QPlainTextEdit(this);
  messages_->setReadOnly(true);
  messages_->setFrameStyle(QFrame::NoFrame);
  messages_->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
  messages_->setStyleSheet(
    "QPlainTextEdit { background-color: transparent; color: #E6EAF0; font-size: 13px; "
    "border: none; padding: 8px; }"
  );
  static_cast<QVBoxLayout*>(layout())->addWidget(messages_, 1);
}

void ChatbotPanel::build_input_area_() {
  auto* input_row = new QFrame(this);
  input_row->setStyleSheet("background-color: transparent; border-top: 1px solid #273245;");
  auto* l = new QHBoxLayout(input_row);
  l->setContentsMargins(12, 8, 12, 12);
  l->setSpacing(8);

  auto* attach = UiTheme::iconButton(IconHelper::ICON_PAPERCLIP, 16, "Attach file", input_row);
  l->addWidget(attach);

  input_ = new QPlainTextEdit(input_row);
  input_->setPlaceholderText("Ask anything…");
  input_->setFrameStyle(QFrame::NoFrame);
  input_->setFixedHeight(60);
  input_->setStyleSheet("QPlainTextEdit { background-color: transparent; color: #F4F4F5; "
                         "border: none; padding: 6px; font-size: 13px; }");
  l->addWidget(input_, 1);

  send_btn_ = new QPushButton(input_row);
  send_btn_->setProperty("class", "accent-gold");
  send_btn_->setIcon(IconHelper::icon(IconHelper::ICON_SEND, 16, QColor("#0B0E13")));
  send_btn_->setIconSize(QSize(16, 16));
  send_btn_->setFixedSize(40, 40);
  send_btn_->setCursor(Qt::PointingHandCursor);
  send_btn_->setToolTip("Send (Ctrl+Enter)");
  l->addWidget(send_btn_);

  static_cast<QVBoxLayout*>(layout())->addWidget(input_row);
}

void ChatbotPanel::set_model_label(const QString& label) {
  subtitle_->setText(label);
}

void ChatbotPanel::append_message(const QString& role, const QString& text) {
  // For Phase 4 we render messages as plain-text rows in the QPlainTextEdit.
  // Phase 6 will port the Java original's native-composition message bubbles
  // (skill §11.1) — QTextBrowser + HTML/CSS for rich markdown rendering.
  QString prefix;
  QString color;
  if (role == "user") {
    prefix = "<b>You</b>: ";
    color = "#F4F4F5";
  } else if (role == "assistant") {
    prefix = "<b>Assistant</b>: ";
    color = "#E6EAF0";
  } else { // error
    prefix = "<b>Error</b>: ";
    color = "#F1B0B0";
  }
  // Escape HTML in text.
  QString escaped = text.toHtmlEscaped();
  escaped.replace("\n", "<br>");
  QString html = QString("<p style=\"color:%1\">%2%3</p>").arg(color, prefix, escaped);
  messages_->appendHtml(html);
  // Scroll to bottom.
  QScrollBar* sb = messages_->verticalScrollBar();
  sb->setValue(sb->maximum());
}

} // namespace fin::ui
