// fin/services/ai_chat.hpp — AiChatClient (port of Java AiChatClient.java)
//
// LLM chat client with streaming SSE responses. Matches the Java original's
// features:
//   - Multi-provider routing (OpenAI, Anthropic, Google Gemini, local Ollama)
//   - Streaming responses via SSE (Server-Sent Events)
//   - Tool calling for MCP (Model Context Protocol) tools
//   - Conversation history with summarisation
//   - Token usage tracking + cost estimation
//   - Per-provider retry / backoff
//
// Skill rule (responsive-ui §1, §11.3): NEVER call on the UI thread.
// Conversations go through fin::app::Executors::net(); streaming tokens are
// delivered to the UI via a callback that throttles at ~30 Hz (skill §3.7
// coalesce).
#pragma once
#include <chrono>
#include <functional>
#include <map>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

namespace fin::services {

enum class AiProvider : std::uint8_t {
  OpenAI = 0,
  Anthropic = 1,
  GoogleGemini = 2,
  LocalOllama = 3,
  Custom = 4,
};

struct AiModelInfo {
  std::string id;          // "gpt-4o-mini", "claude-3-5-sonnet-20241022", "gemini-1.5-flash"
  std::string display_name;
  AiProvider  provider;
  int         max_input_tokens{0};
  int         max_output_tokens{0};
  double      input_price_per_1m{0.0};   // USD per 1M tokens
  double      output_price_per_1m{0.0};
  bool        supports_streaming{true};
  bool        supports_tools{false};
  bool        supports_vision{false};
};

struct AiChatMessage {
  enum class Role { System, User, Assistant, Tool } role{Role::User};
  std::string content;
  std::string tool_call_id;   // for tool responses
  std::vector<std::string> tool_calls;  // for assistant messages requesting tool calls
  std::chrono::utc_clock::time_point timestamp;
};

struct AiChatUsage {
  int prompt_tokens{0};
  int completion_tokens{0};
  double estimated_cost_usd{0.0};
};

struct AiChatRequest {
  std::string conversation_id;          // for transcript persistence
  std::vector<AiChatMessage> history;
  std::string system_prompt;
  AiModelInfo model;
  double     temperature{0.7};
  int        max_tokens{1024};
  bool       stream{true};
  std::vector<std::string> enabled_tool_names;   // MCP tools the model can call
};

struct AiChatChunk {
  std::string content_delta;
  std::vector<std::string> tool_calls_delta;
  AiChatUsage usage;
  std::string error;
  bool done{false};
};

class AiChatClient {
 public:
  /// Initialise with per-provider API keys (loaded from ApiKeysVault).
  static void init(std::map<AiProvider, std::string> api_keys);

  /// Send a chat request with streaming. `on_chunk` is called on the UI
  /// thread for each SSE event (throttled at ~30 Hz per skill §3.7).
  /// `on_done` is called once when the response is complete.
  /// Returns a cancellation handle — call cancel() to abort the request.
  class RequestHandle {
   public:
    void cancel();
    bool cancelled() const noexcept;
   private:
    std::shared_ptr<std::atomic<bool>> stop_flag_{std::make_shared<std::atomic<bool>>(false)};
    friend class AiChatClient;
  };
  static std::shared_ptr<RequestHandle> send_streaming(
      const AiChatRequest& req,
      std::function<void(const AiChatChunk&)> on_chunk,
      std::function<void(const AiChatChunk&)> on_done);

  /// List available models per provider (loaded from a ModelCatalog JSON).
  static std::vector<AiModelInfo> list_models(AiProvider provider);
};

} // namespace fin::services
