package com.onecritto.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
@Data
@NoArgsConstructor
public class SecretFile {
    private String id;
    private String name;
    private String contentType;
    private long size;

    private long addTime;        // DATA DI INSERIMENTO (timestamp millis)
    private long lastEdit;
    private Path vaultPath;
    private long blobOffset;
    private byte[] iv;

    public SecretFile(String id, String name, String contentType,
                      long size, Path vaultPath, long blobOffset, byte[] iv) {
        this.id = id;
        this.name = name;
        this.contentType = contentType;
        this.size = size;
        this.vaultPath = vaultPath;
        this.blobOffset = blobOffset;
        this.iv = iv;
    }
}
