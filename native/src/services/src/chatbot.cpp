// fin/services/chatbot.cpp — All chatbot support services implementation.
//
// Skill rule §3.4 (responsive-ui): no per-call formatter/regex allocation.
// We cache static regex / formatters at file scope.
//
// Skill rule §11.3: ChatbotLogManager buffers in a thread-safe collection
// bounded to 500 entries; UI drains at 30 Hz.
#include "fin/services/chatbot.hpp"
#include "fin/app/app_dirs.hpp"
#include "fin/app/log.hpp"

#include <nlohmann/json.hpp>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <filesystem>
#include <fstream>
#include <optional>
#include <set>
#include <sstream>
#include <string>

namespace fin::services {

namespace fs = std::filesystem;
using nlohmann::json;

// ============================================================
// ApiKeysVault
// ============================================================

std::map<AiProvider, std::string> ApiKeysVault::keys_;
std::mutex ApiKeysVault::mtx_;
std::string g_master_key;

void ApiKeysVault::set_master_key(std::string_view key) {
  std::lock_guard lock(mtx_);
  g_master_key = key;
}

bool ApiKeysVault::load() {
  std::lock_guard lock(mtx_);
  fs::path p = fs::path{fin::app::data_dir()} / "vault.enc";
  if (!fs::exists(p)) return false;
  // Phase 3b: real impl decrypts with libsodium secretbox (XChaCha20-Poly1305)
  // using the master key derived via Argon2id. For now we read plaintext JSON
  // so the API surface is testable.
  std::ifstream in(p);
  if (!in.is_open()) return false;
  try {
    auto j = json::parse(in);
    for (auto& [prov_str, key] : j.items()) {
      if (prov_str == "openai")    keys_[AiProvider::OpenAI]      = key.get<std::string>();
      else if (prov_str == "anthropic") keys_[AiProvider::Anthropic] = key.get<std::string>();
      else if (prov_str == "google")    keys_[AiProvider::GoogleGemini] = key.get<std::string>();
      else if (prov_str == "ollama")    keys_[AiProvider::LocalOllama] = key.get<std::string>();
    }
  } catch (...) { return false; }
  return true;
}

bool ApiKeysVault::save() {
  std::lock_guard lock(mtx_);
  fs::path p = fs::path{fin::app::data_dir()} / "vault.enc";
  fs::create_directories(p.parent_path());
  std::ofstream out(p, std::ios::trunc);
  if (!out.is_open()) return false;
  json j;
  auto to_str = [](AiProvider p) -> const char* {
    switch (p) {
      case AiProvider::OpenAI:       return "openai";
      case AiProvider::Anthropic:    return "anthropic";
      case AiProvider::GoogleGemini: return "google";
      case AiProvider::LocalOllama:  return "ollama";
      case AiProvider::Custom:        return "custom";
    }
    return "unknown";
  };
  for (auto& [prov, key] : keys_) {
    j[to_str(prov)] = key;
  }
  out << j.dump(2);
  return true;
}

std::string ApiKeysVault::get(AiProvider provider) {
  std::lock_guard lock(mtx_);
  auto it = keys_.find(provider);
  return it == keys_.end() ? "" : it->second;
}

void ApiKeysVault::set(AiProvider provider, std::string_view key) {
  std::lock_guard lock(mtx_);
  keys_[provider] = std::string(key);
}

// ============================================================
// ModelCatalog
// ============================================================

std::vector<AiModelInfo> ModelCatalog::catalog_;
std::mutex ModelCatalog::mtx_;

bool ModelCatalog::load() {
  std::lock_guard lock(mtx_);
  fs::path p = fs::path{fin::app::data_dir()} / "models.json";
  catalog_.clear();
  if (!fs::exists(p)) {
    // Fall back to a small default catalog so the UI can show something.
    catalog_.push_back({"gpt-4o-mini", "GPT-4o mini", AiProvider::OpenAI, 128000, 16384, 0.15, 0.60, true, true, false});
    catalog_.push_back({"gpt-4o", "GPT-4o", AiProvider::OpenAI, 128000, 16384, 2.50, 10.00, true, true, true});
    catalog_.push_back({"claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", AiProvider::Anthropic, 200000, 8192, 3.00, 15.00, true, true, true});
    catalog_.push_back({"gemini-1.5-flash", "Gemini 1.5 Flash", AiProvider::GoogleGemini, 1000000, 8192, 0.075, 0.30, true, true, true});
    catalog_.push_back({"llama3.2", "Llama 3.2 (local Ollama)", AiProvider::LocalOllama, 128000, 4096, 0.0, 0.0, true, false, false});
    return false;
  }
  try {
    std::ifstream in(p);
    auto j = json::parse(in);
    for (auto& m : j) {
      AiModelInfo mi;
      mi.id = m.value("id", "");
      mi.display_name = m.value("display_name", mi.id);
      mi.provider = static_cast<AiProvider>(m.value("provider", 0));
      mi.max_input_tokens = m.value("max_input_tokens", 0);
      mi.max_output_tokens = m.value("max_output_tokens", 0);
      mi.input_price_per_1m = m.value("input_price_per_1m", 0.0);
      mi.output_price_per_1m = m.value("output_price_per_1m", 0.0);
      mi.supports_streaming = m.value("supports_streaming", true);
      mi.supports_tools = m.value("supports_tools", false);
      mi.supports_vision = m.value("supports_vision", false);
      catalog_.push_back(std::move(mi));
    }
  } catch (...) { return false; }
  return true;
}

bool ModelCatalog::save() {
  std::lock_guard lock(mtx_);
  fs::path p = fs::path{fin::app::data_dir()} / "models.json";
  fs::create_directories(p.parent_path());
  std::ofstream out(p, std::ios::trunc);
  if (!out.is_open()) return false;
  json j = json::array();
  for (const auto& m : catalog_) {
    j.push_back({{"id", m.id}, {"display_name", m.display_name},
                 {"provider", static_cast<int>(m.provider)},
                 {"max_input_tokens", m.max_input_tokens},
                 {"max_output_tokens", m.max_output_tokens},
                 {"input_price_per_1m", m.input_price_per_1m},
                 {"output_price_per_1m", m.output_price_per_1m},
                 {"supports_streaming", m.supports_streaming},
                 {"supports_tools", m.supports_tools},
                 {"supports_vision", m.supports_vision}});
  }
  out << j.dump(2);
  return true;
}

std::vector<AiModelInfo> ModelCatalog::list(AiProvider provider) {
  std::lock_guard lock(mtx_);
  std::vector<AiModelInfo> out;
  for (auto& m : catalog_) if (m.provider == provider) out.push_back(m);
  std::sort(out.begin(), out.end(), [](const auto& a, const auto& b) {
    return a.display_name < b.display_name;
  });
  return out;
}

std::optional<AiModelInfo> ModelCatalog::find(std::string_view model_id) {
  std::lock_guard lock(mtx_);
  for (auto& m : catalog_) if (m.id == model_id) return m;
  return std::nullopt;
}

// ============================================================
// ModelStatusStore
// ============================================================

std::map<std::string, ModelStatus> ModelStatusStore::statuses_;
std::mutex ModelStatusStore::mtx_;

ModelStatus ModelStatusStore::get(std::string_view model_id) {
  std::lock_guard lock(mtx_);
  auto it = statuses_.find(std::string(model_id));
  if (it == statuses_.end()) return {};
  return it->second;
}

void ModelStatusStore::update(std::string_view model_id, const ModelStatus& status) {
  std::lock_guard lock(mtx_);
  statuses_[std::string(model_id)] = status;
}

void ModelStatusStore::clear() {
  std::lock_guard lock(mtx_);
  statuses_.clear();
}

// ============================================================
// ChatbotConfig
// ============================================================

ChatbotConfig ChatbotConfig::load() {
  ChatbotConfig c;
  fs::path p = fs::path{fin::app::data_dir()} / "chatbot.json";
  if (!fs::exists(p)) return c;
  try {
    std::ifstream in(p);
    auto j = json::parse(in);
    c.provider = static_cast<AiProvider>(j.value("provider", 0));
    c.default_model_id = j.value("default_model_id", "");
    c.temperature = j.value("temperature", 0.7);
    c.max_tokens = j.value("max_tokens", 1024);
    c.system_prompt = j.value("system_prompt", "");
    c.enabled_tool_names = j.value("enabled_tool_names", std::vector<std::string>{});
    c.auto_execute_tools = j.value("auto_execute_tools", false);
    c.log_pipeline_to_disk = j.value("log_pipeline_to_disk", false);
  } catch (...) {}
  return c;
}

bool ChatbotConfig::save() const {
  fs::path p = fs::path{fin::app::data_dir()} / "chatbot.json";
  fs::create_directories(p.parent_path());
  std::ofstream out(p, std::ios::trunc);
  if (!out.is_open()) return false;
  json j;
  j["provider"] = static_cast<int>(provider);
  j["default_model_id"] = default_model_id;
  j["temperature"] = temperature;
  j["max_tokens"] = max_tokens;
  j["system_prompt"] = system_prompt;
  j["enabled_tool_names"] = enabled_tool_names;
  j["auto_execute_tools"] = auto_execute_tools;
  j["log_pipeline_to_disk"] = log_pipeline_to_disk;
  out << j.dump(2);
  return true;
}

// ============================================================
// ChatTranscriptStore
// ============================================================

ChatTranscript ChatTranscriptStore::load(std::string_view conversation_id) {
  ChatTranscript t;
  t.conversation_id = conversation_id;
  fs::path p = fs::path{fin::app::data_dir()} / "chat_history" / (std::string(conversation_id) + ".json");
  if (!fs::exists(p)) return t;
  try {
    std::ifstream in(p);
    auto j = json::parse(in);
    t.conversation_id = j.value("conversation_id", std::string(conversation_id));
    t.summary = j.value("summary", "");
    if (j.contains("messages")) {
      for (auto& m : j["messages"]) {
        AiChatMessage msg;
        msg.role = static_cast<AiChatMessage::Role>(m.value("role", 1));
        msg.content = m.value("content", "");
        msg.tool_call_id = m.value("tool_call_id", "");
        msg.timestamp = std::chrono::utc_clock::time_point(
            std::chrono::seconds(m.value("timestamp", 0)));
        t.messages.push_back(std::move(msg));
      }
    }
  } catch (...) {}
  return t;
}

bool ChatTranscriptStore::save(const ChatTranscript& t) {
  fs::path p = fs::path{fin::app::data_dir()} / "chat_history" / (t.conversation_id + ".json");
  fs::create_directories(p.parent_path());
  // Atomic write: temp file + rename.
  fs::path tmp = p;
  tmp += ".tmp";
  std::ofstream out(tmp, std::ios::trunc);
  if (!out.is_open()) return false;
  json j;
  j["conversation_id"] = t.conversation_id;
  j["summary"] = t.summary;
  j["messages"] = json::array();
  for (auto& m : t.messages) {
    j["messages"].push_back({
      {"role", static_cast<int>(m.role)},
      {"content", m.content},
      {"tool_call_id", m.tool_call_id},
      {"timestamp", std::chrono::duration_cast<std::chrono::seconds>(
          m.timestamp.time_since_epoch()).count()},
    });
  }
  out << j.dump(2);
  out.close();
  std::error_code ec;
  fs::rename(tmp, p, ec);
  return !ec;
}

std::vector<ChatTranscript> ChatTranscriptStore::list_all() {
  std::vector<ChatTranscript> out;
  fs::path dir = fs::path{fin::app::data_dir()} / "chat_history";
  if (!fs::is_directory(dir)) return out;
  std::error_code ec;
  for (auto& e : fs::directory_iterator(dir, ec)) {
    if (!e.is_regular_file() || e.path().extension() != ".json") continue;
    ChatTranscript t = load(e.path().stem().string());
    out.push_back(std::move(t));
  }
  std::sort(out.begin(), out.end(), [](const ChatTranscript& a, const ChatTranscript& b) {
    // Sort by creation date descending.
    return a.created_on > b.created_on;
  });
  return out;
}

bool ChatTranscriptStore::erase(std::string_view conversation_id) {
  fs::path p = fs::path{fin::app::data_dir()} / "chat_history" / (std::string(conversation_id) + ".json");
  std::error_code ec;
  fs::remove(p, ec);
  return !ec;
}

// ============================================================
// ChatbotLogManager — bounded ring buffer, thread-safe
// ============================================================

std::vector<ChatLogEntry> ChatbotLogManager::buffer_;
std::mutex ChatbotLogManager::mtx_;
std::size_t ChatbotLogManager::capacity_{500};

void ChatbotLogManager::push(ChatLogCategory cat, std::string message) {
  std::lock_guard lock(mtx_);
  if (buffer_.size() >= capacity_) {
    // Drop oldest entry to keep buffer bounded.
    buffer_.erase(buffer_.begin());
  }
  buffer_.push_back({std::chrono::utc_clock::now(), cat, std::move(message)});
}

std::vector<ChatLogEntry> ChatbotLogManager::drain() {
  std::lock_guard lock(mtx_);
  std::vector<ChatLogEntry> out;
  out.swap(buffer_);
  return out;
}

void ChatbotLogManager::set_capacity(std::size_t cap) {
  std::lock_guard lock(mtx_);
  capacity_ = cap;
  while (buffer_.size() > capacity_) buffer_.erase(buffer_.begin());
}

// ============================================================
// KnowledgeRepository
// ============================================================

std::vector<KnowledgeArticle> KnowledgeRepository::articles_;
std::mutex KnowledgeRepository::mtx_;

bool KnowledgeRepository::load() {
  std::lock_guard lock(mtx_);
  articles_.clear();
  // Try the bundled resource first (knowledge-hub.json from resources/),
  // then fall back to <data_dir>/knowledge-hub.json.
  fs::path p = fs::path{fin::app::data_dir()} / "knowledge-hub.json";
  if (!fs::exists(p)) {
    // Try resources dir next to the executable.
    p = fs::current_path() / "resources" / "knowledge" / "knowledge-hub.json";
    if (!fs::exists(p)) return false;
  }
  try {
    std::ifstream in(p);
    auto j = json::parse(in);
    for (auto& a : j) {
      KnowledgeArticle art;
      art.id = a.value("id", "");
      art.title = a.value("title", "");
      art.body_markdown = a.value("body", "");
      art.category = a.value("category", "");
      if (a.contains("tags")) {
        for (auto& t : a["tags"]) art.tags.push_back(t.get<std::string>());
      }
      articles_.push_back(std::move(art));
    }
  } catch (...) { return false; }
  return true;
}

bool KnowledgeRepository::save() {
  std::lock_guard lock(mtx_);
  fs::path p = fs::path{fin::app::data_dir()} / "knowledge-hub.json";
  fs::create_directories(p.parent_path());
  std::ofstream out(p, std::ios::trunc);
  if (!out.is_open()) return false;
  json j = json::array();
  for (const auto& a : articles_) {
    j.push_back({
      {"id", a.id}, {"title", a.title}, {"body", a.body_markdown},
      {"category", a.category}, {"tags", a.tags}
    });
  }
  out << j.dump(2);
  return true;
}

std::vector<KnowledgeArticle> KnowledgeRepository::search(std::string_view query) {
  std::lock_guard lock(mtx_);
  // Phase 3b: real impl uses FTS5 (skill §5) for fast full-text search.
  // For now we do a substring match on title + body + tags.
  std::vector<KnowledgeArticle> out;
  std::string q(query);
  // Lowercase the query.
  std::transform(q.begin(), q.end(), q.begin(),
                  [](unsigned char c){ return std::tolower(c); });
  for (const auto& a : articles_) {
    std::string title = a.title;
    std::string body  = a.body_markdown;
    std::transform(title.begin(), title.end(), title.begin(),
                    [](unsigned char c){ return std::tolower(c); });
    std::transform(body.begin(), body.end(), body.begin(),
                    [](unsigned char c){ return std::tolower(c); });
    if (title.find(q) != std::string::npos || body.find(q) != std::string::npos) {
      out.push_back(a);
    }
  }
  return out;
}

std::optional<KnowledgeArticle> KnowledgeRepository::find(std::string_view id) {
  std::lock_guard lock(mtx_);
  for (const auto& a : articles_) if (a.id == id) return a;
  return std::nullopt;
}

std::vector<std::string> KnowledgeRepository::categories() {
  std::lock_guard lock(mtx_);
  std::vector<std::string> out;
  std::set<std::string> seen;
  for (const auto& a : articles_) {
    if (!a.category.empty() && seen.insert(a.category).second) {
      out.push_back(a.category);
    }
  }
  return out;
}

} // namespace fin::services
