package com.onecritto.persistence;

public final class OneCrittoV4Format {

    private OneCrittoV4Format() { }

    // Puoi tenere la stessa MAGIC di V3, così il "tipo" di file resta OneCritto,
    // ma distingui il formato con il campo VERSION.
    public static final byte[] MAGIC = OneCrittoV3Format.MAGIC;

    // Nuova versione di formato
    public static final int VERSION = 4;

    // Stesse lunghezze di V3 per non cambiare layout del file
    public static final int SALT_LENGTH        = OneCrittoV3Format.SALT_LENGTH;
    public static final int METADATA_IV_LENGTH = OneCrittoV3Format.METADATA_IV_LENGTH;
    public static final int FILE_IV_LENGTH     = OneCrittoV3Format.FILE_IV_LENGTH;
    public static final int GCM_TAG_LENGTH     = OneCrittoV3Format.GCM_TAG_LENGTH;
    public static final int HMAC_LENGTH        = OneCrittoV3Format.HMAC_LENGTH;
    public static final int AES_KEY_LENGTH     = OneCrittoV3Format.AES_KEY_LENGTH;
}
