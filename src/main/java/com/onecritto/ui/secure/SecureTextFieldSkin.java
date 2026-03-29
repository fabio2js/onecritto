package com.onecritto.ui.secure;

import com.onecritto.i18n.I18n;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.Locale;

public class SecureTextFieldSkin extends SkinBase<SecureTextField> {

    private static final int COPY_TIMEOUT_SECONDS = 20;

    private static final int MAX_CHARS = 1000;

    private final Label textLabel = new Label();
    private final ScrollPane scrollPane = new ScrollPane();
    private final Rectangle caret = new Rectangle(1.5, 18, Color.WHITE);

    private final Button btnReveal = new Button();
    private final Button btnCopy = new Button();
    private final Button btnPaste = new Button();

    private final StackPane textPane = new StackPane();
    private final Label countdownLabel = new Label();

    private Timeline copyCountdown;
    private int secondsRemaining;


    public SecureTextFieldSkin(SecureTextField control) {
        super(control);

         btnReveal.getStyleClass().add("small-button");
         btnCopy.getStyleClass().add("small-button");
         btnPaste.getStyleClass().add("small-button");

        // alla fine della costruzione (dopo btnReveal/btnCopy/btnPaste e countdownRow)
        if (control.isLoginMode()) {
            // nascondi/elimini i bottoni copy/paste
            btnCopy.setVisible(false);
            btnCopy.setManaged(false);
            control.setReveal(false);
            btnPaste.setVisible(false);
            btnPaste.setManaged(false);

        }



        textPane.setMinHeight(38);
        scrollPane.setMinHeight(38);

        textLabel.setPadding(new Insets(8, 10, 8, 10));
        textLabel.setFont(Font.font(14));
        textLabel.setWrapText(false);
        textLabel.setBackground(Background.EMPTY);
        textLabel.setBorder(Border.EMPTY);

        scrollPane.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-control-inner-background: transparent;"
        );
        textPane.setStyle(

                        "-fx-control-inner-background: transparent;"
        );
        textPane.getStyleClass().add("secure-field");

        // SCROLLPANE
        scrollPane.setContent(textLabel);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPannable(false);
        scrollPane.setFitToHeight(true);
        scrollPane.setMinHeight(38);
        scrollPane.setBackground(Background.EMPTY);
        scrollPane.setBorder(Border.EMPTY);

        // CARET
        caret.setVisible(false);
        caret.setManaged(false);
        // TEXTPANE (superficie interattiva)
        textPane.getChildren().addAll(scrollPane, caret);
        //StackPane.setAlignment(caret, Pos.CENTER_LEFT);

// il caret parte dall’angolo in alto a sinistra del textPane
        StackPane.setAlignment(caret, Pos.TOP_LEFT);

        textPane.setMinHeight(38);
        scrollPane.setMinHeight(38);

        // BOTTONI
        makeIcon(btnReveal, FontAwesomeIcon.EYE);
        makeIcon(btnCopy, FontAwesomeIcon.COPY);
        makeIcon(btnPaste, FontAwesomeIcon.PASTE);

        HBox buttonBar = new HBox(8, btnReveal, btnCopy, btnPaste);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setStyle("-fx-background-color: transparent;");
        buttonBar.setBackground(Background.EMPTY);
        Label titleLabel = new Label();
        titleLabel.textProperty().bind(control.titleProperty());
        titleLabel.setPadding(new Insets(0, 0, 2, 2));
        titleLabel.setBackground(Background.EMPTY);
        titleLabel.setStyle("-fx-background-color: transparent;");
        StackPane wrapper = new StackPane(textPane);
        wrapper.setPadding(new Insets(0));
        wrapper.setBorder(Border.EMPTY);
        HBox.setHgrow(wrapper, Priority.ALWAYS);
        wrapper.setMaxWidth(Double.MAX_VALUE);


        HBox topRow = new HBox(8, wrapper, buttonBar);
        topRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(topRow, Priority.ALWAYS);
        topRow.setAlignment(Pos.CENTER_LEFT);

       // textLabel.getStyleClass().add("secure-field");
        Label maxCharsLabel = new Label("Max 1000 chars");
        maxCharsLabel.getStyleClass().add("helper-text");

        if (control.isLoginMode()){
            maxCharsLabel.setText("");
        }


        HBox leftBox = new HBox(maxCharsLabel);
        leftBox.setAlignment(Pos.CENTER_LEFT);

        HBox rightBox = new HBox( topRow);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        VBox actions = new VBox(rightBox,leftBox);

        actions.setPadding(new Insets(4));

// NIENTE cartellone → solo topRow trasparente
        topRow.setBackground(Background.EMPTY);
        topRow.setBorder(Border.EMPTY);
        HBox.setHgrow(textPane, Priority.ALWAYS);

        // COUNTDOWN

        countdownLabel.setMinWidth(80);
        countdownLabel.getStyleClass().add("countdown-label-paste");


