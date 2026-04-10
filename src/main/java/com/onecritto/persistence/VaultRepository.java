package com.onecritto.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.onecritto.model.SecretFile;
import com.onecritto.model.Vault;
import com.onecritto.observer.ProgressObservable;
import com.onecritto.security.FilePermissionUtils;
import com.onecritto.security.TempVaultFiles;
import com.onecritto.service.VaultContext;
import com.onecritto.util.SecureLogger;
import javafx.application.Platform;
import javafx.scene.control.TableView;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

public class VaultRepository extends ProgressObservable {



    private final ObjectMapper mapper = new ObjectMapper();


    public final static VaultContext VAULT_CONTEXT = new VaultContext();

    boolean saveFailed = false;

        public void loadVaultV4(Path input, char[] password) throws Exception {



        VAULT_CONTEXT.setVaultPath(input);
        VAULT_CONTEXT.setMasterPassword(password.clone());

        try (RandomAccessFile raf = new RandomAccessFile(input.toFile(), "r")) {

            SecureLogger.debug("loadVaultV4: starting load");
            notifyProgress(0.05);

            // 1) MAGIC
            byte[] magic = new byte[OneCrittoV4Format.MAGIC.length];
            raf.readFully(magic);

            int version = raf.readInt();

            if (!Arrays.equals(magic, OneCrittoV4Format.MAGIC)) {
                throw new IOException("File non è un OneCritto V4");
            }

            if (version != OneCrittoV4Format.VERSION) {
                throw new IOException("Versione non supportata (atteso V4)");
            }
            notifyProgress(0.10);

            // 3) SALT + META_IV
            byte[] salt = new byte[OneCrittoV4Format.SALT_LENGTH];
            raf.readFully(salt);


            VAULT_CONTEXT.setSalt(salt);

            byte[] metaIv = new byte[OneCrittoV4Format.METADATA_IV_LENGTH];
            raf.readFully(metaIv);
            VAULT_CONTEXT.setMetadataIv(metaIv);

            notifyProgress(0.20);


            long fileSize = raf.length();
            SecureLogger.debug("File size: " + fileSize + " bytes");


            // 4) POS HMAC + POS metaLen
            long hmacPos    = fileSize - OneCrittoV4Format.HMAC_LENGTH;
            long metaLenPos = hmacPos - 8;


            if (metaLenPos < 0) throw new IOException("File troppo corto");

            raf.seek(metaLenPos);
            long metaLen = OneCrittoV3Format.readLongLE(raf);
            SecureLogger.debug("Metadata length: " + metaLen + " bytes");

            if (metaLen <= 0 || metaLen > fileSize || metaLen > Integer.MAX_VALUE) {
                throw new IOException("Lunghezza metadati non valida: " + metaLen);
            }
            notifyProgress(0.30);
            long metaEncPos = metaLenPos - metaLen;

            if (metaEncPos < 0) throw new IOException("File corrotto (metaEncPos negativo)");

            raf.seek(metaEncPos);
            byte[] encMeta = new byte[(int) metaLen];
            raf.readFully(encMeta);
            notifyProgress(0.45);

            // 5) Deriva chiavi separate (64 byte Argon2 → AES + HMAC)
            SecretKey[] keys = CryptoServiceV4.deriveKeys(password, salt);
            SecretKey key = keys[0];
            SecretKey hmacKey = keys[1];

            VAULT_CONTEXT.setKeyEnc(key);              // usata per i file
            VAULT_CONTEXT.setEncryptedMetadata(encMeta); // usata per lo sblocco da lock screen

            if (!CryptoServiceV4.verifyHmac(input, hmacKey, hmacPos)) {
                throw new SecurityException("HMAC non valido: file corrotto o password errata");
            }
            notifyProgress(0.75);
            byte[] metadataJson = CryptoServiceV3.decryptMetadata(encMeta, key, metaIv);
          ;

            VaultMetadata meta = mapper.readValue(metadataJson, VaultMetadata.class);

            SecureLogger.debug("Entries: " + meta.getEntries().size());
            SecureLogger.debug("Files: " + meta.getFiles().size());

            // 6) Ricostruisci Vault con files
            Vault v = new Vault(meta.getEntries(), new ArrayList<>());

            if (meta.getFiles() != null) {

                for (FileMeta fm : meta.getFiles()) {



                    long ivOffset = fm.getOffset();
                    long contentOffset = ivOffset + OneCrittoV3Format.FILE_IV_LENGTH;

                    raf.seek(ivOffset);
                    byte[] fileIv = new byte[OneCrittoV3Format.FILE_IV_LENGTH];
                    raf.readFully(fileIv);

                    SecretFile sf = new SecretFile(
                            fm.getId(),
                            fm.getName(),
                            fm.getContentType(),
                            fm.getSize(),
                            input,
                            contentOffset,
                            fileIv
                    );
                    sf.setAddTime(fm.getAddTime());
                    sf.setLastEdit(fm.getLastEdit());

                    v.getFiles().add(sf);
                    double progressFiles = 0.75 + (0.20 * (v.getFiles().size() / (double) meta.getFiles().size()));
                     notifyProgress(progressFiles) ;

                }
            }

            // Restore SSH connections
            if (meta.getSshConnections() != null) {
                v.setSshConnections(new java.util.ArrayList<>(meta.getSshConnections()));
            }

            VAULT_CONTEXT.setVault(v);
            notifyProgress(1.0);
            SecureLogger.debug("loadVaultV4: load OK");
            checkMemoryAfterOperation();

        }
    }





