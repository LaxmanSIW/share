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

    /**
     * Result of one full send: the assistant's text + what tools it ran.
     *
     * <p>Token counts are the SUM over every provider request of the send
     * (router pass + each tool round), parsed from the provider's own usage
     * block — they show next to each reply so users can see what a question
     * really costs. {@code -1} means the provider did not report usage.</p>
     */
    public record ChatResult(String text, List<String> toolTrace,
                             long promptTokens, long completionTokens,
                             long totalTokens, long elapsedMs, String modelUsed) {
        /** Back-compatible constructor: no usage metadata. */
        public ChatResult(String text, List<String> toolTrace) {
            this(text, toolTrace, -1, -1, -1, -1, "");
        }
    }

    /**
     * Hard cap on model↔tool round trips per send (safety + cost bound).
     * @deprecated Replaced by {@link ChatbotConfig#getMaxToolCalls()} — this constant
     *             is kept only for Javadoc reference; the live value comes from settings.
     */
    @SuppressWarnings("unused")
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
        long t0 = System.currentTimeMillis();
        // Ollama runs locally and needs no key; every cloud provider does.
        if (cfg.getApiKey().isBlank() && !ChatbotConfig.OLLAMA.equals(cfg.getProvider())) {
            throw new IllegalStateException("No API key configured — open Settings → Chatbot and save your key.");
        }
        // Per-send config copy: quota failover switches the model INSIDE this
        // send. Mutating the shared persisted instance made the model chip and
        // Settings silently drift to a model the user never picked (they are
        // the same object) — the copy keeps the user's choice untouched.
        cfg = cfg.copyForSend();
        List<ChatTurn> turns = new ArrayList<>(history);
        turns.add(new ChatTurn("user", userText == null ? "" : userText, attachment));

        Usage usage = new Usage();
        // USER level = the log's turn separator: every new message opens a
        // clearly visible block in the execution log, so reading it top-down
        // tells exactly which request each step belongs to.
        ChatbotLogManager.user("New message: \"" + shorten(userText, 80) + "\"",
                attachment != null ? "Image attached (" + attachment.mimeType() + ", " + attachment.data().length + " bytes)" : null);

        // ── Token & request optimization pipeline ────────────────────
        // Each tool schema costs input tokens on EVERY request, and tool
        // rounds multiply requests. So:
        //   1. Zero-cost skip: greetings/smalltalk (regex) are answered
        //      LOCALLY — zero requests, zero tokens (works mid-chat).
        //   2. Smart routing: a tiny router request (tool NAMES only) picks
        //      the few tools this question needs; the heavy pass carries only
        //      those schemas (~7K → ~1K input tokens typically).
        //   3. If the model calls a tool outside the shortlist, escalate ONCE
        //      to the full catalogue (correctness net, rare).

        // ── MCP-off fast path ─────────────────────────────────────────
        // When the MCP server is stopped the assistant has no business-data
        // tools at all. Sending schemas anyway made the model ask for tools
        // that could never run (or hang on the old single-thread executor).
        // Instead: dispatch with ZERO schemas and a system note that tells
        // the model to point the user at Settings → MCP Server — the chat
        // always replies, instantly, with actionable guidance.
        boolean mcpOff = !com.invoicestudio.mcp.McpServer.isRunning();
        if (mcpOff) {
            ChatbotLogManager.warn("MCP server is OFF — replying without business-data tools",
                    "If the user asks for live data the model will direct them to Settings → MCP Server");
            ProviderResponse resp = dispatch(cfg, turns, java.util.Set.of(), true);
            usage.add(resp.promptTokens(), resp.completionTokens());
            logUsage(usage, "MCP-off reply");
            ChatbotLogManager.success("Completed (MCP off — conversational reply)", resp.text());
            return result(resp.text(), List.of(), usage, t0, activeModel(cfg));
        }

        java.util.Set<String> allowed = null;
        boolean confirmCtx = isConfirmationContext(turns, userText);
        if (confirmCtx) {
            ChatbotLogManager.info("Confirmation context detected", "Ensuring confirm_operation is available");
        }

        // ── Zero-cost local greetings (work MID-CHAT too) ─────────────
        // "hi", "hello", "thanks"… carry no business intent, but once any
        // history existed they fell through to the smart router and paid a
        // full flash-model round trip before the answer (the "even a simple
        // hi takes ages" report). They are now answered LOCALLY: zero
        // requests, zero tokens, instant — at any position in the chat.
        // Confirmation contexts and queued approvals still bypass the
        // shortcut so "yes/ok" flows and "reply yes to confirm" turns are
        // never eaten by the greeting matcher.
        String first = userText == null ? "" : userText.trim();
        if (attachment == null && !confirmCtx && CHAT_ONLY.matcher(first).matches()) {
            String canned = localChatReply(first);
            ChatbotLogManager.router("Instant local reply — no API call, no tokens", first);
            ChatbotLogManager.success("Completed locally (0 requests)", canned);
            return result(canned, List.of(), usage, t0, "local");
        }

        String firstModel = activeModel(cfg);

        if (cfg.isSmartRouting() && attachment == null) {
            ChatbotLogManager.router("Evaluating tools via smart router...", first);
            ToolRoute route = routeTools(cfg, turns, first, confirmCtx, usage);
            if (route != null && !route.needTools() && !confirmCtx) {
                // Router already answered the question conversationally —
                // no second request, no schemas, no tool rounds.
                ChatbotLogManager.router("Direct conversational answer from router", route.note());
                logUsage(usage, "Router direct answer");
                ChatbotLogManager.success("Completed via smart router direct answer", route.note());
                return result(route.note(), List.of(), usage, t0, activeModel(cfg));
            }
            if (route != null && !route.tools().isEmpty()) {
                allowed = new java.util.HashSet<>(route.tools());
                ChatbotLogManager.router("Shortlisted tools: " + allowed, null);
            } else {
                ChatbotLogManager.router("Router requested full catalogue or failed open", null);
            }
            if (confirmCtx) {
                if (allowed != null) {
                    allowed.add("confirm_operation");
                }
            }
        }

        List<String> trace = new ArrayList<>();
        boolean escalated = false;
        int round = 0;
        java.util.Set<String> exhausted = new java.util.HashSet<>();
        String originalModel = cfg.getModel();
        while (true) {
            if (round > cfg.getMaxToolCalls()) {
                ChatbotLogManager.warn("Safety limit reached: " + cfg.getMaxToolCalls() + " tool rounds", null);
                return new ChatResult("(stopped after " + cfg.getMaxToolCalls()
                        + " tool rounds — ask me to continue)", trace,
                        usage.prompt, usage.completion, usage.total(),
                        System.currentTimeMillis() - t0, cfg.getModel());
            }
            ProviderResponse resp;
            try {
                String activeModel = cfg.getModel().isBlank() ? defaultModel(cfg.getProvider()) : cfg.getModel();
                ChatbotLogManager.dispatch("Round " + (round + 1) + " -> " + cfg.getProvider()
                        + " (" + activeModel + ")",
                        "Turns in payload: " + turns.size() + ", Allowed tools: "
                        + (allowed == null ? "ALL (" + McpToolRegistry.tools().size() + ")" : allowed.toString()));
                resp = dispatch(cfg, turns, allowed, false);
            } catch (IllegalStateException ex) {
                ChatbotLogManager.error("Provider call failed: " + ex.getMessage(), null);
                String msg = String.valueOf(ex.getMessage());
                // ── Model-status memory (red/green dots) ─────────────────
                // Real usage is the only signal (no provider exposes a
                // balance API), so record the wall against the model that
                // hit it — the pickers show these as red dots until a
                // success flips them green or the daily window ages out.
                ModelStatusStore.markBlocked(cfg.getProvider(), firstModel, msg);
                // ── Quota / daily-limit failover ──────────────────────────────
                // When a Gemini model hits its daily free-tier bucket ("Daily
                // free-tier limit") or any provider returns a hard quota error,
                // iterate through failover candidates until one succeeds.
                // We only failover for Gemini because other providers don't share
                // a common failover catalogue — but we still give a friendly error.
                boolean isDailyLimit = msg.contains("Daily free-tier limit");
                boolean isGemini = ChatbotConfig.GEMINI.equals(cfg.getProvider());
                if (isDailyLimit && isGemini) {
                    // Auto-failover: this model's daily bucket is empty — try
                    // the next best chat model with remaining quota.
                    String currentModel = cfg.getModel().isBlank() ? defaultModel(cfg.getProvider()) : cfg.getModel();
                    exhausted.add(currentModel);
                    String next = ModelCatalog.nextFailover(currentModel, exhausted);
                    if (next != null) {
                        exhausted.add(next); // one attempt per candidate
                        ChatbotLogManager.warn(
                                "Model " + currentModel + " hit daily quota — trying " + next, null);
                        cfg.setModel(next); // per-send copy — the saved config is NOT touched
                        // Failover is FREE: it does not consume a tool round
                        // (round is only incremented after a real tool result
                        // comes back). The heavy in-flight MCP payloads are
                        // dropped too — the new model restarts from the last
                        // good conversational context + the original prompt,
                        // instead of replaying every tool call/result pair.
                        turns = trimForFailover(turns, cfg);
                        allowed = null;      // new model plans fresh, full catalogue
                        escalated = false;   // and may escalate once again
                        trace.add("⚠ model " + (originalModel.isBlank() ? "default" : originalModel)
                                + " hit its daily limit — switched to " + next);
                        logUsage(usage, "After failover to " + next);
                        continue;
                    }
                    throw new IllegalStateException(msg + "\nAll Gemini fallback models are also at their "
                            + "daily limits — wait for the daily reset or use another provider (Ollama is local & free).", ex);
                }
                throw ex;
            }
            usage.add(resp.promptTokens(), resp.completionTokens());
            if (resp.toolCalls().isEmpty()) {
                // The pair that actually answered — post-failover this is the
                // failover model, not the configured one. GREEN: a real
                // request just succeeded through it.
                String answeredBy = cfg.getModel().isBlank() ? defaultModel(cfg.getProvider()) : cfg.getModel();
                ModelStatusStore.markOk(cfg.getProvider(), answeredBy);
                ChatbotLogManager.success("Completed in " + round + " tool round(s)",
                        trace.isEmpty() ? "Direct response" : "Tools executed: " + String.join(" -> ", trace));
                logUsage(usage, "Final (" + round + " tool round(s), model "
                        + (cfg.getModel().isBlank() ? "default" : cfg.getModel()) + ")");
                return result(resp.text(), trace, usage, t0, activeModel(cfg));
            }
            // Router under-selected? (model called a tool that wasn't
            // shortlisted). Escalate once to the full catalogue and re-ask —
            // cheaper than always carrying every schema.
            if (allowed != null && !escalated) {
                final java.util.Set<String> currentAllowed = allowed;
                if (resp.toolCalls().stream().anyMatch(tc -> !currentAllowed.contains(tc.name()))) {
                    escalated = true;
                    allowed = null;
                    ChatbotLogManager.warn("Model requested tool outside shortlist -> escalating to full catalogue", null);
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
                ChatbotLogManager.tool("Model requested tool: " + tc.name(), "Args: " + tc.argumentsJson());
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
                long tTool0 = System.currentTimeMillis();
                try {
                    Object res = McpToolRegistry.call(tc.name(), parseArgs(tc.argumentsJson()));
                    out = M.writeValueAsString(res);
                } catch (Exception e) {
                    String err = String.valueOf(e.getMessage());
                    if (!com.invoicestudio.mcp.McpServer.isRunning()) {
                        err += " | hint: the MCP server is OFF — tell the user to start it in "
                                + "Settings → MCP Server, then ask again.";
                    }
                    out = M.writeValueAsString(Map.of("error", err));
                }
                long elapsed = System.currentTimeMillis() - tTool0;
                ChatbotLogManager.mcp("MCP " + tc.name() + " executed in " + elapsed + "ms",
                        "Result: " + shorten(out, 120));
                // Mirror into the MCP audit trail. Chatbot executions go
                // directly through McpToolRegistry (not the McpServer HTTP
                // endpoint), so without this the Settings → MCP Server audit
                // tab and mcp-audit.log never saw what the assistant did to
                // the user's books ("no logs in logview").
                boolean failed = out.startsWith("{\"error\"");
                com.invoicestudio.mcp.McpAuditLog.log((failed ? "[CHAT-ERROR] " : "[CHAT] ")
                        + tc.name() + " " + shorten(tc.argumentsJson(), 160)
                        + " — " + elapsed + "ms — " + shorten(out, 140));
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
            "(?is)^\\s*(hi+|hello+|hey+|yo|thanks?|thank\\s*you|thx|ty|great|nice|cool|wow|"
            + "good\\s*(morning|afternoon|evening|night)|bye+|goodbye|see\\s*ya|"
            + "who\\s+are\\s+you\\??|how\\s+are\\s+you\\??|what\\s+can\\s+you\\s+do\\??|help)\\s*[!.?]*\\s*$");

    /**
     * Deterministic local answers for pure-smalltalk messages — sent without
     * ANY provider request (instant, 0 tokens, works mid-chat). Package-private
     * static so tests can assert the exact copy. Kept free of randomness on
     * purpose: same input, same reply, verifiable.
     */
    static String localChatReply(String input) {
        String s = input == null ? "" : input.toLowerCase().replaceAll("\\s+", " ").trim();
        s = s.replaceAll("[!.?]+$", "").trim();
        if (s.matches("who\\s+are\\s+you")) {
            return "I'm the InvoiceStudio Assistant — built into your billing app. I work on your real books: "
                    + "buyers, suppliers, items & stock, invoices, purchases, expenses, reports and label "
                    + "printing. Try e.g. \"list top 5 buyers by balance\".";
        }
        if (s.matches("how\\s+are\\s+you")) {
            return "I'm running great, thanks! Ready when you are — invoices, stock, payments, reports.";
        }
        if (s.matches("what\\s+can\\s+you\\s+do") || s.equals("help")) {
            return "Here's what I can do:\n"
                    + "- Sales: create, pay and delete invoices; list & search bills\n"
                    + "- Directory: buyers, suppliers, transports (create/update/delete)\n"
                    + "- Catalog: items, categories, stock & reorder levels\n"
                    + "- Purchases & expenses: record bills, payments, vouchers\n"
                    + "- Reports: stock, profitability, financial summary, daybook\n"
                    + "- Templates: design/print labels & invoices, export PDFs\n"
                    + "- Knowledge Hub: how-to guides (try \"how do I design a label?\")\n\n"
                    + "Tip: type /new to drop earlier context and save tokens.";
        }
        if (s.matches("good (morning|afternoon|evening|night)")) {
            return "Good " + s.replace("good ", "") + "! Ready to help with invoices, stock, payments "
                    + "or reports — what do you need?";
        }
        if (s.matches("(hi+|hello+|hey+|yo)")) {
            return "Hello! I'm your InvoiceStudio assistant — I can manage invoices, buyers, suppliers, "
                    + "stock, expenses and reports with your live data. What would you like to do?";
        }
        if (s.matches("(thanks?|thank you|thx|ty)")) {
            return "You're welcome! Anything else — bills, stock, payments, reports?";
        }
        if (s.matches("(bye+|goodbye|see ya)")) {
            return "Goodbye! Ping me anytime you need an invoice, a stock check or a report.";
        }
        return "Glad you like it! Want me to pull a report, create a bill or check stock?";
    }

    /** Confirmation responses when an operation or prompt requires user approval or answer. */
    private static final java.util.regex.Pattern CONFIRMATION_WORDS = java.util.regex.Pattern.compile(
            "(?is)^\\s*(y|yes|yeah|yep|sure|ok(ay)?|confirm(ed)?|approve(d)?|proceed|go\\s*ahead|do\\s*it|please\\s*do|"
            + "yes\\s*(please|do|confirm|proceed|approve)|reject|deny|cancel|no)\\s*[!.?]*\\s*$");

    /**
     * Determines whether the current request is an affirmative or response to an
     * assistant confirmation request, or whether operations are pending approval.
     */
    static boolean isConfirmationContext(List<ChatTurn> turns, String userText) {
        if (turns == null || turns.isEmpty()) return false;
        String trimmed = userText == null ? "" : userText.trim();
        if (CONFIRMATION_WORDS.matcher(trimmed).matches()) {
            return true;
        }
        if (!com.invoicestudio.mcp.PendingOperations.pending().isEmpty()) {
            return true;
        }
        for (int i = turns.size() - 1; i >= 0; i--) {
            ChatTurn t = turns.get(i);
            if ("assistant".equals(t.role())) {
                String txt = t.text() == null ? "" : t.text().toLowerCase();
                if (txt.contains("confirm") || txt.contains("approve") || txt.contains("operationid")
                        || txt.contains("proceed") || txt.contains("pending") || txt.contains("waiting for")) {
                    return true;
                }
                break;
            }
        }
        return false;
    }

    /** Tool results larger than this are truncated before re-sending. */
    private static final int MAX_RESULT_CHARS = 4000;

    /** Router decision — visible to tests. */
    record ToolRoute(boolean needTools, List<String> tools, String note) {}

    /**
     * Emits one compact TOKENS log entry per stage. Everything logged here
     * was already computed (usage blocks ride along with every provider
     * response) — the tracing adds no extra network or CPU work.
     */
    private static void logUsage(Usage usage, String stage) {
        if (!usage.reported()) return; // provider sent no usage block — stay quiet
        ChatbotLogManager.usage("↑" + Math.max(usage.prompt, 0) + " ↓" + Math.max(usage.completion, 0)
                + " tok — total " + usage.total() + (stage == null || stage.isBlank() ? "" : "  (" + stage + ")"), null);
    }

    /** Builds the final ChatResult with the accumulated usage + wall time. */
    private static ChatResult result(String text, List<String> trace, Usage usage,
                                     long t0, String modelUsed) {
        return new ChatResult(text, trace, usage.prompt, usage.completion, usage.total(),
                System.currentTimeMillis() - t0, modelUsed == null ? "" : modelUsed);
    }

    /** The model that actually answers for this send (default filled in when blank). */
    private static String activeModel(ChatbotConfig cfg) {
        return cfg.getModel().isBlank() ? defaultModel(cfg.getProvider()) : cfg.getModel();
    }

    /**
     * Router pass: one lean request (no tool schemas — names only) that
     * either answers conversationally (CHAT) or shortlists the tools the
     * question needs (ROUTE). Supplies the immediately preceding assistant
     * turn so context/confirmation requests are never misclassified as small talk.
     * Returns null to fail OPEN (full catalogue) on any router error —
     * optimization must never cost correctness.
     */
    private ToolRoute routeTools(ChatbotConfig cfg, List<ChatTurn> turns, String userText,
                                 boolean confirmCtx, Usage usage) {
        try {
            StringBuilder names = new StringBuilder();
            for (McpToolRegistry.ToolDef t : McpToolRegistry.tools()) {
                if (names.length() > 0) names.append(", ");
                names.append(t.name);
            }
            String sys = "You are the request router of an InvoiceStudio billing app assistant. "
                    + "Available tools: " + names + ". \n"
                    + "Decide if the user's LATEST message needs live business data via tools or is confirming/approving an operation.\n"
                    + "Reply with EXACTLY one line and nothing else:\n"
                    + "- If user is confirming, approving, or rejecting an operation: ROUTE: confirm_operation\n"
                    + "- If business data or actions are needed: ROUTE: tool1, tool2 (fewest matching names from the list)\n"
                    + "- Questions about how to use InvoiceStudio itself: ROUTE: get_app_guide\n"
                    + "- Otherwise (small talk, greetings, general knowledge): CHAT: <answer the user briefly>";

            String query;
            if (turns != null && turns.size() >= 2) {
                // Supply the immediately preceding assistant turn so the router has the conversation context
                ChatTurn prev = turns.get(turns.size() - 2);
                query = "Previous assistant message: " + shorten(prev.text(), 240) + "\nUser reply: " + userText;
            } else {
                query = userText;
            }

            RawAnswer raw = rawCompletion(cfg, sys, query);
            if (raw == null || raw.text() == null) return null;
            usage.add(raw.promptTokens(), raw.completionTokens());
            return parseRouteDecision(raw.text(), cfg);
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
    private RawAnswer rawCompletion(ChatbotConfig cfg, String system, String userText) throws Exception {
        return switch (cfg.getProvider()) {
            case ChatbotConfig.GEMINI -> rawGemini(cfg, system, userText);
            default -> rawOpenAiCompatible(cfg, system, userText);
        };
    }

    /** Router answer + the usage block of that single lean request. */
    private record RawAnswer(String text, long promptTokens, long completionTokens) {}

    private RawAnswer rawGemini(ChatbotConfig cfg, String system, String userText) throws Exception {
        // The router always runs on the LIGHTEST flash model: it only has to
        // classify the request (or answer small talk), it never touches
        // business data — so it must not burn the user's chosen (possibly
        // paid/scarce) model quota and adds minimal latency.
        String model = "gemini-flash-lite-latest";
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
        return new RawAnswer(
                root.path("candidates").path(0).path("content").path("parts").path(0)
                        .path("text").asText(""),
                root.path("usageMetadata").path("promptTokenCount").asLong(-1),
                root.path("usageMetadata").path("candidatesTokenCount").asLong(-1));
    }

    private RawAnswer rawOpenAiCompatible(ChatbotConfig cfg, String system, String userText) throws Exception {
        // Same light-router policy as Gemini: Z.ai's glm-4.5-flash tier is
        // free (live-verified with a real key) — the router must never burn
        // the user's chosen (possibly metered) GLM model on classification.
        String model = switch (cfg.getProvider()) {
            case ChatbotConfig.GLM -> "glm-4.5-flash";
            default -> cfg.getModel().isBlank() ? defaultModel(cfg.getProvider()) : cfg.getModel();
        };
        String url = switch (cfg.getProvider()) {
            case ChatbotConfig.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions";
            case ChatbotConfig.GROQ -> "https://api.groq.com/openai/v1/chat/completions";
            case ChatbotConfig.OLLAMA -> "http://localhost:11434/v1/chat/completions";
            case ChatbotConfig.MISTRAL -> "https://api.mistral.ai/v1/chat/completions";
            case ChatbotConfig.DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions";
            case ChatbotConfig.GLM -> GLM_ENDPOINT;
            default -> cfg.getEndpoint().isBlank()
                    ? "https://api.openai.com/v1/chat/completions" : cfg.getEndpoint();
        };
        if (url == null || url.isBlank()) throw new IllegalStateException("Custom provider needs an endpoint.");
        ObjectNode body = M.createObjectNode();
        body.put("model", model);
        body.putArray("messages").add(M.createObjectNode().put("role", "system").put("content", system))
                .add(M.createObjectNode().put("role", "user").put("content", userText));
        JsonNode root = post(url, "POST", body, Map.of("Authorization", "Bearer " + cfg.getApiKey()));
        return new RawAnswer(
                root.path("choices").path(0).path("message").path("content").asText(""),
                root.path("usage").path("prompt_tokens").asLong(-1),
                root.path("usage").path("completion_tokens").asLong(-1));
    }

    /**
     * Routes to the right provider implementation with the allowed-tool filter.
     *
     * @param mcpOff when true the request carries zero tool schemas and the
     *               system instruction explains how to re-enable tools —
     *               used when the MCP server is stopped so the model can
     *               still answer (and point at Settings → MCP Server).
     */
    private ProviderResponse dispatch(ChatbotConfig cfg, List<ChatTurn> turns,
                                      java.util.Set<String> allowed, boolean mcpOff) throws Exception {
        return switch (cfg.getProvider()) {
            case ChatbotConfig.OPENAI -> callOpenAiCompatible(cfg, turns,
                    "https://api.openai.com/v1/chat/completions", Map.of(), allowed, mcpOff);
            case ChatbotConfig.ANTHROPIC -> callAnthropic(cfg, turns, allowed, mcpOff);
            case ChatbotConfig.OPENROUTER -> callOpenAiCompatible(cfg, turns,
                    "https://openrouter.ai/api/v1/chat/completions", Map.of(), allowed, mcpOff);
            case ChatbotConfig.GROQ -> callOpenAiCompatible(cfg, turns,
                    "https://api.groq.com/openai/v1/chat/completions", Map.of(), allowed, mcpOff);
            case ChatbotConfig.OLLAMA -> callOpenAiCompatible(cfg, turns,
                    "http://localhost:11434/v1/chat/completions", Map.of(), allowed, mcpOff);
            case ChatbotConfig.MISTRAL -> callOpenAiCompatible(cfg, turns,
                    "https://api.mistral.ai/v1/chat/completions", Map.of(), allowed, mcpOff);
            case ChatbotConfig.DEEPSEEK -> callOpenAiCompatible(cfg, turns,
                    "https://api.deepseek.com/v1/chat/completions", Map.of(), allowed, mcpOff);
            case ChatbotConfig.GLM -> callOpenAiCompatible(cfg, turns, GLM_ENDPOINT, Map.of(), allowed, mcpOff);
            case ChatbotConfig.CUSTOM -> callOpenAiCompatible(cfg, turns, "", Map.of(), allowed, mcpOff);
            default -> callGemini(cfg, turns, allowed, mcpOff);
        };
    }

    /** Z.ai (GLM) OpenAI-compatible chat endpoint — free flash tier by default. */
    public static final String GLM_ENDPOINT = "https://api.z.ai/api/paas/v4/chat/completions";

    /**
     * Provider default model when the user left the model field blank.
     *
     * <p><b>Light-by-default policy:</b> Gemini defaults to the flash-LITE
     * alias — the lightest chat-capable tier — so everyday questions never
     * burn the scarcer flash/pro request quotas. Heavier models remain one
     * pick away in Settings → Chatbot / the header model menu.</p>
     */
    public static String defaultModel(String provider) {
        return switch (provider) {
            case ChatbotConfig.OPENAI -> "gpt-4o-mini";
            case ChatbotConfig.ANTHROPIC -> "claude-sonnet-4-5";
            case ChatbotConfig.OPENROUTER -> "openai/gpt-4o-mini";
            case ChatbotConfig.GROQ -> "llama-3.3-70b-versatile";
            case ChatbotConfig.OLLAMA -> "llama3.1";
            case ChatbotConfig.MISTRAL -> "mistral-large-latest";
            case ChatbotConfig.DEEPSEEK -> "deepseek-chat";
            case ChatbotConfig.GLM -> "glm-4.5-flash"; // free tier on Z.ai
            case ChatbotConfig.CUSTOM -> "";
            // "flash-lite-latest" is Google's stable alias for the current
            // light flash model — immune to per-version retirements (the
            // gemini-2.0-flash 404 was live-verified) and listed for this key.
            default -> "gemini-flash-lite-latest";
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
            case ChatbotConfig.GLM -> "Z.ai GLM (free flash tier)";
            case ChatbotConfig.CUSTOM -> "Custom OpenAI-compatible…";
            default -> "Google Gemini";
        };
    }

    // ─────────────────────────────────────────────────────────────────
    // Gemini
    // ─────────────────────────────────────────────────────────────────

    private ProviderResponse callGemini(ChatbotConfig cfg, List<ChatTurn> turns,
                                        java.util.Set<String> allowed, boolean mcpOff) throws Exception {
        String model = cfg.getModel().isBlank() ? defaultModel(ChatbotConfig.GEMINI) : cfg.getModel();
        String url = (cfg.getEndpoint().isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta/models/" : cfg.getEndpoint())
                + model + ":generateContent?key=" + cfg.getApiKey();

        ObjectNode body = M.createObjectNode();
        body.set("system_instruction", sysInstruction(mcpOff));
        body.set("contents", geminiContents(cfg, turns));
        // Zero-schema fast paths (smalltalk / MCP-off) omit the tools field
        // entirely — some Gemini versions reject an EMPTY functionDeclarations
        // array with HTTP 400, and an omitted field is the cleanest "no tools".
        if (allowed == null || !allowed.isEmpty()) {
            body.set("tools", geminiTools(allowed));
        }

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
                    String id = fc.hasNonNull("id") ? fc.path("id").asText()
                            : (p.hasNonNull("id") ? p.path("id").asText() : "");
                    // thoughtSignature lives on the PART, beside functionCall —
                    // not inside it (live-verified 400 without it).
                    calls.add(new ToolCall(fc.path("name").asText(),
                            M.writeValueAsString(fc.path("args")), id,
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
        return new ProviderResponse(text.toString(), calls,
                root.path("usageMetadata").path("promptTokenCount").asLong(-1),
                root.path("usageMetadata").path("candidatesTokenCount").asLong(-1));
    }

    private ArrayNode geminiContents(ChatbotConfig cfg, List<ChatTurn> turns) throws Exception {
        ArrayNode contents = M.createArrayNode();
        List<ChatTurn> prepared = prepareTurns(turns, cfg);

        // Gemini strictly requires:
        // 1. The first turn MUST have role "user".
        // 2. Alternating turns between user and model (consecutive turns of same role merged).
        // 3. Every functionCall turn (model) must be followed by a functionResponse turn (user).
        String lastRole = null;
        ObjectNode currentContent = null;
        ArrayNode currentParts = null;

        for (ChatTurn t : prepared) {
            String role = switch (t.role()) {
                case "assistant", "call" -> "model";
                default -> "user";
            };

            if (currentContent == null || !role.equals(lastRole)) {
                currentContent = contents.addObject();
                currentContent.put("role", role);
                currentParts = currentContent.putArray("parts");
                lastRole = role;
            }

            if ("call".equals(t.role())) {
                JsonNode cs = M.readTree(t.text());
                for (JsonNode c2 : cs) {
                    ObjectNode fc = currentParts.addObject();
                    JsonNode args = M.readTree(c2.path("args").asText("{}"));
                    ObjectNode call = M.createObjectNode()
                            .put("name", c2.path("name").asText())
                            .set("args", args);
                    if (c2.hasNonNull("id") && !c2.path("id").asText().isBlank()) {
                        call.put("id", c2.path("id").asText());
                    }
                    fc.set("functionCall", call);
                    String tSig = c2.path("tSig").asText("");
                    if (!tSig.isBlank()) fc.put("thoughtSignature", tSig);
                }
            } else if ("tool".equals(t.role())) {
                JsonNode rs = M.readTree(t.text());
                for (JsonNode r : rs) {
                    ObjectNode fr = currentParts.addObject();
                    ObjectNode frObj = M.createObjectNode()
                            .put("name", r.path("name").asText());
                    if (r.hasNonNull("id") && !r.path("id").asText().isBlank()) {
                        frObj.put("id", r.path("id").asText());
                    }
                    frObj.set("response", M.createObjectNode().set("result", M.readTree(r.path("result").asText())));
                    fr.set("functionResponse", frObj);
                }
            } else {
                if (t.image() != null) {
                    ObjectNode id = currentParts.addObject();
                    id.set("inline_data", M.createObjectNode()
                            .put("mime_type", t.image().mimeType())
                            .put("data", java.util.Base64.getEncoder().encodeToString(t.image().data())));
                }
                if (t.text() != null && !t.text().isEmpty()) {
                    currentParts.addObject().put("text", t.text());
                } else if (t.image() != null) {
                    currentParts.addObject().put("text", "Please analyze this image.");
                }
            }
        }
        return contents;
    }

    private ArrayNode geminiTools(java.util.Set<String> allowed) {
        ArrayNode fns = M.createArrayNode();
        for (McpToolRegistry.ToolDef t : McpToolRegistry.tools()) {
            // allowed == null → full catalogue; empty set → ZERO tools
            // (smalltalk / MCP-off fast paths). Non-empty → shortlist only.
            if (allowed != null && !allowed.contains(t.name)) continue;
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
                                                  java.util.Set<String> allowed, boolean mcpOff) throws Exception {
        String model = cfg.getModel().isBlank() ? defaultModel(cfg.getProvider()) : cfg.getModel();
        String url = cfg.getEndpoint().isBlank() ? defaultUrl : cfg.getEndpoint();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Custom provider needs an endpoint — set it in Settings → Chatbot " +
                    "(e.g. https://your-host/v1/chat/completions).");
        }

        ObjectNode body = M.createObjectNode();
        body.put("model", model);
        ArrayNode msgs = body.putArray("messages");
        msgs.add(M.createObjectNode().put("role", "system").put("content", systemPrompt(mcpOff)));
        for (ChatTurn t : prepareTurns(turns, cfg)) {
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
        // Zero-schema fast paths omit the tools field entirely (empty tools
        // arrays are rejected by some providers with HTTP 400).
        if (allowed == null || !allowed.isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (McpToolRegistry.ToolDef t : McpToolRegistry.tools()) {
                // allowed == null → full catalogue; empty set → ZERO tools.
                if (allowed != null && !allowed.contains(t.name)) continue;
                ObjectNode fn = tools.addObject().put("type", "function").putObject("function");
                fn.put("name", t.name);
                fn.put("description", t.description);
                fn.set("parameters", M.valueToTree(t.inputSchema));
            }
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
        return new ProviderResponse(text, calls,
                root.path("usage").path("prompt_tokens").asLong(-1),
                root.path("usage").path("completion_tokens").asLong(-1));
    }

    // ─────────────────────────────────────────────────────────────────
    // Anthropic
    // ─────────────────────────────────────────────────────────────────

    private ProviderResponse callAnthropic(ChatbotConfig cfg, List<ChatTurn> turns,
                                           java.util.Set<String> allowed, boolean mcpOff) throws Exception {
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
        sysItem.put("type", "text").put("text", systemPrompt(mcpOff));
        body.set("system", sysBlock.get("content"));
        ArrayNode msgs = body.putArray("messages");
        for (ChatTurn t : prepareTurns(turns, cfg)) {
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
        // Zero-schema fast paths omit the tools field entirely (empty tools
        // arrays are rejected by some providers with HTTP 400).
        if (allowed == null || !allowed.isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (McpToolRegistry.ToolDef t : McpToolRegistry.tools()) {
                // allowed == null → full catalogue; empty set → ZERO tools.
                if (allowed != null && !allowed.contains(t.name)) continue;
                ObjectNode f = tools.addObject();
                f.put("name", t.name);
                f.put("description", t.description);
                f.set("input_schema", M.valueToTree(t.inputSchema));
            }
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
        return new ProviderResponse(text, calls,
                root.path("usage").path("input_tokens").asLong(-1),
                root.path("usage").path("output_tokens").asLong(-1));
    }

    // ─────────────────────────────────────────────────────────────────
    // Shared plumbing
    // ─────────────────────────────────────────────────────────────────

    private record ToolCall(String name, String argumentsJson, String id, String thoughtSignature) {}

    /**
     * One provider answer + the usage block it carried. Usage parsing is
     * per-provider ({@code usageMetadata} for Gemini, {@code usage} for the
     * OpenAI-compatible shape, {@code usage} for Anthropic) and defaults to
     * -1 when the provider omits it — totals simply stay hidden in the UI.
     */
    private record ProviderResponse(String text, List<ToolCall> toolCalls,
                                    long promptTokens, long completionTokens) {
        ProviderResponse(String text, List<ToolCall> toolCalls) { this(text, toolCalls, -1, -1); }
    }

    /** Running token totals for one send (accumulated across all requests). */
    private static final class Usage {
        long prompt = -1;   // -1 = nothing reported yet
        long completion = -1;
        void add(long p, long c) {
            if (p >= 0)  prompt     = Math.max(prompt, 0) + p;
            if (c >= 0)  completion = Math.max(completion, 0) + c;
        }
        long total() {
            long p = Math.max(prompt, 0), c = Math.max(completion, 0);
            return (prompt < 0 && completion < 0) ? -1 : p + c;
        }
        boolean reported() { return prompt >= 0 || completion >= 0; }
    }

    private JsonNode post(String url, String method, ObjectNode body,
                          Map<String, String> headers) throws Exception {
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(body)));
        headers.forEach(rb::header);
        String payloadChars = null;
        try {
            payloadChars = String.valueOf(rb.build().bodyPublisher().orElseThrow().contentLength());
        } catch (Exception ignored) { }
        HttpResponse<String> resp = null;
        long httpStart = System.currentTimeMillis();
        // Transient provider failures (5xx overload, per-minute 429 bursts) get
        // a short retry ladder. DAILY quota exhaustion (free tier: 20 req/day
        // per model) is NOT retryable — fail fast with an honest message.
        for (int attempt = 0; ; attempt++) {
            resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            boolean dailyQuota = resp.statusCode() == 429
                    && resp.body() != null && (resp.body().contains("PerDay")
                        // Z.ai: a model outside the key's plan/balance is NOT
                        // transient — retrying just wastes ~9 s of the user's
                        // time before the honest error (live-verified).
                        || resp.body().contains("Insufficient balance"));
            boolean retryable = (resp.statusCode() == 429 || resp.statusCode() >= 500) && !dailyQuota;
            if (!retryable || attempt >= 2) break;
            long delayMs = 3000L * (attempt + 1); // 3s, 6s
            ChatbotLogManager.warn("Provider returned HTTP " + resp.statusCode()
                    + " (retry " + (attempt + 1) + "/2 in " + delayMs + "ms)", null);
            try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // One trace line per real HTTP request: bytes up, status, wall time.
        // Pure bookkeeping on data already in hand — no measurable cost.
        ChatbotLogManager.http("HTTP " + resp.statusCode() + " in "
                + (System.currentTimeMillis() - httpStart) + "ms"
                + (payloadChars != null ? " — payload " + payloadChars + " chars" : ""), null);
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
        // Z.ai balance wall (live-verified): newer GLM tiers need a paid plan —
        // the hint must point at a FREE model, not a vague "rate limit".
        if (rawBody != null && rawBody.contains("Insufficient balance")) {
            return "This GLM model needs balance/plan on your Z.ai key. Pick the free tier "
                    + "(glm-4.5-flash — it is the default) or another model in Settings → Chatbot, "
                    + "or add balance in your Z.ai console.";
        }
        String hint = switch (status) {
            case 401, 403 -> " — check the API key in Settings → Chatbot.";
            case 404 -> " — check the model name / endpoint in Settings → Chatbot.";
            case 429 -> " — rate limit reached; wait a moment or reduce history window.";
            default -> "";
        };
        return "Provider error (HTTP " + status + "): " + detail + hint;
    }

    /**
     * Prepares the conversation turns for API transmission.
     * Trims older prior history to the configured message window while keeping
     * the entire CURRENT interaction (the latest user turn and all in-flight tool
     * calls and responses) completely intact.
     * Ensures the conversation always begins with a user turn and contains
     * no orphaned call/tool turns at the edges.
     */
    static List<ChatTurn> prepareTurns(List<ChatTurn> turns, ChatbotConfig cfg) {
        if (turns == null || turns.isEmpty()) return List.of();

        // Find the index of the latest user turn (the start of the current request)
        int currentTurnIndex = -1;
        for (int i = turns.size() - 1; i >= 0; i--) {
            if ("user".equals(turns.get(i).role())) {
                currentTurnIndex = i;
                break;
            }
        }

        if (currentTurnIndex < 0) {
            // Fallback: sanitize turns so it starts with user
            List<ChatTurn> out = new ArrayList<>(turns);
            while (!out.isEmpty() && !"user".equals(out.get(0).role())) out.remove(0);
            return out;
        }

        List<ChatTurn> priorHistory = new ArrayList<>(turns.subList(0, currentTurnIndex));
        List<ChatTurn> currentTurn = new ArrayList<>(turns.subList(currentTurnIndex, turns.size()));

        int keep = cfg.getHistoryMessages();
        if (priorHistory.size() > keep) {
            priorHistory = new ArrayList<>(priorHistory.subList(priorHistory.size() - keep, priorHistory.size()));
        }

        // Prior history must begin with a user turn (never assistant, call or tool)
        while (!priorHistory.isEmpty() && !"user".equals(priorHistory.get(0).role())) {
            priorHistory.remove(0);
        }
        // Prior history must not end with an incomplete call turn
        while (!priorHistory.isEmpty() && "call".equals(priorHistory.get(priorHistory.size() - 1).role())) {
            priorHistory.remove(priorHistory.size() - 1);
        }

        List<ChatTurn> combined = new ArrayList<>(priorHistory);
        combined.addAll(currentTurn);

        // Final safety sanitization: must start with user, must not end with dangling call
        while (!combined.isEmpty() && !"user".equals(combined.get(0).role())) {
            combined.remove(0);
        }
        while (!combined.isEmpty() && "call".equals(combined.get(combined.size() - 1).role())) {
            combined.remove(combined.size() - 1);
        }

        return combined;
    }

    /**
     * Slims the conversation for a post-quota-failover request on a NEW model.
     *
     * <p>The failed model may have accumulated many call/tool turn pairs —
     * each pairing re-sends the full MCP tool result payloads (up to
     * {@link #MAX_RESULT_CHARS} chars per result) as input tokens. Replaying
     * all of that into a fresh model burns quota and adds latency for context
     * the new model never asked for. Instead we keep:</p>
     * <ul>
     *   <li>the tail of the prior conversation (user + assistant TEXT only,
     *       i.e. the last correct responses), trimmed to the configured
     *       history window, and</li>
     *   <li>the current interaction from the user's original prompt onward —
     *       with every in-flight call/tool payload dropped.</li>
     * </ul>
     * The new model then re-plans the tool work itself, from the prompt.
     */
    static List<ChatTurn> trimForFailover(List<ChatTurn> turns, ChatbotConfig cfg) {
        if (turns == null || turns.isEmpty()) return turns;
        int lastUser = -1;
        for (int i = turns.size() - 1; i >= 0; i--) {
            if ("user".equals(turns.get(i).role())) { lastUser = i; break; }
        }
        if (lastUser < 0) return turns;

        List<ChatTurn> prior = new ArrayList<>();
        for (ChatTurn t : turns.subList(0, lastUser)) {
            if ("user".equals(t.role()) || "assistant".equals(t.role())) prior.add(t);
        }
        int keep = cfg.getHistoryMessages();
        if (prior.size() > keep) {
            prior = new ArrayList<>(prior.subList(prior.size() - keep, prior.size()));
        }
        List<ChatTurn> out = new ArrayList<>(prior);
        // Keep ONLY the user's original prompt — every in-flight call/tool
        // payload turn that the failed model accumulated is dropped.
        out.add(turns.get(lastUser));
        return out;
    }

    private ObjectNode sysInstruction() throws Exception {
        return sysInstruction(false);
    }

    private ObjectNode sysInstruction(boolean mcpOff) throws Exception {
        // NOTE: same chained-call trap as the Anthropic system block — hold the
        // wrapper explicitly so we return { "parts": [ {"text": …} ] }, not the
        // bare inner text node (that exact mistake produced Gemini HTTP 400s).
        ObjectNode si = M.createObjectNode();
        ObjectNode part = si.putArray("parts").addObject();
        part.put("text", systemPrompt(mcpOff));
        return si;
    }

    /** The assistant's identity + tool etiquette. Mirrors MCP_SERVER.md's safety model. */
    private String systemPrompt() {
        return systemPrompt(false);
    }

    /** The assistant's identity + tool etiquette. Mirrors MCP_SERVER.md's safety model. */
    private String systemPrompt(boolean mcpOff) {
        if (mcpOff) {
            return """
                    You are the InvoiceStudio Assistant, embedded inside the InvoiceStudio desktop billing \
                    application. The InvoiceStudio MCP tool server is currently OFF, so you have NO \
                    business-data tools in this conversation — do not pretend to look anything up and do \
                    not invent numbers. Answer general conversation normally (greetings, explanations, \
                    billing advice, template/design help). Whenever the user asks for live business data \
                    (bills, buyers, suppliers, items, stock, purchases, expenses, reports, printing) or \
                    any action on their records, politely explain that the MCP server is switched off and \
                    tell them exactly: please start the MCP server by going to Settings → MCP Server and \
                    turning it on (Auto-start keeps it on). After they start it, they can ask again. \
                    Answer in the user's language and be concise.""";
        }
        String base = """
                You are the InvoiceStudio Assistant, embedded inside the InvoiceStudio desktop billing \
                application (wholesale apparel business: buyers, suppliers, items/stock, invoices, purchases, \
                expenses, financials, thermal label printing on a TSC TA210, and a bill/label template designer). \
                You have direct tool access to the app's data through its MCP tools — use them to answer \
                questions with REAL data instead of guessing. Read tools (list_*, get_*, *_report) are safe \
                to call freely; mutating tools may return requiresConfirmation with an operationId — explain the \
                action and operationId to the user and ask for their confirmation. When the user confirms \
                (e.g. says yes, confirm, approve, proceed, ok), immediately call the confirm_operation tool \
                with the operationId and approve=true to execute the action. If the user cancels or rejects, \
                call confirm_operation with approve=false. \
                When presenting tabular data, lists of records, metrics, or comparisons (e.g. buyers, stock, \
                items, bills, purchases, expenses, profitability, reports), ALWAYS format them in clean GitHub-Flavored \
                Markdown tables (| Column 1 | Column 2 | ...) with clear headers and aligned figures (e.g. ₹1,250.00). \
                Use Markdown bold (**...**), bullet points, and section headers (###) to make answers structured, \
                refined, and easily readable. Answer in the user's language and be concise.""";
        return withPendingApprovals(base);
    }

    /**
     * When destructive operations are queued for approval, spell them out in
     * the system prompt (id + tool + summary). Without this the model only
     * knew the operationId if it happened to be in visible history — it
     * guessed one, got "unknown operationId" back, and burned rounds (the
     * "asked to delete, it got stuck" report). Now a simple "yes" resolves
     * in exactly one tool round.
     */
    static String withPendingApprovals(String base) {
        List<com.invoicestudio.mcp.PendingOperations.PendingOp> ops =
                com.invoicestudio.mcp.PendingOperations.pending();
        if (ops == null || ops.isEmpty()) return base;
        StringBuilder sb = new StringBuilder(base)
                .append("\n\nPENDING APPROVALS — these queued operations are waiting for the user's yes/no:");
        for (com.invoicestudio.mcp.PendingOperations.PendingOp op : ops) {
            sb.append("\n- operationId=").append(op.getId())
                    .append(" | tool=").append(op.getTool())
                    .append(" | ").append(op.getSummary());
        }
        sb.append("\nIf the user just approved (yes/ok/confirm), call confirm_operation with approve=true and the ")
            .append("operationId above — nothing else. If they refused, call it with approve=false. ")
            .append("Do NOT call the underlying tool again — it would queue a duplicate.");
        return sb.toString();
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
        return shorten(s, 60);
    }

    private String shorten(String s, int maxChars) {
        if (s == null) return "";
        String flat = s.replaceAll("\\s+", " ");
        return flat.length() > maxChars ? flat.substring(0, maxChars) + "…" : flat;
    }
}
