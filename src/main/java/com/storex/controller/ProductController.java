package com.storex.controller;

import java.io.IOException;
import java.math.BigDecimal;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import com.storex.dao.ProductDAO;
import com.storex.model.Product;
import com.storex.util.CurrencyUtil;

import atlantafx.base.theme.Styles;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Controller for the product management view.
 *
 * Responsibilities: - Bind Product data to a TableView (id, name, category,
 * price, stock). - Provide action buttons per row (edit/delete). - Open a modal
 * product form for add/edit operations. - Delete products with confirmation and
 * reload data after changes.
 *
 * Implementation notes / caveats: - ProductDAO calls are synchronous and
 * executed on the JavaFX thread; for large data sets or slow I/O, move DAO
 * calls to background Tasks/Services to avoid UI freezes. - showProductDialog()
 * loads an FXML form and blocks using showAndWait(); ensure the
 * product_form.fxml controller correctly signals save/cancel via
 * isSaveClicked()/getProduct(). - Price formatting uses
 * CurrencyUtil.format(BigDecimal). Locale/precision concerns should be handled
 * inside CurrencyUtil.
 */
public class ProductController {

    /* FXML UI bindings */
    @FXML
    private TableView<Product> tblProducts;
    @FXML
    private TableColumn<Product, Integer> colId;
    @FXML
    private TableColumn<Product, String> colName;
    @FXML
    private TableColumn<Product, String> colCategory;
    @FXML
    private TableColumn<Product, BigDecimal> colPrice;
    @FXML
    private TableColumn<Product, Integer> colStock;
    @FXML
    private TableColumn<Product, Void> colActions;

    /* Data access and model */
    private final ProductDAO productDAO = new ProductDAO();
    private final ObservableList<Product> masterData = FXCollections.observableArrayList();