    public void saveVaultV4(TableView<SecretFile> fileTable) throws Exception {

        saveFailed = false;

        Vault vault =  VAULT_CONTEXT.getVault();

        Path output = VAULT_CONTEXT.getVaultPath();

        // 1) Decidi se stai aggiornando un vault esistente
        boolean hasOldVault = java.nio.file.Files.exists(VAULT_CONTEXT.getVaultPath());

        byte[] salt;
        byte[] metaIv;

        // 1a) Se esiste già il vault, riusa il salt (la chiave AES deve restare
        //     la stessa per i blob dei file già cifrati). Il metaIv viene SEMPRE
        //     rigenerato per evitare il riuso del nonce GCM sui metadati.
        if (hasOldVault) {
            try (RandomAccessFile rafHeader = new RandomAccessFile(VAULT_CONTEXT.getVaultPath().toFile(), "r")) {

                byte[] magic = new byte[OneCrittoV3Format.MAGIC.length];
                rafHeader.readFully(magic);
                int version = rafHeader.readInt();

                if (!java.util.Arrays.equals(magic, OneCrittoV4Format.MAGIC) ||
                        version != OneCrittoV4Format.VERSION) {
                    throw new IOException("Vault esistente non è un OneCritto V4 valido");
                }

                salt = new byte[OneCrittoV4Format.SALT_LENGTH];
                rafHeader.readFully(salt);
            }
        } else {
            salt = CryptoServiceV3.randomBytes(OneCrittoV4Format.SALT_LENGTH);
        }
        // Nuovo IV metadati ad ogni salvataggio (nonce GCM non deve mai ripetersi con la stessa chiave)
        metaIv = CryptoServiceV3.randomBytes(OneCrittoV4Format.METADATA_IV_LENGTH);
        // Aggiorna il context della chiamata
        VAULT_CONTEXT.setSalt(salt);
        VAULT_CONTEXT.setMetadataIv(metaIv);

        // 1b) Prepara file temporaneo per scrittura sicura
        Path tmp =  Files.createTempFile(
                output.getParent(),
                output.getFileName().toString(),
                ".tmp"
        );

        try (RandomAccessFile rafOld = hasOldVault
                ? new RandomAccessFile(output.toFile(), "r")
                : null;
             FileOutputStream fos = new FileOutputStream(tmp.toFile());
             BufferedOutputStream out = new BufferedOutputStream(fos)) {




            // 2) HEADER
            out.write(OneCrittoV4Format.MAGIC);
            OneCrittoV3Format.writeInt(out, OneCrittoV4Format.VERSION);
            out.write(salt);
            out.write(metaIv);
            out.flush();



            // Chiavi separate (64 byte Argon2 → AES + HMAC)
            SecretKey[] keys = CryptoServiceV4.deriveKeys(
                    VAULT_CONTEXT.getMasterPassword(), salt);
            SecretKey key = keys[0];
            SecretKey hmacKey = keys[1];

            VAULT_CONTEXT.setKeyEnc(key);
            // 3) METADATA in memoria
            VaultMetadata metadata = new VaultMetadata();
            metadata.setEntries(vault.getEntries());

            java.util.List<FileMeta> fileMetas = new java.util.ArrayList<>();
            if (vault.getFiles() != null) {
                for (SecretFile f : vault.getFiles()) {
                    FileMeta fm = new FileMeta(
                            f.getId(),
                            f.getName(),
                            f.getContentType(),
                            0L,   // offset placeholder
                            0L,   // size placeholder
                            f.getLastEdit(),
                            f.getAddTime()
                    );
                    fileMetas.add(fm);
                }
            }
            metadata.setFiles(fileMetas);

            // SSH connections
            metadata.setSshConnections(vault.getSshConnections());






// ============ CALCOLO DIMENSIONE TOTALE PER IL PROGRESS =============
            long totalBytesForProgress = 0L;
            long writtenBytesForProgress = 0L;

            if (vault.getFiles() != null) {
                for (SecretFile f : vault.getFiles()) {

                    Path original = f.getVaultPath();


                    try {
                        if (original != null && java.nio.file.Files.exists(original)) {
                            totalBytesForProgress += java.nio.file.Files.size(original);
                        }
                    } catch (Exception ignored) {}
                }
            }

            notifyProgress(0.0);
            notifyMessage("Inizio salvataggio files...");


            // 4) SCRITTURA BLOB FILE
            if (vault.getFiles() != null) {

                for (int i = 0; i < vault.getFiles().size(); i++) {

                    SecretFile f = vault.getFiles().get(i);
                    FileMeta fm = fileMetas.get(i);

                    long ivOffsetNew = fos.getChannel().position();


                    // Caso A: file già nel vault precedente → copia cifrato
                    boolean copyFromOldVault =
                            hasOldVault
                                    && f.getVaultPath() != null
                                    && output.equals(f.getVaultPath())
                                    && f.getBlobOffset() > 0
                                    && f.getSize() > 0;

                    if (copyFromOldVault) {

                        // Caso A: copia diretta del blob cifrato (stessa chiave)
                        long oldIvOffset = f.getBlobOffset() - OneCrittoV3Format.FILE_IV_LENGTH;
                        long toCopy = OneCrittoV3Format.FILE_IV_LENGTH + f.getSize();

                        if (rafOld == null) {
                            throw new IOException("Vault precedente non disponibile per copia");
                        }

                        rafOld.seek(oldIvOffset);

                        byte[] buf = new byte[8192];
                        long remaining = toCopy;
                        while (remaining > 0) {
                            int r = rafOld.read(buf, 0, (int) Math.min(buf.length, remaining));
                            if (r == -1) {
                                throw new IOException("EOF copiando blob esistente");
                            }
                            out.write(buf, 0, r);
                            remaining -= r;
                        }
                        out.flush();

                        long contentOffsetNew = ivOffsetNew + OneCrittoV3Format.FILE_IV_LENGTH;
                        long after = fos.getChannel().position();
                        long encryptedSize = after - contentOffsetNew;



                        // aggiorna metadata
                        fm.setOffset(ivOffsetNew);
                        fm.setSize(encryptedSize);

                        // aggiornamento progresso durante copia-file
                        writtenBytesForProgress += encryptedSize;
                        if (totalBytesForProgress > 0) {
                            double progress = (double) writtenBytesForProgress / (double) totalBytesForProgress;
                            notifyProgress(progress);
                            notifyMessage("Copia " + f.getName() + " (" + String.format("%.0f%%", progress * 100) + ")");
                        }

                        // aggiorna oggetto runtime
                        f.setBlobOffset(contentOffsetNew);
                        f.setSize(encryptedSize);
                        // iv resta quello già presente in f.getIv()

                    } else {


                        // Caso B: file nuovo → cifra dal file sorgente
                        byte[] fileIv = CryptoServiceV3.randomBytes(OneCrittoV3Format.FILE_IV_LENGTH);
                        out.write(fileIv);
                        out.flush();

                        long contentOffsetNew = fos.getChannel().position();


                        long writtenPlain;
                        try (InputStream in = java.nio.file.Files.newInputStream(f.getVaultPath())) {
                            writtenPlain = CryptoServiceV3.encryptStream(in, out, key, fileIv);
                            writtenBytesForProgress += writtenPlain;

                            if (totalBytesForProgress > 0) {
                                double progress = (double) writtenBytesForProgress / (double) totalBytesForProgress;
                                notifyProgress(progress);
                                notifyMessage("Cifratura " + f.getName() + " (" + String.format("%.0f%%", progress * 100) + ")");
                            }
                        }
                        out.flush();

                        long after = fos.getChannel().position();
                        long encryptedSize = after - contentOffsetNew;

                        writtenBytesForProgress += writtenPlain;
                        if (totalBytesForProgress > 0) {
                            double progress = (double) writtenBytesForProgress / (double) totalBytesForProgress;
                            notifyProgress(progress);
                            notifyMessage("Cifratura " + f.getName() + " (" + String.format("%.0f%%", progress * 100) + ")");
                        }


                        fm.setOffset(ivOffsetNew);
                        fm.setSize(encryptedSize);

                        f.setBlobOffset(contentOffsetNew);
                        f.setSize(encryptedSize);
                        f.setIv(fileIv);
                        long total = vault.getFiles().stream().mapToLong(SecretFile::getSize).sum();
                        long done  = fileMetas.stream().mapToLong(FileMeta::getSize).sum();
                        double progress = total > 0 ? (double) done / total : 0.0;
                        notifyProgress(progress);
                        notifyMessage("Cifratura " + f.getName() + " (" + String.format("%.0f%%", progress * 100) + ")");
                    }
                }
            }



            // 5) METADATA cifrati: [ENC_META][META_LEN_LE]
            byte[] metadataJson = mapper.writeValueAsBytes(metadata);
            byte[] encMeta = CryptoServiceV3.encryptMetadata(metadataJson, key, metaIv);

            // Aggiorna il context così che unlock dopo lock usi IV e blob coerenti
            VAULT_CONTEXT.setEncryptedMetadata(encMeta);


            out.write(encMeta);
            out.flush();



            OneCrittoV3Format.writeLongLE(out, encMeta.length);
            out.flush();

            long beforeHmacSize = fos.getChannel().position();

            // 6) HMAC finale (usa hmacKey già derivata con deriveKeys)

            Mac mac = CryptoServiceV4.initMac(hmacKey);

            try (InputStream is = java.nio.file.Files.newInputStream(tmp)) {
                byte[] buf = new byte[8192];
                long remaining = beforeHmacSize;

                while (remaining > 0) {
                    int r = is.read(buf, 0, (int) Math.min(buf.length, remaining));
                    if (r == -1) break;
                    mac.update(buf, 0, r);
                    remaining -= r;
                }
            }

            byte[] hmac = mac.doFinal();



            out.write(hmac);
            out.flush();


            notifyProgress(1.0);
            notifyMessage("Vault saved successfully");
        } catch (Exception ex) {
            saveFailed = true;
            // RIMUOVE IL FILE APPENA AGGIUNTO
            if (vault.getFiles() != null && !vault.getFiles().isEmpty()) {

                SecretFile last = vault.getFiles().getLast();

                // lo rimuovo dalla lista RAM
                vault.getFiles().remove(last);

                // rimuovo anche da tabella
                Platform.runLater(() -> fileTable.getItems().remove(last));
            }


            throw ex;   // rilancia l’errore
        } finally {
            if (saveFailed) {
                try {
                       TempVaultFiles.secureDelete(tmp);

                } catch (Exception ignore) {

                }
            }
        }

        // 7) Sostituisci il vecchio vault con quello nuovo
        java.nio.file.Files.move(
                tmp,
                output,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );


        FilePermissionUtils.secureOwnerOnly(output);


        // 8) Dopo il salvataggio, TUTTI i file risiedono nel vault
        if (vault.getFiles() != null) {
            for (SecretFile f : vault.getFiles()) {
                f.setVaultPath(output);
            }
        }

    }
    private static long usedMB() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }

    private void checkMemoryAfterOperation() {
        // se superiamo 600MB di RAM usata → GC leggero
        if (usedMB() > 600) {
            SecureLogger.debug("Low memory: forcing cleanup, usedMB=" + usedMB());
            System.gc();
        }
    }
}
