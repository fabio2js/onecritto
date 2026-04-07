package com.onecritto.importer;

import com.onecritto.importer.parser.CsvParser;
import com.onecritto.model.SecretEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImportOrchestratorTest {

    @TempDir
    Path tempDir;

    // ═══════════════════════════════════════════════════════════════
    // CSV PARSING
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parseChromeCSV() throws Exception {
        Path csv = tempDir.resolve("chrome.csv");
        Files.writeString(csv, """
                name,url,username,password,note
                Gmail,https://gmail.com,user@gmail.com,MyP@ss123,personal email
                GitHub,https://github.com,dev@github.com,GhToken!99,work
                """, StandardCharsets.UTF_8);

        CsvParser parser = new CsvParser();
        assertTrue(parser.canParse(csv));

        ParseResult result = parser.parse(csv);
        assertEquals("chrome", result.detectedSource());
        assertEquals(2, result.rows().size());
        assertEquals("Gmail", result.rows().get(0).get("name"));
        assertEquals("user@gmail.com", result.rows().get(0).get("username"));
    }

    @Test
    void parseBitwardenCSV() throws Exception {
        Path csv = tempDir.resolve("bitwarden.csv");
        Files.writeString(csv, """
                folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
                Social,,login,Twitter,,,,https://twitter.com,tweeter,Tw!tter99,
                Work,,login,Slack,team chat,,,https://slack.com,worker,Sl@ck456,JBSWY3DPEHPK3PXP
                """, StandardCharsets.UTF_8);

        CsvParser parser = new CsvParser();
        ParseResult result = parser.parse(csv);
        assertEquals("bitwarden", result.detectedSource());
        assertEquals(2, result.rows().size());
        assertEquals("tweeter", result.rows().get(0).get("login_username"));
    }

    @Test
    void parseKeePassCSV() throws Exception {
        Path csv = tempDir.resolve("keepass.csv");
        Files.writeString(csv, """
                Group,Title,Username,Password,URL,Notes
                Internet,Amazon,buyer@mail.com,Am@z0nPwd,https://amazon.com,Prime account
                Banking,MyBank,john,B@nk$ecure,https://mybank.com,
                """, StandardCharsets.UTF_8);

        CsvParser parser = new CsvParser();
        ParseResult result = parser.parse(csv);
        assertEquals("keepass", result.detectedSource());
        assertEquals(2, result.rows().size());
    }

    @Test
    void parseLastPassCSV() throws Exception {
        Path csv = tempDir.resolve("lastpass.csv");
        Files.writeString(csv, """
                url,username,password,totp,extra,name,grouping,fav
                https://netflix.com,watcher,N3tfl!x,,binge watch,Netflix,Entertainment,0
                """, StandardCharsets.UTF_8);

        CsvParser parser = new CsvParser();
        ParseResult result = parser.parse(csv);
        assertEquals("lastpass", result.detectedSource());
        assertEquals(1, result.rows().size());
    }

    @Test
    void parseSemicolonSeparatedCSV() throws Exception {
        Path csv = tempDir.resolve("semicolon.csv");
        Files.writeString(csv, """
                title;username;password;url
                Test;user1;pass1;https://test.com
                Test2;user2;pass2;https://test2.com
                """, StandardCharsets.UTF_8);

        CsvParser parser = new CsvParser();
        assertTrue(parser.canParse(csv));
        ParseResult result = parser.parse(csv);
        assertEquals(2, result.rows().size());
        assertEquals("user1", result.rows().get(0).get("username"));
    }

    @Test
    void parseCsvWithBOM() throws Exception {
        Path csv = tempDir.resolve("bom.csv");
        // UTF-8 BOM + content
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = "name,url,username,password\nTest,https://test.com,user,pass\n"
                .getBytes(StandardCharsets.UTF_8);
        byte[] full = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, full, 0, bom.length);
        System.arraycopy(content, 0, full, bom.length, content.length);
        Files.write(csv, full);

        CsvParser parser = new CsvParser();
        ParseResult result = parser.parse(csv);
        // Il primo header non deve avere il BOM
        assertEquals("name", result.headers().get(0));
    }

    // ═══════════════════════════════════════════════════════════════
    // FIELD MAPPER
    // ═══════════════════════════════════════════════════════════════

    @Test
    void mapperUsesChromeProfile() {
        FieldMapper mapper = new FieldMapper();
        List<String> headers = List.of("name", "url", "username", "password", "note");
        MappingSchema schema = mapper.map(headers, "chrome", List.of());

        assertEquals(TargetField.TITLE, schema.getMappings().get("name").targetField());
        assertEquals(TargetField.URL, schema.getMappings().get("url").targetField());
        assertEquals(TargetField.USERNAME, schema.getMappings().get("username").targetField());
        assertEquals(TargetField.PASSWORD, schema.getMappings().get("password").targetField());
        assertEquals(Confidence.HIGH, schema.getMappings().get("name").confidence());
    }

    @Test
    void mapperFuzzyMatchesUnknownHeaders() {
        FieldMapper mapper = new FieldMapper();
        List<String> headers = List.of("site_name", "email_address", "secret_key", "web_url", "memo");
        List<Map<String, String>> samples = List.of(
                Map.of("site_name", "Gmail", "email_address", "user@gmail.com",
                        "secret_key", "xK#9mP!2qR", "web_url", "https://gmail.com",
                        "memo", "This is a long note about the account that has details")
        );
        MappingSchema schema = mapper.map(headers, null, samples);

        // "email_address" contiene "email" → USERNAME
        assertEquals(TargetField.USERNAME, schema.getMappings().get("email_address").targetField());
    }

    @Test
    void mapperUsesHeuristicOnValues() {
        FieldMapper mapper = new FieldMapper();
        List<String> headers = List.of("col1", "col2", "col3");
        List<Map<String, String>> samples = List.of(
                Map.of("col1", "https://example.com", "col2", "user@mail.com", "col3", "xK#9mP!2qR"),
                Map.of("col1", "https://test.com", "col2", "admin@test.com", "col3", "Zp$4nW@8bY"),
                Map.of("col1", "https://foo.com", "col2", "foo@bar.com", "col3", "aB3$dE6&gH")
        );
        MappingSchema schema = mapper.map(headers, null, samples);

        assertEquals(TargetField.URL, schema.getMappings().get("col1").targetField());
        assertEquals(TargetField.USERNAME, schema.getMappings().get("col2").targetField());
        assertEquals(TargetField.PASSWORD, schema.getMappings().get("col3").targetField());
    }

    @Test
    void mapperSkipsUnrecognizedHeaders() {
        FieldMapper mapper = new FieldMapper();
        List<String> headers = List.of("title", "password", "zzz_unknown_zzz");
        MappingSchema schema = mapper.map(headers, null, List.of());

        assertEquals(TargetField.TITLE, schema.getMappings().get("title").targetField());
        assertEquals(TargetField.PASSWORD, schema.getMappings().get("password").targetField());
        assertEquals(TargetField.SKIP, schema.getMappings().get("zzz_unknown_zzz").targetField());
    }

    // ═══════════════════════════════════════════════════════════════
    // ENTRY TRANSFORMER
    // ═══════════════════════════════════════════════════════════════

    @Test
    void transformCreatesSecretEntries() {
        MappingSchema schema = new MappingSchema();
        schema.put("name", TargetField.TITLE, Confidence.HIGH);
        schema.put("user", TargetField.USERNAME, Confidence.HIGH);
        schema.put("pass", TargetField.PASSWORD, Confidence.HIGH);
        schema.put("site", TargetField.URL, Confidence.HIGH);
        schema.put("info", TargetField.NOTE, Confidence.HIGH);

        ParseResult data = new ParseResult(
                List.of("name", "user", "pass", "site", "info"),
                List.of(
                        Map.of("name", "Gmail", "user", "user@gmail.com",
                                "pass", "secret123", "site", "https://gmail.com", "info", "personal"),
                        Map.of("name", "GitHub", "user", "dev",
                                "pass", "ghtoken", "site", "https://github.com", "info", "")
                ),
                "test"
        );

        EntryTransformer transformer = new EntryTransformer();
        List<SecretEntry> entries = transformer.transform(data, schema);

        assertEquals(2, entries.size());
        assertEquals("Gmail", entries.get(0).getTitle());
        assertArrayEquals("user@gmail.com".toCharArray(), entries.get(0).getUsername());
        assertArrayEquals("secret123".toCharArray(), entries.get(0).getPassword());
        assertEquals("https://gmail.com", entries.get(0).getUrl());
        assertNotNull(entries.get(0).getId());
        assertTrue(entries.get(0).getCreatedAt() > 0);
    }

    @Test
    void transformSkipsEmptyRows() {
        MappingSchema schema = new MappingSchema();
        schema.put("name", TargetField.TITLE, Confidence.HIGH);
        schema.put("pass", TargetField.PASSWORD, Confidence.HIGH);

        ParseResult data = new ParseResult(
                List.of("name", "pass"),
                List.of(
                        Map.of("name", "", "pass", ""),
                        Map.of("name", "Valid", "pass", "secret")
                ),
                null
        );

        EntryTransformer transformer = new EntryTransformer();
        List<SecretEntry> entries = transformer.transform(data, schema);
        assertEquals(1, entries.size());
        assertEquals("Valid", entries.get(0).getTitle());
    }

    @Test
    void transformUsesDomainAsFallbackTitle() {
        MappingSchema schema = new MappingSchema();
        schema.put("user", TargetField.USERNAME, Confidence.HIGH);
        schema.put("pass", TargetField.PASSWORD, Confidence.HIGH);
        schema.put("site", TargetField.URL, Confidence.HIGH);

        ParseResult data = new ParseResult(
                List.of("user", "pass", "site"),
                List.of(Map.of("user", "admin", "pass", "pwd", "site", "https://example.com/login")),
                null
        );

        EntryTransformer transformer = new EntryTransformer();
        List<SecretEntry> entries = transformer.transform(data, schema);
        assertEquals("example.com", entries.get(0).getTitle());
    }

    // ═══════════════════════════════════════════════════════════════
    // FULL ORCHESTRATOR
    // ═══════════════════════════════════════════════════════════════

    @Test
    void fullImportChromeCSV() throws Exception {
        Path csv = tempDir.resolve("chrome_full.csv");
        Files.writeString(csv, """
                name,url,username,password,note
                Gmail,https://gmail.com,user@gmail.com,MyP@ss123,personal
                GitHub,https://github.com,dev,GhToken!99,work
                Netflix,https://netflix.com,watcher,N3tfl!x,
                """, StandardCharsets.UTF_8);

        ImportOrchestrator orchestrator = new ImportOrchestrator();
        ImportOrchestrator.ImportPreviewData preview = orchestrator.prepareImport(csv);

        assertEquals("chrome", preview.schema().getDetectedSource());
        assertEquals(3, preview.parseResult().rows().size());

        List<SecretEntry> entries = orchestrator.executeImport(preview);
        assertEquals(3, entries.size());
        assertEquals("Gmail", entries.get(0).getTitle());
        assertArrayEquals("MyP@ss123".toCharArray(), entries.get(0).getPassword());
    }

    @Test
    void fullImportBitwardenCSV() throws Exception {
        Path csv = tempDir.resolve("bitwarden_full.csv");
        Files.writeString(csv, """
                folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
                Social,,login,Twitter,,,,https://twitter.com,tweeter,Tw!tter99,
                """, StandardCharsets.UTF_8);

        ImportOrchestrator orchestrator = new ImportOrchestrator();
        ImportOrchestrator.ImportPreviewData preview = orchestrator.prepareImport(csv);
        assertEquals("bitwarden", preview.schema().getDetectedSource());

        List<SecretEntry> entries = orchestrator.executeImport(preview);
        assertEquals(1, entries.size());
        assertEquals("Twitter", entries.get(0).getTitle());
        assertArrayEquals("tweeter".toCharArray(), entries.get(0).getUsername());
    }

    @Test
    void unsupportedFileThrowsException() {
        Path unsupported = tempDir.resolve("file.dat");
        try {
            Files.writeString(unsupported, "binary data here");
        } catch (Exception e) {
            fail(e);
        }

        ImportOrchestrator orchestrator = new ImportOrchestrator();
        assertThrows(ImportOrchestrator.ImportException.class,
                () -> orchestrator.prepareImport(unsupported));
    }

    @Test
    void emptyFileThrowsException() throws Exception {
        Path csv = tempDir.resolve("empty.csv");
        Files.writeString(csv, "name,url,username,password\n", StandardCharsets.UTF_8);

        ImportOrchestrator orchestrator = new ImportOrchestrator();
        assertThrows(ImportOrchestrator.ImportException.class,
                () -> orchestrator.prepareImport(csv));
    }

    // ═══════════════════════════════════════════════════════════════
    // JARO-WINKLER (sanity check)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void jaroWinklerIdenticalStrings() {
        assertEquals(1.0, FieldMapper.jaroWinkler("password", "password"), 0.001);
    }

    @Test
    void jaroWinklerSimilarStrings() {
        double score = FieldMapper.jaroWinkler("password", "passwort");
        assertTrue(score > 0.9, "Expected >0.9, got " + score);
    }

    @Test
    void jaroWinklerDifferentStrings() {
        double score = FieldMapper.jaroWinkler("password", "xxxxxxxx");
        assertTrue(score < 0.5, "Expected <0.5, got " + score);
    }
}
