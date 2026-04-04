package com.onecritto.persistence;

import com.onecritto.model.SecretEntry;
import com.onecritto.model.SshConnection;
import lombok.Data;

import java.util.List;

/**
 * Struttura dei metadati cifrati del formato OneCritto V3.
 *
 * Questa classe viene serializzata/deserializzata da Jackson come JSON
 * all’interno della sezione "metadata" del file OneCritto V3.
 */
@Data
public class VaultMetadata {

    /** Lista delle password/account salvati */
    private List<SecretEntry> entries;

    /** Lista dei file allegati */
    private List<FileMeta> files;

    /** Lista delle connessioni SSH salvate */
    private List<SshConnection> sshConnections;

}
