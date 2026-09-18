package com.invoicestudio.service;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central home for the app's long-lived background executors.
 *
 * Skill rule 1.4: never {@code Executors.newXxx()} inline. Every inline pool
 * is an untracked thread with an unbounded queue that nobody shuts down —
 * a thread leak per call site (e.g. one leaked thread per login attempt).
 *
 * All pools here are named (visible in thread dumps), daemon (never block JVM
 * exit) and shut down once from {@code Application.stop()}.
 */
public final class AppExecutors {

    /** Network / file I/O: single thread, serialized, so parallel logins can't race. */
    private static final ExecutorService IO = single("invoicestudio-io");

    /**
     * Chat/AI requests: small dedicated pool. AI calls legitimately run for
     * many seconds (smart-router pass + tool rounds + provider retries), so
     * they must NEVER share the serialized IO thread — one slow provider
     * response used to wedge every later message behind it (the chat
     * showed the busy dots forever and "got stuck"). 3 workers keep the
     * chat panel, the settings "Test connection" probe and a bulk flow
     * independent of each other and of file/network IO.
     */
    private static final ExecutorService CHAT = Executors.newFixedThreadPool(
            3,
            r -> {
                Thread t = new Thread(r, "invoicestudio-chat");
                t.setDaemon(true);
                return t;
            });

    /** CPU-bound work (rendering, image, export prep) that must not touch the DB. */
    private static final ExecutorService CPU = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r, "invoicestudio-cpu");
                t.setDaemon(true);
                return t;
            });

    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    private AppExecutors() {}

    public static ExecutorService io() {
        return IO;
    }

    /** Dedicated pool for chat/AI provider requests (never blocked by file IO). */
    public static ExecutorService chat() {
        return CHAT;
    }

    public static ExecutorService cpu() {
        return CPU;
    }

    /** Runs {@code r} on the FX Application Thread, immediately if already there. */
    public static void runOnFx(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    private static ExecutorService single(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    /** Called once from {@code Application.stop()}; in-flight tasks are interrupted. */
    public static void shutdownAll() {
        if (SHUTDOWN.compareAndSet(false, true)) {
            IO.shutdownNow();
            CHAT.shutdownNow();
            CPU.shutdownNow();
        }
    }
}
