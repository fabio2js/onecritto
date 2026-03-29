package com.onecritto.persistence;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceV3Test {

    private static final char[] TEST_PASSWORD = "TestPassword!456".toCharArray();
    private static final byte[] TEST_SALT = newRandomSalt();

    private static byte[] newRandomSalt() {
        byte[] salt = new byte[OneCrittoV3Format.SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    // =========================================================================
    //  AES-GCM METADATA ROUND-TRIP
    // =========================================================================

    @Test
    void encryptDecryptMetadata_roundTrip() throws Exception {
        SecretKey key = CryptoServiceV3.deriveMasterKey(
                TEST_PASSWORD, TEST_SALT, OneCrittoV3Format.PBKDF2_ITERATIONS);
        byte[] iv = CryptoServiceV3.randomBytes(OneCrittoV3Format.METADATA_IV_LENGTH);

        byte[] plaintext = "{\"entries\":[],\"files\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] ciphertext = CryptoServiceV3.encryptMetadata(plaintext, key, iv);
        byte[] decrypted = CryptoServiceV3.decryptMetadata(ciphertext, key, iv);

        assertArrayEquals(plaintext, decrypted, "Decrypted data must match original plaintext");
    }

    @Test
    void decryptMetadata_wrongKeyFails() throws Exception {
        SecretKey key = CryptoServiceV3.deriveMasterKey(
                TEST_PASSWORD, TEST_SALT, OneCrittoV3Format.PBKDF2_ITERATIONS);
        byte[] iv = CryptoServiceV3.randomBytes(OneCrittoV3Format.METADATA_IV_LENGTH);

        byte[] plaintext = "secret data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ciphertext = CryptoServiceV3.encryptMetadata(plaintext, key, iv);

        // Derive key with different password
        SecretKey wrongKey = CryptoServiceV3.deriveMasterKey(
                "WrongPassword!789".toCharArray(), TEST_SALT, OneCrittoV3Format.PBKDF2_ITERATIONS);

        assertThrows(Exception.class,
                () -> CryptoServiceV3.decryptMetadata(ciphertext, wrongKey, iv),
                "Decryption with wrong key must throw");
    }

    @Test
    void decryptMetadata_wrongIvFails() throws Exception {
        SecretKey key = CryptoServiceV3.deriveMasterKey(
                TEST_PASSWORD, TEST_SALT, OneCrittoV3Format.PBKDF2_ITERATIONS);
        byte[] iv = CryptoServiceV3.randomBytes(OneCrittoV3Format.METADATA_IV_LENGTH);

        byte[] plaintext = "secret data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ciphertext = CryptoServiceV3.encryptMetadata(plaintext, key, iv);

        byte[] wrongIv = CryptoServiceV3.randomBytes(OneCrittoV3Format.METADATA_IV_LENGTH);

        assertThrows(Exception.class,
                () -> CryptoServiceV3.decryptMetadata(ciphertext, key, wrongIv),
                "Decryption with wrong IV must throw");
    }

    @Test
    void encryptMetadata_ciphertextDiffersFromPlaintext() throws Exception {
        SecretKey key = CryptoServiceV3.deriveMasterKey(
                TEST_PASSWORD, TEST_SALT, OneCrittoV3Format.PBKDF2_ITERATIONS);
        byte[] iv = CryptoServiceV3.randomBytes(OneCrittoV3Format.METADATA_IV_LENGTH);

        byte[] plaintext = "this is secret data for test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ciphertext = CryptoServiceV3.encryptMetadata(plaintext, key, iv);

        assertFalse(java.util.Arrays.equals(plaintext, ciphertext),
                "Ciphertext must not equal plaintext");
        assertTrue(ciphertext.length > plaintext.length,
                "Ciphertext must be longer (GCM tag appended)");
    }

    // =========================================================================
    //  GCM TAG INTEGRITY — flipping a bit must cause decryption failure
    // =========================================================================

    @Test
    void decryptMetadata_tampered_ciphertextFails() throws Exception {
        SecretKey key = CryptoServiceV3.deriveMasterKey(
                TEST_PASSWORD, TEST_SALT, OneCrittoV3Format.PBKDF2_ITERATIONS);
        byte[] iv = CryptoServiceV3.randomBytes(OneCrittoV3Format.METADATA_IV_LENGTH);

        byte[] plaintext = "integrity test data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ciphertext = CryptoServiceV3.encryptMetadata(plaintext, key, iv);

        // Flip one bit in the ciphertext
        byte[] tampered = ciphertext.clone();
        tampered[0] ^= 0x01;

        assertThrows(Exception.class,
                () -> CryptoServiceV3.decryptMetadata(tampered, key, iv),
                "Tampered ciphertext must be rejected by GCM");
    }

    // =========================================================================
    //  RANDOM BYTES
    // =========================================================================

    @Test
    void randomBytes_correctLengthAndNotAllZeros() {
        byte[] random = CryptoServiceV3.randomBytes(32);

        assertEquals(32, random.length);
        assertFalse(isAllZeros(random), "Random bytes must not be all zeros");
    }

    @Test
    void randomBytes_twoCallsProduceDifferentValues() {
        byte[] a = CryptoServiceV3.randomBytes(32);
        byte[] b = CryptoServiceV3.randomBytes(32);

        assertFalse(java.util.Arrays.equals(a, b),
                "Two random byte generations should differ");
    }

    private static boolean isAllZeros(byte[] data) {
        for (byte b : data) {
            if (b != 0) return false;
        }
        return true;
    }
}
