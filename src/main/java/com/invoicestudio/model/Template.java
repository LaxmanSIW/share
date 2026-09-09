package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    @Override
    public String toString() { return name; }
}
