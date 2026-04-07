package com.onecritto.importer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Schema di mapping completo: mappa ogni colonna sorgente al campo target.
 */
public class MappingSchema {

    private final Map<String, FieldMapping> mappings = new LinkedHashMap<>();
    private String detectedSource;

    public void put(String sourceColumn, TargetField target, Confidence confidence) {
        mappings.put(sourceColumn, new FieldMapping(sourceColumn, target, confidence));
    }

    public Map<String, FieldMapping> getMappings() {
        return Collections.unmodifiableMap(mappings);
    }

    public String fieldFor(TargetField target) {
        return mappings.entrySet().stream()
                .filter(e -> e.getValue().targetField() == target)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public void setMapping(String sourceColumn, TargetField target) {
        Confidence conf = mappings.containsKey(sourceColumn)
                ? mappings.get(sourceColumn).confidence()
                : Confidence.NONE;
        mappings.put(sourceColumn, new FieldMapping(sourceColumn, target, conf));
    }

    public String getDetectedSource() {
        return detectedSource;
    }

    public void setDetectedSource(String detectedSource) {
        this.detectedSource = detectedSource;
    }
}
