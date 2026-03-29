package com.onecritto.sentinel;

import com.onecritto.sentinel.model.AnomalyEvent;
import com.onecritto.sentinel.model.PasswordScore;
import com.onecritto.sentinel.model.SentinelReport;
import com.onecritto.sentinel.model.VaultHealthReport;

import java.util.List;

/**
 * Aggregates individual entry scores and anomalies into a single
 * Vault Health Score (0-100).
 *
 * Scoring formula:
 *   base = weighted average of entry scores                        (max 70)
 *   duplicate penalty  = -2 per duplicate pair                     (up to -20)
 *   old password penalty = -1 per entry > 90 days                  (up to -15)
 *   anomaly penalty     = -3 per WARNING, -5 per ALERT anomaly    (up to -15)
 *   bonus: all entries STRONG and no duplicates                    (+10)
 */
public class VaultHealthScorer {

    public VaultHealthReport compute(List<SentinelReport> reports, List<AnomalyEvent> anomalies) {
        VaultHealthReport report = new VaultHealthReport();
        report.setEntryReports(reports);
        report.setAnomalies(anomalies);
        report.recomputeCounts();

        if (reports.isEmpty()) {
            report.setHealthScore(100); // empty vault = no risk
            return report;
        }

        // 1. Weighted average of entry scores (max 70 points)
        double sumScores = 0;
        for (SentinelReport r : reports) {
            sumScores += (r.getPasswordScore() != null) ? r.getPasswordScore().getScore() : 0;
        }
        double avgScore = sumScores / reports.size();
        int base = (int) Math.round(avgScore * 0.7); // scale to max 70

        // 2. Duplicate penalty
        int dupPenalty = Math.min(report.getDuplicateCount() * 2, 20);

        // 3. Old password penalty
        int oldPenalty = Math.min(report.getOldPasswordCount(), 15);

        // 4. Anomaly penalty
        int anomalyPenalty = 0;
        for (AnomalyEvent a : anomalies) {
            switch (a.getSeverity()) {
                case WARNING -> anomalyPenalty += 3;
                case ALERT   -> anomalyPenalty += 5;
                default      -> anomalyPenalty += 1;
            }
        }
        anomalyPenalty = Math.min(anomalyPenalty, 15);

        // 5. Perfect vault bonus
        int bonus = 0;
        if (report.getCriticalCount() == 0 && report.getWeakCount() == 0
                && report.getDuplicateCount() == 0 && report.getOldPasswordCount() == 0
                && report.getFairCount() == 0) {
            bonus = 10;
        }

        int health = base - dupPenalty - oldPenalty - anomalyPenalty + bonus;
        health = Math.max(0, Math.min(100, health));

        report.setHealthScore(health);
        return report;
    }
}
