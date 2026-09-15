import com.invoicestudio.service.TsplPipelineVerify;

/** Bootstrap so `java -cp` can start the TSPL verify harness (same as SmokeLauncher). */
public class TsplVerifyLauncher {
    public static void main(String[] args) {
        try {
            TsplPipelineVerify.main(args);
        } catch (Exception e) {
            System.err.println("[CRASH] " + e);
            e.printStackTrace();
            System.exit(4);
        }
    }
}
