package com.storex.controller;

import java.io.IOException;

import com.storex.MainApp;
import com.storex.dao.UserDAO;
import com.storex.model.User;
import com.storex.util.UserSession;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * LoginController handles the login functionality for the application.
 * 
 * This controller manages user authentication by validating username and password inputs,
 * authenticating credentials against the database via UserDAO, and managing session initialization
 * upon successful login.
 * 
 * <h2>FXML Components:</h2>
 * <ul>
 *   <li>{@link #txtUsername} - TextField for username input</li>
 *   <li>{@link #txtPassword} - PasswordField for password input</li>
 * </ul>
 * 
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Validates that username and password fields are not empty</li>
 *   <li>Authenticates user credentials using {@link UserDAO#authenticate(String, String)}</li>
 *   <li>Initializes user session upon successful authentication via {@link UserSession#init(User)}</li>
 *   <li>Loads the dashboard view after successful login</li>
 *   <li>Displays error alerts for invalid input or failed authentication</li>
 * </ul>
 * 
 * <h2>Usage:</h2>
 * This controller should be bound to a JavaFX login view (login.fxml) where the
 * {@link #handleLogin()} method is triggered by a login button action.
 * 
 * @author Ardean Bima Saputra
 * @version 1.0
 * @see UserDAO
 * @see UserSession
 * @see MainApp
 */
public class LoginController {

    @FXML
    private TextField txtUsername;
    @FXML
    private PasswordField txtPassword;

    private final UserDAO userDAO = new UserDAO();

    /**
     * Handles the login process when the login button is clicked.
     * 
     * Validates that both username and password fields are not empty.
     * Attempts to authenticate the user against the database using the provided credentials.
     * 
     * If authentication is successful:
     * - Initializes the user session with the authenticated user
     * - Logs the successful login with username and role
     * - Loads the dashboard view
     * 
     * If authentication fails or an IOException occurs during dashboard loading:
     * - Displays an appropriate error alert to the user
     * - Logs the error details to standard error output
     * 
     * @throws IOException if the dashboard.fxml file cannot be loaded from resources/fxml/
     */
    @FXML
    private void handleLogin() {
        String usernameInput = txtUsername.getText().trim();
        String passwordInput = txtPassword.getText().trim();

        if (usernameInput.isBlank() || passwordInput.isBlank()) {
            showAlert("Warning", "Input cannot be empty.");
            return;
        }

        User validUser = userDAO.authenticate(usernameInput, passwordInput);

        if (validUser != null) {
            UserSession.init(validUser);

            System.out.println("Login Success: " + UserSession.getUsername() + " [" + UserSession.getRole() + "]");

            try {
                MainApp.setRoot("dashboard");
            } catch (IOException e) {
                System.err.println("Failed to load dashboard.fxml. Check the resources/fxml/ folder!");
                e.printStackTrace();
            }
        } else {
            showAlert("Login Failed", "Invalid credentials. Use admin/admin123 or check the database.");
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
