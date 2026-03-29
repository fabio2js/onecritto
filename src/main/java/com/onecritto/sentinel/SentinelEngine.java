package com.onecritto.sentinel;

import com.onecritto.model.SecretEntry;
import com.onecritto.model.Vault;
import com.onecritto.sentinel.model.*;
import javafx.application.Platform;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Central orchestrator for Sentinel security analysis.
 * Runs analyses asynchronously and delivers results on the JavaFX thread.
 */
public class SentinelEngine {

    private final PasswordAnalyzer passwordAnalyzer = new PasswordAnalyzer();
    private final AnomalyDetector anomalyDetector = new AnomalyDetector();
    private final RotationAdvisor rotationAdvisor = new RotationAdvisor();
    private final VaultHealthScorer healthScorer = new VaultHealthScorer();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sentinel-engine");
        t.setDaemon(true);
        return t;
    });

    private volatile VaultHealthReport lastReport;

    /**
     * Run full vault analysis asynchronously.
     * Callback is invoked on the JavaFX Application thread.
     */
    public void analyzeAsync(Vault vault, Consumer<VaultHealthReport> onComplete) {
        CompletableFuture.supplyAsync(() -> analyze(vault), executor)
                .thenAccept(report -> Platform.runLater(() -> {
                    lastReport = report;
                    if (onComplete != null) onComplete.accept(report);
                }));
    }

    /**
     * Synchronous analysis — use on background threads only.
     */
    public VaultHealthReport analyze(Vault vault) {
        // Filter out entries with empty passwords — they are incomplete/unused
        List<SecretEntry> entries = vault.getEntries().stream()
                .filter(e -> e.getPassword() != null && e.getPassword().length > 0)
                .toList();

        // 1. Score all passwords
        List<PasswordScore> scores = passwordAnalyzer.scoreAll(entries);

        // 2. Build per-entry reports
        List<SentinelReport> reports = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            SentinelReport sr = new SentinelReport();
            sr.setEntryId(entries.get(i).getId());
            sr.setEntryTitle(entries.get(i).getTitle());
            sr.setPasswordScore(scores.get(i));
            sr.setPasswordAgeDays(computePasswordAgeDays(entries.get(i)));
            sr.setOverallRisk(scores.get(i).getRiskLevel());
            reports.add(sr);
        }

        // 3. Collect anomalies
        List<AnomalyEvent> anomalies = anomalyDetector.getDetectedAnomalies();

        // 4. Compute vault health
        return healthScorer.compute(reports, anomalies);
    }

    /**
     * Compute password age in days from the entry's passwordChangedAt (or createdAt) timestamp.
     * Returns 0 if no date is available (legacy entries without tracking).
     */
    private int computePasswordAgeDays(SecretEntry entry) {
        long ts = entry.getPasswordChangedAt();
        if (ts <= 0) ts = entry.getCreatedAt();
        if (ts <= 0) return 0;

        long days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(ts), Instant.now());
        return (int) Math.max(0, days);
    }

    /**
     * Score a single entry (for real-time feedback in add/edit screens).
     */
    public PasswordScore scoreSingle(SecretEntry entry, boolean isDuplicate) {
        return passwordAnalyzer.scoreEntry(entry, isDuplicate);
    }

    /** Returns the most recent analysis report. */
    public VaultHealthReport getLastReport() {
        return lastReport;
    }

    /** Delegate: get the rotation plan from the last report. */
    public List<RotationAdvisor.RotationItem> getRotationPlan() {
        if (lastReport == null) return List.of();
        return rotationAdvisor.buildPlan(lastReport.getEntryReports());
    }

    /** Delegate: access to the anomaly detector for event recording. */
    public AnomalyDetector getAnomalyDetector() {
        return anomalyDetector;
    }

    /** Shutdown the executor (call on application exit). */
    public void shutdown() {
        executor.shutdownNow();
    }
}
