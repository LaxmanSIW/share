package com.invoicestudio.service;

import com.invoicestudio.AppDirs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chat transcript persistence: append/load roundtrip, explicit clear, role
 * validation, cap enforcement and corrupt-file tolerance. Runs against an
 * isolated temp data dir so it never touches a real user transcript.
 */
class ChatTranscriptStoreTest {

    private static final String FILE = "chat-transcript.json";
    private static Path overrideDir;

    @BeforeAll
    static void redirectDataDir() throws Exception {
        overrideDir = Files.createTempDirectory("chat-transcript-test");
        System.setProperty("invoicestudio.data.dir", overrideDir.toString());
    }

    @AfterAll
    static void clearOverride() {
        System.clearProperty("invoicestudio.data.dir");
    }

    @AfterEach
    void cleanFile() throws Exception {
        Files.deleteIfExists(AppDirs.dataDir().resolve(FILE));
    }

    @Test
    void appendAndLoadRoundtripsToDisk() {
        ChatTranscriptStore.append("user", "Top 5 buyers by outstanding");
        ChatTranscriptStore.append("assistant", "Here are your top 5 buyers…");

        // A FRESH reader (new open of the chat, even after app restart) sees it.
        List<ChatTranscriptStore.TranscriptTurn> turns = ChatTranscriptStore.load();
        assertEquals(2, turns.size());
        assertEquals("user", turns.get(0).role());
        assertEquals("Top 5 buyers by outstanding", turns.get(0).text());
        assertEquals("assistant", turns.get(1).role());
    }

    @Test
    void loadOnEmptyStoreReturnsEmptyListNotCrash() {
        assertTrue(ChatTranscriptStore.load().isEmpty());
    }

    @Test
    void clearEmptiesTheTranscript() {
        ChatTranscriptStore.append("user", "hello");
        ChatTranscriptStore.clear();
        assertTrue(ChatTranscriptStore.load().isEmpty());
    }

    @Test
    void nonConversationRolesAreRejected() {
        ChatTranscriptStore.append("call", "{\"name\":\"tool\"}");
        ChatTranscriptStore.append(null, "no role");
        ChatTranscriptStore.append("user", null);
        assertTrue(ChatTranscriptStore.load().isEmpty());
    }

    @Test
    void corruptFileIsToleratedAsEmpty() throws Exception {
        Files.write(AppDirs.dataDir().resolve(FILE), "{not json".getBytes());
        assertTrue(ChatTranscriptStore.load().isEmpty());
        // And the store keeps working afterwards.
        ChatTranscriptStore.append("user", "still works");
        assertEquals(1, ChatTranscriptStore.load().size());
    }

    @Test
    void transcriptIsCappedAtBoundedSize() {
        for (int i = 0; i < 450; i++) {
            ChatTranscriptStore.append("user", "msg " + i);
        }
        List<ChatTranscriptStore.TranscriptTurn> turns = ChatTranscriptStore.load();
        assertEquals(400, turns.size());
        // Oldest dropped, newest kept.
        assertEquals("msg 449", turns.get(turns.size() - 1).text());
    }
}
