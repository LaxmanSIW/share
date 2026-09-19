package com.invoicestudio.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-view freshness tracking for stale-while-revalidate navigation.
 *
 * <p>Replaces the single global "lastDataEpoch" that used to sit in
 * {@code StudioApp}. That one flag had a hole: when view A's background
 * refresh completed, it marked the WHOLE app fresh — so navigating to view B
 * (whose data had changed since B was last shown) skipped B's refresh and
 * served stale rows. The user's report: "someone might forget to refresh and
 * see stale data" — it wasn't forgetfulness; the check was wrong.</p>
 *
 * <p>Now every view remembers the data epoch it was last (re)read at, and is
 * refreshed exactly when the global epoch has moved since <em>its own</em>
 * last refresh — never because some other view refreshed.</p>
 *
 * <p>Pure bookkeeping, no JavaFX — unit-testable without the toolkit.</p>
 */
public final class ViewEpochTracker {

    /** view id → data epoch the view's data was last read at. */
    private final Map<String, Long> refreshedAt = new HashMap<>();

    /**
     * Should {@code viewId} refresh when shown, given the current global
     * data epoch? True exactly when the epoch has moved since THIS view
     * last read its data — never because another view refreshed.
     *
     * <p>Untracked = freshly built (the caller marks at build time), so it
     * never needs a refresh.</p>
     */
    public synchronized boolean needsRefresh(String viewId, long currentEpoch) {
        Long at = refreshedAt.get(viewId);
        return at != null && currentEpoch != at;
    }

    /** Records that {@code viewId} re-read its data at {@code currentEpoch}. */
    public synchronized void markRefreshed(String viewId, long currentEpoch) {
        refreshedAt.put(viewId, currentEpoch);
    }

    /** Forgets everything (logout / view-cache wipe). */
    public synchronized void clear() {
        refreshedAt.clear();
    }
}
