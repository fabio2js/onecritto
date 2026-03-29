package com.onecritto.ui.secure;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Control;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.Arrays;

public abstract class SecureInputBase extends Control {

    // ------- BUFFER RAM SICURO -------
    protected char[] buffer = new char[0];

    // ------- SHADOW UI (NON SICURA) -------
    protected char[] uiValue = new char[0];
    protected String renderedShadow = "";

    protected int cursorPos = 0;
    protected boolean reveal = false;

    public boolean isPwdField = false;
    // ---------- TITLE PROPERTY (per mostrare il nome del campo) ----------
    private final StringProperty title = new SimpleStringProperty("");

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String value) {
        title.set(value);
    }

    public StringProperty titleProperty() {
        return title;
    }

    private final BooleanProperty loginMode = new SimpleBooleanProperty(false);

    public boolean isLoginMode() {
        return loginMode.get();
    }

    public void setLoginMode(boolean value) {
        loginMode.set(value);
    }

    public BooleanProperty loginModeProperty() {
        return loginMode;
    }



    private Runnable onPwdValueChanged;

    public void setOnPwdValueChanged(Runnable r) {
        this.onPwdValueChanged = r;
    }

    protected void fireValueChanged() {
        if (onPwdValueChanged != null) onPwdValueChanged.run();
    }

    // blocca input durante wipe
    protected boolean inputLocked = false;

    // callback usata dalle skin
    protected Runnable onWipeRequest;



    // flag: questo componente ha scritto nella clipboard di Windows?
    private boolean windowsClipboardOwned = false;

    public void setValue(char[] source) {
        if (inputLocked) {
            return;
        }

        // 1) pulisci vecchi dati dal buffer/uiValue
        if (buffer != null) {
            wipe(buffer);
        }
        if (uiValue != null) {
            wipe(uiValue);
        }

        // 2) caso valore nullo/vuoto
        if (source == null || source.length == 0) {
            buffer = new char[0];
            uiValue = new char[0];
            renderedShadow = "";
            cursorPos = 0;
        } else {
            // 3) copia difensiva del valore in ingresso
            buffer = java.util.Arrays.copyOf(source, source.length);
            uiValue = java.util.Arrays.copyOf(buffer, buffer.length);

            // 4) shadow "chiaro" interno (coerente con il resto del controllo)
            renderedShadow = new String(uiValue);

            // 5) caret alla fine
            cursorPos = uiValue.length;
        }

        // 6) notifica la skin (se registrata)
        if (onValueChanged != null) {
            onValueChanged.run();
        }
        fireValueChanged();
    }
    // ===========================================================
    // COSTRUTTORE
    // ===========================================================
    public SecureInputBase() {
        setFocusTraversable(true);
    }

    // ===========================================================
    // API COMUNE PER TEXTFIELD E TEXTAREA
    // ===========================================================
    public void append(char c) {
        if (inputLocked) return;

        // FIX SICURO: caret non può superare buffer.length
        if (cursorPos < 0) cursorPos = 0;
        if (cursorPos > buffer.length) cursorPos = buffer.length;

        char[] newBuf = new char[buffer.length + 1];

        // parte prima del caret
        System.arraycopy(buffer, 0, newBuf, 0, cursorPos);

        // nuovo carattere
        newBuf[cursorPos] = c;

        // parte dopo
        System.arraycopy(buffer, cursorPos, newBuf, cursorPos + 1, buffer.length - cursorPos);

        wipe(buffer);
        buffer = newBuf;

        updateUiShadow();
        cursorPos++;

        renderedShadow = new String(uiValue);
        fireValueChanged();
    }

    public void clearValue() {
        // 1) cancella buffer reale
        wipe(buffer);
        buffer = new char[0];

        // 2) cancella shadow UI
        wipe(uiValue);
        uiValue = new char[0];

        // 3) cancella rendered
        renderedShadow = "";

        // 4) reset caret
        cursorPos = 0;

        // 5) notifica UI
        fireValueChanged();
    }


    public void backspace() {
        if (inputLocked) return;

        if (cursorPos < 0) cursorPos = 0;
        if (cursorPos > buffer.length) cursorPos = buffer.length;

        if (cursorPos == 0) return;
        if (buffer.length == 0) return;

        char[] newBuf = new char[buffer.length - 1];

        System.arraycopy(buffer, 0, newBuf, 0, cursorPos - 1);
        System.arraycopy(buffer, cursorPos, newBuf, cursorPos - 1, buffer.length - cursorPos);

        wipe(buffer);
        buffer = newBuf;

        updateUiShadow();
        cursorPos--;

        renderedShadow = new String(uiValue);
        fireValueChanged();
    }


    public void delete() {
        if (inputLocked) return;

        if (cursorPos < 0) cursorPos = 0;
        if (cursorPos > buffer.length) cursorPos = buffer.length;
        if (cursorPos >= buffer.length) return;

        char[] newBuf = new char[buffer.length - 1];

        // parte prima del caret
        if (cursorPos > 0) {
            System.arraycopy(buffer, 0, newBuf, 0, cursorPos);
        }
        // parte dopo il caret (salta 1 char)
        if (cursorPos < buffer.length - 1) {
            System.arraycopy(buffer, cursorPos + 1, newBuf, cursorPos,
                    buffer.length - cursorPos - 1);
        }

        wipe(buffer);
        buffer = newBuf;

        updateUiShadow();
        renderedShadow = new String(uiValue);
        fireValueChanged();
    }



    public void updateUiShadow() {
        uiValue = Arrays.copyOf(buffer, buffer.length);
    }

    public char[] getValue() {
        return Arrays.copyOf(buffer, buffer.length);
    }

    public String getRenderedClearText() {
        return renderedShadow;
    }

    public String getRenderedMaskedText() {
        if (uiValue == null || uiValue.length == 0) return "";

        // MASCHERA SEMPRE, indipendentemente dal carattere
        char[] out = new char[uiValue.length];
        Arrays.fill(out, '•');  // <-- bullet universale

        return new String(out); // OK perché è SOLO mask e NON contiene dati sensibili
    }

    public boolean isReveal() {
        return reveal;
    }

    public void setReveal(boolean reveal) {
        this.reveal = reveal;
    }

    public int getCursorPos() {
        return cursorPos;
    }

    public boolean isInputLocked() {
        return inputLocked;
    }

    // ===========================================================
    // COPY SEMPRE su Windows clipboard
    // ===========================================================
    public void copyToClipboard(char[] data) {
        if (data == null || data.length == 0) return;

        ClipboardContent cc = new ClipboardContent();
        cc.putString(new String(data));   // OK: stringa temporanea
        Clipboard.getSystemClipboard().setContent(cc);

        windowsClipboardOwned = true;


    }
    // ===========================================================
    // PASTE SEMPRE da Windows clipboard
    // ===========================================================
    public void pasteFromClipboard() {
        if (inputLocked) return;

        String clip = Clipboard.getSystemClipboard().getString();
        if (clip == null || clip.isEmpty()) return;

        char[] chars = clip.toCharArray();
        for (char c : chars) {
            if (c == '\r') continue;
            if (c == '\n' || (c >= 32 && c != 127)) {
                append(c);
            }
        }
        wipe(chars);

        renderedShadow = new String(uiValue);
    }


    // usato per il “secondo copy” quando la UI è fantasma
    public void recoverFromUiShadow() {
        if (buffer.length == 0 && uiValue.length > 0) {
            buffer = Arrays.copyOf(uiValue, uiValue.length);
            renderedShadow = new String(uiValue);
            cursorPos = uiValue.length;
        }
    }

    // ===========================================================
    // WIPE COMPLETO RAM
    // ===========================================================
    public void wipeAllMemory() {
        inputLocked = true;

        // cancella buffer sicuro
        wipe(buffer);
        buffer = new char[0];

        // eventuale cancella clipboard Windows SOLO se l'abbiamo scritta noi
        clearWindowsClipboardIfOwned();

        if (onWipeRequest != null) {
            onWipeRequest.run();
        }

        Platform.runLater(() -> inputLocked = false);
    }

    protected void clearWindowsClipboardIfOwned() {
        if (windowsClipboardOwned) {
            ClipboardContent cc = new ClipboardContent();
            cc.putString("");
            Clipboard.getSystemClipboard().setContent(cc);
        }
        windowsClipboardOwned = false;
    }

    public void setOnWipeRequest(Runnable r) {
        onWipeRequest = r;
    }

    // ===========================================================
    // UTILS
    // ===========================================================
    public void wipe(char[] arr) {
        if (arr != null) Arrays.fill(arr, '\0');
    }



    // callback usata dalle skin per aggiornare la UI
    protected Runnable onValueChanged;

    public void setOnValueChanged(Runnable r) {
        this.onValueChanged = r;
    }
}
