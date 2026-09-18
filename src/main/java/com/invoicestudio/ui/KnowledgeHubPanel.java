package com.invoicestudio.ui;

import com.invoicestudio.model.KnowledgeArticle;
import com.invoicestudio.service.KnowledgeRepository;
import com.invoicestudio.ui.chat.ChatMarkdownRenderer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Settings → Knowledge Hub: Markdown-driven documentation and knowledge base.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li><b>Markdown-Driven</b>: Articles are formatted and rendered via clean GitHub-Flavored Markdown.</li>
 *   <li><b>Editable & Dynamic</b>: Supports View Mode and Edit Mode; add, edit, and delete topics at any level.</li>
 *   <li><b>Multi-Level Tree</b>: Expandable/collapsible hierarchy with toggle persistence and auto-expanding search filter.</li>
 *   <li><b>Full Monitor Height</b>: Automatically calculates height = monitor/window height - box start Y - margin to prevent outer display scrolling.</li>
 *   <li><b>Global Codebase Storage</b>: Loaded from and saved directly into the application codebase with author tracking.</li>
 * </ul>
 */
public class KnowledgeHubPanel extends VBox {

    private static final String GOLD = "#D9A13B";
    private static final String TEXT = "#F2F4F8";
    private static final String MUTED = "#97A3B6";
    private static final String CARD_STYLE =
            "-fx-background-color: #131B28; -fx-background-radius: 8;"
            + "-fx-border-color: #243247; -fx-border-radius: 8; -fx-border-width: 1;";

    private final KnowledgeRepository repo = KnowledgeRepository.getInstance();

    // ── Sidebar Controls ──────────────────────────────────────────────
    private final TextField searchField = new TextField();
    private final VBox treeBox = new VBox(2);
    private final ScrollPane treeScroll = new ScrollPane(treeBox);
    private final Label countChip = new Label();
    private final Set<String> expandedCategories = new HashSet<>();

    // ── Content Controls ──────────────────────────────────────────────
    private final StackPane contentArea = new StackPane();
    private final VBox viewModeContainer = new VBox(10);
    private final VBox editModeContainer = new VBox(10);

    // View Mode elements
    private final Label breadcrumbLbl = new Label();
    private final Label contentTitle = new Label();
    private final Label authorLbl = new Label();
    private final Label contentSubtitle = new Label();
    private final VBox markdownBox = new VBox(10);
    private final ScrollPane articleScroll = new ScrollPane(markdownBox);
    private final Button editBtn = new Button("Edit Article");
    private final Button deleteBtn = new Button("Delete");

    // Edit Mode elements
    private final Label editModeTitleLbl = new Label("Edit Documentation Article");
    private final ComboBox<String> pathCombo = new ComboBox<>();
    private final TextField titleField = new TextField();
    private final TextField authorField = new TextField();
    private final TextField subtitleField = new TextField();
    private final TextArea markdownEditor = new TextArea();
    private final Button saveBtn = new Button("Save Article");
    private final Button cancelBtn = new Button("Cancel");

    // Split container (Sidebar + Content)
    private HBox split;

    // State
    private KnowledgeArticle currentArticle;
    private boolean isCreatingNew = false;

