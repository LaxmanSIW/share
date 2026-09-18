package com.invoicestudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Live Gemini model catalogue ({@code GET /v1beta/models}) for the Settings
 * model picker and the chat header's model menu. Filters the raw list down
 * to CHAT-capable text models: must support generateContent, and excludes
 * TTS / image / embedding / live-audio / robotics / video / music / gemma
 * (Gemma cannot do function calling, which the assistant requires).
 *
 * <p>Verified against the live API: entries carry name, displayName,
 * description and token limits. The dashboard's RPM/TPM/RPD quota columns
 * are NOT exposed by any public API — remaining-quota failover is therefore
 * driven by actual 429 "daily limit" responses instead (see AiChatClient).</p>
 */
public final class ModelCatalog {

    /** One chat-capable model as shown in the pickers. */
    public record ModelInfo(String id, String displayName, String description,
                            long inputTokenLimit) {}

    /** Z.ai (GLM) OpenAI-compatible model catalogue endpoint (live-verified:
     *  GET with Bearer auth returns {"object":"list","data":[{"id":…}]}). */
    public static final String GLM_MODELS_URL = "https://api.z.ai/api/paas/v4/models";

    private record Cache(long fetchedAt, List<ModelInfo> models) {}

    private static Cache cache;
    private static Cache glmCache;
    private static final long TTL_MS = 10 * 60_000L; // 10 minutes

    private ModelCatalog() {}

    /** Chat-capable models for the given Gemini key (cached, newest first). */
    public static List<ModelInfo> chatModels(String apiKey) throws Exception {
        if (cache != null && System.currentTimeMillis() - cache.fetchedAt() < TTL_MS) {
            return cache.models();
        }
        ObjectMapper M = new ObjectMapper();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

        List<ModelInfo> out = new ArrayList<>();
        String pageToken = "";
        do {
            String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey
                    + "&pageSize=200" + (pageToken.isEmpty() ? "" : "&pageToken=" + pageToken);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("HTTP " + resp.statusCode() + " loading model catalogue");
            }
            JsonNode root = M.readTree(resp.body());
            for (JsonNode m : root.path("models")) {
                ModelInfo info = parse(M, m);
                if (info != null) out.add(info);
            }
            pageToken = root.path("nextPageToken").asText("");
        } while (!pageToken.isEmpty());

        cache = new Cache(System.currentTimeMillis(), List.copyOf(out));
        return cache.models();
    }

    /**
     * Chat models for the given Z.ai (GLM) key — live from
     * {@code GET /api/paas/v4/models} (cached 10 min, same policy as Gemini).
     *
     * <p>The raw entries carry only {@code id}/{@code created}/{@code owned_by}
     * (no display names or token limits — live-verified), so the id doubles as
     * the display name. Obvious non-chat entries (embeddings/audio) are
     * filtered defensively; ids are returned in the API's own order.</p>
     */
    public static List<ModelInfo> glmModels(String apiKey) throws Exception {
        if (glmCache != null && System.currentTimeMillis() - glmCache.fetchedAt() < TTL_MS) {
            return glmCache.models();
        }
        ObjectMapper M = new ObjectMapper();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GLM_MODELS_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " loading GLM model catalogue");
        }
        JsonNode root = M.readTree(resp.body());
        List<ModelInfo> out = new ArrayList<>();
        for (JsonNode m : root.path("data")) {
            ModelInfo info = parseGlm(m);
            if (info != null) out.add(info);
        }
        glmCache = new Cache(System.currentTimeMillis(), List.copyOf(out));
        return glmCache.models();
    }

    /** Parses one raw GLM model entry; null when not chat-capable. */
    static ModelInfo parseGlm(JsonNode m) {
        String id = m.path("id").asText("").trim();
        if (id.isEmpty()) return null;
        String lower = id.toLowerCase();
        if (lower.contains("embed") || lower.contains("audio") || lower.contains("tts")
                || lower.contains("asr") || lower.contains("video") || lower.contains("image")) {
            return null;
        }
        return new ModelInfo(id, id, "", 0);
    }

    /**
     * Chat models for a provider id — the single entry point used by the
     * pickers. Only providers with a live catalogue endpoint are supported;
     * the caller decides what to do for the rest.
     */
    public static boolean hasLiveCatalogue(String provider) {
        return ChatbotConfig.GEMINI.equals(provider) || ChatbotConfig.GLM.equals(provider);
    }

    /** Provider-dispatching catalogue fetch (cached per provider). */
    public static List<ModelInfo> modelsFor(String provider, String apiKey) throws Exception {
        if (ChatbotConfig.GLM.equals(provider)) return glmModels(apiKey);
        return chatModels(apiKey); // default: Gemini
    }

    /** Parses + filters one raw model entry; null when not chat-capable. */
    static ModelInfo parse(ObjectMapper M, JsonNode m) {
        String id = m.path("name").asText(""); // "models/gemini-x"
        if (id.startsWith("models/")) id = id.substring("models/".length());
        if (id.isEmpty()) return null;

        boolean canGenerate = false;
        for (JsonNode g : m.path("supportedGenerationMethods")) {
            if ("generateContent".equals(g.asText())) { canGenerate = true; break; }
        }
        if (!canGenerate) return null;

        String lower = id.toLowerCase();
        if (lower.contains("tts") || lower.contains("image") || lower.contains("embed")
                || lower.contains("live") || lower.contains("audio") || lower.contains("transcribe")
                || lower.contains("robotics") || lower.contains("veo") || lower.contains("lyria")
                || lower.contains("nano-banana") || lower.contains("computer-use")
                || lower.contains("antigravity") || lower.contains("deep-research")
                || lower.startsWith("gemma")) {
            return null;
        }
        return new ModelInfo(id, m.path("displayName").asText(id),
                m.path("description").asText(""), m.path("inputTokenLimit").asLong(0));
    }

    /**
     * Ordered failover candidates for the assistant (fast flash models first).
     * LIGHT models lead the ladder: when the daily bucket of the primary
     * model empties, we drop to the lightest tier with remaining quota —
     * never UP to a scarcer/pro model. All candidates are flash-family
     * models that keep full function-calling support.
     */
    public static List<String> failoverCandidates() {
        return List.of(
                "gemini-flash-lite-latest", "gemini-flash-latest", "gemini-3.8-flash",
                "gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.5-flash",
                "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite");
    }

    /** Next untried candidate after {@code current} (never returns current). */
    public static String nextFailover(String current, java.util.Set<String> exhausted) {
        for (String cand : failoverCandidates()) {
            if (!cand.equals(current) && !exhausted.contains(cand)) return cand;
        }
        return null;
    }

    public static void invalidate() {
        cache = null;
        glmCache = null;
    }
}
