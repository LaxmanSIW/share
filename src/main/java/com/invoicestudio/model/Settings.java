package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings {
    private BusinessProfile business = new BusinessProfile();
    private String currency = "₹";
    private boolean interState; // true -> IGST, false -> CGST+SGST
    private String billNoPrefix = "INV-";
    private int billNoNext = 1;
    private int billNoDigits = 4; // 0/1: no padding, 3: 001, 4: 0001
    private boolean monochromePrint = false; // B&W Xerox Print Mode
    private double printOffsetX; // mm
    private double printOffsetY; // mm
    private boolean statusStamp = true;
    private boolean autoRecurring = false;

    /**
     * Thermal label (TSC/TSPL) brightness threshold, 0–255. Downsampled dot
     * grays ≤ threshold burn sharp black, everything above stays sharp white
     * — the only two colors a thermal head can produce. Default 150 (slightly
     * above mid-gray so hairlines and small text survive). Keep in sync with
     * {@code MonoImage.DEFAULT_THRESHOLD} (kept as a literal to avoid a
     * model→service dependency).
     */
    private int barcodeThreshold = 150;

    private List<BuyerFieldDef> buyerFields = new ArrayList<>();
    private List<CustomFontDef> customFonts = new ArrayList<>();

    public Settings() {}

    public BusinessProfile getBusiness() { return business != null ? business : new BusinessProfile(); }
    public void setBusiness(BusinessProfile business) { this.business = business; }

    public String getCurrency() { return currency != null ? currency : "₹"; }
    public void setCurrency(String currency) { this.currency = currency; }

    public boolean isInterState() { return interState; }
    public void setInterState(boolean interState) { this.interState = interState; }

    public String getBillNoPrefix() { return billNoPrefix != null ? billNoPrefix : ""; }
    public void setBillNoPrefix(String billNoPrefix) { this.billNoPrefix = billNoPrefix != null ? billNoPrefix : ""; }

    public int getBillNoNext() { return billNoNext; }
    public void setBillNoNext(int billNoNext) { this.billNoNext = billNoNext; }

    public int getBillNoDigits() { return billNoDigits > 0 ? billNoDigits : 1; }
    public void setBillNoDigits(int billNoDigits) { this.billNoDigits = Math.max(0, billNoDigits); }

    public boolean isMonochromePrint() { return monochromePrint; }
    public void setMonochromePrint(boolean monochromePrint) { this.monochromePrint = monochromePrint; }

    public double getPrintOffsetX() { return printOffsetX; }
    public void setPrintOffsetX(double printOffsetX) { this.printOffsetX = printOffsetX; }

    public double getPrintOffsetY() { return printOffsetY; }
    public void setPrintOffsetY(double printOffsetY) { this.printOffsetY = printOffsetY; }

    public boolean isStatusStamp() { return statusStamp; }
    public void setStatusStamp(boolean statusStamp) { this.statusStamp = statusStamp; }

    public boolean isAutoRecurring() { return autoRecurring; }
    public void setAutoRecurring(boolean autoRecurring) { this.autoRecurring = autoRecurring; }

    public int getBarcodeThreshold() { return barcodeThreshold; }
    public void setBarcodeThreshold(int barcodeThreshold) {
        this.barcodeThreshold = Math.max(0, Math.min(255, barcodeThreshold));
    }

    public List<BuyerFieldDef> getBuyerFields() { return buyerFields; }
    public void setBuyerFields(List<BuyerFieldDef> buyerFields) { this.buyerFields = buyerFields != null ? buyerFields : new ArrayList<>(); }

    public List<CustomFontDef> getCustomFonts() { return customFonts; }
    public void setCustomFonts(List<CustomFontDef> customFonts) { this.customFonts = customFonts != null ? customFonts : new ArrayList<>(); }
}
