package com.onecritto.service;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class VaultContextTest {

    // =========================================================================
    //  secureClear — keys and password must be wiped
    // =========================================================================

    @Test
    void secureClear_wipesPasswordAndKeys() {
        VaultContext ctx = new VaultContext();

        char[] password = "MySecret!123".toCharArray();
        ctx.setMasterPassword(password);
        ctx.setKeyEnc(new SecretKeySpec(new byte[32], "AES"));
        ctx.setKeyMac(new SecretKeySpec(new byte[32], "HmacSHA256"));

        ctx.secureClear();

        assertNull(ctx.getMasterPassword(), "masterPassword must be null after secureClear");
        assertNull(ctx.getKeyEnc(), "keyEnc must be null after secureClear");
        assertNull(ctx.getKeyMac(), "keyMac must be null after secureClear");

        // Original char[] must be zeroed
        for (char c : password) {
            assertEquals('\0', c, "Password char array must be zeroed");
        }
    }

    @Test
    void secureClear_preservesSaltAndMetadata() {
        VaultContext ctx = new VaultContext();

        byte[] salt = new byte[]{1, 2, 3, 4};
        byte[] encMeta = new byte[]{5, 6, 7, 8};
        byte[] iv = new byte[]{9, 10, 11, 12};

        ctx.setSalt(salt);
        ctx.setEncryptedMetadata(encMeta);
        ctx.setMetadataIv(iv);

        ctx.secureClear();

        assertNotNull(ctx.getSalt(), "salt must survive secureClear (needed for unlock)");
        assertNotNull(ctx.getEncryptedMetadata(), "encryptedMetadata must survive secureClear");
        assertNotNull(ctx.getMetadataIv(), "metadataIv must survive secureClear");
    }

    // =========================================================================
    //  fullClear — everything must be wiped
    // =========================================================================

    @Test
    void fullClear_wipesEverything() {
        VaultContext ctx = new VaultContext();

        ctx.setMasterPassword("MySecret!123".toCharArray());
        ctx.setKeyEnc(new SecretKeySpec(new byte[32], "AES"));
        ctx.setSalt(new byte[]{1, 2, 3});
        ctx.setEncryptedMetadata(new byte[]{4, 5, 6});
        ctx.setMetadataIv(new byte[]{7, 8, 9});
        ctx.setVaultPath(Paths.get("/tmp/test.onecritto"));

        ctx.fullClear();

        assertNull(ctx.getMasterPassword());
        assertNull(ctx.getKeyEnc());
        assertNull(ctx.getSalt());
        assertNull(ctx.getEncryptedMetadata());
        assertNull(ctx.getMetadataIv());
        assertNull(ctx.getVault());
        assertNull(ctx.getVaultPath());
    }

    @Test
    void fullClear_zerosByteArrays() {
        VaultContext ctx = new VaultContext();

        byte[] salt = new byte[]{1, 2, 3, 4};
        byte[] encMeta = new byte[]{5, 6, 7, 8};
        byte[] iv = new byte[]{9, 10, 11, 12};

        ctx.setSalt(salt);
        ctx.setEncryptedMetadata(encMeta);
        ctx.setMetadataIv(iv);

        ctx.fullClear();

        // The original byte arrays must be zeroed
        for (byte b : salt) assertEquals(0, b, "salt bytes must be zeroed");
        for (byte b : encMeta) assertEquals(0, b, "encryptedMetadata bytes must be zeroed");
        for (byte b : iv) assertEquals(0, b, "metadataIv bytes must be zeroed");
    }

    // =========================================================================
    //  toString — must NOT leak sensitive fields
    // =========================================================================

    @Test
    void toString_doesNotContainPassword() {
        VaultContext ctx = new VaultContext();
        ctx.setMasterPassword("SuperSecret!".toCharArray());
        ctx.setSalt(new byte[]{0x41, 0x42}); // "AB"

        String str = ctx.toString();

        assertFalse(str.contains("SuperSecret"), "toString must not contain password");
        assertFalse(str.contains("masterPassword"), "toString must not reference masterPassword field");
        assertFalse(str.contains("salt"), "toString must not reference salt field");
        assertFalse(str.contains("keyEnc"), "toString must not reference keyEnc field");
    }
}
