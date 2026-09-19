package com.invoicestudio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.invoicestudio.AppDirs;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-model usability memory, powering the red/green status dots in the
 * model picker and the chat header's model menu.
 *
 * <p>How a dot earns its colour — there is no public "balance" API for any
 * provider, so status is LEARNED from real usage instead:</p>
 * <ul>
 *   <li><b>RED</b> — a request through {@link AiChatClient} failed with a
 *       model-specific wall: daily free-tier quota (Gemini "Daily free-tier
 *       limit" / GenerateRequestsPerDay), Z.ai "Insufficient balance",
 *       402 payment required, 403/404 model-access or unknown-model errors.
 *       The model id is stored so the pickers can warn before the user
 *       wastes a request.</li>
 *   <li><b>GREEN</b> — the same provider+model completed a real request
 *       successfully. A later success always wins: a model marked red by a
 *       transient/daily error flips back to green the moment it works
 *       again (e.g. after the daily quota reset).</li>
 * </ul>
 *
 * <p>RED entries age out after {@link #RED_TTL}: daily buckets refill at
 * midnight Pacific, so a stale "no balance" memory must not block a model
 * forever. GREEN has no TTL — it is refreshed by every successful use.</p>
 *
 * <p>Persisted per-user as {@code model-status.json} in the app data dir
 * (same posture as {@code api-vault.json}: plain local JSON). Thread-safe:
 * all mutation happens under a single monitor; the map is copied out for
 * readers.</p>
 */
public final class ModelStatusStore {

    /** Learned usability of one provider+model pair. */
    public enum State { OK, BLOCKED }

    public record Entry(String provider, String model, State state,
                        String reason, String since) {}

    private static final String FILE_NAME = "model-status.json";
    /** RED entries older than this are treated as unknown (daily reset). */
    private static final Duration RED_TTL = Duration.ofHours(26);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Object LOCK = new Object();

    private ModelStatusStore() {}

    // ── Recording (called by AiChatClient) ────────────────────────────

    /** Marks provider+model BLOCKED with the given human reason. */
    public static void markBlocked(String provider, String model, String reason) {
        update(provider, model, State.BLOCKED, reason);
    }

    /** Marks provider+model OK (a real successful request through it). */
    public static void markOk(String provider, String model) {
        update(provider, model, State.OK, null);
    }

    private static void update(String provider, String model, State state, String reason) {
        if (provider == null || provider.isBlank() || model == null || model.isBlank()) return;
        synchronized (LOCK) {
            Map<String, Map<String, Entry>> all = load();
            Map<String, Entry> perProvider = all.computeIfAbsent(provider, p -> new ConcurrentHashMap<>());
            perProvider.put(model, new Entry(provider, model, state, reason, Instant.now().toString()));
            persist(all);
        }
    }

    // ── Queries (called by the pickers) ───────────────────────────────

    /**
     * The learned state for provider+model. BLOCKED entries older than
     * {@link #RED_TTL} read as unknown — the daily bucket may have reset.
     */
    public static Entry get(String provider, String model) {
        if (provider == null || model == null) return null;
        synchronized (LOCK) {
            Entry e = load().getOrDefault(provider, Map.of()).get(model);
            if (e != null && e.state() == State.BLOCKED) {
                try {
                    if (Duration.between(Instant.parse(e.since()), Instant.now()).compareTo(RED_TTL) > 0) {
                        return null; // aged out — treat as unknown
                    }
                } catch (Exception ignored) {
                    return null; // corrupt timestamp — fail open
                }
            }
            return e;
        }
    }

    // ── Persistence ───────────────────────────────────────────────────

    private static Map<String, Map<String, Entry>> load() {
        try {
            File f = file();
            if (f.exists()) {
                return MAPPER.readValue(f, new TypeReference<>() { });
            }
        } catch (Exception ignored) {
            AppLog.debug(new Exception("model-status.json unreadable — starting fresh", ignored));
        }
        return new ConcurrentHashMap<>();
    }

    private static void persist(Map<String, Map<String, Entry>> all) {
        try {
            MAPPER.writeValue(file(), all);
        } catch (Exception ignored) {
            AppLog.debug(ignored);
        }
    }

    private static File file() {
        return AppDirs.dataDir().resolve(FILE_NAME).toFile();
    }
}
