package com.onecritto.ui.secure;

import com.onecritto.i18n.I18n;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.Arrays;
import java.util.Locale;

public class SecureTextAreaSkin extends SkinBase<SecureTextArea> {

    private static final int MAX_CHARS = 5000;
    private static final int TIMEOUT = 20;

    private final TextArea textArea = new TextArea();

    private final Button btnReveal = new Button();
    private final Button btnCopy   = new Button();
    private final Button btnPaste  = new Button();

    private final Label countdownLabel = new Label();
    private Timeline countdown;
    private int secondsRemaining;

    private boolean updatingFromModel = false;





    public SecureTextAreaSkin(SecureTextArea control) {
        super(control);
// TITLE LABEL
        Label titleLabel = new Label();
        titleLabel.textProperty().bind(control.titleProperty());

        titleLabel.setPadding(new Insets(0, 0, 2, 2));
        // alla fine della costruzione (dopo btnReveal/btnCopy/btnPaste e countdownRow)
        if (control.isLoginMode()) {
            // nascondi/elimini i bottoni copy/paste
            btnCopy.setVisible(false);
            btnCopy.setManaged(false);
            control.setReveal(false);
            btnPaste.setVisible(false);
            btnPaste.setManaged(false);

        }
        countdownLabel.getStyleClass().add("countdown-label-paste");



        // ---------------- TEXTAREA ----------------
        textArea.setWrapText(false);
        textArea.setPrefRowCount(5);
        textArea.setContextMenu(new ContextMenu());
        textArea.setEditable(true);
        textArea.setFocusTraversable(true);

        StackPane textContainer = new StackPane(textArea);

        textContainer.setFocusTraversable(false);

        textArea.getStyleClass().add("secure-textarea");
   //     textContainer.getStyleClass().add("secure-textarea");
        textContainer.setPadding(Insets.EMPTY);
        textContainer.setBackground(Background.EMPTY);
        textContainer.setBorder(Border.EMPTY);


        // ---------------- BOTTONI ----------------
        makeIcon(btnReveal, FontAwesomeIcon.EYE);
        makeIcon(btnCopy,   FontAwesomeIcon.COPY);
        makeIcon(btnPaste,  FontAwesomeIcon.PASTE);
        Button btnDelete = new Button();
        makeIcon(btnDelete, FontAwesomeIcon.TRASH);


        // ---------------- BOTTOM ----------------
        Label maxCharsLabel = new Label("Max 5000 chars");
        maxCharsLabel.getStyleClass().add("helper-text");




        HBox leftBox = new HBox(maxCharsLabel);
        leftBox.setAlignment(Pos.CENTER_LEFT);

        HBox rightBox = new HBox(8, btnReveal, btnCopy, btnPaste, btnDelete);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        BorderPane actions = new BorderPane();
        actions.setLeft(leftBox);
        actions.setRight(rightBox);
        actions.setPadding(new Insets(4));


        countdownLabel.setMinWidth(80);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bottom = new HBox(8, spacer, countdownLabel);
        bottom.setPadding(new Insets(0, 4, 4, 4));
        bottom.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(
                4,
                titleLabel,        // <---- NUOVO titolo sopra
                textContainer,
                actions,
                bottom
        );
        root.setPadding(new Insets(4));
        root.setPadding(Insets.EMPTY);
        root.setBackground(Background.EMPTY);
        root.setBorder(Border.EMPTY);









        // DELETE button -> svuota tutto
        btnDelete.setOnAction(e -> {
            control.wipe(control.buffer);
            control.buffer = new char[0];

            control.wipe(control.uiValue);
            control.uiValue = new char[0];

            control.renderedShadow = "";
            control.cursorPos = 0;

            updateText();
            control.requestFocus();
        });

        getChildren().add(root);

        installHandlers(control);
        updateText();


        if (!control.isLoginMode()) {
            control.setReveal(true);
            btnReveal.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.EYE_SLASH));
             Platform.runLater(this::updateText);
        }


    }

    private void makeIcon(Button b, FontAwesomeIcon ico) {
        FontAwesomeIconView v = new FontAwesomeIconView(ico);
        v.setGlyphSize(14);
        b.setGraphic(v);
        b.getStyleClass().setAll("button", "small-button");

        b.setFocusTraversable(false);
        b.setText(null);
    }

    // ------------------------------------------------------------
    // EVENTI
    // ------------------------------------------------------------
    private void installHandlers(SecureTextArea control) {

        // Sync caret → cursorPos
        textArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (updatingFromModel) return;
            control.cursorPos = newPos.intValue();
        });

        // Focus
        control.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                Platform.runLater(textArea::requestFocus);
            }
        });

        // Niente selezione
       /* textArea.selectionProperty().addListener((obs, oldSel, newSel) ->
                Platform.runLater(textArea::deselect)
        ); */

        // BLOCCA gestione di DELETE e Ctrl+A/C/X sulla TextArea
        textArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {

            if (e.getCode() == KeyCode.DELETE) {
                // fermiamo completamente il comportamento default della TextArea
                e.consume();
                return;
            }

            if (e.isControlDown()) {
                if (e.getCode() == KeyCode.A ||
                        e.getCode() == KeyCode.C ||
                        e.getCode() == KeyCode.X) {
                    e.consume();
                }
            }
        });

        // niente doppio click
        textArea.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getClickCount() > 1) {
                e.consume();
            }
        });

        textArea.setOnMouseClicked(e -> control.requestFocus());




        control.addEventFilter(KeyEvent.KEY_TYPED, e -> {

            String ch = e.getCharacter();
            if (ch == null || ch.isEmpty()) return;

            // CANCELLAZIONE → KEY_TYPED PER DELETE
            if (ch.charAt(0) == '\u007F') { // DELETE generato come carattere ASCII 127
                control.delete();
                updateText();
                e.consume();
                return;
            }

            char c = ch.charAt(0);

            if (c >= 32 || c == '\n') {
                if (control.getRenderedClearText().length() < MAX_CHARS) {
                    control.append(c);
                    updateText();
                }
                e.consume();
            }
        });



        control.addEventFilter(KeyEvent.KEY_PRESSED, e -> {

            if (e.getCode() == KeyCode.BACK_SPACE) {
                control.backspace();
                updateText();
                e.consume();
            }



            if (e.getCode() == KeyCode.ENTER) {
                if (control.getRenderedClearText().length() < MAX_CHARS) {
                    control.append('\n');
                    updateText();
                }
                e.consume();
            }

            if (e.isControlDown() && e.getCode() == KeyCode.V) {
                control.pasteFromClipboard();
                updateText();
                e.consume();
            }
        });

        // REVEAL
        btnReveal.setOnAction(e -> {
            control.setReveal(!control.isReveal());
            btnReveal.setGraphic(new FontAwesomeIconView(
                    control.isReveal() ? FontAwesomeIcon.EYE_SLASH : FontAwesomeIcon.EYE
            ));
            updateText();
            control.requestFocus();
        });

        // COPY
        // COPY SOLO TESTO SELEZIONATO
        btnCopy.setOnAction(e -> {
            SecureTextArea c = getSkinnable();

            int start = textArea.getSelection().getStart();
            int end   = textArea.getSelection().getEnd();

            // Nessuna selezione → non copiare
            if (start == end) {
                c.requestFocus();
                return;
            }

            // Aggiorniamo il buffer sicuro se era desincronizzato
            c.recoverFromUiShadow();

            char[] value = c.getValue();
            int len = value.length;
            if (start < 0 || end > len) {
                c.requestFocus();
                return;
            }

            char[] slice = Arrays.copyOfRange(value, start, end);

            // Copia nella clipboard Windows
            c.copyToClipboard(slice);

            // Wipe della slice temporanea
            c.wipe(slice);

            startCountdown(c);
            c.requestFocus();
        });

        // PASTE
        btnPaste.setOnAction(e -> {
            control.pasteFromClipboard();
            updateText();
            control.requestFocus();
        });
    }

    // ------------------------------------------------------------
    // UPDATE TEXT + CARET
    // ------------------------------------------------------------
    private void updateText() {
        SecureTextArea c = getSkinnable();

        String display = c.isReveal()
                ? c.getRenderedClearText()
                : c.getRenderedMaskedText();

        int caretPos = c.getCursorPos();

        updatingFromModel = true;
        textArea.setText(display);

        final int safePos = Math.min(Math.max(0, caretPos), display.length());
        Platform.runLater(() -> {
            textArea.positionCaret(safePos);
            updatingFromModel = false;
        });
    }

    // ------------------------------------------------------------
    // COUNTDOWN
    // ------------------------------------------------------------
    private void startCountdown(SecureTextArea c) {
        secondsRemaining = TIMEOUT;
        Locale current = I18n.getCurrentLocale();

        String expires;

        // Evidenzia la lingua selezionata
        if ("it".equals(current.getLanguage())) {
                  expires="Scade tra " ;
        }else{
                expires = "Time left ";
        }


        countdownLabel.setText(expires + secondsRemaining + "s");

        if (countdown != null) countdown.stop();

        countdown = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    secondsRemaining--;

                    if (secondsRemaining > 0) {
                        countdownLabel.setText(expires + secondsRemaining + "s");
                    } else {
                        countdownLabel.setText("");


// se vuoi, puoi cancellare clipboard Windows SOLO se il copy l'ha scritta:
                        c.clearWindowsClipboardIfOwned(); // rendilo pubblico o crea un wrapper

// NON cancellare il buffer e NON toccare la UI
                        countdown.stop();
                    }
                })
        );
        countdown.setCycleCount(TIMEOUT);
        countdown.playFromStart();
    }
}
