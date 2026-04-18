package com.onecritto.importer;

import java.util.*;

/**
 * Mappa le colonne del file importato ai campi {@link TargetField} di OneCritto.
 * <p>
 * Strategia a 3 livelli:
 * <ol>
 *   <li>Profili noti (header set esatti per Chrome, Bitwarden, KeePass, ecc.)</li>
 *   <li>Matching per sinonimi (exact + contains)</li>
 *   <li>Heuristic sui valori (pattern @, http, entropia)</li>
 * </ol>
 */
public class FieldMapper {

    // ── Profili hardcoded per source noti ──────────────────────────────────

    private static final Map<String, Map<String, TargetField>> SOURCE_PROFILES = Map.ofEntries(
            Map.entry("chrome", Map.of(
                    "name", TargetField.TITLE,
                    "url", TargetField.URL,
                    "username", TargetField.USERNAME,
                    "password", TargetField.PASSWORD,
                    "note", TargetField.NOTE
            )),

            Map.entry("bitwarden", Map.of(
                    "name", TargetField.TITLE,
                    "login_uri", TargetField.URL,
                    "login_username", TargetField.USERNAME,
                    "login_password", TargetField.PASSWORD,
                    "notes", TargetField.NOTE,
                    "folder", TargetField.CATEGORY
            )),
            Map.entry("keepass", mapOf(
                    "Title", TargetField.TITLE,
                    "Username", TargetField.USERNAME,
                    "UserName", TargetField.USERNAME,
                    "Password", TargetField.PASSWORD,
                    "URL", TargetField.URL,
                    "Notes", TargetField.NOTE,
                    "Group", TargetField.CATEGORY
            )),

            Map.entry("lastpass", Map.of(
                    "name", TargetField.TITLE,
                    "url", TargetField.URL,
                    "username", TargetField.USERNAME,
                    "password", TargetField.PASSWORD,
                    "extra", TargetField.NOTE,
                    "grouping", TargetField.CATEGORY
            )),
            Map.entry("1password", Map.of(
                    "Title", TargetField.TITLE,
                    "Url", TargetField.URL,
                    "Username", TargetField.USERNAME,
                    "Password", TargetField.PASSWORD,
                    "Notes", TargetField.NOTE
            )),
            Map.entry("firefox", Map.of(
                    "url", TargetField.URL,
                    "username", TargetField.USERNAME,
                    "password", TargetField.PASSWORD
            )),
            Map.entry("dashlane", Map.of(
                    "title", TargetField.TITLE,
                    "url", TargetField.URL,
                    "login", TargetField.USERNAME,
                    "password", TargetField.PASSWORD,
                    "note", TargetField.NOTE,
                    "category", TargetField.CATEGORY
            )),
            Map.entry("nordpass", mapOf(
                    "name", TargetField.TITLE,
                    "url", TargetField.URL,
                    "username", TargetField.USERNAME,
                    "password", TargetField.PASSWORD,
                    "note", TargetField.NOTE
            )),
            Map.entry("protonpass", mapOf(
                    "name", TargetField.TITLE,
                    "url", TargetField.URL,
                    "email", TargetField.USERNAME,
                    "username", TargetField.USERNAME,
                    "password", TargetField.PASSWORD,
                    "note", TargetField.NOTE
            )),
            Map.entry("safari", Map.of(
                    "Title", TargetField.TITLE,
                    "URL", TargetField.URL,
                    "Username", TargetField.USERNAME,
                    "Password", TargetField.PASSWORD,
                    "Notes", TargetField.NOTE
            ))
    );

    /**
     * Genera il MappingSchema per le colonne date.
     *
     * @param headers       header del file sorgente
     * @param detectedSource source riconosciuto dal parser (nullable)
     * @param sampleRows     prime righe di dati per heuristic
     */
    public MappingSchema map(List<String> headers, String detectedSource,
                             List<Map<String, String>> sampleRows) {

        MappingSchema schema = new MappingSchema();
        schema.setDetectedSource(detectedSource);

        // 1. Profilo hardcoded
        if (detectedSource != null && SOURCE_PROFILES.containsKey(detectedSource)) {
            Map<String, TargetField> profile = SOURCE_PROFILES.get(detectedSource);
            for (String header : headers) {
                TargetField target = profile.get(header);
                if (target == null) {
                    // Prova case-insensitive
                    target = profile.entrySet().stream()
                            .filter(e -> e.getKey().equalsIgnoreCase(header))
                            .map(Map.Entry::getValue)
                            .findFirst().orElse(null);
                }
                if (target != null) {
                    schema.put(header, target, Confidence.HIGH);
                } else {
                    schema.put(header, TargetField.SKIP, Confidence.NONE);
                }
            }
            return schema;
        }

        // 2+3+4. Fuzzy mapping
        Map<String, TargetField> synonymIndex = TargetField.getSynonymIndex();
        Set<TargetField> assigned = new HashSet<>();

        for (String header : headers) {
            String normalized = TargetField.normalize(header);

            // 2a. Exact match sinonimi
            TargetField exact = synonymIndex.get(normalized);
            if (exact != null && !assigned.contains(exact)) {
                schema.put(header, exact, Confidence.HIGH);
                assigned.add(exact);
                continue;
            }

            // 2b. Contains match
            TargetField contains = findContainsMatch(normalized, assigned);
            if (contains != null) {
                schema.put(header, contains, Confidence.MEDIUM);
                assigned.add(contains);
                continue;
            }

            // 2c. Jaro-Winkler fuzzy > 0.85
            TargetField fuzzy = findJaroWinklerMatch(normalized, 0.85, assigned);
            if (fuzzy != null) {
                schema.put(header, fuzzy, Confidence.LOW);
                assigned.add(fuzzy);
                continue;
            }

            // 3. Heuristic sui valori
            TargetField heuristic = inferFromValues(header, sampleRows, assigned);
            if (heuristic != null) {
                schema.put(header, heuristic, Confidence.LOW);
                assigned.add(heuristic);
                continue;
            }

            // 4. SKIP
            schema.put(header, TargetField.SKIP, Confidence.NONE);
        }

        return schema;
    }

