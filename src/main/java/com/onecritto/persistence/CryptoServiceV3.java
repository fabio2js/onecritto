package com.onecritto.persistence;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public class CryptoServiceV3 {
    public static SecretKey deriveHmacKey(char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {

        PBEKeySpec spec = new PBEKeySpec(
                password,
                salt,
                iterations,
                256
        );

        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = f.generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "HmacSHA256");
    }
    private static final SecureRandom RNG = new SecureRandom();

    // ----------------------------------------------------------------------
    //  KEY DERIVATION
    // ----------------------------------------------------------------------

    /**
     * Deriva una chiave AES-256 dalla password utente.
     */
    public static SecretKey deriveMasterKey(char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {

        PBEKeySpec spec = new PBEKeySpec(
                password,
                salt,
                iterations,
                OneCrittoV3Format.AES_KEY_LENGTH * 8 // 256 bit
        );

        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = f.generateSecret(spec).getEncoded();

        return new SecretKeySpec(key, "AES");
    }

    // ----------------------------------------------------------------------
    //  METADATA ENCRYPTION
    // ----------------------------------------------------------------------

    /**
     * Cifra i metadati del vault.
     */
    public static byte[] encryptMetadata(byte[] metadataJson, SecretKey key, byte[] iv)
            throws GeneralSecurityException {

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        GCMParameterSpec gcm = new GCMParameterSpec(
                OneCrittoV3Format.GCM_TAG_LENGTH * 8,
                iv
        );

        cipher.init(Cipher.ENCRYPT_MODE, key, gcm);
        return cipher.doFinal(metadataJson);
    }

    /**
     * Decifra i metadati.
     */
    public static byte[] decryptMetadata(byte[] cipherBytes, SecretKey key, byte[] iv)
            throws GeneralSecurityException {

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        GCMParameterSpec gcm = new GCMParameterSpec(
                OneCrittoV3Format.GCM_TAG_LENGTH * 8,
                iv
        );

        cipher.init(Cipher.DECRYPT_MODE, key, gcm);
        return cipher.doFinal(cipherBytes);
    }

    public static Mac initMac(SecretKey key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        return mac;
    }
    // in CryptoServiceV3

    // Limite pratico del provider SunJCE per AES/GCM (non si applica più con Bouncy Castle)
    public static final long GCM_MAX_PLAINTEXT = Long.MAX_VALUE;

    public static long encryptStream(InputStream in,
                                     OutputStream out,
                                     SecretKey key,
                                     byte[] iv)
            throws Exception {

        // Bouncy Castle GCM streaming: nessun limite di 2GB
        GCMModeCipher gcmCipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
        AEADParameters params = new AEADParameters(
                new KeyParameter(key.getEncoded()),
                OneCrittoV3Format.GCM_TAG_LENGTH * 8,
                iv
        );
        gcmCipher.init(true, params);

        byte[] buf = new byte[8192];
        long writtenPlain = 0;

        int len;
        while ((len = in.read(buf)) != -1) {
            writtenPlain += len;

            byte[] outBuf = new byte[gcmCipher.getUpdateOutputSize(len)];
            int produced = gcmCipher.processBytes(buf, 0, len, outBuf, 0);
            if (produced > 0) {
                out.write(outBuf, 0, produced);
            }
        }

        // Scrive anche il tag finale GCM
        byte[] finalBuf = new byte[gcmCipher.getOutputSize(0)];
        int finalLen = gcmCipher.doFinal(finalBuf, 0);
        if (finalLen > 0) {
            out.write(finalBuf, 0, finalLen);
        }

        // non chiudiamo out, lo fa il chiamante
        return writtenPlain;
    }






    // ----------------------------------------------------------------------
    //  STREAM ENCRYPTION (PER FILE GRANDI)
    // ----------------------------------------------------------------------





    // ----------------------------------------------------------------------
    //  HMAC finale
    // ----------------------------------------------------------------------

    public static byte[] computeHmac(SecretKey key, byte[] data) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(key);
        return hmac.doFinal(data);
    }


    // ----------------------------------------------------------------------
    //  UTILITY
    // ----------------------------------------------------------------------

    public static byte[] randomBytes(int len) {
        byte[] b = new byte[len];
        RNG.nextBytes(b);
        return b;
    }

}
