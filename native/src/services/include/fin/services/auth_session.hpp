// fin/services/auth_session.hpp — Current user ID provider (port of Java AuthSessionManager)
//
// The Java original is a static singleton. In C++ we keep the same shape (a
// process-wide atomic) but route through AuthSessionManager::current_user_id()
// so tests can inject a fake.
//
// Skill rule: the data layer must NEVER make UI assumptions; user ID is a
// thread-safe primitive, not a Qt property.
#pragma once
#include "fin/ids.hpp"
#include <atomic>
#include <string_view>

namespace fin::services {

class AuthSessionManager {
 public:
  /// Returns the currently active user ID, or an empty string if no user is
  /// signed in. DAOs use this to partition their data per user.
  static UserId current_user_id() noexcept;

  /// Set the current user ID (called by AuthView after successful login).
  static void set_current_user_id(UserId id) noexcept;

  /// Clear (called on logout). Subsequent DAO calls return empty data.
  static void clear() noexcept;
};

} // namespace fin::services
