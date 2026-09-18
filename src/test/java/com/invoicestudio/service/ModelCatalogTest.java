package com.invoicestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for the live model catalogue filter and failover ordering. */
class ModelCatalogTest {

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void chatTextModelsPass_ttsImageEmbedLiveAreFiltered() throws Exception {
        String raw = """
                [
                  {"name":"models/gemini-3.8-flash","displayName":"Gemini 3.8 Flash",
                   "supportedGenerationMethods":["generateContent","countTokens"],"inputTokenLimit":1048576},
                  {"name":"models/gemini-2.5-flash-tts","displayName":"Gemini 2.5 Flash TTS",
                   "supportedGenerationMethods":["generateContent"],"inputTokenLimit":8192},
                  {"name":"models/gemini-3.1-flash-image","displayName":"Nano Banana 2",
                   "supportedGenerationMethods":["generateContent"],"inputTokenLimit":32000},
                  {"name":"models/text-embedding-1","displayName":"Embedding",
                   "supportedGenerationMethods":["embedContent"],"inputTokenLimit":2048},
                  {"name":"models/gemini-3.8-live","displayName":"Live",
                   "supportedGenerationMethods":["bidiGenerateContent"],"inputTokenLimit":64000},
                  {"name":"models/gemma-4-31b-it","displayName":"Gemma 4 31B",
                   "supportedGenerationMethods":["generateContent"],"inputTokenLimit":8192}
                ]""";
        var nodes = M.readTree(raw);
        var parsed = new java.util.ArrayList<ModelCatalog.ModelInfo>();
        for (var n : nodes) {
            var info = ModelCatalog.parse(M, n);
            if (info != null) parsed.add(info);
        }
        assertEquals(1, parsed.size());
        assertEquals("gemini-3.8-flash", parsed.get(0).id());
        assertEquals("Gemini 3.8 Flash", parsed.get(0).displayName());
        assertEquals(1048576, parsed.get(0).inputTokenLimit());
    }

    @Test
    void failoverSkipsExhaustedAndCurrent() {
        var exhausted = new java.util.HashSet<>(java.util.Set.of("gemini-flash-latest", "gemini-3.8-flash"));
        String next = ModelCatalog.nextFailover("gemini-flash-latest", exhausted);
        assertEquals("gemini-3.7-flash", next);
        // Everything exhausted → null (honest failure)
        var all = new java.util.HashSet<>(ModelCatalog.failoverCandidates());
        assertNull(ModelCatalog.nextFailover("not-a-candidate", all));
    }
}
