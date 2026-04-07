package com.onecritto.ui;

import com.onecritto.i18n.I18n;
import com.onecritto.importer.*;
import com.onecritto.model.SecretEntry;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;

import java.text.MessageFormat;
import java.util.*;

public class ImportPreviewController {

    @FXML private Label headerLabel;
    @FXML private Label detectedSourceLabel;
    @FXML private Label entryCountLabel;
    @FXML private VBox mappingContainer;
    @FXML private CheckBox selectAllCheckBox;
    @FXML private TableView<SecretEntry> previewTable;
    @FXML private TableColumn<SecretEntry, Boolean> previewSelectCol;
    @FXML private TableColumn<SecretEntry, String> previewTitleCol;
    @FXML private TableColumn<SecretEntry, String> previewUsernameCol;
    @FXML private TableColumn<SecretEntry, String> previewPasswordCol;
    @FXML private TableColumn<SecretEntry, String> previewUrlCol;
    @FXML private TableColumn<SecretEntry, String> previewCategoryCol;
    @FXML private Button importButton;
    @FXML private Label statusLabel;

    @Getter private boolean confirmed = false;
    @Getter private List<SecretEntry> importedEntries = List.of();

    private ImportOrchestrator.ImportPreviewData previewData;
    private final ImportOrchestrator orchestrator = new ImportOrchestrator();
    private final Map<String, ComboBox<String>> mappingCombos = new LinkedHashMap<>();
    private final Map<SecretEntry, BooleanProperty> selectionMap = new IdentityHashMap<>();
    private boolean updatingSelectAll = false;

    @FXML
    private void initialize() {
        // Select All checkbox
        selectAllCheckBox.setOnAction(e -> {
            if (updatingSelectAll) return;
            boolean sel = selectAllCheckBox.isSelected();
            for (BooleanProperty bp : selectionMap.values()) {
                bp.set(sel);
            }
            updateStatus();
        });
    }

    public void setPreviewData(ImportOrchestrator.ImportPreviewData data) {
        this.previewData = data;
        populateUI();
    }

    private void populateUI() {
        MappingSchema schema = previewData.schema();
        ParseResult parseResult = previewData.parseResult();

        // Detected source
        String source = schema.getDetectedSource();
        detectedSourceLabel.setText(source != null ? source : I18n.t("import.source.unknown"));

        // Entry count
        entryCountLabel.setText(MessageFormat.format(I18n.t("import.entry.count"), parseResult.rows().size()));

        // Mapping UI
        buildMappingUI(schema, parseResult.headers());

        // Preview table
        refreshPreview();
    }

    private void buildMappingUI(MappingSchema schema, List<String> headers) {
        mappingContainer.getChildren().clear();
        mappingCombos.clear();

        List<String> targetOptions = new ArrayList<>();
        for (TargetField tf : TargetField.values()) {
            targetOptions.add(tf.name());
        }

        for (String header : headers) {
            FieldMapping mapping = schema.getMappings().get(header);
            if (mapping == null) continue;

            HBox row = new HBox(10);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            // Colonna sorgente
            Label sourceLabel = new Label(header);
            sourceLabel.setPrefWidth(160);
            sourceLabel.setMinWidth(120);
            sourceLabel.setStyle("-fx-font-weight: bold;");

            // Freccia
            Label arrow = new Label("→");
            arrow.setStyle("-fx-font-size: 14px;");

            // ComboBox target
            ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(targetOptions));
            combo.setValue(mapping.targetField().name());
            combo.setPrefWidth(140);
            combo.setOnAction(e -> refreshPreview());
            mappingCombos.put(header, combo);

            // Badge confidenza
            Label badge = createConfidenceBadge(mapping.confidence());

            HBox.setHgrow(combo, Priority.NEVER);
            row.getChildren().addAll(sourceLabel, arrow, combo, badge);
            mappingContainer.getChildren().add(row);
        }
    }

    private Label createConfidenceBadge(Confidence confidence) {
        Label badge = new Label();
        switch (confidence) {
            case HIGH:
                badge.setText("✅ auto");
                badge.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 11px;");
                break;
            case MEDIUM:
                badge.setText("🔶 likely");
                badge.setStyle("-fx-text-fill: #ff9800; -fx-font-size: 11px;");
                break;
            case LOW:
                badge.setText("⚠ guess");
                badge.setStyle("-fx-text-fill: #f44336; -fx-font-size: 11px;");
                break;
            case NONE:
                badge.setText("⏭ skip");
                badge.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
                break;
        }
        return badge;
    }

    private void refreshPreview() {
        // Aggiorna lo schema con le selezioni correnti dell'utente
        MappingSchema schema = previewData.schema();
        for (Map.Entry<String, ComboBox<String>> entry : mappingCombos.entrySet()) {
            String header = entry.getKey();
            String selected = entry.getValue().getValue();
            if (selected != null) {
                schema.setMapping(header, TargetField.valueOf(selected));
            }
        }

        // Rigenera le entry
        List<SecretEntry> entries = orchestrator.executeImport(previewData);

        // Selection map
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

        // Checkbox column
        previewSelectCol.setCellValueFactory(cd -> selectionMap.get(cd.getValue()));
        previewSelectCol.setCellFactory(col -> new CheckBoxTableCell<>());

        // Popolamento tabella preview
        previewTable.setEditable(true);
        previewTitleCol.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(cd.getValue().getTitle()));
        previewUsernameCol.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(maskChars(cd.getValue().getUsername())));
        previewPasswordCol.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(cd.getValue().getPassword() != null
                        && cd.getValue().getPassword().length > 0 ? "••••••••" : ""));
        previewUrlCol.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(cd.getValue().getUrl()));
        previewCategoryCol.setCellValueFactory(cd ->
                new ReadOnlyStringWrapper(cd.getValue().getCategory()));

        previewTable.setItems(FXCollections.observableArrayList(entries));

        // Sync selectAll
        updatingSelectAll = true;
        selectAllCheckBox.setSelected(true);
        updatingSelectAll = false;

        updateStatus();
    }

    private String maskChars(char[] chars) {
        if (chars == null || chars.length == 0) return "";
        if (chars.length <= 3) return "•••";
        return new String(chars, 0, 2) + "•".repeat(Math.min(chars.length - 2, 6));
    }

    private void updateStatus() {
        long selected = selectionMap.values().stream().filter(BooleanProperty::get).count();
        long total = selectionMap.size();
        statusLabel.setText(MessageFormat.format(I18n.t("import.preview.status"), selected + "/" + total));
    }

    @FXML
    private void handleImport() {
        MappingSchema schema = previewData.schema();
        for (Map.Entry<String, ComboBox<String>> entry : mappingCombos.entrySet()) {
            String selected = entry.getValue().getValue();
            if (selected != null) {
                schema.setMapping(entry.getKey(), TargetField.valueOf(selected));
            }
        }

        // Collect selected entries from current table (same instances as selectionMap keys)
        importedEntries = new ArrayList<>();
        for (SecretEntry e : previewTable.getItems()) {
            BooleanProperty bp = selectionMap.get(e);
            if (bp != null && bp.get()) {
                importedEntries.add(e);
            }
        }
        confirmed = !importedEntries.isEmpty();
        closeWindow();
    }

    @FXML
    private void handleCancel() {
        confirmed = false;
        closeWindow();
    }

    private void closeWindow() {
        importButton.getScene().getWindow().hide();
    }
}
