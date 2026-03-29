package com.onecritto.ui;

import com.onecritto.App;
import com.onecritto.model.Vault;
import com.onecritto.observer.ProgressObserver;
import com.onecritto.persistence.VaultRepository;
import com.onecritto.sentinel.PasswordAnalyzer;
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
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.onecritto.i18n.I18n;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;


public class LoginController implements ProgressObserver {
    @FXML
    public VBox rootContainer;
    @FXML public ProgressBar loginProgressBar;
    @FXML public Label loginProgressLabel;
    @FXML public FontAwesomeIconView genPassword;
    @FXML public TextFlow loginHelpText;
    @FXML public Button btnLangEN;
    @FXML public Button btnLangIT;
    @FXML public Label languageLabel;
    @FXML public Label updateLicenseLabel;
    @FXML
    private SecureTextField masterPasswordField;

    @FXML
    private Label errorLabel;

    @FXML private ProgressBar strengthBar;
    @FXML private Label strengthLabel;

    @FXML
    private Button openVaultButton;

    @FXML
    private Button createVaultButton;   // <- aggiunto per showSaveDialog

    private final VaultRepository repository = new VaultRepository();
    private final PasswordAnalyzer passwordAnalyzer = new PasswordAnalyzer();


    private void openLicenseUpdateDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/trial_request.fxml"),
                    I18n.getBundle()
            );

            Stage dialog = new Stage();
            dialog.setTitle("Update License");
            dialog.initOwner(updateLicenseLabel.getScene().getWindow()); // Modale su login window
            dialog.initModality(Modality.APPLICATION_MODAL);

            Scene scene = new Scene(loader.load());
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.showAndWait();

        } catch (Exception ex) {
            SecureLogger.error("Failed to open license update dialog", ex);
        }
    }

    @FXML
    public void initialize() {
        updateLicenseLabel.setVisible(false);
        masterPasswordField.setLoginMode(true);
        masterPasswordField.setDialogMode(true);
        repository.addObserver(this);
        btnLangIT.setOnAction(e -> changeLanguage(Locale.ITALIAN));
        btnLangEN.setOnAction(e -> changeLanguage(Locale.ENGLISH));
        updateLanguageButtons();
        updateLicenseLabel.setOnMouseClicked(e -> openLicenseUpdateDialog());

        // Real-time strength bar
        strengthBar.setProgress(0);
        strengthLabel.setText("");
        masterPasswordField.setOnPwdValueChanged(this::updateStrengthBar);
    }

    public void showUpdateLicense(boolean show){

        updateLicenseLabel.setVisible(show);
    }

    private void updateStrengthBar() {
        char[] pwd = masterPasswordField.getValue();
        if (pwd == null || pwd.length == 0) {
            strengthBar.setProgress(0);
            strengthLabel.setText("");
            strengthBar.setStyle("");
            return;
        }

        int score = passwordAnalyzer.scorePassword(pwd);
        double progress = score / 100.0;
        strengthBar.setProgress(progress);

        String color;
        String labelKey;
        if (score >= 80) {
            color = "#4CAF50";
            labelKey = "login.strength.strong";
        } else if (score >= 60) {
            color = "#8BC34A";
            labelKey = "login.strength.good";
        } else if (score >= 40) {
            color = "#FFC107";
            labelKey = "login.strength.fair";
        } else if (score >= 20) {
            color = "#FF9800";
            labelKey = "login.strength.weak";
        } else {
            color = "#F44336";
            labelKey = "login.strength.critical";
        }

        // Apply color to the .bar sub-node
        javafx.scene.Node bar = strengthBar.lookup(".bar");
        if (bar != null) {
            bar.setStyle("-fx-background-color: " + color + ";");
        }

        strengthLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + color + ";");
        strengthLabel.setText(I18n.t(labelKey));
    }

    private void updateTextsForLanguage() {
        // Titolo e descrizioni
        updateLoginHelpText();

        // Pulsanti
        openVaultButton.setText(I18n.t("login.open.vault"));
        createVaultButton.setText(I18n.t("login.new.vault"));


    }
    private void updateLanguageButtons() {
        Locale current = I18n.getCurrentLocale();

        updateTextsForLanguage(); // <--- NUOVO

        // Evidenzia la lingua selezionata
        if ("it".equals(current.getLanguage())) {
            btnLangIT.setStyle("-fx-background-color: #3574F0; -fx-text-fill: white;");
            btnLangEN.setStyle("-fx-background-color: #444; -fx-text-fill: white;");

        } else {

            btnLangEN.setStyle("-fx-background-color: #3574F0; -fx-text-fill: white;");
            btnLangIT.setStyle("-fx-background-color: #444; -fx-text-fill: white;");
        }
    }


    private void changeLanguage(Locale locale) {

        MainController.saveLanguagePreferenceStatic(locale);
        I18n.setLocale(locale); // <--- AGGIUNTO

        Platform.runLater(this::updateLanguageButtons);


    }



    private void updateLoginHelpText() {
        loginHelpText.getChildren().clear();
        this.languageLabel.setText(I18n.t("login.lang.label"));
        String line1 = I18n.t("login.help.line1");
        String line2 = I18n.t("login.help.line2");
        String line3 = I18n.t("login.help.line3");
        String line4 = I18n.t("login.help.line4");

        Text t1 = new Text(line1 + "\n");
        t1.setStyle("-fx-fill: #e6e6e6;");

        Text t2 = new Text(line2 + "\n");
        t2.setStyle("-fx-fill: #bbbbbb;");

        Text t3 = new Text(line3 + "\n");
        t3.setStyle("-fx-fill: #bbbbbb;");

        Text t4 = new Text(line4);
        t4.setStyle("-fx-fill: #631b63; -fx-font-weight: bold;");

        loginHelpText.getChildren().addAll(t1, t2, t3, t4);
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
            dialog.initOwner(masterPasswordField.getScene().getWindow());
            dialog.setResizable(false);
            dialog.getScene().getStylesheets().add(
                    Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
            );
            dialog.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
            ));

            PasswordGeneratorController ctrl = loader.getController();
            ctrl.initialize();

            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();


        } catch (Exception e) {
            SecureLogger.error(e.getMessage(),e);
        }
    }



    // Animazione percentuale: mantiene l'ultimo valore mostrato
    private double lastShownProgress = 0;

    private void disableLoginProgress() {
      loginProgressBar.setManaged(false);
        loginProgressLabel.setManaged(false);

        openVaultButton.setDisable(false);
        createVaultButton.setDisable(false);
    }

    /**
     * Visualizza progress bar e resetta stato UI
     */
    private void startLoginProgress(String msg) {
        loginProgressBar.setManaged(true);
        loginProgressLabel.setManaged(true);

        loginProgressBar.setProgress(0);
        loginProgressLabel.setText(msg != null ? msg : "");

        openVaultButton.setDisable(true);
        createVaultButton.setDisable(true);

        lastShownProgress = 0;
    }

    /**
     * Nasconde la progress bar con fade-out
     */
    private void completeLoginProgress() {
      javafx.animation.FadeTransition ft =
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(350),
                        loginProgressBar);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(ev -> {
            loginProgressBar.setVisible(false);
            loginProgressLabel.setVisible(false);
            loginProgressLabel.setText("");
            loginProgressLabel.setManaged(false);
            loginProgressBar.setManaged(false);
        });
        ft.play();

    }


    @FXML
    private void handleOpenVault() {
        char[] password = masterPasswordField.getValue();
        if (password.length < 10) {
            showError(I18n.t("login.error.minlength"));
            return;
        }

        startLoginProgress("");

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("login.open.title"));

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("OneCritto Vault (*.onecritto)", "*.onecritto")
        );

        Stage fcStage = (Stage) rootContainer.getScene().getWindow();
        fcStage.getIcons().add(new javafx.scene.image.Image(
                Objects.requireNonNull(getClass().getResourceAsStream(
                        "/icons/onecritto_white_key_32x32.png"))
        ));
        File file = chooser.showOpenDialog(fcStage);
        if (file == null) {
            disableLoginProgress();
            return;
        }

        Thread worker = getThread(file, password);
        worker.start();
    }

    private Thread getThread(File file, char[] password) {
        Path vaultPath = file.toPath();

        Thread worker = new Thread(() -> {
            try {
                repository.loadVaultV4(vaultPath, password);
                masterPasswordField.wipeAllMemory();

                Platform.runLater(this::completeLoginProgress);

            } catch (Exception e) {
                Platform.runLater(() -> {
                    disableLoginProgress();
                    showError(I18n.t("login.error.open"));
                });
            }
        });

        worker.setDaemon(true);
        return worker;
    }


    /**
     * CREA un nuovo vault V2
     */
    @FXML
    private void handleCreateVault() throws Exception {
        char[] pwd = masterPasswordField.getValue();
        if (pwd.length < 10) {
            showError(I18n.t("login.error.minlength"));
            return;
        }

        int score = passwordAnalyzer.scorePassword(pwd);
        if (score < 60) {
            showError(I18n.t("login.error.weak_password"));
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("login.create.title"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("OneCritto Vault (*.onecritto)", "*.onecritto")
        );

        Stage fcStage = (Stage) rootContainer.getScene().getWindow();
        fcStage.getIcons().add(new javafx.scene.image.Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
        ));
        File file = chooser.showSaveDialog(fcStage);
        if (file == null) {
            disableLoginProgress();
            return;
        }

        Path vaultPath = file.toPath();
        startLoginProgress(I18n.t("login.progress.create"));

        Thread worker = getThread(vaultPath, pwd);
        worker.start();
    }

    private Thread getThread(Path vaultPath, char[] pwd) {
        Thread worker = new Thread(() -> {
            try {
                Vault vault = new Vault(new ArrayList<>(), new ArrayList<>());
                VaultRepository.VAULT_CONTEXT.setVault(vault);
                VaultRepository.VAULT_CONTEXT.setVaultPath(vaultPath);
                VaultRepository.VAULT_CONTEXT.setMasterPassword(pwd);

                repository.saveVaultV4(null);


                Platform.runLater(() -> {

                    try {
                        completeLoginProgress();
                        openMainWindow();
                    } catch (Exception e) {
                        showError(I18n.t("login.error.open"));
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    disableLoginProgress();
                    showError(I18n.t("login.error.open"));
                });
            }
        });

        worker.setDaemon(true);
        return worker;
    }


    // ------------------------------------------------------------------

    private void showError(String msg) {
        errorLabel.setText(msg);
    }

    private void openMainWindow() throws Exception {

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml")  , I18n.getBundle());
        Parent root = loader.load();
        Scene scene = new Scene(root);



        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/onecritto-theme.css")).toExternalForm()
        );
        Stage stage = (Stage) masterPasswordField.getScene().getWindow();

        stage.getIcons().add(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_256x256.png"))
        ));
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();



