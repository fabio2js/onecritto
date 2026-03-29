package com.onecritto.sentinel.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated vault health report produced by Sentinel.
 */
@Data
public class VaultHealthReport {

    /** Overall vault health score 0-100 */
    private int healthScore;

    /** Per-entry reports */
    private List<SentinelReport> entryReports = new ArrayList<>();

    /** Recent anomaly events */
    private List<AnomalyEvent> anomalies = new ArrayList<>();

    // --- summary counters ---
    private int totalEntries;
    private int criticalCount;
    private int weakCount;
    private int fairCount;
    private int goodCount;
    private int strongCount;
    private int duplicateCount;
    private int oldPasswordCount;   // > 90 days without rotation

    public void recomputeCounts() {
        criticalCount = weakCount = fairCount = goodCount = strongCount = 0;
        duplicateCount = oldPasswordCount = 0;

        for (SentinelReport r : entryReports) {
            if (r.getPasswordScore() == null) continue;

            switch (r.getPasswordScore().getRiskLevel()) {
                case CRITICAL -> criticalCount++;
                case WEAK     -> weakCount++;
                case FAIR     -> fairCount++;
                case GOOD     -> goodCount++;
                case STRONG   -> strongCount++;
            }

            if (r.getPasswordScore().isDuplicate()) duplicateCount++;
            if (r.getPasswordAgeDays() > 90) oldPasswordCount++;
        }
        totalEntries = entryReports.size();
    }
}
