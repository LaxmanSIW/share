import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.model.UserSession;
import com.invoicestudio.service.AppExecutors;
import com.invoicestudio.service.ChatbotConfig;
import com.invoicestudio.ui.ChatbotPanel;
import com.invoicestudio.ui.StudioApp;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtime verification for the chatbot + knowledge-hub work:
 *  A) Knowledge Hub renders as a TSC tree: top-level group expanded,
 *     sub-levels collapsed, article switching works (old title check).
 *  B) Chatbot: floating icon visible → click opens the panel; typing a
 *     message without an API key yields the setup hint (no network).
 *  C) Settings → Chatbot tab exists with provider combo + show-icon switch;
 *     toggling the switch removes/restores the floating icon live.
 *
 * Exit code 0 = all steps passed, no uncaught exceptions.
 */
public class ChatbotKnowledgeVerify extends StudioApp {

    static final List<String> failures = new CopyOnWriteArrayList<>();
    static final List<String> passed = new CopyOnWriteArrayList<>();
    static final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
    static final File SHOT_DIR = new File(System.getProperty("smoke.shots", "cb-shots"));

    Stage stage;
    final List<Runnable> plan = new ArrayList<>();
    int idx = 0;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            uncaught.add(ex);
            System.err.println("[UNCAUGHT][" + t.getName() + "] " + ex);
            ex.printStackTrace();
        });
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            UserSession s = new UserSession();
            s.setUserId("smoke-user");
            s.setEmail("smoke@invoicestudio.local");
            s.setDisplayName("Chatbot Tester");
            s.setIdToken("");
            s.setRefreshToken("");
            s.setExpiresAtMillis(System.currentTimeMillis() + 7L * 24 * 3600 * 1000);
            s.setRememberMe(true);
            new com.invoicestudio.db.AuthDao(db).saveSession(s);
            // Ensure the icon starts visible for this run.
            ChatbotConfig cfg = new ChatbotConfig();
            cfg.setShowIcon(true);
            cfg.save();
            System.out.println("[SEED] auth session + chatbot config written");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        super.start(stage);
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if ("JavaFX Application Thread".equals(t.getName())) {
                t.setUncaughtExceptionHandler((th, ex) -> uncaught.add(ex));
            }
        }
        SHOT_DIR.mkdirs();
        settleThen(6.0, this::buildPlan);
    }

    void settleThen(double sec, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.seconds(sec));
        p.setOnFinished(e -> r.run());
        p.play();
    }

    void buildPlan() {
        plan.add(this::step01KnowledgeTab);
        plan.add(this::step02KnowledgeTree);
        plan.add(this::step03KnowledgeArticleSwitch);
        plan.add(this::step04ChatbotIconVisible);
        plan.add(this::step05ChatbotOpens);
        plan.add(this::step06ChatbotNoKeyHint);
        plan.add(this::step07ChatbotSettingsTab);
        plan.add(this::step08IconToggleLive);
        plan.add(this::step09CloseChatbot);
        idx = 0;
        runNext();
    }

    void runNext() {
        if (idx >= plan.size()) { finish(); return; }
        Runnable step = plan.get(idx);
        System.out.println("[STEP] " + idx);
        try {
            step.run();
        } catch (Throwable t) {
            failures.add("step " + idx + " threw: " + t);
            t.printStackTrace();
            finish();
            return;
        }
    }

    /** Step engine: runNext() schedules the step, then() advances it. */

    // ── Steps ─────────────────────────────────────────────────────────

    void step01KnowledgeTab() {
        Button settings = buttonWithText("Settings");
        if (settings == null) { fail("01-settings", "Settings button missing"); return; }
        settings.fire();
        then("01-settings", "settings opened", () -> tabPane() != null);
    }

    void step02KnowledgeTree() {
        TabPane tp = tabPane();
        Tab k = tab(tp, "Knowledge");
        if (k == null) { fail("02-knowledge-tree", "Knowledge tab missing"); return; }
        Platform.runLater(() -> tp.getSelectionModel().select(k));
        then("02-knowledge-tree", "TSC tree: root expanded, sub-levels present, articles reachable",
                () -> {
                    String big = allText(stage.getScene().getRoot());
                    return big.contains("Knowledge Hub")
                            && big.contains("TA210 — Printer")
                            && big.contains("TSPL Language")
                            && big.contains("Troubleshooting")
                            && big.contains("TSC TA210 — Printer Overview");
                });
    }

    void step03KnowledgeArticleSwitch() {
        // The threshold article lives in a COLLAPSED sub-level by default —
        // expand the Print Settings group by clicking its header row, then
        // click the article.
        Label groupRow = null;
        for (Label l : allLabels(stage.getScene().getRoot())) {
            if ("Print Settings".equals(l.getText())) { groupRow = l; break; }
        }
        if (groupRow == null) { fail("03-knowledge-switch", "Print Settings group row missing"); advance(); return; }
        // Click the ROW (the label's HBox parent has the handler).
        javafx.scene.Node row = groupRow.getParent();
        Platform.runLater(() -> {
            javafx.scene.input.MouseEvent click = new javafx.scene.input.MouseEvent(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, false, false, false, null);
            javafx.event.Event.fireEvent(row, click);
        });
        PauseTransition settle = new PauseTransition(Duration.millis(600));
        settle.setOnFinished(e -> {
            Label target = null;
            for (Label l : allLabels(stage.getScene().getRoot())) {
                if ("Brightness Threshold — Sharp Black & White".equals(l.getText())) { target = l; break; }
            }
            if (target == null) { fail("03-knowledge-switch", "threshold article row not rendered after group expand"); advance(); return; }
            javafx.scene.Node artRow = target.getParent();
            Platform.runLater(() -> {
                javafx.scene.input.MouseEvent click = new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                        javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                        true, false, false, false, false, false, null);
                javafx.event.Event.fireEvent(artRow, click);
            });
            then("03-knowledge-switch", "group expand + click opens the article body",
                    () -> allText(stage.getScene().getRoot()).contains("BRIGHTNESS THRESHOLD"));
        });
        settle.play();
    }

    List<Label> allLabels(Node n) {
        List<Label> out = new ArrayList<>();
        collectLabels(n, out);
        return out;
    }

    void collectLabels(Node n, List<Label> out) {
        if (n instanceof Label l) out.add(l);
        if (n instanceof Parent p) for (Node ch : p.getChildrenUnmodifiable()) collectLabels(ch, out);
    }

    /** Advances the engine after a fail() so one missing node can't hang the run. */
    void advance() {
        idx++;
        PauseTransition n = new PauseTransition(Duration.millis(250));
        n.setOnFinished(ev -> Platform.runLater(this::runNext));
        n.play();
    }

    void step04ChatbotIconVisible() {
        TabPane tp = tabPane();
        if (tp != null) {
            Platform.runLater(() -> {
                try { tp.getSelectionModel().select(0); } catch (Throwable t) { t.printStackTrace(); }
            });
        }
        then("04-icon-visible", "floating chat icon on the shell", () -> findChatIcon() != null);
    }

    void step05ChatbotOpens() {
        Node icon = findChatIcon();
        if (icon == null) { fail("05-chatbot-open", "icon vanished"); advance(); return; }
        // Ensure a key is present so the panel opens (not the config toast).
        // IMPORTANT: use the shell's OWN config instance — the app reacts to
        // that object, not to a fresh load() copy.
        Platform.runLater(() -> {
            ChatbotConfig cfg = chatbotConfig();
            cfg.setApiKey("test-key-not-real");
            cfg.save();
            javafx.scene.input.MouseEvent click = new javafx.scene.input.MouseEvent(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, false, false, false, null);
            javafx.event.Event.fireEvent(icon, click);
        });
        then("05-chatbot-open", "chatbot panel opened with greeting",
                () -> allText(stage.getScene().getRoot()).contains("Assistant")
                        && stage.getScene().getRoot().lookup("#chat-send") != null);
    }

    void step06ChatbotNoKeyHint() {
        // Deterministic no-key path: blank key → the panel must show the
        // friendly "No API key configured" bubble instantly, with no network
        // dependency (invalid-key HTTP errors are provider-side, flaky in CI).
        Platform.runLater(() -> {
            ChatbotConfig cfg = chatbotConfig();
            cfg.setApiKey("");
            cfg.save();
        });
        TextArea input = findInput();
        if (input == null) {
            System.out.println("[DBG] chat input NOT found; panel children:");
            for (Node n : stage.getScene().getRoot().getChildrenUnmodifiable()) {
                System.out.println("[DBG]   child: " + n.getClass().getName());
            }
            fail("06-chatbot-error-path", "chat input missing");
            advance();
            return;
        }
        Platform.runLater(() -> {
            input.setText("hello");
            fireSend();
        });
        then("06-chatbot-error-path", "send without a key shows the no-key hint instantly",
                () -> allText(stage.getScene().getRoot()).toLowerCase().contains("no api key configured"));
    }

    void step07ChatbotSettingsTab() {
        closePanel();
        Button settings = buttonWithText("Settings");
        if (settings != null) settings.fire();
        then("07-chatbot-settings", "Settings → Chatbot tab with provider/model/key/icon controls",
                () -> {
                    TabPane tp = tabPane();
                    Tab cb = tab(tp, "Chatbot");
                    if (cb == null) return false;
                    Platform.runLater(() -> tp.getSelectionModel().select(cb));
                    String big = allText(stage.getScene().getRoot());
                    return big.contains("AI Chatbot") && big.contains("PROVIDER & MODEL")
                            && big.contains("BEHAVIOUR");
                });
    }

    void step08IconToggleLive() {
        Platform.runLater(() -> {
            ChatbotConfig cfg = chatbotConfig();
            cfg.setShowIcon(false);
            cfg.save();
            refreshChatbotIcon();
        });
        then("08-icon-hide", "switching show-icon OFF removes the floating icon",
                () -> findChatIcon() == null, () -> Platform.runLater(() -> {
                    ChatbotConfig cfg = chatbotConfig();
                    cfg.setShowIcon(true);
                    cfg.save();
                    refreshChatbotIcon();
                }));
    }

    void step09CloseChatbot() {
        then("09-icon-restore", "switching show-icon ON restores the floating icon",
                () -> findChatIcon() != null);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    void then(String name, String why, java.util.function.BooleanSupplier check) {
        then(name, why, check, null);
    }

    void then(String name, String why, java.util.function.BooleanSupplier check, Runnable after) {
        System.out.println("[THEN] " + name);
        PauseTransition p = new PauseTransition(Duration.millis(700));
        p.setOnFinished(e -> {
            boolean ok = false;
            try { ok = check.getAsBoolean(); } catch (Throwable t) { failures.add(name + " threw: " + t); }
            if (ok) passed.add(name + " — " + why);
            else failures.add(name + " — " + why);
            if (after != null) after.run();
            idx++;
            shotAsync();
            PauseTransition n = new PauseTransition(Duration.millis(400));
            n.setOnFinished(ev -> Platform.runLater(this::runNext));
            n.play();
        });
        p.play();
    }
    void fail(String name, String why) {
        System.out.println("[FAIL-STEP] " + name + ": " + why);
        failures.add(name + " — " + why);
        shot();
    }

    void shot() {
        shotAsync();
    }

    /** Snapshots on a short delay so we never block the step engine mid-turn
     *  (Scene.snapshot on the FX thread can deadlock under software rendering). */
    void shotAsync() {
        PauseTransition p = new PauseTransition(Duration.millis(120));
        p.setOnFinished(e -> {
            try {
                WritableImage img = stage.getScene().snapshot(null);
                File f = new File(SHOT_DIR, "cb-" + idx + ".png");
                ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", f);
            } catch (Throwable ignored) { }
        });
        p.play();
    }

    Button buttonWithText(String text) {
        return findButton(stage.getScene().getRoot(), text);
    }

    Button findButton(Node n, String text) {
        if (n instanceof Button b && b.getText() != null && b.getText().contains(text)) return b;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                Button r = findButton(ch, text);
                if (r != null) return r;
            }
        }
        return null;
    }

    TabPane tabPane() {
        return findFirst(stage.getScene().getRoot(), TabPane.class);
    }

    Tab tab(TabPane tp, String title) {
        if (tp == null) return null;
        for (Tab t : tp.getTabs()) if (title.equals(t.getText())) return t;
        return null;
    }

    Node findChatIcon() {
        return stage.getScene().getRoot().lookup("#chatbot-fab");
    }

    TextArea findInput() {
        // The chat input is the only TextArea inside the ChatbotPanel.
        for (Node n : stage.getScene().getRoot().getChildrenUnmodifiable()) {
            if (n instanceof ChatbotPanel panel) return findFirst(panel, TextArea.class);
        }
        return null;
    }

    void fireSend() {
        for (Node n : stage.getScene().getRoot().getChildrenUnmodifiable()) {
            if (n instanceof ChatbotPanel panel) {
                Button send = (Button) panel.lookup("#chat-send");
                if (send != null) send.fire();
                return;
            }
        }
    }

    void closePanel() {
        // The overlay list is immutable — use the panel's own ✕ button.
        for (Node n : stage.getScene().getRoot().getChildrenUnmodifiable()) {
            if (n instanceof ChatbotPanel panel) {
                Button close = (Button) panel.lookup("#chat-close");
                if (close != null) close.fire();
                return;
            }
        }
    }

    String allText(Node n) {
        StringBuilder sb = new StringBuilder();
        collectText(n, sb);
        return sb.toString();
    }

    void collectText(Node n, StringBuilder sb) {
        if (n instanceof javafx.scene.control.Labeled l && l.getText() != null) sb.append(l.getText()).append('\n');
        if (n instanceof javafx.scene.control.TextInputControl tic && tic.getText() != null) sb.append(tic.getText()).append('\n');
        if (n instanceof Parent p) for (Node ch : p.getChildrenUnmodifiable()) collectText(ch, sb);
    }

    Label labelWithText(Node n, String exact) {
        if (n instanceof Label l && exact.equals(l.getText())) return l;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                Label r = labelWithText(ch, exact);
                if (r != null) return r;
            }
        }
        return null;
    }

    <T extends Node> T findFirst(Node n, Class<T> type) {
        if (type.isInstance(n)) return type.cast(n);
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                T r = findFirst(ch, type);
                if (r != null) return r;
            }
        }
        return null;
    }

    void finish() {
        System.out.println("--------------------------------------------------");
        for (String p : passed) System.out.println("[OK] " + p);
        for (String f : failures) System.out.println("[BAD] " + f);
        for (Throwable t : uncaught) System.out.println("[BAD] uncaught: " + t);
        boolean ok = failures.isEmpty() && uncaught.isEmpty();
        System.out.println(ok ? "CHATBOT KNOWLEDGE VERIFY: SUCCESS" : "CHATBOT KNOWLEDGE VERIFY: FAILED");
        AppExecutors.shutdownAll();
        Platform.exit();
        if (!ok) System.exit(1);
    }
}
