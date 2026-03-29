package com.onecritto.ui;

import com.onecritto.i18n.I18n;
import com.onecritto.model.SecretEntry;
import com.onecritto.sentinel.BreachChecker;
import com.onecritto.sentinel.BreachChecker.BreachResult;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.text.MessageFormat;
import java.util.List;

/**
 * Controller for the Breach Checker dialog.
 * Scans all vault passwords against HIBP using k-anonymity.
 */
public class BreachCheckerController {

    @FXML private Label summaryLabel;
    @FXML private Label descriptionLabel;
    @FXML private VBox progressBox;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Label currentEntryLabel;
    @FXML private TableView<BreachResult> resultsTable;
    @FXML private TableColumn<BreachResult, String> statusColumn;
    @FXML private TableColumn<BreachResult, String> titleColumn;
    @FXML private TableColumn<BreachResult, String> breachCountColumn;
    @FXML private TableColumn<BreachResult, String> detailColumn;
    @FXML private Button scanButton;

    private final BreachChecker breachChecker = new BreachChecker();
    private final ObservableList<BreachResult> results = FXCollections.observableArrayList();
    private List<SecretEntry> entries;
    private volatile boolean scanning = false;
    private volatile boolean cancelRequested = false;

    public void setEntries(List<SecretEntry> entries) {
        this.entries = entries;
    }

    @FXML
    private void initialize() {
        resultsTable.setItems(results);

        statusColumn.setCellValueFactory(cd -> {
            BreachResult r = cd.getValue();
            String icon = r.error() ? "\u2753" : r.isBreached() ? "\uD83D\uDED1" : "\u2705";
            return new SimpleStringProperty(icon);
        });
        statusColumn.setStyle("-fx-alignment: CENTER; -fx-font-size: 16px;");

        titleColumn.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().entryTitle()));

        breachCountColumn.setCellValueFactory(cd -> {
            BreachResult r = cd.getValue();
            if (r.error()) return new SimpleStringProperty(I18n.t("breach.error"));
            if (r.breachCount() == 0) return new SimpleStringProperty(I18n.t("breach.safe"));
            return new SimpleStringProperty(
                    MessageFormat.format(I18n.t("breach.found"), r.breachCount()));
        });

        detailColumn.setCellValueFactory(cd -> {
            BreachResult r = cd.getValue();
            if (r.error()) return new SimpleStringProperty(I18n.t("breach.detail.error"));
            if (r.breachCount() == 0) return new SimpleStringProperty(I18n.t("breach.detail.safe"));
            return new SimpleStringProperty(I18n.t("breach.detail.found"));
        });

        // Color rows based on breach status
        resultsTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(BreachResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else if (item.isBreached()) {
                    setStyle("-fx-background-color: #e74c3c22;");
                } else if (item.error()) {
                    setStyle("-fx-background-color: #f1c40f11;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    @FXML
    private void handleScan() {
        if (entries == null || entries.isEmpty()) return;

        if (scanning) {
            cancelRequested = true;
            return;
        }

        scanning = true;
        cancelRequested = false;
        results.clear();
        scanButton.setText(I18n.t("breach.btn.cancel"));
        progressBox.setVisible(true);
        progressBox.setManaged(true);
        summaryLabel.setText("");

        List<SecretEntry> toCheck = entries.stream()
                .filter(e -> e.getPassword() != null && e.getPassword().length > 0)
                .toList();

        int total = toCheck.size();

        Thread scanThread = new Thread(() -> {
            int breached = 0;
            int errors = 0;

            for (int i = 0; i < toCheck.size(); i++) {
                if (cancelRequested) break;

                SecretEntry entry = toCheck.get(i);
                final int idx = i;

                Platform.runLater(() -> {
                    progressBar.setProgress((double) idx / total);
                    progressLabel.setText((idx + 1) + "/" + total);
                    currentEntryLabel.setText(entry.getTitle());
                });

                int count = breachChecker.check(entry.getPassword());
                boolean error = (count < 0);
                if (error) errors++;
                if (count > 0) breached++;

                BreachResult result = new BreachResult(
                        entry.getId(), entry.getTitle(),
                        Math.max(count, 0), error);

                Platform.runLater(() -> results.add(result));

                // Rate limiting: ~100ms between requests to be respectful
                if (i < toCheck.size() - 1) {
                    try { Thread.sleep(100); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            final int fBreached = breached;
            final int fErrors = errors;
            final boolean cancelled = cancelRequested;

            Platform.runLater(() -> {
                progressBox.setVisible(false);
                progressBox.setManaged(false);
                scanButton.setText(I18n.t("breach.btn.scan"));
                scanning = false;

                if (cancelled) {
                    summaryLabel.setText(I18n.t("breach.cancelled"));
                    summaryLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e67e22;");
                } else if (fBreached == 0 && fErrors == 0) {
                    summaryLabel.setText(I18n.t("breach.all_safe"));
                    summaryLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2ecc71;");
                } else if (fBreached > 0) {
                    summaryLabel.setText(MessageFormat.format(I18n.t("breach.summary_found"), fBreached));
                    summaryLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
                } else {
                    summaryLabel.setText(MessageFormat.format(I18n.t("breach.summary_errors"), fErrors));
                    summaryLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e67e22;");
                }

                // Sort: breached first, then errors, then safe
                results.sort((a, b) -> {
                    int aPriority = a.isBreached() ? 0 : a.error() ? 1 : 2;
                    int bPriority = b.isBreached() ? 0 : b.error() ? 1 : 2;
                    if (aPriority != bPriority) return Integer.compare(aPriority, bPriority);
                    return Integer.compare(b.breachCount(), a.breachCount());
                });
            });
        }, "breach-checker");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    @FXML
    private void handleClose() {
        cancelRequested = true;
        ((Stage) resultsTable.getScene().getWindow()).close();
    }
}
