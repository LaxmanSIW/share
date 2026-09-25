// fin/services/ai_chat.cpp
//
// Phase 3b skeleton: the real implementation uses QNetworkAccessManager to
// POST to each provider's chat completions endpoint, parse the SSE stream
// (`data:` chunks with JSON content_delta), and deliver them to the UI
// callback. For now this is a stub that emits an error chunk.
#include "fin/services/ai_chat.hpp"
#include "fin/app/log.hpp"

#include <QTimer>
#include <QCoreApplication>

#include <atomic>
#include <chrono>

namespace fin::services {

namespace {
std::map<AiProvider, std::string> g_api_keys;
}

void AiChatClient::init(std::map<AiProvider, std::string> api_keys) {
  g_api_keys = std::move(api_keys);
}

void AiChatClient::RequestHandle::cancel() { stop_flag_->store(true, std::memory_order_release); }
bool AiChatClient::RequestHandle::cancelled() const noexcept { return stop_flag_->load(std::memory_order_acquire); }

std::shared_ptr<AiChatClient::RequestHandle> AiChatClient::send_streaming(
    const AiChatRequest& req,
    std::function<void(const AiChatChunk&)> on_chunk,
    std::function<void(const AiChatChunk&)> on_done) {
  auto handle = std::make_shared<RequestHandle>();

  // Phase 3b: real impl posts to:
  //   OpenAI:    https://api.openai.com/v1/chat/completions  (stream=true)
  //   Anthropic: https://api.anthropic.com/v1/messages      (stream=true)
  //   Gemini:    https://generativelanguage.googleapis.com/v1beta/models/<model>:streamGenerateContent
  //   Ollama:    http://localhost:11434/api/chat            (stream=true)
  // and parses SSE data: chunks per skill §11.3 (responsive-ui).

  AiChatChunk error_chunk;
  error_chunk.error = "Phase 3b: streaming not implemented";
  error_chunk.done = true;
  (void)req;

  // Skill rule §1.2 + §4: deliver on UI thread.
  QTimer::singleShot(0, qApp, [handle, error_chunk, on_chunk, on_done]() mutable {
    if (handle->cancelled()) return;  // skill rule 4: re-check at delivery
    if (on_chunk) on_chunk(error_chunk);
    if (on_done)  on_done(error_chunk);
  });
  return handle;
}

std::vector<AiModelInfo> AiChatClient::list_models(AiProvider provider) {
  // Phase 3b: real impl loads from ModelCatalog.json (the Java original has
  // a baked-in catalog that maps provider -> list of models with prices).
  (void)provider;
  return {};
}

} // namespace fin::services
