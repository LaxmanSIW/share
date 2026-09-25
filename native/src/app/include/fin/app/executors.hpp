// fin/app/executors.hpp — Per-workload thread pools (port of Java AppExecutors.java)
//
// Skill responsive-ui §2: separate pools per workload so a 30s import can
// never starve the 10ms page fetch for the visible table.
//   - db():        single-threaded writer actor + small reader pool
//   - render():    CPU-bound layout / display-list / image decode
//   - export_pool():  PDF / print / Excel (long, cancellable)
//   - net():       updates, licence, sync
//
// All pools are daemon (won't block app exit). shutdown() is called from
// Application::stop() / main()'s teardown.
#pragma once
#include <QThreadPool>
#include <memory>

namespace fin::app {

class Executors {
 public:
  /// DB pool: 1 writer + N readers. SQLite is single-writer; we enforce this
  /// at the connection layer (one write connection per process), and let N
  /// reader connections fan out across this pool. Capacity: 4 threads.
  static QThreadPool* db();

  /// Render pool: layout, display-list build, image decode, thumbnails.
  /// Capacity: max(2, cores - 1). Cancellable via std::stop_token.
  static QThreadPool* render();

  /// Export pool: PDF / print / Excel / CSV (long, cancellable).
  /// Capacity: 2 (these jobs are I/O heavy, not CPU-heavy).
  static QThreadPool* export_pool();

  /// Net pool: chatbot / MCP / licence / sync.
  /// Capacity: 4.
  static QThreadPool* net();

  /// Shut down all pools and wait up to `timeout_ms` per pool for in-flight
  /// work to finish. Called from app teardown.
  static void shutdown(int timeout_ms = 5000);
};

} // namespace fin::app
