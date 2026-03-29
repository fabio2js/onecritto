package com.onecritto.sentinel.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed password scoring for a single entry.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordScore {

    private String entryId;
    private String entryTitle;

    /** Overall score 0-100 */
    private int score;

    /** Estimated entropy in bits */
    private double entropyBits;

    /** True if password appears in the top-10k common passwords dictionary */
    private boolean commonPassword;

    /** True if the same password is used by another entry in the vault */
    private boolean duplicate;

    /** True if keyboard patterns were detected (qwerty, 1234, etc.) */
    private boolean hasKeyboardPattern;

    /** True if leet-speak substitutions of common words were detected */
    private boolean hasLeetSpeak;

    /** True if excessive character repetitions found */
    private boolean hasRepetitions;

    /** Human-readable risk level */
    private RiskLevel riskLevel;

    /** Textual suggestions for improvement */
    private String suggestion;

    /** Breach count from HIBP: null=not checked, 0=safe, >0=found in N breaches, -1=error */
    private Integer breachCount;

    public enum RiskLevel {
        CRITICAL,   // score 0-19
        WEAK,       // score 20-39
        FAIR,       // score 40-59
        GOOD,       // score 60-79
        STRONG      // score 80-100
    }

    public static RiskLevel levelFromScore(int score) {
        if (score < 20) return RiskLevel.CRITICAL;
        if (score < 40) return RiskLevel.WEAK;
        if (score < 60) return RiskLevel.FAIR;
        if (score < 80) return RiskLevel.GOOD;
        return RiskLevel.STRONG;
    }
}
