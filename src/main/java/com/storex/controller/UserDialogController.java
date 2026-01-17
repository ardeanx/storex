package com.storex.controller;

import com.storex.model.User;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller for the user creation / edit dialog.
 *
 * Responsibilities: - Initialize role choices (ADMIN, CASHIER) and default
 * selection. - Populate dialog fields when editing an existing user. - Provide
 * getters to retrieve User object and password entered by the operator. -
 * Validate required fields before saving.
 *
 * Caveats: - Password handling is plain-text at the UI level; ensure DAO hashes
 * passwords before persisting. - getResult() returns a User with id = 0 (caller
 * must decide insert vs update and preserve real id when updating). - UI
 * strings (password prompt) contain Indonesian text ("Kosongkan jika tidak
 * diubah").
 */
public class UserDialogController {

    /**
     * Username input field. Required.
     */
    @FXML
    private TextField txtUsername;

    /**
     * Full name input field. Required.
     */
    @FXML
    private TextField txtFullName;

    /**
     * Password input field. When editing, leaving this blank indicates no
     * password change.
     */
    @FXML
    private PasswordField txtPassword;

    /**
     * Role selector. Values: "ADMIN" or "CASHIER". Defaults to "CASHIER".
     */
    @FXML
    private ComboBox<String> comboRole;

    /**
     * JavaFX initialization callback.
     *
     * - Populates the role ComboBox with permitted roles. - Sets default role
     * to "CASHIER".
     */
    @FXML
    public void initialize() {
        comboRole.setItems(FXCollections.observableArrayList("ADMIN", "CASHIER"));
        comboRole.setValue("CASHIER");
    }

    /**
     * Pre-fill dialog fields when editing an existing user.
     *
     * - Sets username, full name and role from the provided User. - Sets a
     * password prompt text to indicate the password is optional when editing.
     *
     * @param user existing User instance to edit (non-null)
     */
    public void setExistingData(User user) {
        txtUsername.setText(user.username());
        txtFullName.setText(user.fullName());
        comboRole.setValue(user.role());
        txtPassword.setPromptText("Kosongkan jika tidak diubah"); // UI hint in Indonesian
    }

    /**
     * Build a User object from current form values.
     *
     * Notes: - The returned User uses id = 0. The caller should replace the id
     * when performing updates. - Username and fullname are trimmed.
     *
     * @return User constructed from form fields
     */
    public User getResult() {
        return new User(0, txtUsername.getText().trim(), txtFullName.getText().trim(), comboRole.getValue());
    }

    /**
     * Return the raw password entered in the dialog.
     *
     * - For new users, this should be non-empty (see validate()). - For edit
     * mode, an empty string indicates "leave existing password unchanged".
     *
     * @return password text (may be empty)
     */
    public String getPassword() {
        return txtPassword.getText();
    }

    /**
     * Basic validation for dialog input.
     *
     * - Ensures username and full name are not blank. - If creating a new user
     * (isNewUser == true), also requires a non-blank password.
     *
     * Behavior: - Returns true when inputs meet requirements; false otherwise.
     *
     * @param isNewUser true when validating creation mode, false for edit mode
     * @return boolean indicating whether input is valid
     */
    public boolean validate(boolean isNewUser) {
        boolean baseValid = !txtUsername.getText().isBlank() && !txtFullName.getText().isBlank();
        if (isNewUser) {
            return baseValid && !txtPassword.getText().isBlank();
        }
        return baseValid;
    }
}
