// fin/ui/chat/chat_widgets.hpp — Chatbot UI components (consolidated)
//
// Port of Java chat/ChatMarkdownRenderer, chat/ChatbotLogDialog, chat/ChatbotModelPickerDialog,
// ChatbotSettingsPanel, ChatPipelineBar. Skill §11.1: native composition vs WebView.
#pragma once
#include <QTextBrowser>
#include <QDialog>
#include <QFrame>
#include <QLabel>
#include <QComboBox>
#include <QFormLayout>
#include <QString>
#include <QStringList>
#include <functional>
#include <vector>

namespace fin::services { struct AiModelInfo; enum class AiProvider; }

namespace fin::ui {

/// ChatMarkdownRenderer — renders AI chat messages from Markdown into QTextBrowser
/// with the brand theme. Skill §11.1: native composition (QTextBrowser + HTML/CSS),
/// NOT WebView. Skill §11.2: tabular data → HTML tables with brand colors.
class ChatMarkdownRenderer {
 public:
  /// Convert a Markdown string to a themed HTML string for QTextBrowser.
  /// Handles: paragraphs, headings, bold/italic/code, lists, tables, code blocks.
  static QString to_html(const QString& markdown);

  /// Quick test: detect if a string contains a Markdown table.
  static bool has_table(const QString& markdown);
};

/// ChatbotLogDialog — real-time CLI execution log viewer (skill §11.3).
/// Shows colored category badges: [ROUTER] [TOOL-CALL] [MCP-EXEC] [SUCCESS].
class ChatbotLogDialog : public QDialog {
  Q_OBJECT
 public:
  explicit ChatbotLogDialog(QWidget* parent = nullptr);
  /// Append a log entry. Auto-scrolls to the bottom.
  void append(const QString& category, const QString& message);
 private:
  QTextBrowser* log_view_;
};

/// ChatbotModelPickerDialog — pick provider + model.
class ChatbotModelPickerDialog : public QDialog {
  Q_OBJECT
 public:
  explicit ChatbotModelPickerDialog(QWidget* parent = nullptr);
  /// Returns the selected model id (empty if user cancelled).
  QString selected_model_id() const { return selected_; }
 private:
  QComboBox* provider_combo_{nullptr};
  QComboBox* model_combo_{nullptr};
  QString selected_;
};

/// ChatPipelineBar — a thin horizontal progress strip showing the chatbot's
/// pipeline stages: ROUTER → TOOL-CALL → MCP-EXEC → RESPONSE.
class ChatPipelineBar : public QFrame {
  Q_OBJECT
 public:
  explicit ChatPipelineBar(QWidget* parent = nullptr);
  void set_stage(int stage_index, const QString& message);
  void clear();
 private:
  std::vector<QLabel*> stages_;
};

/// ChatbotSettingsPanel — provider/key/model/system-prompt settings.
class ChatbotSettingsPanel : public QFrame {
  Q_OBJECT
 public:
  explicit ChatbotSettingsPanel(QWidget* parent = nullptr);
};

} // namespace fin::ui
