// fin/services/mcp.hpp — MCP server (port of Java mcp/McpServer.java)
//
// The Model Context Protocol exposes InvoiceStudio's data and operations as
// "tools" that AI assistants can call. Examples:
//   - search_buyers(query) → list of buyers
//   - create_bill(buyer, items, ...) → new bill id
//   - get_stock(item_id) → current stock balance
//   - export_invoice_pdf(bill_id) → file path
//
// Skill rule (responsive-ui §11.3): tool execution runs on the DB pool
// (read tools) or the writer actor (write tools); results are JSON
// projections (McpProjections) consumed by the AI as context.
#pragma once
#include <functional>
#include <map>
#include <memory>
#include <string>
#include <string_view>
#include <vector>
#include <chrono>

namespace fin::services {

struct McpToolResult {
  bool ok{false};
  std::string json_output;   // projected result for the AI
  std::string error;
  std::chrono::milliseconds duration_ms{0};
};

struct McpTool {
  std::string name;
  std::string description;
  std::string input_schema_json;   // JSON Schema for arguments
  bool        read_only{true};        // false → writer actor
  std::function<McpToolResult(const std::string& args_json)> invoke;
};

struct McpToolCall {
  std::string id;
  std::string name;
  std::string args_json;
};

struct McpToolResponse {
  std::string id;
  McpToolResult result;
};

class McpServer {
 public:
  /// Initialise the MCP server with the standard InvoiceStudio tools.
  /// The server runs on localhost only (loopback) for security.
  static void init(int port = 0);  // 0 = auto-pick free port

  /// Register a custom tool (UI plugins, knowledge hub extensions, etc.).
  static void register_tool(McpTool tool);

  /// List all registered tools (sent to the AI as the function catalog).
  static std::vector<McpTool> list_tools();

  /// Invoke a tool by name with JSON args. Skill rule: read tools run on the
  /// DB pool; write tools serialise through the writer actor. Returns when
  /// complete; UI MUST call this off-thread.
  static McpToolResult invoke(const std::string& tool_name, const std::string& args_json);

  /// Audit log: append-only log of every tool call (skill §7: audit trail).
  static void log_audit(std::string_view tool_name, std::string_view args,
                        std::string_view user_id, std::string_view result);
};

} // namespace fin::services
