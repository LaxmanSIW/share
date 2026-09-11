package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemCategory {
    private String id;
    private String name = "";
    private String createdAt;
    private String updatedAt;

    public ItemCategory() {
        this.id = "cat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public ItemCategory(String id, String name) {
        this.id = (id != null && !id.isBlank()) ? id : "cat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.name = name != null ? name : "";
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return name;
    }
}
