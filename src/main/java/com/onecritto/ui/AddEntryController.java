package com.onecritto.ui;

import com.onecritto.App;
import com.onecritto.i18n.I18n;
import com.onecritto.model.SecretEntry;
import com.onecritto.ui.secure.SecureTextArea;
import com.onecritto.ui.secure.SecureTextField;
import com.onecritto.util.SecureLogger;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
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

import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

import static com.onecritto.util.UIUtils.showError;


public class AddEntryController {

    @FXML private TextField titleField;
    @FXML private SecureTextField usernameField;

    @FXML private SecureTextField passwordField;

    @FXML private ProgressBar passwordStrengthBar;
    @FXML private Label passwordStrengthLabel;

    @FXML private SecureTextArea notesField;
    @FXML private ComboBox<String> categoryBox;

    @Getter private boolean saved = false;
    @Getter private SecretEntry entry;
    @FXML private FontAwesomeIconView toggleIcon;
    @FXML private FontAwesomeIconView genPassword;
    @FXML private Button togglePasswordGenerator;



    @FXML
    public void initialize() {

        categoryBox.getItems().addAll(
                I18n.t("entry.category.password"),
                I18n.t("entry.category.note"),
                I18n.t("entry.category.other")
        );
        categoryBox.getSelectionModel().select(I18n.t("entry.category.password"));
        passwordField.isPwdField=true;
        passwordField.setOnPwdValueChanged(() -> {

            updatePasswordStrength(passwordField.getValue());
        });
        usernameField.setTitle(I18n.t("entry.field.username"));
        passwordField.setTitle(I18n.t("entry.field.password"));
        notesField.setTitle(I18n.t("entry.field.notes"));



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





    @Setter
    @Getter
    private MainController mainController;

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


    private void updatePasswordStrength(char[] pwd) {


            PasswordGeneratorController.updateStrengthBar(pwd, passwordStrengthBar, passwordStrengthLabel, true);


    }




    // ------------------------
    // SAVE / CANCEL
    // ------------------------
    @FXML
    private void handleSave() {

        if (titleField.getText().isBlank()) {
            showError(I18n.t("entry.error.titleRequired"));
            return;
        }

        char[] pwd = passwordField.getValue();

        entry = new SecretEntry();
        entry.setId(java.util.UUID.randomUUID().toString());
        entry.setTitle(titleField.getText());
        entry.setUsername(usernameField.getValue());
        entry.setPassword(pwd);
        entry.setNotes(notesField.getValue());
        entry.setCategory(categoryBox.getValue());

        long now = System.currentTimeMillis();
        entry.setCreatedAt(now);
        if (pwd != null && pwd.length > 0) {
            entry.setPasswordChangedAt(now);
        }

        saved = true;
        closeWindow();
    }

    @FXML
    private void handleCancel() {
        saved = false;
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) titleField.getScene().getWindow();
        stage.close();
    }


}
