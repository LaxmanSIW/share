/**
 * Bootstrap for MerchantTour — the `java` launcher refuses to start a class
 * that extends javafx.application.Application when JavaFX is on the classpath
 * (not module path). Identical pattern to SmokeLauncher / BulkVerifyLauncher.
 */
public class MerchantTourLauncher {
    public static void main(String[] args) {
        MerchantTour.main(args);
    }
}
