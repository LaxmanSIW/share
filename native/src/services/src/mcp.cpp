// fin/services/mcp.cpp — MCP server + tool registry (Phase 3b skeleton).
//
// The full Java implementation has McpServer (HTTP server), McpToolRegistry
// (1494 lines of tool definitions), McpArgs, McpProjections, McpEnsure,
// McpAuditLog, McpConfig, PendingOperations, GuideContent, McpImageResult,
// McpSettingsPanel — 11 files totaling ~3000 lines.
//
// In C++ we collapse these into:
//   - fin/services/mcp.hpp — public API (single header)
//   - fin/services/mcp.cpp — registry + invocation + audit
//
// The actual HTTP server (which serves the JSON-RPC endpoint to AI clients)
// is added in the UI phase when we have the QNetworkAccessManager wiring.
#include "fin/services/mcp.hpp"
#include "fin/app/log.hpp"
#include "fin/app/executors.hpp"

#include <nlohmann/json.hpp>

#include <QTimer>
#include <QCoreApplication>

#include <atomic>
#include <chrono>
#include <map>
#include <mutex>
#include <string>

namespace fin::services {

namespace {

std::mutex g_registry_mtx;
std::vector<McpTool> g_registry;
std::atomic<int>    g_port{0};
std::atomic<bool>   g_initialised{false};

void register_builtin_tools_locked_() {
  // Phase 3b: real impl registers ~40 tools covering bills, buyers, items,
  // suppliers, purchases, stock, reports, expenses, templates, exports.
  // Each tool: name, description, JSON Schema, read_only flag, invoke closure.
  // The closure calls into the appropriate DAO + service.
  // For now, register a single demo tool so the API surface is exercised.
  McpTool demo;
  demo.name = "ping";
  demo.description = "Echo back the argument. Useful for testing MCP wiring.";
  demo.input_schema_json = R"({"type":"object","properties":{"msg":{"type":"string"}},"required":["msg"]})";
  demo.read_only = true;
  demo.invoke = [](const std::string& args_json) -> McpToolResult {
    McpToolResult r;
    try {
      auto j = nlohmann::json::parse(args_json);
      r.json_output = nlohmann::json({{"echo", j.value("msg", "")}}).dump();
      r.ok = true;
    } catch (const std::exception& e) {
      r.error = e.what();
    }
    return r;
  };
  g_registry.push_back(std::move(demo));
}

} // namespace

void McpServer::init(int port) {
  std::lock_guard lock(g_registry_mtx);
  if (g_initialised.exchange(true)) return;
  g_port.store(port);
  register_builtin_tools_locked_();
  fin::app::log::infof("McpServer initialised (port={}); {} tools registered",
                       port, g_registry.size());
}

void McpServer::register_tool(McpTool tool) {
  std::lock_guard lock(g_registry_mtx);
  // Replace if name exists.
  for (auto& t : g_registry) {
    if (t.name == tool.name) { t = std::move(tool); return; }
  }
  g_registry.push_back(std::move(tool));
}

std::vector<McpTool> McpServer::list_tools() {
  std::lock_guard lock(g_registry_mtx);
  return g_registry;
}

McpToolResult McpServer::invoke(const std::string& tool_name, const std::string& args_json) {
  // Skill rule §3 (responsive-ui): read tools run on DB pool, write tools on
  // the writer actor. For now we invoke synchronously from the calling thread;
  // the UI MUST call this off-thread via fin::app::run_async on Executors::db().
  McpToolResult result;
  result.duration_ms = std::chrono::milliseconds{0};

  McpTool tool;
  {
    std::lock_guard lock(g_registry_mtx);
    for (const auto& t : g_registry) {
      if (t.name == tool_name) { tool = t; break; }
    }
  }
  if (tool.name.empty()) {
    result.error = "Unknown tool: " + tool_name;
    return result;
  }

  auto t0 = std::chrono::steady_clock::now();
  try {
    result = tool.invoke(args_json);
  } catch (const std::exception& e) {
    result.ok = false;
    result.error = std::string("Exception: ") + e.what();
  } catch (...) {
    result.ok = false;
    result.error = "Unknown exception in tool invocation";
  }
  auto t1 = std::chrono::steady_clock::now();
  result.duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0);
  return result;
}

void McpServer::log_audit(std::string_view tool_name, std::string_view args,
                           std::string_view user_id, std::string_view result) {
  // Skill §7 audit trail: append to a log file with timestamp + caller + tool
  // + args + result. In a future phase this becomes an SQLite audit_log table
  // inside the posting transaction (per skill §7 numbering + audit rules).
  fin::app::log::infof("[MCP-AUDIT] user={} tool={} args={} result={}",
                       user_id, tool_name, args, result);
}

} // namespace fin::services
