package com.invoicestudio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.invoicestudio.AppDirs;
import com.invoicestudio.model.KnowledgeArticle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Repository for managing Knowledge Hub documentation articles.
 *
 * <p>Storage Architecture:</p>
 * <ul>
 *   <li><b>Global Codebase Storage</b>: Loaded from classpath resource {@code /knowledge/knowledge-hub.json}
 *       (or directly from {@code src/main/resources/knowledge/knowledge-hub.json} in development).</li>
 *   <li><b>Global Updates</b>: In development or workspace environments, saving articles writes
 *       directly back to {@code src/main/resources/knowledge/knowledge-hub.json} so that additions
 *       and edits become part of the software codebase and version control.</li>
 *   <li><b>Runtime Fallback</b>: Also syncs to {@code %APPDATA%\InvoiceStudio\knowledge-hub.json}
 *       so that packaged runtime instances persist user additions locally.</li>
 * </ul>
 */
public final class KnowledgeRepository {

    private static final String FILE_NAME = "knowledge-hub.json";
    private static final String CLASSPATH_RESOURCE = "/knowledge/knowledge-hub.json";
    private static final Path SOURCE_DEV_PATH = Paths.get("src", "main", "resources", "knowledge", "knowledge-hub.json");
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static KnowledgeRepository instance;

    private final Path storagePath;
    private final boolean persistToDevSource;
    private final List<KnowledgeArticle> articles = new CopyOnWriteArrayList<>();

    public static synchronized KnowledgeRepository getInstance() {
        if (instance == null) {
            instance = new KnowledgeRepository(AppDirs.dataDir().resolve(FILE_NAME), true);
        }
        return instance;
    }

    public static KnowledgeRepository createCustom(Path path) {
        return new KnowledgeRepository(path, false);
    }

    KnowledgeRepository(Path storagePath, boolean persistToDevSource) {
        this.storagePath = storagePath;
        this.persistToDevSource = persistToDevSource;
        load();
    }

    public synchronized void load() {
        articles.clear();

        // 1. If local storagePath exists on disk and is non-empty, use it first (for isolated test repos or runtime edits)
        if (Files.exists(storagePath) && storagePath.toFile().length() > 0) {
            try {
                List<KnowledgeArticle> loaded = MAPPER.readValue(
                        storagePath.toFile(),
                        new TypeReference<List<KnowledgeArticle>>() {});
                if (loaded != null && !loaded.isEmpty()) {
                    articles.addAll(loaded);
                    // ── Merge shipped updates into the local library ──────────
                    // The local copy wins for articles the user already has
                    // (their edits must never be clobbered), but articles that
                    // ship NEW with an app update (e.g. newly seeded chapters)
                    // must reach existing installs too — a plain "local wins"
                    // rule froze every install on the article list it first
                    // saved, which is exactly why shipped chapters went
                    // missing ("chapter not showing" reports).
                    boolean merged = mergeShippedArticles(loadShippedArticles());
                    if (merged) {
                        saveToLocalStorage();
                    }
                    return;
                }
            } catch (Exception ex) {
                AppLog.error(ex);
            }
        }

        // 2. Try loading from development source file if it exists on disk
        if (persistToDevSource && Files.exists(SOURCE_DEV_PATH)) {
            try {
                List<KnowledgeArticle> loaded = MAPPER.readValue(
                        SOURCE_DEV_PATH.toFile(),
                        new TypeReference<List<KnowledgeArticle>>() {});
                if (loaded != null && !loaded.isEmpty()) {
                    articles.addAll(loaded);
                    saveToLocalStorage();
                    return;
                }
            } catch (Exception ex) {
                AppLog.error(ex);
            }
        }

        // 3. Try loading from bundled classpath resource
        try (InputStream in = KnowledgeRepository.class.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in != null) {
                List<KnowledgeArticle> loaded = MAPPER.readValue(
                        in,
                        new TypeReference<List<KnowledgeArticle>>() {});
                if (loaded != null && !loaded.isEmpty()) {
                    articles.addAll(loaded);
                    saveToLocalStorage();
                    return;
                }
            }
        } catch (Exception ex) {
            AppLog.error(ex);
        }

