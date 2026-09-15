package com.invoicestudio.service;

/**
 * Sends raw print bytes straight into the OS spooler for one named
 * printer — the passthrough TSC drivers accept as TSPL/TSPL2 scripts
 * (datatype RAW, no GDI rendering, no driver stock interference).
 */
public interface RawPrintTransport {

    /** Outcome of one RAW spool attempt. */
    record Result(boolean success, String message) {}

    /**
     * Spools {@code data} to {@code printerName} as a RAW job.
     * Implementations must be silent (no dialogs) and thread-safe.
     */
    Result send(String printerName, String jobName, byte[] data);
}
