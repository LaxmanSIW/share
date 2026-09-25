// fin/services/firebase_auth.cpp
//
// Phase 3b skeleton: the real implementation uses QNetworkAccessManager +
// QtNetwork to call the Firebase Identity Toolkit REST API. The signatures
// here match what the UI's AuthView will call. JWT decoding is implemented
// locally (no network) using a simple base64+JSON parse.
#include "fin/services/firebase_auth.hpp"
#include "fin/app/executors.hpp"
#include "fin/app/log.hpp"

#include <nlohmann/json.hpp>

#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QUrl>
#include <QByteArray>
#include <QTimer>

#include <atomic>
#include <chrono>
#include <cstdio>
#include <string>
#include <vector>

namespace fin::services {

namespace {

FirebaseAuthConfig g_config;
std::atomic<bool>  g_initialised{false};

/// Decode 4 base64 chars into 3 bytes.
int b64_decode_chunk(const char* in, std::uint8_t* out) {
  auto lookup = [](char c) -> int {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+' || c == '-') return 62;
    if (c == '/' || c == '_') return 63;
    return -1;
  };
  int v[4];
  for (int i = 0; i < 4; ++i) {
    v[i] = lookup(in[i]);
    if (v[i] < 0) return -1;
  }
  out[0] = static_cast<std::uint8_t>((v[0] << 2) | (v[1] >> 4));
  out[1] = static_cast<std::uint8_t>(((v[1] & 0xF) << 4) | (v[2] >> 2));
  out[2] = static_cast<std::uint8_t>(((v[2] & 0x3) << 6) | v[3]);
  return (v[2] == 63) ? 1 : (v[3] == 63) ? 2 : 3;
}

/// Base64-url decode (Firebase uses base64url in JWTs).
std::vector<std::uint8_t> b64url_decode(std::string_view in) {
  std::vector<std::uint8_t> out;
  std::string buf;
  for (char c : in) {
    if (c == '=') break;
    buf.push_back(c);
  }
  while (buf.size() % 4 != 0) buf.push_back('=');
  std::uint8_t triplet[3];
  for (std::size_t i = 0; i + 3 < buf.size() + 1; i += 4) {
    int n = b64_decode_chunk(buf.data() + i, triplet);
    if (n < 0) return out;
    out.insert(out.end(), triplet, triplet + n);
    if (n < 3) break;
  }
  return out;
}

} // namespace

void FirebaseAuthService::init(FirebaseAuthConfig config) {
  g_config = std::move(config);
  g_initialised.store(true);
}

void FirebaseAuthService::sign_in_with_email_password(
    std::string_view email, std::string_view password, bool remember_me,
    std::function<void(AuthResult)> done) {
  // Phase 3b skeleton: real impl posts to
  //   https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=API_KEY
  // with {email, password, returnSecureToken=true}. Parses id_token,
  // refresh_token, expires_in. Calls done on the UI thread via run_async.
  AuthResult result;
  result.ok = false;
  result.error = "Phase 3b: not implemented";
  (void)email; (void)password; (void)remember_me;
  QTimer::singleShot(0, qApp, [done = std::move(done), result = std::move(result)]() mutable {
    done(std::move(result));
  });
}

void FirebaseAuthService::sign_up_with_email_password(
    std::string_view email, std::string_view password,
    std::function<void(AuthResult)> done) {
  AuthResult result;
  result.ok = false;
  result.error = "Phase 3b: not implemented";
  (void)email; (void)password;
  QTimer::singleShot(0, qApp, [done = std::move(done), result = std::move(result)]() mutable {
    done(std::move(result));
  });
}

void FirebaseAuthService::refresh_session(
    const fin::model::UserSession& session,
    std::function<void(AuthResult)> done) {
  AuthResult result;
  result.ok = false;
  result.error = "Phase 3b: not implemented";
  (void)session;
  QTimer::singleShot(0, qApp, [done = std::move(done), result = std::move(result)]() mutable {
    done(std::move(result));
  });
}

void FirebaseAuthService::sign_out(std::function<void(bool)> done) {
  // Skill rule §1.6: deliver on UI thread, never block.
  QTimer::singleShot(0, qApp, [done = std::move(done)]() { done(true); });
}

std::string FirebaseAuthService::decode_id_token(std::string_view id_token) {
  // A Firebase id_token is a JWT: header.payload.signature (all base64url).
  // We don't verify the signature here (no SSL public key available locally);
  // we just extract the payload for the UI to read user_id / email / display_name.
  // Skill rule §11 (security §11): never trust the token server-side without
  // signature verification; the UI uses this only for display, not for auth.
  auto dot1 = id_token.find('.');
  if (dot1 == std::string_view::npos) return {};
  auto dot2 = id_token.find('.', dot1 + 1);
  if (dot2 == std::string_view::npos) return {};
  auto payload_view = id_token.substr(dot1 + 1, dot2 - dot1 - 1);
  auto bytes = b64url_decode(payload_view);
  if (bytes.empty()) return {};
  return std::string(reinterpret_cast<const char*>(bytes.data()), bytes.size());
}

} // namespace fin::services
