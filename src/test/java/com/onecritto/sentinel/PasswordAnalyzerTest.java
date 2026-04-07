package com.onecritto.sentinel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordAnalyzerTest {

    // --- Common / leet passwords should have very low entropy (~7-9 bits) ---

    @Test
    void commonPassword_veryLowEntropy() {
        double e = PasswordAnalyzer.computeEntropy("password".toCharArray());
        assertTrue(e < 10, "Common password 'password' should be < 10 bits, got " + e);
    }

    @Test
    void commonPasswordUpperFirstLetter_slightlyMore() {
        double e = PasswordAnalyzer.computeEntropy("Password".toCharArray());
        // +1 bit for case variation
        assertTrue(e < 11, "Common password 'Password' should be < 11 bits, got " + e);
        assertTrue(e > 7, "Should be > 7 bits (case adds ~1 bit), got " + e);
    }

    @Test
    void leetSpeakCommon_lowEntropy() {
        double e = PasswordAnalyzer.computeEntropy("p@ssw0rd".toCharArray());
        assertTrue(e < 12, "Leet-speak 'p@ssw0rd' should be < 12 bits, got " + e);
    }

    // --- Dictionary substring detection ---

    @Test
    void dictionarySubstring_reducedEntropy() {
        double withDict = PasswordAnalyzer.computeEntropy("MyPassword1!".toCharArray());
        double fullyRandom = PasswordAnalyzer.computeEntropy("k&$fx#mK2@vq".toCharArray());
        assertTrue(withDict < fullyRandom,
                "Password with dictionary word should have less entropy than random same-length. " +
                "Dict=" + withDict + ", Random=" + fullyRandom);
        assertTrue(withDict < 45, "'MyPassword1!' should be < 45 bits, got " + withDict);
    }

    // --- English dictionary words ---

    @Test
    void englishWord_butterfly_reducedEntropy() {
        double e = PasswordAnalyzer.computeEntropy("Butterfly42!".toCharArray());
        // "butterfly" is in the English dictionary, so entropy should be much less than naive
        assertTrue(e < 50, "'Butterfly42!' should be < 50 bits, got " + e);
    }

    @Test
    void englishWord_mountain_reducedEntropy() {
        double e = PasswordAnalyzer.computeEntropy("Mountain99".toCharArray());
        assertTrue(e < 30, "'Mountain99' should be < 30 bits, got " + e);
    }

    @Test
    void twoEnglishWords_reducedEntropy() {
        double e = PasswordAnalyzer.computeEntropy("SummerWinter".toCharArray());
        // Two dictionary words concatenated — each ~11 bits in a ~2000-word dict
        assertTrue(e < 35, "'SummerWinter' should be < 35 bits, got " + e);
    }

    @Test
    void nonDictionaryRandom_highEntropy() {
        // This should NOT match any dictionary words
        double e = PasswordAnalyzer.computeEntropy("xjqmzwpbtg".toCharArray());
        assertTrue(e > 40, "Non-dictionary random 'xjqmzwpbtg' should be > 40 bits, got " + e);
    }

    // --- Keyboard patterns ---

    @Test
    void keyboardPattern_lowEntropy() {
        double e = PasswordAnalyzer.computeEntropy("qwerty123".toCharArray());
        assertTrue(e < 20, "Keyboard pattern 'qwerty123' should be < 20 bits, got " + e);
    }

    // --- Repetitions ---

    @Test
    void repetitions_lowEntropy() {
        double e = PasswordAnalyzer.computeEntropy("aaabbb111".toCharArray());
        assertTrue(e < 20, "Repetition-heavy password should be < 20 bits, got " + e);
    }

    // --- Sequences ---

    @Test
    void sequences_lowEntropy() {
        double e = PasswordAnalyzer.computeEntropy("abcdef123456".toCharArray());
        assertTrue(e < 25, "Sequential password should be < 25 bits, got " + e);
    }

    // --- Truly random passwords should have high entropy ---

    @Test
    void randomPassword_highEntropy() {
        // 16 random chars from all classes
        double e = PasswordAnalyzer.computeEntropy("k&$fx#mK2@vqP!9z".toCharArray());
        assertTrue(e > 80, "16-char random all-class password should be > 80 bits, got " + e);
    }

    @Test
    void randomLowerOnly_moderateEntropy() {
        // 10 random lowercase letters (no patterns)
        double e = PasswordAnalyzer.computeEntropy("xjqmzwpbtg".toCharArray());
        assertTrue(e > 40, "10-char random lowercase should be > 40 bits, got " + e);
        assertTrue(e < 55, "10-char random lowercase should be < 55 bits, got " + e);
    }

    // --- Structure penalty ---

    @Test
    void structurePenalty_firstUpperTrailingDigit() {
        double withStructure = PasswordAnalyzer.computeEntropy("Xjqmzwpb1".toCharArray());
        double noStructure   = PasswordAnalyzer.computeEntropy("xJqmzw1pb".toCharArray());
        // "Xjqmzwpb1" has predictable first-upper + trailing digit => should be penalized
        assertTrue(withStructure < noStructure,
                "Predictable structure should yield less entropy. Struct=" + withStructure + ", NoStruct=" + noStructure);
    }

    // --- Empty / null ---

    @Test
    void emptyPassword_zeroEntropy() {
        assertEquals(0, PasswordAnalyzer.computeEntropy(new char[0]));
        assertEquals(0, PasswordAnalyzer.computeEntropy(null));
    }

    // --- Entropy should never be negative ---

    @Test
    void entropy_neverNegative() {
        // Various edge cases
        String[] cases = {"a", "11", "!!!", "Aa1!", "qwerty", "abcdefghij1234567890"};
        for (String pwd : cases) {
            double e = PasswordAnalyzer.computeEntropy(pwd.toCharArray());
            assertTrue(e >= 0, "Entropy should never be negative for '" + pwd + "', got " + e);
        }
    }

    // --- Multilingual dictionary words should get reduced entropy ---

    @Test
    void italianWord_montagna_reducedEntropy() {
        double e = PasswordAnalyzer.computeEntropy("Montagna99".toCharArray());
        assertTrue(e < 30, "Italian word 'Montagna99' should be < 30 bits, got " + e);
    }

    @Test
    void portugueseWord_borboleta_reducedEntropy() {
        double e = PasswordAnalyzer.computeEntropy("Borboleta42!".toCharArray());
        assertTrue(e < 50, "Portuguese word 'Borboleta42!' should be < 50 bits, got " + e);
    }

    @Test
    void spanishWord_esperanza_reducedEntropy() {
        double e = PasswordAnalyzer.computeEntropy("Esperanza2024".toCharArray());
        assertTrue(e < 45, "Spanish word 'Esperanza2024' should be < 45 bits, got " + e);
    }

    @Test
    void frenchWord_maison_reducedEntropy() {
        double e = PasswordAnalyzer.computeEntropy("Maison123!".toCharArray());
        assertTrue(e < 40, "French word 'Maison123!' should be < 40 bits, got " + e);
    }

    @Test
    void germanWord_freundschaft_reducedEntropy() {
        double e = PasswordAnalyzer.computeEntropy("Freundschaft1".toCharArray());
        assertTrue(e < 30, "German word 'Freundschaft1' should be < 30 bits, got " + e);
    }

    // --- Natural language patterns should reduce entropy even without dictionary match ---

    @Test
    void naturalLanguageText_reducedEntropy() {
        // "loreTAbucchinara4" looks like natural language (Italian name+word) even if not in dictionary
        double e = PasswordAnalyzer.computeEntropy("loreTAbucchinara4".toCharArray());
        assertTrue(e < 65, "'loreTAbucchinara4' should be < 65 bits (language-like), got " + e);
        // Should be significantly less than naive log2(62)*17 ≈ 101 bits
        assertTrue(e < 101 * 0.7, "Should be at least 30% less than naive estimate, got " + e);
    }

    @Test
    void trueRandomStaysHigh() {
        // Truly random text should NOT be penalized by language bigram detection
        double e = PasswordAnalyzer.computeEntropy("k&$fx#mK2@vqP!9z".toCharArray());
        assertTrue(e > 80, "Random 16-char password should remain > 80 bits, got " + e);
    }
}
