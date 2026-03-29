package com.onecritto.licensing;


import com.onecritto.i18n.I18n;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Gestisce la verifica della licenza trial firmata RSA.
 *
 * Importante:
 *  - In APP deve esserci SOLO la chiave pubblica.
 *  - La chiave privata serve per generare i file di licenza (tool esterno).
 */
public final class TrialLicenseManager {

    // Sostituisci questa stringa con la TUA chiave pubblica RSA in formato X.509, Base64
    // (senza header/footer "-----BEGIN PUBLIC KEY-----").
    private static final String PUBLIC_KEY_BASE64 =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw7QdCduamlqUxfUBqjdHuOz1f7Y5sz0lIPLVUjdrov2ezBIdUyGz+hflMaSJaKU9r+BhrJ3Tzth1kBNVfTtxZskU078E4IFjSYIaBapQdRtDHF9t4aGxRc5NqcKRxKFvypK1vJKkO2Ap1ZaPOoj695VPHNMdM78Qk6Jj4s4jST2zx+5SjH68+VAdibNxi3B8ITOAB1gBTlW7KyH+YVpHwvZx4IaOvD7thVdUD2QgDK3UR/skz83D67CJDyyfNkJwSjP6CJMSUfT+rg0rKdAoKCAGnYQ4cim11UI3Wm7fVn/Xz6GvJq20Ruq7LNSSMwqcFSpWgKAe654bDZMNTAkOqQIDAQAB";



    private static final String SIGNATURE_ALGO = "SHA256withRSA";

    private TrialLicenseManager() {
    }
    public static boolean hasTrialLicense() {
        return Files.exists(getLicenseFilePath());
    }
    /**
     * Metodo da chiamare all'avvio: se la trial non è valida, lancia TrialExpiredException.
     */
    public static void checkTrialOrThrow() throws TrialExpiredException {
        try {
            String hwid = HardwareIdUtil.getHardwareId();


            TrialLicense lic = loadLicenseFile();

            // 1) Verifica firma RSA
            verifySignatureOrThrow(lic);


            // 2) Verifica HWID con tolleranza hardware
            if (!hwid.equalsIgnoreCase(lic.getHwid())) {
                throw new TrialExpiredException(I18n.t("lic.trial.invalidHardware"));

            }

            Instant now = Instant.now();

            // 3) Verifica periodo di validità
            if (now.isBefore(lic.getStart())) {
                throw new TrialExpiredException(I18n.t("lic.trial.notYetValid"));
            }
            if (now.isAfter(lic.getExpire())) {
                throw new TrialExpiredException(I18n.t("lic.trial.expired"));
            }

            // Nessun aggiornamento su file: i dati sono statici e firmati.
            // Anti-rollback reale richiederebbe una sorgente di tempo fidata (es. server).

        } catch (TrialExpiredException e) {
            throw e;
        } catch (Exception e) {
            throw new TrialExpiredException(I18n.t("lic.trial.error") + ": " + e.getMessage());
        }
    }

    public static TrialLicense loadLicenseFile() throws Exception {
        Path path = getLicenseFilePath();
        if (!Files.exists(path)) {
            throw new TrialExpiredException(I18n.t("lic.trial.missingFile"));
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return TrialLicense.parse(content);
    }

    private static void verifySignatureOrThrow(TrialLicense lic) throws Exception {
        PublicKey publicKey = loadPublicKey();
        Signature sig = Signature.getInstance(SIGNATURE_ALGO);
        sig.initVerify(publicKey);
        byte[] data = lic.dataToSign().getBytes(StandardCharsets.UTF_8);
        sig.update(data);

        byte[] signatureBytes = Base64.getDecoder().decode(lic.getSignature());

        boolean ok = sig.verify(signatureBytes);
        if (!ok) {
            throw new TrialExpiredException(I18n.t("lic.trial.badSignature"));
        }
    }


    private static PublicKey loadPublicKey() throws Exception {
        byte[] encoded = Base64.getDecoder().decode(PUBLIC_KEY_BASE64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    /**
     * Percorso del file di licenza trial.
     * Puoi riutilizzare lo stesso schema di prima.
     */
    private static Path getLicenseFilePath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path baseDir;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                baseDir = Paths.get(appData, "OneCritto");
            } else {
                baseDir = Paths.get(System.getProperty("user.home"), ".onecritto");
            }
        } else if (os.contains("mac")) {
            baseDir = Paths.get(System.getProperty("user.home"),
                    "Library", "Application Support", "OneCritto");
        } else {
            baseDir = Paths.get(System.getProperty("user.home"), ".config", "onecritto");
        }
        return baseDir.resolve("onecritto-license.lic");
    }
}

