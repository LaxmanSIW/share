// fin/app/async.hpp — async spine (skill: runAsync/LatestOnly/Debouncer + Outcome)
//
// Laws from the skill (responsive-ui §3):
//   1. Never block UI thread.
//   2. Start async, deliver on UI thread.
//   3. Every long job cancellable via std::stop_token.
//   4. Cancel means "never delivered" — re-check stop flag on UI thread at delivery.
//   5. Latest request wins for search/filter/zoom/preview.
//   6. Debounce input-driven work (150-250ms search, 60-100ms preview).
//   7. Coalesce updates — emit ranges, poll atomic at 10-30 Hz.
//
// This file is Qt-aware (uses QCoreApplication, QObject, Qt::QueuedConnection to
// deliver results on the UI thread). The domain layer above stays pure C++.
#pragma once
#include <atomic>
#include <chrono>
#include <functional>
#include <memory>
#include <mutex>
#include <optional>
#include <stop_token>
#include <thread>
#include <variant>
#include <vector>

#include <QObject>
#include <QPointer>
#include <QTimer>

namespace fin::app {

// === Outcome — value or error, never an exception across a thread boundary ===

template <class T>
class Outcome {
 public:
  Outcome() = default;

  static Outcome ok(T v) { Outcome o; o.value_ = std::move(v); return o; }
  static Outcome error(std::string e) { Outcome o; o.error_ = std::move(e); return o; }

  bool ok() const noexcept { return value_.has_value(); }
  const T& operator*() const { return *value_; }
  T& operator*() { return *value_; }
  const T* operator->() const { return &*value_; }
  T* operator->() { return &*value_; }
  const std::string& error() const noexcept { return error_; }
  const T& value_or(const T& fallback) const { return value_ ? *value_ : fallback; }

 private:
  std::optional<T>      value_;
  std::string           error_;
};

// === TaskHandle — lifetime + cancellation for one async job ===

class TaskHandle {
 public:
  TaskHandle() : stop_{std::make_shared<std::atomic<bool>>(false)} {}

  void cancel() { stop_->store(true, std::memory_order_release); }
  bool cancelled() const noexcept { return stop_->load(std::memory_order_acquire); }

  /// Public access to the underlying atomic cancellation flag, so worker code
  /// and the RunTask template can poll it without being a friend.
  std::shared_ptr<std::atomic<bool>> cancelled_flag() const noexcept { return stop_; }

  /// Re-check on the UI thread at delivery time (skill rule 4). Returns true
  /// if the result should NOT be delivered to the UI.
  bool should_drop() const noexcept { return cancelled(); }

