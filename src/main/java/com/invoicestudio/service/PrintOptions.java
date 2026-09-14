package com.invoicestudio.service;

import javafx.print.Paper;
import javafx.print.PageOrientation;
import javafx.print.Printer;

/**
 * Fully-resolved print request produced by the modern print preview dialog.
 * <p>
 * Carries everything needed to print deterministically: the exact printer,
 * paper, orientation and copy count chosen by the user. The printing pipeline
 * builds its PageLayout from these values AFTER all UI interaction is done,
 * so the Windows driver's default paper form can never override the job
 * (this was the root cause of A4 bills printing at 100x140 mm).
 */
public class PrintOptions {

    private final Printer printer;
    private final Paper paper;
    private final PageOrientation orientation;
    private final int copies;
    private final String jobName;

    public PrintOptions(Printer printer, Paper paper, PageOrientation orientation, int copies, String jobName) {
        this.printer = printer;
        this.paper = paper != null ? paper : Paper.A4;
        this.orientation = orientation != null ? orientation : PageOrientation.PORTRAIT;
        this.copies = Math.max(1, copies);
        this.jobName = jobName != null ? jobName : "InvoiceStudio Print";
    }

    public Printer getPrinter() { return printer; }

    public Paper getPaper() { return paper; }

    public PageOrientation getOrientation() { return orientation; }

    public int getCopies() { return copies; }

    public String getJobName() { return jobName; }

    /** Human-readable paper size in mm, e.g. "210 x 297 mm". */
    public static String paperSizeText(Paper paper) {
        if (paper == null) return "?";
        double wMm = paper.getWidth() * 25.4 / 72.0;
        double hMm = paper.getHeight() * 25.4 / 72.0;
        return String.format(java.util.Locale.US, "%.0f x %.0f mm", wMm, hMm);
    }
}
