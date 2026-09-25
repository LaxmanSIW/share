// fin/ui/panels/mcp_settings_panel.hpp — MCP server settings panel (port of Java McpSettingsPanel)
#pragma once
#include <QFrame>
class QLineEdit;
class QSpinBox;
class QTextEdit;

namespace fin::ui {

class McpSettingsPanel : public QFrame {
  Q_OBJECT
 public:
  explicit McpSettingsPanel(QWidget* parent = nullptr);
};

} // namespace fin::ui
