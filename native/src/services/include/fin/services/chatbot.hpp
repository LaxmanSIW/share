// fin/services/chatbot.hpp — Chatbot config + transcript + log (Java originals:
// ChatbotConfig, ChatbotLogManager, ChatTranscriptStore, ModelCatalog,
// ModelStatusStore, ApiKeysVault, KnowledgeRepository, KnowledgeSeed)
//
// Collapsed into one header because they're small, interdependent, and the
// Java original split them across 8 files mostly due to Java's one-public-
// class-per-file rule.
#pragma once
#include "fin/services/ai_chat.hpp"

#include <chrono>
#include <filesystem>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace fin::services {

// === ApiKeysVault: encrypted at-rest storage of provider API keys ===

class ApiKeysVault {
 public:
  /// Set the master key used to encrypt the vault file. Called once at app
  /// startup; derived from the user's login + a per-install salt (skill §11
  /// security: Argon2id key derivation via libsodium).
  static void set_master_key(std::string_view key);

  /// Load the vault from <data_dir>/vault.enc.
  static bool load();
  /// Save the vault to <data_dir>/vault.enc (atomic via temp file + rename).
  static bool save();

  /// Get/set a per-provider API key. Returns empty if not set.
  static std::string get(AiProvider provider);
  static void set(AiProvider provider, std::string_view key);

 private:
  static std::map<AiProvider, std::string> keys_;
  static std::mutex mtx_;
};

// === ModelCatalog: catalog of available models per provider (with prices) ===

class ModelCatalog {
 public:
  /// Load the catalog from <data_dir>/models.json (a JSON file with the
  /// structure the Java original hardcodes in ModelCatalog.java).
  static bool load();
  /// Save the catalog (used when the user adds a custom model).
  static bool save();

  /// List models for a provider (sorted by display_name).
  static std::vector<AiModelInfo> list(AiProvider provider);
  /// Find a model by id (across all providers).
  static std::optional<AiModelInfo> find(std::string_view model_id);

 private:
  static std::vector<AiModelInfo> catalog_;
  static std::mutex mtx_;
};

// === ModelStatusStore: live status of provider endpoints (last ping time,
// latency, error rate) ===

struct ModelStatus {
  std::string model_id;
  bool       reachable{false};
  std::chrono::milliseconds last_ping_latency{0};
  std::chrono::utc_clock::time_point last_check{};
  std::string last_error;
  double     error_rate{0.0};  // rolling window; 0..1
};

class ModelStatusStore {
 public:
  static ModelStatus get(std::string_view model_id);
  static void update(std::string_view model_id, const ModelStatus& status);
  static void clear();
 private:
  static std::map<std::string, ModelStatus> statuses_;
  static std::mutex mtx_;
};

// === ChatbotConfig: user preferences (selected provider, default model,
// temperature, system prompt, enabled tools) ===

struct ChatbotConfig {
  AiProvider provider{AiProvider::OpenAI};
  std::string default_model_id;       // "gpt-4o-mini" / "claude-3-5-sonnet-20241022" / etc.
  double     temperature{0.7};
  int        max_tokens{1024};
  std::string system_prompt;           // user-customisable; default is invoice-domain-aware
  std::vector<std::string> enabled_tool_names;  // MCP tools the chatbot can call
  bool       auto_execute_tools{false};   // skip confirmation dialog
  bool       log_pipeline_to_disk{false};  // chatbot log manager

  /// Load from <data_dir>/chatbot.json.
  static ChatbotConfig load();
  /// Save to <data_dir>/chatbot.json (atomic via temp + rename).
  bool save() const;
};

// === ChatTranscriptStore: persistent conversation history ===

struct ChatTranscript {
  std::string conversation_id;
  std::vector<AiChatMessage> messages;
  std::chrono::sys_days created_on;
  std::string summary;            // auto-generated when context grows too large
};

class ChatTranscriptStore {
 public:
  /// Load a conversation by id (from <data_dir>/chat_history/<id>.json).
  static ChatTranscript load(std::string_view conversation_id);
  /// Save a conversation (atomic write).
  static bool save(const ChatTranscript& t);
  /// List all conversations (newest first).
  static std::vector<ChatTranscript> list_all();
  /// Delete a conversation.
  static bool erase(std::string_view conversation_id);
};

// === ChatbotLogManager: real-time CLI execution log (skill §11.3) ===
//
// Buffers log entries in a thread-safe bounded collection (max 500 entries).
// Worker threads (net pool) push entries; the UI ChatbotLogDialog polls and
// renders them with colored category badges.

enum class ChatLogCategory : std::uint8_t {
  Router = 0, ToolCall = 1, McpExec = 2, Success = 3, Error = 4, Info = 5,
};

struct ChatLogEntry {
  std::chrono::utc_clock::time_point timestamp;
  ChatLogCategory category;
  std::string message;
};

class ChatbotLogManager {
 public:
  /// Push a log entry from any thread (locks mutex; cheap).
  static void push(ChatLogCategory cat, std::string message);
  /// Drain all entries (called by UI at 30 Hz; clears the buffer).
  static std::vector<ChatLogEntry> drain();
  /// Set the maximum buffer size (default 500; skill §11.3 cap).
  static void set_capacity(std::size_t cap);
 private:
  static std::vector<ChatLogEntry> buffer_;
  static std::mutex mtx_;
  static std::size_t capacity_;
};

// === KnowledgeRepository: searchable knowledge base articles ===

struct KnowledgeArticle {
  std::string id;
  std::string title;
  std::string body_markdown;
  std::vector<std::string> tags;
  std::string category;
};

class KnowledgeRepository {
 public:
  /// Load from <data_dir>/knowledge-hub.json (a curated JSON of articles).
  static bool load();
  /// Save the knowledge base.
  static bool save();

  /// Search articles by query (FTS5; skill §5 SQLite: keyset pagination).
  static std::vector<KnowledgeArticle> search(std::string_view query);
  /// Find an article by id.
  static std::optional<KnowledgeArticle> find(std::string_view id);
  /// List all categories.
  static std::vector<std::string> categories();

 private:
  static std::vector<KnowledgeArticle> articles_;
  static std::mutex mtx_;
};

} // namespace fin::services
