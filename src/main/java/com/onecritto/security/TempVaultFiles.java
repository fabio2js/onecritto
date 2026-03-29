package com.onecritto.security;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.EnumSet;

public final class TempVaultFiles {

    // Nuovo brand → OneCritto
    private static final String BASE_DIR_NAME = ".onecritto";
    private static final String TEMP_DIR_NAME = "temp";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TempVaultFiles() {
    }

    /**
     * Ritorna la cartella temporanea dedicata.
     * Percorso finale: ~/.onecritto/temp
     */
    public static Path getTempDir() throws IOException {
        Path home = Paths.get(System.getProperty("user.home"));
        Path baseDir = home.resolve(BASE_DIR_NAME);
        Path tempDir = baseDir.resolve(TEMP_DIR_NAME);

        Files.createDirectories(tempDir);
        return tempDir;
    }


    public static Path createTempFileForOpen(String originalFilename) throws IOException {
        Path tempDir = getTempDir();

        String safeName = (originalFilename == null)
                ? "file"
                : originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        return Files.createTempFile(tempDir, "open_", "_" + safeName);
    }


    /**
     * Sovrascrive e cancella un file in modo sicuro.
     */
    public static void secureDelete(Path file) throws IOException {
        if (file == null || !Files.exists(file)) {
            return;
        }

        long size = Files.size(file);

        if (size > 0) {
            // Usa FileChannel, che ha force(true)
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                    file,
                    java.nio.file.StandardOpenOption.WRITE
            )) {

                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(1024 * 1024); // 1MB
                long written = 0;

                while (written < size) {
                    buffer.clear();
                    int chunkSize = (int) Math.min(buffer.capacity(), size - written);

                    byte[] randomBytes = new byte[chunkSize];
                    SECURE_RANDOM.nextBytes(randomBytes);

                    buffer.put(randomBytes);
                    buffer.flip();

                    channel.write(buffer);
                    written += chunkSize;
                }

                channel.force(true); // ora è risolto, perché channel è FileChannel
            } catch (IOException e) {
                // Anche se fallisce la sovrascrittura, proviamo comunque a cancellare
            }
        }

        Files.deleteIfExists(file);
    }

    /**
     * Cancella in modo sicuro tutti i file temporanei.
     * Da richiamare all'avvio e in shutdown.
     */
    public static void cleanupTempDirSecure() {
        Path tempDir;
        try {
            tempDir = getTempDir();
        } catch (IOException e) {
            return;
        }

        if (!Files.isDirectory(tempDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    try {
                        secureDelete(p);
                    } catch (IOException ignored) {}
                }
            }
        } catch (IOException ignored) {}
    }
}
