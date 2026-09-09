/**
 * Bootstrap for the deep navigation smoke harness.
 *
 * The `java` launcher refuses to start a class that extends
 * javafx.application.Application when JavaFX is on the classpath (not module
 * path) — "JavaFX runtime components are missing". Production avoids this via
 * com.invoicestudio.Launcher; the harness uses the identical pattern.
 */
public class SmokeLauncher {
    public static void main(String[] args) {
        NavSmokeRunner.main(args);
    }
}
