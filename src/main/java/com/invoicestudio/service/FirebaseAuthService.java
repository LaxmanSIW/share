package com.invoicestudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.invoicestudio.model.UserSession;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * High-performance Firebase Auth client using Google Identity Toolkit REST API.
 * Handles Email/Password sign-in/up, password reset, token refresh, and Google Sign-In via browser loopback.
 */
public class FirebaseAuthService {

    public static final String API_KEY = "AIzaSyB9x6qly2iT2jr5-S7I9H34wlY9X9mZPa0";
    public static final String AUTH_DOMAIN = "pass-4za83.firebaseapp.com";
    public static final String PROJECT_ID = "securepass-4za83";
    public static final String STORAGE_BUCKET = "securepass-4za83.firebasestorage.app";
    public static final String MESSAGING_SENDER_ID = "321375647106";
    public static final String APP_ID = "1:321375647106:web:85a03304c9e74c3e41cb9b";

    private static final String IDENTITY_BASE_URL = "https://identitytoolkit.googleapis.com/v1/accounts";
    private static final String TOKEN_BASE_URL = "https://securetoken.googleapis.com/v1/token";

    private static FirebaseAuthService instance;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FirebaseAuthService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static synchronized FirebaseAuthService getInstance() {
        if (instance == null) {
            instance = new FirebaseAuthService();
        }
        return instance;
    }

