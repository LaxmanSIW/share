package com.invoicestudio.db;

import com.invoicestudio.model.UserSession;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

public class AuthDao {
    private final DatabaseManager db;

    public AuthDao(DatabaseManager db) {
        this.db = db;
    }

    public void saveSession(UserSession session) {
        if (session == null) return;
        try (Connection conn = db.getConnection()) {
            // Keep single active session record
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM auth_session")) {
                del.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO auth_session (id, user_id, email, display_name, id_token, refresh_token, expires_at, remember_me, created_at) " +
                    "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, session.getUserId());
                ps.setString(2, session.getEmail());
                ps.setString(3, session.getDisplayName());
                ps.setString(4, session.getIdToken());
                ps.setString(5, session.getRefreshToken());
                ps.setLong(6, session.getExpiresAtMillis());
                ps.setInt(7, session.isRememberMe() ? 1 : 0);
                ps.setString(8, session.getCreatedAt() != null ? session.getCreatedAt() : Instant.now().toString());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public UserSession getActiveSession() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT user_id, email, display_name, id_token, refresh_token, expires_at, remember_me, created_at FROM auth_session WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                UserSession session = new UserSession();
                session.setUserId(rs.getString("user_id"));
                session.setEmail(rs.getString("email"));
                session.setDisplayName(rs.getString("display_name"));
                session.setIdToken(rs.getString("id_token"));
                session.setRefreshToken(rs.getString("refresh_token"));
                session.setExpiresAtMillis(rs.getLong("expires_at"));
                session.setRememberMe(rs.getInt("remember_me") == 1);
                session.setCreatedAt(rs.getString("created_at"));
                return session;
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return null;
    }

    public void updateTokens(String userId, String idToken, String refreshToken, long expiresAtMillis) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE auth_session SET id_token = ?, refresh_token = ?, expires_at = ? WHERE user_id = ?")) {
            ps.setString(1, idToken);
            ps.setString(2, refreshToken);
            ps.setLong(3, expiresAtMillis);
            ps.setString(4, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public void updateTokens(String idToken, String refreshToken, long expiresAtMillis) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE auth_session SET id_token = ?, refresh_token = ?, expires_at = ? WHERE id = 1")) {
            ps.setString(1, idToken);
            ps.setString(2, refreshToken);
            ps.setLong(3, expiresAtMillis);
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public void clearSession() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM auth_session")) {
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }
}