// dopo
        MainController controller = loader.getController();
        controller.init();


    }


    @Override
    public void onProgress(double value) {

        Platform.runLater(() -> {
            if (loginProgressBar == null || loginProgressLabel == null)
                return;

            // Assicura visualizzazione
            loginProgressBar.setManaged(true);
            loginProgressLabel.setManaged(true);

            loginProgressBar.setVisible(true);
            loginProgressLabel.setVisible(true);

            // Aggiorna progress BAR
            loginProgressBar.setProgress(value);

            // Animazione percentuale fluida
            double target = value;
            double start = lastShownProgress;
            double delta = target - start;

            if (Math.abs(delta) < 0.001) {
                lastShownProgress = target;
                loginProgressLabel.setText(String.format("%.0f%%", target * 100));
            } else {
                javafx.animation.Timeline tl = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(
                                javafx.util.Duration.millis(200),
                                ev -> {
                                    lastShownProgress = target;
                                    loginProgressLabel.setText(String.format("%.0f%%", target * 100));
                                }
                        )
                );

                tl.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
                    double fraction = newVal.toMillis() / tl.getTotalDuration().toMillis();
                    double animated = start + delta * fraction;
                    loginProgressLabel.setText(String.format("%.0f%%", animated * 100));
                });

                tl.setOnFinished(ev -> lastShownProgress = target);
                tl.play();
            }

            // Se abbiamo raggiunto il 100%
            if (value >= 0.9999) {
                completeLoginProgress();

                // Ritarda apertura finestra per permettere al fade di terminare
                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(450));
                pause.setOnFinished(ev -> {
                    try {
                        openMainWindow();
                    } catch (Exception e) {
                        showError(I18n.t("login.error.open"));
                    }
                });
                pause.play();
            }
        });
    }



    @Override
    public void onMessage(String msg) {
        Platform.runLater(() -> {
            if (loginProgressLabel != null) {
                loginProgressLabel.setVisible(true);
                loginProgressLabel.setText(msg);
            }
        });
    }


}
