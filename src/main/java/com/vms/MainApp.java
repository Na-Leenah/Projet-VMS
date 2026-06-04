package com.vms;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.vms.config.DatabaseConfig;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/login.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1100, 700);
        scene.getStylesheets().add(
                getClass().getResource("/css/global.css").toExternalForm());

        primaryStage.setTitle("VMS – Connexion");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(980);
        primaryStage.setMinHeight(640);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> {
            DatabaseConfig.fermer();
            Platform.exit();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