    /**
     * Initialize the controller after FXML load.
     *
     * - Sets up cell value factories for each column. - Configures price column
     * to display formatted currency. - Configures action column (edit/delete
     * buttons per row). - Loads initial data via loadData().
     *
     * Note: This method runs on the JavaFX Application Thread.
     */
    @FXML
    public void initialize() {
        // ID column: simple object property wrapping Product.id()
        colId.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().id()));

        // Name column: string property from Product.name()
        colName.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().name()));

        // Category column: string property from Product.category()
        colCategory.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().category()));

        // Stock column: integer property from Product.stock()
        colStock.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().stock()));

        // Price column: use BigDecimal property, render formatted currency text
        colPrice.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().price()));
        colPrice.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal price, boolean empty) {
                super.updateItem(price, empty);
                // If empty or null, clear text; otherwise show formatted currency.
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(CurrencyUtil.format(price)); // CurrencyUtil expected to handle locale & formatting
                }
            }
        });

        // Set up action buttons (Edit/Delete) and load data into table
        setupActionColumn();
        loadData();
    }

    /**
     * Load products from ProductDAO into the table's master data list.
     *
     * Behavior: - Calls productDAO.getAllProducts() and replaces masterData
     * contents. - Binds masterData to the TableView (tblProducts.setItems).
     *
     * Caveat: - productDAO.getAllProducts() is synchronous; use a background
     * thread for heavy DB loads.
     */
    private void loadData() {
        masterData.setAll(productDAO.getAllProducts());
        tblProducts.setItems(masterData);
    }

    /**
     * Configure the actions column to show Edit and Delete buttons for each
     * row.
     *
     * Implementation details: - Uses FontIcon (Ikonli) for button graphics. -
     * Applies Atlantafx Styles classes for circular, colored buttons. - Edit
     * button opens product form modal via showProductDialog(Product). - Delete
     * button prompts confirmation and calls handleDelete(Product).
     *
     * Note: - getIndex() usage is tied to the TableCell; ensure model & view
     * remain in sync when using it.
     */
    private void setupActionColumn() {
        colActions.setCellFactory(param -> new TableCell<>() {
            // Icon buttons - no text label, only icons
            private final Button btnEdit = new Button("", new FontIcon(Material2AL.EDIT));
            private final Button btnDelete = new Button("", new FontIcon(Material2AL.DELETE));
            private final HBox container = new HBox(8, btnEdit, btnDelete);

            {
                // Center buttons horizontally in cell
                container.setAlignment(Pos.CENTER);

                // Apply CSS utility classes from Atlantafx (keeps styling out of code if CSS available)
                btnEdit.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.SUCCESS);
                btnDelete.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.DANGER);

                // Edit action: fetch product at current index and open dialog
                btnEdit.setOnAction(event -> {
                    Product p = getTableView().getItems().get(getIndex());
                    showProductDialog(p);
                });

                // Delete action: fetch product at current index and handle deletion
                btnDelete.setOnAction(event -> {
                    Product p = getTableView().getItems().get(getIndex());
                    handleDelete(p);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                // Show buttons only for non-empty rows
                setGraphic(empty ? null : container);
            }
        });
    }

    /**
     * Handler for the "Add Product" UI control.
     *
     * Opens the product form dialog with null (indicates creation of a new
     * product).
     */
    @FXML
    private void handleAddProduct() {
        showProductDialog(null);
    }

    /**
     * Delete the provided product after user confirmation.
     *
     * Flow: - Show a confirmation Alert (YES/NO). - If YES => attempt
     * productDAO.delete(product.id()). - On success: reload table data. - On
     * failure: show error (likely because product is referenced in
     * transactions).
     *
     * Notes: - productDAO.delete(...) is synchronous and must return boolean
     * success. - Consider handling DB constraint exceptions in DAO and
     * surfacing a specific error message.
     *
     * @param p product to delete
     */
    private void handleDelete(Product p) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete product " + p.name() + "?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Delete Confirmation");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                if (productDAO.delete(p.id())) {
                    loadData();
                } else {
                    // Generic error: deletion failed (likely referential integrity)
                    showError("Failed to delete product. The product may be referenced by existing transactions.");
                }
            }
        });
    }

    /**
     * Show the product form dialog for creating or editing a Product.
     *
     * Behavior: - Loads "/fxml/product_form.fxml" via FXMLLoader. - Retrieves
     * ProductFormController and calls controller.setProduct(p) to pre-populate
     * when editing. - Opens a modal Stage with
     * initModality(Modality.APPLICATION_MODAL) and blocks with showAndWait(). -
     * After dialog closes, if controller.isSaveClicked() is true, obtains the
     * updated Product and: - Calls productDAO.insert(result) when p == null
     * (add mode). - Calls productDAO.update(result) when p != null (edit mode).
     * - On successful DAO operation: reload data. Otherwise show an error
     * alert.
     *
     * Error handling: - Catches IOException from FXMLLoader.load(), prints
     * stack trace and shows an error Alert.
     *
     * Caveats: - The dialog uses blocking UI semantics; ensure the form
     * controller performs validation and sets isSaveClicked correctly. - DAO
     * operations after form close are synchronous; consider moving
     * insert/update to background thread if DB latency is a concern.
     *
     * @param p existing product to edit, or null to create a new product
     */
    private void showProductDialog(Product p) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/product_form.fxml"));
            Parent root = loader.load();

            ProductFormController controller = loader.getController();
            controller.setProduct(p); // pre-populate form for edit; null for add

            Stage stage = new Stage();
            stage.setTitle(p == null ? "Add Product" : "Edit Product");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            // Blocking call: will wait until the modal is closed
            stage.showAndWait();

            // After dialog closes, check whether save was clicked
            if (controller.isSaveClicked()) {
                Product result = controller.getProduct();
                boolean success = (p == null) ? productDAO.insert(result) : productDAO.update(result);

                if (success) {
                    loadData(); // refresh table to reflect change
                } else {
                    showError("Failed to save data to the database.");
                }
            }
        } catch (IOException e) {
            // Log stack trace for debugging; show error to user summarizing the failure
            e.printStackTrace();
            showError("Failed to load product form: " + e.getMessage());
        }
    }

    /**
     * Show an error alert with the provided message.
     *
     * @param msg error message to display
     */
    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.showAndWait();
    }
}
