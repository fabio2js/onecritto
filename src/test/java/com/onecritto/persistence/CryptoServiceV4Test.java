package com.onecritto.persistence;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceV4Test {

    private static final char[] TEST_PASSWORD = "MasterPassword!123".toCharArray();
    private static final byte[] TEST_SALT = newRandomSalt();

    private static byte[] newRandomSalt() {
        byte[] salt = new byte[OneCrittoV4Format.SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    // =========================================================================
    //  KEY SEPARATION — deriveKeys() must produce two DISTINCT keys
    // =========================================================================

    @Test
    void deriveKeys_producesTwoDifferentKeys() throws Exception {
        SecretKey[] keys = CryptoServiceV4.deriveKeys(TEST_PASSWORD, TEST_SALT);

        assertNotNull(keys[0], "masterKey must not be null");
        assertNotNull(keys[1], "hmacKey must not be null");

        assertFalse(
                java.util.Arrays.equals(keys[0].getEncoded(), keys[1].getEncoded()),
                "AES master key and HMAC key must NOT be identical"
        );
    }

    @Test
    void deriveKeys_keysHaveCorrectAlgorithms() throws Exception {
        SecretKey[] keys = CryptoServiceV4.deriveKeys(TEST_PASSWORD, TEST_SALT);

        assertEquals("AES", keys[0].getAlgorithm());
        assertEquals("HmacSHA256", keys[1].getAlgorithm());
    }

    @Test
    void deriveKeys_keysHaveCorrectLength() throws Exception {
        SecretKey[] keys = CryptoServiceV4.deriveKeys(TEST_PASSWORD, TEST_SALT);

        assertEquals(32, keys[0].getEncoded().length, "AES key must be 256 bit");
        assertEquals(32, keys[1].getEncoded().length, "HMAC key must be 256 bit");
    }

    // =========================================================================
    //  DETERMINISM — same input must always produce same output
    // =========================================================================

    @Test
    void deriveKeys_isDeterministic() throws Exception {
        SecretKey[] keys1 = CryptoServiceV4.deriveKeys(TEST_PASSWORD, TEST_SALT);
        SecretKey[] keys2 = CryptoServiceV4.deriveKeys(TEST_PASSWORD, TEST_SALT);

        assertArrayEquals(keys1[0].getEncoded(), keys2[0].getEncoded(),
                "Same password+salt must produce same AES key");
        assertArrayEquals(keys1[1].getEncoded(), keys2[1].getEncoded(),
                "Same password+salt must produce same HMAC key");
    }

    // =========================================================================
    //  SALT SENSITIVITY — different salt must produce different keys
    // =========================================================================

    @Test
    void deriveKeys_differentSaltProducesDifferentKeys() throws Exception {
        byte[] otherSalt = newRandomSalt();

        SecretKey[] keys1 = CryptoServiceV4.deriveKeys(TEST_PASSWORD, TEST_SALT);
        SecretKey[] keys2 = CryptoServiceV4.deriveKeys(TEST_PASSWORD, otherSalt);

        assertFalse(
                java.util.Arrays.equals(keys1[0].getEncoded(), keys2[0].getEncoded()),
                "Different salt must produce different AES keys"
        );
    }

    // =========================================================================
    //  PASSWORD SENSITIVITY — different password must produce different keys
    // =========================================================================

    @Test
    void deriveKeys_differentPasswordProducesDifferentKeys() throws Exception {
        char[] otherPassword = "AnotherStrongP@ss99".toCharArray();

        SecretKey[] keys1 = CryptoServiceV4.deriveKeys(TEST_PASSWORD, TEST_SALT);
        SecretKey[] keys2 = CryptoServiceV4.deriveKeys(otherPassword, TEST_SALT);

        assertFalse(
                java.util.Arrays.equals(keys1[0].getEncoded(), keys2[0].getEncoded()),
                "Different password must produce different AES keys"
        );
    }
}
