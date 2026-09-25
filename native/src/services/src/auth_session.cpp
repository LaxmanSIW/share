// fin/services/auth_session.cpp
#include "fin/services/auth_session.hpp"

namespace fin::services {

namespace {
std::atomic<std::int64_t> g_current_user_id{0};
}

UserId AuthSessionManager::current_user_id() noexcept {
  return UserId{g_current_user_id.load(std::memory_order_acquire)};
}

void AuthSessionManager::set_current_user_id(UserId id) noexcept {
  g_current_user_id.store(id.value, std::memory_order_release);
}

void AuthSessionManager::clear() noexcept {
  g_current_user_id.store(0, std::memory_order_release);
}

} // namespace fin::services