        Region spacer = new Region();
        HBox countdownRow = new HBox(8, spacer, countdownLabel);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        countdownRow.setAlignment(Pos.CENTER_RIGHT);
        countdownRow.setPadding(new Insets(0, 4, 0, 4));

        // ROOT
        VBox root = new VBox(4,
                titleLabel,   // <-- titolo sopra
                actions,
                countdownRow
        );
        root.setPadding(new Insets(2));
        getChildren().add(root);

        // Focus: il vero nodo "tastiera" è textPane
        control.setFocusTraversable(false);
        textPane.setFocusTraversable(true);
        scrollPane.setFocusTraversable(false);
        textLabel.setFocusTraversable(true);

         installHandlers(control);

        // LABEL

        textLabel.setFont(Font.font(14));
        textLabel.setWrapText(false);
        textLabel.setEllipsisString(null);      // <--- rimuove i "..."

        textLabel.setMaxWidth(Double.MAX_VALUE);

// SCROLLPANE
        scrollPane.setContent(textLabel);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);         // <--- importantissimo
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPannable(false);


        scrollPane.setBorder(Border.EMPTY);
        scrollPane.setBackground(Background.EMPTY);
        scrollPane.setPadding(Insets.EMPTY);



        updateText();

        // CARET blink basato su focus di textPane
        caretBlink = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(caret.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.seconds(0.43),
                        new KeyValue(caret.opacityProperty(), 0.0, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(Duration.seconds(0.85),
                        new KeyValue(caret.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
                )
        );
        caretBlink.setCycleCount(Animation.INDEFINITE);
        caretBlink.play();


        getSkinnable().focusedProperty().addListener((obs, oldV, focused) -> {
            if (focused) {
                caret.setOpacity(1);
                caretBlink.play();
            } else {
                caretBlink.stop();
                caret.setOpacity(0);
            }
        });
        getSkinnable().setOnKeyTyped(ev -> restartCaretBlink());
        getSkinnable().setOnMouseClicked(ev -> restartCaretBlink());

        if (!control.isLoginMode() && !control.isPwdField) {
            control.setReveal(true);
            btnReveal.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.EYE_SLASH));
            Platform.runLater(this::updateText);
        }
    }
    private Timeline caretBlink;

    private void restartCaretBlink() {
        caret.setOpacity(1);
        caretBlink.stop();
        caretBlink.playFromStart();
    }
    private void makeIcon(Button b, FontAwesomeIcon ico) {
        FontAwesomeIconView v = new FontAwesomeIconView(ico);
        v.setGlyphSize(14);
        b.setGraphic(v);
        b.getStyleClass().add("small-button");
        b.setFocusTraversable(false);
        b.setText(null);
    }
    public void clearUi() {
        textLabel.setText("");
        caret.setTranslateX(4);
        scrollPane.setHvalue(0);
    }
    // ------------------------------------------------------------
    // EVENTI
    // ------------------------------------------------------------
    private void installHandlers(SecureTextField control) {

        // CLICK → focus su textPane (e implicitamente sul controllo)
        textPane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            textPane.requestFocus();
            e.consume();
        });
        textLabel.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            textPane.requestFocus();
            e.consume();
        });

        // Quando textPane ha focus, consideriamo il controllo "focused"
        textPane.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                showCaret();
            } else {
                caret.setVisible(false);
            }
        });

        // BLOCCA gestione frecce da parte dello ScrollPane
        scrollPane.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode().isArrowKey()) {
                e.consume();
            }
        });

        // INPUT LOGICO: SOLO su textPane
        // 1) KEY_TYPED: caratteri normali + DELETE (ASCII 127)
        textPane.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            String s = e.getCharacter();
            if (s == null || s.isEmpty()) return;

            char c = s.charAt(0);

            // DELETE: come in SecureTextArea
            if (c == 127) {  // ASCII DEL
                control.delete();
                updateText();
                updateCaretAndScroll();
                e.consume();
                return;
            }

            if (c >= 32 || c == '\n') {
                if(control.getRenderedClearText().length()<MAX_CHARS) {
                    control.append(c);
                    updateText();
                }
                e.consume();
            }
        });

        // 2) KEY_PRESSED: frecce + backspace + ctrl+V
        textPane.addEventFilter(KeyEvent.KEY_PRESSED, e -> {

            // FRECCIA SINISTRA
            if (e.getCode() == KeyCode.LEFT) {
                if (control.cursorPos > 0) {
                    control.cursorPos--;
                    updateCaretAndScroll();
                }
                e.consume();
                return;
            }

            // FRECCIA DESTRA
            if (e.getCode() == KeyCode.RIGHT) {
                if (control.uiValue != null && control.cursorPos < control.uiValue.length) {
                    control.cursorPos++;
                    updateCaretAndScroll();
                }
                e.consume();
                return;
            }

            // BACKSPACE
            if (e.getCode() == KeyCode.BACK_SPACE) {
                control.backspace();
                updateText();
                updateCaretAndScroll();
                e.consume();
                return;
            }

            // ENTER (opzionale: se non lo vuoi, puoi toglierlo)
            if (e.getCode() == KeyCode.ENTER) {
                e.consume(); // non permettiamo newline nel field
                return;
            }

            // CTRL+V (paste)
            if (e.isControlDown() && e.getCode() == KeyCode.V) {
                control.pasteFromClipboard();
                updateText();
                updateCaretAndScroll();
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
            Platform.runLater(this::showCaret);
        });

        // COPY
        btnCopy.setOnAction(e -> {
            control.recoverFromUiShadow();
            char[] data = control.getValue();
            control.copyToClipboard(data);
            control.wipe(data);

            startCountdown(control);
            textPane.requestFocus();
        });

        // PASTE
        btnPaste.setOnAction(e -> {
            control.pasteFromClipboard();
            updateText();
            Platform.runLater(this::showCaret);
        });

        // wipe callback (quando scade il timeout)
        control.setOnWipeRequest(() -> {
            scrollPane.setHvalue(0);
            caret.setTranslateX(4);
        });
    }

    // ------------------------------------------------------------
    // UPDATE TESTO + CARET
    // ------------------------------------------------------------
    // ------------------------------------------------------------
