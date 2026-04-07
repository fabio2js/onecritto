package com.onecritto.sentinel;

import com.onecritto.i18n.I18n;
import com.onecritto.model.SecretEntry;
import com.onecritto.sentinel.model.PasswordScore;
import com.onecritto.sentinel.model.PasswordScore.RiskLevel;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    // ---- Common bigrams across European languages (EN, IT, ES, PT, FR, DE) ----
    private static final Set<String> LANGUAGE_BIGRAMS = Set.of(
            "en", "er", "an", "re", "on", "in", "es", "de", "te", "al",
            "ar", "or", "at", "ti", "le", "se", "ne", "la", "co", "ta",
            "st", "el", "no", "to", "ra", "io", "nd", "ch", "it", "is",
            "me", "ni", "si", "li", "do", "pe", "ma", "ca", "na", "ri",
            "lo", "il", "ha", "ng", "ou", "nt", "ad", "he", "th", "ge",
            "ei", "be", "un", "ie", "os", "as", "em", "da", "ce", "mi"
    );
    /** Bits per char for natural European language text (Shannon estimate). */
    private static final double NATURAL_LANG_BPC = 1.5;

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

    // ---- Multilingual word dictionaries (loaded from resource files) ----
    private static final Set<String> DICTIONARY_WORDS;
    private static final String[] DICT_FILES = {
        "/sentinel/english_words.txt",
        "/sentinel/italian_words.txt",
        "/sentinel/portuguese_words.txt",
        "/sentinel/spanish_words.txt",
        "/sentinel/french_words.txt",
        "/sentinel/german_words.txt"
    };
    static {
        Set<String> words = new HashSet<>();
        for (String dictFile : DICT_FILES) {
            try (InputStream is = PasswordAnalyzer.class.getResourceAsStream(dictFile)) {
                if (is != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            String w = line.trim().toLowerCase(Locale.ROOT);
                            if (w.length() >= 4 && !w.startsWith("#")) words.add(w);
                        }
                    }
                }
            } catch (Exception ignored) { /* fallback: skip this file */ }
        }
        DICTIONARY_WORDS = Collections.unmodifiableSet(words);
    }

    /** All known words (common passwords + multilingual dictionaries) sorted by length descending for greedy matching. */
    private static final List<String> DICT_BY_LENGTH;
    static {
        Set<String> merged = new HashSet<>(COMMON_PASSWORDS);
        merged.addAll(DICTIONARY_WORDS);
        List<String> list = new ArrayList<>(merged);
        list.sort((a, b) -> b.length() - a.length());
        DICT_BY_LENGTH = Collections.unmodifiableList(list);
    }

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

    /**
     * Compute entropy using pattern-aware tokenization.
     * Detects dictionary words, keyboard patterns, repetitions, and sequences
     * to give realistic entropy values (similar to zxcvbn/KeePass approach).
     */
    static double computeEntropy(char[] pwd) {
        if (pwd == null || pwd.length == 0) return 0;

        String original = new String(pwd);
        String lower = original.toLowerCase(Locale.ROOT);
        String deleet = deLeet(lower);

        // --- Full-password common/leet match ---
        if (COMMON_PASSWORDS.contains(lower)) {
            double bits = log2(COMMON_PASSWORDS.size());
            for (char c : pwd) {
                if (Character.isUpperCase(c)) { bits += 1.0; break; }
            }
            return bits;
        }
        if (!deleet.equals(lower) && COMMON_PASSWORDS.contains(deleet)) {
            return log2(COMMON_PASSWORDS.size()) + 2.0;
        }

        // --- Token-based entropy estimation ---
        boolean[] consumed = new boolean[pwd.length];
        double entropy = 0.0;

        // 1. Dictionary substrings (longest first, >=4 chars)
        entropy += consumeDictionary(lower, deleet, consumed);

        // 2. Keyboard walk patterns
        entropy += consumeKeyboardPatterns(lower, consumed);

        // 3. Repetition runs (3+ identical chars)
        entropy += consumeRepetitions(lower, consumed);

        // 4. Sequential runs (3+ ascending/descending)
        entropy += consumeSequences(lower, consumed);

        // 5. Remaining truly random characters
        entropy += remainingEntropy(pwd, consumed);

        // 6. Predictable structure penalty
        entropy -= structurePenalty(pwd, consumed);

        return Math.max(1.0, entropy);
    }

    // ---- Entropy helper methods ----

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    /** Consume dictionary word substrings (greedy, longest first). */
    private static double consumeDictionary(String lower, String deleet, boolean[] consumed) {
        double bits = 0;
        for (String word : DICT_BY_LENGTH) {
            if (word.length() < 4) continue;
            bits += matchWordInText(lower, word, consumed);
            if (!deleet.equals(lower)) {
                bits += matchWordInText(deleet, word, consumed);
            }
        }
        return bits;
    }

    private static double matchWordInText(String text, String word, boolean[] consumed) {
        double bits = 0;
        int idx = text.indexOf(word);
        while (idx >= 0 && idx + word.length() <= consumed.length) {
            boolean overlap = false;
            for (int i = idx; i < idx + word.length(); i++) {
                if (consumed[i]) { overlap = true; break; }
            }
            if (!overlap) {
                for (int i = idx; i < idx + word.length(); i++) consumed[i] = true;
                // Common password = smaller pool (easier to guess), English word = larger pool
                double pool = COMMON_PASSWORDS.contains(word) ? COMMON_PASSWORDS.size() : DICT_BY_LENGTH.size();
                bits += log2(pool);
            }
            idx = text.indexOf(word, idx + 1);
        }
        return bits;
    }

    /** Consume keyboard walk patterns (longest first). */
    private static double consumeKeyboardPatterns(String lower, boolean[] consumed) {
        double bits = 0;
        String[] sorted = KEYBOARD_PATTERNS.clone();
        Arrays.sort(sorted, (a, b) -> b.length() - a.length());
        for (String pat : sorted) {
            int idx = lower.indexOf(pat);
            while (idx >= 0 && idx + pat.length() <= consumed.length) {
                boolean overlap = false;
                for (int i = idx; i < idx + pat.length(); i++) {
                    if (consumed[i]) { overlap = true; break; }
                }
                if (!overlap) {
                    for (int i = idx; i < idx + pat.length(); i++) consumed[i] = true;
                    bits += log2(KEYBOARD_PATTERNS.length * 2.0);
                }
                idx = lower.indexOf(pat, idx + 1);
            }
        }
        return bits;
    }

    /** Consume runs of 3+ identical characters. */
    private static double consumeRepetitions(String lower, boolean[] consumed) {
        double bits = 0;
        int i = 0;
        while (i < lower.length()) {
            if (consumed[i]) { i++; continue; }
            char c = lower.charAt(i);
            int runEnd = i + 1;
            while (runEnd < lower.length() && !consumed[runEnd] && lower.charAt(runEnd) == c) runEnd++;
            int runLen = runEnd - i;
            if (runLen >= 3) {
                for (int j = i; j < runEnd; j++) consumed[j] = true;
                bits += bitsForChar(c) + log2(runLen);
                i = runEnd;
            } else {
                i++;
            }
        }
        return bits;
    }

    /** Consume runs of 3+ sequential characters (ascending or descending). */
    private static double consumeSequences(String lower, boolean[] consumed) {
        double bits = 0;
        int i = 0;
        while (i < lower.length() - 2) {
            if (consumed[i] || consumed[i + 1]) { i++; continue; }
            char a = lower.charAt(i);
            char b = lower.charAt(i + 1);
            if (!sameCharClass(a, b)) { i++; continue; }
            int dir = b - a;
            if (dir != 1 && dir != -1) { i++; continue; }
            int seqEnd = i + 2;
            while (seqEnd < lower.length() && !consumed[seqEnd]
                    && sameCharClass(lower.charAt(seqEnd), lower.charAt(seqEnd - 1))
                    && (lower.charAt(seqEnd) - lower.charAt(seqEnd - 1)) == dir) {
                seqEnd++;
            }
            int seqLen = seqEnd - i;
            if (seqLen >= 3) {
                for (int j = i; j < seqEnd; j++) consumed[j] = true;
                int pool = Character.isDigit(a) ? 10 : 26;
                bits += log2(pool) + 1 + log2(seqLen);
                i = seqEnd;
            } else {
                i++;
            }
        }
        return bits;
    }

    private static boolean sameCharClass(char a, char b) {
        return (Character.isLetter(a) && Character.isLetter(b))
                || (Character.isDigit(a) && Character.isDigit(b));
    }

    /**
     * Entropy for remaining unconsumed characters.
     * Uses language bigram analysis to detect natural-language-like text
     * and character repetition penalty for repeated characters.
     */
    private static double remainingEntropy(char[] pwd, boolean[] consumed) {
        // Collect unconsumed characters
        List<Character> chars = new ArrayList<>();
        StringBuilder lowerBuf = new StringBuilder();
        boolean hasLower = false, hasUpper = false, hasDigit = false, hasSymbol = false;
        for (int i = 0; i < pwd.length; i++) {
            if (consumed[i]) continue;
            chars.add(pwd[i]);
            lowerBuf.append(Character.toLowerCase(pwd[i]));
            char c = pwd[i];
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSymbol = true;
        }
        int count = chars.size();
        if (count == 0) return 0;
        int charsetSize = (hasLower ? 26 : 0) + (hasUpper ? 26 : 0)
                + (hasDigit ? 10 : 0) + (hasSymbol ? 33 : 0);
        if (charsetSize == 0) return 0;
        double fullBpc = log2(charsetSize);

        // Language bigram analysis: measure how "language-like" the remaining text is
        String lowerStr = lowerBuf.toString();
        double languageRatio = 0;
        if (lowerStr.length() >= 2) {
            int bigramCount = 0;
            int langHits = 0;
            for (int i = 0; i < lowerStr.length() - 1; i++) {
                char a = lowerStr.charAt(i);
                char b = lowerStr.charAt(i + 1);
                if (Character.isLetter(a) && Character.isLetter(b)) {
                    bigramCount++;
                    if (LANGUAGE_BIGRAMS.contains("" + a + b)) langHits++;
                }
            }
            if (bigramCount > 0) languageRatio = (double) langHits / bigramCount;
        }

        // Interpolate per-char entropy: full charset ↔ natural language
        double effectiveBpc = fullBpc * (1.0 - languageRatio) + NATURAL_LANG_BPC * languageRatio;

        // Character repetition penalty: first occurrence gets effectiveBpc,
        // repeated chars get min(effectiveBpc, log2(uniquesSoFar))
        Set<Character> seen = new HashSet<>();
        double bits = 0;
        for (char c : chars) {
            if (seen.add(c)) {
                bits += effectiveBpc;
            } else {
                bits += Math.min(effectiveBpc, log2(Math.max(2, seen.size())));
            }
        }
        return bits;
    }

    /** Bits of entropy for a single character based on its class. */
    private static double bitsForChar(char c) {
        if (Character.isLowerCase(c)) return log2(26);
        if (Character.isUpperCase(c)) return log2(26);
        if (Character.isDigit(c))     return log2(10);
        return log2(33);
    }

    /** Penalty for predictable password structure. */
    private static double structurePenalty(char[] pwd, boolean[] consumed) {
        double penalty = 0;
        // Only first letter uppercase => ~1 bit instead of full ~4.7 bits
        if (pwd.length > 1 && Character.isUpperCase(pwd[0]) && !consumed[0]) {
            boolean onlyFirstUpper = true;
            for (int i = 1; i < pwd.length; i++) {
                if (!consumed[i] && Character.isUpperCase(pwd[i])) {
                    onlyFirstUpper = false;
                    break;
                }
            }
            if (onlyFirstUpper) {
                penalty += log2(26) - 1.0;
            }
        }
        // All digits at end => predictable positioning
        int trailingDigits = 0;
        for (int i = pwd.length - 1; i >= 0; i--) {
            if (!consumed[i] && Character.isDigit(pwd[i])) trailingDigits++;
            else if (!consumed[i]) break;
        }
        int totalDigits = 0;
        for (int i = 0; i < pwd.length; i++) {
            if (!consumed[i] && Character.isDigit(pwd[i])) totalDigits++;
        }
        if (trailingDigits > 0 && trailingDigits == totalDigits && trailingDigits <= 4) {
            penalty += trailingDigits * 1.5;
        }
        // All symbols at end => predictable positioning
        int trailingSymbols = 0;
        for (int i = pwd.length - 1; i >= 0; i--) {
            if (!consumed[i] && !Character.isLetterOrDigit(pwd[i])) trailingSymbols++;
            else if (!consumed[i]) break;
        }
        int totalSymbols = 0;
        for (int i = 0; i < pwd.length; i++) {
            if (!consumed[i] && !Character.isLetterOrDigit(pwd[i])) totalSymbols++;
        }
        if (trailingSymbols > 0 && trailingSymbols == totalSymbols && trailingSymbols <= 3) {
            penalty += trailingSymbols * 1.5;
        }
        return penalty;
    }

    private static String deLeet(String s) {
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
