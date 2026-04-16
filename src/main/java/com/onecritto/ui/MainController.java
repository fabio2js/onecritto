package com.onecritto.ui;

import com.onecritto.App;
import com.onecritto.importer.ImportOrchestrator;
import com.onecritto.model.SecretEntry;
import com.onecritto.model.SecretFile;
import com.onecritto.model.SshConnection;
import com.onecritto.i18n.I18n;
import com.onecritto.observer.ProgressObserver;
import com.onecritto.persistence.CryptoServiceV3;
import com.onecritto.persistence.CryptoServiceV4;
import com.onecritto.persistence.OneCrittoV3Format;
import com.onecritto.security.TempVaultFiles;
import com.onecritto.sentinel.SentinelEngine;
import com.onecritto.sentinel.model.PasswordScore;
import com.onecritto.sentinel.model.SentinelReport;
import com.onecritto.sentinel.model.VaultHealthReport;
import com.onecritto.ui.secure.SecureMaskTableCell;
import com.onecritto.ui.secure.SecureTextField;
import com.onecritto.ui.secure.SecureTextFieldSkin;
 
import com.onecritto.util.SecureLogger;
import com.onecritto.util.UIUtils;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import com.onecritto.persistence.VaultRepository;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.crypto.SecretKey;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.onecritto.util.UIUtils.*;

public class MainController implements ProgressObserver {

    @FXML public Button homeButton;
    @FXML public TabPane mainTabs;
    @FXML public VBox busyOverlay;
    @FXML public ProgressIndicator busySpinner;
    @FXML public Label busyLabel;
    @FXML public Label labelDimension;
    @FXML public Label labelLicense;
    @FXML public Tab tabPassowrd;
    @FXML public Tab tabFiles;
    @FXML public Button btnAddFile;
    @FXML public Button btnOpenFile;
    @FXML public Button btnExportFile;
    @FXML public Button btnElimina;
    @FXML public Button btnWipeTemp;
    @FXML public Label lblTempFileCount;

    private boolean acquireVaultLock(String message) {
        if (vaultBusy) {
            UIUtils.showToast(fileTable, message);
            return true;
        }

        vaultBusy = true;

        // Ferma i timer di inattività per evitare lockScreen durante operazioni lunghe
        if (inactivityTimer != null) inactivityTimer.stop();
        if (countdownTimer != null) countdownTimer.stop();

        if (locked) {
            return false;
        }
        busyOverlay.setVisible(true);


        return false;
    }

    private void releaseVaultLock() {
        vaultBusy = false;

        if (locked) {
            return;
        }

        busyOverlay.setVisible(false);

        // Riavvia i timer di inattività dopo il completamento dell'operazione
        resetInactivityTimer();
    }






    // Semaforo per evitare operazioni concorrenti sul vault
    private final Object vaultLock = new Object();

    private volatile boolean vaultBusy = false;

    private final VaultRepository repository = new VaultRepository();
    private final SentinelEngine sentinelEngine = new SentinelEngine();
    @FXML  public Label labelPath;
    @FXML public Label sentinelHealthBadge;
    @FXML private TableColumn<SecretEntry, Integer> strengthColumn;


    @FXML private TableView<SecretEntry> tableEntries;
    @FXML private TableColumn<SecretEntry, String> titleColumn;
    @FXML private TableColumn<SecretEntry, String> usernameColumn;
    @FXML private TableColumn<SecretEntry, String> categoryColumn;
    @FXML private TableColumn<SecretEntry, String> noteColumn;
    @FXML private TableColumn<SecretEntry, Number> entryCreatedAtColumn;
    @FXML private TableColumn<SecretEntry, Number> entryPasswordChangedColumn;

    @FXML private TableView<SecretFile> fileTable;
    @FXML private TableColumn<SecretFile, String> fileNameColumn;
    @FXML private TableColumn<SecretFile, Number> fileSizeColumn;
    @FXML private TableColumn<SecretFile, String> fileTypeColumn;

    @FXML private TableColumn<SecretFile, Number> fileAddTimeColumn;
    @FXML private TableColumn<SecretFile, Number> fileLastEditColumn;

    // SSH tab
    @FXML public Tab tabSsh;
    @FXML private TableView<SshConnection> sshTable;
    @FXML private TableColumn<SshConnection, String> sshNameColumn;
    @FXML private TableColumn<SshConnection, String> sshHostColumn;
    @FXML private TableColumn<SshConnection, Number> sshPortColumn;
    @FXML private TableColumn<SshConnection, String> sshUserColumn;
    @FXML private TableColumn<SshConnection, String> sshKeyColumn;
    @FXML private TableColumn<SshConnection, String> sshCommandColumn;

    final private ObservableList<SshConnection> fullSshConnections = FXCollections.observableArrayList();
    private FilteredList<SshConnection> filteredSsh;

    @FXML private Label infoLabel;
    @FXML private Label clipboardCountdownLabel;
    private javafx.animation.Timeline clipboardTimer;


    @FXML private ProgressBar fileProgressBar;
    @FXML private Label fileProgressPercentLabel;
    @FXML private Label fileProgressDetailsLabel;

    @FXML private Label    speedLabel;
    @FXML private Label    etaLabel;

    @FXML private TextField searchTextField;

    final private ObservableList<SecretEntry> fullEntries = FXCollections.observableArrayList();
    final private ObservableList<SecretFile> fullFiles    = FXCollections.observableArrayList();

    private FilteredList<SecretEntry> filteredEntries;

    private FilteredList<SecretFile> filteredFiles;

    private static final long INACTIVITY_TIMEOUT_MS = 3 * 60000L; // 3 minuti
    private javafx.animation.Timeline inactivityTimer;
    private boolean locked = false;

 






    @FXML
    private void initialize() {


        unlockPasswordField.setPrefWidth(220L);
        unlockPasswordField.setMinWidth(220L);
        unlockPasswordField.setMaxWidth(220L);

        unlockPasswordField.setLoginMode(true);

        repository.addObserver(this);
        labelLicense.setStyle("-fx-text-fill: #3574F0; -fx-underline: true; -fx-cursor: hand;");

        labelLicense.setOnMouseClicked(e -> {
            MainApp.getAppHostServices().showDocument("https://onecritto.com");
        });
// ----- SEARCH -----
        searchTextField.textProperty().addListener((obs, oldV, newV) -> {
            applySearchFilter(newV);
        });

// ----- HOME BUTTON -----
        homeButton.setOnAction(e -> {
            searchTextField.clear();
            applySearchFilter("");
        });

        unlockButton.setOnAction(e -> unlockScreen(unlockPasswordField.getValue()));




        Platform.runLater(() -> {
            Scene scene = mainRoot.getScene();
            if (scene == null) return;

            scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.L) {
                    lockScreen();
                    event.consume();
                }
            });
        });
        Platform.runLater(() -> {
            fixTableLayout(tableEntries);
            fixTableLayout(fileTable);
            fixTableLayout(sshTable);
        });

        labelPath.setText(
                MessageFormat.format(I18n.t("main.vault.path"), VaultRepository.VAULT_CONTEXT.getVaultPath().toFile().getAbsolutePath())
        );
        // --- LISTE ORDINABILI & FILTRABILI PER ENTRIES ---
        filteredEntries = new FilteredList<>(fullEntries, p -> true);
        SortedList<SecretEntry> sortedEntries = new SortedList<>(filteredEntries);
        sortedEntries.comparatorProperty().bind(tableEntries.comparatorProperty());
        tableEntries.setItems(sortedEntries);

// --- LISTE ORDINABILI & FILTRABILI PER FILES ---
        filteredFiles = new FilteredList<>(fullFiles, p -> true);
        SortedList<SecretFile> sortedFiles = new SortedList<>(filteredFiles);
        sortedFiles.comparatorProperty().bind(fileTable.comparatorProperty());
        fileTable.setItems(sortedFiles);

// --- LISTE ORDINABILI & FILTRABILI PER SSH ---
        filteredSsh = new FilteredList<>(fullSshConnections, p -> true);
        SortedList<SshConnection> sortedSsh = new SortedList<>(filteredSsh);
        sortedSsh.comparatorProperty().bind(sshTable.comparatorProperty());
        sshTable.setItems(sortedSsh);

        // SSH table columns
        sshNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        sshHostColumn.setCellValueFactory(new PropertyValueFactory<>("host"));
        sshPortColumn.setCellValueFactory(new PropertyValueFactory<>("port"));
        sshUserColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        sshKeyColumn.setCellValueFactory(new PropertyValueFactory<>("keyFileName"));
        sshCommandColumn.setCellValueFactory(cd -> {
            SshConnection c = cd.getValue();
            return new ReadOnlyStringWrapper(buildSshCommand(c, null));
        });