// UPDATE TESTO + CARET
// ------------------------------------------------------------
    public void updateText() {     // ← DIVENTA public
        SecureTextField c = getSkinnable();

        String display = c.isReveal()
                ? c.getRenderedClearText()
                : c.getRenderedMaskedText();

        textLabel.setText(display);
        updateCaretAndScroll();

        if (c.getValue().length == 0) {
            textLabel.setText("");
            caret.setTranslateX(4);
            scrollPane.setHvalue(0);
        }
    }

    private void updateCaretAndScroll() {
        SecureTextField c = getSkinnable();

        String display = c.isReveal()
                ? c.getRenderedClearText()
                : c.getRenderedMaskedText();

        if (display == null) display = "";

        double textWidth = computeTextWidth(textLabel.getFont(), display);

        double viewportW = scrollPane.getViewportBounds().getWidth();
        if (viewportW <= 0) viewportW = scrollPane.getWidth() - 4;

        double labelW = Math.max(textWidth + 4, viewportW);
        textLabel.setPrefWidth(labelW);

        double maxOffset = Math.max(0, labelW - viewportW);

        int caretIndex = Math.max(0, Math.min(c.cursorPos, display.length()));

        // --- OFFSET PERFETTO ---
        double CARET_OFFSET = 1.8;

        Insets pad = textLabel.getPadding();
        double paddingLeft = pad != null ? pad.getLeft() : 0;

        double caretX = paddingLeft + computeTextWidth(
                textLabel.getFont(),
                display.substring(0, caretIndex)
        ) + CARET_OFFSET;

        double currentOffset = scrollPane.getHvalue() * maxOffset;

        if (caretX > currentOffset + viewportW - 16) {
            double target = Math.min(caretX - (viewportW - 16), maxOffset);
            scrollPane.setHvalue(maxOffset == 0 ? 0 : target / maxOffset);
            currentOffset = target;
        } else if (caretX < currentOffset + 4) {
            double target = Math.max(caretX - 4, 0);
            scrollPane.setHvalue(maxOffset == 0 ? 0 : target / maxOffset);
            currentOffset = target;
        }

        caret.setTranslateX(caretX - currentOffset);

        double baseline = textLabel.getBaselineOffset();
        caret.setTranslateY(baseline - (caret.getHeight()*0.5));
    }



    private void showCaret() {
        caret.setVisible(true);
        updateCaretAndScroll();
    }

    private double computeTextWidth(Font font, String text) {
        if (text == null || text.isEmpty()) return 0;
        Text helper = new Text(text);
        helper.setFont(font);
        return helper.getLayoutBounds().getWidth();
    }

    // ------------------------------------------------------------
    // COUNTDOWN COPY
    // ------------------------------------------------------------
    private void startCountdown(SecureTextField c) {
        secondsRemaining = COPY_TIMEOUT_SECONDS;


        Locale current = I18n.getCurrentLocale();

        String expires;

        // Evidenzia la lingua selezionata
        if ("it".equals(current.getLanguage())) {
            expires="Scade tra: " ;
        }else{
            expires = "Time left: ";
        }






        countdownLabel.setText(expires + secondsRemaining + "s");

        if (copyCountdown != null) {
            copyCountdown.stop();
        }

        copyCountdown = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    secondsRemaining--;

                    if (secondsRemaining > 0) {
                        countdownLabel.setText(expires + secondsRemaining + "s");
                    } else {

                        countdownLabel.setText("");



// se vuoi, puoi cancellare clipboard Windows SOLO se il copy l'ha scritta:
                        c.clearWindowsClipboardIfOwned(); // rendilo pubblico o crea un wrapper

                        copyCountdown.stop();


                    }
                })
        );
        copyCountdown.setCycleCount(COPY_TIMEOUT_SECONDS);
        copyCountdown.playFromStart();
    }
}
