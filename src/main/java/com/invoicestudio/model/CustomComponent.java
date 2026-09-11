package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomComponent {
    private String id;
    private String name;
    private String description = "";
    private String category = "General";
    private String createdAt;
    private double width;
    private double height;
    private List<TemplateElement> elements = new ArrayList<>();

    public CustomComponent() {}

    public CustomComponent(String id, String name, String description, String category, List<TemplateElement> elements) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.elements = elements != null ? elements : new ArrayList<>();
        this.createdAt = java.time.Instant.now().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category != null ? category : "General"; }
    public void setCategory(String category) { this.category = category; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public List<TemplateElement> getElements() { return elements; }
    public void setElements(List<TemplateElement> elements) { this.elements = elements != null ? elements : new ArrayList<>(); }
}
