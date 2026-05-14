package com.onecritto.ui;

import com.onecritto.i18n.I18n;
import com.onecritto.model.SecretEntry;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import lombok.Getter;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog di selezione delle entry da esportare in formato CSV.
 * Mostra una tabella con checkbox per la selezione (singola o multipla),
 * e restituisce la lista delle entry confermate.
 */
public class ExportDialogController {

    @FXML private CheckBox selectAllCheckBox;
    @FXML private Label entryCountLabel;
    @FXML private Label statusLabel;
    @FXML private TableView<SecretEntry> entriesTable;
    @FXML private TableColumn<SecretEntry, Boolean> selectCol;
    @FXML private TableColumn<SecretEntry, String> titleCol;
    @FXML private TableColumn<SecretEntry, String> usernameCol;
    @FXML private TableColumn<SecretEntry, String> urlCol;
    @FXML private TableColumn<SecretEntry, String> categoryCol;
    @FXML private Button exportButton;

    @Getter private boolean confirmed = false;
    @Getter private List<SecretEntry> selectedEntries = List.of();

    private final Map<SecretEntry, BooleanProperty> selectionMap = new IdentityHashMap<>();
    private boolean updatingSelectAll = false;

    @FXML
    private void initialize() {
        selectAllCheckBox.setOnAction(e -> {
            if (updatingSelectAll) return;
            boolean sel = selectAllCheckBox.isSelected();
            for (BooleanProperty bp : selectionMap.values()) {
                bp.set(sel);
            }
            updateStatus();
        });
    }

    public void setEntries(List<SecretEntry> entries) {
        selectionMap.clear();
        for (SecretEntry entry : entries) {
            BooleanProperty bp = new SimpleBooleanProperty(true);
            bp.addListener((obs, oldVal, newVal) -> {
                if (!updatingSelectAll) {
                    updatingSelectAll = true;
                    boolean allSelected = selectionMap.values().stream().allMatch(BooleanProperty::get);
                    selectAllCheckBox.setSelected(allSelected);
                    updatingSelectAll = false;
                }
                updateStatus();
            });
            selectionMap.put(entry, bp);
        }

        selectCol.setCellValueFactory(cd -> selectionMap.get(cd.getValue()));
        selectCol.setCellFactory(col -> new CheckBoxTableCell<>());

        titleCol.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(safe(cd.getValue().getTitle())));
        usernameCol.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(maskChars(cd.getValue().getUsername())));
        urlCol.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(safe(cd.getValue().getUrl())));
        categoryCol.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(safe(cd.getValue().getCategory())));

        entriesTable.setEditable(true);
        entriesTable.setItems(FXCollections.observableArrayList(entries));

        entryCountLabel.setText(MessageFormat.format(I18n.t("export.entry.count"), entries.size()));

        updatingSelectAll = true;
        selectAllCheckBox.setSelected(true);
        updatingSelectAll = false;

        updateStatus();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String maskChars(char[] chars) {
        if (chars == null || chars.length == 0) return "";
        if (chars.length <= 3) return "•••";
        return new String(chars, 0, 2) + "•".repeat(Math.min(chars.length - 2, 6));
    }

    private void updateStatus() {
        long selected = selectionMap.values().stream().filter(BooleanProperty::get).count();
        long total = selectionMap.size();
        statusLabel.setText(MessageFormat.format(I18n.t("export.status"), selected + "/" + total));
        exportButton.setDisable(selected == 0);
    }

    @FXML
    private void handleExport() {
        selectedEntries = new ArrayList<>();
        for (SecretEntry e : entriesTable.getItems()) {
            BooleanProperty bp = selectionMap.get(e);
            if (bp != null && bp.get()) {
                selectedEntries.add(e);
            }
        }
        confirmed = !selectedEntries.isEmpty();
        closeWindow();
    }

    @FXML
    private void handleCancel() {
        confirmed = false;
        closeWindow();
    }

    private void closeWindow() {
        exportButton.getScene().getWindow().hide();
    }
}
