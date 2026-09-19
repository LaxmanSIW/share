package com.invoicestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.invoicestudio.AppDirs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent chat transcript for the assistant panel.
 *
 * <p>Why this exists: the chatbot overlay is torn down and rebuilt on every
 * open/close ({@code StudioApp.toggleChatbot} constructs a fresh
 * {@code ChatbotPanel}), and the panel's history list died with it — closing
 * the chat wiped the conversation. The explicit trash button already exists
 * for users who WANT a clean slate, so persistence is the correct default:
 * reopening the chat restores the previous conversation, and only the trash
 * button (or a "/new" thread reset) clears it.</p>
 *
 * <p>Same storage posture as the rest of the chatbot's local files
 * ({@code chatbot.json}, {@code api-vault.json}): plain per-user JSON in the
 * app data dir, machine-local, rewritten on every append. Turns are stored
 * as plain {role, text} pairs — attached images are conversation UI, not
 * transcript content, and are not persisted (the user saw the image when it
 * was sent; the model's answer is what needs to survive a restart).</p>
 *
 * <p>Thread-safety: all static state guarded on a single monitor.</p>
 */
public final class ChatTranscriptStore {

    /** One persisted conversation turn (role: "user" | "assistant"). */
    public record TranscriptTurn(String role, String text) {}

    private static final String FILE_NAME = "chat-transcript.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final int MAX_TURNS = 400; // hard cap: bounded file, bounded memory
    private static final Object LOCK = new Object();

    private ChatTranscriptStore() {}

    // ── Read / write ──────────────────────────────────────────────────

    /** All persisted turns, oldest first (empty when none / unreadable). */
    public static List<TranscriptTurn> load() {
        synchronized (LOCK) {
            try {
                File f = file();
                if (f.exists()) {
                    TranscriptTurn[] arr = MAPPER.readValue(f, TranscriptTurn[].class);
                    List<TranscriptTurn> out = new ArrayList<>();
                    for (TranscriptTurn t : arr) {
                        if (t != null && t.role() != null && t.text() != null
                                && ("user".equals(t.role()) || "assistant".equals(t.role()))) {
                            out.add(t);
                        }
                    }
                    return out;
                }
            } catch (Exception ignored) {
                AppLog.debug(new Exception("chat-transcript.json unreadable — starting with an empty transcript", ignored));
            }
            return new ArrayList<>();
        }
    }

    /** Appends one turn and persists. Only user/assistant roles are kept. */
    public static void append(String role, String text) {
        if (role == null || text == null) return;
        if (!"user".equals(role) && !"assistant".equals(role)) return;
        synchronized (LOCK) {
            List<TranscriptTurn> turns = load();
            turns.add(new TranscriptTurn(role, text));
            while (turns.size() > MAX_TURNS) {
                turns.remove(0);
            }
            persist(turns);
        }
    }

    /** Explicit clear only (trash button / "/new"). Never called on close. */
    public static void clear() {
        synchronized (LOCK) {
            persist(new ArrayList<>());
        }
    }

    private static void persist(List<TranscriptTurn> turns) {
        try {
            MAPPER.writeValue(file(), turns);
        } catch (Exception ignored) {
            AppLog.debug(ignored);
        }
    }

    private static File file() {
        return AppDirs.dataDir().resolve(FILE_NAME).toFile();
    }
}
