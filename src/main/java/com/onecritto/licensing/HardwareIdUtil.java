package com.onecritto.licensing;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HardwareIdUtil {

    private HardwareIdUtil() {
    }

    /**
     * Genera un HWID in tempo reale basato su:
     * - CPU ID
     * - Motherboard serial
     * - Disk serial (disco principale)
     */
    public static String getHardwareId() {
        try {
            String cpu   = normalize(getCpuId());
            String board = normalize(getBoardSerial());
            String disk  = normalize(getDiskSerial());

            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            update(sha, cpu);
            update(sha, board);
            update(sha, disk);

            return toHex(sha.digest()).toUpperCase();
        } catch (Exception e) {
            return "HWID_ERR";
        }
    }

    // --------------------------------------------------------
    // Raccolta hardware reale (senza cache)
    // --------------------------------------------------------

    private static String getCpuId() {
        if (isWindows()) {
            // 1) Tentativo con WMIC
            String wmicOutput = run("wmic", "cpu", "get", "processorid");
            String cpuId = extractLastNonEmptyLine(wmicOutput);
            if (cpuId != null && !cpuId.isEmpty()
                    && !cpuId.toLowerCase(Locale.ROOT).contains("processorid")) {
                return cpuId;
            }

            // 2) Fallback con PowerShell
            String psCmd = "Get-WmiObject Win32_Processor | "
                    + "Select-Object -ExpandProperty ProcessorId";
            String psOutput = runPowerShell(psCmd);
            String psCpuId = extractLastNonEmptyLine(psOutput);
            if (psCpuId != null && !psCpuId.isEmpty()) {
                return psCpuId;
            }

            return null;
        }

        if (isMac()) {
            // Su macOS leggiamo l'IOPlatformExpertDevice che contiene l'UUID macchina
            return run("ioreg", "-rd1", "-c", "IOPlatformExpertDevice");
        }

        if (isLinux()) {
            // Su Linux estraiamo solo campi stabili da /proc/cpuinfo
            // (vendor_id, cpu family, model, model name, stepping)
            // Il file intero contiene campi dinamici (cpu MHz, bogomips)
            // che cambiano ad ogni boot e invalidano l'HWID
            return extractStableCpuInfo();
        }

        return null;
    }

    private static String getBoardSerial() {
        if (isWindows()) {
            String out = run("wmic", "baseboard", "get", "serialnumber");
            return extractLastNonEmptyLine(out);
        }
        if (isMac()) {
            // Spesso l'IOPlatformExpertDevice contiene anche il serial della scheda
            return run("ioreg", "-rd1", "-c", "IOPlatformExpertDevice");
        }
        if (isLinux()) {
            // board_serial richiede permessi root; se non leggibile,
            // usiamo /etc/machine-id che e' sempre leggibile e stabile
            String serial = readFile("/sys/class/dmi/id/board_serial");
            if (serial != null && !serial.isBlank()) return serial;
            return readFile("/etc/machine-id");
        }
        return null;
    }

    private static String getDiskSerial() {
        if (isWindows()) {
            String out = run("wmic", "diskdrive", "get", "serialnumber");
            return extractLastNonEmptyLine(out);
        }
        if (isMac()) {
            // IOMedia contiene informazioni sui dischi; estraiamo l'output grezzo
            return run("ioreg", "-rd1", "-c", "IOMedia");
        }
        if (isLinux()) {
            // Prova i path comuni per serial disco
            for (String dev : new String[]{"sda", "nvme0n1", "vda", "sdb"}) {
                String s = readFile("/sys/class/block/" + dev + "/device/serial");
                if (s != null && !s.isBlank()) return s;
            }
            // Fallback con lsblk
            String lsblk = run("lsblk", "-ndo", "SERIAL", "/dev/sda");
            if (lsblk != null && !lsblk.isBlank()) return lsblk;
            lsblk = run("lsblk", "-ndo", "SERIAL", "/dev/nvme0n1");
            if (lsblk != null && !lsblk.isBlank()) return lsblk;
        }
        return null;
    }

    // --------------------------------------------------------
    // Helpers generali
    // --------------------------------------------------------

    private static void update(MessageDigest md, String s) {
        if (s != null && !s.isEmpty()) {
            md.update(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Esegue un comando nativo e restituisce l'output completo come stringa.
     */
    private static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = r.readLine()) != null) {
                    lines.add(line);
                }
            }
            return String.join("\n", lines);
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Esegue un comando PowerShell e restituisce l'output completo come stringa.
     */
    private static String runPowerShell(String command) {
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command", command)
                    .redirectErrorStream(true)
                    .start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = r.readLine()) != null) {
                    lines.add(line);
                }
            }
            return String.join("\n", lines);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String readFile(String path) {
        try {
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of(path),
                    StandardCharsets.UTF_8
            );
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Estrae solo i campi stabili da /proc/cpuinfo (Linux).
     * Esclude campi dinamici come cpu MHz e bogomips che cambiano ad ogni boot.
     */
    private static String extractStableCpuInfo() {
        String cpuinfo = readFile("/proc/cpuinfo");
        if (cpuinfo == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String line : cpuinfo.split("\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("vendor_id") ||
                lower.startsWith("cpu family") ||
                lower.startsWith("model name") ||
                lower.startsWith("model\t") ||
                lower.startsWith("stepping")) {
                sb.append(line.trim());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Normalizza le stringhe hardware per evitare differenze dovute a spazi,
     * newline o formattazioni differenti dopo aggiornamenti.
     */
    private static String normalize(String s) {
        if (s == null) return "";
        return s
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Restituisce l'ultima riga non vuota da un output multilinea.
     * Utile con WMIC che stampa header + valore su righe separate.
     */
    private static String extractLastNonEmptyLine(String output) {
        if (output == null) return null;
        String[] lines = output.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String l = lines[i].trim();
            if (!l.isEmpty()) {
                return l;
            }
        }
        return null;
    }

    private static boolean isWindows() {
        return os().contains("win");
    }

    private static boolean isMac() {
        return os().contains("mac");
    }

    private static boolean isLinux() {
        return os().contains("linux");
    }

    private static String os() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
            sb.append(Character.forDigit((b & 0xF), 16));
        }
        return sb.toString();
    }
}
