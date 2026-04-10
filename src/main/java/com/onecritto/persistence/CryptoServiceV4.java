package com.onecritto.persistence;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

public class CryptoServiceV4 {

    public static boolean verifyHmac(Path file, SecretKey hmacKey, long hmacPos) throws Exception {

        Mac mac = CryptoServiceV4.initMac(hmacKey);

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {

            long toRead = hmacPos; // fino all'HMAC (escluso)
            byte[] buffer = new byte[8192];

            raf.seek(0);

            long remaining = toRead;

            while (remaining > 0) {
                int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) break;

                mac.update(buffer, 0, read);
                remaining -= read;
            }

            // Ora leggi l'HMAC memorizzato
            raf.seek(hmacPos);
            byte[] storedHmac = new byte[mac.getMacLength()];
            raf.readFully(storedHmac);

            byte[] computed = mac.doFinal();

            // confronto costante-time
            return MessageDigest.isEqual(storedHmac, computed);
        }
    }
    // Parametri Argon2 – da regolare se vuoi più / meno costoso
    private static final int ARGON2_MEMORY_KB   = 64 * 1024; // 64 MB
    private static final int ARGON2_ITERATIONS  = 3;
    private static final int ARGON2_PARALLELISM = 1;

    private static byte[] argon2(char[] password,
                                 byte[] salt,
                                 int outLen) {

        Argon2Parameters.Builder builder =
                new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withSalt(salt)
                        .withMemoryAsKB(ARGON2_MEMORY_KB)
                        .withIterations(ARGON2_ITERATIONS)
                        .withParallelism(ARGON2_PARALLELISM);

        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        gen.init(builder.build());

        byte[] out = new byte[outLen];
        gen.generateBytes(password, out);
        return out;
    }





    /**
     * Deriva in una sola chiamata Argon2 (64 byte) sia la master key AES-256
     * sia la chiave HMAC-SHA256, garantendo separazione crittografica.
     *
     * @return SecretKey[0] = masterKey (AES), SecretKey[1] = hmacKey (HMAC-SHA256)
     */
    public static SecretKey[] deriveKeys(char[] password, byte[] salt)
            throws GeneralSecurityException {

        byte[] keyBytes = argon2(password, salt, 64);

        byte[] encBytes = Arrays.copyOfRange(keyBytes, 0, 32);
        byte[] macBytes = Arrays.copyOfRange(keyBytes, 32, 64);
        Arrays.fill(keyBytes, (byte) 0);

        SecretKey masterKey = new SecretKeySpec(encBytes, "AES");
        SecretKey hmacKey   = new SecretKeySpec(macBytes, "HmacSHA256");

        Arrays.fill(encBytes, (byte) 0);
        Arrays.fill(macBytes, (byte) 0);

        return new SecretKey[] { masterKey, hmacKey };
    }

    public static Mac initMac(SecretKey key) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        return mac;
    }
}
