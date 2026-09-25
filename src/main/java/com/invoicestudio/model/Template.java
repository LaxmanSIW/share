package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Template {
    private String id;
    private String name = "Untitled Template";
    private PageConfig page = new PageConfig();
    private List<TemplateElement> elements = new ArrayList<>();
    private double printOffsetX; // mm
    private double printOffsetY; // mm
    private String createdAt;
    private String updatedAt;

    /**
     * "bill" (default/legacy/null) = normal document template.
     * "label" = Barcode Mode: canvas is ONE label cell driven by labelConfig
     * (thermal strip stock, bulk variable printing).
     */
    private String mode = "bill";

    /** Barcode Mode stock geometry — null-safe via {@link #labelOrNew()}. */
    private LabelConfig labelConfig;

    public Template() {}

    public Template(String id, String name, PageConfig page, List<TemplateElement> elements) {
        this.id = id;
        this.name = name;
        this.page = page != null ? page : new PageConfig();
        this.elements = elements != null ? elements : new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PageConfig getPage() { return page != null ? page : new PageConfig(); }
    public void setPage(PageConfig page) { this.page = page; }

    public List<TemplateElement> getElements() { return elements; }
    public void setElements(List<TemplateElement> elements) { this.elements = elements != null ? elements : new ArrayList<>(); }

    public double getPrintOffsetX() { return printOffsetX; }
    public void setPrintOffsetX(double printOffsetX) { this.printOffsetX = printOffsetX; }

    public double getPrintOffsetY() { return printOffsetY; }
    public void setPrintOffsetY(double printOffsetY) { this.printOffsetY = printOffsetY; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    /** "label" = Barcode Mode; anything else (incl. null) is a normal bill template. */
    public String getMode() { return mode != null ? mode : "bill"; }
    public void setMode(String mode) { this.mode = mode; }

    public boolean isLabelMode() { return "label".equalsIgnoreCase(getMode()); }

    /** Never-null label config; returns the live instance or a fresh default. */
    public LabelConfig labelOrNew() {
        if (labelConfig == null) labelConfig = new LabelConfig();
        return labelConfig;
    }

    public LabelConfig getLabelConfig() { return labelConfig; }
    public void setLabelConfig(LabelConfig labelConfig) { this.labelConfig = labelConfig; }

    @Override
    public String toString() { return name; }

    /**
     * Full-fidelity deep copy used by "Duplicate Template". A Jackson
     * round-trip copies every persisted field — mode, labelConfig, print
     * offsets, and every element — so a duplicated barcode template stays a
     * barcode template with its stock geometry and print settings intact.
     * (The old duplicate path rebuilt from a bill preset and copied only
     * page+elements, silently dropping mode/labelConfig/print settings.)
     *
     * <p>Deep matters: element instances (and labelConfig) are cloned, so
     * editing the copy in the designer can never mutate the source template.</p>
     *
     * @return an exact copy with a fresh id and copied (not shared) children;
     *         createdAt/updatedAt are preserved so the DAO sees original
     *         creation time and stamps a new updatedAt on save.
     */
    public static Template copyOf(Template source) {
        if (source == null) return null;
        try {
            Template copy = COPY_MAPPER.readValue(COPY_MAPPER.writeValueAsString(source), Template.class);
            copy.id = null; // caller assigns a fresh id; null also prevents the DAO upsert from overwriting the source row
            return copy;
        } catch (Exception e) {
            // Jackson round-trip of a plain POJO cannot realistically fail;
            // fall back to a field copy so duplicate still works (shallow).
            Template copy = new Template(null, source.name, source.page, source.elements);
            copy.printOffsetX = source.printOffsetX;
            copy.printOffsetY = source.printOffsetY;
            copy.mode = source.mode;
            copy.labelConfig = source.labelConfig;
            copy.createdAt = source.createdAt;
            copy.updatedAt = source.updatedAt;
            return copy;
        }
    }

    private static final ObjectMapper COPY_MAPPER = new ObjectMapper();
}
