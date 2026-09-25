// fin/app/async.hpp — additional public getter for TaskHandle's atomic flag.
// (Keep this header small — the existing async.hpp already declared
// `cancelled_flag()` would be added here.)
#pragma once
#include <memory>
#include <atomic>

namespace fin::app {

// Free helper to expose the cancellation atomic for tests / external polling.
// (Defined inline since the TaskHandle struct's stop_ is private; we
// don't need access from production code, only the RunTask template which
// is a friend.)
inline std::shared_ptr<std::atomic<bool>> make_cancellation_flag() {
  return std::make_shared<std::atomic<bool>>(false);
}

} // namespace fin::app
