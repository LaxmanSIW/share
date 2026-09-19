# Chapter 10 — Signing In: Authentication

> **Part 5 of InvoiceStudio: Zero to Finished Product**
> Files covered in full this chapter: `service/FirebaseAuthService.java`,
> `service/AuthSessionManager.java`, `model/UserSession.java`, `db/AuthDao.java`,
> `ui/auth/AuthView.java` (688 lines), `ui/auth/GoogleSignInButton.java`,
> `ui/auth/LogoutDialog.java`, `ui/auth/PasswordFieldWithToggle.java`,
> `ui/auth/PasswordStrengthMeter.java` — all 9 read from the repository.
> Goal at the end: you understand how a desktop JavaFX app does real cloud
> authentication over plain HTTPS REST calls, how the session survives restarts, how
> Google sign-in works through a localhost loopback browser dance, and how data stays
> partitioned per user.

---

## 1. Chapter goal

By the end of this chapter you will have built the authentication layer: a Firebase
Identity Toolkit REST client (email/password sign-in & sign-up, password reset,
password change, token refresh, browser-based Google sign-in), an ambient session
holder every layer can ask "who is logged in?", a persisted single-row session store
in SQLite, and the six-screen auth UI with premium widgets.

## 2. Story intro

Think of an **airport**. Your passport (`idToken`) proves who you are, but it expires.
The immigration desk accepts it only while valid; when it expires, you use your
long-term visa (`refreshToken`) at a special counter to get a new passport without
re-applying for citizenship (re-entering your password). The airport's manifest
(`auth_session` table) remembers whether you asked to be fast-tracked next visit
(`rememberMe`).

That is exactly Firebase's model, and this app talks to it with **no Firebase SDK** —
just `java.net.http.HttpClient` and JSON. Why? A fat-jar desktop app benefits from
zero extra dependencies: the REST endpoints (`identitytoolkit.googleapis.com/v1/accounts:*`,
`securetoken.googleapis.com/v1/token`) are stable, documented, and need only an API key.

The **Google sign-in** story is different: browsers can do Google's OAuth popup;
desktop apps cannot embed one safely. The trick this app uses is the classic
**loopback flow**: the desktop starts a tiny HTTP server on `127.0.0.1`, opens the
user's real browser to a local page that runs the Firebase JS SDK, and the browser
POSTs the resulting tokens back to the localhost server. The desktop receives the
session like a webhook.

## 3. Concepts first

- **Token** — a signed string proving identity. Firebase issues an `idToken`
  (short-lived, ~1 hour) and a `refreshToken` (long-lived, used only to mint new
  idTokens).
- **REST API** — talking HTTP with JSON bodies to named endpoints. `POST` = send data,
  `?key=API_KEY` = your Firebase project credential (an *web API key*, public by
  design — it identifies the project, it does not by itself grant access).
- **Loopback redirect flow** — OAuth for desktop apps: listen on `127.0.0.1:<port>`,
  open the system browser, receive credentials via a local HTTP request.
- **`HttpExchange` / `HttpServer`** — JDK's built-in com.sun HTTP server; here used
  only for the localhost callback page.
- **Ambient context** — a static holder (`AuthSessionManager`) that any layer can
  query without constructor plumbing; a pragmatic pattern for cross-cutting identity,
  with the trade-off of hidden global state.
- **Singleton with listener bus** — `CopyOnWriteArrayList<Consumer<UserSession>>`
  notifies interested parties (the data hub, the user pill) whenever the session
  changes.
- **30-second expiry grace** — `isExpired()` counts a token expiring within 30 s as
  already expired, so a request never starts with a token that dies mid-flight.

## 4. Files in this chapter

