package com.onecritto.importer;

import java.nio.file.Path;

/**
 * Interfaccia per i parser dei diversi formati di file di export password.
 */
public interface FileParser {

    boolean canParse(Path file);

    ParseResult parse(Path file) throws Exception;
}
