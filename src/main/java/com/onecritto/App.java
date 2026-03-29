package com.onecritto;


import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

public class App {


    public static void init(Stage stage) {

        stage.getIcons().add(new Image(
                Objects.requireNonNull(App.class.getResourceAsStream("/icons/onecritto_white_key_256x256.png"))
        ));
    }


}