| File | Lines | Role |
|---|---|---|
| `service/FirebaseAuthService.java` | ~520 | REST client for Identity Toolkit + token refresh + Google loopback |
| `model/UserSession.java` | ~70 | Session snapshot: ids, tokens, expiry, rememberMe |
| `service/AuthSessionManager.java` | ~70 | Static current-session holder + listener bus |
| `db/AuthDao.java` | ~110 | Single-row session persistence in SQLite |
| `ui/auth/AuthView.java` | 688 | The 6-state auth screen (StackPane card) |
| `ui/auth/GoogleSignInButton.java` | ~55 | Branded button with official 4-colour G |
| `ui/auth/PasswordFieldWithToggle.java` | ~120 | Password box + eye toggle |
| `ui/auth/PasswordStrengthMeter.java` | ~160 | 4-bar meter + live criteria checklist |
| `ui/auth/LogoutDialog.java` | ~85 | Logout confirmation overlay |

## 5. Step-by-step build

### 5.1 `FirebaseAuthService.java` — the REST client

**Constants and lifecycle.** The class opens with the Firebase web config as public
constants (`API_KEY`, `AUTH_DOMAIN`, `PROJECT_ID`, `STORAGE_BUCKET`,
`MESSAGING_SENDER_ID`, `APP_ID`) and the two base URLs:

```java
private static final String IDENTITY_BASE_URL = "https://identitytoolkit.googleapis.com/v1/accounts";
private static final String TOKEN_BASE_URL    = "https://securetoken.googleapis.com/v1/token";
```

`ISSUE:` these are *public* web API keys — fine for Firebase (they're meant to ship in
browsers), but worth knowing they are visible to anyone who decompiles the jar. Their
`ISSUE:` sibling: shipping them in source means the project id is public knowledge;
rotate them via the Firebase console, never by hiding them.

Two shared, daemon-threaded pools serve the Google flow (`GOOGLE_HTTP_POOL` for the
loopback server, `GOOGLE_STOP_SCHEDULER` for delayed stops and the 3-minute timeout) —
the comments invoke the project's "no inline Executors" rule (skill 1.4). The service
is a lazy singleton (`getInstance()`), wrapping an `HttpClient` with a 15 s connect
timeout, and defines `AuthException` for user-friendly failures.

**`signInWithEmail(email, password, rememberMe)`** — the canonical request pattern all
methods share:

```java
ObjectNode req = objectMapper.createObjectNode();
req.put("email", email != null ? email.trim() : "");
req.put("password", password);
req.put("returnSecureToken", true);
// POST to IDENTITY_BASE_URL + ":signInWithPassword?key=" + API_KEY
// 20 s timeout, JSON content type
// statusCode != 200  →  throw new AuthException(parseErrorMessage(body))
// else parse: localId, email, displayName, idToken, refreshToken, expiresIn (default 3600 s)
UserSession session = new UserSession(userId, resEmail, displayName, idToken,
        refreshToken, System.currentTimeMillis() + expiresInSec * 1000L, rememberMe);
```

**`signUpWithEmail(...)`** — same pattern against `:signUp`, then a *second* call to
`:update` to set the displayName when one was provided (its new idToken is adopted;
failure is swallowed with a log because a missing display name must never block
account creation).

**`sendPasswordReset(email)`** — `:sendOobCode` with `requestType: "PASSWORD_RESET"`;
no session is returned, just success/failure.

**`updatePassword(idToken, newPassword)`** — `:update` with the current idToken; the
response carries fresh tokens, which become the new session.

**`refreshSession(session)`** — the token-refresh counter. Note the *form-encoded*
body (`grant_type=refresh_token&refresh_token=...`, `x-www-form-urlencoded`) — this
endpoint, unlike the others, is form-based. It mutates and returns the same session
with a new idToken, possibly rotated refreshToken, and new expiry.

**`signInWithGoogle(onSuccess, onError)`** — the loopback dance, run on
`AppExecutors.io()`:

1. Bind `127.0.0.1:8085`; if taken, port 0 (OS picks free).
2. Register `/`: any GET (or non-callback POST) returns `buildGoogleAuthHtml(port)` —
   a styled dark-gold page loading the Firebase **JS compat SDK** (v10.8.0) with the
   same config, whose `handleGoogleLogin()` runs `auth.signInWithPopup(provider)`
   automatically on load, gets `user.getIdToken()`, and `fetch`es
   `POST http://127.0.0.1:<port>/callback` with `{uid, email, displayName, idToken,
   refreshToken}`.
