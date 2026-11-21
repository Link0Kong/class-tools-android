import com.android.apksig.ApkVerifier;
import com.heytap.msp.util.ApkSignatureUtils;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

/**
 * CLI wrapper around OPPO's official ApkSignatureUtils.getOPPOSign(byte[]).
 *
 * OPPOSign.exe is a JavaFX GUI; this class calls the same jar method headlessly so the
 * release pipeline can compute the value for every build.
 */
public final class OppoSignCli {
    private OppoSignCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: OppoSignCli <apk>");
            System.exit(2);
        }
        ApkVerifier.Result result = ApkSignatureUtils.getResult(args[0]);
        Map<String, List<X509Certificate>> certs = result.getSignerCertificates();
        if (certs == null || certs.isEmpty()) {
            System.err.println("NO_SIGNER_CERT");
            System.exit(2);
        }
        X509Certificate cert = certs.values().iterator().next().get(0);
        System.out.println(ApkSignatureUtils.getOPPOSign(cert.getEncoded()));
    }
}
