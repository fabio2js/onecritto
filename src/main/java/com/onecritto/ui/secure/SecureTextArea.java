package com.onecritto.ui.secure;

import javafx.scene.control.Skin;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;

public class SecureTextArea extends SecureInputBase {

    private static final int MAX_CHARS = 2000;

    public SecureTextArea() {
        // riusa lo stile OneCritto (bordo, background, ecc.)

        setFocusTraversable(false);


        setBackground(Background.EMPTY);
        setBorder(Border.EMPTY);

    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SecureTextAreaSkin(this);
    }




}
