package com.onecritto.sentinel;

import com.onecritto.i18n.I18n;
import com.onecritto.sentinel.model.AnomalyEvent;
import com.onecritto.sentinel.model.AnomalyEvent.AnomalyType;
import com.onecritto.sentinel.model.AnomalyEvent.Severity;

import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Detects anomalous behavioral patterns in vault usage using simple
 * statistical rules — no AI/ML required.
 *
 * Tracked signals:
 * - Unusual access hours (outside user's normal window)
 * - Rapid mass copy of passwords
 * - Rapid bulk file export
 * - Multiple failed unlock attempts in short time
 * - Bulk entry deletion
 */
public class AnomalyDetector {

    // ---- configuration constants ----
    private static final int NORMAL_HOUR_START = 7;   // 07:00
    private static final int NORMAL_HOUR_END   = 23;  // 23:00

    private static final int COPY_BURST_THRESHOLD  = 5;
    private static final long COPY_BURST_WINDOW_MS = 60_000;   // 1 minute

    private static final int EXPORT_BURST_THRESHOLD  = 3;
    private static final long EXPORT_BURST_WINDOW_MS = 120_000; // 2 minutes

    private static final int FAILED_UNLOCK_THRESHOLD  = 5;
    private static final long FAILED_UNLOCK_WINDOW_MS = 180_000; // 3 minutes

    private static final int BULK_DELETE_THRESHOLD = 5;

    // ---- event windows (ring buffers) ----
    private final Deque<Long> copyTimestamps   = new ArrayDeque<>();
    private final Deque<Long> exportTimestamps = new ArrayDeque<>();
    private final Deque<Long> failedUnlockTimestamps = new ArrayDeque<>();

    // ---- detected anomalies (session-level) ----
    private final List<AnomalyEvent> detectedAnomalies = new ArrayList<>();

    /**
     * Call when the vault is accessed (opened / unlocked).
     */
    public AnomalyEvent checkAccessTime() {
        int hour = LocalTime.now().getHour();
        if (hour < NORMAL_HOUR_START || hour >= NORMAL_HOUR_END) {
            AnomalyEvent e = new AnomalyEvent(
                    System.currentTimeMillis(),
                    AnomalyType.UNUSUAL_HOUR,
                    I18n.t("sentinel.anomaly.unusual_hour"),
                    Severity.INFO
            );
            detectedAnomalies.add(e);
            return e;
        }
        return null;
    }

    /**
     * Call after a password is copied to clipboard.
     */
    public AnomalyEvent recordPasswordCopy() {
        return recordBurst(copyTimestamps, COPY_BURST_WINDOW_MS, COPY_BURST_THRESHOLD,
                AnomalyType.MASS_COPY, I18n.t("sentinel.anomaly.mass_copy"), Severity.WARNING);
    }

    /**
     * Call after a file is exported from the vault.
     */
    public AnomalyEvent recordFileExport() {
        return recordBurst(exportTimestamps, EXPORT_BURST_WINDOW_MS, EXPORT_BURST_THRESHOLD,
                AnomalyType.RAPID_EXPORT, I18n.t("sentinel.anomaly.rapid_export"), Severity.WARNING);
    }

    /**
     * Call after a failed unlock attempt.
     */
    public AnomalyEvent recordFailedUnlock() {
        return recordBurst(failedUnlockTimestamps, FAILED_UNLOCK_WINDOW_MS, FAILED_UNLOCK_THRESHOLD,
                AnomalyType.FAILED_UNLOCK_BURST, I18n.t("sentinel.anomaly.failed_unlock"), Severity.ALERT);
    }

    /**
     * Call when entries are bulk-deleted.
     */
    public AnomalyEvent checkBulkDelete(int deletedCount) {
        if (deletedCount >= BULK_DELETE_THRESHOLD) {
            AnomalyEvent e = new AnomalyEvent(
                    System.currentTimeMillis(),
                    AnomalyType.BULK_DELETE,
                    I18n.t("sentinel.anomaly.bulk_delete"),
                    Severity.ALERT
            );
            detectedAnomalies.add(e);
            return e;
        }
        return null;
    }

    /** Returns all anomalies detected in the current session. */
    public List<AnomalyEvent> getDetectedAnomalies() {
        return List.copyOf(detectedAnomalies);
    }

    /** Clears session anomalies (e.g. on vault lock). */
    public void clearSession() {
        detectedAnomalies.clear();
        copyTimestamps.clear();
        exportTimestamps.clear();
        failedUnlockTimestamps.clear();
    }

    // ---- internal helpers ----

    private AnomalyEvent recordBurst(Deque<Long> timestamps, long windowMs,
                                     int threshold, AnomalyType type,
                                     String description, Severity severity) {
        long now = System.currentTimeMillis();
        timestamps.addLast(now);

        // evict old entries outside the window
        while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > windowMs) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= threshold) {
            AnomalyEvent e = new AnomalyEvent(now, type, description, severity);
            detectedAnomalies.add(e);
            timestamps.clear(); // reset to avoid repeat triggers
            return e;
        }
        return null;
    }
}
