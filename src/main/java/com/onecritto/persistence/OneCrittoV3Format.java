package com.onecritto.persistence;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public final class OneCrittoV3Format {

    // ---- MAGIC HEADER (8 byte) ----
    public static final byte[] MAGIC = new byte[]{
            'O','C','R','3', 0x0D, 0x0A, 0x2A, 0x2A
    };

    // ---- VERSIONE ----
    public static final int VERSION = 3;

    // ---- PARAMETRI CRITTO ----
    public static final int SALT_LENGTH = 32;
    public static final int METADATA_IV_LENGTH = 12;
    public static final int FILE_IV_LENGTH = 12;

    public static final int PBKDF2_ITERATIONS = 200_000;
    public static final int AES_KEY_LENGTH = 32;   // 256 bit
    public static final int GCM_TAG_LENGTH = 16;   // 128 bit

    // ---- HMAC ----
    public static final int HMAC_LENGTH = 32;

    // ---- UTILITÀ DI I/O ----
    public static void writeInt(java.io.OutputStream out, int v) throws java.io.IOException {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }




    public static void writeLongLE(OutputStream out, long v) throws IOException {
        for (int i = 0; i < 8; i++) {
            out.write((int) ((v >>> (8 * i)) & 0xFF));
        }
    }

    public static long readLongLE(RandomAccessFile raf) throws IOException {
        long r = 0;
        for (int i = 0; i < 8; i++) {
            int b = raf.read();
            if (b < 0) throw new EOFException("EOF reading long LE");
            r |= ((long) b & 0xFF) << (8 * i);
        }
        return r;
    }

}
