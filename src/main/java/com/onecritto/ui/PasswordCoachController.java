package com.onecritto.ui;

import com.onecritto.i18n.I18n;
import com.onecritto.sentinel.PasswordCoach;
import com.onecritto.sentinel.PasswordCoach.CoachTip;
import com.onecritto.sentinel.PasswordCoach.Severity;
import com.onecritto.sentinel.model.PasswordScore;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * Controller for the Password Coach dialog.
 * Displays coaching tips for a selected vault entry.
 */
public class PasswordCoachController {

    @FXML private Label scoreLabel;
    @FXML private Label entryTitleLabel;
    @FXML private ProgressBar strengthBar;
    @FXML private Label strengthLabel;
    @FXML private VBox tipsContainer;

    private final PasswordCoach coach = new PasswordCoach();

    /**
     * Populate the dialog with coaching tips for the given entry.
     */
    public void populate(PasswordScore score, int passwordAgeDays) {
        // Entry title
        entryTitleLabel.setText(score.getEntryTitle() != null ? score.getEntryTitle() : "");

        // Score display
        int s = score.getScore();
        String color = colorForScore(s);
        scoreLabel.setText(s + "/100");
        scoreLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        // Strength bar
        strengthBar.setProgress(s / 100.0);
        strengthBar.setStyle("-fx-accent: " + color + ";");
        strengthLabel.setText(I18n.t("coach.strength." + score.getRiskLevel().name().toLowerCase()));
        strengthLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");

        // Generate tips
        List<CoachTip> tips = coach.coach(score, passwordAgeDays);

        tipsContainer.getChildren().clear();
        for (CoachTip tip : tips) {
            tipsContainer.getChildren().add(buildTipRow(tip));
        }
    }

    private HBox buildTipRow(CoachTip tip) {
        // Icon
        Label iconLabel = new Label(tip.icon());
        iconLabel.setStyle("-fx-font-size: 18px; -fx-min-width: 28;");
        iconLabel.setAlignment(Pos.CENTER);

        // Severity indicator bar
        Label severityDot = new Label();
        severityDot.setMinWidth(4);
        severityDot.setMaxWidth(4);
        severityDot.setMinHeight(20);

        String dotColor = switch (tip.severity()) {
            case CRITICAL -> "#e74c3c";
            case WARNING  -> "#e67e22";
            case INFO     -> "#3574F0";
            case OK       -> "#2ecc71";
        };
        severityDot.setStyle("-fx-background-color: " + dotColor + "; -fx-background-radius: 2;");

        // Message
        Label msgLabel = new Label(tip.message());
        msgLabel.setWrapText(true);
        msgLabel.setStyle("-fx-font-size: 13px;");
        HBox.setHgrow(msgLabel, Priority.ALWAYS);

        HBox row = new HBox(8, iconLabel, severityDot, msgLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 6 10 6 6; -fx-background-color: " + dotColor + "11; -fx-background-radius: 6;");

        return row;
    }

    private String colorForScore(int score) {
        if (score >= 80) return "#2ecc71";
        if (score >= 60) return "#27ae60";
        if (score >= 40) return "#f1c40f";
        if (score >= 20) return "#e67e22";
        return "#e74c3c";
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) tipsContainer.getScene().getWindow();
        stage.close();
    }
}
