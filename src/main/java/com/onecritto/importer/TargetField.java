package com.onecritto.importer;

import java.util.List;
import java.util.Map;

/**
 * Campo target a cui ogni colonna del file importato viene mappata.
 */
public enum TargetField {

    TITLE(List.of("title", "name", "site", "service", "entry", "label", "description",
            "voce", "titolo", "nome")),
    USERNAME(List.of("username", "user", "login", "email", "account", "e-mail", "mail",
            "utente", "login_username")),
    PASSWORD(List.of("password", "pass", "pwd", "secret", "parola", "chiave",
            "login_password")),
    URL(List.of("url", "uri", "link", "website", "sito", "web", "href", "indirizzo",
            "login_uri")),
    NOTE(List.of("note", "notes", "comment", "comments", "extra", "memo", "appunti")),
    CATEGORY(List.of("category", "group", "folder", "tag", "type", "gruppo", "cartella",
            "tipo", "grouping")),
    SKIP(List.of());

    private final List<String> synonyms;

    TargetField(List<String> synonyms) {
        this.synonyms = synonyms;
    }

    public List<String> getSynonyms() {
        return synonyms;
    }

    /**
     * Mappa precalcolata: sinonimo normalizzato → TargetField.
     */
    private static final Map<String, TargetField> SYNONYM_INDEX;

    static {
        var builder = new java.util.HashMap<String, TargetField>();
        for (TargetField field : values()) {
            for (String syn : field.synonyms) {
                builder.put(normalize(syn), field);
            }
        }
        SYNONYM_INDEX = Map.copyOf(builder);
    }

    public static Map<String, TargetField> getSynonymIndex() {
        return SYNONYM_INDEX;
    }

    public static String normalize(String input) {
        return input.toLowerCase().trim()
                .replaceAll("[_\\-\\s]+", "")
                .replaceAll("[^a-z0-9]", "");
    }
}
