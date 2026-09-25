// fin/ui/panels/mcp_settings_panel.cpp
#include "fin/ui/panels/mcp_settings_panel.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/ui/widgets/data_grid.hpp"
#include "fin/services/mcp.hpp"

#include <QVBoxLayout>
#include <QFormLayout>
#include <QLineEdit>
#include <QSpinBox>
#include <QTextEdit>
#include <QPushButton>
#include <QLabel>
#include <QTimer>

namespace fin::ui {

McpSettingsPanel::McpSettingsPanel(QWidget* parent) : QFrame(parent) {
  setProperty("class", "card");
  auto* l = new QVBoxLayout(this);
  l->setContentsMargins(16, 16, 16, 16);
  l->setSpacing(12);
  l->addWidget(UiTheme::cardTitle("MCP Server Settings", this));

  auto* form = new QFormLayout();
  auto* port = new QSpinBox(this); port->setRange(0, 65535); port->setValue(8765);
  auto* bind = new QLineEdit("127.0.0.1", this);  // localhost only — security.
  form->addRow("Port:", port);
  form->addRow("Bind address:", bind);
  l->addLayout(form);

  // Tools table
  l->addWidget(UiTheme::muted("Registered tools:", this));
  auto* tools_grid = new DataGrid(this);
  tools_grid->set_columns({{"Name", 180}, {"Read-only", 80}, {"Description", 400}, {"", -1}});
  l->addWidget(tools_grid, 1);

  // Populate tool list.
  QTimer::singleShot(0, this, [tools_grid]{
    auto* model = new QStandardItemModel(0, 4, tools_grid);
    model->setHorizontalHeaderLabels({"Name", "Read-only", "Description", ""});
    for (const auto& t : fin::services::McpServer::list_tools()) {
      QList<QStandardItem*> row;
      row << new QStandardItem(QString::fromStdString(t.name))
          << new QStandardItem(t.read_only ? "Yes" : "No")
          << new QStandardItem(QString::fromStdString(t.description))
          << new QStandardItem("");
      model->appendRow(row);
    }
    tools_grid->setModel(model);
  });

  // Action buttons
  auto* btn_row = new QHBoxLayout();
  btn_row->addStretch();
  auto* restart = UiTheme::primaryButton("Restart MCP Server", this);
  auto* view_audit = UiTheme::ghostButton("View Audit Log", this);
  btn_row->addWidget(view_audit);
  btn_row->addWidget(restart);
  l->addLayout(btn_row);
}

} // namespace fin::ui
