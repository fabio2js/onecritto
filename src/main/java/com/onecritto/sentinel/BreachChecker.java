package com.onecritto.sentinel;

import com.onecritto.util.SecureLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Breach checker using the Have I Been Pwned (HIBP) Passwords API
 * with k-anonymity: only the first 5 hex chars of the SHA-1 hash
 * are sent to the server, preserving password privacy.
 *
 * Protocol:
 *   1. SHA-1(password) → 40 hex chars
 *   2. Send prefix (first 5) → GET /range/{prefix}
 *   3. Server returns all suffixes matching that prefix
 *   4. Check locally if full suffix is in the list
 *   5. Each line = suffix:count  →  count = # of breaches
 */
public class BreachChecker {

    private static final String API_URL = "https://api.pwnedpasswords.com/range/";
    private static final int TIMEOUT_MS = 10_000;

    /** Cache: SHA-1 hex (uppercase) → breach count (0 = safe, -1 = error/not checked) */
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    public BreachChecker() {
    }

    /**
     * Result of a single breach check.
     */
    public record BreachResult(String entryId, String entryTitle, int breachCount, boolean error) {
        public boolean isBreached() { return breachCount > 0; }
        public boolean isSafe()     { return !error && breachCount == 0; }
    }

    /**
     * Check a single password against the HIBP database.
     *
     * @param password the password as char array (not stored, not logged)
     * @return breach count (0 = safe, >0 = found in N breaches, -1 = API error)
     */
    public int check(char[] password) {
        if (password == null || password.length == 0) return -1;

        try {
            String sha1Hex = sha1Hex(password);

            // Check cache first
            Integer cached = cache.get(sha1Hex);
            if (cached != null) return cached;

            String prefix = sha1Hex.substring(0, 5);
            String suffix = sha1Hex.substring(5);

            HttpURLConnection conn = (HttpURLConnection)
                    URI.create(API_URL + prefix).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "OneCritto-PasswordManager");
            conn.setRequestProperty("Add-Padding", "true");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            int status = conn.getResponseCode();
            if (status != 200) {
                SecureLogger.debug("HIBP API returned status " + status);
                conn.disconnect();
                return -1;
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line).append('\n');
                }
            } finally {
                conn.disconnect();
            }

            int count = parseSuffixCount(body.toString(), suffix);
            cache.put(sha1Hex, count);
            return count;

        } catch (Exception e) {
            SecureLogger.debug("Breach check failed: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Parse the HIBP response body to find the matching suffix.
     * Response format: "SUFFIX:COUNT\r\n" per line (suffix is 35 hex chars).
     */
    private int parseSuffixCount(String body, String suffix) {
        for (String line : body.split("\r?\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon < 0) continue;

            String lineSuffix = line.substring(0, colon).trim();
            if (lineSuffix.equalsIgnoreCase(suffix)) {
                try {
                    return Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    return 1; // found but can't parse count
                }
            }
        }
        return 0; // not found → safe
    }

    /**
     * Compute SHA-1 hex digest of a char array, then zero the intermediate byte array.
     */
    static String sha1Hex(char[] password) {
        byte[] bytes = new String(password).getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(bytes);
            return bytesToHex(digest).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 not available", e);
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /** Clear the internal cache. */
    public void clearCache() {
        cache.clear();
    }
}
