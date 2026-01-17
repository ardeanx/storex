package com.storex.controller;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.storex.MainApp;
import com.storex.util.AppConfig;
import com.storex.util.DatabaseHelper;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Controller for the application Settings view.
 *
 * <p>
 * Responsibilities: - Load application settings from AppConfig into UI fields.
 * - Allow selection of logo and icon files via FileChooser. - Persist settings
 * into the database (INSERT ... ON DUPLICATE KEY UPDATE). - Refresh app-wide
 * resources (reload AppConfig, update application icon, refresh dashboard).
 *
 * <p>
 * Key assumptions and behavior: - This controller runs on the JavaFX
 * Application Thread. - Database write is executed synchronously inside
 * {@link #handleSave()} using JDBC batch + commit. - Timezones are populated
 * from {@link java.util.TimeZone#getAvailableIDs()} and sorted. - UI label
 * strings (file chooser titles and alerts) are currently in Indonesian.
 */
public class SettingsController {

    /* FXML bindings - UI controls */
    @FXML
    private TextField txtAppName, txtShopName, txtShopEmail, txtShopPhone, txtCurrency;
    @FXML
    private TextArea txtShopAddress;
    @FXML
    private ComboBox<String> comboTimezone;
    @FXML
    private Label lblPaths;

    /* Internal state for selected asset file paths */
    private String logoPath = "";
    private String iconPath = "";

    /**
     * Called by JavaFX after FXML is loaded.
     *
     * <p>
     * Initialization steps: - Populate timezone ComboBox with all available
     * TimeZone IDs (sorted). - Load persisted settings into UI fields via
     * {@link #loadSettings()}.
     */
    @FXML
    public void initialize() {
        List<String> zones = new ArrayList<>(List.of(TimeZone.getAvailableIDs()));
        Collections.sort(zones);
        comboTimezone.setItems(FXCollections.observableArrayList(zones));

        loadSettings();
    }

    /**
     * Load settings from AppConfig and populate the form fields.
     *
     * Behavior: - Calls {@link AppConfig#load()} to ensure the latest
     * configuration is in memory. - For each UI control, sets its text/value
     * only if the control reference is not null (defensive check for headless
     * tests or partial view loads). - Reads logo/icon paths from AppConfig and
     * updates the path label via {@link #updatePathLabel()}.
     */
    private void loadSettings() {
        AppConfig.load();

        if (txtAppName != null) {
            txtAppName.setText(AppConfig.get("app_name", "StoreX"));
        }
        if (txtShopName != null) {
            txtShopName.setText(AppConfig.get("shop_name", ""));
        }
        if (txtShopEmail != null) {
            txtShopEmail.setText(AppConfig.get("shop_email", ""));
        }
        if (txtShopPhone != null) {
            txtShopPhone.setText(AppConfig.get("shop_phone", ""));
        }
        if (txtShopAddress != null) {
            txtShopAddress.setText(AppConfig.get("shop_address", ""));
        }
        if (txtCurrency != null) {
            txtCurrency.setText(AppConfig.get("currency_symbol", "Rp"));
        }
        if (comboTimezone != null) {
            comboTimezone.setValue(AppConfig.get("timezone", "Asia/Jakarta"));
        }

        // Preserve current logo/icon settings for the UI
        logoPath = AppConfig.get("logo_path", "");
        iconPath = AppConfig.get("icon_path", "");
        updatePathLabel();
    }

    /**
     * Handler invoked by the "Select Logo" button.
     *
     * <p>
     * Opens a FileChooser and, if the user selected a file, stores its absolute
     * path into {@link #logoPath} and refreshes the UI label.
     */
    @FXML
    private void handleSelectLogo() {
        String path = selectFile("Pilih Logo Toko"); // note: UI title in Indonesian
        if (!path.isEmpty()) {
            logoPath = path;
            updatePathLabel();
        }
    }

    /**
     * Handler invoked by the "Select Icon" button.
     *
     * <p>
     * Same behavior as {@link #handleSelectLogo()}, different chooser title.
     */
    @FXML
    private void handleSelectIcon() {
        String path = selectFile("Pilih Icon Aplikasi"); // note: UI title in Indonesian
        if (!path.isEmpty()) {
            iconPath = path;
            updatePathLabel();
        }
    }

    /**
     * Show a FileChooser restricted to common image formats and return the
     * selected file path.
     *
     * @param title title shown on the FileChooser dialog
     * @return absolute path of selected file, or empty string when user cancels
     */
    private String selectFile(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        // Limit selection to typical image types
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File f = fc.showOpenDialog(txtAppName.getScene().getWindow()); // parent window from a control
        return (f != null) ? f.getAbsolutePath() : "";
    }

    /**
     * Update the label that indicates whether logo/icon files are selected.
     *
     * <p>
     * Displays "Selected" if a path was chosen, otherwise "N/A". Defensive
     * null-check on the label.
     */
    private void updatePathLabel() {
        if (lblPaths != null) {
            lblPaths.setText("Logo: " + (logoPath.isEmpty() ? "N/A" : "Selected")
                    + " | Icon: " + (iconPath.isEmpty() ? "N/A" : "Selected"));
        }
    }

    /**
     * Persist current settings into the database.
     *
     * <p>
     * Workflow: - Collect settings into a Map&lt;String,String&gt;. - Build a
     * SQL statement that inserts or updates (MySQL-style): INSERT INTO settings
     * (setting_key, setting_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE
     * setting_value = VALUES(setting_value) - Open JDBC connection via
     * {@link DatabaseHelper#connect()}. - Use PreparedStatement with batching;
     * wrap operations in a transaction (conn.setAutoCommit(false)). - Execute
     * batch and commit. - On success: reload AppConfig, update application icon
     * via MainApp, refresh dashboard UI, and show success Alert. - On failure:
     * rollback implicit by closing connection after exception and show error
     * Alert with exception message.
     *
     * Important notes: - This method performs blocking I/O on the JavaFX
     * thread. For slow DB calls, the UI will freeze. Consider moving DB writes
     * into a background Task or Service. - The SQL uses MySQL-specific ON
     * DUPLICATE KEY syntax; portability to other RDBMS requires changes. - No
     * validation is performed on input fields before saving; empty values will
     * be inserted/updated.
     */
    @FXML
    private void handleSave() {
        Map<String, String> m = new HashMap<>();
        m.put("app_name", txtAppName.getText());
        m.put("shop_name", txtShopName.getText());
        m.put("shop_email", txtShopEmail.getText());
        m.put("shop_phone", txtShopPhone.getText());
        m.put("shop_address", txtShopAddress.getText());
        m.put("currency_symbol", txtCurrency.getText());
        m.put("timezone", comboTimezone.getValue());
        m.put("logo_path", logoPath);
        m.put("icon_path", iconPath);

        // SQL will insert or update existing key
        String sql = "INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)";

        try (Connection conn = DatabaseHelper.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false); // begin transaction

            // Add each setting as a batch item
            for (var e : m.entrySet()) {
                ps.setString(1, e.getKey());
                ps.setString(2, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit(); // commit all updates

            // Refresh in-memory config and UI resources
            AppConfig.load();
            MainApp.updateAppIcon((Stage) txtAppName.getScene().getWindow());
            DashboardController dash = MainApp.getDashboardController();
            if (dash != null) {
                dash.updateSidebarLogo();
                dash.initialize(); // re-init dashboard to pick up changes
            }

            // Notify user of success (UI string currently Indonesian)
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Pengaturan Berhasil Disimpan!");
            alert.showAndWait();

        } catch (SQLException e) {
            // Log stack trace and show an error to the user containing the DB message
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Gagal menyimpan ke Database: " + e.getMessage()).showAndWait();
        }
    }
}
