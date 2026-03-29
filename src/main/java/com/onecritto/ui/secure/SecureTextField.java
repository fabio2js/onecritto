package com.onecritto.ui.secure;

import javafx.scene.control.Skin;
import javafx.scene.layout.Background;

public class SecureTextField extends SecureInputBase {

    public SecureTextField() {

        // mantieni la style class per font, padding, ecc.

        setFocusTraversable(true);

        // IMPORTANTISSIMO: il Control non deve disegnare niente
       setBackground(Background.EMPTY);
      //  setBorder(Border.EMPTY);

    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SecureTextFieldSkin(this);
    }

    private boolean dialogMode = false;

    public boolean isDialogMode() {
        return dialogMode;
    }

    public void setDialogMode(boolean dialogMode) {
        this.dialogMode = dialogMode;
    }





}