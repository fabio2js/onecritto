package com.onecritto.ui;

import com.onecritto.App;
import com.onecritto.i18n.I18n;
import com.onecritto.persistence.VaultRepository;
import com.onecritto.security.TempVaultFiles;
import com.onecritto.util.SecureLogger;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

public class MainApp extends Application {

    private static  HostServices hostServices;







    public static   HostServices getAppHostServices() {
        return hostServices;
    }





    private static boolean isEnvTrue(String name) {
        String v = System.getenv(name);
        return v != null && (v.equalsIgnoreCase("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes"));
    }



    @Override
    public void start(Stage stage) throws Exception {
        // Leggo la lingua salvata dall’utente
        Locale pref = MainController.loadLanguagePreference();
        I18n.setLocale(pref);

        SecureLogger.initDebugMode(
                Boolean.getBoolean("onecritto.debug") ||                 // se jpackage passa davvero -D...
                        isEnvTrue("ONECRITTO_DEBUG") ||                         // metodo più affidabile con .exe
                        Files.exists(Paths.get(System.getProperty("user.home"), ".onecritto", "debug.cfg")));

        System.out.println("onecritto.debug:" + SecureLogger.isDebugMode());

        if(SecureLogger.isDebugMode() ) {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                e.printStackTrace();
            });

            System.setErr(new PrintStream(new FileOutputStream("onecritto_error.log", true)));
            System.setOut(new PrintStream(new FileOutputStream("onecritto_info.log", true)));
        }


        hostServices = getHostServices();
        TempVaultFiles.cleanupTempDirSecure();
        App.init(stage);

        //
        // 1) SPLASH SCREEN
        //
        Stage splashStage = new Stage();
        Image splashImg = new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/icons/1.png"))
        );
        splashStage.getIcons().add(new Image(
                Objects.requireNonNull(App.class.getResourceAsStream("/icons/onecritto_white_key_256x256.png"))
        ));
        ImageView splashView = new ImageView(splashImg);
        splashView.setPreserveRatio(true);
      //  splashView.setFitWidth(450);

        StackPane splashRoot = new StackPane(splashView);
        splashRoot.setStyle("-fx-background-color: #0d0f23;");

        Scene splashScene = new Scene(splashRoot);

        splashStage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        splashStage.setScene(splashScene);
        splashStage.setResizable(false);
        splashStage.centerOnScreen();
        splashStage.show();

        //
        // 2) Thread di transizione splash → verifica licenza → login
        //
        new Thread(() -> {
            try {
                Thread.sleep(1800);
            } catch (InterruptedException ignored) {}

            Platform.runLater(() -> {


                splashStage.close();
                try {
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/fxml/login.fxml"),
                            ResourceBundle.getBundle("i18n.messages", I18n.getCurrentLocale())
                    );

                    Parent root = loader.load(); // <-- PRIMA devi caricare l'FXML



                    Scene scene = new Scene(root);
                    scene.getStylesheets().add(
                            Objects.requireNonNull(getClass().getResource("/css/onecritto-theme.css")).toExternalForm()
                    );

                    stage.setTitle("OneCritto");
                    stage.getIcons().add(new Image(
                            Objects.requireNonNull(getClass().getResourceAsStream("/icons/onecritto_white_key_256x256.png"))
                    ));

                    stage.setScene(scene);
                    stage.centerOnScreen();
                    stage.show();

                    stage.setOnCloseRequest(e -> VaultRepository.VAULT_CONTEXT.fullClear());

                } catch (Exception e) {
                    SecureLogger.error(e.getMessage(), e);
                }

            });
        }).start();

        // cleanup in caso di chiusura app
        Runtime.getRuntime().addShutdownHook(new Thread(TempVaultFiles::cleanupTempDirSecure));
    }

}
