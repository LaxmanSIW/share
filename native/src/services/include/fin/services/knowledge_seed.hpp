// fin/services/knowledge_seed.hpp — KnowledgeSeed (port of Java KnowledgeSeed.java, 1552 lines)
//
// Seeds the knowledge base with the curated article collection bundled in
// resources/knowledge/knowledge-hub.json. Called once at first run.
#pragma once
namespace fin::services {

class KnowledgeSeed {
 public:
  /// If the user has no knowledge-hub.json in their data dir, copy the
  /// bundled one (from resources/) into the data dir. Idempotent.
  static bool seed_if_missing();

  /// Force re-seed (overwrites the user's local copy with the bundled one).
  /// Used by Help → "Restore default articles".
  static bool force_reseed();
};

} // namespace fin::services
