package com.onecritto.importer;

/**
 * Mapping di una singola colonna: colonna sorgente → campo target + confidenza.
 */
public record FieldMapping(
        String sourceColumn,
        TargetField targetField,
        Confidence confidence
) {}