3. The `/callback` POST handler parses that JSON, builds a `UserSession`
   (1-hour expiry, `rememberMe=true`), replies `{"status":"ok"}`, and — guarded by
   `handled.compareAndSet(false, true)` so it fires exactly once — delivers
   `onSuccess` on the FX thread and schedules the server's stop 1 s later.
4. Opens the page via `Desktop.browse` (or surfaces the URL as an error for
   unsupported desktops).
5. A 3-minute timeout on the scheduler stops the server and reports "timed out" if
   nothing was handled.

**`parseErrorMessage(body)`** — turns Firebase error codes into human sentences:
`EMAIL_NOT_FOUND`, `INVALID_PASSWORD`/`INVALID_LOGIN_CREDENTIALS`, `EMAIL_EXISTS`,
`WEAK_PASSWORD`, `USER_DISABLED`, `TOO_MANY_ATTEMPTS_TRY_LATER`, `INVALID_EMAIL`,
`MISSING_PASSWORD`; unknown messages get underscores→spaces; anything unparseable
falls back to the connectivity hint.

### 5.2 `UserSession.java` — the passport

```java
private String userId, email, displayName, idToken, refreshToken;
private long expiresAtMillis;
private boolean rememberMe;
private String createdAt;

public boolean isExpired() {
    // Return true if expired or within 30 seconds of expiry
    return System.currentTimeMillis() >= (expiresAtMillis - 30_000);
}
```

Plain Jackson-friendly bean (`@JsonIgnoreProperties(ignoreUnknown = true)` — package
convention). The only logic is the 30-second expiry grace: a token 29 s from death is
treated as dead, so no request begins with a token that expires mid-call.

### 5.3 `AuthSessionManager.java` — ambient identity

```java
private static volatile UserSession activeSession;
private static final CopyOnWriteArrayList<Consumer<UserSession>> listeners = ...;

public static void setActiveSession(UserSession session) {
    activeSession = session;
    for (Consumer<UserSession> l : listeners) { try { l.accept(session); } catch (Exception e) { AppLog.error(e); } }
}
public static String getCurrentUserId()      { /* "" when logged out */ }
public static String getCurrentUserEmail()   { /* "" when logged out */ }
public static String getCurrentUserDisplayName() { /* name → email prefix → "User" */ }
public static boolean isLoggedIn()           { return s != null && !s.isExpired(); }
public static void clear()                   { setActiveSession(null); }
```

