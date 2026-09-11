import * as cookie from "cookie";
import * as jose from "jose";
import { scryptSync, randomBytes, timingSafeEqual } from "crypto";
import { db } from "./db/engine";
import { env } from "./lib/env";
import type { User } from "./db/types";

const SECRET_KEY = new TextEncoder().encode(env.appSecret || "alpha-erp-local-secret-key-2026");
export const AUTH_COOKIE_NAME = "alpha_auth";
const COOKIE_MAX_AGE = 30 * 60; // 30 minutes in seconds — session expires, user must log in again

// Sliding-session optimization: only reissue a fresh token/cookie when
// less than this much time is left on the current one. Avoids signing
// a brand new JWT on every single request when the session is nowhere
// close to expiring yet.
export const REFRESH_THRESHOLD_SECONDS = 15 * 60; // 15 minutes

// Default admin credentials — used only to seed the very first user.
const DEFAULT_USERNAME = "admin";
const DEFAULT_PASSWORD = "admin";

type AuthConfig = {
  username?: string;
};

export interface LocalAuthPayload {
  username: string;
  role: string;
  exp?: number; // unix seconds when this token expires (set by jose)
}

// ─── Password hashing (scrypt, no external deps) ──────────
function hashPassword(password: string): string {
  const salt = randomBytes(16).toString("hex");
  const hash = scryptSync(password, salt, 64).toString("hex");
  return `${salt}:${hash}`;
}

function verifyPassword(password: string, stored: string): boolean {
  const [salt, hash] = (stored || "").split(":");
  if (!salt || !hash) return false;
  const hashBuffer = Buffer.from(hash, "hex");
  const suppliedBuffer = scryptSync(password, salt, 64);
  if (hashBuffer.length !== suppliedBuffer.length) return false;
  return timingSafeEqual(hashBuffer, suppliedBuffer);
}

// ─── Bootstrap: called on every server start (see boot.ts) ─
// If no user exists at all, create the default admin/admin so the
// app is usable on first run. If a user already exists, do nothing —
// they log in with their own credentials.
export function ensureAdminExists() {
  const row = db.prepare(`SELECT * FROM users LIMIT 1`).get();
 
  if (!row) {
    const now = new Date().toISOString();
    db.prepare(`
      INSERT INTO users (username, password, role, name, createdAt, updatedAt)
      VALUES (?, ?, ?, ?, ?, ?)
    `).run(DEFAULT_USERNAME, hashPassword(DEFAULT_PASSWORD), "admin", "Admin", now, now);
  }
}

export async function createLocalAuthToken(payload: LocalAuthPayload): Promise<string> {
  return new jose.SignJWT(payload as unknown as jose.JWTPayload)
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setExpirationTime("30m")
    .sign(SECRET_KEY);
}

export async function verifyLocalAuthToken(token: string): Promise<LocalAuthPayload | null> {
  try {
    const { payload } = await jose.jwtVerify(token, SECRET_KEY, { clockTolerance: 60 });
    return {
      username: payload.username as string,
      role: payload.role as string,
      exp: payload.exp,
    };
  } catch {
    return null;
  }
}

export function validateCredentials(username: string, password: string): boolean {
  ensureAdminExists();
  const user = db.prepare(`SELECT * FROM users WHERE username = ?`).get(username) as any;
  if (!user) return false;
  return verifyPassword(password, user.password);
}

// Fetches the real user row for use in context/login responses.
// Password/hash is never included in the returned object.
export function getUserByUsername(username: string): User | null {
  const row = db.prepare(`SELECT * FROM users WHERE username = ?`).get(username) as any;
  if (!row) return null;
  return {
    id: row.id,
    unionId: row.unionId ?? null,
    username: row.username,
    password: "", // never leak hash outward
    name: row.name ?? null,
    email: row.email ?? null,
    avatar: row.avatar ?? null,
    role: row.role ?? null,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    lastSignInAt: row.lastSignInAt ?? null,
  };
}

export function updateCredentials(
  username: string,
  currentPassword: string,
  newPassword?: string,
  newUsername?: string
): AuthConfig {
  ensureAdminExists();
  const user = db.prepare(`SELECT * FROM users WHERE username = ?`).get(username) as any;
  if (!user || !verifyPassword(currentPassword, user.password)) {
    throw new Error("Current password is incorrect");
  }

  const finalUsername = newUsername || username;
  const finalPassword = newPassword ? hashPassword(newPassword) : user.password;

  db.prepare(`UPDATE users SET password = ?, username = ?, updatedAt = ? WHERE username = ?`)
    .run(finalPassword, finalUsername, new Date().toISOString(), username);

  return {
    username: finalUsername,
  };
}

export function isDefaultCredentials(): boolean {
  ensureAdminExists();
  const user = db.prepare(`SELECT * FROM users WHERE role = 'admin' LIMIT 1`).get() as any;
  if (!user) return false;
  return user.username === DEFAULT_USERNAME && verifyPassword(DEFAULT_PASSWORD, user.password);
}

export function getAuthCookie(headers: Headers): string | undefined {
  const cookies = cookie.parse(headers.get("cookie") || "");
  return cookies[AUTH_COOKIE_NAME];
}

export function serializeAuthCookie(token: string): string {
  return cookie.serialize(AUTH_COOKIE_NAME, token, {
    httpOnly: true,
    path: "/",
    sameSite: "lax",
    secure: false,
    maxAge: COOKIE_MAX_AGE,
  });
}

export function serializeClearCookie(): string {
  return cookie.serialize(AUTH_COOKIE_NAME, "", {
    httpOnly: true,
    path: "/",
    sameSite: "lax",
    secure: false,
    maxAge: 0,
  });
}