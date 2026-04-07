package com.onecritto.importer;

import com.onecritto.importer.parser.CsvParser;
import com.onecritto.model.SecretEntry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Orchestratore dell'import: coordina parser → mapper → transformer.
 */
public class ImportOrchestrator {

    private final List<FileParser> parsers = List.of(
            new CsvParser()
    );

    private final FieldMapper fieldMapper = new FieldMapper();
    private final EntryTransformer transformer = new EntryTransformer();

    /**
     * Parsa il file e genera il mapping schema per la preview.
     *
     * @return risultato intermedio contenente dati raw + mapping
     * @throws ImportException se il file non è supportato o è vuoto
     */
    public ImportPreviewData prepareImport(Path file) throws ImportException {
        FileParser parser = parsers.stream()
                .filter(p -> p.canParse(file))
                .findFirst()
                .orElseThrow(() -> new ImportException("import.error.unsupported"));

        ParseResult result;
        try {
            result = parser.parse(file);
        } catch (Exception e) {
            throw new ImportException("import.error.parse", e);
        }

        if (result.rows().isEmpty()) {
            throw new ImportException("import.error.empty");
        }

        List<Map<String, String>> sample = result.rows().size() > 10
                ? result.rows().subList(0, 10)
                : result.rows();

        MappingSchema schema = fieldMapper.map(result.headers(), result.detectedSource(), sample);

        return new ImportPreviewData(result, schema);
    }

    /**
     * Applica il mapping (eventualmente modificato dall'utente nella preview)
     * e genera le entry pronte per il vault.
     */
    public List<SecretEntry> executeImport(ImportPreviewData previewData) {
        return transformer.transform(previewData.parseResult(), previewData.schema());
    }

    /**
     * Dati intermedi mostrati nella preview dialog.
     */
    public record ImportPreviewData(
            ParseResult parseResult,
            MappingSchema schema
    ) {}

    public static class ImportException extends Exception {
        public ImportException(String messageKey) {
            super(messageKey);
        }

        public ImportException(String messageKey, Throwable cause) {
            super(messageKey, cause);
        }
    }
}
