package com.invoicestudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.invoicestudio.mcp.McpToolRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chatbot's AI engine: talks to Gemini, OpenAI or Anthropic-compatible
 * chat APIs over plain {@link HttpClient} (no new dependencies) and gives the
 * model the app's full MCP tool surface through native function calling.
 *
 * <p>REST contracts (verified against official docs, no guessing):</p>
 * <ul>
 *   <li><b>Gemini</b> — {@code POST /v1beta/models/{model}:generateContent?key=…};
 *       {@code contents[].parts[]} with {@code text} / {@code inline_data}
 *       (base64) / {@code functionCall} / {@code functionResponse}; tools as
 *       {@code functionDeclarations} with an {@code parameters} schema.</li>
 *   <li><b>OpenAI</b> — {@code POST /v1/chat/completions}, Bearer key;
 *       {@code messages[]} with {@code content[].image_url} (data URIs for
 *       attachments); tools as {@code tools[].function}; calls come back as
 *       {@code tool_calls[]}, results are sent as {@code role:"tool"}.</li>
 *   <li><b>Anthropic</b> — {@code POST /v1/messages}, {@code x-api-key} +
 *       {@code anthropic-version}; images as {@code source.type=base64};
 *       tools as {@code tools[].input_schema}; calls back as
 *       {@code tool_use} blocks, results as {@code tool_result}.</li>
 * </ul>
 *
 * <p>The tool loop runs at most {@link #MAX_TOOL_ROUNDS} iterations: the
 * model asks for MCP tools by name, this class executes them locally through
 * {@link McpToolRegistry#call} (which keeps the app's own confirmation-gate
 * safety model for destructive ops) and returns JSON results to the model.</p>
 */
public final class AiChatClient {

    /** One chat attachment: raw image bytes + mime type. */
    public record ImagePart(String mimeType, byte[] data) {}

    /** One turn of chat as the app stores it. */
    public record ChatTurn(String role, String text, ImagePart image) {
        public static ChatTurn user(String t) { return new ChatTurn("user", t, null); }
        public static ChatTurn userImage(String t, ImagePart img) { return new ChatTurn("user", t, img); }
        public static ChatTurn assistant(String t) { return new ChatTurn("assistant", t, null); }
    }

    /** Result of one full send: the assistant's text + what tools it ran. */
    public record ChatResult(String text, List<String> toolTrace) {}

    /** Hard cap on model↔tool round trips per send (safety + cost bound). */
    private static final int MAX_TOOL_ROUNDS = 6;

    private static final ObjectMapper M = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * Sends the conversation to the configured provider and resolves the
     * model's tool calls against the MCP registry until it produces text.
     *
     * @param cfg        chatbot settings (provider, model, key, endpoint)
     * @param history    prior turns (oldest first); tail is trimmed to the configured window
     * @param userText   the new user message
     * @param attachment optional image attached to this message (nullable)
     */
    public ChatResult send(ChatbotConfig cfg, List<ChatTurn> history,
                           String userText, ImagePart attachment) throws Exception {
        // Ollama runs locally and needs no key; every cloud provider does.
        if (cfg.getApiKey().isBlank() && !ChatbotConfig.OLLAMA.equals(cfg.getProvider())) {
            throw new IllegalStateException("No API key configured — open Settings → Chatbot and save your key.");
        }
        List<ChatTurn> turns = new ArrayList<>(history);
        turns.add(new ChatTurn("user", userText == null ? "" : userText, attachment));

        // ── Token & request optimization pipeline ────────────────────
        // Each tool schema costs input tokens on EVERY request, and tool
        // rounds multiply requests. So:
        //   1. Zero-cost skip: greetings/smalltalk (regex) get one lean,
        //      schema-free request.
        //   2. Smart routing: a tiny router request (tool NAMES only) picks
        //      the few tools this question needs; the heavy pass carries only
        //      those schemas (~7K → ~1K input tokens typically).
        //   3. If the model calls a tool outside the shortlist, escalate ONCE
        //      to the full catalogue (correctness net, rare).
        java.util.Set<String> allowed = null;
        if (cfg.isSmartRouting() && attachment == null) {
            String first = userText == null ? "" : userText.trim();
            boolean pureChat = CHAT_ONLY.matcher(first).matches()
                    && turns.stream().noneMatch(t -> "call".equals(t.role()) || "tool".equals(t.role()));
            if (pureChat) {
                ProviderResponse resp = dispatch(cfg, turns, java.util.Set.of());
                return new ChatResult(resp.text(), List.of());
            }
            ToolRoute route = routeTools(cfg, first);
            if (route != null && !route.needTools()) {
                // Router already answered the question conversationally —
                // no second request, no schemas, no tool rounds.
                return new ChatResult(route.note(), List.of());
            }
            if (route != null) allowed = new java.util.HashSet<>(route.tools());
        }

        List<String> trace = new ArrayList<>();
        boolean escalated = false;
        int round = 0;
        java.util.Set<String> exhausted = new java.util.HashSet<>();
        String originalModel = cfg.getModel();
        while (true) {
            if (round > MAX_TOOL_ROUNDS) {
                return new ChatResult("(stopped after " + MAX_TOOL_ROUNDS
                        + " tool rounds — ask me to continue)", trace);
            }
            ProviderResponse resp;
            try {
                resp = dispatch(cfg, turns, allowed);
            } catch (IllegalStateException ex) {
                String msg = String.valueOf(ex.getMessage());
                if (msg.contains("Daily free-tier limit")
                        && ChatbotConfig.GEMINI.equals(cfg.getProvider())) {
                    // Auto-failover: this model's daily bucket is empty — try
                    // the next best chat model with remaining quota.
                    exhausted.add(cfg.getModel().isBlank() ? defaultModel(cfg.getProvider()) : cfg.getModel());
                    String next = ModelCatalog.nextFailover(
                            cfg.getModel().isBlank() ? defaultModel(cfg.getProvider()) : cfg.getModel(),
                            exhausted);
                    if (next != null) {
                        exhausted.add(next); // one attempt per candidate
                        cfg.setModel(next);
                        trace.add("⚠ model " + (originalModel.isBlank() ? "default" : originalModel)
                                + " hit its daily limit — switched to " + next);
                        continue;
                    }
                    cfg.setModel(originalModel); // restore before reporting
                    throw new IllegalStateException(msg + "\nAll Gemini fallback models are also at their "
                            + "daily limits — wait for the daily reset or use another provider (Ollama is local & free).", ex);
                }
                throw ex;
            }
            if (resp.toolCalls().isEmpty()) {
                if (originalModel != null && !cfg.getModel().equals(originalModel)) {
                    cfg.setModel(originalModel); // failover is per-send only
                }
                return new ChatResult(resp.text(), trace);
            }
            // Router under-selected? (model called a tool that wasn't
            // shortlisted). Escalate once to the full catalogue and re-ask —
            // cheaper than always carrying every schema.
            if (allowed != null && !escalated) {
                final java.util.Set<String> currentAllowed = allowed;
                if (resp.toolCalls().stream().anyMatch(tc -> !currentAllowed.contains(tc.name()))) {
                    escalated = true;
                    allowed = null;
                    continue;
                }
            }
            // Record the model's tool-call REQUEST in provider-neutral form
            // (role "call") — each provider renderer re-emits it correctly on
            // the next round: Gemini needs the functionCall PART before the
            // functionResponse, OpenAI needs assistant.tool_calls, Anthropic
            // needs assistant tool_use blocks. A bare empty-text assistant
            // turn is INVALID on all three and breaks round 2.
            ArrayNode callsJson = M.createArrayNode();
            for (ToolCall tc : resp.toolCalls()) {
                ObjectNode cj = callsJson.addObject();
                cj.put("name", tc.name());
                cj.put("args", tc.argumentsJson());
                cj.put("id", tc.id());
                if (tc.thoughtSignature() != null && !tc.thoughtSignature().isBlank()) {
                    cj.put("tSig", tc.thoughtSignature());
                }
            }
            turns.add(new ChatTurn("call", M.writeValueAsString(callsJson), null));
            // Execute each requested tool locally and record the results so
            // the next request carries them back to the model. Results are
            // capped — a huge JSON dump would be re-sent every later round.
            ArrayNode results = M.createArrayNode();
            for (ToolCall tc : resp.toolCalls()) {
                trace.add(tc.name() + "(" + shorten(tc.argumentsJson()) + ")");
                String out;
                try {
                    Object res = McpToolRegistry.call(tc.name(), parseArgs(tc.argumentsJson()));
                    out = M.writeValueAsString(res);
                } catch (Exception e) {
                    out = M.writeValueAsString(Map.of("error", String.valueOf(e.getMessage())));
                }
                if (out.length() > MAX_RESULT_CHARS) {
                    out = M.writeValueAsString(out.substring(0, MAX_RESULT_CHARS)
                            + " …(truncated — narrow your query, e.g. add a limit)");
                }
                ObjectNode r = results.addObject();
                r.put("name", tc.name());
                r.put("id", tc.id());
                r.put("result", out);
            }
            turns.add(new ChatTurn("tool", results.toString(), null));
            round++;
        }
    }

    /** Pure smalltalk — answered with zero tool schemas (zero-cost skip). */
    private static final java.util.regex.Pattern CHAT_ONLY = java.util.regex.Pattern.compile(
            "(?is)^\\s*(hi+|hello+|hey+|yo|thanks?|thank\\s*you|thx|ty|ok(ay)?|great|nice|cool|wow|"
            + "good\\s*(morning|afternoon|evening|night)|bye+|goodbye|see\\s*ya|"
            + "who\\s+are\\s+you\\??|how\\s+are\\s+you\\??|what\\s+can\\s+you\\s+do\\??|help)\\s*[!.?]*\\s*$");

    /** Tool results larger than this are truncated before re-sending. */
    private static final int MAX_RESULT_CHARS = 4000;

    /** Router decision — visible to tests. */
    record ToolRoute(boolean needTools, List<String> tools, String note) {}

    /**
     * Router pass: one lean request (no tool schemas — names only) that
     * either answers conversationally (CHAT) or shortlists the tools the
     * question needs (ROUTE). Returns null to fail OPEN (full catalogue)
     * on any router error — optimization must never cost correctness.
     */
    private ToolRoute routeTools(ChatbotConfig cfg, String userText) {
        try {
            StringBuilder names = new StringBuilder();
            for (McpToolRegistry.ToolDef t : McpToolRegistry.tools()) {
                if (names.length() > 0) names.append(", ");
                names.append(t.name);
            }
            String sys = "You are the request router of an InvoiceStudio billing app assistant. "
                    + "Available tools: " + names + ". \n"
                    + "Decide if the user's LATEST message needs live business data via tools.\n"
                    + "Reply with EXACTLY one line and nothing else:\n"
                    + "- If data is needed: ROUTE: tool1, tool2 (fewest matching names from the list)\n"
                    + "- Questions about how to use InvoiceStudio itself: ROUTE: get_app_guide\n"
                    + "- Otherwise (small talk, greetings, general knowledge): CHAT: <answer the user briefly>";
            String out = rawCompletion(cfg, sys, userText);
            if (out == null) return null;
            return parseRouteDecision(out, cfg);
        } catch (Exception e) {
            AppLog.debug(e);
            return null; // fail open
        }
    }

    /** Parses the router line; visible for tests. Package-private, static. */
    static ToolRoute parseRouteDecision(String routerOutput, ChatbotConfig cfg) {
        String line = routerOutput == null ? "" : routerOutput.trim();
        int route = line.toUpperCase().indexOf("ROUTE:");
        if (route >= 0) {
            String list = line.substring(route + 6);
            int nl = list.indexOf('\n');
            if (nl >= 0) list = list.substring(0, nl);
            java.util.Set<String> known = new java.util.HashSet<>();
            for (McpToolRegistry.ToolDef t : McpToolRegistry.tools()) known.add(t.name);
            List<String> picked = new ArrayList<>();
            for (String raw : list.split(",")) {
                String n = raw.trim().replace("`", "");
                if (known.contains(n) && !picked.contains(n)) picked.add(n);
            }
            if (picked.isEmpty()) return new ToolRoute(true, List.of(), ""); // empty shortlist = all tools
            return new ToolRoute(true, picked, "");
        }
        int chat = line.toUpperCase().indexOf("CHAT:");
        if (chat >= 0 && chat + 5 < line.length()) {
            String answer = line.substring(chat + 5).trim();
            if (!answer.isEmpty()) return new ToolRoute(false, List.of(), answer);
        }
        // Unparseable → fail open: full pass with the complete catalogue.
        return new ToolRoute(true, List.of(), "");
    }

    /** One no-tools completion with a custom system instruction (router). */
    private String rawCompletion(ChatbotConfig cfg, String system, String userText) throws Exception {
        return switch (cfg.getProvider()) {
            case ChatbotConfig.GEMINI -> rawGemini(cfg, system, userText);
            default -> rawOpenAiCompatible(cfg, system, userText);
        };
    }

    private String rawGemini(ChatbotConfig cfg, String system, String userText) throws Exception {
        // The router runs on a separate LITE model — free tier quotas are per
        // model per day, so routing must not eat the main model's request cap
        // (and lite ≈ tiny latency/cost).
        String model = "gemini-3.1-flash-lite";
        String url = (cfg.getEndpoint().isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta/models/" : cfg.getEndpoint())
                + model + ":generateContent?key=" + cfg.getApiKey();
        ObjectNode body = M.createObjectNode();
        ObjectNode si = M.createObjectNode();
        ObjectNode part = si.putArray("parts").addObject();
        part.put("text", system);
        body.set("system_instruction", si);
        ObjectNode c = body.putArray("contents").addObject();
        c.put("role", "user");
        c.putArray("parts").addObject().put("text", userText);
        JsonNode root = post(url, "POST", body, Map.of());
        return root.path("candidates").path(0).path("content").path("parts").path(0)
                .path("text").asText("");
    }

    private String rawOpenAiCompatible(ChatbotConfig cfg, String system, String userText) throws Exception {
        String model = cfg.getModel().isBlank() ? defaultModel(cfg.getProvider()) : cfg.getModel();
        String url = switch (cfg.getProvider()) {
            case ChatbotConfig.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions";
            case ChatbotConfig.GROQ -> "https://api.groq.com/openai/v1/chat/completions";
            case ChatbotConfig.OLLAMA -> "http://localhost:11434/v1/chat/completions";
            case ChatbotConfig.MISTRAL -> "https://api.mistral.ai/v1/chat/completions";
            case ChatbotConfig.DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions";
            default -> cfg.getEndpoint().isBlank()
                    ? "https://api.openai.com/v1/chat/completions" : cfg.getEndpoint();
        };
        if (url == null || url.isBlank()) throw new IllegalStateException("Custom provider needs an endpoint.");
        ObjectNode body = M.createObjectNode();
        body.put("model", model);
        body.putArray("messages").add(M.createObjectNode().put("role", "system").put("content", system))
                .add(M.createObjectNode().put("role", "user").put("content", userText));
        JsonNode root = post(url, "POST", body, Map.of("Authorization", "Bearer " + cfg.getApiKey()));
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    /** Routes to the right provider implementation with the allowed-tool filter. */
    private ProviderResponse dispatch(ChatbotConfig cfg, List<ChatTurn> turns,
                                      java.util.Set<String> allowed) throws Exception {
        return switch (cfg.getProvider()) {
            case ChatbotConfig.OPENAI -> callOpenAiCompatible(cfg, turns,
                    "https://api.openai.com/v1/chat/completions", Map.of(), allowed);
            case ChatbotConfig.ANTHROPIC -> callAnthropic(cfg, turns, allowed);
            case ChatbotConfig.OPENROUTER -> callOpenAiCompatible(cfg, turns,
                    "https://openrouter.ai/api/v1/chat/completions", Map.of(), allowed);
            case ChatbotConfig.GROQ -> callOpenAiCompatible(cfg, turns,
                    "https://api.groq.com/openai/v1/chat/completions", Map.of(), allowed);
            case ChatbotConfig.OLLAMA -> callOpenAiCompatible(cfg, turns,
                    "http://localhost:11434/v1/chat/completions", Map.of(), allowed);
            case ChatbotConfig.MISTRAL -> callOpenAiCompatible(cfg, turns,
                    "https://api.mistral.ai/v1/chat/completions", Map.of(), allowed);
            case ChatbotConfig.DEEPSEEK -> callOpenAiCompatible(cfg, turns,
                    "https://api.deepseek.com/v1/chat/completions", Map.of(), allowed);
            case ChatbotConfig.CUSTOM -> callOpenAiCompatible(cfg, turns, "", Map.of(), allowed);
            default -> callGemini(cfg, turns, allowed);
        };
    }

    /** Provider default model when the user left the model field blank. */
    public static String defaultModel(String provider) {
        return switch (provider) {
            case ChatbotConfig.OPENAI -> "gpt-4o-mini";
            case ChatbotConfig.ANTHROPIC -> "claude-sonnet-4-5";
            case ChatbotConfig.OPENROUTER -> "openai/gpt-4o-mini";
            case ChatbotConfig.GROQ -> "llama-3.3-70b-versatile";
            case ChatbotConfig.OLLAMA -> "llama3.1";
            case ChatbotConfig.MISTRAL -> "mistral-large-latest";
            case ChatbotConfig.DEEPSEEK -> "deepseek-chat";
            case ChatbotConfig.CUSTOM -> "";
            // "flash-latest" is Google's stable alias for the current flash
            // model — immune to per-version retirements (gemini-2.0-flash's
            // 404 was live-verified) and listed for this key.
            default -> "gemini-flash-latest";
        };
    }

    /** Human-readable provider display names for the Settings combo. */
    public static String providerLabel(String provider) {
        return switch (provider) {
            case ChatbotConfig.OPENAI -> "OpenAI (GPT)";
            case ChatbotConfig.ANTHROPIC -> "Anthropic (Claude)";
            case ChatbotConfig.OPENROUTER -> "OpenRouter (400+ models)";
            case ChatbotConfig.GROQ -> "Groq (ultra-fast Llama)";
            case ChatbotConfig.OLLAMA -> "Ollama (local, free)";
            case ChatbotConfig.MISTRAL -> "Mistral AI";
            case ChatbotConfig.DEEPSEEK -> "DeepSeek";
            case ChatbotConfig.CUSTOM -> "Custom OpenAI-compatible…";
            default -> "Google Gemini";
        };
    }

    // ─────────────────────────────────────────────────────────────────
    // Gemini
    // ─────────────────────────────────────────────────────────────────

    private ProviderResponse callGemini(ChatbotConfig cfg, List<ChatTurn> turns,
                                        java.util.Set<String> allowed) throws Exception {
        String model = cfg.getModel().isBlank() ? defaultModel(ChatbotConfig.GEMINI) : cfg.getModel();
        String url = (cfg.getEndpoint().isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta/models/" : cfg.getEndpoint())
                + model + ":generateContent?key=" + cfg.getApiKey();

        ObjectNode body = M.createObjectNode();
        body.set("system_instruction", sysInstruction());
        body.set("contents", geminiContents(cfg, turns));
        body.set("tools", geminiTools(allowed));

        JsonNode root = post(url, "POST", body, Map.of());
        JsonNode cand = root.path("candidates").path(0);
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        JsonNode parts = cand.path("content").path("parts");
        if (parts.isArray()) {
            for (JsonNode p : parts) {
                if (p.hasNonNull("text")) text.append(p.get("text").asText());
                JsonNode fc = p.path("functionCall");
                if (fc.isObject()) {
                    // thoughtSignature lives on the PART, beside functionCall —
                    // not inside it (live-verified 400 without it).
                    calls.add(new ToolCall(fc.path("name").asText(),
                            M.writeValueAsString(fc.path("args")), "",
                            p.path("thoughtSignature").asText("")));
                }
            }
        }
        if (!cand.isMissingNode() && text.isEmpty() && calls.isEmpty()) {
            String reason = cand.path("finishReason").asText("");
            if (!reason.isBlank() && !"STOP".equals(reason)) {
                throw new IllegalStateException("Gemini returned finishReason=" + reason);
            }
        }
        return new ProviderResponse(text.toString(), calls);
    }

    private ArrayNode geminiContents(ChatbotConfig cfg, List<ChatTurn> turns) throws Exception {
        ArrayNode contents = M.createArrayNode();
        for (ChatTurn t : trimHistory(turns, cfg)) {
            // Gemini roles are only "user" and "model"; tool results ride as
            // role "user" parts. Function-call turns replay as role "model".
            String role = switch (t.role()) {
                case "assistant", "call" -> "model";
                default -> "user";
            };
            ObjectNode c = contents.addObject();
            c.put("role", role);
            ArrayNode parts = c.putArray("parts");
            if ("call".equals(t.role())) {
                JsonNode cs = M.readTree(t.text());
                for (JsonNode c2 : cs) {
                    ObjectNode fc = parts.addObject();
                    JsonNode args = M.readTree(c2.path("args").asText("{}"));
                    // Gemini 3 requires the model's thoughtSignature to round-
                    // trip on replayed functionCall parts (live-verified 400).
                    ObjectNode call = M.createObjectNode()
                            .put("name", c2.path("name").asText())
                            .set("args", args);
                    fc.set("functionCall", call);
                    String tSig = c2.path("tSig").asText("");
                    if (!tSig.isBlank()) fc.put("thoughtSignature", tSig);
                }
            } else if ("tool".equals(t.role())) {
                JsonNode rs = M.readTree(t.text());
                for (JsonNode r : rs) {
                    ObjectNode fr = parts.addObject();
                    fr.set("functionResponse", M.createObjectNode()
                            .put("name", r.path("name").asText())
                            .set("response", M.createObjectNode().set("result", M.readTree(r.path("result").asText()))));
                }
            } else {
                if (t.image() != null) {
                    ObjectNode id = parts.addObject();
                    id.set("inline_data", M.createObjectNode()
                            .put("mime_type", t.image().mimeType())
                            .put("data", java.util.Base64.getEncoder().encodeToString(t.image().data())));
                }
                if (t.text() != null && !t.text().isEmpty()) {
                    parts.addObject().put("text", t.text());
                }
            }
        }
        return contents;
    }

    private ArrayNode geminiTools(java.util.Set<String> allowed) {
        ArrayNode fns = M.createArrayNode();
        for (McpToolRegistry.ToolDef t : McpToolRegistry.tools()) {
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(t.name)) continue;
            ObjectNode f = fns.addObject();
            f.put("name", t.name);
            f.put("description", t.description);
            f.set("parameters", geminiSafeSchema(M.valueToTree(t.inputSchema)));
        }
        return M.createArrayNode().add(M.createObjectNode().set("functionDeclarations", fns));
    }

    /** Gemini rejects array schemas without an `items` field — inject a
     *  permissive one recursively (caught live via HTTP 400 on tools 38/42). */
    private JsonNode geminiSafeSchema(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            if ("array".equals(obj.path("type").asText()) && !obj.has("items")) {
                obj.set("items", M.createObjectNode().put("type", "string"));
            }
            if (obj.has("properties")) {
                obj.get("properties").forEach(p -> geminiSafeSchema(p));
            }
            if (obj.has("items")) {
                geminiSafeSchema(obj.get("items"));
            }
        }
        return node;
    }

    // ─────────────────────────────────────────────────────────────────
    // OpenAI
    // ─────────────────────────────────────────────────────────────────

    private ProviderResponse callOpenAiCompatible(ChatbotConfig cfg, List<ChatTurn> turns,
                                                  String defaultUrl, Map<String, String> extraHeaders,
                                                  java.util.Set<String> allowed) throws Exception {
        String model = cfg.getModel().isBlank() ? defaultModel(cfg.getProvider()) : cfg.getModel();
        String url = cfg.getEndpoint().isBlank() ? defaultUrl : cfg.getEndpoint();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Custom provider needs an endpoint — set it in Settings → Chatbot " +
                    "(e.g. https://your-host/v1/chat/completions).");
        }

        ObjectNode body = M.createObjectNode();
        body.put("model", model);
        ArrayNode msgs = body.putArray("messages");
        msgs.add(M.createObjectNode().put("role", "system").put("content", systemPrompt()));
        for (ChatTurn t : trimHistory(turns, cfg)) {
            switch (t.role()) {
                case "call" -> {
                    // Assistant turn WITH tool_calls — a separate contentless
                    // assistant message is invalid; the id must round-trip.
                    ObjectNode m = msgs.addObject();
                    m.put("role", "assistant");
                    m.put("content", (String) null);
                    ArrayNode tcs = m.putArray("tool_calls");
                    JsonNode cs = M.readTree(t.text());
                    for (JsonNode c2 : cs) {
                        ObjectNode tc = tcs.addObject();
                        tc.put("id", c2.path("id").asText("call_" + tcs.size()));
                        tc.put("type", "function");
                        tc.set("function", M.createObjectNode()
                                .put("name", c2.path("name").asText())
                                .put("arguments", c2.path("args").asText("{}")));
                    }
                }
                case "user" -> {
                    ObjectNode m = msgs.addObject();
                    m.put("role", "user");
                    if (t.image() != null) {
                        ArrayNode content = m.putArray("content");
                        content.addObject().put("type", "text").put("text", t.text() == null ? "" : t.text());
                        String b64 = java.util.Base64.getEncoder().encodeToString(t.image().data());
                        content.addObject().put("type", "image_url").set("image_url",
                                M.createObjectNode().put("url", "data:" + t.image().mimeType() + ";base64," + b64));
                    } else {
                        m.put("content", t.text() == null ? "" : t.text());
                    }
                }
                case "assistant" -> msgs.addObject().put("role", "assistant").put("content", t.text() == null ? "" : t.text());
                case "tool" -> {
                    JsonNode rs = M.readTree(t.text());
                    for (JsonNode r : rs) {
                        msgs.addObject().put("role", "tool")
                                .put("tool_call_id", r.path("id").asText())
                                .put("content", r.path("result").asText());
                    }
                }
                default -> { }
            }
        }
        ArrayNode tools = body.putArray("tools");
        for (McpToolRegistry.ToolDef t : McpToolRegistry.tools()) {
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(t.name)) continue;
            ObjectNode fn = tools.addObject().put("type", "function").putObject("function");
            fn.put("name", t.name);
            fn.put("description", t.description);
            fn.set("parameters", M.valueToTree(t.inputSchema));
        }

        Map<String, String> headers = new java.util.LinkedHashMap<>(extraHeaders);
        headers.put("Authorization", "Bearer " + cfg.getApiKey());
        JsonNode root = post(url, "POST", body, headers);
        JsonNode msg = root.path("choices").path(0).path("message");
        String text = msg.path("content").asText("");
        List<ToolCall> calls = new ArrayList<>();
        JsonNode tcs = msg.path("tool_calls");
        if (tcs.isArray()) {
            for (JsonNode tc : tcs) {                    calls.add(new ToolCall(tc.path("function").path("name").asText(),
                            tc.path("function").path("arguments").asText("{}"),
                            tc.path("id").asText(), ""));
            }
        }
        return new ProviderResponse(text, calls);
    }

    // ─────────────────────────────────────────────────────────────────
    // Anthropic
    // ─────────────────────────────────────────────────────────────────

    private ProviderResponse callAnthropic(ChatbotConfig cfg, List<ChatTurn> turns,
                                           java.util.Set<String> allowed) throws Exception {
        String model = cfg.getModel().isBlank() ? defaultModel(ChatbotConfig.ANTHROPIC) : cfg.getModel();
        String url = cfg.getEndpoint().isBlank()
                ? "https://api.anthropic.com/v1/messages" : cfg.getEndpoint();

        ObjectNode body = M.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 4096);
        // NOTE: build the wrapper first — chained putArray(...).addObject().put(...)
        // returns the INNER node, and Anthropic needs { "system": [ {type,text} ] }.
        ObjectNode sysBlock = M.createObjectNode();
        ObjectNode sysItem = sysBlock.putArray("content").addObject();
        sysItem.put("type", "text").put("text", systemPrompt());
        body.set("system", sysBlock.get("content"));
        ArrayNode msgs = body.putArray("messages");
        for (ChatTurn t : trimHistory(turns, cfg)) {
            switch (t.role()) {
                case "user" -> {
                    ObjectNode m = msgs.addObject();
                    m.put("role", "user");
                    ArrayNode content = m.putArray("content");
                    if (t.image() != null) {
                        ObjectNode img = content.addObject().put("type", "image");
                        img.set("source", M.createObjectNode()
                                .put("type", "base64")
                                .put("media_type", t.image().mimeType())
                                .put("data", java.util.Base64.getEncoder().encodeToString(t.image().data())));
                    }
                    content.addObject().put("type", "text").put("text", t.text() == null ? "" : t.text());
                }
                case "assistant" -> msgs.addObject().put("role", "assistant").putArray("content").addObject()
                        .put("type", "text").put("text", t.text() == null ? "" : t.text());
                case "call" -> {
                    // Assistant turn replaying its tool_use blocks — Anthropic
                    // requires the tool_use to precede the matching tool_result.
                    ObjectNode m = msgs.addObject();
                    m.put("role", "assistant");
                    ArrayNode content = m.putArray("content");
                    JsonNode cs = M.readTree(t.text());
                    for (JsonNode c2 : cs) {
                        ObjectNode b = content.addObject();
                        b.put("type", "tool_use");
                        b.put("id", c2.path("id").asText("toolu_" + content.size()));
                        b.put("name", c2.path("name").asText());
                        b.set("input", M.readTree(c2.path("args").asText("{}")));
                    }
                }
                case "tool" -> {
                    ObjectNode m = msgs.addObject();
                    m.put("role", "user");
                    ArrayNode content = m.putArray("content");
                    JsonNode rs = M.readTree(t.text());
                    for (JsonNode r : rs) {
                        content.addObject().put("type", "tool_result")
                                .put("tool_use_id", r.path("id").asText())
                                .put("content", r.path("result").asText());
                    }
                }
                default -> { }
            }
        }
        ArrayNode tools = body.putArray("tools");
        for (McpToolRegistry.ToolDef t : McpToolRegistry.tools()) {
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(t.name)) continue;
            ObjectNode f = tools.addObject();
            f.put("name", t.name);
            f.put("description", t.description);
            f.set("input_schema", M.valueToTree(t.inputSchema));
        }

        JsonNode root = post(url, "POST", body, Map.of(
                "x-api-key", cfg.getApiKey(),
                "anthropic-version", "2023-06-01"));
        String text = "";
        List<ToolCall> calls = new ArrayList<>();
        JsonNode content = root.path("content");
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode b : content) {
                if ("text".equals(b.path("type").asText())) sb.append(b.path("text").asText());
                if ("tool_use".equals(b.path("type").asText())) {
                    calls.add(new ToolCall(b.path("name").asText(),
                            M.writeValueAsString(b.path("input")), b.path("id").asText(), ""));
                }
            }
            text = sb.toString();
        }
        return new ProviderResponse(text, calls);
    }

    // ─────────────────────────────────────────────────────────────────
    // Shared plumbing
    // ─────────────────────────────────────────────────────────────────

    private record ToolCall(String name, String argumentsJson, String id, String thoughtSignature) {}
    private record ProviderResponse(String text, List<ToolCall> toolCalls) {}

    private JsonNode post(String url, String method, ObjectNode body,
                          Map<String, String> headers) throws Exception {
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body)));
        headers.forEach(rb::header);
        HttpResponse<String> resp = null;
        // Transient provider failures (5xx overload, per-minute 429 bursts) get
        // a short retry ladder. DAILY quota exhaustion (free tier: 20 req/day
        // per model) is NOT retryable — fail fast with an honest message.
        for (int attempt = 0; ; attempt++) {
            resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            boolean dailyQuota = resp.statusCode() == 429
                    && resp.body() != null && resp.body().contains("PerDay");
            boolean retryable = (resp.statusCode() == 429 || resp.statusCode() >= 500) && !dailyQuota;
            if (!retryable || attempt >= 2) break;
            long delayMs = 3000L * (attempt + 1); // 3s, 6s
            try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException(friendlyProviderError(resp.statusCode(), resp.body()));
        }
        return M.readTree(resp.body());
    }

    /** Providers return nested JSON errors — surface the human message, not raw JSON. */
    private static String friendlyProviderError(int status, String rawBody) {
        String detail = rawBody == null ? "" : rawBody;
        try {
            JsonNode root = M.readTree(detail);
            for (JsonNode cand : new JsonNode[]{
                    root.path("error").path("message"),   // Gemini / OpenAI / Groq / OpenRouter
                    root.path("error").path("description"), // OAuth-style errors
                    root.path("message"),                  // Anthropic / generic
                    root.path("error") }) {                 // plain-string error
                if (cand.isTextual() && !cand.asText().isBlank()) { detail = cand.asText(); break; }
            }
        } catch (Exception ignored) { }
        if (detail.length() > 300) detail = detail.substring(0, 300) + "…";
        // Free-tier daily cap (live-verified): 20 requests/day per model.
        if (rawBody != null && rawBody.contains("GenerateRequestsPerDay")) {
            return "Daily free-tier limit reached for this model (20 requests/day on the free plan). "
                    + "Wait for the daily reset, pick another model in Settings → Chatbot, or use a "
                    + "different provider (e.g. a local Ollama model — unlimited and free).";
        }
        String hint = switch (status) {
            case 401, 403 -> " — check the API key in Settings → Chatbot.";
            case 404 -> " — check the model name / endpoint in Settings → Chatbot.";
            case 429 -> " — rate limit reached; wait a moment or reduce history window.";
            default -> "";
        };
        return "Provider error (HTTP " + status + "): " + detail + hint;
    }

    /** Keeps at most {@code historyMessages} turns (plus the live user turn).
     *  Uses the CONFIGURED window (was hardcoded — the Settings slider had no
     *  effect on requests). */
    private List<ChatTurn> trimHistory(List<ChatTurn> turns, ChatbotConfig cfg) {
        int keep = cfg.getHistoryMessages();
        List<ChatTurn> out = turns.size() <= keep
                ? new ArrayList<>(turns) : new ArrayList<>(turns.subList(turns.size() - keep, turns.size()));
        // Sanitize window edges: a tool RESULT without its preceding call, or
        // a trailing call without its result, is invalid on every provider.
        while (!out.isEmpty() && "tool".equals(out.get(0).role())) out.remove(0);
        while (!out.isEmpty() && "call".equals(out.get(out.size() - 1).role())) out.remove(out.size() - 1);
        return out;
    }

    private ObjectNode sysInstruction() throws Exception {
        // NOTE: same chained-call trap as the Anthropic system block — hold the
        // wrapper explicitly so we return { "parts": [ {"text": …} ] }, not the
        // bare inner text node (that exact mistake produced Gemini HTTP 400s).
        ObjectNode si = M.createObjectNode();
        ObjectNode part = si.putArray("parts").addObject();
        part.put("text", systemPrompt());
        return si;
    }

    /** The assistant's identity + tool etiquette. Mirrors MCP_SERVER.md's safety model. */
    private String systemPrompt() {
        return """
                You are the InvoiceStudio Assistant, embedded inside the InvoiceStudio desktop billing \
                application (wholesale apparel business: buyers, suppliers, items/stock, invoices, purchases, \
                expenses, financials, thermal label printing on a TSC TA210, and a bill/label template designer). \
                You have direct tool access to the app's data through its MCP tools — use them to answer \
                questions with REAL data instead of guessing. Read tools (list_*, get_*, *_report) are safe \
                to call freely; mutating tools may return requiresConfirmation — tell the user to approve the \
                operation in Settings → MCP Server (or that you will wait for their approval). Answer in the \
                user's language, be concise, and format numbers as plain text (no markdown tables — the chat \
                pane renders plain text).""";
    }

    private Map<String, Object> parseArgs(String json) {
        try {
            Map<String, Object> raw = M.readValue(json == null || json.isBlank() ? "{}" : json,
                    M.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            return raw;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String shorten(String s) {
        if (s == null) return "";
        String flat = s.replaceAll("\\s+", " ");
        return flat.length() > 60 ? flat.substring(0, 60) + "…" : flat;
    }
}