// --- DOPPIO CLICK PER APRIRE MODIFICA SSH ---
        sshTable.setRowFactory(tv -> {
            TableRow<SshConnection> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    sshTable.getSelectionModel().select(row.getIndex());
                    handleSshEdit();
                }
            });
            return row;
        });

// carica i dati dopo avere preparato le liste
        loadTable();
        loadFiles();
        loadSshConnections();

        // Sentinel: run initial analysis
        runSentinelAnalysis();

      
    }

    private boolean charArrayContainsIgnoreCase(char[] arr, String needleLower) {
        if (arr == null || arr.length == 0) return false;
        if (needleLower == null || needleLower.isBlank()) return true;

        String needle = needleLower.toLowerCase();

        // confronto char-by-char in modo sicuro
        for (int i = 0; i <= arr.length - needle.length(); i++) {
            boolean match = true;
            for (int j = 0; j < needle.length(); j++) {

                char c1 = Character.toLowerCase(arr[i + j]);
                char c2 = needle.charAt(j);

                if (c1 != c2) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }

        return false;
    }


    private void fixTableLayout(TableView<?> table) {
        // Forza un layout iniziale
        table.layout();

        // Richiede un secondo layout PASS dopo la creazione dello Stage
        table.requestLayout();

        // Fissa il glitch delle colonne che non si allineano
        for (TableColumn<?, ?> col : table.getColumns()) {
            col.setVisible(false);
            col.setVisible(true);
        }
    }

    @FXML private VBox lockPane;
    @FXML private SecureTextField unlockPasswordField;
    @FXML private Button unlockButton;
    @FXML private Label unlockErrorLabel;
    @FXML private BorderPane appRoot;



    private void unlockScreen(char[] pwd) {
        try {
            // 0) Verifica che il contesto sia inizializzato
            if (VaultRepository.VAULT_CONTEXT.getSalt() == null ||
                    VaultRepository.VAULT_CONTEXT.getEncryptedMetadata() == null ||
                    VaultRepository.VAULT_CONTEXT.getMetadataIv() == null) {

                unlockErrorLabel.setText(I18n.t("unlock.error.not_initialized"));

                return;
            }

            // 1) Ricostruzione chiavi separate da password
            SecretKey[] keys = CryptoServiceV4.deriveKeys(
                    pwd,
                    VaultRepository.VAULT_CONTEXT.getSalt());
            SecretKey key = keys[0];

            VaultRepository.VAULT_CONTEXT.setKeyEnc(key);

            // 2) Decifra metadati e ricostruisce il Vault in memoria
            CryptoServiceV3.decryptMetadata(
                    VaultRepository.VAULT_CONTEXT.getEncryptedMetadata(),
                    key,
                    VaultRepository.VAULT_CONTEXT.getMetadataIv()
            );

            // 3) Password OK → memorizza masterPassword (se ti serve tenerla)
            VaultRepository.VAULT_CONTEXT.setMasterPassword(pwd.clone());

            // 4) Sblocco riuscito
            locked = false;
            lockPane.setDisable(true);
            lockPane.setVisible(false);
            unlockPasswordField.clearValue();
            ((SecureTextFieldSkin)unlockPasswordField.getSkin()).clearUi();

            unlockErrorLabel.setText("");

            appRoot.setEffect(null);
            appRoot.setDisable(false);

            // reset timer inattività
            resetInactivityTimer();

        } catch (Exception ex) {
            SecureLogger.error(ex.getMessage(),ex);
            unlockErrorLabel.setText(I18n.t("unlock.error.generic"));
        } finally {
            // in ogni caso, pulisci il buffer locale
            if (pwd != null) {
                java.util.Arrays.fill(pwd, '\0');
            }
        }
    }


    void lockScreen() {
        locked = true;

        //lockPane.toFront();
        List<Window> windows = new ArrayList<>(Window.getWindows());
        for (Window w : windows) {
            if (w instanceof Stage stage) {
                if (!stage.equals(mainRoot.getScene().getWindow())) {
                    stage.close();
                }
            }
        }

        // Effetto blur su tutto il contenuto
        GaussianBlur blur = new GaussianBlur(25);
        appRoot.setEffect(blur);

        // Overlay "tenda" scura
        lockPane.setStyle("-fx-background-color: rgba(0,0,0,0.60);");

        lockPane.setVisible(true);
        VaultRepository.VAULT_CONTEXT.secureClear();

        lockPane.setDisable(false);
        unlockPasswordField.wipeAllMemory();
        unlockErrorLabel.setText("");

    }






    @FXML private StackPane mainRoot;




    private void registerSceneActivity(Scene scene) {
        scene.addEventFilter(MouseEvent.ANY, e -> resetInactivityTimer());
        scene.addEventFilter(KeyEvent.ANY, e -> resetInactivityTimer());
    }
    private javafx.animation.Timeline countdownTimer;

     private Task currentFileTask;

    public void init() {



        Scene scene = mainRoot.getScene();
        if (scene != null) {
            registerSceneActivity(scene);
        } else {
            // scena non pronta, la registriamo dopo il render
            Platform.runLater(() -> registerSceneActivity(mainRoot.getScene()));
        }

        char[] mp =  VaultRepository.VAULT_CONTEXT.getMasterPassword();

        // password table
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        noteColumn.setCellValueFactory(new PropertyValueFactory<>("notes"));


        usernameColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(""));
        noteColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(""));

        noteColumn.setCellFactory(col ->
                new SecureMaskTableCell(SecretEntry::getNotes));




        usernameColumn.setCellFactory(col ->
                new SecureMaskTableCell(SecretEntry::getUsername));

     
        tableEntries.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableEntries.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        titleColumn.setMinWidth(130);
        usernameColumn.setMinWidth(130);
        strengthColumn.setMinWidth(130);
        categoryColumn.setMinWidth(100);
        noteColumn.setMinWidth(120);
        entryCreatedAtColumn.setMinWidth(150);
        entryPasswordChangedColumn.setMinWidth(150);

        // L'ultima colonna si espande per coprire lo spazio residuo (no colonna vuota).
        // Quando la finestra è stretta, le minWidth forzano la scrollbar orizzontale.
        entryPasswordChangedColumn.prefWidthProperty().bind(
                Bindings.max(150,
                        tableEntries.widthProperty()
                                .subtract(titleColumn.widthProperty())
                                .subtract(usernameColumn.widthProperty())
                                .subtract(strengthColumn.widthProperty())
                                .subtract(categoryColumn.widthProperty())
                                .subtract(noteColumn.widthProperty())
                                .subtract(entryCreatedAtColumn.widthProperty())
                                .subtract(2)
                )
        );

        fixTabBorder(tabPassowrd);
        fixTabBorder(tabFiles);
        fixTabBorder(tabSsh);
        // --- COLONNA FORZA PASSWORD (ordinabile con score 0–100, -1 = vuoto) ---
        strengthColumn.setCellValueFactory(cellData -> {
            SecretEntry entry = cellData.getValue();
            char[] pwd = entry.getPassword();
            if (pwd == null || pwd.length == 0) {
                return new ReadOnlyObjectWrapper<>(-1);
            }
            int score = PasswordGeneratorController.updateStrengthBar(pwd, new ProgressBar(), new Label(), false);
            return new ReadOnlyObjectWrapper<>(score);
        });

// --- CELL FACTORY con ProgressBar + Label ---
        strengthColumn.setCellFactory(col -> new TableCell<SecretEntry, Integer>() {

            private final ProgressBar bar = new ProgressBar(0);
            private final Label lbl = new Label();
            private final HBox box = new HBox(6);
            private final Label emptyLbl = new Label();

            {
                bar.setPrefWidth(70);
                bar.setMaxWidth(70);
                lbl.setStyle("-fx-text-fill: #444;");
                box.getChildren().addAll(bar, lbl);
                emptyLbl.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
            }

            @Override
            protected void updateItem(Integer score, boolean empty) {
                super.updateItem(score, empty);

                if (empty || score == null) {
                    setGraphic(null);
                    return;
                }

                if (score == -1) {
                    emptyLbl.setText(I18n.t("pwdgen.strength.empty"));
                    setGraphic(emptyLbl);
                    return;
                }

                PasswordGeneratorController.updateStrengthBar( getTableRow().getItem().getPassword(), bar, lbl, false);

                setGraphic(box);
            }
        });





        // === FORMATTER DATE createdAt / passwordChangedAt per entry ===
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        entryCreatedAtColumn.setCellValueFactory(data ->
                new ReadOnlyObjectWrapper<>(data.getValue().getCreatedAt()));
        entryPasswordChangedColumn.setCellValueFactory(data ->
                new ReadOnlyObjectWrapper<>(data.getValue().getPasswordChangedAt()));

        entryCreatedAtColumn.setCellFactory(col -> new TableCell<SecretEntry, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.longValue() == 0) {
                    setText(null);
                } else {
                    setText(Instant.ofEpochMilli(item.longValue())
                            .atZone(ZoneId.systemDefault())
                            .format(dtf));
                }
            }
        });

        entryPasswordChangedColumn.setCellFactory(col -> new TableCell<SecretEntry, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.longValue() == 0) {
                    setText(null);
                } else {
                    setText(Instant.ofEpochMilli(item.longValue())
                            .atZone(ZoneId.systemDefault())
                            .format(dtf));
                }
            }
        });

