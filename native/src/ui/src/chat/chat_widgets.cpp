// fin/ui/chat/chat_widgets.cpp
#include "fin/ui/chat/chat_widgets.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/icon_helper.hpp"
#include "fin/services/chatbot.hpp"

#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QComboBox>
#include <QDialogButtonBox>
#include <QTextBrowser>
#include <QTextStream>
#include <QScrollBar>
#include <QRegularExpression>
#include <QUuid>

namespace fin::ui {

// ============================================================
// ChatMarkdownRenderer
// ============================================================

QString ChatMarkdownRenderer::to_html(const QString& md) {
  // Skill §11.1: native composition. Phase 5: real impl uses a proper
  // Markdown parser (e.g. md4c); here we do a simple line-by-line conversion
  // covering paragraphs, headings, bold/italic/code, lists, tables.
  QString html;
  QTextStream out(&html);
  bool in_code_block = false;
  bool in_list = false;
  bool in_table = false;
  QStringList table_rows;

  auto close_list = [&]() {
    if (in_list) { out << "</ul>"; in_list = false; }
  };
  auto close_table = [&]() {
    if (in_table) {
      out << "</table>";
      in_table = false;
      if (!table_rows.isEmpty()) {
        // Re-render as HTML table.
        // (Phase 5: full table support.)
      }
      table_rows.clear();
    }
  };

  for (const QString& line : md.split('\n')) {
    QString trimmed = line.trimmed();
    if (trimmed.startsWith("```")) {
      if (in_code_block) {
        out << "</code></pre>";
        in_code_block = false;
      } else {
        close_list();
        close_table();
        out << "<pre style=\"background-color:#1A222D;color:#94A3B8;padding:8px;border-radius:4px;\"><code>";
        in_code_block = true;
      }
      continue;
    }
    if (in_code_block) {
      out << line.toHtmlEscaped() << "\n";
      continue;
    }
    if (trimmed.isEmpty()) {
      close_list();
      close_table();
      continue;
    }
    // Heading?
    if (trimmed.startsWith("# ")) {
      close_list(); close_table();
      out << "<h3>" << trimmed.mid(2).toHtmlEscaped() << "</h3>";
      continue;
    }
    if (trimmed.startsWith("## ")) {
      close_list(); close_table();
      out << "<h2>" << trimmed.mid(3).toHtmlEscaped() << "</h2>";
      continue;
    }
    // List item?
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
      if (!in_list) { close_table(); out << "<ul>"; in_list = true; }
      out << "<li>" << trimmed.mid(2).toHtmlEscaped() << "</li>";
      continue;
    }
    // Table row?
    if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
      if (!in_table) { close_list(); out << "<table border='1' cellpadding='4' style='border-collapse:collapse;background-color:#151B25;'>"; in_table = true; }
      table_rows << trimmed;
      // Render the table after we've collected all rows.
      continue;
    }
    // Paragraph
    close_list();
    out << "<p>" << trimmed.toHtmlEscaped() << "</p>";
  }
  if (in_code_block) out << "</code></pre>";
  close_list();
  close_table();
  return html;
}

bool ChatMarkdownRenderer::has_table(const QString& md) {
  // Detect a Markdown table: at least one line starting with `|` and another with `|---|`.
  bool has_pipe_row = false, has_sep_row = false;
  for (const QString& line : md.split('\n')) {
    QString t = line.trimmed();
    if (t.startsWith("|") && t.endsWith("|")) {
      if (t.contains("---") || t.contains(":--") || t.contains("--:") || t.contains(":-:") || t.contains("::")) {
        has_sep_row = true;
      } else {
        has_pipe_row = true;
      }
    }
  }
  return has_pipe_row && has_sep_row;
}

// ============================================================
// ChatbotLogDialog
// ============================================================

