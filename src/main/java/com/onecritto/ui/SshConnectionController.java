package com.onecritto.ui;

import com.onecritto.i18n.I18n;
import com.onecritto.model.SecretFile;
import com.onecritto.model.SshConnection;
import com.onecritto.persistence.VaultRepository;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

import static com.onecritto.util.UIUtils.showError;

public class SshConnectionController {

    @FXML private TextField nameField;
    @FXML private TextField hostField;
    @FXML private TextField usernameField;
    @FXML private TextField portField;
    @FXML private ComboBox<SecretFile> keyFileCombo;
    @FXML private Label commandPreview;

    @Getter private boolean saved = false;
    @Getter private SshConnection connection;

    @Setter private MainController mainController;

    @FXML
    public void initialize() {
        // Populate key file combo with vault files
        List<SecretFile> vaultFiles = VaultRepository.VAULT_CONTEXT.getVault().getFiles();
        keyFileCombo.getItems().addAll(vaultFiles);

        keyFileCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(SecretFile sf) {
                return sf == null ? "" : sf.getName();
            }

            @Override
            public SecretFile fromString(String s) {
                return null;
            }
        });

        // Live preview of the SSH command
        nameField.textProperty().addListener((o, ov, nv) -> updatePreview());
        hostField.textProperty().addListener((o, ov, nv) -> updatePreview());
        usernameField.textProperty().addListener((o, ov, nv) -> updatePreview());
        portField.textProperty().addListener((o, ov, nv) -> updatePreview());
        keyFileCombo.valueProperty().addListener((o, ov, nv) -> updatePreview());

        // Port: only allow digits
        portField.textProperty().addListener((o, ov, nv) -> {
            if (nv != null && !nv.matches("\\d*")) {
                portField.setText(nv.replaceAll("[^\\d]", ""));
            }
        });
    }

    private void updatePreview() {
        String user = usernameField.getText();
        String host = hostField.getText();
        String port = portField.getText();
        SecretFile key = keyFileCombo.getValue();

        StringBuilder cmd = new StringBuilder("ssh");
        if (key != null) {
            cmd.append(" -i <").append(key.getName()).append(">");
        }
        if (port != null && !port.isBlank() && !"22".equals(port.trim())) {
            cmd.append(" -p ").append(port.trim());
        }
        if (user != null && !user.isBlank()) {
            cmd.append(" ").append(user.trim());
            if (host != null && !host.isBlank()) {
                cmd.append("@").append(host.trim());
            }
        } else if (host != null && !host.isBlank()) {
            cmd.append(" ").append(host.trim());
        }
        commandPreview.setText(cmd.toString());
    }

    public void setConnection(SshConnection conn) {
        this.connection = conn;
        nameField.setText(conn.getName());
        hostField.setText(conn.getHost());
        usernameField.setText(conn.getUsername());
        portField.setText(String.valueOf(conn.getPort()));

        // Select the matching key file
        if (conn.getKeyFileId() != null) {
            for (SecretFile sf : keyFileCombo.getItems()) {
                if (sf.getId().equals(conn.getKeyFileId())) {
                    keyFileCombo.setValue(sf);
                    break;
                }
            }
        }
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText();
        String host = hostField.getText();
        String user = usernameField.getText();
        String portText = portField.getText();

        if (name == null || name.isBlank()) {
            showError(I18n.t("ssh.error.name.required"));
            return;
        }
        if (host == null || host.isBlank()) {
            showError(I18n.t("ssh.error.host.required"));
            return;
        }

        int port = 22;
        if (portText != null && !portText.isBlank()) {
            try {
                port = Integer.parseInt(portText.trim());
                if (port < 1 || port > 65535) {
                    showError(I18n.t("ssh.error.port.invalid"));
                    return;
                }
            } catch (NumberFormatException e) {
                showError(I18n.t("ssh.error.port.invalid"));
                return;
            }
        }

        SecretFile selectedKey = keyFileCombo.getValue();

        if (connection == null) {
            connection = new SshConnection(
                    UUID.randomUUID().toString(),
                    name.trim(),
                    host.trim(),
                    port,
                    user != null ? user.trim() : "",
                    selectedKey != null ? selectedKey.getId() : null,
                    selectedKey != null ? selectedKey.getName() : null
            );
        } else {
            connection.setName(name.trim());
            connection.setHost(host.trim());
            connection.setPort(port);
            connection.setUsername(user != null ? user.trim() : "");
            connection.setKeyFileId(selectedKey != null ? selectedKey.getId() : null);
            connection.setKeyFileName(selectedKey != null ? selectedKey.getName() : null);
        }

        saved = true;
        ((Stage) nameField.getScene().getWindow()).close();
    }

    @FXML
    private void handleCancel() {
        saved = false;
        ((Stage) nameField.getScene().getWindow()).close();
    }
}
