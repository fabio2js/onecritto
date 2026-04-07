package com.onecritto.importer.parser;

import com.onecritto.importer.FileParser;
import com.onecritto.importer.ParseResult;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CsvParser implements FileParser {

    @Override
    public boolean canParse(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (!name.endsWith(".csv") && !name.endsWith(".txt")) {
            return false;
        }
        try {
            String head = readHead(file, 2048);
            // CSV tipicamente ha una riga header con separatori
            return head.contains(",") || head.contains(";") || head.contains("\t");
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public ParseResult parse(Path file) throws Exception {
        char separator = detectSeparator(file);
        String detectedSource = null;

        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             CSVReader reader = new CSVReaderBuilder(br)
                     .withCSVParser(new CSVParserBuilder().withSeparator(separator).build())
                     .build()) {

            String[] headerRow = reader.readNext();
            if (headerRow == null || headerRow.length == 0) {
                return new ParseResult(List.of(), List.of(), null);
            }

            // Rimuovi BOM se presente
            headerRow[0] = stripBom(headerRow[0]);

            List<String> headers = Arrays.asList(headerRow);
            detectedSource = detectSourceFromHeaders(headers);

            List<Map<String, String>> rows = new ArrayList<>();
            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headerRow.length && i < line.length; i++) {
                    row.put(headerRow[i], line[i] != null ? line[i].trim() : "");
                }
                rows.add(row);
            }

            return new ParseResult(headers, rows, detectedSource);
        }
    }

    private char detectSeparator(Path file) throws IOException {
        String head = readHead(file, 4096);
        int commas = count(head, ',');
        int semicolons = count(head, ';');
        int tabs = count(head, '\t');

        if (tabs > commas && tabs > semicolons) return '\t';
        if (semicolons > commas) return ';';
        return ',';
    }

    private String readHead(Path file, int maxBytes) throws IOException {
        byte[] buf = new byte[maxBytes];
        int read;
        try (var is = Files.newInputStream(file)) {
            read = is.read(buf);
        }
        return read > 0 ? new String(buf, 0, read, StandardCharsets.UTF_8) : "";
    }

    private int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) n++;
        }
        return n;
    }

    private String stripBom(String s) {
        if (s != null && s.startsWith("\uFEFF")) {
            return s.substring(1);
        }
        return s;
    }

    private String detectSourceFromHeaders(List<String> headers) {
        Set<String> lowerHeaders = new HashSet<>();
        for (String h : headers) {
            lowerHeaders.add(h.toLowerCase().trim());
        }

        // Bitwarden (first — very specific headers)
        if (lowerHeaders.containsAll(Set.of("login_uri", "login_username", "login_password"))) {
            return "bitwarden";
        }
        // LastPass (before Chrome — has extra/grouping differentiator)
        if (lowerHeaders.containsAll(Set.of("url", "username", "password", "extra", "name", "grouping"))) {
            return "lastpass";
        }
        // KeePass CSV
        if (lowerHeaders.containsAll(Set.of("group", "title", "username", "password", "url"))) {
            return "keepass";
        }
        // Chrome / Edge (simpler header set — check after more specific formats)
        if (lowerHeaders.containsAll(Set.of("name", "url", "username", "password"))) {
            return "chrome";
        }
        // 1Password
        if (lowerHeaders.containsAll(Set.of("title", "username", "password"))
                && (lowerHeaders.contains("url") || lowerHeaders.contains("urls"))) {
            return "1password";
        }
        // Firefox
        if (lowerHeaders.containsAll(Set.of("url", "username", "password", "httprealm"))) {
            return "firefox";
        }
        // Dashlane
        if (lowerHeaders.containsAll(Set.of("title", "url", "login", "password"))) {
            return "dashlane";
        }
        // NordPass
        if (lowerHeaders.contains("cardholdername") || lowerHeaders.contains("cardnumber")) {
            return "nordpass";
        }
        // Proton Pass
        if (lowerHeaders.containsAll(Set.of("type", "name", "url", "password"))
                && lowerHeaders.contains("email")) {
            return "protonpass";
        }
        // Safari
        if (lowerHeaders.containsAll(Set.of("title", "url", "username", "password"))
                && lowerHeaders.contains("otpauth")) {
            return "safari";
        }

        return null;
    }
}
