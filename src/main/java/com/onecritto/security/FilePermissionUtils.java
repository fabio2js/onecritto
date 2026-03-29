package com.onecritto.security;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

public final class FilePermissionUtils {

    private FilePermissionUtils() { }

    /**
     * Imposta permessi "owner-only" sul file indicato.
     * - Su POSIX (Linux/macOS): chmod 600
     * - Su Windows NTFS: ACL owner-only (SYSTEM + owner)
     * - Su FAT/FAT32/exFAT/ReFS: nessuna ACL applicabile → fallback sicuro
     */
    public static void secureOwnerOnly(Path path) {

        // ==================================
        // 1) POSIX (Linux / macOS / UNIX)
        // ==================================
        try {
            Set<PosixFilePermission> perms =
                    PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, perms);
            return; // POSIX OK → fine
        } catch (UnsupportedOperationException ignore) {
            // Non POSIX → Windows
        } catch (Exception ex) {
            // Continuiamo verso Windows
        }

        // ==================================
        // 2) Windows NTFS — ACL owner-only
        // ==================================
        try {
            AclFileAttributeView aclView =
                    Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (aclView == null) {
                return; // filesystem senza ACL (FAT32, exFAT)
            }

            UserPrincipal owner = aclView.getOwner();

            // ACE: owner → full control
            AclEntry ownerEntry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(
                            AclEntryPermission.READ_DATA,
                            AclEntryPermission.WRITE_DATA,
                            AclEntryPermission.APPEND_DATA,
                            AclEntryPermission.READ_ATTRIBUTES,
                            AclEntryPermission.WRITE_ATTRIBUTES,
                            AclEntryPermission.READ_NAMED_ATTRS,
                            AclEntryPermission.WRITE_NAMED_ATTRS,
                            AclEntryPermission.DELETE,
                            AclEntryPermission.READ_ACL,
                            AclEntryPermission.SYNCHRONIZE
                    )
                    .build();

            // ACE: SYSTEM → read/write (necessario per backup, antivirus, servizi OS)
            UserPrincipal system = path.getFileSystem()
                    .getUserPrincipalLookupService()
                    .lookupPrincipalByName("NT AUTHORITY\\SYSTEM");

            AclEntry systemEntry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(system)
                    .setPermissions(
                            AclEntryPermission.READ_DATA,
                            AclEntryPermission.WRITE_DATA,
                            AclEntryPermission.READ_ATTRIBUTES,
                            AclEntryPermission.SYNCHRONIZE
                    )
                    .build();

            // Sostituisce TUTTA la ACL con solo owner + SYSTEM
            aclView.setAcl(List.of(ownerEntry, systemEntry));

        } catch (Exception ex) {
            // Filesystem non supporta ACL o errore di lookup → fallback silenzioso
        }
    }
}
