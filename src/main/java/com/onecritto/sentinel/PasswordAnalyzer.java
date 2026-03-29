package com.onecritto.sentinel;

import com.onecritto.i18n.I18n;
import com.onecritto.model.SecretEntry;
import com.onecritto.sentinel.model.PasswordScore;
import com.onecritto.sentinel.model.PasswordScore.RiskLevel;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Analyzes password strength using entropy calculation, pattern detection,
 * dictionary matching, duplicate detection, and leet-speak recognition.
 * Pure Java — no AI, no external dependencies.
 */
public class PasswordAnalyzer {

    // ---- Top common passwords (embedded small dictionary) ----
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "123456", "password", "123456789", "12345678", "12345",
            "1234567", "qwerty", "abc123", "111111", "letmein",
            "iloveyou", "admin", "welcome", "monkey", "login",
            "dragon", "princess", "654321", "qwertyuiop", "aaa111",
            "passw0rd", "master", "sunshine", "trustno1", "password1",
            "qwerty123", "admin123", "user", "default", "root",
            "test", "1q2w3e4r", "zaq12wsx", "shadow", "batman",
            "football", "baseball", "soccer", "charlie", "michael",
            "ashley", "buster", "daniel", "jessica", "pepper",
            "hunter", "zxcvbnm", "thomas", "william", "access",
            "master123", "hello", "mustang", "696969", "abcdef",
            "qazwsx", "superman", "freedom", "killer", "marvel",
            "jordan", "jennifer", "andrew", "harley", "ranger",
            "dakota", "starwars", "ginger", "yellow", "robert",
            "cookie", "george", "computer", "michelle", "joshua",
            "maggie", "tigger", "silver", "samantha", "amanda",
            "andrea", "chicken", "matthew", "rockyou", "changeme",
            "biteme", "whatever", "secret", "google", "nothing",
            "internet", "service", "canada", "hello123", "banana",
            "pass123", "password123", "1234567890", "000000", "5201314",
            "666666", "888888", "987654", "112233", "121212"
    );

    // ---- Keyboard walk patterns ----
    private static final String[] KEYBOARD_PATTERNS = {
            "qwerty", "qwertyuiop", "asdf", "asdfgh", "asdfghjkl",
            "zxcvbn", "zxcvbnm", "1234", "12345", "123456",
            "2345", "3456", "4567", "5678", "6789", "7890",
            "abcd", "bcde", "cdef", "defg", "efgh",
            "1q2w3e", "1q2w3e4r", "qazwsx", "1qaz2wsx",
            "!@#$%", "!@#$%^", "!@#$%^&", "!@#$%^&*"
    };

    // ---- Leet-speak substitutions ----
    private static final Map<Character, Character> LEET_MAP = Map.ofEntries(
            Map.entry('@', 'a'), Map.entry('4', 'a'),
            Map.entry('8', 'b'),
            Map.entry('3', 'e'),
            Map.entry('6', 'g'),
            Map.entry('1', 'i'), Map.entry('!', 'i'),
            Map.entry('0', 'o'),
            Map.entry('5', 's'), Map.entry('$', 's'),
            Map.entry('7', 't'), Map.entry('+', 't'),
            Map.entry('2', 'z')
    );

    /**
     * Scores all entries in the vault.
     */
    public List<PasswordScore> scoreAll(List<SecretEntry> entries) {
        // Build hash set for duplicate detection
        Map<String, List<String>> hashToIds = new HashMap<>();
        for (SecretEntry e : entries) {
            if (e.getPassword() != null && e.getPassword().length > 0) {
                String hash = sha256Hex(e.getPassword());
                hashToIds.computeIfAbsent(hash, k -> new ArrayList<>()).add(e.getId());
            }
        }

        Set<String> duplicateIds = new HashSet<>();
        for (List<String> ids : hashToIds.values()) {
            if (ids.size() > 1) {
                duplicateIds.addAll(ids);
            }
        }

        List<PasswordScore> results = new ArrayList<>();
        for (SecretEntry entry : entries) {
            results.add(scoreEntry(entry, duplicateIds.contains(entry.getId())));
        }
        return results;
    }

    /**
     * Scores a single entry.
     */
    public PasswordScore scoreEntry(SecretEntry entry, boolean isDuplicate) {
        char[] pwd = entry.getPassword();
        PasswordScore ps = new PasswordScore();
        ps.setEntryId(entry.getId());
        ps.setEntryTitle(entry.getTitle());
        ps.setDuplicate(isDuplicate);

        if (pwd == null || pwd.length == 0) {
            ps.setScore(0);
            ps.setRiskLevel(RiskLevel.CRITICAL);
            ps.setSuggestion(I18n.t("sentinel.tip.empty"));
            return ps;
        }

        // --- 1. Entropy ---
        double entropy = computeEntropy(pwd);
        ps.setEntropyBits(entropy);

        // --- 2. Common password check ---
        String lower = new String(pwd).toLowerCase(Locale.ROOT);
        boolean isCommon = COMMON_PASSWORDS.contains(lower);
        ps.setCommonPassword(isCommon);

        // --- 3. Leet-speak de-substitution check ---
        String deleet = deLeet(lower);
        boolean isLeet = !deleet.equals(lower) && COMMON_PASSWORDS.contains(deleet);
        ps.setHasLeetSpeak(isLeet);

        // --- 4. Keyboard patterns ---
        boolean hasPattern = false;
        for (String pattern : KEYBOARD_PATTERNS) {
            if (lower.contains(pattern)) {
                hasPattern = true;
                break;
            }
        }
        ps.setHasKeyboardPattern(hasPattern);

        // --- 5. Repetitions ---
        boolean hasRepetitions = lower.matches(".*(.)\\1{2,}.*") || lower.matches("^(.)\\1+$");
        ps.setHasRepetitions(hasRepetitions);

        // --- Compute raw score ---
        int score = computeRawScore(pwd, entropy, isCommon, isLeet, hasPattern, hasRepetitions, isDuplicate);
        score = Math.max(0, Math.min(100, score));
        ps.setScore(score);
        ps.setRiskLevel(PasswordScore.levelFromScore(score));

        // --- Build suggestion ---
        ps.setSuggestion(buildSuggestion(ps));

        return ps;
    }

    // ---------- Internal scoring logic ----------

    private int computeRawScore(char[] pwd, double entropy, boolean isCommon,
                                boolean isLeet, boolean hasPattern,
                                boolean hasRepetitions, boolean isDuplicate) {
        if (isCommon || isLeet) {
            return 5; // common password is nearly worthless
        }

        int score = 0;

        // Entropy contribution (max ~55 points)
        if (entropy > 20) score += 10;
        if (entropy > 35) score += 10;
        if (entropy > 50) score += 10;
        if (entropy > 65) score += 10;
        if (entropy > 80) score += 10;
        if (entropy > 100) score += 5;

        // Length bonus
        if (pwd.length >= 12) score += 5;
        if (pwd.length >= 16) score += 5;
        if (pwd.length >= 20) score += 5;

        // Character variety
        boolean hasLower = false, hasUpper = false, hasDigit = false, hasSymbol = false;
        for (char c : pwd) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSymbol = true;
        }
        int variety = (hasLower ? 1 : 0) + (hasUpper ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSymbol ? 1 : 0);
        score += variety * 5; // max 20

        // Penalties
        if (hasPattern)     score -= 15;
        if (hasRepetitions) score -= 10;
        if (isDuplicate)    score -= 15;

        return score;
    }

    static double computeEntropy(char[] pwd) {
        int charsetSize = 0;
        boolean hasLower = false, hasUpper = false, hasDigit = false, hasSymbol = false;
        for (char c : pwd) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSymbol = true;
        }
        if (hasLower) charsetSize += 26;
        if (hasUpper) charsetSize += 26;
        if (hasDigit) charsetSize += 10;
        if (hasSymbol) charsetSize += 33;
        if (charsetSize == 0) return 0;
        return pwd.length * (Math.log(charsetSize) / Math.log(2));
    }

    private String deLeet(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(LEET_MAP.getOrDefault(c, c));
        }
        return sb.toString();
    }

    private String sha256Hex(char[] pwd) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new byte[pwd.length * 2];
            for (int i = 0; i < pwd.length; i++) {
                bytes[i * 2] = (byte) (pwd[i] >> 8);
                bytes[i * 2 + 1] = (byte) (pwd[i]);
            }
            byte[] hash = md.digest(bytes);
            // clear temp buffer
            Arrays.fill(bytes, (byte) 0);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildSuggestion(PasswordScore ps) {
        if (ps.isCommonPassword() || ps.isHasLeetSpeak()) {
            return I18n.t("sentinel.tip.common");
        }
        List<String> tips = new ArrayList<>();
        if (ps.isDuplicate()) tips.add(I18n.t("sentinel.tip.duplicate"));
        if (ps.isHasKeyboardPattern()) tips.add(I18n.t("sentinel.tip.pattern"));
        if (ps.isHasRepetitions()) tips.add(I18n.t("sentinel.tip.repetition"));
        if (ps.getEntropyBits() < 50) tips.add(I18n.t("sentinel.tip.short"));
        if (tips.isEmpty() && ps.getScore() >= 80) return I18n.t("sentinel.tip.good");
        if (tips.isEmpty()) return I18n.t("sentinel.tip.improve");
        return String.join(" ", tips);
    }

    /**
     * Score a standalone password without vault context (no duplicate check).
     * Returns a score 0-100, matching the Sentinel scoring system.
     */
    public int scorePassword(char[] pwd) {
        if (pwd == null || pwd.length == 0) return 0;

        double entropy = computeEntropy(pwd);
        String lower = new String(pwd).toLowerCase(Locale.ROOT);
        boolean isCommon = COMMON_PASSWORDS.contains(lower);
        String deleet = deLeet(lower);
        boolean isLeet = !deleet.equals(lower) && COMMON_PASSWORDS.contains(deleet);

        boolean hasPattern = false;
        for (String pattern : KEYBOARD_PATTERNS) {
            if (lower.contains(pattern)) { hasPattern = true; break; }
        }

        boolean hasRepetitions = lower.matches(".*(.)\\1{2,}.*") || lower.matches("^(.)\\1+$");

        int score = computeRawScore(pwd, entropy, isCommon, isLeet, hasPattern, hasRepetitions, false);
        return Math.max(0, Math.min(100, score));
    }
}
