package com.onecritto.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

@Getter
@Setter
public class SecretEntry {

    private String id;
    private String title;
    private String category;

    /** Epoch millis when the entry was first created */
    private long createdAt;

    /** Epoch millis when the password was last changed */
    private long passwordChangedAt;

    // ---- CAMPi REALI (non serializzati) ----
    @JsonIgnore
    private char[] username;

    @JsonIgnore
    private char[] password;

    @JsonIgnore
    private char[] notes;

    // ---- VERSIONI SERIALIZZATE (int[]) ----

    @JsonProperty("username")
    public int[] getUsernameEncoded() {
        return username == null ? null : encode(username);
    }

    @JsonProperty("username")
    public void setUsernameEncoded(int[] data) {
        this.username = data == null ? null : decode(data);
    }

    @JsonProperty("password")
    public int[] getPasswordEncoded() {
        return password == null ? null : encode(password);
    }

    @JsonProperty("password")
    public void setPasswordEncoded(int[] data) {
        this.password = data == null ? null : decode(data);
    }

    @JsonProperty("notes")
    public int[] getNotesEncoded() {
        return notes == null ? null : encode(notes);
    }

    @JsonProperty("notes")
    public void setNotesEncoded(int[] data) {
        this.notes = data == null ? null : decode(data);
    }


    // ---- UTILITIES ----

    private int[] encode(char[] src) {
        int[] out = new int[src.length];
        for (int i = 0; i < src.length; i++) out[i] = src[i];
        return out;
    }

    private char[] decode(int[] src) {
        char[] out = new char[src.length];
        for (int i = 0; i < src.length; i++) out[i] = (char) src[i];
        return out;
    }

    public void setUsername(char[] value) {
        if (value == null) {
            this.username = null;
            return;
        }

        // Copia difensiva (sicura)
        this.username = Arrays.copyOf(value, value.length);
    }

    public void setPassword(char[] value) {
        if (value == null) {
            this.password = null;
            return;
        }

        this.password = Arrays.copyOf(value, value.length);
    }

    public void setNotes(char[] value) {
        if (value == null) {
            this.notes = null;
            return;
        }

        this.notes = Arrays.copyOf(value, value.length);
    }

}