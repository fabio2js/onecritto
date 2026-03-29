package com.onecritto.service;

import com.onecritto.model.Vault;
import lombok.Data;
import lombok.ToString;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.util.Arrays;

@Data
@ToString(exclude = {"masterPassword", "salt", "keyEnc", "keyMac", "encryptedMetadata", "metadataIv"})
public class VaultContext {

    private Path vaultPath;

    private byte[] salt;
    private int iterations;

    private SecretKey keyEnc;
    private SecretKey keyMac;

    // === V3: METADATI CIFRATI ===
    private byte[] encryptedMetadata;  // blob AES-GCM
    private byte[] metadataIv;         // IV usato per cifrare i metadati

    // === Vault decifrato ===
    private Vault vault;

    private char[] masterPassword;


    public void secureClear() {

        // 1) Cancella master password (in RAM)
        if (masterPassword != null) {
            Arrays.fill(masterPassword, '\0');
            masterPassword = null;
        }

        // 2) Cancella chiave di cifratura principale
        if (keyEnc != null) {
            try { keyEnc.destroy(); } catch (Exception ignored) {}
            keyEnc = null;
        }

        // 3) Cancella MAC key
        if (keyMac != null) {
            try { keyMac.destroy(); } catch (Exception ignored) {}
            keyMac = null;
        }

        // 4) salt, encryptedMetadata e metadataIv NON vengono azzerati qui:
        //    sono necessari per l'unlock da lock-screen e non sono segreti
        //    (sono già presenti in chiaro nel file vault su disco).
        //    Vengono azzerati in fullClear() alla chiusura del vault.
    }

    /**
     * Cancellazione completa di tutto il materiale crittografico.
     * Da invocare solo alla chiusura definitiva del vault (non al lock).
     */
    public void fullClear() {
        secureClear();

        if (salt != null) {
            Arrays.fill(salt, (byte) 0);
            salt = null;
        }
        if (encryptedMetadata != null) {
            Arrays.fill(encryptedMetadata, (byte) 0);
            encryptedMetadata = null;
        }
        if (metadataIv != null) {
            Arrays.fill(metadataIv, (byte) 0);
            metadataIv = null;
        }

        vault = null;
        vaultPath = null;
    }




}



