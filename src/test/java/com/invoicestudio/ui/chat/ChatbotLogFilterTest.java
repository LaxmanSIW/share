package com.invoicestudio.ui.chat;

import com.invoicestudio.service.ChatbotLogManager.LogLevel;
import com.invoicestudio.service.ChatbotLogManager.LogEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The execution-log filter predicate — pure logic, no JavaFX toolkit.
 * The USER turn divider must stay visible in EVERY filter so a collapsed
 * view still anchors each step to the message it belongs to.
 */
class ChatbotLogFilterTest {

    private static LogEntry entry(LogLevel level) {
        return new LogEntry("10:00:00.000", level, level.name(), "message", null);
    }

    @Test
    void userTurnDividerIsVisibleInEveryFilter() {
        LogEntry user = entry(LogLevel.USER);
        for (String chip : List.of("ALL", "ROUTING", "HTTP", "TOOLS", "RESULTS", "ISSUES", "UNKNOWN-CHIP")) {
            assertTrue(ChatbotLogDialog.showsEntry(chip, user),
                    "USER divider must survive the " + chip + " filter");
        }
    }

    @Test
    void routingFilterShowsRouterAndDispatchOnly() {
        String chip = "ROUTING";
        assertTrue(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.ROUTER)));
        assertTrue(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.DISPATCH)));
        for (LogLevel lv : List.of(LogLevel.HTTP, LogLevel.TOKENS, LogLevel.TOOL,
                LogLevel.MCP, LogLevel.SUCCESS, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR)) {
            assertFalse(ChatbotLogDialog.showsEntry(chip, entry(lv)), chip + " must hide " + lv);
        }
    }

    @Test
    void httpFilterCoversRequestsAndTokens() {
        String chip = "HTTP";
        assertTrue(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.HTTP)));
        assertTrue(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.TOKENS)));
        assertFalse(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.ROUTER)));
        assertFalse(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.MCP)));
    }

    @Test
    void toolsFilterCoversToolCallsAndMcpExecutions() {
        String chip = "TOOLS";
        assertTrue(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.TOOL)));
        assertTrue(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.MCP)));
        assertFalse(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.HTTP)));
        assertFalse(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.SUCCESS)));
    }

    @Test
    void resultsFilterShowsSuccessAndSystemNotes() {
        String chip = "RESULTS";
        assertTrue(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.SUCCESS)));
        assertTrue(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.INFO)));
        assertFalse(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.WARN)));
        assertFalse(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.ERROR)));
    }

    @Test
    void issuesFilterShowsWarnAndErrorOnly() {
        String chip = "ISSUES";
        assertTrue(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.WARN)));
        assertTrue(ChatbotLogDialog.showsEntry(chip, entry(LogLevel.ERROR)));
        for (LogLevel lv : List.of(LogLevel.INFO, LogLevel.SUCCESS, LogLevel.HTTP,
                LogLevel.ROUTER, LogLevel.DISPATCH, LogLevel.TOOL, LogLevel.MCP, LogLevel.TOKENS)) {
            assertFalse(ChatbotLogDialog.showsEntry(chip, entry(lv)), chip + " must hide " + lv);
        }
    }

    @Test
    void allFilterShowsEverythingAndUnknownChipsFailOpen() {
        for (LogLevel lv : LogLevel.values()) {
            assertTrue(ChatbotLogDialog.showsEntry("ALL", entry(lv)));
            assertTrue(ChatbotLogDialog.showsEntry("SOMETHING-NEW", entry(lv)),
                    "unknown chip must fail open (show all) rather than hide logs");
        }
        assertFalse(ChatbotLogDialog.showsEntry("ALL", null), "null entry hidden, not a crash");
    }
}
