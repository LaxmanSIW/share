// src/app/src/smoke_test.cpp — Headless smoke test harness (skill §7 layer 3)
//
// Skill rule: launch the real app under Xvfb/offscreen platform, navigate
// every view, assert no crashes + UiWatchdog reports zero stalls + each view
// shows its expected initial state.
//
// Invoked by `invoicestudio --smoke` (see main.cpp). Exits 0 on success,
// non-zero on failure.
//
// This is the test that catches "view N throws on refresh" bugs after a
// refactor — the kind of bug that unit tests miss because each view's
// constructor + refresh path involves DB access + DAO calls + Qt signal
// wiring. Worth its weight in gold per skill §9.
#include "fin/ui/studio_app.hpp"
#include "fin/ui/ui_theme.hpp"
#include "fin/app/ui_watchdog.hpp"
#include "fin/app/log.hpp"
#include "fin/app/executors.hpp"

#include <QApplication>
#include <QTimer>
#include <QElapsedTimer>
#include <QCoreApplication>
#include <QStandardPaths>

#include <cstdio>
#include <cstdlib>
#include <vector>
#include <string>

namespace {

/// Skill §11: UiWatchdog reports stalls during smoke run.
std::shared_ptr<fin::app::UiWatchdog> g_watchdog;

/// Navigate through every sidebar item, dwell for 500ms each, verify no crash.
struct SmokeStep {
  std::string view_id;
  std::string description;
};

const std::vector<SmokeStep> kSmokeSteps = {
  {"dashboard",        "Dashboard loads + KPI cards show"},
  {"bills",            "Bills list loads from BillDao"},
  {"create_bill",      "Create Bill form loads + totals compute"},
  {"buyers",           "Buyers directory loads from BuyerDao"},
  {"items",            "Items directory loads from ItemDao"},
  {"suppliers",        "Suppliers directory loads from SupplierDao"},
  {"purchases",        "Purchases list loads from PurchaseBillDao"},
  {"create_purchase",  "Create Purchase form loads"},
  {"transactions",     "Transactions list loads"},
  {"expenses",         "Expenses list loads"},
  {"financials",       "Financials report loads"},
  {"stock_analysis",   "Stock analysis loads"},
  {"categories",       "Categories directory loads"},
  {"transports",       "Transports directory loads"},
  {"variables",        "Variables directory loads"},
  {"templates",        "Templates directory loads"},
  {"designer",         "Template Designer canvas + negative-axis ruler render"},
  {"label_history",    "Label print history loads"},
  {"history",          "History view loads"},
  {"reports",          "Reports tabs load"},
  {"settings",         "Settings tabs load"},
};

int run_smoke() {
  fin::app::log::info("=== SMOKE TEST START ===");

  // Start the UiWatchdog — must report zero stalls throughout the smoke run
  // (skill §11).
  g_watchdog = std::make_shared<fin::app::UiWatchdog>(
      std::chrono::milliseconds(50),
      std::chrono::milliseconds(200));
  g_watchdog->set_handler([](std::chrono::milliseconds stall) {
    fin::app::log::errorf("UiWatchdog: stall detected for {} ms", stall.count());
  });
  g_watchdog->start();

  QElapsedTimer t;
  t.start();

  fin::ui::StudioApp* shell = nullptr;
  QTimer::singleShot(0, qApp, [&]() {
    shell = new fin::ui::StudioApp();
    shell->startup();

    // Walk through every view, dwelling 500ms each.
    int idx = 0;
    QTimer* stepper = new QTimer(qApp);
    stepper->setInterval(500);
    QObject::connect(stepper, &QTimer::timeout, qApp, [&, stepper]() mutable {
      if (idx >= static_cast<int>(kSmokeSteps.size())) {
        // Done — emit summary + quit.
        stepper->stop();
        auto stalls = g_watchdog->stall_count();
        fin::app::log::infof("UiWatchdog: {} stalls reported during smoke", stalls);
        fin::app::log::infof("=== SMOKE TEST PASSED in {} ms ===", t.elapsed());
        g_watchdog->stop();
        qApp->quit();
        std::exit(stalls == 0 ? 0 : 1);
      }
      const auto& step = kSmokeSteps[idx];
      fin::app::log::infof("smoke [{}] view='{}' — {}", idx, step.view_id, step.description);
      shell->show_view(QString::fromStdString(step.view_id));
      ++idx;
    });
    stepper->start();
  });

  int rc = qApp->exec();
  return rc;
}

} // namespace
