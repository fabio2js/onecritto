package com.onecritto.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class TempVaultFilesTest {

    @TempDir
    Path tempDir;

    // =========================================================================
    //  SECURE DELETE — overwrite + delete
    // =========================================================================

    @Test
    void secureDelete_fileIsRemovedAfterDeletion() throws Exception {
        Path file = tempDir.resolve("secret.dat");
        byte[] data = new byte[4096];
        new SecureRandom().nextBytes(data);
        Files.write(file, data);

        assertTrue(Files.exists(file));

        TempVaultFiles.secureDelete(file);

        assertFalse(Files.exists(file), "File must be deleted after secureDelete");
    }

    @Test
    void secureDelete_nullPathDoesNotThrow() {
        assertDoesNotThrow(() -> TempVaultFiles.secureDelete(null));
    }

    @Test
    void secureDelete_nonExistentPathDoesNotThrow() {
        Path nonExistent = tempDir.resolve("does_not_exist.dat");
        assertDoesNotThrow(() -> TempVaultFiles.secureDelete(nonExistent));
    }

    @Test
    void secureDelete_emptyFileIsDeleted() throws Exception {
        Path file = tempDir.resolve("empty.dat");
        Files.createFile(file);
        assertEquals(0, Files.size(file));

        TempVaultFiles.secureDelete(file);

        assertFalse(Files.exists(file));
    }
}
