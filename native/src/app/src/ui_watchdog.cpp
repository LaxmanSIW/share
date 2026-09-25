// fin/app/ui_watchdog.cpp
#include "fin/app/ui_watchdog.hpp"

#include <QCoreApplication>
#include <QMetaObject>
#include <QThread>
#include <chrono>

namespace fin::app {

UiWatchdog::UiWatchdog(std::chrono::milliseconds ping, std::chrono::milliseconds threshold, QObject* parent)
  : QObject(parent), ping_(ping), threshold_(threshold) {
  ping_timer_.setInterval(static_cast<int>(ping_.count()));
  ping_timer_.setTimerType(Qt::PreciseTimer);
  connect(&ping_timer_, &QTimer::timeout, this, [this] {
    last_ping_ts_.store(std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count(),
        std::memory_order_release);
  });
}

UiWatchdog::~UiWatchdog() {
  stop();
}

void UiWatchdog::start() {
  if (running_.exchange(true)) return;
  ping_timer_.start();
  watcher_ = std::thread([this] {
    while (running_.load(std::memory_order_acquire)) {
      std::this_thread::sleep_for(ping_);
      auto last = last_ping_ts_.load(std::memory_order_acquire);
      auto now  = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count();
      if (last == 0) continue; // not yet started
      auto age_ms = now - last;
      if (age_ms > threshold_.count()) {
        stall_count_.fetch_add(1, std::memory_order_relaxed);
        if (handler_) {
          QMetaObject::invokeMethod(qApp, [this, age_ms] {
            if (handler_) handler_(std::chrono::milliseconds(age_ms));
          });
        }
      }
    }
  });
}

void UiWatchdog::stop() {
  if (!running_.exchange(false)) return;
  ping_timer_.stop();
  if (watcher_.joinable()) watcher_.join();
}

} // namespace fin::app

#include "ui_watchdog.moc"