        // 4. Seed default documentation
        articles.addAll(createDefaultArticles());
        saveToFile();
    }

    /**
     * Loads the bundled article list for merge-on-load: dev source first
     * (dev workspaces), then the classpath resource shipped in the jar.
     * Returns an empty list when neither is readable — merging then no-ops
     * and the local library loads untouched.
     */
    private List<KnowledgeArticle> loadShippedArticles() {
        List<KnowledgeArticle> shipped = new ArrayList<>();
        if (persistToDevSource && Files.exists(SOURCE_DEV_PATH)) {
            try {
                shipped.addAll(MAPPER.readValue(SOURCE_DEV_PATH.toFile(),
                        new TypeReference<List<KnowledgeArticle>>() {}));
            } catch (Exception ex) {
                AppLog.debug(ex);
            }
        }
        if (shipped.isEmpty()) {
            try (InputStream in = KnowledgeRepository.class.getResourceAsStream(CLASSPATH_RESOURCE)) {
                if (in != null) {
                    shipped.addAll(MAPPER.readValue(in, new TypeReference<List<KnowledgeArticle>>() {}));
                }
            } catch (Exception ex) {
                AppLog.debug(ex);
            }
        }
        return shipped;
    }

    /**
     * Adds every shipped article whose id is missing locally.
     *
     * @return true when at least one article was added (caller should persist)
     */
    private boolean mergeShippedArticles(List<KnowledgeArticle> shipped) {
        if (shipped == null || shipped.isEmpty()) return false;
        Set<String> known = new HashSet<>();
        for (KnowledgeArticle a : articles) known.add(a.id());
        boolean added = false;
        for (KnowledgeArticle b : shipped) {
            if (b != null && b.id() != null && known.add(b.id())) {
                articles.add(b);
                added = true;
            }
        }
        return added;
    }

    public synchronized void saveArticle(KnowledgeArticle article) {
        if (article == null) return;
        int idx = -1;
        for (int i = 0; i < articles.size(); i++) {
            if (articles.get(i).id().equals(article.id())) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            articles.set(idx, article);
        } else {
            articles.add(article);
        }
        saveToFile();
    }

    public synchronized boolean deleteArticle(String id) {
        if (id == null) return false;
        boolean removed = articles.removeIf(a -> a.id().equals(id));
        if (removed) {
            saveToFile();
        }
        return removed;
    }

    public synchronized void resetToDefaults() {
        articles.clear();
        articles.addAll(createDefaultArticles());
        saveToFile();
    }

    public List<KnowledgeArticle> getAllArticles() {
        return Collections.unmodifiableList(new ArrayList<>(articles));
    }

    public Optional<KnowledgeArticle> getArticleById(String id) {
        return articles.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    /**
     * Returns a sorted list of all unique category paths currently in use.
     */
    public List<String> getAllCategoryPaths() {
        Set<String> paths = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (KnowledgeArticle a : articles) {
            if (a.path() != null && !a.path().isBlank()) {
                paths.add(a.path().trim());
            }
        }
        if (paths.isEmpty()) {
            paths.add("General");
        }
        return new ArrayList<>(paths);
    }

    private void saveToFile() {
        // 1. Save to global source in codebase if enabled (dev / source workspace mode)
        if (persistToDevSource) {
            try {
                Path parent = SOURCE_DEV_PATH.getParent();
                if (parent != null && Files.isDirectory(parent)) {
                    MAPPER.writeValue(SOURCE_DEV_PATH.toFile(), articles);
                }
            } catch (Exception e) {
                // Ignored if in read-only environment
            }
        }

        // 2. Save to local storagePath
        saveToLocalStorage();
    }

    private void saveToLocalStorage() {
        try {
            Path parent = storagePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(storagePath.toFile(), articles);
        } catch (IOException e) {
            AppLog.error(e);
        }
    }

    // ── Tree Construction ─────────────────────────────────────────────

    public static final class CategoryNode {
        private final String name;
        private final String fullPath;
        private final List<CategoryNode> subCategories = new ArrayList<>();
        private final List<KnowledgeArticle> articles = new ArrayList<>();
        private boolean expanded;

        public CategoryNode(String name, String fullPath, boolean expanded) {
            this.name = name;
            this.fullPath = fullPath;
            this.expanded = expanded;
        }

        public String name() { return name; }
        public String fullPath() { return fullPath; }
        public List<CategoryNode> subCategories() { return subCategories; }
        public List<KnowledgeArticle> articles() { return articles; }
        public boolean isExpanded() { return expanded; }
        public void setExpanded(boolean exp) { this.expanded = exp; }

        public int totalArticles() {
            int count = articles.size();
            for (CategoryNode sub : subCategories) {
                count += sub.totalArticles();
            }
            return count;
        }

        public boolean matches(String query) {
            if (query == null || query.isBlank()) return true;
            String q = query.trim().toLowerCase(Locale.ROOT);
            if (name.toLowerCase(Locale.ROOT).contains(q)) return true;
            for (KnowledgeArticle a : articles) {
                if (a.title().toLowerCase(Locale.ROOT).contains(q)) return true;
                if (a.subtitle().toLowerCase(Locale.ROOT).contains(q)) return true;
                if (a.markdown().toLowerCase(Locale.ROOT).contains(q)) return true;
                if (a.author().toLowerCase(Locale.ROOT).contains(q)) return true;
            }
            for (CategoryNode sub : subCategories) {
                if (sub.matches(q)) return true;
            }
            return false;
        }
    }

    /**
     * Builds a multi-level CategoryNode hierarchy from all articles.
     */
    public CategoryNode buildCategoryTree() {
        CategoryNode rootNode = new CategoryNode("Root", "", true);

        for (KnowledgeArticle article : articles) {
            String path = article.path();
            if (path == null || path.isBlank()) path = "General";

            String[] parts = path.split("\\s*/\\s*");
            CategoryNode current = rootNode;
            StringBuilder currentPath = new StringBuilder();

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) continue;

                if (currentPath.length() > 0) currentPath.append(" / ");
                currentPath.append(part);

                String pStr = currentPath.toString();
                CategoryNode next = null;
                for (CategoryNode existing : current.subCategories) {
                    if (existing.name.equalsIgnoreCase(part)) {
                        next = existing;
                        break;
                    }
                }
                if (next == null) {
                    boolean defaultExpanded = (i == 0);
                    next = new CategoryNode(part, pStr, defaultExpanded);
                    current.subCategories.add(next);
                }
                current = next;
            }
            current.articles.add(article);
        }

        return rootNode;
    }

    // ── Default Knowledge Seed ────────────────────────────────────────

    private List<KnowledgeArticle> createDefaultArticles() {
        return KnowledgeSeed.buildAllArticles();
    }
}
