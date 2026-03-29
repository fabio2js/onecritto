package com.onecritto.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OneCrittoFormatTest {

    // =========================================================================
    //  V4 inherits V3 constants correctly
    // =========================================================================

    @Test
    void v4_magicMatchesV3() {
        assertArrayEquals(OneCrittoV3Format.MAGIC, OneCrittoV4Format.MAGIC);
    }

    @Test
    void v4_versionIs4() {
        assertEquals(4, OneCrittoV4Format.VERSION);
    }

    @Test
    void v3_versionIs3() {
        assertEquals(3, OneCrittoV3Format.VERSION);
    }

    // =========================================================================
    //  CRYPTO PARAMETER SANITY
    // =========================================================================

    @Test
    void saltLength_is32Bytes() {
        assertEquals(32, OneCrittoV3Format.SALT_LENGTH, "Salt must be 256 bit");
    }

    @Test
    void aesKeyLength_is32Bytes() {
        assertEquals(32, OneCrittoV3Format.AES_KEY_LENGTH, "AES key must be 256 bit");
    }

    @Test
    void gcmTagLength_is16Bytes() {
        assertEquals(16, OneCrittoV3Format.GCM_TAG_LENGTH, "GCM tag must be 128 bit");
    }

    @Test
    void hmacLength_is32Bytes() {
        assertEquals(32, OneCrittoV3Format.HMAC_LENGTH, "HMAC-SHA256 is 256 bit");
    }

    @Test
    void metadataIvLength_is12Bytes() {
        assertEquals(12, OneCrittoV3Format.METADATA_IV_LENGTH, "GCM IV must be 96 bit");
    }

    @Test
    void fileIvLength_is12Bytes() {
        assertEquals(12, OneCrittoV3Format.FILE_IV_LENGTH, "GCM IV must be 96 bit");
    }

    @Test
    void pbkdf2Iterations_atLeast100k() {
        assertTrue(OneCrittoV3Format.PBKDF2_ITERATIONS >= 100_000,
                "PBKDF2 iterations must be at least 100k");
    }
}
