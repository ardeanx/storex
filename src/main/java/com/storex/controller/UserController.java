package com.storex.controller;

import com.storex.model.User;
import com.storex.dao.UserDAO;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import java.io.IOException;
import java.util.Optional;

/**
 * Controller for the user management view.
 *
 * <p>
 * Responsibilities: - Render a TableView of users (username, full name, role).
 * - Provide per-row actions: Edit and Delete. - Open a modal dialog for adding
 * or editing users. - Validate dialog results and persist changes via
 * {@link UserDAO}.
 *
 * <p>
 * Notes / caveats: - All DAO calls are synchronous and executed on the JavaFX
 * thread (may freeze UI for slow DB). - UI strings (dialogs/titles) are
 * currently in Indonesian. - Password handling is delegated to
 * {@code UserDialogController} and {@code UserDAO}; ensure DAO stores hashed
 * passwords. - TableCell usage relies on getIndex(); keep model/view sync when
 * modifying the underlying list.
 */
public class UserController {

    /* FXML bindings for table and columns */
    @FXML
    private TableView<User> tableUsers;
    @FXML
    private TableColumn<User, String> colUsername;
    @FXML
    private TableColumn<User, String> colFullName;
    @FXML
    private TableColumn<User, String> colRole;
    @FXML
    private TableColumn<User, Void> colAction;

    /* DAO for user persistence */
    private final UserDAO userDAO = new UserDAO();

    /**
     * JavaFX initialization callback.
     *
     * - Binds table columns to User properties. - Sets up action buttons column
     * (Edit/Delete). - Loads initial user data into the table.
     */
    @FXML
    public void initialize() {
        colUsername.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().username()));
        colFullName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fullName()));
        colRole.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().role()));

        // Configure per-row action buttons (Edit & Delete)
        setupActionButtons();

        // Populate table
        loadData();
    }

    /**
     * Configures the action column with Edit and Delete buttons.
     *
     * Implementation details: - Uses an anonymous Callback to return a
     * TableCell with two styled Buttons. - Buttons call handlers that operate
     * on the User at getIndex(). - Styling and emoji icons are applied inline;
     * consider moving to CSS.
     *
     * Caveat: - getIndex() is used to locate the User. If the underlying list
     * changes, ensure indices remain consistent.
     */
    private void setupActionButtons() {
        colAction.setCellFactory(new Callback<>() {
            @Override
            public TableCell<User, Void> call(final TableColumn<User, Void> param) {
                return new TableCell<>() {
                    private final Button btnEdit = new Button("✎");
                    private final Button btnDelete = new Button("🗑");
                    private final HBox pane = new HBox(10, btnEdit, btnDelete);

                    {
                        // Inline styles for quick visual cue (green = edit, red = delete)
                        btnEdit.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-background-radius: 15; -fx-cursor: hand;");
                        btnDelete.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-background-radius: 15; -fx-cursor: hand;");
                        pane.setStyle("-fx-alignment: CENTER;");

                        // Wire button actions to controller handlers using current row index
                        btnEdit.setOnAction(e -> handleEditUser(getTableView().getItems().get(getIndex())));
                        btnDelete.setOnAction(e -> handleDeleteUser(getTableView().getItems().get(getIndex())));
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        // Show the pane only for non-empty rows
                        setGraphic(empty ? null : pane);
                    }
                };
            }
        });
    }

    /**
     * Load all users from DAO and populate the TableView.
     *
     * Note: userDAO.getAllUsers() is synchronous. For many users or slow DB,
     * run in a background Task.
     */
    @FXML
    private void loadData() {
        tableUsers.setItems(FXCollections.observableArrayList(userDAO.getAllUsers()));
    }

    /**
     * Handler for "Add User" UI action.
     *
     * Opens the user dialog in create mode (existingUser == null).
     */
    @FXML
    private void handleAddUser() {
        showUserDialog(null);
    }

    /**
     * Handler to edit an existing user.
     *
     * Delegates to showUserDialog with the selected user instance.
     *
     * @param user user to edit
     */
    private void handleEditUser(User user) {
        showUserDialog(user);
    }

    /**
     * Show the user dialog for add or edit operations.
     *
     * Flow: - Loads "/fxml/user_dialog.fxml" and gets its controller
     * (UserDialogController). - If editing, pre-populates controller with
     * existing user data. - Builds a JavaFX {@link Dialog} with Save and Cancel
     * buttons and shows it modally via showAndWait(). - On Save: validates
     * input via controller.validate(isNew) and then either inserts or updates
     * using UserDAO. - On success: refreshes table via loadData(); on failure:
     * shows an error Alert.
     *
     * Important details: - Title strings are in Indonesian ("Tambah Pengguna
     * Baru" / "Edit Pengguna"). - Password is retrieved from the dialog
     * controller and passed to DAO when creating a new user. Ensure
     * {@code UserDAO.insert(...)} hashes passwords and enforces security best
     * practices. - Validation is handled by the dialog controller; this method
     * trusts controller.validate(...) result.
     *
     * @param existingUser existing user to edit, or null to create a new user
     */
    private void showUserDialog(User existingUser) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/user_dialog.fxml"));
            VBox dialogContent = loader.load();
            UserDialogController controller = loader.getController();

            // Dialog title depends on mode
            String title = (existingUser == null) ? "Tambah Pengguna Baru" : "Edit Pengguna";

            // If editing, populate the dialog fields with existing data
            if (existingUser != null) {
                controller.setExistingData(existingUser);
            }

            // Build a modal dialog using the loaded FXML content
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(title);
            dialog.getDialogPane().setContent(dialogContent);

            // Add Save and Cancel buttons
            ButtonType saveButtonType = new ButtonType("Simpan", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

            // Show dialog and wait for user action
            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isPresent() && result.get() == saveButtonType) {
                // Validate user input; pass `true` if creating (existingUser == null)
                if (controller.validate(existingUser == null)) {
                    User inputUser = controller.getResult();
                    String password = controller.getPassword(); // may be null when updating (depends on dialog implementation)

                    boolean success;
                    if (existingUser == null) {
                        // Create new user; DAO should handle password hashing
                        success = userDAO.insert(inputUser.username(), password, inputUser.fullName(), inputUser.role());
                    } else {
                        // Update existing user (password not updated here)
                        success = userDAO.update(existingUser.id(), inputUser.username(), inputUser.fullName(), inputUser.role());
                    }

                    if (success) {
                        loadData(); // refresh table to reflect changes
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Database Error", "Gagal menyimpan data.");
                    }
                } else {
                    // Validation failed inside dialog controller
                    showAlert(Alert.AlertType.WARNING, "Validasi Gagal", "Input tidak valid.");
                }
            }
        } catch (IOException e) {
            // Loading FXML failed — print stack trace for debugging
            e.printStackTrace();
        }
    }

    /**
     * Prompt confirmation and delete a user.
     *
     * - Shows a confirmation Alert (message in Indonesian). - If confirmed and
     * DAO.delete returns true -> reload table.
     *
     * Note: - No feedback shown when delete fails (other than absence of
     * reload). - Consider surfacing DAO failures with an Alert for better UX.
     *
     * @param user user to be deleted
     */
    private void handleDeleteUser(User user) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Hapus user " + user.username() + "?");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK && userDAO.delete(user.id())) {
                loadData();
            }
        });
    }

    /**
     * Utility method to show Alerts in a uniform way.
     *
     * @param type alert type (INFORMATION, WARNING, ERROR, etc.)
     * @param title window title for the alert
     * @param content message to display
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content);
        alert.setTitle(title);
        alert.showAndWait();
    }
}
