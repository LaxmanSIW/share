// fin/app/ui_watchdog.hpp — UI stall detector (skill responsive-ui §11)
//
// Posts a ping every 50ms via a timer; if the ping isn't serviced within 200ms
// the watchdog fires the stall handler (on the watchdog thread, so it can
// capture the UI thread's stack). Ship in release behind a telemetry opt-in.
#pragma once
#include <atomic>
#include <chrono>
#include <functional>
#include <thread>
#include <QObject>
#include <QTimer>

namespace fin::app {

class UiWatchdog : public QObject {
  Q_OBJECT
 public:
  using StallHandler = std::function<void(std::chrono::milliseconds /*stall_duration*/)>;

  explicit UiWatchdog(std::chrono::milliseconds ping = std::chrono::milliseconds(50),
                      std::chrono::milliseconds threshold = std::chrono::milliseconds(200),
                      QObject* parent = nullptr);
  ~UiWatchdog() override;

  void set_handler(StallHandler h) { handler_ = std::move(h); }
  void start();
  void stop();

  // For tests: simulated max stall count.
  std::uint64_t stall_count() const noexcept { return stall_count_.load(); }

 private:
  QTimer        ping_timer_;
  std::atomic<std::int64_t> last_ping_ts_{0};  // ms since epoch, set by ping_timer
  std::atomic<std::uint64_t> stall_count_{0};
  std::atomic<bool> running_{false};
  std::thread    watcher_;
  StallHandler   handler_;
  std::chrono::milliseconds ping_;
  std::chrono::milliseconds threshold_;
};

} // namespace fin::app
