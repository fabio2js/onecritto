package com.onecritto.ui;

import com.onecritto.i18n.I18n;
import com.onecritto.licensing.HardwareIdUtil;
import com.onecritto.util.SecureLogger;
import com.onecritto.util.UIUtils;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TrialRequestController {

    @FXML private TextField hwidField;
    @FXML private Button copyButton;
    @FXML private Button closeButton;
    @FXML private Button importButton;
    @FXML private Label licensePathLabel; // <-- nuova label

    @FXML
    private void initialize() {
        String hwid = HardwareIdUtil.getHardwareId();
        hwidField.setText(hwid);
        writeHwidFile(hwid);

        // Mostra la cartella destinazione licenza
        Path path = getLicenseFilePath();
        licensePathLabel.setText(
                I18n.t("trial.license.path") + ": " + path.getParent().toAbsolutePath()
        );

        copyButton.setOnAction(e -> copyHWID());
        closeButton.setOnAction(e -> closeWindow());
        importButton.setOnAction(e -> importLicense());
    }

    private void importLicense() {
        Stage stage = (Stage) importButton.getScene().getWindow();

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("trial.import.license"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("OneCritto License (*.lic)", "*.lic")
        );

         var file = chooser.showOpenDialog(stage);
        if (file == null) return;

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            Path target = getLicenseFilePath();
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);

            SecureLogger.info("License imported to: " + target.toAbsolutePath());
            Alert alert = new Alert(Alert.AlertType.INFORMATION);

            alert.setTitle(I18n.t("trial.import.success"));
            alert.setHeaderText(I18n.t("trial.import.success"));
            alert.setContentText(I18n.t("trial.import.restart"));
            alert.initOwner(stage);
            alert.getButtonTypes().setAll(ButtonType.OK);
            alert.showAndWait();


        } catch (Exception ex) {
            SecureLogger.error("License import failed", ex);
            UIUtils.showToast(importButton, I18n.t("trial.import.error"));
        }
    }

    private static Path getLicenseFilePath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path baseDir;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                baseDir = Paths.get(appData, "OneCritto");
            } else {
                baseDir = Paths.get(System.getProperty("user.home"), ".onecritto");
            }
        } else if (os.contains("mac")) {
            baseDir = Paths.get(System.getProperty("user.home"),
                    "Library", "Application Support", "OneCritto");
        } else {
            baseDir = Paths.get(System.getProperty("user.home"), ".config", "onecritto");
        }
        return baseDir.resolve("onecritto-license.lic");
    }

    private void writeHwidFile(String hwid) {
        try {
            Files.writeString(Path.of("HWID.txt"), hwid + System.lineSeparator());
        } catch (IOException ignored) {}
    }

    private void copyHWID() {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(hwidField.getText());
        Clipboard.getSystemClipboard().setContent(cc);
        UIUtils.showToast(hwidField, I18n.t("trial.hwid.copied"));
    }

    private void closeWindow() {
        Stage stage = (Stage) hwidField.getScene().getWindow();
        stage.close();
    }
}