// --- DOPPIO CLICK + CONTEXT MENU ---
        tableEntries.setRowFactory(tv -> {
            TableRow<SecretEntry> row = new TableRow<>();

            // Context menu
            ContextMenu ctxMenu = new ContextMenu();
            javafx.scene.control.MenuItem copyPwd = new javafx.scene.control.MenuItem(I18n.t("main.ctx.copy.password"));
            javafx.scene.control.MenuItem copyUsr = new javafx.scene.control.MenuItem(I18n.t("main.ctx.copy.username"));

            copyPwd.setOnAction(e -> {
                SecretEntry entry = row.getItem();
                if (entry != null && entry.getPassword() != null) {
                    copyToClipboardWithCountdown(entry.getPassword());
                }
            });

            copyUsr.setOnAction(e -> {
                SecretEntry entry = row.getItem();
                if (entry != null && entry.getUsername() != null) {
                    copyToClipboardWithCountdown(entry.getUsername());
                }
            });

            ctxMenu.getItems().addAll(copyPwd, copyUsr);

            row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(ctxMenu)
            );

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    // imposta riga selezionata
                    tableEntries.getSelectionModel().select(row.getIndex());

                    // chiama il tuo metodo di modifica
                    handleEdit();
                }
            });

            return row;
        });


        loadTable();

        // file table
        updateTempFileCount();
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        fileTypeColumn.setCellValueFactory(new PropertyValueFactory<>("contentType"));
        fileSizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));

        // === FORMATTER DATE addTime / lastEdit ===

        fileAddTimeColumn.setCellValueFactory(new PropertyValueFactory<>("addTime"));
        fileLastEditColumn.setCellValueFactory(new PropertyValueFactory<>("lastEdit"));

        fileAddTimeColumn.setCellFactory(col -> new TableCell<SecretFile, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || ((long)item) == 0) {
                    setText(null);
                } else {
                    setText(Instant.ofEpochMilli(item.longValue())
                            .atZone(ZoneId.systemDefault())
                            .format(dtf));
                }
            }
        });

        fileLastEditColumn.setCellFactory(col -> new TableCell<SecretFile, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || ((long)item) == 0) {
                    setText(null);
                } else {
                    setText(Instant.ofEpochMilli(item.longValue())
                            .atZone(ZoneId.systemDefault())
                            .format(dtf));
                }
            }
        });


        // formatter per dimensioni leggibili
        fileSizeColumn.setCellFactory(col -> new TableCell<SecretFile, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(humanReadableSize(item.longValue()));
                }
            }
        });

        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        fileNameColumn.setPrefWidth(250);
        fileSizeColumn.setPrefWidth(120);
        fileTypeColumn.setPrefWidth(150);
        fileLastEditColumn.setPrefWidth(160);
        fileAddTimeColumn.setPrefWidth(160);

