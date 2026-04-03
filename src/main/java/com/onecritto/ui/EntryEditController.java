package com.onecritto.ui;

import com.onecritto.App;
import com.onecritto.i18n.I18n;
import com.onecritto.model.SecretEntry;
import com.onecritto.ui.secure.SecureTextArea;
import com.onecritto.ui.secure.SecureTextField;
import com.onecritto.util.SecureLogger;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;


public class EntryEditController {

    @FXML private TextField titleField;
    @FXML private TextField urlField;
    @FXML private SecureTextField usernameField;
    @FXML private SecureTextArea notesField;

    @FXML private SecureTextField secretField;



    @FXML private ProgressBar passwordStrengthBar;
    @FXML private Label passwordStrengthLabel;
    @FXML private ComboBox<String> categoryBox;

    private SecretEntry entry;
    private Runnable onSaveCallback;
    private char[] originalPassword;

    @Setter
    @Getter
    private MainController mainController;

    private void updatePasswordStrength(char[] pwd) {
        PasswordGeneratorController.updateStrengthBar(pwd, passwordStrengthBar,passwordStrengthLabel,false);
    }






    public void handleOpenPasswordGenerator() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/password_generator.fxml"),
                    I18n.getBundle()
            );



            Stage dialog = new Stage();
            dialog.setScene(new Scene(loader.load()));

            dialog.setTitle(I18n.t("entry.dialog.pwdgen.title"));
            dialog.initOwner(usernameField.getScene().getWindow());
            dialog.setResizable(false);
            dialog.getScene().getStylesheets().add(
                    Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
            );
            dialog.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
            ));
            dialog.addEventFilter(MouseEvent.ANY, e -> mainController.resetInactivityTimer());
            dialog.addEventFilter(KeyEvent.ANY, e -> mainController.resetInactivityTimer());
            PasswordGeneratorController ctrl = loader.getController();
            ctrl.initialize();
            ctrl.setMainController(mainController);

            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();


        } catch (Exception e) {
            SecureLogger.error(e.getMessage(),e);
        }
    }



    public void init(SecretEntry entry, Runnable onSaveCallback) {

        this.entry = entry;
        this.onSaveCallback = onSaveCallback;
        secretField.isPwdField=true;
        usernameField.setTitle(I18n.t("entry.field.username"));
        secretField.setTitle(I18n.t("entry.field.password"));
        notesField.setTitle(I18n.t("entry.field.notes"));
        secretField.setOnPwdValueChanged(() -> {
            updatePasswordStrength(secretField.getValue());
        });

        categoryBox.getItems().addAll(
                I18n.t("entry.category.password"),
                I18n.t("entry.category.note"),
                I18n.t("entry.category.other")
        );
        categoryBox.getSelectionModel().select(entry.getCategory());
        titleField.setText(entry.getTitle());
        urlField.setText(entry.getUrl());
        usernameField.setValue(entry.getUsername());
        secretField.setValue(entry.getPassword());
        notesField.setValue(entry.getNotes());

        // Save original password to detect changes
        originalPassword = entry.getPassword() != null
                ? Arrays.copyOf(entry.getPassword(), entry.getPassword().length)
                : null;




        Platform.runLater(() -> {
            Scene scene = usernameField.getScene();
            if (scene == null) return;

            scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.L) {
                    mainController.lockScreen();
                    event.consume();
                }
            });
        });

     }




    @FXML
    private void handleSave() {

        // 1) sincronizza i controlli secure col buffer reale
        usernameField.recoverFromUiShadow();
        secretField.recoverFromUiShadow();
        notesField.recoverFromUiShadow();

        // 2) aggiorna l'ENTRY ESISTENTE (this.entry), NON una nuova
        entry.setUsername(usernameField.getValue());
        char[] newPwd = secretField.getValue();
        boolean passwordChanged = !java.util.Arrays.equals(originalPassword, newPwd);
        entry.setPassword(newPwd);
        entry.setNotes(notesField.getValue());
        entry.setCategory(categoryBox.getSelectionModel().getSelectedItem());
        entry.setTitle(titleField.getText());
        entry.setUrl(urlField.getText());

        // Aggiorna passwordChangedAt solo se la password è cambiata (sentinel 90gg)
        if (passwordChanged) {
            if (newPwd == null || newPwd.length == 0) {
                entry.setPasswordChangedAt(0);
            } else {
                entry.setPasswordChangedAt(System.currentTimeMillis());
            }
        }

        // 3) callback: di solito refresh tabella + salvataggio vault
        if (onSaveCallback != null) {
            onSaveCallback.run();
        }

        // 4) chiudi la finestra
        close();



    }

    @FXML
    private void handleCancel() {
        close();
    }

    private void close() {
        ((Stage) titleField.getScene().getWindow()).close();
    }

}
