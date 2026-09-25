// fin/services/firebase_auth.hpp — FirebaseAuthService (port of Java FirebaseAuthService.java)
//
// Firebase Auth REST API client. Matches the Java original:
//   - signUpWithEmailAndPassword + signInWithEmailAndPassword + refresh token
//   - Google sign-in via the OAuth 2.0 device flow
//   - id_token JWT verification (parse the payload, check exp/iat)
//
// Skill rule (responsive-ui §1): NEVER call this on the UI thread. Auth calls
// go through fin::app::Executors::net() and the result is delivered on the
// UI thread via run_async. This header declares the API; the .cpp uses
// QNetworkAccessManager internally.
#pragma once
#include "fin/model/buyer_supplier.hpp"  // for UserSession

#include <chrono>
#include <functional>
#include <string>
#include <string_view>

namespace fin::services {

struct FirebaseAuthConfig {
  std::string api_key;            // Firebase Web API key
  std::string project_id;
};

struct AuthResult {
  bool ok{false};
  std::string error;
  fin::model::UserSession session;
};

class FirebaseAuthService {
 public:
  /// Initialise with the project's API key (loaded from settings.json).
  static void init(FirebaseAuthConfig config);

  /// Sign in with email + password. Calls `done` on the UI thread.
  /// `remember_me` controls whether the session is persisted to disk.
  static void sign_in_with_email_password(
      std::string_view email,
      std::string_view password,
      bool remember_me,
      std::function<void(AuthResult)> done);

  /// Sign up with email + password.
  static void sign_up_with_email_password(
      std::string_view email,
      std::string_view password,
      std::function<void(AuthResult)> done);

  /// Refresh an expired session using a stored refresh token.
  static void refresh_session(
      const fin::model::UserSession& session,
      std::function<void(AuthResult)> done);

  /// Sign out (revoke refresh token server-side, clear local session).
  static void sign_out(std::function<void(bool)> done);

  /// Decode a JWT id_token locally (no network). Returns the parsed payload
  /// as a JSON string, or empty on failure. Used to read user_id / email /
  /// display_name without a round-trip.
  static std::string decode_id_token(std::string_view id_token);
};

} // namespace fin::services
