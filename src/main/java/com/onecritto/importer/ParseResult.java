package com.onecritto.importer;

import java.util.List;
import java.util.Map;

/**
 * Risultato uniforme del parsing di un file di export password.
 */
public record ParseResult(
        List<String> headers,
        List<Map<String, String>> rows,
        String detectedSource
) {}
