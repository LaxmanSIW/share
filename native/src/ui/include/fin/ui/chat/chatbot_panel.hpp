// fin/ui/chat/chatbot_panel.hpp — Chatbot overlay (port of Java ChatbotPanel.java)
//
// In-app AI chatbot overlay panel (ChatGPT/Gemini style) in the app's
// dark-gold theme:
//   - Header: gold sparkle avatar + "Assistant" title + provider/model subtitle
//   - Messages: avatar rows — gold AI avatar left, slate user avatar right
//   - Input: rounded pill with borderless text area + paperclip + send button
//   - Suggestion chips under the greeting
#pragma once
#include <QFrame>
#include <QListWidget>
#include <QPlainTextEdit>
#include <QPushButton>
#include <QLabel>

namespace fin::ui {

class ChatbotPanel : public QFrame {
  Q_OBJECT
 public:
  explicit ChatbotPanel(QWidget* parent = nullptr);

  /// Set the model label shown in the header ("GPT-4o" / "Claude 3.5" etc.)
  void set_model_label(const QString& label);

  /// Append a message bubble. role = "user" / "assistant" / "error".
  void append_message(const QString& role, const QString& text);

 signals:
  void send_requested(const QString& text);
  void close_requested();

 private:
  void build_header_();
  void build_messages_area_();
  void build_input_area_();

  QFrame*      header_;
  QLabel*      title_;
  QLabel*      subtitle_;
  QPlainTextEdit* messages_;
  QPlainTextEdit* input_;
  QPushButton* send_btn_;
  QPushButton* close_btn_;
};

} // namespace fin::ui
