package com.onecritto.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class SecureLogger {

    // Attiva/disattiva log di debug — impostabile una sola volta all'avvio
    private static volatile boolean DEBUG_MODE = false;
    private static volatile boolean debugModeLocked = false;

    /**
     * Inizializza DEBUG_MODE all'avvio dell'applicazione.
     * Può essere chiamato una sola volta; le chiamate successive vengono ignorate.
     */
    public static void initDebugMode(boolean enabled) {
        if (!debugModeLocked) {
            DEBUG_MODE = enabled;
            debugModeLocked = true;
        }
    }

    public static boolean isDebugMode() {
        return DEBUG_MODE;
    }

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SecureLogger() {}

    public static void debug(String msg) {
        if (DEBUG_MODE) {
            System.out.println("[DEBUG " + TS.format(LocalDateTime.now()) + "] " + msg);
        }
    }

    public static void info(String msg) {
        if (DEBUG_MODE) {
            System.out.println("[INFO  " + TS.format(LocalDateTime.now()) + "] " + msg);
        }
    }

    public static void error(String msg, Throwable ex) {
        if (DEBUG_MODE) {
            System.err.println("[ERROR " + TS.format(LocalDateTime.now()) + "] " + msg);
            if (ex != null) {
                ex.printStackTrace();
            }
        }
    }
}