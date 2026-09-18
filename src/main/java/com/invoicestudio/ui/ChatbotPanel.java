package com.invoicestudio.ui;

import com.invoicestudio.service.AiChatClient;
import com.invoicestudio.service.AppExecutors;
import com.invoicestudio.service.ChatbotConfig;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The in-app AI chatbot overlay panel — designed after mainstream AI chats
 * (ChatGPT/Gemini), in the app's dark-gold theme:
 * <ul>
 *   <li>Header: gold sparkle avatar + "Assistant" title with provider/model
 *       subtitle; icon-only action buttons with tooltips (expand, clear, close).</li>
 *   <li>Messages: avatar rows — gold AI avatar left, slate user avatar right;
 *       rounded speech bubbles hugging their avatar corner.</li>
 *   <li>Suggestion chips under the greeting (dismissed on first send).</li>
 *   <li>Input: one rounded pill — borderless auto-growing text area, paperclip
 *       attach icon, circular gold send button; keyboard hint underneath.</li>
 * </ul>
 */
public class ChatbotPanel extends VBox {

    private static final String CARD = "-fx-background-color: #10161F; -fx-border-color: #273245;"
            + "-fx-border-width: 1 0 0 1;";
    private static final String USER_BUBBLE = "-fx-background-color: #2A2417; -fx-text-fill: #F2EBDD;"
            + "-fx-background-radius: 14 4 14 14; -fx-padding: 9 13 9 13; -fx-font-size: 13px;";
    private static final String AI_BUBBLE = "-fx-background-color: #17202E; -fx-text-fill: #E6EAF0;"
            + "-fx-background-radius: 4 14 14 14; -fx-padding: 9 13 9 13; -fx-font-size: 13px;";
    private static final String ERROR_BUBBLE = "-fx-background-color: rgba(220,38,38,0.15); -fx-text-fill: #f1b0b0;"
            + "-fx-background-radius: 4 14 14 14; -fx-padding: 9 13 9 13; -fx-font-size: 13px;";
    private static final String GOLD = "#D9A13B";

    // Material-design glyph paths (24×24 grid), sized per use.
    private static final String SVG_SEND = "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z";
    private static final String SVG_PAPERCLIP =
            "M16.5 6v11.5a4 4 0 0 1-8 0V5a2.5 2.5 0 0 1 5 0v10.5a1 1 0 0 1-2 0V6H10v9.5"
            + "a2.5 2.5 0 0 0 5 0V5a4 4 0 0 0-8 0v12.5a5.5 5.5 0 0 0 11 0V6h-1.5z";
    private static final String SVG_CLOSE =
            "M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z";
    private static final String SVG_TRASH =
            "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";
    private static final String SVG_EXPAND = "M6.41 6 5 7.41 9.58 12 5 16.59 6.41 18l6-6z M13 6l-1.41 1.41L16.17 12l-4.58 4.59L13 18l6-6z";
    private static final String SVG_PERSON =
            "M12 12c2.2 0 4-1.8 4-4s-1.8-4-4-4-4 1.8-4 4 1.8 4 4 4zm0 2c-2.7 0-8 1.3-8 4v2h16v-2c0-2.7-5.3-4-8-4z";
    private static final String SVG_COPY =
            "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z";
    private static final String SVG_CHECK =
            "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
    private static final String SVG_TERMINAL =
            "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8h16v10zm-2-1h-6v-2h6v2zM7.5 17l-1.41-1.41L8.67 13l-2.58-2.59L7.5 9l4 4-4 4z";

    private final StudioApp app;
    private final ChatbotConfig cfg;
    private final Runnable closeAction;
    private final List<AiChatClient.ChatTurn> history = new ArrayList<>();
    /** Registered once; removed when this panel closes (toggleChatbot creates
     *  a fresh panel each open — without removal the static listener list grew). */
    private final java.util.function.Consumer<ChatbotConfig> configListener =
            c -> javafx.application.Platform.runLater(this::refreshConfig);

