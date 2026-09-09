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
    private double printOffsetX; // mm
    private double printOffsetY; // mm
    private boolean statusStamp = true;
    private boolean autoRecurring = false;
    private List<BuyerFieldDef> buyerFields = new ArrayList<>();
    private List<CustomFontDef> customFonts = new ArrayList<>();

    public Settings() {}

    public BusinessProfile getBusiness() { return business != null ? business : new BusinessProfile(); }
    public void setBusiness(BusinessProfile business) { this.business = business; }

    public String getCurrency() { return currency != null ? currency : "₹"; }
    public void setCurrency(String currency) { this.currency = currency; }

    public boolean isInterState() { return interState; }
    public void setInterState(boolean interState) { this.interState = interState; }

    public String getBillNoPrefix() { return billNoPrefix != null ? billNoPrefix : "INV-"; }
    public void setBillNoPrefix(String billNoPrefix) { this.billNoPrefix = billNoPrefix; }

    public int getBillNoNext() { return billNoNext; }
    public void setBillNoNext(int billNoNext) { this.billNoNext = billNoNext; }

    public double getPrintOffsetX() { return printOffsetX; }
    public void setPrintOffsetX(double printOffsetX) { this.printOffsetX = printOffsetX; }

    public double getPrintOffsetY() { return printOffsetY; }
    public void setPrintOffsetY(double printOffsetY) { this.printOffsetY = printOffsetY; }

    public boolean isStatusStamp() { return statusStamp; }
    public void setStatusStamp(boolean statusStamp) { this.statusStamp = statusStamp; }

    public boolean isAutoRecurring() { return autoRecurring; }
    public void setAutoRecurring(boolean autoRecurring) { this.autoRecurring = autoRecurring; }

    public List<BuyerFieldDef> getBuyerFields() { return buyerFields; }
    public void setBuyerFields(List<BuyerFieldDef> buyerFields) { this.buyerFields = buyerFields != null ? buyerFields : new ArrayList<>(); }

    public List<CustomFontDef> getCustomFonts() { return customFonts; }
    public void setCustomFonts(List<CustomFontDef> customFonts) { this.customFonts = customFonts != null ? customFonts : new ArrayList<>(); }
}
