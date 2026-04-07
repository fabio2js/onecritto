package com.onecritto.importer;

import com.onecritto.model.SecretEntry;

import java.util.*;

/**
 * Trasforma i dati raw + schema di mapping in una lista di {@link SecretEntry} pronte per il vault.
 */
public class EntryTransformer {

    public List<SecretEntry> transform(ParseResult data, MappingSchema schema) {
        List<SecretEntry> entries = new ArrayList<>();

        for (Map<String, String> row : data.rows()) {
            String title = getField(row, schema, TargetField.TITLE);
            String username = getField(row, schema, TargetField.USERNAME);
            String password = getField(row, schema, TargetField.PASSWORD);
            String url = getField(row, schema, TargetField.URL);
            String notes = getField(row, schema, TargetField.NOTE);
            String category = getField(row, schema, TargetField.CATEGORY);

            // Almeno un campo significativo deve essere presente
            if (isBlank(title) && isBlank(username) && isBlank(password)) {
                continue;
            }

            // Se manca il titolo, usa l'URL o l'username come fallback
            if (isBlank(title)) {
                title = !isBlank(url) ? extractDomain(url) : username;
            }

            SecretEntry entry = new SecretEntry();
            entry.setId(UUID.randomUUID().toString());
            entry.setTitle(title);
            entry.setUrl(url != null ? url : "");
            entry.setUsername(toCharArray(username));
            entry.setPassword(toCharArray(password));
            entry.setNotes(toCharArray(notes));
            entry.setCategory(category != null && !category.isBlank() ? category : "Password");

            long now = System.currentTimeMillis();
            entry.setCreatedAt(now);
            if (password != null && !password.isBlank()) {
                entry.setPasswordChangedAt(now);
            }

            entries.add(entry);
        }

        return entries;
    }

    private String getField(Map<String, String> row, MappingSchema schema, TargetField target) {
        String column = schema.fieldFor(target);
        if (column == null) return null;
        String val = row.get(column);
        return val != null ? val.trim() : null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private char[] toCharArray(String s) {
        if (s == null || s.isEmpty()) return new char[0];
        return s.toCharArray();
    }

    private String extractDomain(String url) {
        try {
            String s = url.replaceFirst("(?i)^https?://", "");
            int slash = s.indexOf('/');
            if (slash > 0) s = s.substring(0, slash);
            return s;
        } catch (Exception e) {
            return url;
        }
    }
}
