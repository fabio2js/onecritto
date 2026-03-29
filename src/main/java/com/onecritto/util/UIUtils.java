package com.onecritto.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Objects;

public class UIUtils {



    public static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);

        DialogPane pane = alert.getDialogPane();

        // 1) aggancia il CSS principale di OneCritto
        pane.getStylesheets().add(
                Objects.requireNonNull(UIUtils.class.getResource("/css/onecritto-theme.css")).toExternalForm()
        );

         pane.getStyleClass().add("onecritto-alert");

        Stage stage = (Stage) pane.getScene().getWindow();
        stage.getIcons().add(new Image(
                Objects.requireNonNull(UIUtils.class.getResourceAsStream("/icons/onecritto_white_key_32x32.png"))
        ));

        alert.showAndWait();
    }

    public static String humanReadableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), unit);
    }


    public static void showToast(Node anchorNode, String message) {

        // Recupera lo Stage partendo da un nodo qualsiasi
        Scene scene = anchorNode.getScene();
        Stage stage = (Stage) scene.getWindow();
        Parent root = scene.getRoot();

        // Se il root non è uno StackPane, lo wrappiamo
        if (!(root instanceof StackPane)) {
            StackPane stack = new StackPane();
            stack.getChildren().add(root);
            scene.setRoot(stack);
            root = stack;
        }

        StackPane stackRoot = (StackPane) root;

        Label toast = new Label(message);
        toast.setStyle("-fx-background-color: #323232; -fx-text-fill: white; "
                + "-fx-padding: 10; -fx-background-radius: 5;");
        toast.setOpacity(0);

        stackRoot.getChildren().add(toast);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), toast);
        fadeIn.setToValue(1);

        PauseTransition delay = new PauseTransition(Duration.seconds(2));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), toast);
        fadeOut.setToValue(0);

        fadeIn.setOnFinished(e -> delay.play());
        delay.setOnFinished(e -> fadeOut.play());
        fadeOut.setOnFinished(e -> stackRoot.getChildren().remove(toast));

        fadeIn.play();
    }

}