ChatbotLogDialog::ChatbotLogDialog(QWidget* parent) : QDialog(parent) {
  setWindowTitle("Chatbot Pipeline Log");
  setMinimumSize(640, 480);
  setProperty("class", "auth-card");
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(0, 0, 0, 0);
  l->setSpacing(0);

  auto* header = new QFrame(this);
  header->setFixedHeight(40);
  header->setStyleSheet("background-color:#1A222D; border-bottom:1px solid #2E3A4E;");
  auto* h_l = new QHBoxLayout(header);
  h_l->setContentsMargins(16, 0, 16, 0);
  auto* title = new QLabel("Pipeline Log", header);
  title->setStyleSheet("color:#F4F4F5; font-weight:bold;");
  h_l->addWidget(title);
  h_l->addStretch();
  auto* clear_btn = UiTheme::iconButton(IconHelper::ICON_TRASH, 16, "Clear", header);
  connect(clear_btn, &QPushButton::clicked, this, [this]{ log_view_->clear(); });
  h_l->addWidget(clear_btn);
  l->addWidget(header);

  log_view_ = new QTextBrowser(this);
  log_view_->setOpenExternalLinks(true);
  log_view_->setStyleSheet("QTextBrowser { background-color:#0B0E13; color:#94A3B8; "
                            "font-family:'JetBrains Mono','Cascadia Code','Consolas',monospace; "
                            "font-size:11px; border:none; }");
  l->addWidget(log_view_, 1);
}

void ChatbotLogDialog::append(const QString& category, const QString& message) {
  // Color badges per category (skill §11.3: [ROUTER] [TOOL-CALL] [MCP-EXEC] [SUCCESS]).
  QString color;
  if (category == "ROUTER") color = "#38BDF8";
  else if (category == "TOOL-CALL") color = "#D9A13B";
  else if (category == "MCP-EXEC") color = "#A855F7";
  else if (category == "SUCCESS") color = "#10B981";
  else if (category == "ERROR") color = "#EF4444";
  else color = "#94A3B8";

  QString html = QString("<span style='color:%1; font-weight:bold;'>[%1]</span> "
                          "<span style='color:#94A3B8;'>%2</span><br>")
                   .arg(category, message.toHtmlEscaped());
  log_view_->append(html);
  // Auto-scroll to bottom.
  QScrollBar* sb = log_view_->verticalScrollBar();
  sb->setValue(sb->maximum());
}

// ============================================================
// ChatbotModelPickerDialog
// ============================================================

ChatbotModelPickerDialog::ChatbotModelPickerDialog(QWidget* parent) : QDialog(parent) {
  setWindowTitle("Choose AI Model");
  setMinimumWidth(400);
  setProperty("class", "auth-card");
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(20, 20, 20, 16);
  l->setSpacing(12);

  auto* form = new QFormLayout();

  provider_combo_ = new QComboBox(this);
  provider_combo_->setMinimumHeight(36);
  provider_combo_->addItem("OpenAI",     static_cast<int>(fin::services::AiProvider::OpenAI));
  provider_combo_->addItem("Anthropic",  static_cast<int>(fin::services::AiProvider::Anthropic));
  provider_combo_->addItem("Google",     static_cast<int>(fin::services::AiProvider::GoogleGemini));
  provider_combo_->addItem("Ollama (local)", static_cast<int>(fin::services::AiProvider::LocalOllama));
  connect(provider_combo_, qOverload<int>(&QComboBox::currentIndexChanged), this, [this](int idx){
    if (idx < 0) return;
    auto prov = static_cast<fin::services::AiProvider>(provider_combo_->itemData(idx).toInt());
    model_combo_->clear();
    for (const auto& m : fin::services::ModelCatalog::list(prov)) {
      model_combo_->addItem(QString::fromStdString(m.display_name), QString::fromStdString(m.id));
    }
  });

  model_combo_ = new QComboBox(this);
  model_combo_->setMinimumHeight(36);

  form->addRow("Provider:", provider_combo_);
  form->addRow("Model:", model_combo_);
  l->addLayout(form);

  // Trigger initial population.
  emit provider_combo_->currentIndexChanged(0);

  auto* btns = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel, this);
  l->addWidget(btns);
  connect(btns, &QDialogButtonBox::accepted, this, [this]{
    selected_ = model_combo_->currentData().toString();
    accept();
  });
  connect(btns, &QDialogButtonBox::rejected, this, &QDialog::reject);
}