// Evita che le colonne collassino
        fileNameColumn.setMinWidth(150);
        fileSizeColumn.setMinWidth(100);
        fileTypeColumn.setMinWidth(120);
        fileLastEditColumn.setMinWidth(140);
        fileAddTimeColumn.setMinWidth(140);

        loadFiles();

        // stato iniziale progress
        fileProgressBar.setVisible(false);
        fileProgressPercentLabel.setVisible(false);
        fileProgressDetailsLabel.setVisible(false);
        startInactivityTimer();
        registerActivityListeners();
        updateVaultStats();

    }
















    private void updateCountdown() {
            long sec = remainingMillis.get() / 1000;

            if (sec <= 0) {
                countdownLabel.setVisible(false);
                return;
            }

            countdownLabel.setVisible(true);
            countdownLabel.setText("⏱" + " " + sec + I18n.t("lock.countdown.seconds"));


        if (sec <= 5) {
                countdownLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px; "
                        + " -fx-padding: 6;");
            } else if (sec <= 10) {
                countdownLabel.setStyle("-fx-text-fill: orange; -fx-font-size: 14px; "
                        + " -fx-padding: 6;");
            } else {
                countdownLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; "
                        + " -fx-padding: 6;");
            }
        }




    @FXML private Label countdownLabel;

    private static final int CLIPBOARD_COUNTDOWN_SECONDS = 20;

    private boolean clipboardOwned = false;

    private void copyToClipboardWithCountdown(char[] data) {
        if (data == null || data.length == 0) return;

        // Copy to clipboard using temporary String (same pattern as SecureInputBase)
        javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
        cc.putString(new String(data));
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
        clipboardOwned = true;

        // Stop any existing clipboard timer
        if (clipboardTimer != null) clipboardTimer.stop();

        final AtomicLong clipRemaining = new AtomicLong(CLIPBOARD_COUNTDOWN_SECONDS);
        clipboardCountdownLabel.setVisible(true);

        clipboardTimer = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    long sec = clipRemaining.decrementAndGet();
                    if (sec <= 0) {
                        // Clear clipboard like SecureInputBase.clearWindowsClipboardIfOwned()
                        if (clipboardOwned) {
                            javafx.scene.input.ClipboardContent empty = new javafx.scene.input.ClipboardContent();
                            empty.putString("");
                            javafx.scene.input.Clipboard.getSystemClipboard().setContent(empty);
                            clipboardOwned = false;
                        }
                        clipboardCountdownLabel.setVisible(false);
                        clipboardTimer.stop();
                        return;
                    }
                    clipboardCountdownLabel.setText(
                        MessageFormat.format(I18n.t("main.clipboard.countdown"), sec));
                    if (sec <= 5) {
                        clipboardCountdownLabel.setStyle(
                            "-fx-text-fill: red; -fx-font-size: 14px; -fx-padding: 6;");
                    } else if (sec <= 10) {
                        clipboardCountdownLabel.setStyle(
                            "-fx-text-fill: orange; -fx-font-size: 14px; -fx-padding: 6;");
                    } else {
                        clipboardCountdownLabel.setStyle(
                            "-fx-text-fill: #00FFFF; -fx-font-size: 14px; -fx-padding: 6;");
                    }
                })
        );
        clipboardTimer.setCycleCount(CLIPBOARD_COUNTDOWN_SECONDS);

        // Set initial state
        clipboardCountdownLabel.setText(
            MessageFormat.format(I18n.t("main.clipboard.countdown"), CLIPBOARD_COUNTDOWN_SECONDS));
        clipboardCountdownLabel.setStyle(
            "-fx-text-fill: #00FFFF; -fx-font-size: 14px; -fx-padding: 6;");
        clipboardTimer.playFromStart();
    }

    public static void fixTabBorder(Tab ta) {
        Runnable apply = () -> ta.setStyle(
                "-fx-focus-color: transparent;" +
                        "-fx-faint-focus-color: transparent;"
        );
        apply.run();
        ta.selectedProperty().addListener((o, ov, nv) -> apply.run());
    }



    protected void resetInactivityTimer() {
        if (locked || vaultBusy) return;
        remainingMillis.set(INACTIVITY_TIMEOUT_MS);
        startInactivityTimer(); // <-- ricarica entrambi i timer in modo pulito
    }

    private void registerActivityListeners() {
        // Listener globale per tutto il runtime JavaFX
        EventHandler<Event> globalActivityListener = e -> resetInactivityTimer();

        // Intercetta mouse e tastiera OVUNQUE
        Toolkit.getDefaultToolkit().addAWTEventListener(
                event -> resetInactivityTimer(),
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK
        );

        // Intercetta eventi JavaFX (senza limitarsi a mainRoot)
        Scene scene = mainRoot.getScene();
        if (scene != null) {
            scene.addEventFilter(MouseEvent.ANY, globalActivityListener);
            scene.addEventFilter(KeyEvent.ANY, globalActivityListener);
        }
    }

    private final AtomicLong remainingMillis = new AtomicLong(INACTIVITY_TIMEOUT_MS);

    private void startInactivityTimer() {

        // Stoppa timer vecchi per evitare Timeline sovrapposte
        if (inactivityTimer != null) inactivityTimer.stop();
        if (countdownTimer != null) countdownTimer.stop();
       // Timer di lock
        inactivityTimer = new Timeline(
                new KeyFrame(Duration.millis(remainingMillis.get()), e -> lockScreen())
        );
        inactivityTimer.setCycleCount(1);

        // Timer countdown (1 secondo per tick)
        countdownTimer = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    remainingMillis.addAndGet(-1000);
                    updateCountdown();

                    if (remainingMillis.get() <= 0) {

                        countdownTimer.stop();
                    }
                })
        );
        countdownTimer.setCycleCount(Animation.INDEFINITE);

        inactivityTimer.playFromStart();
        countdownTimer.playFromStart();
    }


    private void loadFiles() {
        ObservableList<SecretFile> data = FXCollections.observableArrayList();

        for (SecretFile sf : VaultRepository.VAULT_CONTEXT.getVault().getFiles()) {
            if ((sf.getContentType() == null || sf.getContentType().isBlank()
                    || "?".equals(sf.getContentType())) && sf.getName() != null) {
                sf.setContentType(guessMimeFromName(sf.getName()));
            }
            data.add(sf);
        }

        fullFiles.setAll(data);

    }

    private void applySearchFilter(String text) {

        if (text == null || text.isBlank()) {
            filteredEntries.setPredicate(e -> true);
            filteredFiles.setPredicate(f -> true);
            filteredSsh.setPredicate(s -> true);
            return;
        }

        String lower = text.toLowerCase();
        int tabIndex = mainTabs.getSelectionModel().getSelectedIndex();

        if (tabIndex == 0) { // PASSWORD TAB

            filteredEntries.setPredicate(e -> {

                // titolo è ancora String → OK usarlo
                boolean matchTitle =
                        e.getTitle() != null && e.getTitle().toLowerCase().contains(lower);

                boolean matchUser =
                        charArrayContainsIgnoreCase(e.getUsername(), lower);

                // categoria è stringa → OK
                boolean matchCategory =
                        e.getCategory() != null && e.getCategory().toLowerCase().contains(lower);

                boolean matchNotes =
                        charArrayContainsIgnoreCase(e.getNotes(), lower);

                // (NON filtriamo per password ovviamente)
                return matchTitle || matchUser || matchCategory || matchNotes;
            });

        } else if (tabIndex == 1) { // FILES TAB

            filteredFiles.setPredicate(f ->
                    f.getName().toLowerCase().contains(lower) ||
                            f.getContentType().toLowerCase().contains(lower)
            );
        } else if (tabIndex == 2) { // SSH TAB

            filteredSsh.setPredicate(s ->
                    (s.getName() != null && s.getName().toLowerCase().contains(lower)) ||
                    (s.getHost() != null && s.getHost().toLowerCase().contains(lower)) ||
                    (s.getUsername() != null && s.getUsername().toLowerCase().contains(lower)) ||
                    (s.getKeyFileName() != null && s.getKeyFileName().toLowerCase().contains(lower))
            );
        }
    }





    private String guessMimeFromName(String name) {
        try {
            return Files.probeContentType(Path.of(name));
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    private void loadTable() {
        fullEntries.setAll(VaultRepository.VAULT_CONTEXT.getVault().getEntries());
         updateVaultStats();
    }



    // formatter dimensioni file
    private static String formatEta(double seconds) {
        if (seconds <= 0) {
              return I18n.t("file.eta.calculating");
        }
        long s = (long) seconds;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format("%dh %02dm %02ds", h, m, sec);
        } else if (m > 0) {
            return String.format("%dm %02ds", m, sec);
        } else {
            return String.format("%ds", sec);
        }
    }

    // ================== PASSWORD ENTRIES ==================

    @FXML
    private void handleEdit() {
        SecretEntry selected = tableEntries.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }


        try {


            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/entry_edit.fxml"),
                    I18n.getBundle()
            );


            Parent root = loader.load();

            EntryEditController controller = loader.getController();
            controller.setMainController(this);

            controller.init(selected, () -> {
                try {


                    // 1) Refresh tabella
                    tableEntries.refresh();

                    // 2) Salva vault aggiornato su disco (formato V3)
                    runSaveVaultWithProgress();

                    // 3) Sentinel: re-analyze
                    runSentinelAnalysis();

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Stage stage = new Stage();
            stage.setTitle(I18n.t("main.edit.title"));


            stage.setScene(new Scene(root));
            stage.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
            ));
            stage.getScene().getStylesheets().add(
                    Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
            );
            stage.initOwner(tableEntries.getScene().getWindow());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(true);
            stage.addEventFilter(MouseEvent.ANY, e -> resetInactivityTimer());
            stage.addEventFilter(KeyEvent.ANY, e -> resetInactivityTimer());
            stage.showAndWait();

        } catch (Exception e) {
            SecureLogger.error(e.getMessage(),e);
        }
    }

    @Override
    public void onProgress(double value) {
        Platform.runLater(() -> {
            if (fileProgressBar != null) {
                fileProgressBar.setVisible(true);
                fileProgressBar.setProgress(value);
            }
            if (fileProgressPercentLabel != null) {
                fileProgressPercentLabel.setVisible(true);
                fileProgressPercentLabel.setText(String.format("%.0f%%", value * 100));
            }
        });
    }

    @Override
    public void onMessage(String msg) {
        Platform.runLater(() -> {
            if (fileProgressDetailsLabel != null) {
                fileProgressDetailsLabel.setVisible(true);
                fileProgressDetailsLabel.setText(msg);
            }
        });
    }

    @FXML
    private void handlePasswordGenerator() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/password_generator.fxml"),
                    I18n.getBundle()
            );

            Stage dialog = new Stage();
            dialog.setScene(new Scene(loader.load()));
            dialog.setTitle(I18n.t("entry.dialog.pwdgen.title"));
            dialog.initOwner(tableEntries.getScene().getWindow());
            dialog.setResizable(false);
            dialog.getScene().getStylesheets().add(
                    Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
            );
            dialog.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
            ));

            PasswordGeneratorController ctrl = loader.getController();
            ctrl.setMainController(this);

            dialog.addEventFilter(MouseEvent.ANY, e -> resetInactivityTimer());
            dialog.addEventFilter(KeyEvent.ANY, e -> resetInactivityTimer());
            dialog.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.L) {
                    lockScreen();
                    event.consume();
                }
            });
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (Exception e) {
            SecureLogger.error(e.getMessage(), e);
        }
    }

    @FXML
    private void handleAdd() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/add_entry.fxml"),
                    I18n.getBundle());

            Stage dialog = new Stage();
            dialog.setScene(new Scene(loader.load()));
            dialog.setTitle(I18n.t("main.add.title"));

            dialog.initOwner(tableEntries.getScene().getWindow());
            dialog.setResizable(true);
            dialog.getScene().getStylesheets().add(
                    Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
            );
            dialog.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
            ));

            AddEntryController ctrl = loader.getController();
            ctrl.setMainController(this);

            dialog.addEventFilter(MouseEvent.ANY, e -> resetInactivityTimer());
            dialog.addEventFilter(KeyEvent.ANY, e -> resetInactivityTimer());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();

            if (ctrl.isSaved()) {
                // 1) aggiorniamo il modello in memoria
                 VaultRepository.VAULT_CONTEXT.getVault().add(ctrl.getEntry());

                // 2) persistiamo il vault sul file .ocv (V3)
                runSaveVaultWithProgress();
                // 3) aggiorniamo la tabella a video
                loadTable();
                // 4) Sentinel: re-analyze
                runSentinelAnalysis();
            }

        } catch (Exception e) {
            SecureLogger.error(e.getMessage(),e);
            UIUtils.showError(I18n.t("main.error.save.entry"));

        }
    }

    @FXML
    private void handleImport() {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("import.chooser.title"));
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter(I18n.t("import.filter.all"), "*.csv"),
                    new FileChooser.ExtensionFilter("CSV", "*.csv")
            );

            java.io.File selectedFile = chooser.showOpenDialog(tableEntries.getScene().getWindow());
            if (selectedFile == null) return;

            ImportOrchestrator orchestrator = new ImportOrchestrator();
            ImportOrchestrator.ImportPreviewData previewData;
            try {
                previewData = orchestrator.prepareImport(selectedFile.toPath());
            } catch (ImportOrchestrator.ImportException ex) {
                UIUtils.showError(I18n.t(ex.getMessage()));
                return;
            }

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/import_preview.fxml"),
                    I18n.getBundle());

            Stage dialog = new Stage();
            dialog.setScene(new Scene(loader.load()));
            dialog.setTitle(I18n.t("import.title"));
            dialog.initOwner(tableEntries.getScene().getWindow());
            dialog.setResizable(true);
            dialog.getScene().getStylesheets().add(
                    Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
            );
            dialog.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
            ));

            ImportPreviewController ctrl = loader.getController();
            ctrl.setPreviewData(previewData);

            dialog.addEventFilter(MouseEvent.ANY, e -> resetInactivityTimer());
            dialog.addEventFilter(KeyEvent.ANY, e -> resetInactivityTimer());
            dialog.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.L) {
                    lockScreen();
                    event.consume();
                }
            });
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();

            if (ctrl.isConfirmed()) {
                List<SecretEntry> imported = ctrl.getImportedEntries();
                for (SecretEntry entry : imported) {
                    VaultRepository.VAULT_CONTEXT.getVault().add(entry);
                }
                runSaveVaultWithProgress();
                loadTable();
                runSentinelAnalysis();
                UIUtils.showToast(tableEntries,
                        MessageFormat.format(I18n.t("import.toast.success"), imported.size()));
            }

        } catch (Exception e) {
            SecureLogger.error(e.getMessage(), e);
            UIUtils.showError(I18n.t("import.error.generic"));
        }
    }

    private void updateVaultStats() {
        if ( VaultRepository.VAULT_CONTEXT.getVault() == null) {



            if (infoLabel != null) {

                infoLabel.setText(
                        MessageFormat.format(I18n.t("main.vault.stats"), 0, 0,0)
                );
            }
            return;
        }

        int entryCount =  VaultRepository.VAULT_CONTEXT.getVault().getEntries().size();
        int fileCount  =  VaultRepository.VAULT_CONTEXT.getVault().getFiles().size();
        int sshCount   =  VaultRepository.VAULT_CONTEXT.getVault().getSshConnections().size();

        if (infoLabel != null) {
            infoLabel.setText(
                    MessageFormat.format(I18n.t("main.vault.stats"), entryCount, fileCount, sshCount)
            );

        }


        try {
            long size = Files.size(VaultRepository.VAULT_CONTEXT.getVaultPath());
            labelDimension.setText(
                    MessageFormat.format(I18n.t("main.vault.size"), humanReadableSize(size))
            );
        } catch (IOException e) {
            labelDimension.setText(MessageFormat.format(I18n.t("main.vault.size"),"-"));
        }

    }



    // ================== FILE HANDLERS ==================

    private void prepareProgressUi() {


            fileProgressBar.progressProperty().unbind();

            // Imposta manualmente progress = 0
            fileProgressBar.setProgress(0);

            fileProgressBar.setVisible(true);
            fileProgressPercentLabel.setVisible(true);
            fileProgressDetailsLabel.setVisible(true);

            fileProgressPercentLabel.setText("0%");
            fileProgressDetailsLabel.setText("");
        }


    private void resetProgressUi() {
        fileProgressBar.progressProperty().unbind();
        fileProgressBar.setProgress(0);

        fileProgressBar.setVisible(false);
        fileProgressPercentLabel.setVisible(false);
        fileProgressDetailsLabel.setVisible(false);
    }


    private void disableTabFileButton(boolean isDisabled){
        btnAddFile.setDisable(isDisabled);
        btnOpenFile.setDisable(isDisabled);
        btnExportFile.setDisable(isDisabled);
        btnElimina.setDisable(isDisabled);
        tabSsh.setDisable(isDisabled);
        tabPassowrd.setDisable(isDisabled);
        btnWipeTemp.setDisable(isDisabled);

    }


    @FXML
    private void handleAddFile() {

        disableTabFileButton(true);

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("main.open.file.choose"));
        Stage fcStage = (Stage) mainRoot.getScene().getWindow();
        fcStage.getIcons().add(new javafx.scene.image.Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
        ));
        File file = chooser.showOpenDialog(fcStage);
        if (file == null){
            disableTabFileButton(false);

            return;
        }



        long totalBytes = file.length();

        String id = UUID.randomUUID().toString();

        // IV AES-GCM per questo file
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);


        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                 long startNanos = System.nanoTime();
                long copied = 0;

                try (InputStream in = Files.newInputStream(file.toPath())) {

                    byte[] buf = new byte[65536];
                    int read;
                    long lastUpdate = 0L;

                    while ((read = in.read(buf)) != -1) {
                        if (isCancelled()) return null;

                        copied += read;

                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 50) {
                            lastUpdate = now;
                            updateProgress(copied, totalBytes);

                            long elapsed = System.nanoTime() - startNanos;
                            double sec = elapsed / 1_000_000_000.0;
                            double speed = sec > 0 ? copied / sec : 0;

                            long remaining = totalBytes - copied;
                            double eta = speed > 0 ? remaining / speed : -1;

                            double percent = (copied * 100.0) / totalBytes;
                            String etaText = eta > 0 ? formatEta(eta) : "calcolo…";

                            double speedMB = speed / (1024 * 1024);

                            Platform.runLater(() -> {
                                fileProgressPercentLabel.setText(String.format("%.0f%%", percent));
                                fileProgressDetailsLabel.setText(String.format("%.2f MB/s, ETA: %s",
                                        speedMB, etaText));
                            });

                        }
                    }
                }

                return null; // nessun byte[] prodotto
            }
        };

        // ---- UI ----
        currentFileTask = task;
        prepareProgressUi();
        fileProgressBar.progressProperty().bind(task.progressProperty());


        task.setOnSucceeded(e -> {

            // Crea descrittore file (Nessuna cifratura qui!)
            // Crea descrittore completo
            SecretFile sf = new SecretFile();
            sf.setId(id);
            sf.setName(file.getName());
            sf.setSize(file.length());
            try {
                sf.setContentType(Files.probeContentType(file.toPath()));
            } catch (IOException ex) {
                sf.setContentType("application/octet-stream");
            }
            sf.setAddTime(System.currentTimeMillis());
            sf.setLastEdit(System.currentTimeMillis());

            // CAMPI ESSENZIALI per il salvataggio!
             sf.setVaultPath(file.toPath());    // <<< OBBLIGATORIO
            sf.setIv(iv);                              // <<< OBBLIGATORIO
            sf.setBlobOffset(0);                       // verrà aggiornato da saveVault()

            // Aggiungi al vault
             VaultRepository.VAULT_CONTEXT.getVault().getFiles().add(sf);
             // --- SALVA VAULT UNICO .ocv (V3) ---
            try {

                runSaveVaultWithProgress();


            } catch (Exception ex) {
                ex.printStackTrace();
                showError(I18n.t("main.error.save"));


                currentFileTask = null;
                return;
            }

            loadFiles();
            updateVaultStats();
            UIUtils.showToast(fileTable, I18n.t("main.file.added"));
            disableTabFileButton(false);

            currentFileTask = null;
        });


        task.setOnFailed(e -> {
            resetProgressUi();
            showError(I18n.t("main.error.encrypt.file"));
            task.getException().printStackTrace();
            disableTabFileButton(false);

            currentFileTask = null;
        });

        task.setOnCancelled(e -> {
            disableTabFileButton(false);

            resetProgressUi();
            UIUtils.showToast(fileTable, I18n.t("main.operation.canceled"));

            currentFileTask = null;
        });

        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();
    }

    @FXML
    private void handleOpenFile() {

        SecretFile sf = fileTable.getSelectionModel().getSelectedItem();
        if (sf == null) {
            return;
        }


        if (acquireVaultLock(I18n.t("main.busy.operation"))) return;


        long totalBytes = sf.getSize();  // lunghezza CIPHERTEXT (incluso TAG)

        Path temp;
        try {
            temp = TempVaultFiles.createTempFileForOpen(sf.getName());
        } catch (IOException e) {
            releaseVaultLock();
            showError(I18n.t("main.busy.temp"));
            return;
        }

        byte[] iv = sf.getIv();
        Path vaultPath = sf.getVaultPath();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {

                SecretKey key = VaultRepository.VAULT_CONTEXT.getKeyEnc();

                decryptBlobToTemp(key, sf, temp, totalBytes);

                SecureLogger.debug("Forcing light memory cleanup after decrypt");
                System.gc();
                return null;
            }
        };

        // UI
        currentFileTask = task;
        prepareProgressUi();
        fileProgressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            resetProgressUi();
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop d = Desktop.getDesktop();

                    if (d.isSupported(Desktop.Action.OPEN)) {
                        // Evita blocco JavaFX: esegui l'apertura in background
                        new Thread(() -> {
                            try { d.open(temp.toFile()); } catch (Exception ignore) {}
                        }).start();
                    } else {
                        // Linux fallback rapido
                        new Thread(() -> {
                            try { new ProcessBuilder("xdg-open", temp.toFile().getAbsolutePath()).start(); }
                            catch (Exception ignore) {}
                        }).start();
                    }
                }

                // Aggiorna solo in RAM (UI) l’ultimo accesso
                sf.setLastEdit(System.currentTimeMillis());
                fileTable.refresh();
                updateTempFileCount();
                releaseVaultLock();

            } catch (Exception ex) {
                showError(I18n.t("main.error.open.file"));
            }
            currentFileTask = null;
        });

        task.setOnFailed(e -> {
            resetProgressUi();
            showError(I18n.t("main.error.decrypt.file"));

            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
            currentFileTask = null;
            releaseVaultLock();
        });

        task.setOnCancelled(e -> {
            resetProgressUi();
            UIUtils.showToast(fileTable, I18n.t("main.operation.canceled"));
            currentFileTask = null;
            releaseVaultLock();
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Decifra un file dal vault in un file temporaneo usando la chiave specificata.
     * Lancia InvalidCipherTextException se il tag GCM non corrisponde (chiave errata).
     */
    private void decryptBlobToTemp(SecretKey key,
                                   SecretFile sf, Path temp, long totalBytes) throws Exception {

        try (RandomAccessFile raf = new RandomAccessFile(sf.getVaultPath().toFile(), "r")) {

            GCMModeCipher gcmCipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
            AEADParameters params = new AEADParameters(
                    new KeyParameter(key.getEncoded()),
                    OneCrittoV3Format.GCM_TAG_LENGTH * 8,
                    sf.getIv()
            );
            gcmCipher.init(false, params);

            raf.seek(sf.getBlobOffset());
            long remaining = sf.getSize();

            try (OutputStream out = Files.newOutputStream(temp)) {

                byte[] buf = new byte[8192];

                while (remaining > 0) {
                    int toRead = (int) Math.min(buf.length, remaining);
                    int n = raf.read(buf, 0, toRead);
                    if (n == -1) throw new IOException("EOF prematuro nel vault");
                    remaining -= n;

                    byte[] outBuf = new byte[gcmCipher.getUpdateOutputSize(n)];
                    int produced = gcmCipher.processBytes(buf, 0, n, outBuf, 0);
                    if (produced > 0) {
                        out.write(outBuf, 0, produced);
                    }
                }

                byte[] finalBuf = new byte[gcmCipher.getOutputSize(0)];
                int finalLen = gcmCipher.doFinal(finalBuf, 0);
                if (finalLen > 0) {
                    out.write(finalBuf, 0, finalLen);
                }
            }
        }
    }

    private void runSaveVaultWithProgress() {
        if (acquireVaultLock(I18n.t("main.busy.operation"))) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                synchronized (vaultLock) {
                      repository.saveVaultV4(fileTable);
                }
                return null;
            }
        };

        currentFileTask = task;

        prepareProgressUi();

        // NON bindiamo la progressbar al task
        fileProgressBar.progressProperty().unbind();
        fileProgressBar.setProgress(0);

        task.setOnSucceeded(e -> {
            resetProgressUi();
            UIUtils.showToast(fileTable, I18n.t("main.vault.saved"));
            currentFileTask = null;
            releaseVaultLock();
            updateVaultStats();
        });

        task.setOnFailed(e -> {
            resetProgressUi();
            showError(I18n.t("main.error.save"));
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
            currentFileTask = null;
            releaseVaultLock();

        });

        task.setOnCancelled(e -> {
            resetProgressUi();
            UIUtils.showToast(fileTable, I18n.t("main.save.canceled"));
            currentFileTask = null;
            releaseVaultLock();

        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }




    private static class RafSliceInputStream extends InputStream {

        private final RandomAccessFile raf;
        private long remaining;

        RafSliceInputStream(RandomAccessFile raf, long offset, long length) throws IOException {
            this.raf = raf;
            this.raf.seek(offset);
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = raf.read();
            if (b != -1) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            if (len > remaining) {
                len = (int) remaining;
            }
            int r = raf.read(b, off, len);
            if (r > 0) {
                remaining -= r;
            }
            return r;
        }
    }


    @FXML
    private void handleExportFile() {


        SecretFile sf = fileTable.getSelectionModel().getSelectedItem();
        if (sf == null) return;

        if (acquireVaultLock(I18n.t("main.busy.operation"))) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("main.export.file"));
        chooser.setInitialFileName(sf.getName());
        Stage fcStage = (Stage) mainRoot.getScene().getWindow();

        File dest = chooser.showSaveDialog(fcStage);
        if (dest == null) {
            releaseVaultLock();
            return;
        }


        long encryptedSize = sf.getSize();   // <--- importantissimo!!!
        long plainSize     = sf.getSize();
        byte[] iv          = sf.getIv();
        Path vaultPath     = sf.getVaultPath();

        Task<Void> task = new Task<>() {

            @Override
            protected Void call() throws Exception {

                SecretKey key = VaultRepository.VAULT_CONTEXT.getKeyEnc();

                decryptBlobToTemp(key, sf, dest.toPath(), encryptedSize);

                return null;
            }
        };

        // UI bindings
        currentFileTask = task;
        prepareProgressUi();
        fileProgressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            resetProgressUi();
            try {
                sf.setLastEdit(System.currentTimeMillis());
                fileTable.refresh();
                UIUtils.showToast(fileTable, I18n.t("main.file.exported"));


            } catch (Exception ex) {
                showError(I18n.t("main.error.export.after"));

            }
            currentFileTask = null;
            releaseVaultLock();
            SecureLogger.debug("cleanup RAM after export");
            System.gc();
        });

        task.setOnFailed(e -> {
            resetProgressUi();
            releaseVaultLock();
            showError(I18n.t("main.error.decrypt.file"));
            if (task.getException() != null) task.getException().printStackTrace();
            currentFileTask = null;
            releaseVaultLock();
            SecureLogger.debug("cleanup RAM after export");
            System.gc();   // li

        });

        task.setOnCancelled(e -> {
            resetProgressUi();
            releaseVaultLock();
            UIUtils.showToast(fileTable, "Operation canceled");
            currentFileTask = null;
            releaseVaultLock();
            SecureLogger.debug("cleanup RAM after export");
            System.gc();   // li

        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }



    private void deleteEntries(){
        List<SecretEntry> list = new ArrayList<>(tableEntries.getSelectionModel().getSelectedItems());

         VaultRepository.VAULT_CONTEXT.getVault().getEntries().removeAll(list);

        try {
            runSaveVaultWithProgress();
            loadTable();
            runSentinelAnalysis();
            UIUtils.showToast(
                    fileTable,
                    MessageFormat.format(I18n.t("main.delete.entries.toast"), list.size())
            );

        } catch (Exception e) {
            SecureLogger.error(e.getMessage(),e);
            showError(I18n.t("main.error.save"));
        }
    }

    @FXML
    private void handleDeleteEntry() {

        List<SecretEntry> toDelete = new ArrayList<>(tableEntries.getSelectionModel().getSelectedItems());
        if(toDelete.isEmpty()){

            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle(I18n.t("main.delete.entry.info.title"));
            info.setHeaderText(I18n.t("main.delete.entry.info.header"));
            info.setContentText(I18n.t("main.delete.entries.empty"));


            // opzionale: posiziona il popup sulla finestra corrente
            info.initOwner(tableEntries.getScene().getWindow());

            info.showAndWait();

            return ;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(I18n.t("main.delete.entries.confirm.title"));
        confirm.setHeaderText(I18n.t("main.delete.entries.confirm.header"));
        ButtonType yes = new ButtonType(I18n.t("main.delete.entries.button"), ButtonBar.ButtonData.OK_DONE);
        ButtonType no  = new ButtonType(I18n.t("main.delete.entries.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.setContentText("Delete");


        confirm.getButtonTypes().setAll(yes, no);
        confirm.initOwner(tableEntries.getScene().getWindow());

        confirm.getDialogPane().setMinWidth(450);
        ButtonBar buttonBar = (ButtonBar) confirm.getDialogPane().lookup(".button-bar");
        if (buttonBar != null) buttonBar.setButtonMinWidth(120);

        ((Stage) confirm.getDialogPane().getScene().getWindow())
                .getIcons()
                .add(new Image(
                        Objects.requireNonNull(getClass().getResourceAsStream(
                                "/icons/onecritto_white_key_32x32.png"))
                ));
        confirm.showAndWait().ifPresent(response -> {
            if (response == yes) {
                deleteEntries();
            }
        });
    }
    /** Pulisce in sicurezza le password dal VaultContext */


    private void deleteFiles() throws Exception {
        List<SecretFile> toDelete = new ArrayList<>(fileTable.getSelectionModel().getSelectedItems());

        // rimuovi dal vault
         VaultRepository.VAULT_CONTEXT.getVault().getFiles().removeAll(toDelete);


        runSaveVaultWithProgress();

        loadFiles();

        showToast(fileTable, toDelete.size() + " file eliminati");
    }


    @FXML
    private void handleDeleteFile() {

        List<SecretFile> toDelete = new ArrayList<>(fileTable.getSelectionModel().getSelectedItems());
        if(toDelete.isEmpty()){

            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle(I18n.t("main.delete.file.info.title"));
            info.setHeaderText(I18n.t("main.delete.file.info.header"));
            info.setContentText(I18n.t("main.delete.file.info.content"));

            // opzionale: posiziona il popup sulla finestra corrente
            info.initOwner(fileTable.getScene().getWindow());

            info.showAndWait();

            return ;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setContentText(I18n.t("alert.delete.file.title"));
        confirm.setTitle(I18n.t("alert.delete.file.title"));
        confirm.setHeaderText(I18n.t("alert.delete.file.header"));

        ButtonType yes = new ButtonType(I18n.t("main.delete.file.button.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType no  = new ButtonType(I18n.t("main.delete.file.button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(yes, no);
        confirm.initOwner(fileTable.getScene().getWindow());

        confirm.getDialogPane().setMinWidth(450);
        ButtonBar fileButtonBar = (ButtonBar) confirm.getDialogPane().lookup(".button-bar");
        if (fileButtonBar != null) fileButtonBar.setButtonMinWidth(120);

        confirm.showAndWait().ifPresent(response -> {
            if (response == yes) {
                try {
                    // 1) elimina i FILES  cifrato dal filesystem (se esiste)
                    deleteFiles();

                } catch (Exception e) {
                    SecureLogger.error(e.getMessage(),e);
                    showToast(fileTable, I18n.t("main.error.delete.file"));

                }
            }
        });
    }

    @FXML
    private void handleWipeTemp() {
        Scene scene = btnWipeTemp.getScene();
        scene.setCursor(javafx.scene.Cursor.WAIT);
        btnWipeTemp.setDisable(true);

        new Thread(() -> {
            TempVaultFiles.cleanupTempDirSecure();
            Platform.runLater(() -> {
                scene.setCursor(javafx.scene.Cursor.DEFAULT);
                btnWipeTemp.setDisable(false);
                updateTempFileCount();
                UIUtils.showToast(fileTable, I18n.t("main.toast.wipetemp.done"));
            });
        }).start();
    }

    public void updateTempFileCount() {
        try {
            Path tempDir = TempVaultFiles.getTempDir();
            long count = Files.list(tempDir).filter(Files::isRegularFile).count();
            lblTempFileCount.setText("Files: " + count);
            if (count > 0) {
                lblTempFileCount.getStyleClass().removeAll("countdown-label", "countdown-label-paste");
                lblTempFileCount.getStyleClass().add("countdown-label-paste");
            } else {
                lblTempFileCount.getStyleClass().removeAll("countdown-label", "countdown-label-paste");
            }
        } catch (Exception e) {
            lblTempFileCount.setText("Files: 0");
            lblTempFileCount.getStyleClass().removeAll("countdown-label", "countdown-label-paste");
        }
    }


    public void switchLanguage(Locale locale) {


        // Ricarica l'intera scena con le nuove risorse
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/main.fxml")
            );
            loader.setResources(ResourceBundle.getBundle("i18n.messages", locale));

            Parent root = loader.load();
            MainController newController = loader.getController();
            newController.afterReloadFromLanguageSwitch(); // mantiene stato

            Stage stage = (Stage) mainRoot.getScene().getWindow();
            stage.getScene().setRoot(root);

        } catch (Exception e) {
            SecureLogger.error("Language switch error", e);
        }
    }

    public void afterReloadFromLanguageSwitch() {
        // Mantieni UI generica
        updateVaultStats();  // Aggiorna statistiche vault
        loadFiles();
        loadTable();// Ricarica tabelle
    }

     static final Path LANG_PREF =
            Paths.get(System.getProperty("user.home"), ".config", "onecritto", "lang");

    private void saveLanguagePreference(Locale locale) {
        try {
            Files.createDirectories(LANG_PREF.getParent());
            Files.writeString(LANG_PREF, locale.getLanguage());
        } catch (Exception ignored) {}
    }

    public static void saveLanguagePreferenceStatic(Locale locale) {
        try {
            Files.createDirectories(LANG_PREF.getParent());
            Files.writeString(LANG_PREF, locale.getLanguage());
        } catch (Exception ignored) {}
    }

    // ================== SENTINEL ==================

    public SentinelEngine getSentinelEngine() {
        return sentinelEngine;
    }

    private void runSentinelAnalysis() {
        if (VaultRepository.VAULT_CONTEXT.getVault() == null) return;
        sentinelEngine.analyzeAsync(VaultRepository.VAULT_CONTEXT.getVault(), report -> {
            updateSentinelBadge(report);
        });
    }

    private void updateSentinelBadge(VaultHealthReport report) {
        if (sentinelHealthBadge == null || report == null) return;
        int score = report.getHealthScore();
        String color;
        if (score >= 80)      color = "#2ecc71";
        else if (score >= 60) color = "#27ae60";
        else if (score >= 40) color = "#f1c40f";
        else if (score >= 20) color = "#e67e22";
        else                  color = "#e74c3c";

        sentinelHealthBadge.setText("\uD83D\uDEE1 " + score + "/100");
        sentinelHealthBadge.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + color + "; -fx-cursor: hand;");
    }

    @FXML
    private void handlePasswordCoach() {
        SecretEntry selected = tableEntries.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UIUtils.showToast(tableEntries, I18n.t("coach.select_entry"));
            return;
        }

        if (selected.getPassword() == null || selected.getPassword().length == 0) {
            UIUtils.showToast(tableEntries, I18n.t("coach.no_password"));
            return;
        }

        // Find the SentinelReport for this entry
        VaultHealthReport report = sentinelEngine.getLastReport();
        if (report == null) {
            report = sentinelEngine.analyze(VaultRepository.VAULT_CONTEXT.getVault());
        }

        PasswordScore score = null;
        int ageDays = 0;
        for (SentinelReport sr : report.getEntryReports()) {
            if (sr.getEntryId().equals(selected.getId())) {
                score = sr.getPasswordScore();
                ageDays = sr.getPasswordAgeDays();
                break;
            }
        }

        if (score == null) {
            // Entry might have empty password or not yet analyzed
            score = sentinelEngine.scoreSingle(selected, false);
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/password_coach.fxml"),
                    I18n.getBundle()
            );
            Parent root = loader.load();
            PasswordCoachController ctrl = loader.getController();
            ctrl.populate(score, ageDays);

            Stage stage = new Stage();
            stage.setTitle(I18n.t("coach.title"));
            stage.setScene(new Scene(root));
            stage.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
            ));
            stage.getScene().getStylesheets().add(
                    Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
            );
            stage.initOwner(tableEntries.getScene().getWindow());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(true);
            stage.setMinWidth(740);
            stage.setMinHeight(480);
            stage.addEventFilter(MouseEvent.ANY, e -> resetInactivityTimer());
            stage.addEventFilter(KeyEvent.ANY, e -> resetInactivityTimer());
            stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.L) {
                    lockScreen();
                    event.consume();
                }
            });
            stage.showAndWait();
        } catch (Exception e) {
            SecureLogger.error(e.getMessage(), e);
        }
    }

    @FXML
    private void handleBreachChecker() {
        if (VaultRepository.VAULT_CONTEXT.getVault() == null) return;

        List<SecretEntry> entries = VaultRepository.VAULT_CONTEXT.getVault().getEntries().stream()
                .filter(e -> e.getPassword() != null && e.getPassword().length > 0)
                .toList();

        if (entries.isEmpty()) {
            UIUtils.showToast(tableEntries, I18n.t("breach.no_entries"));
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/breach_checker.fxml"),
                    I18n.getBundle()
            );
            Parent root = loader.load();
            BreachCheckerController ctrl = loader.getController();
            ctrl.setEntries(entries);

            Stage stage = new Stage();
            stage.setTitle(I18n.t("breach.title"));
            stage.setScene(new Scene(root));
            stage.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
            ));
            stage.getScene().getStylesheets().add(
                    Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
            );
            stage.initOwner(tableEntries.getScene().getWindow());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(true);
            stage.setMinWidth(740);
            stage.setMinHeight(480);
            stage.addEventFilter(MouseEvent.ANY, e -> resetInactivityTimer());
            stage.addEventFilter(KeyEvent.ANY, e -> resetInactivityTimer());
            stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.L) {
                    lockScreen();
                    event.consume();
                }
            });
            stage.showAndWait();
        } catch (Exception e) {
            SecureLogger.error(e.getMessage(), e);
        }
    }

    @FXML
    private void handleSentinelDashboard() {
        VaultHealthReport report = sentinelEngine.getLastReport();
        if (report == null) {
            // run synchronous if no report yet
            report = sentinelEngine.analyze(VaultRepository.VAULT_CONTEXT.getVault());
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/sentinel_dashboard.fxml"),
                    I18n.getBundle()
            );
            Parent root = loader.load();
            SentinelDashboardController ctrl = loader.getController();
            ctrl.setMainController(this);
            ctrl.populate(report, sentinelEngine.getRotationPlan());

            Stage stage = new Stage();
            stage.setTitle(I18n.t("sentinel.dashboard.title"));
            stage.setScene(new Scene(root));
            stage.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
            ));
            stage.getScene().getStylesheets().add(
                    Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
            );
            stage.initOwner(tableEntries.getScene().getWindow());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(true);
            stage.setMinWidth(700);
            stage.setMinHeight(500);
            stage.addEventFilter(MouseEvent.ANY, e -> resetInactivityTimer());
            stage.addEventFilter(KeyEvent.ANY, e -> resetInactivityTimer());
            stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.L) {
                    lockScreen();
                    event.consume();
                }
            });
            stage.showAndWait();
        } catch (Exception e) {
            SecureLogger.error(e.getMessage(), e);
        }
    }

   static Locale loadLanguagePreference() {
        try {
            if (Files.exists(LANG_PREF)) {
                String lang = Files.readString(LANG_PREF).trim();
                return new Locale(lang);
            }
        } catch (Exception exception) {
            SecureLogger.error(exception.getMessage(),exception);
        }
        return Locale.getDefault();
    }

    // ========== SSH CONNECTION MANAGER ==========

    private void loadSshConnections() {
        List<SshConnection> connections = VaultRepository.VAULT_CONTEXT.getVault().getSshConnections();
        if (connections == null) {
            VaultRepository.VAULT_CONTEXT.getVault().setSshConnections(new ArrayList<>());
            connections = VaultRepository.VAULT_CONTEXT.getVault().getSshConnections();
        }
        fullSshConnections.setAll(connections);
    }

    private String buildSshCommand(SshConnection conn, Path keyPath) {
        StringBuilder cmd = new StringBuilder("ssh");
        if (keyPath != null) {
            cmd.append(" -i \"").append(keyPath.toAbsolutePath()).append("\"");
        } else if (conn.getKeyFileName() != null) {
            cmd.append(" -i <").append(conn.getKeyFileName()).append(">");
        }
        if (conn.getPort() != 22) {
            cmd.append(" -p ").append(conn.getPort());
        }
        if (conn.getUsername() != null && !conn.getUsername().isBlank()) {
            cmd.append(" ").append(conn.getUsername()).append("@").append(conn.getHost());
        } else {
            cmd.append(" ").append(conn.getHost());
        }
        return cmd.toString();
    }

    @FXML
    private void handleSshAdd() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/ssh_connection.fxml"),
                    I18n.getBundle());

            Stage dialog = new Stage();
            dialog.setScene(new Scene(loader.load()));
            dialog.setTitle(I18n.t("ssh.dialog.add.title"));

            dialog.initOwner(sshTable.getScene().getWindow());
            dialog.setResizable(true);
            dialog.getScene().getStylesheets().add(
                    Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
            );
            dialog.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
            ));

            SshConnectionController ctrl = loader.getController();
            ctrl.setMainController(this);

            dialog.addEventFilter(MouseEvent.ANY, e -> resetInactivityTimer());
            dialog.addEventFilter(KeyEvent.ANY, e -> resetInactivityTimer());
            dialog.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.isControlDown() && e.getCode() == KeyCode.L) {
                    lockScreen();
                    e.consume();
                }
            });
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();

            if (ctrl.isSaved()) {
                VaultRepository.VAULT_CONTEXT.getVault().getSshConnections().add(ctrl.getConnection());
                runSaveVaultWithProgress();
                loadSshConnections();
            }

        } catch (Exception e) {
            SecureLogger.error(e.getMessage(), e);
            UIUtils.showError(I18n.t("ssh.error.save"));
        }
    }

    @FXML
    private void handleSshEdit() {
        SshConnection selected = sshTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/ssh_connection.fxml"),
                    I18n.getBundle());

            Stage dialog = new Stage();
            dialog.setScene(new Scene(loader.load()));
            dialog.setTitle(I18n.t("ssh.dialog.edit.title"));

            dialog.initOwner(sshTable.getScene().getWindow());
            dialog.setResizable(true);
            dialog.getScene().getStylesheets().add(
                    Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
            );
            dialog.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
            ));

            SshConnectionController ctrl = loader.getController();
            ctrl.setMainController(this);
            ctrl.setConnection(selected);

            dialog.addEventFilter(MouseEvent.ANY, e -> resetInactivityTimer());
            dialog.addEventFilter(KeyEvent.ANY, e -> resetInactivityTimer());
            dialog.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.isControlDown() && e.getCode() == KeyCode.L) {
                    lockScreen();
                    e.consume();
                }
            });
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();

            if (ctrl.isSaved()) {
                runSaveVaultWithProgress();
                loadSshConnections();
            }

        } catch (Exception e) {
            SecureLogger.error(e.getMessage(), e);
            UIUtils.showError(I18n.t("ssh.error.save"));
        }
    }

    @FXML
    private void handleSshDelete() {
        SshConnection selected = sshTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.t("ssh.delete.confirm"),
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle(I18n.t("ssh.delete.title"));
        confirm.setHeaderText(I18n.t("ssh.delete.header"));
        confirm.initOwner(sshTable.getScene().getWindow());
        confirm.getDialogPane().setMinWidth(650);
        confirm.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(App.class.getResource("/css/onecritto-theme.css")).toExternalForm()
        );

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                VaultRepository.VAULT_CONTEXT.getVault().getSshConnections().remove(selected);
                runSaveVaultWithProgress();
                loadSshConnections();
            }
        });
    }

    @FXML
    private void handleSshConnect() {
        SshConnection selected = sshTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (selected.getKeyFileId() == null) {
            // No key file — open terminal with plain SSH command
            launchTerminalWithCommand(buildSshCommand(selected, null));
            return;
        }

        // Find the SecretFile for the key
        SecretFile keyFile = null;
        for (SecretFile sf : VaultRepository.VAULT_CONTEXT.getVault().getFiles()) {
            if (sf.getId().equals(selected.getKeyFileId())) {
                keyFile = sf;
                break;
            }
        }

        if (keyFile == null) {
            UIUtils.showError(I18n.t("ssh.error.key.notfound"));
            return;
        }

        if (acquireVaultLock(I18n.t("main.busy.operation"))) return;

        final SecretFile sf = keyFile;
        final SshConnection conn = selected;

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                Path temp = TempVaultFiles.createTempFileForOpen(sf.getName());
                SecretKey key = VaultRepository.VAULT_CONTEXT.getKeyEnc();

                decryptBlobToTemp(key, sf, temp, sf.getSize());

                // Set restrictive permissions on the key file (ssh requires 600)
                try {
                    temp.toFile().setReadable(false, false);
                    temp.toFile().setReadable(true, true);
                    temp.toFile().setWritable(false, false);
                    temp.toFile().setWritable(true, true);
                } catch (Exception ignored) {}

                return temp;
            }
        };

        task.setOnSucceeded(e -> {
            releaseVaultLock();
            Path keyPath = task.getValue();
            String command = buildSshCommand(conn, keyPath);
            launchTerminalWithCommand(command);
            updateTempFileCount();
        });

        task.setOnFailed(e -> {
            releaseVaultLock();
            UIUtils.showError(I18n.t("ssh.error.decrypt"));
            if (task.getException() != null) {
                SecureLogger.error(task.getException().getMessage(), task.getException());
            }
        });

        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();
    }

    private void launchTerminalWithCommand(String command) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                // Windows: open cmd.exe with the SSH command
                pb = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", command);
            } else if (os.contains("mac")) {
                // macOS: open Terminal.app
                pb = new ProcessBuilder("osascript", "-e",
                        "tell application \"Terminal\" to do script \"" + command.replace("\"", "\\\"") + "\"");
            } else {
                // Linux: try common terminal emulators
                String terminal = findLinuxTerminal();
                if (terminal != null) {
                    pb = new ProcessBuilder(terminal, "-e", command);
                } else {
                    // Fallback: xterm
                    pb = new ProcessBuilder("xterm", "-e", command);
                }
            }
            pb.start();
        } catch (Exception e) {
            SecureLogger.error("Failed to launch terminal: " + e.getMessage(), e);
            UIUtils.showError(I18n.t("ssh.error.terminal"));
        }
    }

    private String findLinuxTerminal() {
        String[] terminals = {"gnome-terminal", "konsole", "xfce4-terminal", "mate-terminal", "xterm"};
        for (String t : terminals) {
            try {
                Process p = new ProcessBuilder("which", t).start();
                if (p.waitFor() == 0) {
                    return t;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

   
}