    private final VBox messages = new VBox(12);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextArea input = new TextArea();
    private final Button sendBtn = new Button();
    private final Label providerLbl = new Label();
    private final javafx.scene.control.Button modelChip = new javafx.scene.control.Button();
    private AiChatClient.ImagePart pendingImage;
    private final HBox attachRow = new HBox(8);
    private final VBox chipsRow = new VBox(6);
    private boolean expanded = false;
    private com.invoicestudio.ui.chat.ChatbotLogDialog logDialog;
    /** Small animated “assistant at work” strip (character + tokens + progress
     *  bar). Hidden & unmanaged while idle; its two animations run only while
     *  a request is in flight. */
    private final com.invoicestudio.ui.chat.ChatPipelineBar pipeline =
            new com.invoicestudio.ui.chat.ChatPipelineBar();
    private final DoubleBinding maxBubbleWidth;

    public ChatbotPanel(StudioApp app, ChatbotConfig cfg, Runnable closeAction) {
        this.app = app;
        this.cfg = cfg;
        this.closeAction = closeAction;

        setStyle(CARD);
        setPadding(new Insets(12));
        setSpacing(8);
        setPrefWidth(460);
        setMinWidth(380);

        providerLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #97A3B6;");
        refreshProviderLabel();
        ChatbotConfig.addChangeListener(configListener);

        messages.setPadding(new Insets(6, 4, 6, 2));
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        maxBubbleWidth = Bindings.createDoubleBinding(() -> {
            double w = scroll.getWidth();
            if (w <= 0) return expanded ? 760.0 : 360.0;
            return Math.max(340.0, w - 70.0);
        }, scroll.widthProperty());

        getChildren().addAll(buildHeader(), new Separator(), scroll, buildInputArea());
        greeting();
    }

    // ── Small builders ────────────────────────────────────────────────

    private static Node svg(String content, double size, String color) {
        SVGPath p = new SVGPath();
        p.setContent(content);
        p.setFill(Color.web(color));
        p.setStyle("-fx-scale-x: " + (size / 24.0) + "; -fx-scale-y: " + (size / 24.0) + ";");
        return p;
    }

    private static Button iconBtn(String id, String path, String tooltip) {
        Button b = new Button();
        b.setId(id);
        b.getStyleClass().add("button-icon-subtle");
        b.setGraphic(svg(path, 16, "#97A3B6"));
        b.setTooltip(new Tooltip(tooltip));
        b.setMinSize(30, 30);
        b.setPrefSize(30, 30);
        b.setMaxSize(30, 30);
        return b;
    }

    private Button copyButton(String text) {
        Button b = new Button();
        b.getStyleClass().add("button-icon-subtle");
        b.setGraphic(svg(SVG_COPY, 12, "#7C8AA0"));
        b.setTooltip(new Tooltip("Copy"));
        b.setMinSize(22, 22);
        b.setPrefSize(22, 22);
        b.setMaxSize(22, 22);
        b.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 0;");
        b.setOnAction(e -> {
            javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(text == null ? "" : text);
            cb.setContent(cc);
            b.setGraphic(svg(SVG_CHECK, 12, GOLD));
            b.setTooltip(new Tooltip("Copied!"));
            javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
            pt.setOnFinished(ev -> {
                b.setGraphic(svg(SVG_COPY, 12, "#7C8AA0"));
                b.setTooltip(new Tooltip("Copy"));
            });
            pt.play();
        });
        return b;
    }

    /** Round avatar: gold sparkle for the AI, slate person for the user. */
    private StackPane avatar(boolean ai, double d) {
        StackPane c = new StackPane(ai
                ? IconHelper.getIcon(IconHelper.ICON_SPARKLES, 12, "#1A1408")
                : svg(SVG_PERSON, 14, "#C9D3E0"));
        c.setStyle("-fx-background-color: " + (ai ? GOLD : "#33415C") + "; -fx-background-radius: 999;");
        c.setMinSize(d, d);
        c.setPrefSize(d, d);
        c.setMaxSize(d, d);
        return c;
    }

