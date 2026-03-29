package com.onecritto.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
@Data
@NoArgsConstructor
public class Vault {

    private List<SecretEntry> entries = new ArrayList<>();
    private List<SecretFile> files = new ArrayList<>();
    private byte[] mac;
    private byte[] salt;

    //  Costruttore per OneCritto V3 (ricostruzione del vault)
    public Vault(List<SecretEntry> entries, List<SecretFile> files) {
        this.entries = entries != null ? entries : new ArrayList<>();
        this.files   = files   != null ? files   : new ArrayList<>();
    }

    public void add(SecretEntry entry) {
        entries.add(entry);
    }

    public void remove(SecretEntry entry) {
        entries.remove(entry);
    }
}

