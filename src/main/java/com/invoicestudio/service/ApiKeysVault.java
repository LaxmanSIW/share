package com.invoicestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.invoicestudio.AppDirs;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * API-key vault for the AI chatbot (Settings → Chatbot → API Key Vault).
 *
 * <p>Stores every provider key the user saves under a memorable name —
 * "Gemini free tier", "Z.ai GLM", "Work OpenAI" — so switching providers is
 * a dropdown pick instead of re-pasting a key. Each entry remembers which
 * provider it belongs to; selecting a provider then auto-fills the matching
 * vault key.</p>
 *
 * <p>Persisted per-user as {@code api-vault.json} in the application data
 * directory (same location and security posture as {@code chatbot.json}:
 * plain local file, never leaves this machine except to call the chosen
 * provider directly). The file is rewritten on every mutation and read on
 * every access — no cached state, so concurrent panels always agree.</p>
 */
public final class ApiKeysVault {

    private static final String FILE_NAME = "api-vault.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** One saved key. {@code provider} is the provider id it was saved for. */
    public record VaultEntry(String id, String label, String provider, String key, String createdAt) {}

    private static final List<java.util.function.Consumer<List<VaultEntry>>> LISTENERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private ApiKeysVault() {}

    // ── Read / write ──────────────────────────────────────────────────

    /** All vault entries (fresh from disk; empty list when none / unreadable). */
    public static List<VaultEntry> list() {
        try {
            File f = file();
            if (f.exists()) {
                VaultEntry[] arr = MAPPER.readValue(f, VaultEntry[].class);
                List<VaultEntry> out = new ArrayList<>(List.of(arr));
                out.sort(Comparator.comparing(VaultEntry::createdAt));
                return out;
            }
        } catch (Exception ignored) {
            AppLog.debug(new Exception("api-vault.json unreadable — treating as empty", ignored));
        }
        return List.of();
    }

    /**
     * Saves a key under {@code label} for {@code provider}. Saving the SAME
     * key for the same provider again just refreshes its label (idempotent —
     * no duplicate rows). Returns the stored entry.
     */
    public static VaultEntry add(String label, String provider, String key) {
        List<VaultEntry> entries = new ArrayList<>(list());
        String trimmedKey = key == null ? "" : key.trim();
        String cleanLabel = label == null || label.isBlank() ? defaultLabel(provider) : label.trim();
        String cleanProvider = provider == null || provider.isBlank() ? ChatbotConfig.GEMINI : provider.trim();

        VaultEntry existing = null;
        for (VaultEntry e : entries) {
            if (cleanProvider.equals(e.provider()) && trimmedKey.equals(e.key())) {
                existing = e;
                break;
            }
        }
        VaultEntry entry;
        if (existing != null) {
            entry = new VaultEntry(existing.id(), cleanLabel, existing.provider(), existing.key(), existing.createdAt());
            entries.set(entries.indexOf(existing), entry);
        } else {
            entry = new VaultEntry(java.util.UUID.randomUUID().toString(),
                    cleanLabel, cleanProvider, trimmedKey, Instant.now().toString());
            entries.add(entry);
        }
        persist(entries);
        return entry;
    }

    /** Removes the entry with this id (no-op when absent). */
    public static void remove(String id) {
        if (id == null) return;
        List<VaultEntry> entries = new ArrayList<>(list());
        boolean changed = entries.removeIf(e -> id.equals(e.id()));
        if (changed) {
            persist(entries);
        }
    }

    /** First saved key for a provider (used to auto-fill on provider switch). */
    public static Optional<VaultEntry> findForProvider(String provider) {
        if (provider == null || provider.isBlank()) return Optional.empty();
        String p = provider.trim();
        return list().stream().filter(e -> p.equals(e.provider())).findFirst();
    }

    // ── Presentation helper ───────────────────────────────────────────

    /**
     * Masked form for list rows: first 5 and last 4 characters visible,
     * everything between hidden. Blank/short keys collapse to dots so a
     * short test key never shows itself fully either.
     */
    public static String mask(String key) {
        if (key == null || key.isBlank()) return "—";
        String k = key.trim();
        if (k.length() <= 9) return "•••••";
        return k.substring(0, 5) + "••••" + k.substring(k.length() - 4);
    }

    private static String defaultLabel(String provider) {
        return AiChatClient.providerLabel(provider) + " key";
    }

    private static void persist(List<VaultEntry> entries) {
        try {
            MAPPER.writeValue(file(), entries);
        } catch (Exception ignored) {
            AppLog.debug(ignored);
        }
        for (java.util.function.Consumer<List<VaultEntry>> l : LISTENERS) {
            try {
                l.accept(list());
            } catch (Exception ignored) {
                AppLog.debug(ignored);
            }
        }
    }

    private static File file() {
        return AppDirs.dataDir().resolve(FILE_NAME).toFile();
    }

    // ── Change listeners (UI refresh) ─────────────────────────────────

    public static void addChangeListener(java.util.function.Consumer<List<VaultEntry>> listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeChangeListener(java.util.function.Consumer<List<VaultEntry>> listener) {
        LISTENERS.remove(listener);
    }
}
