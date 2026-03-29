package com.onecritto.licensing;

import java.time.Instant;
import java.util.Objects;

/**
 * Modello dati della licenza trial firmata RSA.
 * Formato testuale semplice "chiave=valore" per riga.
 *
 * Campi:
 *  - hwid   : fingerprint macchina
 *  - start  : data inizio trial (Instant ISO-8601)
 *  - expire : data fine trial (Instant ISO-8601)
 *  - sig    : firma RSA Base64 di "hwid|start|expire"
 */
public final class TrialLicense {

    private final String hwid;
    private final Instant start;
    private final Instant expire;
    private final String signature; // Base64

    public TrialLicense(String hwid, Instant start, Instant expire, String signature) {
        this.hwid = Objects.requireNonNull(hwid, "hwid");
        this.start = Objects.requireNonNull(start, "start");
        this.expire = Objects.requireNonNull(expire, "expire");
        this.signature = Objects.requireNonNull(signature, "signature");
    }

    public String getHwid() {
        return hwid;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getExpire() {
        return expire;
    }

    public String getSignature() {
        return signature;
    }

    /**
     * Stringa canonica su cui viene calcolata/verificata la firma RSA.
     */
    public String dataToSign() {
        return hwid + "|" + start.toString() + "|" + expire.toString();
    }

    /**
     * Serializza in formato testuale:
     * hwid=...
     * start=...
     * expire=...
     * sig=...
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("hwid=").append(hwid).append("\n");
        sb.append("start=").append(start.toString()).append("\n");
        sb.append("expire=").append(expire.toString()).append("\n");
        sb.append("sig=").append(signature).append("\n");
        return sb.toString();
    }

    /**
     * Parsing dallo stesso formato di serialize().
     */
    public static TrialLicense parse(String content) {
        String[] lines = content.split("\\r?\\n");
        String hwid = null;
        Instant start = null;
        Instant expire = null;
        String sig = null;

        for (String line : lines) {
            if (line.isEmpty()) continue;
            int idx = line.indexOf('=');
            if (idx <= 0) continue;
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            switch (key) {
                case "hwid":
                    hwid = value;
                    break;
                case "start":
                    start = Instant.parse(value);
                    break;
                case "expire":
                    expire = Instant.parse(value);
                    break;
                case "sig":
                    sig = value;
                    break;
                default:
                    // ignora campi sconosciuti
            }
        }

        if (hwid == null || start == null || expire == null || sig == null) {
            throw new IllegalArgumentException("File di licenza trial corrotto o incompleto");
        }
        return new TrialLicense(hwid, start, expire, sig);
    }
}