    public KnowledgeHubPanel() {
        setSpacing(10);
        setPadding(new Insets(10, 10, 8, 10));
        setFillWidth(true);
        VBox.setVgrow(this, Priority.ALWAYS);

        // Expand root topics by default
        expandedCategories.add("TSC");
        expandedCategories.add("AI Chatbot");

        getChildren().addAll(buildHero(), buildBody());

        searchField.textProperty().addListener((obs, oldVal, newVal) -> rebuildTree());
        rebuildTree();

        // Dynamically constrain split height to monitor height minus start Y and bottom margin
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.heightProperty().addListener((o, ov, nv) -> updateSplitHeight());
                Platform.runLater(this::updateSplitHeight);
            }
        });
        layoutBoundsProperty().addListener((obs, ov, nv) -> updateSplitHeight());

        // Select first article
        List<KnowledgeArticle> all = repo.getAllArticles();
        if (!all.isEmpty()) {
            showArticle(all.get(0));
        }

        // Refresh when articles change OUTSIDE this panel (the assistant's
        // knowledge_* MCP tools mutate on worker threads) — the sidebar tree
        // and count chip must not go stale until reopen.
        KnowledgeRepository.addChangeListener(() -> Platform.runLater(() -> {
            rebuildTree();
            if (currentArticle != null) {
                KnowledgeArticle fresh = repo.getArticleById(currentArticle.id()).orElse(null);
                if (fresh != null && fresh != currentArticle) {
                    showArticle(fresh);
                }
            }
        }));
    }

    /**
     * Calculates and enforces split height = window/scene height - box start Y - margin.
     * Prevents the whole display/window from scrolling while keeping internal scrollbars active.
     */
    private void updateSplitHeight() {
        if (split == null || getScene() == null) return;
        double sceneH = getScene().getHeight();
        if (sceneH <= 0) return;

        javafx.geometry.Point2D p = split.localToScene(0, 0);
        double startY = (p != null && p.getY() > 0) ? p.getY() : 180.0;
        double bottomMargin = 28.0;
        double avail = Math.max(320.0, sceneH - startY - bottomMargin);

        split.setMaxHeight(avail);
        split.setPrefHeight(avail);
    }

    // ─────────────────────────────────────────────────────────────────
    // Top Hero Bar
    // ─────────────────────────────────────────────────────────────────

    private Node buildHero() {
        StackPane badge = new StackPane(IconHelper.getIcon(IconHelper.ICON_SPARKLES, 20, GOLD));
        badge.setStyle("-fx-background-color: rgba(217,161,59,0.14); -fx-background-radius: 10;"
                + "-fx-border-color: rgba(217,161,59,0.45); -fx-border-radius: 10; -fx-border-width: 1;");
        badge.setPrefSize(40, 40);
        badge.setMinSize(40, 40);
        badge.setMaxSize(40, 40);

        Label title = new Label("Knowledge Hub");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + TEXT + ";");

        Label subtitle = new Label("Software documentation, hardware guides, and chatbot manuals formatted in Markdown. Add and edit topics with global codebase persistence.");
        subtitle.setStyle("-fx-font-size: 11.5px; -fx-text-fill: " + MUTED + ";");
        subtitle.setWrapText(true);

        countChip.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: " + GOLD + ";"
                + "-fx-background-color: rgba(217,161,59,0.12); -fx-background-radius: 6;"
                + "-fx-border-color: rgba(217,161,59,0.35); -fx-border-radius: 6; -fx-border-width: 1;"
                + "-fx-padding: 2 8 2 8;");

        HBox titleRow = new HBox(8, title, countChip);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        VBox textBox = new VBox(2, titleRow, subtitle);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Button addArticleBtn = new Button("+ Add Article");
        addArticleBtn.setStyle("-fx-background-color: " + GOLD + "; -fx-text-fill: #0B0E13; -fx-font-weight: bold; "
                + "-fx-background-radius: 6; -fx-padding: 6 14 6 14; -fx-font-size: 12px; -fx-cursor: hand;");
        addArticleBtn.setOnAction(e -> startCreatingNewArticle(null));

        HBox hero = new HBox(12, badge, textBox, addArticleBtn);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setStyle(CARD_STYLE + "-fx-padding: 10 16 10 16;");
        return hero;
    }

    // ─────────────────────────────────────────────────────────────────
    // Main Body Split (Sidebar + Content View/Edit)
    // ─────────────────────────────────────────────────────────────────

    private Node buildBody() {
        // ── 1. Left Sidebar ──────────────────────────────────────────
        searchField.setPromptText("🔍  Search topics, markdown or author…");
        searchField.setStyle("-fx-background-color: #0E141F; -fx-text-fill: " + TEXT
                + "; -fx-prompt-text-fill: #5b6779;"
                + "-fx-background-radius: 6; -fx-border-color: #273449; -fx-border-radius: 6; -fx-border-width: 1;"
                + "-fx-padding: 7 10 7 10; -fx-font-size: 12px;");

        treeBox.setStyle("-fx-background-color: transparent;");
        treeScroll.setFitToWidth(true);
        treeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        treeScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        treeScroll.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        VBox.setVgrow(treeScroll, Priority.ALWAYS);

        VBox sidebar = new VBox(8, searchField, treeScroll);
        sidebar.setStyle(CARD_STYLE + "-fx-padding: 10;");
        sidebar.setPrefWidth(320);
        sidebar.setMinWidth(280);
        sidebar.setMaxWidth(400);
        VBox.setVgrow(sidebar, Priority.ALWAYS);

        // ── 2. Right Content Area ────────────────────────────────────
        buildViewMode();
        buildEditMode();

        contentArea.getChildren().setAll(viewModeContainer);
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        HBox.setHgrow(contentArea, Priority.ALWAYS);

        split = new HBox(12, sidebar, contentArea);
        split.setFillHeight(true);
        VBox.setVgrow(split, Priority.ALWAYS);
        return split;
    }

    // ─────────────────────────────────────────────────────────────────
    // View Mode Layout
    // ─────────────────────────────────────────────────────────────────

    private void buildViewMode() {
        viewModeContainer.setStyle(CARD_STYLE + "-fx-padding: 16 20 12 20;");
        VBox.setVgrow(viewModeContainer, Priority.ALWAYS);

        breadcrumbLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + GOLD + "; -fx-font-weight: bold;");

        contentTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + TEXT + ";");
        contentTitle.setWrapText(true);

        authorLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        contentSubtitle.setStyle("-fx-font-size: 12.5px; -fx-text-fill: " + MUTED + ";");
        contentSubtitle.setWrapText(true);

        editBtn.setStyle("-fx-background-color: #1E2B3E; -fx-text-fill: #E2E8F0; -fx-border-color: #31435F; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 14 6 14; -fx-font-size: 12px; -fx-cursor: hand;");
        editBtn.setOnAction(e -> startEditingCurrentArticle());

        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #F87171; -fx-border-color: #552B2B; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 12 6 12; -fx-font-size: 12px; -fx-cursor: hand;");
        deleteBtn.setOnAction(e -> confirmDeleteCurrentArticle());

        HBox actionBtns = new HBox(8, editBtn, deleteBtn);
        actionBtns.setAlignment(Pos.CENTER_RIGHT);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        VBox titleGroup = new VBox(3, breadcrumbLbl, contentTitle, authorLbl, contentSubtitle);
        HBox.setHgrow(titleGroup, Priority.ALWAYS);

        HBox headerRow = new HBox(12, titleGroup, headerSpacer, actionBtns);
        headerRow.setAlignment(Pos.TOP_LEFT);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #223044; -fx-padding: 2 0 2 0;");

        markdownBox.setStyle("-fx-background-color: transparent;");
        markdownBox.setPadding(new Insets(6, 4, 16, 2));

        articleScroll.setFitToWidth(true);
        articleScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        articleScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        articleScroll.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        VBox.setVgrow(articleScroll, Priority.ALWAYS);

        viewModeContainer.getChildren().setAll(headerRow, sep, articleScroll);
    }

    // ─────────────────────────────────────────────────────────────────
    // Edit Mode Layout
    // ─────────────────────────────────────────────────────────────────

    private void buildEditMode() {
        editModeContainer.setStyle(CARD_STYLE + "-fx-padding: 16 20 12 20;");
        VBox.setVgrow(editModeContainer, Priority.ALWAYS);

        editModeTitleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + GOLD + ";");

        // Category Path Field (Editable ComboBox)
        Label pathLbl = new Label("Topic Category Path (use / to nest levels, e.g. TSC / TSPL Language or AI Chatbot / 01. Architecture & Lifecycle):");
        pathLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #94A3B8;");

        pathCombo.setEditable(true);
        pathCombo.setMaxWidth(Double.MAX_VALUE);
        pathCombo.setStyle("-fx-background-color: #0E141F; -fx-border-color: #273449; -fx-border-radius: 6; "
                + "-fx-background-radius: 6; -fx-font-size: 12.5px;");

        // Title and Author Fields
        Label titleLbl = new Label("Article Title:");
        titleLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #94A3B8;");

        titleField.setPromptText("Enter a clear title…");
        titleField.setStyle("-fx-background-color: #0E141F; -fx-text-fill: #FFFFFF; -fx-border-color: #273449; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 7 10 7 10; -fx-font-size: 13px; -fx-font-weight: bold;");
        VBox titleBox = new VBox(4, titleLbl, titleField);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Label authorFieldLbl = new Label("Author / Contributor:");
        authorFieldLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #94A3B8;");

        authorField.setPromptText("Your name or team…");
        authorField.setStyle("-fx-background-color: #0E141F; -fx-text-fill: #E2E8F0; -fx-border-color: #273449; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 7 10 7 10; -fx-font-size: 12.5px;");
        authorField.setPrefWidth(220);
        VBox authorBox = new VBox(4, authorFieldLbl, authorField);

        HBox titleAuthorRow = new HBox(12, titleBox, authorBox);

        // Subtitle Field
        Label subLbl = new Label("Subtitle / Short Summary:");
        subLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #94A3B8;");

        subtitleField.setPromptText("Brief 1-sentence summary of this article…");
        subtitleField.setStyle("-fx-background-color: #0E141F; -fx-text-fill: #CBD5E1; -fx-border-color: #273449; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 10 6 10; -fx-font-size: 12px;");

        // Markdown Editor
        Label mdLbl = new Label("Markdown Content (supports headings, tables, code blocks, lists, notes):");
        mdLbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #94A3B8;");

        markdownEditor.setPromptText("Write your article in Markdown here…\n\n# Heading 1\n## Heading 2\n\n- Bullet points\n\n| Tables | Work | Too |\n|---|---|---|\n\n```tspl\nSIZE 432 dot,200 dot\n```\n\n> [!NOTE]\n> Notes and callouts\n");
        markdownEditor.setWrapText(true);
        markdownEditor.setStyle("-fx-background-color: #0A0E17; -fx-control-inner-background: #0A0E17; "
                + "-fx-text-fill: #E2E8F0; -fx-font-family: Consolas, 'Courier New', monospace; -fx-font-size: 12.5px; "
                + "-fx-border-color: #243247; -fx-border-radius: 6; -fx-background-radius: 6;");
        VBox.setVgrow(markdownEditor, Priority.ALWAYS);

        // Action Buttons
        saveBtn.setStyle("-fx-background-color: " + GOLD + "; -fx-text-fill: #0B0E13; -fx-font-weight: bold; "
                + "-fx-background-radius: 6; -fx-padding: 7 18 7 18; -fx-font-size: 12px; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> saveCurrentEdit());

        cancelBtn.setStyle("-fx-background-color: #1E2B3E; -fx-text-fill: #CBD5E1; -fx-border-color: #31435F; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 7 16 7 16; -fx-font-size: 12px; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> cancelEdit());

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        Label hintLbl = new Label("Saved globally in codebase (src/main/resources/knowledge/knowledge-hub.json) with author attribution.");
        hintLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        HBox footerBar = new HBox(10, hintLbl, footerSpacer, cancelBtn, saveBtn);
        footerBar.setAlignment(Pos.CENTER_LEFT);
        footerBar.setPadding(new Insets(6, 0, 0, 0));

        editModeContainer.getChildren().setAll(
                editModeTitleLbl,
                new VBox(4, pathLbl, pathCombo),
                titleAuthorRow,
                new VBox(4, subLbl, subtitleField),
                new VBox(4, mdLbl, markdownEditor),
                footerBar
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // Action Handlers
    // ─────────────────────────────────────────────────────────────────

    private void showArticle(KnowledgeArticle article) {
        this.currentArticle = article;
        this.isCreatingNew = false;

        breadcrumbLbl.setText(article.path().replace(" / ", "  ›  ").toUpperCase());
        contentTitle.setText(article.title());

        String dateStr = DateTimeFormatter.ofPattern("MMM d, yyyy")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(article.updatedAt()));
        authorLbl.setText("👤  By " + article.author() + "  ·  🕒  " + dateStr);

        contentSubtitle.setText(article.subtitle().isEmpty() ? "No description provided." : article.subtitle());

        markdownBox.getChildren().clear();
        Node renderedContent = ChatMarkdownRenderer.render(article.markdown(), false);
        markdownBox.getChildren().add(renderedContent);

        // Reset scroll position to top
        Platform.runLater(() -> articleScroll.setVvalue(0.0));

        contentArea.getChildren().setAll(viewModeContainer);
        rebuildTree();
    }

    private void startEditingCurrentArticle() {
        if (currentArticle == null) return;
        isCreatingNew = false;
        editModeTitleLbl.setText("Edit Article — " + currentArticle.title());

        pathCombo.setItems(FXCollections.observableArrayList(repo.getAllCategoryPaths()));
        pathCombo.setValue(currentArticle.path());

        titleField.setText(currentArticle.title());
        authorField.setText(currentArticle.author());
        subtitleField.setText(currentArticle.subtitle());
        markdownEditor.setText(currentArticle.markdown());

        contentArea.getChildren().setAll(editModeContainer);
    }

    private void startCreatingNewArticle(String defaultPath) {
        isCreatingNew = true;
        editModeTitleLbl.setText("Create New Documentation Article");

        pathCombo.setItems(FXCollections.observableArrayList(repo.getAllCategoryPaths()));
        String path = (defaultPath != null && !defaultPath.isBlank())
                ? defaultPath
                : (currentArticle != null ? currentArticle.path() : "TSC / General");
        pathCombo.setValue(path);

        titleField.clear();
        authorField.setText(System.getProperty("user.name", "InvoiceStudio Core"));
        subtitleField.clear();
        markdownEditor.setText("# Article Title\n\nOverview of this topic...\n\n### Key Concepts\n- Point 1\n- Point 2\n\n```tspl\n// Code sample\n```\n");

        contentArea.getChildren().setAll(editModeContainer);
    }

    private void saveCurrentEdit() {
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        if (title.isEmpty()) {
            titleField.setStyle(titleField.getStyle() + "-fx-border-color: #EF4444;");
            return;
        }

        String path = pathCombo.getValue() == null ? "" : pathCombo.getValue().trim();
        if (path.isEmpty()) path = "General";

        String author = authorField.getText() == null || authorField.getText().isBlank()
                ? System.getProperty("user.name", "InvoiceStudio Core")
                : authorField.getText().trim();

        String subtitle = subtitleField.getText() == null ? "" : subtitleField.getText().trim();
        String markdown = markdownEditor.getText() == null ? "" : markdownEditor.getText();

        KnowledgeArticle toSave;
        if (isCreatingNew || currentArticle == null) {
            String newId = "art_" + System.currentTimeMillis();
            toSave = new KnowledgeArticle(newId, path, title, subtitle, markdown, System.currentTimeMillis(), author);
        } else {
            toSave = currentArticle.withUpdates(path, title, subtitle, markdown, author);
        }

        repo.saveArticle(toSave);
        showArticle(toSave);
    }

    private void cancelEdit() {
        if (currentArticle != null) {
            showArticle(currentArticle);
        } else {
            List<KnowledgeArticle> all = repo.getAllArticles();
            if (!all.isEmpty()) showArticle(all.get(0));
        }
    }

    private void confirmDeleteCurrentArticle() {
        if (currentArticle == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Article");
        alert.setHeaderText("Delete '" + currentArticle.title() + "'?");
        alert.setContentText("Are you sure you want to delete this article? This action cannot be undone.");
        DialogHelper.styleDialog(alert);

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                repo.deleteArticle(currentArticle.id());
                List<KnowledgeArticle> remaining = repo.getAllArticles();
                if (!remaining.isEmpty()) {
                    showArticle(remaining.get(0));
                } else {
                    currentArticle = null;
                    markdownBox.getChildren().clear();
                    contentTitle.setText("No Articles");
                    contentSubtitle.setText("Click + Add Article to create your first article.");
                }
                rebuildTree();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // Multi-Level Tree Rendering
    // ─────────────────────────────────────────────────────────────────

    private void rebuildTree() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        treeBox.getChildren().clear();

        KnowledgeRepository.CategoryNode root = repo.buildCategoryTree();
        int[] count = {0};

        // Render each top level category under root (e.g. "TSC", "AI Chatbot")
        for (KnowledgeRepository.CategoryNode topCat : root.subCategories()) {
            if (topCat.matches(query)) {
                renderCategoryNode(topCat, query, 0, count);
            }
        }

        countChip.setText(count[0] + " topics");
    }

    private void renderCategoryNode(KnowledgeRepository.CategoryNode node, String query, int depth, int[] count) {
        boolean searching = !query.isEmpty();
        boolean isExp = searching || expandedCategories.contains(node.fullPath());

        treeBox.getChildren().add(buildGroupRow(node, depth, isExp));

        if (isExp) {
            // 1. Render child categories first
            for (KnowledgeRepository.CategoryNode sub : node.subCategories()) {
                if (sub.matches(query)) {
                    renderCategoryNode(sub, query, depth + 1, count);
                }
            }

            // 2. Render articles belonging to this category
            for (KnowledgeArticle art : node.articles()) {
                if (matchesQuery(art, query)) {
                    treeBox.getChildren().add(buildArticleRow(art, depth + 1));
                    count[0]++;
                }
            }
        } else {
            // Count collapsed matching articles as well
            countCollapsedArticles(node, query, count);
        }
    }

    private void countCollapsedArticles(KnowledgeRepository.CategoryNode node, String query, int[] count) {
        for (KnowledgeArticle art : node.articles()) {
            if (matchesQuery(art, query)) count[0]++;
        }
        for (KnowledgeRepository.CategoryNode sub : node.subCategories()) {
            countCollapsedArticles(sub, query, count);
        }
    }

    private boolean matchesQuery(KnowledgeArticle art, String query) {
        if (query.isEmpty()) return true;
        return art.title().toLowerCase(Locale.ROOT).contains(query)
                || art.subtitle().toLowerCase(Locale.ROOT).contains(query)
                || art.markdown().toLowerCase(Locale.ROOT).contains(query)
                || art.author().toLowerCase(Locale.ROOT).contains(query);
    }

    private Node buildGroupRow(KnowledgeRepository.CategoryNode group, int depth, boolean isExpanded) {
        Label arrow = new Label(isExpanded ? "▾" : "▸");
        arrow.setStyle("-fx-text-fill: " + GOLD + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        arrow.setMinWidth(14);

        Label name = new Label(group.name());
        boolean topLevel = depth == 0;
        name.setStyle("-fx-font-size: " + (topLevel ? "12px" : "12.5px") + "; -fx-font-weight: bold; -fx-text-fill: "
                + (topLevel ? GOLD : "#C8D2E0") + ";");

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);

        Label countLbl = new Label(String.valueOf(group.totalArticles()));
        countLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B; -fx-background-color: #1A2434; "
                + "-fx-background-radius: 4; -fx-padding: 1 5 1 5;");

        HBox row = new HBox(6, arrow, name, spring, countLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(topLevel ? 7 : 4, 6, topLevel ? 3 : 4, 6 + depth * 14));
        if (!topLevel) {
            row.setStyle("-fx-background-color: rgba(255,255,255,0.02); -fx-background-radius: 6;");
        }

        // Clicking ANYWHERE on the row toggles expand / collapse reliably
        row.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (expandedCategories.contains(group.fullPath())) {
                expandedCategories.remove(group.fullPath());
            } else {
                expandedCategories.add(group.fullPath());
            }
            rebuildTree();
        });
        row.setCursor(javafx.scene.Cursor.HAND);
        return row;
    }

    private Node buildArticleRow(KnowledgeArticle art, int depth) {
        boolean sel = currentArticle != null && currentArticle.id().equals(art.id());

        Label title = new Label(art.title());
        title.setStyle("-fx-font-size: 12px; -fx-font-weight: " + (sel ? "bold" : "normal") + "; "
                + "-fx-text-fill: " + (sel ? "#F0D9A8" : "#CBD5E1") + ";");
        title.setWrapText(true);

        VBox card = new VBox(2, title);
        card.setPadding(new Insets(5, 8, 5, 8));
        card.setStyle("-fx-background-color: " + (sel ? "#1A263B" : "transparent") + ";"
                + "-fx-background-radius: 6; -fx-border-radius: 6;"
                + (sel ? "-fx-border-color: " + GOLD + "; -fx-border-width: 0 0 0 3;" : ""));

        HBox holder = new HBox(card);
        HBox.setHgrow(card, Priority.ALWAYS);
        holder.setPadding(new Insets(1, 0, 1, 6 + depth * 14));
        holder.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> showArticle(art));
        holder.setCursor(javafx.scene.Cursor.HAND);
        return holder;
    }
}