    // ── Chrome ────────────────────────────────────────────────────────

    private Node buildHeader() {
        Label title = new Label("Assistant");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #F2F4F8;");

        // Model chip — click to pick a model from the LIVE catalogue
        // (fetched from the provider API; Gemini models.list verified).
        modelChip.setText(currentModelLabel());
        modelChip.getStyleClass().add("button-icon-subtle");
        modelChip.setStyle("-fx-font-size: 11px; -fx-text-fill: #97A3B6; -fx-padding: 3 8 3 8;");
        modelChip.setTooltip(new Tooltip("Change model (live list from your provider)"));
        modelChip.setOnAction(e -> showModelMenu());

        VBox titleBox = new VBox(1, title, new HBox(6, providerLbl, modelChip));
        titleBox.setAlignment(Pos.CENTER_LEFT);
        ((HBox) titleBox.getChildren().get(1)).setAlignment(Pos.CENTER_LEFT);

        Button logBtn = iconBtn("chat-logs", SVG_TERMINAL, "View background execution logs");
        logBtn.setOnAction(e -> showLogDialog());

        Button expandBtn = iconBtn("chat-expand", SVG_EXPAND,
                expanded ? "Collapse panel" : "Expand panel");
        expandBtn.setOnAction(e -> {
            expanded = !expanded;
            ((Tooltip) expandBtn.getTooltip()).setText(expanded ? "Collapse panel" : "Expand panel");
            double w = expanded ? Math.max(760, app.getRootPane().getWidth() - 120) : 460;
            setPrefWidth(w);
            setMaxWidth(w);
            updateInputHeight(input.getText());
        });

        Button clearBtn = iconBtn("chat-clear", SVG_TRASH, "Clear conversation");
        clearBtn.setOnAction(e -> {
            history.clear();
            messages.getChildren().clear();
            com.invoicestudio.service.ChatbotLogManager.clear();
            greeting();
        });

        Button closeBtn = iconBtn("chat-close", SVG_CLOSE, "Close chat");
        closeBtn.setOnAction(e -> {
            // Session logs are KEPT when the panel closes (they are the only
            // forensic trail of what the assistant executed — wiping them on
            // close is why "no logs" were found after a deletion). Only the
            // explicit Clear button empties them.
            ChatbotConfig.removeChangeListener(configListener);
            closeAction.run();
        });

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        HBox head = new HBox(8, avatar(true, 30), titleBox, spring, logBtn, expandBtn, clearBtn, closeBtn);
        head.setAlignment(Pos.CENTER_LEFT);
        return head;
    }

    private void showLogDialog() {
        if (logDialog == null) {
            logDialog = new com.invoicestudio.ui.chat.ChatbotLogDialog(app.getPrimaryStage());
        }
        logDialog.show();
    }

