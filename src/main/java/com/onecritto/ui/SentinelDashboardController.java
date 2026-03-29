package com.onecritto.ui;

import com.onecritto.i18n.I18n;
import com.onecritto.sentinel.RotationAdvisor;
import com.onecritto.sentinel.model.VaultHealthReport;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.Setter;

import java.util.List;

/**
 * Controller for the Sentinel Security Dashboard.
 */
public class SentinelDashboardController {

    @FXML private Label healthScoreLabel;
    @FXML private ProgressBar healthBar;
    @FXML private Label healthDescription;

    @FXML private Label criticalLabel;
    @FXML private Label weakLabel;
    @FXML private Label fairLabel;
    @FXML private Label goodLabel;
    @FXML private Label strongLabel;
    @FXML private Label duplicateLabel;

    @FXML private TableView<RotationAdvisor.RotationItem> rotationTable;
    @FXML private TableColumn<RotationAdvisor.RotationItem, Integer> rotPriorityCol;
    @FXML private TableColumn<RotationAdvisor.RotationItem, String> rotTitleCol;
    @FXML private TableColumn<RotationAdvisor.RotationItem, String> rotReasonCol;

    @Setter private MainController mainController;

    public void populate(VaultHealthReport report, List<RotationAdvisor.RotationItem> rotationPlan) {
        // --- Health score ---
        int score = report.getHealthScore();
        healthScoreLabel.setText(score + "/100");
        healthBar.setProgress(score / 100.0);

        // Color the health bar based on score
        String barColor;
        String description;
        if (score >= 80) {
            barColor = "#2ecc71";
            description = I18n.t("sentinel.health.strong");
        } else if (score >= 60) {
            barColor = "#27ae60";
            description = I18n.t("sentinel.health.good");
        } else if (score >= 40) {
            barColor = "#f1c40f";
            description = I18n.t("sentinel.health.fair");
        } else if (score >= 20) {
            barColor = "#e67e22";
            description = I18n.t("sentinel.health.weak");
        } else {
            barColor = "#e74c3c";
            description = I18n.t("sentinel.health.critical");
        }

        healthBar.setStyle("-fx-accent: " + barColor + ";");
        healthScoreLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + barColor + ";");
        healthDescription.setText(description);

        // --- Summary counters ---
        criticalLabel.setText(String.valueOf(report.getCriticalCount()));
        weakLabel.setText(String.valueOf(report.getWeakCount()));
        fairLabel.setText(String.valueOf(report.getFairCount()));
        goodLabel.setText(String.valueOf(report.getGoodCount()));
        strongLabel.setText(String.valueOf(report.getStrongCount()));
        duplicateLabel.setText(String.valueOf(report.getDuplicateCount()));

        // --- Rotation plan table ---
        rotPriorityCol.setCellValueFactory(data ->
                new ReadOnlyObjectWrapper<>(data.getValue().priority()));
        rotTitleCol.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().entryTitle()));
        rotReasonCol.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().reason()));

        // Priority cell with color coding
        rotPriorityCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(String.valueOf(item));
                String color = switch (item) {
                    case 1 -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                    case 2 -> "-fx-text-fill: #e67e22; -fx-font-weight: bold;";
                    case 3 -> "-fx-text-fill: #9b59b6;";
                    case 4 -> "-fx-text-fill: #f1c40f;";
                    default -> "-fx-text-fill: #888;";
                };
                setStyle(color);
            }
        });

        rotationTable.setItems(FXCollections.observableArrayList(rotationPlan));
        rotationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        rotationTable.setSelectionModel(null);
        rotationTable.setFocusTraversable(false);
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) healthBar.getScene().getWindow();
        stage.close();
    }
}
