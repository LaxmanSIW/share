package com.invoicestudio.service;

import com.invoicestudio.model.UserSession;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Ambient session manager providing current authenticated user context across all layers.
 */
public final class AuthSessionManager {

    private static volatile UserSession activeSession;
    private static final CopyOnWriteArrayList<Consumer<UserSession>> listeners = new CopyOnWriteArrayList<>();

    private AuthSessionManager() {}

    public static UserSession getActiveSession() {
        return activeSession;
    }

    public static void setActiveSession(UserSession session) {
        activeSession = session;
        for (Consumer<UserSession> l : listeners) {
            try {
                l.accept(session);
            } catch (Exception e) {
                com.invoicestudio.service.AppLog.error(e);
            }
        }
    }

    public static String getCurrentUserId() {
        UserSession s = activeSession;
        if (s != null && s.getUserId() != null && !s.getUserId().isBlank()) {
            return s.getUserId();
        }
        return "";
    }

    public static String getCurrentUserEmail() {
        UserSession s = activeSession;
        if (s != null && s.getEmail() != null) {
            return s.getEmail();
        }
        return "";
    }

    public static String getCurrentUserDisplayName() {
        UserSession s = activeSession;
        if (s != null && s.getDisplayName() != null && !s.getDisplayName().isBlank()) {
            return s.getDisplayName();
        }
        String email = getCurrentUserEmail();
        if (!email.isBlank() && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }
        return "User";
    }

    public static boolean isLoggedIn() {
        UserSession s = activeSession;
        return s != null && !s.isExpired();
    }

    public static void clear() {
        setActiveSession(null);
    }

    public static void addSessionChangeListener(Consumer<UserSession> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeSessionChangeListener(Consumer<UserSession> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}