    private Node buildInputArea() {
        // Borderless auto-growing text area inside a rounded pill.
        input.setPromptText("Ask about your business…");
        input.setWrapText(true);
        input.setPrefRowCount(1);
        input.setMinHeight(36);
        input.setMaxHeight(92);
        input.setPrefHeight(36);
        input.setStyle("-fx-background-color: transparent; -fx-text-fill: #E6EAF0;"
                + "-fx-prompt-text-fill: #5b6779; -fx-border-color: transparent;"
                + "-fx-padding: 6 2 6 10; -fx-font-size: 13px;");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) {
                    e.consume();
                    input.replaceSelection("\n");
                } else {
                    e.consume();
                    send();
                }
            }
        });
        input.textProperty().addListener((o, a, t) -> updateInputHeight(t));

        Button attachBtn = iconBtn("chat-attach", SVG_PAPERCLIP, "Attach an image (bill, label, screenshot…)");
        attachBtn.setOnAction(e -> pickImage());

        sendBtn.setId("chat-send");
        sendBtn.setGraphic(svg(SVG_SEND, 15, "#1A1408"));
        sendBtn.setStyle("-fx-background-color: " + GOLD + "; -fx-background-radius: 999; -fx-cursor: hand;"
                + "-fx-background-insets: 0;");
        sendBtn.setTooltip(new Tooltip("Send  (Enter)"));
        sendBtn.setMinSize(34, 34);
        sendBtn.setPrefSize(34, 34);
        sendBtn.setMaxSize(34, 34);
        sendBtn.setOnAction(e -> send());

        HBox pill = new HBox(6, input, attachBtn, sendBtn);
        pill.setAlignment(Pos.CENTER_RIGHT);
        pill.setMinHeight(Region.USE_PREF_SIZE);
        pill.setStyle("-fx-background-color: #0F1520; -fx-border-color: #273245;"
                + "-fx-background-radius: 14; -fx-border-radius: 14; -fx-border-width: 1;"
                + "-fx-padding: 5 6 5 4;");

        Label hint = new Label("Enter to send · Shift+Enter for a new line");
        hint.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #5b6779;");
        hint.setAlignment(Pos.CENTER);
        hint.setMaxWidth(Double.MAX_VALUE);

        attachRow.setAlignment(Pos.CENTER_LEFT);
        attachRow.setManaged(false);
        attachRow.setVisible(false);

        VBox footer = new VBox(6, attachRow, pipeline, pill, hint);
        return footer;
    }

    private void updateInputHeight(String text) {
        if (text == null || text.isEmpty()) {
            applyInputHeight(1);
            return;
        }
        int lines = 1;
        int charsInLine = 0;
        int maxChars = expanded ? 72 : 36;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines++;
                charsInLine = 0;
            } else {
                charsInLine++;
                if (charsInLine >= maxChars) {
                    lines++;
                    charsInLine = 0;
                }
            }
            if (lines >= 3) {
                lines = 3;
                break;
            }
        }
        applyInputHeight(lines);
    }

    private void applyInputHeight(int lines) {
        double h = lines == 1 ? 36.0 : (lines == 2 ? 64.0 : 92.0);
        if (input.getPrefHeight() != h) {
            input.setPrefRowCount(lines);
            input.setMinHeight(h);
            input.setPrefHeight(h);
        }
    }

    public void refreshConfig() {
        refreshProviderLabel();
    }

    public void refreshProviderLabel() {
        providerLbl.setText(AiChatClient.providerLabel(cfg.getProvider()));
        modelChip.setText(currentModelLabel());
    }

    private String currentModelLabel() {
        return "· " + (cfg.getModel().isBlank()
                ? AiChatClient.defaultModel(cfg.getProvider()) : cfg.getModel());
    }

    /** Dropdown of chat-capable models fetched LIVE from the provider API. */
    private void showModelMenu() {
        javafx.scene.control.Label head = new javafx.scene.control.Label("Models (live from "
                + AiChatClient.providerLabel(cfg.getProvider()) + ")");
        head.setStyle("-fx-font-size: 11px; -fx-text-fill: #97A3B6; -fx-padding: 8 12 4 12;");
        VBox list = new VBox(2, head);
        list.setStyle("-fx-background-color: #131B28; -fx-border-color: #273245;"
                + "-fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");
        list.setPrefWidth(340);
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(list);
        sp.setFitToWidth(true);
        sp.setPrefHeight(320);
        sp.setStyle("-fx-background-color: transparent;");

        javafx.scene.control.Label loading = new javafx.scene.control.Label("Loading…");
        loading.setStyle("-fx-text-fill: #97A3B6; -fx-padding: 6 12 10 12;");
        list.getChildren().add(loading);

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.getContent().add(new javafx.scene.layout.StackPane(sp));
        popup.setAutoHide(true);

        com.invoicestudio.service.AppExecutors.io().execute(() -> {
            List<com.invoicestudio.service.ModelCatalog.ModelInfo> models = List.of();
            String error = null;
            try {
                if (com.invoicestudio.service.ModelCatalog.hasLiveCatalogue(cfg.getProvider())) {
                    models = com.invoicestudio.service.ModelCatalog.modelsFor(cfg.getProvider(), cfg.getApiKey());
                } else {
                    error = "Live catalogue is available for Gemini and Z.ai GLM; type a model id in Settings → Chatbot.";
                }
            } catch (Exception ex) {
                error = "Couldn't load catalogue: " + ex.getMessage();
            }
            final List<com.invoicestudio.service.ModelCatalog.ModelInfo> fm = models;
            final String ferr = error;
            javafx.application.Platform.runLater(() -> {
                list.getChildren().remove(loading);
                if (ferr != null) {
                    javafx.scene.control.Label err = new javafx.scene.control.Label(ferr);
                    err.setWrapText(true);
                    err.setStyle("-fx-text-fill: #f1b0b0; -fx-font-size: 11px; -fx-padding: 4 12 10 12;");
                    list.getChildren().add(err);
                    return;
                }
                String current = cfg.getModel().isBlank()
                        ? AiChatClient.defaultModel(cfg.getProvider()) : cfg.getModel();
                for (com.invoicestudio.service.ModelCatalog.ModelInfo mi : fm) {
                    boolean active = mi.id().equals(current);
                    javafx.scene.control.Button b = new javafx.scene.control.Button(
                            (active ? "✓  " : "     ") + mi.displayName());
                    b.setStyle("-fx-background-color: transparent; -fx-text-fill: "
                            + (active ? GOLD : "#C7D0DE") + "; -fx-alignment: CENTER_LEFT;"
                            + "-fx-padding: 6 12 6 12; -fx-font-size: 12.5px; -fx-cursor: hand;"
                            + "-fx-max-width: 9999;");
                    b.setOnMouseEntered(ev -> b.setStyle(b.getStyle()
                            .replace("transparent", "#1E2A3C")));
                    b.setOnMouseExited(ev -> b.setStyle(b.getStyle()
                            .replace("#1E2A3C", "transparent")));
                    b.setOnAction(ev -> {
                        cfg.setModel(mi.id().equals(current) ? "" : mi.id());
                        cfg.save();
                        refreshProviderLabel();
                        popup.hide();
                    });
                    list.getChildren().add(b);
                }
            });
        });

        popup.show(app.getPrimaryStage());
        // Anchor under the chip.
        javafx.geometry.Point2D p = modelChip.localToScreen(0, modelChip.getHeight() + 4);
        if (p != null) {
            popup.setX(Math.max(8, p.getX() - 40));
            popup.setY(p.getY());
        }
    }

    // ── Conversation flow ────────────────────────────────────────────

    private void greeting() {
        boolean configured = !cfg.getApiKey().isBlank();
        addAiBubble(configured
                ? "Hi! I can read your live business data — try a suggestion below, or ask anything."
                : "Welcome! To enable me, open Settings → Chatbot and save your API key. "
                + "I support Gemini, OpenAI, Claude and more — including local models via Ollama.",
                null);
        if (!configured) return;
        chipsRow.getChildren().clear();
        for (String q : new String[]{
                "Top 5 buyers by outstanding",
                "Items with stock below 10 pcs",
                "This month's sales total"}) {
            Button chip = new Button(q);
            chip.setStyle("-fx-background-color: #17202E; -fx-text-fill: #C7D0DE; -fx-background-radius: 999;"
                    + "-fx-border-color: #273245; -fx-border-radius: 999; -fx-padding: 6 14 6 14;"
                    + "-fx-font-size: 12px; -fx-cursor: hand; -fx-alignment: CENTER_LEFT;");
            chip.setGraphic(IconHelper.getIcon(IconHelper.ICON_SPARKLES, 10, GOLD));
            chip.setOnAction(e -> {
                input.setText(q);
                send();
            });
            chip.setOnMouseEntered(ev -> chip.setStyle(chip.getStyle().replace("#17202E", "#1E2A3C")));
            chip.setOnMouseExited(ev -> chip.setStyle(chip.getStyle().replace("#1E2A3C", "#17202E")));
            chipsRow.getChildren().add(chip);
        }
        messages.getChildren().add(chipsRow);
    }

    private void pickImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Attach image");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp"));
        File f = fc.showOpenDialog(app.getPrimaryStage());
        if (f == null) return;
        try {
            byte[] data = Files.readAllBytes(f.toPath());
            String name = f.getName().toLowerCase();
            String mime = name.endsWith(".png") ? "image/png"
                    : name.endsWith(".gif") ? "image/gif"
                    : name.endsWith(".webp") ? "image/webp"
                    : name.endsWith(".bmp") ? "image/bmp" : "image/jpeg";
            pendingImage = new AiChatClient.ImagePart(mime, data);
            renderPendingImage();
        } catch (Exception ex) {
            addAiBubble("Couldn't read that image: " + ex.getMessage(), null, true);
        }
    }

    private void renderPendingImage() {
        attachRow.getChildren().clear();
        if (pendingImage == null) {
            attachRow.setManaged(false);
            attachRow.setVisible(false);
            return;
        }
        ImageView thumb = new ImageView(new Image(new ByteArrayInputStream(pendingImage.data())));
        thumb.setFitHeight(56);
        thumb.setPreserveRatio(true);
        thumb.setStyle("-fx-background-radius: 8;");
        Label name = new Label("Image attached — sends with your next message");
        name.setStyle("-fx-font-size: 11px; -fx-text-fill: " + GOLD + ";");
        Button remove = iconBtn("chat-attach-remove", SVG_CLOSE, "Remove attachment");
        remove.setOnAction(e -> { pendingImage = null; renderPendingImage(); });
        attachRow.getChildren().addAll(thumb, name, remove);
        attachRow.setManaged(true);
        attachRow.setVisible(true);
    }

    private void send() {
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty() && pendingImage == null) return;
        if (chipsRow.getParent() != null) messages.getChildren().remove(chipsRow);
        if (!cfg.getApiKey().isBlank()) {
            history.add(pendingImage != null
                    ? AiChatClient.ChatTurn.userImage(text, pendingImage)
                    : AiChatClient.ChatTurn.user(text));
        }

        // Echo the user's message (with attachment thumbnail) into the chat.
        VBox box = new VBox(2);
        if (!text.isEmpty()) {
            Label l = new Label(text);
            l.setWrapText(true);
            l.setStyle(USER_BUBBLE);
            l.setMinHeight(Region.USE_PREF_SIZE);
            l.maxWidthProperty().bind(maxBubbleWidth);

            Button copyBtn = copyButton(text);
            HBox actionRow = new HBox(5, metaLabel(nowTime()), copyBtn);
            actionRow.setAlignment(Pos.CENTER_RIGHT);
            actionRow.setPadding(new Insets(1, 4, 0, 0));

            box.getChildren().addAll(l, actionRow);
        }
        if (pendingImage != null) {
            ImageView img = new ImageView(new Image(new ByteArrayInputStream(pendingImage.data())));
            img.setFitWidth(200);
            img.setPreserveRatio(true);
            img.setStyle("-fx-background-radius: 10;");
            box.getChildren().add(0, img);
        }
        HBox userRow = new HBox(8, box, avatar(false, 26));
        userRow.setAlignment(Pos.TOP_RIGHT);
        messages.getChildren().add(userRow);

        input.clear();
        updateInputHeight("");
        pendingImage = null;
        renderPendingImage();
        scrollToBottom();

        if (cfg.getApiKey().isBlank()) {
            addAiBubble("No API key configured — open Settings → Chatbot to set one up.", null, true);
            return;
        }

        // Busy indicator (thinking dots) + network call off the FX thread.
        // The dots double as a LIVE progress line: while the tool loop runs
        // (router → model → MCP tools → model …) the latest execution step is
        // streamed into the label, so a slow multi-request turn never looks
        // like a silent hang ("asked to delete — it got stuck, no logs").
        Label busy = new Label("●  ●  ●");
        busy.setStyle("-fx-font-size: 11px; -fx-text-fill: #7C8AA0; -fx-padding: 8 0 0 36;");
        messages.getChildren().add(busy);
        scrollToBottom();
        sendBtn.setDisable(true);

        pipeline.begin(); // animated character + token coins + progress bar

        java.util.function.Consumer<com.invoicestudio.service.ChatbotLogManager.LogEntry> progress =
                entry -> {
                    String step = progressText(entry);
                    if (!step.isEmpty()) busy.setText(step);
                    pipeline.onLog(entry);
                };
        com.invoicestudio.service.ChatbotLogManager.addListener(progress);

        List<AiChatClient.ChatTurn> snapshot = new ArrayList<>(history);
        Task<AiChatClient.ChatResult> task = new Task<>() {
            @Override protected AiChatClient.ChatResult call() throws Exception {
                // Snapshot already includes the user turn; hand everything except it —
                // send() re-appends the newest user turn itself.
                return new AiChatClient().send(cfg, snapshot.subList(0, snapshot.size() - 1),
                        text, imageOf(snapshot));
            }
        };
        task.setOnSucceeded(e -> AppExecutors.runOnFx(() -> {
            com.invoicestudio.service.ChatbotLogManager.removeListener(progress);
            messages.getChildren().remove(busy);
            sendBtn.setDisable(false);
            pipeline.end(true);
            AiChatClient.ChatResult r = task.getValue();
            if (!r.toolTrace().isEmpty()) {
                Label trace = new Label("🔧  " + String.join("  ·  ", r.toolTrace()));
                trace.setWrapText(true);
                trace.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #7C8AA0; -fx-padding: 0 0 0 36;");
                messages.getChildren().add(trace);
            }
            addAiBubble(r.text(), null, false, resultMeta(r));
            history.add(AiChatClient.ChatTurn.assistant(r.text()));
        }));
        task.setOnFailed(e -> AppExecutors.runOnFx(() -> {
            com.invoicestudio.service.ChatbotLogManager.removeListener(progress);
            messages.getChildren().remove(busy);
            sendBtn.setDisable(false);
            pipeline.end(false);
            Throwable ex = task.getException();
            addAiBubble(friendlyError(ex), null, true, nowTime());
        }));
        AppExecutors.chat().execute(task);
    }

    /** One-line busy-label text for a log step (empty = keep previous). */
    private static String progressText(com.invoicestudio.service.ChatbotLogManager.LogEntry e) {
        if (e == null || e.tag() == null) return "";
        return switch (e.tag()) {
            case "ROUTER" -> "●  Thinking…";
            case "DISPATCH" -> "●  " + shortenProgress(e.message(), 52);
            case "TOOL-CALL" -> "●  " + shortenProgress(e.message(), 52);
            case "MCP-EXEC" -> "●  " + shortenProgress(e.message(), 52);
            case "SUCCESS" -> "●  Finishing…";
            case "WARN", "ERROR" -> "●  " + shortenProgress(e.message(), 52);
            default -> "";
        };
    }

    private static String shortenProgress(String s, int max) {
        if (s == null) return "";
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > max ? flat.substring(0, max) + "…" : flat;
    }

    /**
     * Human-readable failure text. When the MCP tool server is off (or the
     * failure mentions the tool layer) the message points at Settings →
     * MCP Server instead of showing a raw stacktrace string — the assistant
     * must never go silent or leave the user with "null".
     */
    private static String friendlyError(Throwable ex) {
        String raw = ex == null ? null : String.valueOf(ex.getMessage());
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            raw = ex == null ? "unknown error" : ex.getClass().getSimpleName();
        }
        boolean mcpRelated = raw.toLowerCase().contains("mcp")
                || raw.contains("DataManager")
                || raw.contains("McpToolRegistry")
                || raw.contains("tool");
        if (mcpRelated && !com.invoicestudio.mcp.McpServer.isRunning()) {
            return "The assistant couldn't run its business-data tools because the MCP server is not "
                    + "running. Please start MCP by going to Settings → MCP Server (switch it on, or "
                    + "enable Auto-start), then ask again.\n\nTechnical detail: " + raw;
        }
        if (mcpRelated) {
            return "A business-data tool failed while answering. The MCP server is running, so this is "
                    + "likely temporary — please try again.\n\nTechnical detail: " + raw;
        }
        return raw;
    }

    /** The attachment of the newest user turn (for the in-flight request). */
    private AiChatClient.ImagePart imageOf(List<AiChatClient.ChatTurn> snapshot) {
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            AiChatClient.ChatTurn t = snapshot.get(i);
            if ("user".equals(t.role())) return t.image();
        }
        return null;
    }

    /** Greeting / simple notices — no meta row beyond the copy button. */
    private void addAiBubble(String text, javafx.scene.image.Image img) {
        addAiBubble(text, img, false, null);
    }

    /** Error-styled bubble (explicit flag — no text sniffing). */
    private void addAiBubble(String text, javafx.scene.image.Image img, boolean isError) {
        addAiBubble(text, img, isError, null);
    }

    /**
     * Assistant bubble with a compact meta row: copy button, local time and —
     * when the provider reported usage — ↑input ↓output tokens + wall time,
     * all in 10px muted text so the chat stays clean.
     */
    private void addAiBubble(String text, javafx.scene.image.Image img, boolean isError, String meta) {
        VBox box = new VBox(2);

        Node contentNode = com.invoicestudio.ui.chat.ChatMarkdownRenderer.render(text, isError);
        VBox bubble = new VBox(contentNode);
        bubble.setStyle(isError ? ERROR_BUBBLE : AI_BUBBLE);
        bubble.setMinHeight(Region.USE_PREF_SIZE);
        bubble.maxWidthProperty().bind(maxBubbleWidth);

        Button copyBtn = copyButton(text);
        HBox actionRow;
        if (meta != null && !meta.isBlank()) {
            actionRow = new HBox(5, metaLabel(meta), copyBtn);
        } else {
            actionRow = new HBox(copyBtn);
        }
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.setPadding(new Insets(1, 0, 0, 4));

        box.getChildren().addAll(bubble, actionRow);

        HBox row = new HBox(8, avatar(true, 26), box);
        row.setAlignment(Pos.TOP_LEFT);
        messages.getChildren().add(row);
        scrollToBottom();
    }

    /** Tiny muted caption (time · tokens · duration) beside the copy icon. */
    private static Label metaLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 10px; -fx-text-fill: #5b6779; -fx-padding: 0 2 0 2;");
        return l;
    }

    /** Local HH:mm timestamp used on every bubble's meta row. */
    private static String nowTime() {
        return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** "12:34 · ↑1,234 ↓567 tok · 3.2s" — only the parts the provider reported. */
    private static String resultMeta(AiChatClient.ChatResult r) {
        StringBuilder sb = new StringBuilder(nowTime());
        if (r.promptTokens() >= 0 || r.completionTokens() >= 0) {
            sb.append(" · ↑").append(r.promptTokens() < 0 ? "—" : String.format("%,d", r.promptTokens()))
              .append(" ↓").append(r.completionTokens() < 0 ? "—" : String.format("%,d", r.completionTokens()))
              .append(" tok");
        }
        if (r.elapsedMs() >= 0) {
            sb.append(" · ").append(r.elapsedMs() < 1000
                    ? r.elapsedMs() + " ms"
                    : String.format("%.1f s", r.elapsedMs() / 1000.0));
        }
        return sb.toString();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scroll.setVvalue(1.0));
    }
}
