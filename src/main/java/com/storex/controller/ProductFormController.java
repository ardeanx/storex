package com.storex.controller;

import java.math.BigDecimal;

import com.storex.model.Product;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Controller for the product form dialog used to add or edit a Product.
 *
 * Responsibilities: - Populate form controls when editing an existing Product.
 * - Validate user input (basic checks) and signal whether user saved. - Produce
 * a Product instance from form fields via getProduct().
 *
 * Important caveats: - getProduct() directly parses txtPrice and txtStock into
 * BigDecimal/int without locale-aware parsing. If the UI ever accepts formatted
 * numbers (commas, currency symbols) parsing will fail. - setProduct() sets the
 * title text to "Edit Produk" (Indonesian). This is a UI string in code. -
 * isInputValid() does basic blank checks and numeric parsing; getProduct() may
 * still throw if called when fields are invalid — caller must respect
 * isSaveClicked().
 */
public class ProductFormController {

    /**
     * FXML label used for dialog title (changes when editing).
     */
    @FXML
    private Label lblTitle;

    /**
     * Form fields bound to UI: barcode, name, price, stock.
     */
    @FXML
    private TextField txtBarcode, txtName, txtPrice, txtStock;

    /**
     * Category combobox with preset values.
     */
    @FXML
    private ComboBox<String> cbCategory;

    /**
     * Backing product (null when creating new).
     */
    private Product product;

    /**
     * Flag that indicates whether user clicked Save. The dialog host should
     * call isSaveClicked() after the modal closes to decide whether to persist.
     */
    private boolean saveClicked = false;

    /**
     * Initialization called by JavaFX after FXML load. Populates category
     * choices. Runs on the JavaFX Application Thread.
     */
    @FXML
    public void initialize() {
        cbCategory.getItems().addAll("Food", "Beverage", "Snack", "Other");
    }

    /**
     * Pre-populate the form when editing an existing product.
     *
     * @param product existing Product to edit; if null, form remains blank for
     * creation
     *
     * Side effects: - sets internal product reference - fills fields (barcode,
     * name, category, price, stock) - sets lblTitle text to "Edit Produk" when
     * product != null
     *
     * Note: Price displayed via BigDecimal.toString() — formatting/locale not
     * applied here.
     */
    public void setProduct(Product product) {
        this.product = product;
        if (product != null) {
            lblTitle.setText("Edit Produk"); // UI string currently in Indonesian
            txtBarcode.setText(product.barcode());
            txtName.setText(product.name());
            cbCategory.setValue(product.category());
            txtPrice.setText(product.price().toString());
            txtStock.setText(String.valueOf(product.stock()));
        }
    }

    /**
     * Returns true if the user confirmed save (clicked Save) before the dialog
     * closed. The host dialog should only call getProduct() when this returns
     * true.
     *
     * @return boolean indicating save action
     */
    public boolean isSaveClicked() {
        return saveClicked;
    }

    /**
     * Create a Product object from current form values.
     *
     * Behavior: - If controller was editing, preserves product.id(); otherwise
     * uses id 0. - Category falls back to "Other" if none selected. - Parses
     * price to BigDecimal and stock to int directly from text fields. - Unit is
     * hardcoded to "Pcs".
     *
     * Warning: - This method will throw NumberFormatException if txtPrice or
     * txtStock contain invalid numbers. - Caller must ensure validation
     * (isInputValid()) before invoking this method.
     *
     * @return new Product built from form fields
     */
    public Product getProduct() {
        return new Product(
                product != null ? product.id() : 0,
                txtBarcode.getText(),
                txtName.getText(),
                cbCategory.getValue() != null ? cbCategory.getValue() : "Other",
                new BigDecimal(txtPrice.getText()), // may throw NumberFormatException
                Integer.parseInt(txtStock.getText()), // may throw NumberFormatException
                "Pcs" // hardcoded unit
        );
    }

    /**
     * Handler for Save button.
     *
     * Flow: - Validates input using isInputValid(). - If valid: sets
     * saveClicked = true and closes the modal Stage. - If invalid: does nothing
     * (UI feedback not provided here).
     *
     * Note: Closing the Stage assumes the controller's txtName is attached to
     * the dialog Scene.
     */
    @FXML
    private void handleSave() {
        if (isInputValid()) {
            saveClicked = true;
            ((Stage) txtName.getScene().getWindow()).close();
        }
    }

    /**
     * Handler for Cancel button. Simply closes the dialog Stage without setting
     * saveClicked.
     */
    @FXML
    private void handleCancel() {
        ((Stage) txtName.getScene().getWindow()).close();
    }

    /**
     * Basic input validation.
     *
     * Checks: - name and barcode must not be blank - price must be parseable as
     * BigDecimal - stock must be parseable as Integer
     *
     * Returns false on any validation failure. Exceptions during numeric
     * parsing are caught and treated as invalid input.
     *
     * @return true when inputs appear valid; false otherwise
     */
    private boolean isInputValid() {
        if (txtName.getText().isBlank() || txtBarcode.getText().isBlank()) {
            return false;
        }
        try {
            new BigDecimal(txtPrice.getText());
            Integer.parseInt(txtStock.getText());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
