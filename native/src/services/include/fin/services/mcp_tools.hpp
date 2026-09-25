// fin/services/mcp_tools.hpp — Full MCP tool registry (~40 tools)
//
// Port of Java McpToolRegistry (1,940 lines). Each tool exposes data + operations
// to AI assistants via the MCP protocol. Read tools run on the DB pool; write
// tools serialise through the writer actor.
//
// Categories:
//   - Bills (create, list, find, mark_paid, add_payment, delete)
//   - Buyers (create, list, find, update, delete)
//   - Items (create, list, find, update_stock, delete)
//   - Suppliers (create, list, find, update, delete)
//   - Purchases (create, list, find, mark_received, delete)
//   - Stock (current_stock, movements, valuation, reorder_report)
//   - Reports (trial_balance, profit_loss, gst_summary, ageing)
//   - Expenses (create, list, total_by_category)
//   - Templates (list, get, save, export_package)
//   - Settings (get, update)
//   - Auth (whoami, sign_out)
//   - Export (pdf_invoice, csv_buyers, csv_items)
#pragma once
#include "fin/services/mcp.hpp"

namespace fin::services {

class McpTools {
 public:
  /// Register all ~40 built-in tools on the given McpServer.
  static void register_all(McpServer& server);
};

} // namespace fin::services
