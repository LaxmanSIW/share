package com.invoicestudio.service;

import com.invoicestudio.service.AppLog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.AppDirs;
import com.invoicestudio.model.CustomComponent;
import com.invoicestudio.model.TemplateElement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CustomComponentManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FILE_NAME = "custom_components.json";

    public static Path getStoragePath() {
        return AppDirs.dataDir().resolve(FILE_NAME);
    }

    public static synchronized List<CustomComponent> loadComponents() {
        Path path = getStoragePath();
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(path.toFile(), new TypeReference<List<CustomComponent>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static synchronized CustomComponent saveComponent(String name, String description, List<TemplateElement> sourceElements) throws IOException {
        return saveComponent(name, description, "Custom", sourceElements);
    }

    public static synchronized CustomComponent saveComponent(String name, String description, String category, List<TemplateElement> sourceElements) throws IOException {
        if (sourceElements == null || sourceElements.isEmpty()) {
            throw new IllegalArgumentException("Cannot create component with empty elements list");
        }

        // Calculate bounding box to normalize coordinates
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        for (TemplateElement el : sourceElements) {
            minX = Math.min(minX, el.getX());
            minY = Math.min(minY, el.getY());
            maxX = Math.max(maxX, el.getX() + el.getW());
            maxY = Math.max(maxY, el.getY() + el.getH());
        }

        double width = Math.max(10.0, maxX - minX);
        double height = Math.max(10.0, maxY - minY);

        // Deep copy and normalize elements relative to (0, 0)
        List<TemplateElement> normalized = new ArrayList<>();
        for (TemplateElement el : sourceElements) {
            String json = MAPPER.writeValueAsString(el);
            TemplateElement copy = MAPPER.readValue(json, TemplateElement.class);
            copy.setX(Math.max(0, el.getX() - minX));
            copy.setY(Math.max(0, el.getY() - minY));
            normalized.add(copy);
        }

        String id = "comp_custom_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        CustomComponent component = new CustomComponent(id, name != null && !name.isBlank() ? name : "Custom Block", description, category, normalized);
        component.setWidth(width);
        component.setHeight(height);

        List<CustomComponent> all = loadComponents();
        all.add(component);

        Path path = getStoragePath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), all);

        return component;
    }

    public static synchronized boolean deleteComponent(String id) {
        if (id == null || id.isBlank()) return false;
        List<CustomComponent> all = loadComponents();
        boolean removed = all.removeIf(c -> id.equalsIgnoreCase(c.getId()));
        if (removed) {
            try {
                Path path = getStoragePath();
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), all);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Instantiates the component at target coordinates with fresh element IDs and a shared groupId.
     */
    public static List<TemplateElement> instantiate(CustomComponent comp, double targetX, double targetY) {
        List<TemplateElement> result = new ArrayList<>();
        if (comp == null || comp.getElements() == null) return result;

        String groupId = "grp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String groupName = comp.getName() != null && !comp.getName().isBlank() ? comp.getName() : "Custom Group";

        for (TemplateElement src : comp.getElements()) {
            try {
                String json = MAPPER.writeValueAsString(src);
                TemplateElement copy = MAPPER.readValue(json, TemplateElement.class);
                copy.setId("el_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
                copy.setX(targetX + src.getX());
                copy.setY(targetY + src.getY());
                copy.setGroupId(groupId);
                copy.setGroupName(groupName);
                copy.setComponentType(comp.getName());
                result.add(copy);
            } catch (Exception ignored) {
            AppLog.debug(ignored); }
        }

        return result;
    }

    public static List<TemplateElement> instantiateComponent(CustomComponent comp, double targetX, double targetY) {
        return instantiate(comp, targetX, targetY);
    }
}
