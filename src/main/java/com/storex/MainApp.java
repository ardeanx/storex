package com.storex;

import java.io.File;
import java.io.IOException;

import com.storex.controller.DashboardController;
import com.storex.util.AppConfig;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * MainApp is the main entry point for the StoreX POS System JavaFX application.
 * 
 * This class extends {@link javafx.application.Application} and manages the primary stage,
 * scene, and navigation between different FXML views. It handles application configuration,
 * theming, and provides centralized access to the dashboard controller.
 * 
 * Key responsibilities:
 * - Initialize the application with the login screen
 * - Load and manage FXML views dynamically
 * - Maintain reference to the primary stage and current scene
 * - Manage the DashboardController instance
 * - Apply application icon and configuration settings
 * 
 * @author Ardean Bima Saputra
 * @version 1.0
 */
public class MainApp extends Application {

    private static Scene scene;
    private static Stage primaryStage;
    private static DashboardController dashboardController;

    /**
     * Initializes and displays the primary application window.
     * <p>
     * This method performs the following tasks:
     * <ul>
     *   <li>Stores the primary stage reference</li>
     *   <li>Loads application configuration</li>
     *   <li>Applies the PrimerLight theme stylesheet</li>
     *   <li>Creates the login scene with dimensions 1200x800</li>
     *   <li>Sets the window title from app configuration</li>
     *   <li>Updates and applies the application icon</li>
     *   <li>Displays the stage</li>
     * </ul>
     * </p>
     *
     * @param stage the primary stage provided by the JavaFX runtime
     * @throws IOException if an error occurs while loading the FXML login resource
     */
    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;

        AppConfig.load();
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        scene = new Scene(loadFXML("login"), 1200, 800);
        stage.setTitle(AppConfig.get("app_name", "StoreX - POS System"));
        stage.setScene(scene);

        updateAppIcon(stage);
        stage.show();
    }

    /**
     * Sets the root node of the scene to the specified FXML file.
     * 
     * <p>Loads an FXML file from the classpath, extracts its controller, and sets it as the root
     * of the current scene. If the controller is an instance of {@link DashboardController},
     * it is cached for later use.</p>
     * 
     * @param fxml the name of the FXML file (without extension) located in the "/fxml/" directory
     * @throws IOException if the FXML file cannot be found or loaded
     */
    public static void setRoot(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("/fxml/" + fxml + ".fxml"));
        Parent root = fxmlLoader.load();

        Object controller = fxmlLoader.getController();
        if (controller instanceof DashboardController) {
            dashboardController = (DashboardController) controller;
        }

        scene.setRoot(root);
        primaryStage.setTitle(AppConfig.get("app_name", "StoreX - POS System"));
        updateAppIcon(primaryStage);
    }

    /**
     * Retrieves the singleton instance of the DashboardController.
     *
     * @return the DashboardController instance used throughout the application
     */
    public static DashboardController getDashboardController() {
        return dashboardController;
    }

    /**
     * Loads and returns the root node of an FXML file.
     * 
     * @param fxml the name of the FXML file (without the .fxml extension) located in the /fxml/ directory
     * @return the root node of the loaded FXML file as a {@link Parent}
     * @throws IOException if the FXML file cannot be found or loaded
     */
    public static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("/fxml/" + fxml + ".fxml"));
        return fxmlLoader.load();
    }

    /**
     * Updates the application icon for the given stage.
     * 
     * <p>Retrieves the icon file path from application configuration and applies it to the stage.
     * If the path is empty or the file does not exist, no action is taken.
     * 
     * @param stage the JavaFX Stage whose icon should be updated
     */
    public static void updateAppIcon(Stage stage) {
        String path = AppConfig.get("icon_path", "");
        if (!path.isEmpty()) {
            File file = new File(path);
            if (file.exists()) {
                stage.getIcons().clear();
                stage.getIcons().add(new Image(file.toURI().toString()));
            }
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
