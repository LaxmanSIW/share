package com.invoicestudio.service;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.event.PrintJobAdapter;
import javax.print.event.PrintJobEvent;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link RawPrintTransport} over the JDK's built-in
 * {@link javax.print} service — no external dependencies.
 * <p>
 * On Windows the Win32 print provider spools byte-array AUTOSENSE docs
 * through {@code StartDocPrinter} with datatype <b>RAW</b>, which TSC
 * drivers pass untouched to the port monitor — exactly how TSPL scripts
 * reach the printer. The spooler therefore never renders or resizes the
 * job: the TA210 receives our SIZE/GAP/BITMAP/PRINT script verbatim.
 * <p>
 * The call is silent (no dialogs) and bounded: it waits at most
 * {@link #SPOOL_WAIT_SECONDS} for the spooler to accept the job before
 * reporting success-with-caveat, so a paused queue never freezes the UI.
 */
public final class JavaxRawPrintTransport implements RawPrintTransport {

    /** Max seconds to wait for the spooler's completed/failed event. */
    public static final int SPOOL_WAIT_SECONDS = 30;

    private static final DocFlavor RAW = DocFlavor.BYTE_ARRAY.AUTOSENSE;

    @Override
    public Result send(String printerName, String jobName, byte[] data) {
        if (data == null || data.length == 0) {
            return new Result(false, "Refusing to send an empty print job.");
        }
        PrintService svc = resolve(printerName);
        if (svc == null) {
            return new Result(false, "Printer \"" + printerName
                    + "\" was not found in the system print services.");
        }
        if (!svc.isDocFlavorSupported(RAW)) {
            return new Result(false, "Printer \"" + printerName
                    + "\" (" + svc.getClass().getSimpleName()
                    + ") does not accept raw data — TSPL direct printing needs the TSC driver.");
        }

        HashPrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        attrs.add(new JobName(jobName != null ? jobName : "InvoiceStudio TSPL", Locale.ENGLISH));

        DocPrintJob job = svc.createPrintJob();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>();
        job.addPrintJobListener(new PrintJobAdapter() {
            @Override public void printJobCompleted(PrintJobEvent e) { done.countDown(); }
            @Override public void printJobFailed(PrintJobEvent e) {
                failure.compareAndSet(null, "The print spooler reported a job failure"
                        + (e != null ? " (" + e + ")" : "") + ".");
                done.countDown();
            }
            @Override public void printJobCanceled(PrintJobEvent e) {
                failure.compareAndSet(null, "The print job was canceled in the spooler.");
                done.countDown();
            }
            @Override public void printJobRequiresAttention(PrintJobEvent e) {
                // e.g. out of paper — keep waiting; the spooler resumes it.
            }
        });

        try {
            Doc doc = new SimpleDoc(data, RAW, null);
            job.print(doc, attrs);
        } catch (PrintException pe) {
            String msg = pe.getMessage();
            return new Result(false, "RAW spool rejected for \"" + printerName + "\": "
                    + (msg != null ? msg : pe.getClass().getSimpleName()));
        }

        try {
            boolean finished = done.await(SPOOL_WAIT_SECONDS, TimeUnit.SECONDS);
            if (failure.get() != null) {
                return new Result(false, failure.get());
            }
            if (!finished) {
                // Spooler accepted the bytes but is busy (paper out, paused).
                return new Result(true, "Spooled to \"" + printerName
                        + "\" — still printing or waiting (check the printer).");
            }
            return new Result(true, "Spooled to \"" + printerName + "\".");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new Result(false, "Interrupted while waiting for the print spooler.");
        }
    }

    /**
     * Finds the spooler service by exact name, then case-insensitive.
     * Returns null when the name is unknown — NEVER silently redirects a
     * named label job to the default printer (physical media could end up
     * on the wrong device).
     */
    private static PrintService resolve(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            return PrintServiceLookup.lookupDefaultPrintService();
        }
        PrintService loose = null;
        for (PrintService s : PrintServiceLookup.lookupPrintServices(null, null)) {
            String n = s.getName();
            if (n.equals(printerName)) return s;
            if (loose == null && n.equalsIgnoreCase(printerName)) loose = s;
        }
        return loose;
    }
}
