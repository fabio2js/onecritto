package com.onecritto.i18n;

import com.onecritto.util.SecureLogger;
import com.onecritto.util.UIUtils;

import java.util.Currency;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class I18n {

    private static Locale currentLocale;
    private static ResourceBundle bundle;



    public static synchronized ResourceBundle getBundle() {
        if (bundle == null) {
            // inizializzazione sicura lazily
            setLocale(Locale.getDefault());
        }
        return bundle;
    }
    private I18n() {
    }




    private static ResourceBundle loadBundle(Locale locale) {
        try {
            return ResourceBundle.getBundle("i18n.messages", locale);
        } catch (MissingResourceException e) {
            // fallback sicuro all'inglese
            return ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        }
    }
    public static void switchToEnglish() {
        setLocale(Locale.ENGLISH);
    }




    public static synchronized void setLocale(Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }

        currentLocale = locale;
        bundle = loadBundle(locale);
    }



    public static Locale getCurrentLocale() {

        return currentLocale;
    }

    public static String t(String key) {
        if (bundle == null) {
            setLocale(Locale.getDefault());
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            // debug-friendly: vedi subito se manca una chiave
            return "!" + key + "!";
        }
    }
}
