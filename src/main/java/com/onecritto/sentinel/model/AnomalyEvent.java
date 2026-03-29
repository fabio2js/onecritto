package com.onecritto.sentinel.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an anomalous behavioral event detected by Sentinel.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyEvent {

    private long timestamp;
    private AnomalyType type;
    private String description;
    private Severity severity;

    public enum AnomalyType {
        UNUSUAL_HOUR,           // vault access at unusual hour
        MASS_COPY,              // many passwords copied rapidly
        RAPID_EXPORT,           // many files exported in short time
        FAILED_UNLOCK_BURST,    // multiple failed unlock attempts
        BULK_DELETE              // many entries deleted at once
    }

    public enum Severity {
        INFO,
        WARNING,
        ALERT
    }
}