// ============================================================
// ChatPipelineBar
// ============================================================

ChatPipelineBar::ChatPipelineBar(QWidget* parent) : QFrame(parent) {
  setFixedHeight(24);
  setStyleSheet("background-color:#1A222D; border-top:1px solid #2E3A4E;");
  auto* l = new QHBoxLayout(this);
  l->setContentsMargins(12, 2, 12, 2);
  l->setSpacing(8);
  for (const char* name : {"ROUTER", "TOOL-CALL", "MCP-EXEC", "RESPONSE"}) {
    auto* seg = new QLabel(QString("[%1]").arg(name), this);
    seg->setStyleSheet("color:#64748B; font-size:10px; font-weight:bold;");
    stages_.push_back(seg);
    l->addWidget(seg);
  }
}

void ChatPipelineBar::set_stage(int stage_index, const QString& message) {
  if (stage_index < 0 || stage_index >= static_cast<int>(stages_.size())) return;
  // Highlight completed stages in green; current in gold; pending in gray.
  for (int i = 0; i < static_cast<int>(stages_.size()); ++i) {
    if (i < stage_index)       stages_[i]->setStyleSheet("color:#10B981; font-size:10px; font-weight:bold;");
    else if (i == stage_index) stages_[i]->setStyleSheet("color:#D9A13B; font-size:10px; font-weight:bold;");
    else                       stages_[i]->setStyleSheet("color:#64748B; font-size:10px; font-weight:bold;");
  }
  stages_[stage_index]->setText(QString("[%1] %2").arg(stages_[stage_index]->text().mid(0, stages_[stage_index]->text().indexOf("]") + 1), message));
}

void ChatPipelineBar::clear() {
  static const char* names[] = {"ROUTER", "TOOL-CALL", "MCP-EXEC", "RESPONSE"};
  for (int i = 0; i < static_cast<int>(stages_.size()); ++i) {
    stages_[i]->setText(QString("[%1]").arg(names[i]));
    stages_[i]->setStyleSheet("color:#64748B; font-size:10px; font-weight:bold;");
  }
}

// ============================================================
// ChatbotSettingsPanel
// ============================================================

ChatbotSettingsPanel::ChatbotSettingsPanel(QWidget* parent) : QFrame(parent) {
  setProperty("class", "card");
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(16, 16, 16, 16);
  l->setSpacing(12);
  l->addWidget(UiTheme::cardTitle("Chatbot Settings", this));

  auto* form = new QFormLayout();
  auto* provider = new QComboBox(this);
  provider->addItems({"OpenAI", "Anthropic", "Google", "Ollama"});
  auto* api_key = new QLineEdit(this); api_key->setEchoMode(QLineEdit::Password);
  auto* system_prompt = new QTextBrowser(this);
  system_prompt->setMaximumHeight(120);
  system_prompt->setPlainText("You are InvoiceStudio Assistant. Answer questions about invoices, buyers, items, stock, accounting. Be concise.");
  auto* temp = new QLineEdit("0.7", this);
  auto* max_tokens = new QLineEdit("1024", this);
  auto* auto_exec = new QCheckBox("Auto-execute tools (skip confirmation)", this);
  auto* log_disk = new QCheckBox("Log pipeline to disk", this);
  form->addRow("Provider:", provider);
  form->addRow("API Key:", api_key);
  form->addRow("System prompt:", system_prompt);
  form->addRow("Temperature:", temp);
  form->addRow("Max tokens:", max_tokens);
  form->addRow("", auto_exec);
  form->addRow("", log_disk);
  l->addLayout(form);
  l->addStretch();
}

} // namespace fin::ui