    private TargetField findContainsMatch(String normalized, Set<TargetField> assigned) {
        for (TargetField field : TargetField.values()) {
            if (field == TargetField.SKIP || assigned.contains(field)) continue;
            for (String syn : field.getSynonyms()) {
                String normSyn = TargetField.normalize(syn);
                if (normalized.contains(normSyn) || normSyn.contains(normalized)) {
                    return field;
                }
            }
        }
        return null;
    }

    private TargetField findJaroWinklerMatch(String normalized, double threshold,
                                              Set<TargetField> assigned) {
        TargetField best = null;
        double bestScore = threshold;

        for (TargetField field : TargetField.values()) {
            if (field == TargetField.SKIP || assigned.contains(field)) continue;
            for (String syn : field.getSynonyms()) {
                double score = jaroWinkler(normalized, TargetField.normalize(syn));
                if (score > bestScore) {
                    bestScore = score;
                    best = field;
                }
            }
        }
        return best;
    }

    private TargetField inferFromValues(String header, List<Map<String, String>> sampleRows,
                                        Set<TargetField> assigned) {
        if (sampleRows == null || sampleRows.isEmpty()) return null;

        int total = Math.min(sampleRows.size(), 10);
        int urlCount = 0, emailCount = 0, highEntropyCount = 0, longTextCount = 0;

        for (int i = 0; i < total; i++) {
            String val = sampleRows.get(i).getOrDefault(header, "");
            if (val.isEmpty()) continue;

            if (val.matches("(?i)https?://.*")) urlCount++;
            if (val.contains("@") && val.contains(".")) emailCount++;
            if (val.length() > 6 && !val.contains(" ") && !val.contains("@")
                    && !val.startsWith("http") && estimateEntropy(val) > 3.0) {
                highEntropyCount++;
            }
            if (val.length() > 50) longTextCount++;
        }

        double ratio = (double) total;
        if (!assigned.contains(TargetField.URL) && urlCount / ratio > 0.5) return TargetField.URL;
        if (!assigned.contains(TargetField.USERNAME) && emailCount / ratio > 0.5) return TargetField.USERNAME;
        if (!assigned.contains(TargetField.PASSWORD) && highEntropyCount / ratio > 0.5) return TargetField.PASSWORD;
        if (!assigned.contains(TargetField.NOTE) && longTextCount / ratio > 0.5) return TargetField.NOTE;

        return null;
    }

    private double estimateEntropy(String s) {
        if (s == null || s.isEmpty()) return 0;
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : s.toCharArray()) freq.merge(c, 1, Integer::sum);
        double entropy = 0;
        double len = s.length();
        for (int count : freq.values()) {
            double p = count / len;
            if (p > 0) entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    // ── Jaro-Winkler ──────────────────────────────────────────────────────

    static double jaroWinkler(String s1, String s2) {
        double jaro = jaro(s1, s2);
        int prefix = 0;
        int maxPrefix = Math.min(4, Math.min(s1.length(), s2.length()));
        for (int i = 0; i < maxPrefix; i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }

    private static double jaro(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int len1 = s1.length(), len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0.0;

        int matchDistance = Math.max(len1, len2) / 2 - 1;
        if (matchDistance < 0) matchDistance = 0;

        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];
        int matches = 0, transpositions = 0;

        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) continue;
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) return 0.0;

        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) continue;
            while (!s2Matches[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }

        return ((double) matches / len1 + (double) matches / len2
                + (double) (matches - transpositions / 2.0) / matches) / 3.0;
    }

    // Map.of supporta max 10 entries, helper per >10
    @SafeVarargs
    private static <K, V> Map<K, V> mapOf(Object... kv) {
        Map<K, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) kv[i];
            @SuppressWarnings("unchecked")
            V val = (V) kv[i + 1];
            m.put(key, val);
        }
        return Collections.unmodifiableMap(m);
    }
}
