package com.onecritto.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a saved SSH connection configuration.
 * The keyFileId references a SecretFile stored in the vault.
 */
@Data
@NoArgsConstructor
public class SshConnection {
    private String id;
    private String name;
    private String host;
    private int port = 22;
    private String username;
    private String keyFileId;     // ID of the SecretFile in the vault (SSH private key)
    private String keyFileName;   // display name of the key file
    private long createdAt;

    public SshConnection(String id, String name, String host, int port,
                         String username, String keyFileId, String keyFileName) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.keyFileId = keyFileId;
        this.keyFileName = keyFileName;
        this.createdAt = System.currentTimeMillis();
    }
}