`volatile` because the session is written on FX threads and read on DB threads; the
listener bus (`CopyOnWriteArrayList`, per Chapter 8's pattern) is what
`DataManager.onUserSwitched()` subscribes to — the moment the session changes, every
cache drops, which is the enforcement point for per-user data isolation. Every DAO
calls `getCurrentUserId()` on each query (Chapter 4) — this manager is *the* source.

### 5.4 `AuthDao.java` — one row of truth

```java
// saveSession: DELETE FROM auth_session;  INSERT INTO auth_session (id, ...) VALUES (1, ...)
// getActiveSession: SELECT ... FROM auth_session WHERE id = 1
// updateTokens(user-id variant): UPDATE ... WHERE user_id = ?
// updateTokens(row variant):     UPDATE ... WHERE id = 1
// clearSession: DELETE FROM auth_session
```

The schema trick: the table has a **single row, id = 1**. Saving deletes everything
first — one user per device, no history of stale tokens. `rememberMe` is an int (1/0)
in SQLite. All methods swallow-and-log: a failed token update degrades to "you'll sign
in again next launch", never a crash. Note the two `updateTokens` overloads — the
studio boot path (Ch. 9) uses the id=1 variant after a refresh.

### 5.5 `AuthView.java` — six screens, one card

The view is a `StackPane` with a transparent-background `ScrollPane` wrapping a
centred 440 px `auth-card` (the doc comment: centring wrapper "to ensure the card is
dead-center on all resolutions").

**State machine.** An `AuthState` enum (`SIGN_IN, SIGN_UP, FORGOT_PASSWORD,
CHECK_EMAIL, SET_NEW_PASSWORD, LOGGED_OUT`) drives `renderState`, which clears the
card and rebuilds it for the state. `setState` adds polish: fade the current card to
0.2 opacity over 180 ms, swap content in `setOnFinished`, fade back over 220 ms.

**SCREEN 1 — SIGN IN** (`renderSignIn`): logo badge ("IS"), title/subtitle, email box
(see `createInputBox` below), `PasswordFieldWithToggle`, a `BorderPane` options row
(remember-me checkbox, default *checked* | "Forgot password?" link → `FORGOT_PASSWORD`),
primary button, error banner (hidden + unmanaged until needed), divider
("Or continue with"), `GoogleSignInButton`, and the sign-up footer link.

The submit handler shows the async discipline used by every screen:

```java
signInBtn.setDisable(true); signInBtn.setText("Signing In...");
AppExecutors.io().submit(() -> {
    try {
        UserSession session = authService.signInWithEmail(email, password, rememberMe.isSelected());
        handleSuccessfulLogin(session, rememberMe.isSelected());
    } catch (Exception ex) {
        Platform.runLater(() -> { /* re-enable button, showBanner(ex.getMessage()) */ });
    }
});
```

Network on the IO pool; every UI mutation back on the FX thread; button disabled with
a progress label so double-clicks can't fire two requests.

**SCREEN 2 — SIGN UP**: name/email/password fields plus `PasswordStrengthMeter`
bound via `strengthMeter.bindToPassword(passField.textProperty())`, a confirm field,
and client-side validation (non-empty, passwords match, ≥ 6 chars) *before* the
network call — fail fast with a banner instead of a round-trip.

**SCREEN 3 — FORGOT PASSWORD**: email + "Reset Password" → `sendPasswordReset` →
`setState(CHECK_EMAIL)`, storing `lastResetEmail` for the next screen's personalised
message.

**SCREEN 4 — CHECK EMAIL**: a green envelope badge, the sent-to message, an
"Open Email App" button that tries `Desktop.mail()` then falls back to browsing Gmail,
a spam-filter hint, and the back link.

**SCREEN 5 — SET NEW PASSWORD**: new password + strength meter + confirm; requires ≥ 8
characters (stricter than sign-up's 6 — `ISSUE:` the two minimums disagree; the meter
says 8 too, so sign-up's 6 is the outlier). Submission requires an active session's
idToken (`updatePassword`) — otherwise it bounces back to sign-in. This screen is
reached from Settings (change password) more than from the reset flow, because the
reset link completes in the browser.

**SCREEN 6 — LOGGED OUT**: green check badge, reassurance copy ("All your invoice
data is safely saved on this computer"), "Sign In Again".

**Helpers**: `handleSuccessfulLogin(session, rememberMe)` runs on the FX thread —
persists the session via `DataManager.get().auth().saveSession(...)` when remember-me
is on, **clears** the stored row when it's off (a previous user's tokens must not
linger), sets the ambient session (listener bus fires → caches drop), and invokes the
success callback that hands control back to the shell (Ch. 9's
`onAuthenticationSuccess`). `createInputBox(iconSvg, placeholder)` builds the
icon-in-a-box text field and stows the `TextField` in `box.getUserData()` so callers
can retrieve it — a slightly unusual but compact pattern. `showBanner` swaps between
`auth-error-banner` / `auth-success-banner` classes and toggles
`visible`+`managed` together (so a hidden banner occupies no layout space).

### 5.6 The four widgets

- **`GoogleSignInButton`** — a `Button` subclass with the official Google "G" drawn as
  four `SVGPath`s in brand colours (#4285F4/#34A853/#FBBC05/#EA4335) inside an HBox
  with "Continue with Google". Brand-correct pixels, zero image assets.
- **`PasswordFieldWithToggle`** — an HBox containing a lock icon, a `PasswordField`,
  a hidden `TextField`, and an eye `Button`. The two text controls are bound
  bidirectionally; toggling swaps which one is `managed`+`visible`, moves focus and
  caret to the end, and recolours the eye gold. Focus listeners add
  `auth-input-box-focused` to the *container* so the gold border lights around icon
  and field together. `getText()` reads whichever control is live.
- **`PasswordStrengthMeter`** — four `Region` bars + status label + three criteria
  rows (8+ chars, a number, both cases). `updateStrength` recomputes on every keystroke
  (via `bindToPassword`): score 0–4 from the criteria plus length ≥ 12 or special
  char; bar classes `strength-empty/weak/fair/good/strong` and status colours red →
  amber → blue → green. Criteria rows flip `○` → `✓` by swapping
  `criteria-label`/`criteria-met` classes.
- **`LogoutDialog`** — an overlay `StackPane` with a dark scrim
  (`rgba(5,7,11,0.75)`), a red circular logout-icon badge, the reassurance subtitle,
  Cancel/Log Out buttons (`auth-btn-danger`), click-on-scrim closes. The shell mounts
  it directly on `rootPane` (Ch. 9) — no separate window.

## 6. How it works at runtime

```
App boot (Ch. 9) ──► dbExecutor: AuthDao.getActiveSession()
     │                     ├─ row exists & token fresh → straight to dashboard
     │                     ├─ row exists & expired → refreshSession()
     │                     │      ├─ ok → AuthDao.updateTokens → dashboard
     │                     │      └─ fail → rememberMe? keep : drop → auth screen
     │                     └─ no row → auth screen
     ▼
User submits → AuthView (FX) → AppExecutors.io() → FirebaseAuthService REST call
     ├─ 200: UserSession → handleSuccessfulLogin
     │     ├─ rememberMe? AuthDao.saveSession : clearSession   (SQLite, id=1 row)
     │     ├─ AuthSessionManager.setActiveSession              (listener bus fires)
     │     └─ onAuthSuccess → shell re-mounts mainLayout → seed → dashboard
     └─ error: banner with parseErrorMessage text, button re-enabled

Google: button → loopback server on 127.0.0.1:8085 → system browser opens
        → Firebase JS popup → POST /callback {tokens} → onSuccess (FX thread)

Any session change → listeners → DataManager.onUserSwitched() → caches dropped
                   → every subsequent DAO query filters by the new userId
```

## 7. How to change it

- **Switch identity providers**: only `FirebaseAuthService` speaks HTTP. Keep the
  `UserSession` shape (id/email/tokens/expiry) and the manager/DAO contract and the
  rest of the app never notices — that's the seam.
- **Tighten the password policy**: change the three minimums together — sign-up's
  `< 6` check, the strength meter's `>= 8` criterion, and `SET_NEW_PASSWORD`'s
  `< 8`. (Today they disagree — see the `ISSUE:` above.)
- **Move the loopback port**: update both the `8085` bind *and* the authorised
  domain/port expectations of your Firebase project; the port-0 fallback means hard
 coding is only a cosmetic preference.
- **Verify**: sign in with remember-me on → restart the app → dashboard appears with
  no prompt (session row restored, token refreshed if > 1 h old); sign out; sign in
  with remember-me off → restart → auth screen; wrong password → the banner shows
  "Incorrect email or password", never a stack trace.

## 8. Performance & UX analysis

- **Network strictly off the FX thread** (`AppExecutors.io()`), buttons disabled with
  progress text during flight — no frozen UI, no double submits. *Cost:* a little
  boilerplate per screen. *Alternative:* `Task<T>`/`Service` — more structure, same
  outcome; the pool pattern is fine at this size.
- **Session restore at boot before the window paints its first real screen** — the
  user who checked "remember me" never sees the login card again unless the refresh
  genuinely failed. *UX win:* seconds saved every single launch.
- **30 s expiry grace + refresh-at-boot** means API-holding features (none today
  beyond auth itself) would never send a dying token.
- **OPTIONAL IMPROVEMENT (Easy):** auto-refresh proactively in a scheduler when the
  token nears expiry instead of only at boot — needed only if future features call
  Firebase during a session.
- **OPTIONAL IMPROVEMENT (Medium):** replace the shared `API_KEY` constant with a
  config file so white-labelling the app doesn't need recompilation. *Trade-off:*
  Easy in code, but it must ship somewhere — Firebase web keys are public anyway.

## 9. Common mistakes and fixes

| Symptom | Cause | Fix |
|---|---|---|
| `AuthException: Failed to refresh...` every launch | Refresh token revoked (password change / inactive > 30 days) | Expected: user signs in again; remember-me-off users get logged out by design |
| Banner shows raw `REQUEST_TYPE` text | Firebase error code not in `parseErrorMessage` | Add a mapping case for the new code |
| Google sign-in does nothing | No default browser / `Desktop` unsupported | The service surfaces "Please open: <url>" — copy it into a browser |
| "Unable to start Google authentication: Address already in use" | Port 8085 held by another app *and* the fallback failed | Rare; retry — fallback binds port 0 |
| Two accounts' data mixed | Someone called `setActiveSession` without going through `handleSuccessfulLogin` | Always route login/logout through AuthView + `clear()` |
| Card jumps when switching states | Banner `visible` toggled without `managed` | Toggle both together (the view already does) |

## 10. Checkpoint

```bash
mvn test -Dtest=AuthAndDataPartitioningTest
```

Green means: per-user partitioning, session-change cache drops, and the manager
helpers behave. Then verify by hand: wrong password → friendly banner; remember-me on
→ restart skips login; Google button opens the browser page; logout overlay cancels
and confirms correctly. Exercises:

1. Add a `PASSWORD_TOO_WEAK` mapping to `parseErrorMessage` with your own sentence.
2. Add a fourth strength criterion (special character) to `PasswordStrengthMeter`
   with its own checklist row and score contribution.
3. Write a unit test proving `UserSession.isExpired()` is true at exactly
   `expiresAtMillis - 29_999` and false at `expiresAtMillis - 31_000`.

## 11. Summary and coverage self-check

Authentication is one REST client (email flows + refresh + Google loopback), one
ambient session holder with a listener bus, one single-row persistence DAO, and a
six-state premium UI built from four reusable widgets. The entire rest of the app
knows only two things: `AuthSessionManager.getCurrentUserId()` and "when the session
changes, caches drop".

**Covered in full this chapter (9/9):** `FirebaseAuthService` (constants, pools,
singleton, `AuthException`, sign-in/up, reset, update, refresh, Google loopback with
its embedded HTML/JS, `parseErrorMessage`'s 8 mappings), `UserSession` (all fields +
30 s grace), `AuthSessionManager` (volatile holder, listener bus, 4 getters, clear),
`AuthDao` (save/get/update×2/clear, id=1 contract), `AuthView` (state machine, all six
`render*` screens, async submit pattern, banner/input/divider helpers,
`handleSuccessfulLogin`), `GoogleSignInButton`, `PasswordFieldWithToggle`,
`PasswordStrengthMeter`, `LogoutDialog`.

**Markers raised this chapter:**
- `ISSUE:` password minimums disagree (sign-up 6, set-new-password and meter 8).
- `ISSUE:` Firebase web API key/config shipped as public constants (normal for
  Firebase, still public-by-decompilation).
- `ISSUE:` Google loopback binds a guessable fixed port (8085) with a random fallback.
- `GAP:` `SET_NEW_PASSWORD` is only reachable with an active session (from Settings);
  the emailed reset link completes entirely in the browser — the flow's final leg
  lives outside the app, by design.

**Next: Chapter 11 — Listing & Editing Data: The Master-Data Views.**
