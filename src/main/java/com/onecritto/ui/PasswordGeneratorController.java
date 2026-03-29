package com.onecritto.ui;

import com.onecritto.i18n.I18n;
import com.onecritto.sentinel.PasswordAnalyzer;
import com.onecritto.sentinel.model.PasswordScore;
import com.onecritto.ui.secure.SecureTextField;
import com.onecritto.ui.secure.SecureTextFieldSkin;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.ResourceBundle;

public class PasswordGeneratorController {

    @FXML public Tab pwdMnemonic;
    @FXML public Tab pwdStrong;
    @FXML public CheckBox mnemonicIncludeSymbols;
    @FXML public TabPane tabPane;
    // TAB 1 (Password Forte)
    @FXML private SecureTextField generatedPasswordField;

    @FXML private Slider lengthSlider;
    @FXML private Label lengthLabel;
    @FXML private CheckBox includeUpper;
    @FXML private CheckBox includeLower;
    @FXML private CheckBox includeDigits;
    @FXML private CheckBox includeSymbols;
    @FXML private CheckBox avoidAmbiguous;
    @FXML private ProgressBar strengthBar;
    @FXML private Label strengthLabel;
    @FXML private Button generateButton;

    // TAB 2 (Password Mnemonica)
    @FXML private SecureTextField mnemonicOutput;
    @FXML private Slider mnemonicSyllablesSlider;
    @FXML private Label mnemonicSyllablesLabel;
    @FXML private CheckBox mnemonicIncludeDigits;
    @FXML private ProgressBar mnemonicStrengthBar;
    @FXML private Label mnemonicStrengthLabel;
    @FXML private Button generateMnemonicButton;

    private final SecureRandom random = new SecureRandom();
    private static final PasswordAnalyzer ANALYZER = new PasswordAnalyzer();



    private void makeDialogTransparent() {
        generatedPasswordField.setDialogMode(true);
        mnemonicOutput.setDialogMode(true);
        tabPane.setBackground(Background.EMPTY);
    }






