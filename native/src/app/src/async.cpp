// fin/app/async.cpp — implementation of the async spine.
#include "fin/app/async.hpp"

#include <QCoreApplication>
#include <QPointer>
#include <QThreadPool>
#include <QRunnable>
#include <QMetaObject>
#include <QTimer>

namespace fin::app {

namespace {

// A QRunnable that owns a work closure and a done closure. The done closure
// is posted to the UI thread via QMetaObject::invokeMethod with
// Qt::QueuedConnection.
template <class T>
class RunTask : public QRunnable {
 public:
  using WorkFn  = std::function<T(std::shared_ptr<std::atomic<bool>>)>;
  using DoneFn  = std::function<void(Outcome<T>)>;

  RunTask(QObject* ctx, std::shared_ptr<TaskHandle> handle, WorkFn work, DoneFn done, std::uint64_t gen, LatestOnly* latest)
    : ctx_(ctx), handle_(std::move(handle)), work_(std::move(work)), done_(std::move(done)), gen_(gen), latest_(latest) {
    setAutoDelete(true);
  }

  void run() override {
    if (handle_->cancelled()) return;
    Outcome<T> out;
    try {
      out = Outcome<T>::ok(work_(handle_->cancelled_flag()));
    } catch (const std::exception& e) {
      out = Outcome<T>::error(e.what());
    } catch (...) {
      out = Outcome<T>::error("unknown exception in async work");
    }
    // If cancelled during work, do not deliver.
    if (handle_->should_drop()) return;

    // Deliver on the UI thread.
    QPointer<QObject> ctx(ctx_);
    QPointer<LatestOnly> latest(latest_);
    auto handle = handle_;
    auto done = done_;
    // We use QTimer::singleShot(0) on the application's UI thread.
    QMetaObject::invokeMethod(qApp, [ctx, latest, gen = gen_, handle, done = std::move(done), out = std::move(out)]() mutable {
      // Skill rule 4: re-check cancellation on UI thread.
      if (handle->should_drop()) return;
      // Skill rule 5: latest-only delivery.
      if (latest && latest->generation() != gen) return;
      // Skill rule: ctx may have been deleted.
      if (!ctx) return;
      done(std::move(out));
    }, Qt::QueuedConnection);
  }

 private:
  QPointer<QObject>     ctx_;
  std::shared_ptr<TaskHandle> handle_;
  WorkFn                work_;
  DoneFn                done_;
  std::uint64_t         gen_;
  QPointer<LatestOnly>  latest_;
};

} // namespace

// === TaskHandle: expose the atomic flag ===

// (declared in async.hpp; we extend it here with a getter)
// We define cancelled_flag() via a free helper in the header for simplicity.

void LatestOnly::run_impl(QThreadPool* pool, std::uint64_t gen,
                          std::shared_ptr<TaskHandle> handle,
                          std::function<void(std::shared_ptr<std::atomic<bool>>)> work,
                          std::function<void()> done) {
  if (!pool) pool = QThreadPool::globalInstance();
  // Wrap `done` (no-arg) to call it after our Outcome<T> wrapper.
  // We need a T to instantiate RunTask<T>; since `done` takes no args, use void.
  // Use std::monostate as a void-like type.
  using T = std::monostate;
  auto work_wrapped = [w = std::move(work)](std::shared_ptr<std::atomic<bool>> f) -> T {
    w(f);
    return std::monostate{};
  };
  auto done_wrapped = [d = std::move(done)](Outcome<T>) { d(); };
  auto* task = new RunTask<T>(nullptr, std::move(handle), std::move(work_wrapped), std::move(done_wrapped), gen, this);
  pool->start(task);
}

} // namespace fin::app

// moc for LatestOnly / Debouncer (Q_OBJECT classes)
#include "async.moc"
