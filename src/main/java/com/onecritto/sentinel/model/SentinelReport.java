package com.onecritto.sentinel.model;

import lombok.Data;

/**
 * Security report for a single vault entry.
 */
@Data
public class SentinelReport {

    private String entryId;
    private String entryTitle;

    private PasswordScore passwordScore;

    /** Password age in days (0 if unknown) */
    private int passwordAgeDays;

    /** Overall entry risk level (derived from password score + age) */
    private PasswordScore.RiskLevel overallRisk;
}