    @FXML
    public void initialize() {
        // oppure getDialogPane() se usi Dialog<>()

        // Rende trasparente il DIALOG e lo stage che lo contiene
        makeDialogTransparent();

        // Slider lunghezza
        lengthLabel.setText(String.valueOf((int) lengthSlider.getValue()));
        lengthSlider.valueProperty().addListener((obs, o, n) ->
                lengthLabel.setText(String.valueOf(n.intValue()))
        );

        // Slider sillabe mnemoniche
        mnemonicSyllablesSlider.valueProperty().addListener((obs, o, n) ->
                mnemonicSyllablesLabel.setText(String.valueOf(n.intValue()))
        );

        // Generazione iniziale
        handleGenerate();
        handleGenerateMnemonic();

        generateButton.setOnAction(e -> handleGenerate());
        generateMnemonicButton.setOnAction(e -> handleGenerateMnemonic());



        MainController.fixTabBorder(pwdMnemonic);
        MainController.fixTabBorder(pwdStrong);

         Platform.runLater(() -> {

             if(mainController == null)return;

            Scene scene = generateButton.getScene();
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

    // ========= PASSWORD FORTE ========= //
    @FXML
    private void handleGenerate() {
        int length = (int) lengthSlider.getValue();

        String upper   = includeUpper.isSelected()   ? "ABCDEFGHIJKLMNOPQRSTUVWXYZ" : "";
        String lower   = includeLower.isSelected()   ? "abcdefghijklmnopqrstuvwxyz" : "";
        String digits  = includeDigits.isSelected()  ? "0123456789" : "";
        String symbols = includeSymbols.isSelected() ? "!@#$%^&*(){}[]?/+=-" : "";

        String all = upper + lower + digits + symbols;

        if (avoidAmbiguous.isSelected()) {
            all = all.replaceAll("[O0Il1]", "");
        }

        if (all.isEmpty()) {
            generatedPasswordField.setValue(I18n.t("pwdgen.error.nocharset").toCharArray());

            generatedPasswordField.setReveal(true);
            generatedPasswordField.updateUiShadow();
            return;
        }

        generatedPasswordField.setOnPwdValueChanged(() -> {
            updateStrengthBar(generatedPasswordField.getValue(), strengthBar, strengthLabel, true);
        });

        char[] pwd = new char[length];
        for (int i = 0; i < length; i++) {
            pwd[i] = all.charAt(random.nextInt(all.length()));
        }

        generatedPasswordField.setValue(pwd);
        generatedPasswordField.setReveal(true);

        // QUI prima c’era solo updateUiShadow() / runLater

        // Forza lo skin a ridisegnare il testo
        SecureTextFieldSkin skin = (SecureTextFieldSkin) generatedPasswordField.getSkin();
        if (skin != null) {
            skin.updateText();
        }

        // aggiorna subito la barra di forza
        updateStrengthBar(generatedPasswordField.getValue(), strengthBar, strengthLabel, true);

        // pulizia del buffer temporaneo
        java.util.Arrays.fill(pwd, '\0');
    }

    @FXML
    private void handleCopy() {

    }

    // ========= PASSWORD MNEMONICA ========= //
    @FXML
    private void handleGenerateMnemonic() {

        int syllables = (int) mnemonicSyllablesSlider.getValue();

        final String[] consonants = {
                "b","c","d","f","g","h","k","l","m","n",
                "p","r","s","t","v","z",
                "br","cr","dr","fr","gr","pr","tr","vr",
                "st","sk","sp","str","gl","cl","pl"
        };

        final String[] vowels = {"a","e","i","o","u"};
        final int[] patterns = {0,1,3};

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < syllables; i++) {
            int p = patterns[random.nextInt(patterns.length)];

            switch (p) {
                case 0:
                    sb.append(consonants[random.nextInt(consonants.length)]);
                    sb.append(vowels[random.nextInt(vowels.length)]);
                    break;
                case 1:
                    sb.append(consonants[random.nextInt(consonants.length)]);
                    sb.append(vowels[random.nextInt(vowels.length)]);
                    sb.append(consonants[random.nextInt(15)]);
                    break;
                case 3:
                    sb.append(consonants[random.nextInt(consonants.length)]);
                    sb.append(vowels[random.nextInt(vowels.length)]);
                    sb.append(vowels[random.nextInt(vowels.length)]);
                    break;
            }
        }

        if (mnemonicIncludeDigits.isSelected()) {
            sb.append(random.nextInt(10));
        }

        if (mnemonicIncludeSymbols.isSelected()) {
            final String symbols = "!@#$%&*?+-_";
            sb.append(symbols.charAt(random.nextInt(symbols.length())));
        }

        // ---- SCRITTURA SICURA ----
        char[] pwd = sb.toString().toCharArray();


        mnemonicOutput.setValue(pwd);
        mnemonicOutput.setReveal(true);

     

        // Forza lo skin a ridisegnare il testo
        SecureTextFieldSkin skin = (SecureTextFieldSkin) mnemonicOutput.getSkin();
        if (skin != null) {
            skin.updateText();
        }

        mnemonicOutput.setOnPwdValueChanged(() -> {
            updateStrengthBar(mnemonicOutput.getValue(), mnemonicStrengthBar, mnemonicStrengthLabel, true);
        });
        // aggiorna forza password
        updateStrengthBar(
                mnemonicOutput.getValue(),
                mnemonicStrengthBar,
                mnemonicStrengthLabel,
                true
        );

        java.util.Arrays.fill(pwd, '\0');
    }









    // ========= VALUTAZIONE FORZA (delegata a Sentinel PasswordAnalyzer) ========= //
    public static int updateStrengthBar(char[] pwd, ProgressBar bar, Label label, boolean printPwdStrength) {

        if (pwd == null || pwd.length == 0) {
            bar.setProgress(0);
            bar.setStyle("");
            label.setText(I18n.t("pwdgen.strength.none"));
            return 0;
        }

        int score = ANALYZER.scorePassword(pwd);
        bar.setProgress(score / 100.0);

        // Colore coerente con il badge Sentinel
        String barColor;
        if (score >= 80)      barColor = "#2ecc71";
        else if (score >= 60) barColor = "#27ae60";
        else if (score >= 40) barColor = "#f1c40f";
        else if (score >= 20) barColor = "#e67e22";
        else                  barColor = "#e74c3c";
        bar.setStyle("-fx-accent: " + barColor + ";");

        PasswordScore.RiskLevel level = PasswordScore.levelFromScore(score);
        String text;
        switch (level) {
            case CRITICAL: text = I18n.t("pwdgen.strength.critical"); break;
            case WEAK:     text = I18n.t("pwdgen.strength.weak");     break;
            case FAIR:     text = I18n.t("pwdgen.strength.fair");     break;
            case GOOD:     text = I18n.t("pwdgen.strength.good");     break;
            case STRONG:   text = I18n.t("pwdgen.strength.strong");   break;
            default:       text = "";                                 break;
        }

        if (printPwdStrength)
            label.setText(I18n.t("pwdgen.strength.prefix") + text);
        else
            label.setText(text);

        return score;
    }


    public void handleClose(ActionEvent actionEvent) {
        Stage stage = (Stage) generateMnemonicButton.getScene().getWindow();
        stage.close();
    }
}