    public static class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }
    }

    /**
     * Authenticate with email & password.
     */
    public UserSession signInWithEmail(String email, String password, boolean rememberMe) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("email", email != null ? email.trim() : "");
        req.put("password", password);
        req.put("returnSecureToken", true);

        String url = IDENTITY_BASE_URL + ":signInWithPassword?key=" + API_KEY;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(req), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new AuthException(parseErrorMessage(response.body()));
        }

        JsonNode root = objectMapper.readTree(response.body());
        String userId = root.path("localId").asText();
        String resEmail = root.path("email").asText(email);
        String displayName = root.path("displayName").asText("");
        String idToken = root.path("idToken").asText();
        String refreshToken = root.path("refreshToken").asText();
        long expiresInSec = root.path("expiresIn").asLong(3600);
        long expiresAtMillis = System.currentTimeMillis() + (expiresInSec * 1000L);

        UserSession session = new UserSession(userId, resEmail, displayName, idToken, refreshToken, expiresAtMillis, rememberMe);
        session.setCreatedAt(Instant.now().toString());
        return session;
    }

    /**
     * Create new user with email, password and optional display name.
     */
    public UserSession signUpWithEmail(String email, String password, String displayName, boolean rememberMe) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("email", email != null ? email.trim() : "");
        req.put("password", password);
        req.put("returnSecureToken", true);

        String url = IDENTITY_BASE_URL + ":signUp?key=" + API_KEY;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(req), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new AuthException(parseErrorMessage(response.body()));
        }

        JsonNode root = objectMapper.readTree(response.body());
        String userId = root.path("localId").asText();
        String resEmail = root.path("email").asText(email);
        String idToken = root.path("idToken").asText();
        String refreshToken = root.path("refreshToken").asText();
        long expiresInSec = root.path("expiresIn").asLong(3600);
        long expiresAtMillis = System.currentTimeMillis() + (expiresInSec * 1000L);

        // If display name provided, update user profile
        String finalDisplayName = displayName != null ? displayName.trim() : "";
        if (!finalDisplayName.isBlank()) {
            try {
                ObjectNode updateReq = objectMapper.createObjectNode();
                updateReq.put("idToken", idToken);
                updateReq.put("displayName", finalDisplayName);
                updateReq.put("returnSecureToken", true);

                HttpRequest updateRequest = HttpRequest.newBuilder()
                        .uri(URI.create(IDENTITY_BASE_URL + ":update?key=" + API_KEY))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(updateReq), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> updateRes = httpClient.send(updateRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (updateRes.statusCode() == 200) {
                    JsonNode updateNode = objectMapper.readTree(updateRes.body());
                    if (updateNode.hasNonNull("idToken")) {
                        idToken = updateNode.get("idToken").asText();
                    }
                }
            } catch (Exception ignored) {}
        }

        UserSession session = new UserSession(userId, resEmail, finalDisplayName, idToken, refreshToken, expiresAtMillis, rememberMe);
        session.setCreatedAt(Instant.now().toString());
        return session;
    }

    /**
     * Send password reset email via Firebase Identity Toolkit.
     */
    public void sendPasswordReset(String email) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("requestType", "PASSWORD_RESET");
        req.put("email", email != null ? email.trim() : "");

        String url = IDENTITY_BASE_URL + ":sendOobCode?key=" + API_KEY;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(req), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new AuthException(parseErrorMessage(response.body()));
        }
    }

    /**
     * Update password for an authenticated user using their idToken.
     */
    public UserSession updatePassword(String idToken, String newPassword) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("idToken", idToken);
        req.put("password", newPassword);
        req.put("returnSecureToken", true);

        String url = IDENTITY_BASE_URL + ":update?key=" + API_KEY;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(req), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new AuthException(parseErrorMessage(response.body()));
        }

        JsonNode root = objectMapper.readTree(response.body());
        String userId = root.path("localId").asText();
        String email = root.path("email").asText();
        String displayName = root.path("displayName").asText("");
        String newIdToken = root.path("idToken").asText(idToken);
        String refreshToken = root.path("refreshToken").asText();
        long expiresInSec = root.path("expiresIn").asLong(3600);
        long expiresAtMillis = System.currentTimeMillis() + (expiresInSec * 1000L);

        UserSession session = new UserSession(userId, email, displayName, newIdToken, refreshToken, expiresAtMillis, true);
        session.setCreatedAt(Instant.now().toString());
        return session;
    }

    /**
     * Exchange a refresh token for a fresh idToken.
     */
    public UserSession refreshSession(UserSession session) throws Exception {
        if (session == null || session.getRefreshToken() == null || session.getRefreshToken().isBlank()) {
            throw new AuthException("No refresh token available");
        }

        String form = "grant_type=refresh_token&refresh_token=" +
                URLEncoder.encode(session.getRefreshToken(), StandardCharsets.UTF_8);

        String url = TOKEN_BASE_URL + "?key=" + API_KEY;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new AuthException("Failed to refresh authentication session: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String idToken = root.path("id_token").asText();
        String refreshToken = root.path("refresh_token").asText(session.getRefreshToken());
        long expiresInSec = root.path("expires_in").asLong(3600);

        session.setIdToken(idToken);
        session.setRefreshToken(refreshToken);
        session.setExpiresAtMillis(System.currentTimeMillis() + (expiresInSec * 1000L));
        return session;
    }

    /**
     * Desktop Google Sign-In with loopback HTTP listener.
     */
    public void signInWithGoogle(Consumer<UserSession> onSuccess, Consumer<String> onError) {
        Executors.newSingleThreadExecutor().submit(() -> {
            HttpServer server = null;
            try {
                // Pick port 8085 or next available port
                int assignedPort = 8085;
                try {
                    server = HttpServer.create(new InetSocketAddress("127.0.0.1", assignedPort), 0);
                } catch (Exception e) {
                    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                    assignedPort = server.getAddress().getPort();
                }
                final int port = assignedPort;
                final HttpServer finalServer = server;
                final AtomicBoolean handled = new AtomicBoolean(false);

                // Handler for root page serving Firebase OAuth popup
                finalServer.createContext("/", new HttpHandler() {
                    @Override
                    public void handle(HttpExchange exchange) throws IOException {
                        String method = exchange.getRequestMethod();
                        if ("POST".equalsIgnoreCase(method) && "/callback".equals(exchange.getRequestURI().getPath())) {
                            // Received user token callback
                            try {
                                byte[] body = exchange.getRequestBody().readAllBytes();
                                JsonNode json = objectMapper.readTree(body);
                                String uid = json.path("uid").asText();
                                String email = json.path("email").asText();
                                String name = json.path("displayName").asText("");
                                String idToken = json.path("idToken").asText();
                                String refreshToken = json.path("refreshToken").asText();

                                UserSession session = new UserSession(uid, email, name, idToken, refreshToken,
                                        System.currentTimeMillis() + 3600_000L, true);
                                session.setCreatedAt(Instant.now().toString());

                                byte[] resp = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                                exchange.getResponseHeaders().set("Content-Type", "application/json");
                                exchange.sendResponseHeaders(200, resp.length);
                                try (OutputStream os = exchange.getResponseBody()) {
                                    os.write(resp);
                                }

                                if (handled.compareAndSet(false, true)) {
                                    Platform.runLater(() -> onSuccess.accept(session));
                                    Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                                        try { finalServer.stop(0); } catch (Exception ignored) {}
                                    }, 1, java.util.concurrent.TimeUnit.SECONDS);
                                }
                            } catch (Exception ex) {
                                byte[] err = "{\"error\":\"invalid\"}".getBytes(StandardCharsets.UTF_8);
                                exchange.sendResponseHeaders(400, err.length);
                                try (OutputStream os = exchange.getResponseBody()) { os.write(err); }
                            }
                            return;
                        }

                        // Serve HTML login landing page
                        String html = buildGoogleAuthHtml(port);
                        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                    }
                });

                finalServer.setExecutor(Executors.newCachedThreadPool());
                finalServer.start();

                String authUrl = "http://127.0.0.1:" + port + "/";
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(authUrl));
                } else {
                    Platform.runLater(() -> onError.accept("Desktop browser not supported. Please open: " + authUrl));
                }

                // Safety timeout after 3 minutes
                final HttpServer cancelServer = finalServer;
                Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                    if (handled.compareAndSet(false, true)) {
                        try { cancelServer.stop(0); } catch (Exception ignored) {}
                        Platform.runLater(() -> onError.accept("Google sign-in timed out. Please try again."));
                    }
                }, 3, java.util.concurrent.TimeUnit.MINUTES);

            } catch (Exception e) {
                if (server != null) {
                    try { server.stop(0); } catch (Exception ignored) {}
                }
                Platform.runLater(() -> onError.accept("Unable to start Google authentication: " + e.getMessage()));
            }
        });
    }

    private String buildGoogleAuthHtml(int port) {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>InvoiceStudio - Google Sign In</title>\n" +
                "  <script src=\"https://www.gstatic.com/firebasejs/10.8.0/firebase-app-compat.js\"></script>\n" +
                "  <script src=\"https://www.gstatic.com/firebasejs/10.8.0/firebase-auth-compat.js\"></script>\n" +
                "  <style>\n" +
                "    body {\n" +
                "      margin: 0; padding: 0; min-height: 100vh;\n" +
                "      display: flex; align-items: center; justify-content: center;\n" +
                "      background-color: #0B0E13;\n" +
                "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n" +
                "      color: #E2E8F0;\n" +
                "    }\n" +
                "    .card {\n" +
                "      background: #151A23;\n" +
                "      border: 1px solid #232B3B;\n" +
                "      border-radius: 16px;\n" +
                "      padding: 40px;\n" +
                "      width: 100%;\n" +
                "      max-width: 420px;\n" +
                "      box-shadow: 0 20px 40px rgba(0,0,0,0.6);\n" +
                "      text-align: center;\n" +
                "    }\n" +
                "    .logo-badge {\n" +
                "      width: 52px; height: 52px; border-radius: 12px; margin: 0 auto 20px;\n" +
                "      background: linear-gradient(135deg, #D4AF37, #AA820A);\n" +
                "      display: flex; align-items: center; justify-content: center;\n" +
                "      font-weight: 800; font-size: 24px; color: #0B0E13;\n" +
                "    }\n" +
                "    h2 { margin: 0 0 8px; font-size: 22px; font-weight: 700; color: #F8FAFC; }\n" +
                "    p { margin: 0 0 24px; font-size: 14px; color: #94A3B8; }\n" +
                "    .btn-google {\n" +
                "      display: inline-flex; align-items: center; justify-content: center; gap: 12px;\n" +
                "      width: 100%; padding: 12px 18px; border-radius: 10px;\n" +
                "      background: #1C2433; border: 1px solid #2A364D;\n" +
                "      color: #F8FAFC; font-size: 15px; font-weight: 600;\n" +
                "      cursor: pointer; transition: all 0.2s ease;\n" +
                "    }\n" +
                "    .btn-google:hover { background: #232D40; border-color: #D4AF37; }\n" +
                "    .status-msg { margin-top: 20px; font-size: 14px; min-height: 24px; }\n" +
                "    .success { color: #10B981; font-weight: 600; }\n" +
                "    .error { color: #EF4444; font-weight: 600; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"card\">\n" +
                "    <div class=\"logo-badge\">IS</div>\n" +
                "    <h2>InvoiceStudio Authentication</h2>\n" +
                "    <p>Sign in with Google to sync your user profile to desktop.</p>\n" +
                "    <button id=\"googleBtn\" class=\"btn-google\" onclick=\"handleGoogleLogin()\">\n" +
                "      <svg width=\"18\" height=\"18\" viewBox=\"0 0 18 18\"><path fill=\"#4285F4\" d=\"M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.874 2.684-6.616z\"/><path fill=\"#34A853\" d=\"M9 18c2.43 0 4.467-.806 5.956-2.184l-2.908-2.258c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332C2.438 15.983 5.482 18 9 18z\"/><path fill=\"#FBBC05\" d=\"M3.964 10.707c-.18-.54-.282-1.117-.282-1.707s.102-1.167.282-1.707V4.961H.957C.347 6.173 0 7.548 0 9s.347 2.827.957 4.039l3.007-2.332z\"/><path fill=\"#EA4335\" d=\"M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0 5.482 0 2.438 2.017.957 4.961L3.964 7.293C4.672 5.166 6.656 3.58 9 3.58z\"/></svg>\n" +
                "      Sign in with Google\n" +
                "    </button>\n" +
                "    <div id=\"status\" class=\"status-msg\"></div>\n" +
                "  </div>\n" +
                "  <script>\n" +
                "    const firebaseConfig = {\n" +
                "      apiKey: \"" + API_KEY + "\",\n" +
                "      authDomain: \"" + AUTH_DOMAIN + "\",\n" +
                "      projectId: \"" + PROJECT_ID + "\",\n" +
                "      storageBucket: \"" + STORAGE_BUCKET + "\",\n" +
                "      messagingSenderId: \"" + MESSAGING_SENDER_ID + "\",\n" +
                "      appId: \"" + APP_ID + "\"\n" +
                "    };\n" +
                "    firebase.initializeApp(firebaseConfig);\n" +
                "    const auth = firebase.auth();\n" +
                "    const provider = new firebase.auth.GoogleAuthProvider();\n" +
                "    provider.setCustomParameters({ prompt: 'select_account' });\n" +
                "    function handleGoogleLogin() {\n" +
                "      const status = document.getElementById('status');\n" +
                "      status.className = 'status-msg';\n" +
                "      status.innerText = 'Connecting with Google...';\n" +
                "      auth.signInWithPopup(provider).then(async (result) => {\n" +
                "        const user = result.user;\n" +
                "        const idToken = await user.getIdToken();\n" +
                "        const payload = {\n" +
                "          uid: user.uid,\n" +
                "          email: user.email,\n" +
                "          displayName: user.displayName || '',\n" +
                "          idToken: idToken,\n" +
                "          refreshToken: user.refreshToken || ''\n" +
                "        };\n" +
                "        status.innerText = 'Saving session in InvoiceStudio...';\n" +
                "        fetch('http://127.0.0.1:" + port + "/callback', {\n" +
                "          method: 'POST',\n" +
                "          headers: { 'Content-Type': 'application/json' },\n" +
                "          body: JSON.stringify(payload)\n" +
                "        }).then(res => {\n" +
                "          if (res.ok) {\n" +
                "            status.className = 'status-msg success';\n" +
                "            status.innerText = 'Authentication successful! You can return to InvoiceStudio now.';\n" +
                "            document.getElementById('googleBtn').style.display = 'none';\n" +
                "          } else {\n" +
                "            status.className = 'status-msg error';\n" +
                "            status.innerText = 'Failed to report session to desktop app.';\n" +
                "          }\n" +
                "        }).catch(err => {\n" +
                "          status.className = 'status-msg error';\n" +
                "          status.innerText = 'Desktop callback error: ' + err.message;\n" +
                "        });\n" +
                "      }).catch((err) => {\n" +
                "        status.className = 'status-msg error';\n" +
                "        status.innerText = err.message || 'Google Sign-In failed.';\n" +
                "      });\n" +
                "    }\n" +
                "    window.onload = function() { handleGoogleLogin(); };\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>\n";
    }

    private String parseErrorMessage(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            if (node.hasNonNull("error")) {
                JsonNode errNode = node.get("error");
                String message = errNode.path("message").asText();
                if (message.contains("EMAIL_NOT_FOUND")) {
                    return "No account found with this email address.";
                } else if (message.contains("INVALID_PASSWORD") || message.contains("INVALID_LOGIN_CREDENTIALS")) {
                    return "Incorrect email or password. Please try again.";
                } else if (message.contains("EMAIL_EXISTS")) {
                    return "An account with this email address already exists. Please sign in.";
                } else if (message.contains("WEAK_PASSWORD")) {
                    return "Password must be at least 6 characters.";
                } else if (message.contains("USER_DISABLED")) {
                    return "This user account has been disabled.";
                } else if (message.contains("TOO_MANY_ATTEMPTS_TRY_LATER")) {
                    return "Too many failed attempts. Please try again in a few minutes.";
                } else if (message.contains("INVALID_EMAIL")) {
                    return "Please enter a valid email address.";
                } else if (message.contains("MISSING_PASSWORD")) {
                    return "Please enter a password.";
                } else if (!message.isBlank()) {
                    return message.replace('_', ' ');
                }
            }
        } catch (Exception ignored) {}
        return "Authentication request failed. Please check your internet connection and try again.";
    }
}