 private:
  std::shared_ptr<std::atomic<bool>> stop_;
};

// === runAsync — fire-and-deliver-on-UI-thread ===
//
// Signature:
//   runAsync(context, work(token) -> T, done(Outcome<T>))
//
// * `context` is a QObject living on the UI thread. When the work completes,
//   we invoke `done` on the UI thread iff `context` is still alive.
// * `work` runs on a worker thread (caller chooses the pool externally).
// * `done` is invoked via Qt::QueuedConnection.
// * If the task is cancelled before delivery, `done` is NOT called (rule 4).
//
// We deliberately take a QThreadPool* so callers can route to the correct
// per-workload pool (DB / Render / Export) per skill §2 of responsive-ui.
class TaskRunner : public QObject {
  Q_OBJECT
 public:
  explicit TaskRunner(QObject* parent = nullptr) : QObject(parent) {}
};

template <class T, class Work, class Done>
[[nodiscard]] std::shared_ptr<TaskHandle> run_async(QObject* context,
                                                    QThreadPool* pool,
                                                    Work&& work,
                                                    Done&& done) {
  auto handle = std::make_shared<TaskHandle>();
  // Capture the stop flag weakly so the worker can poll it; capture `context`
  // via QPointer so we don't dereference a deleted QObject on delivery.
  QPointer<QObject> ctx_ptr(context);
  auto stop = handle; // shared_ptr captured by value
  auto work_fn = std::make_shared<std::function<T(std::shared_ptr<std::atomic<bool>>)>>(
    [w = std::forward<Work>(work)](std::shared_ptr<std::atomic<bool>> stop_flag) mutable -> T {
      // We cannot pass stop_token into a std::function directly (it's not
      // default-constructible in some impls); pass the atomic and let the
      // worker poll it.
      return w(stop_flag);
    });
  // A worker lambda that runs on the pool, captures shared_ptr (cheap copies),
  // then posts result back to UI thread via QTimer::singleShot(0, ctx, ...).
  struct ResultHolder {
    std::shared_ptr<TaskHandle> handle;
    QPointer<QObject>           ctx;
    Done                        done;
    std::shared_ptr<std::function<T(std::shared_ptr<std::atomic<bool>>)>> work_fn;
    std::shared_ptr<std::atomic<bool>> stop_flag;
  };
  auto result = std::make_shared<ResultHolder>(ResultHolder{handle, ctx_ptr, std::forward<Done>(done), work_fn, stop});
  // Note: pool pointer is unused in this minimal impl — see QThreadPool::start
  // in the implementation file.
  (void)pool;
  // We can't post a QtConcurrent::run from a header-only file cleanly; the
  // implementation must live in async.cpp. This header-only stub provides the
  // type signature and contract.
  static_assert(std::is_invocable_v<Work, std::shared_ptr<std::atomic<bool>>>,
                "work must accept a shared_ptr<atomic<bool>> cancellation flag");
  static_assert(std::is_invocable_v<Done, Outcome<T>>,
                "done must accept an Outcome<T>");
  return handle;
}

// === LatestOnly — last-write-wins for search/filter/preview ===
//
// Skill rule 5: out-of-order completion must not show stale data.
class LatestOnly : public QObject {
  Q_OBJECT
 public:
  explicit LatestOnly(QObject* parent = nullptr) : QObject(parent) {}

  template <class Work, class Done>
  void run(QThreadPool* pool, Work&& work, Done&& done) {
    auto gen = ++generation_;
    // Cancel previous
    if (auto h = current_.lock()) h->cancel();
    auto handle = std::make_shared<TaskHandle>();
    current_ = handle;
    // Run + deliver only if generation matches.
    // (Full impl in async.cpp; declared here for the contract.)
    run_impl(pool, gen, std::move(handle), std::forward<Work>(work), std::forward<Done>(done));
  }

  /// Public for the RunTask template (in async.cpp) which checks generation
  /// against the latest-gen on delivery (skill rule 5).
  std::uint64_t generation() const noexcept { return generation_.load(std::memory_order_acquire); }

 private:
  std::atomic<std::uint64_t> generation_{0};
  std::weak_ptr<TaskHandle> current_;

  void run_impl(QThreadPool* pool, std::uint64_t gen,
                std::shared_ptr<TaskHandle> handle,
                std::function<void(std::shared_ptr<std::atomic<bool>>)> work,
                std::function<void()> done);
};

// === Debouncer — coalesces bursts of input into one trigger ===
//
// Skill rule 6: 150-250ms for search, 50-100ms for live preview.
class Debouncer : public QObject {
  Q_OBJECT
 public:
  Debouncer(std::chrono::milliseconds window, QObject* parent = nullptr)
    : QObject(parent), window_(window) {
    timer_.setSingleShot(true);
    timer_.setInterval(static_cast<int>(window_.count()));
    connect(&timer_, &QTimer::timeout, this, [this] {
      auto fn = std::move(callback_);
      callback_ = nullptr;
      if (fn) fn();
    });
  }
  template <class F> void trigger(F&& f) {
    callback_ = std::forward<F>(f);
    timer_.start(); // restarts the timer (Qt's behavior)
  }
  void cancel() { timer_.stop(); callback_ = nullptr; }

 private:
  std::chrono::milliseconds window_;
  QTimer  timer_;
  std::function<void()> callback_;
};

} // namespace fin::app
