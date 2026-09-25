// fin/app/executors.cpp
#include "fin/app/executors.hpp"

#include <QThread>
#include <QCoreApplication>

namespace fin::app {

namespace {

QThreadPool* make_pool(const char* name, int threads) {
  auto* pool = new QThreadPool(qApp);
  pool->setMaxThreadCount(threads);
  // Qt 6: setThreadPriority is supported; we use the default.
  // Naming: Qt does not name threads; profiling will show as QtThread.
  // Skill: pools are daemon — setExpiryTimeout lets idle threads die quickly.
  pool->setExpiryTimeout(30000); // 30s idle expiry
  (void)name;
  return pool;
}

QThreadPool* g_db      = nullptr;
QThreadPool* g_render  = nullptr;
QThreadPool* g_export  = nullptr;
QThreadPool* g_net     = nullptr;

QThreadPool* db_inst()       { if (!g_db)     g_db     = make_pool("fin-db",     4); return g_db; }
QThreadPool* render_inst()   { if (!g_render) g_render = make_pool("fin-render", std::max(2, QThread::idealThreadCount() - 1)); return g_render; }
QThreadPool* export_inst()  { if (!g_export) g_export = make_pool("fin-export", 2); return g_export; }
QThreadPool* net_inst()     { if (!g_net)    g_net    = make_pool("fin-net",    4); return g_net; }

} // namespace

QThreadPool* Executors::db()         { return db_inst(); }
QThreadPool* Executors::render()     { return render_inst(); }
QThreadPool* Executors::export_pool(){ return export_inst(); }
QThreadPool* Executors::net()        { return net_inst(); }

void Executors::shutdown(int timeout_ms) {
  // Skill §8 (responsive-ui startup/shutdown): request stop on all pools, wait
  // with a timeout, flush the writer queue, then destroy. A user must never
  // wait 10s to close the app.
  auto drain = [timeout_ms](QThreadPool*& p) {
    if (!p) return;
    p->clear();
    p->waitForDone(timeout_ms);
    // Do not delete: parented to qApp; Qt deletes on app teardown.
    p = nullptr;
  };
  drain(g_export);
  drain(g_render);
  drain(g_db);
  drain(g_net);
}

} // namespace fin::app
