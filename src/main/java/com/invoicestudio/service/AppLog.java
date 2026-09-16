package com.invoicestudio.service;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Single logging surface (skill rule 4.2): one place that decides where
 * errors go, so a future file-logger or crash reporter is a one-line change
 * instead of a 100-file hunt for {@code printStackTrace}.
 *
 * Deliberately dependency-free and throwable-only — DAO catch blocks log
 * through here, views keep their own user-facing feedback (Toast/Alert).
 */
public final class AppLog {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static volatile PrintStream sink = System.err;

    private AppLog() {}

    /** Logs the exception with the calling class, thread and timestamp. */
    public static void error(Throwable t) {
        StackTraceElement caller = callerOf(t);
        sink.println("[" + LocalTime.now().format(TS) + "] ["
                + Thread.currentThread().getName() + "] ERROR "
                + (caller != null ? caller.getClassName() + "." + caller.getMethodName() + " (): " : "")
                + t);
        t.printStackTrace(sink);
    }

    /** Logs a message plus the exception. */
    public static void error(String message, Throwable t) {
        sink.println("[" + LocalTime.now().format(TS) + "] ["
                + Thread.currentThread().getName() + "] ERROR " + message + ": " + t);
        t.printStackTrace(sink);
    }

    public static void warn(String message) {
        sink.println("[" + LocalTime.now().format(TS) + "] ["
                + Thread.currentThread().getName() + "] WARN  " + message);
    }

    private static final boolean DEBUG_ENABLED =
            Boolean.parseBoolean(System.getProperty("applog.debug", "false"));

    /**
     * Debug-level trace for deliberate best-effort catch blocks (DAO parse
     * fallbacks, optional lookups). No-op unless the app is started with
     * {@code -Dapplog.debug=true} — zero cost and zero console spam by
     * default, full trace when diagnosing.
     */
    public static void debug(Throwable t) {
        if (DEBUG_ENABLED) error(t);
    }

    /** Debug-level message; see {@link #debug(Throwable)}. */
    public static void debug(String message) {
        if (DEBUG_ENABLED) warn(message);
    }

    /** Redirect target for tests or a future file logger. */
    public static void setSink(PrintStream newSink) {
        sink = newSink != null ? newSink : System.err;
    }

    private static StackTraceElement callerOf(Throwable t) {
        StackTraceElement[] frames = t.getStackTrace();
        for (StackTraceElement f : frames) {
            if (f.getClassName().startsWith("com.invoicestudio.")) {
                return f;
            }
        }
        return frames.length > 0 ? frames[0] : null;
    }
}
