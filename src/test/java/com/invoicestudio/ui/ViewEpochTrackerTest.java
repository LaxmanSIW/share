package com.invoicestudio.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The stale-view bug: the old design had ONE global lastDataEpoch, so when
 * view A's background refresh completed it marked the WHOLE app fresh — and
 * navigating to view B (whose data changed since B was last shown) skipped
 * B's refresh. Per-view tracking fixes exactly that scenario.
 */
class ViewEpochTrackerTest {

    private static final long NO_DATA = Long.MIN_VALUE;

    @Test
    void editElsewhereThenReturnMustRefresh() {
        ViewEpochTracker t = new ViewEpochTracker();

        // Buyers built at epoch 4.
        t.markRefreshed("buyers", 4);
        assertFalse(t.needsRefresh("buyers", 4), "nothing changed → no refresh");

        // Buyer edited → global epoch 5. User navigates to Invoices first.
        assertFalse(t.needsRefresh("history", 5),
                "invoices never tracked (just built later at 5) → no refresh on first build");
        t.markRefreshed("history", 5);

        // Back to Buyers: its own last refresh was at 4, global is 5 → MUST refresh.
        assertTrue(t.needsRefresh("buyers", 5),
                "a refresh of ANOTHER view must never mark this one fresh");
    }

    @Test
    void refreshOfThisViewMarksItFreshAtTheNewEpoch() {
        ViewEpochTracker t = new ViewEpochTracker();
        t.markRefreshed("buyers", 4);
        t.markRefreshed("history", 5);      // other view's refresh — irrelevant here
        assertTrue(t.needsRefresh("buyers", 5));

        // Buyers' own refresh lands at 5.
        t.markRefreshed("buyers", 5);
        assertFalse(t.needsRefresh("buyers", 5), "own fresh refresh → no repeat refresh");
    }

    @Test
    void firstBuildNeedsNoRefreshEvenIfEpochMoved() {
        ViewEpochTracker t = new ViewEpochTracker();
        t.markRefreshed("history", 5);
        // A brand-new view (never tracked = just built from fresh data)
        // never needs an immediate refresh, whatever the epoch is.
        assertFalse(t.needsRefresh("buyers", 5),
                "factory-built views read fresh data at build time");
        assertFalse(t.needsRefresh("dashboard", NO_DATA));
    }

    @Test
    void secondWriteWhileLookingAtTheViewTriggersRefreshOnRevisit() {
        ViewEpochTracker t = new ViewEpochTracker();
        t.markRefreshed("items", 2);        // view built/shown
        // two more edits happen while the user sits on Items (in-place edits
        // refresh the view itself in practice; this is the revisit path)
        t.markRefreshed("items", 3);
        t.markRefreshed("items", 4);
        assertFalse(t.needsRefresh("items", 4));
        assertTrue(t.needsRefresh("items", 5), "one more edit → refresh on next show");
    }

    @Test
    void clearResetsEverything() {
        ViewEpochTracker t = new ViewEpochTracker();
        t.markRefreshed("buyers", 4);
        t.markRefreshed("history", 4);
        t.clear();
        // After logout the view cache is wiped too — rebuilt views read
        // fresh data at build time, so untracked views need no refresh.
        assertFalse(t.needsRefresh("history", 9));
        assertFalse(t.needsRefresh("buyers", 9));
    }
}
