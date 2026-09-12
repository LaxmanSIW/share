package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSession {

    private String userId;
    private String email;
    private String displayName;
    private String idToken;
    private String refreshToken;
    private long expiresAtMillis;
    private boolean rememberMe;
    private String createdAt;

    public UserSession() {}

    public UserSession(String userId, String email, String displayName, String idToken, String refreshToken, long expiresAtMillis, boolean rememberMe) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.idToken = idToken;
        this.refreshToken = refreshToken;
        this.expiresAtMillis = expiresAtMillis;
        this.rememberMe = rememberMe;
    }

    public boolean isExpired() {
        // Return true if expired or within 30 seconds of expiry
        return System.currentTimeMillis() >= (expiresAtMillis - 30_000);
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public long getExpiresAtMillis() { return expiresAtMillis; }
    public void setExpiresAtMillis(long expiresAtMillis) { this.expiresAtMillis = expiresAtMillis; }

    public boolean isRememberMe() { return rememberMe; }
    public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
