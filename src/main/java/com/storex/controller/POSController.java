package com.storex.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.storex.dao.ProductDAO;
import com.storex.dao.SaleDAO;
import com.storex.model.CartItem;
import com.storex.model.Product;
import com.storex.util.CurrencyUtil;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/**
 * Controller for the POS (Point Of Sale) screen.
 *
 * <p>
 * Main responsibilities: - Handle product search and adding items to the cart -
 * Manage item quantity adjustments (plus/minus) - Calculate totals and change -
 * Persist transactions via SaleDAO
 *
 * Notes: - This code assumes execution on the JavaFX Application Thread. - DAO
 * operations are synchronous; for heavy I/O move them off the UI thread.
 */
public class POSController {

    // --- FXML bindings (connected via FXML file) ---
    @FXML
    private TextField txtSearch, txtCash; // txtCash contains cash input (string; non-digits are filtered)
    @FXML
    private Label lblTotal, lblChange, lblStatus; // labels for total/change/status
    @FXML
    private TableView<CartItem> tblCart;
    @FXML
    private TableColumn<CartItem, String> colCartName;
    @FXML
    private TableColumn<CartItem, BigDecimal> colCartPrice;
    @FXML
    private TableColumn<CartItem, Integer> colCartQty;
    @FXML
    private TableColumn<CartItem, BigDecimal> colCartSubtotal;
    @FXML
    private TableColumn<CartItem, Void> colCartActions;

    // --- DAO and local state ---
    private final ProductDAO productDAO = new ProductDAO(); // product access
    private final SaleDAO saleDAO = new SaleDAO(); // transaction persistence
    private final ObservableList<CartItem> cartData = FXCollections.observableArrayList(); // table model
    private BigDecimal totalAmount = BigDecimal.ZERO; // current total
    private int editingSaleId = -1; // -1 means not editing an existing sale

    /**
     * Initialize controller — called automatically after FXML is loaded.
     *
     * <p>
     * Responsibilities: - Set cell value factories and cell factories for
     * display formatting - Wire plus/minus buttons for each row - Bind table to
     * cartData and listen to txtCash changes to recalculate change
     */
    @FXML
    public void initialize() {
        // Product name (String)
        colCartName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().name()));

        // Price: display as currency (CurrencyUtil.format)
        colCartPrice.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().price()));
        colCartPrice.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : CurrencyUtil.format(price));
            }
        });

        // Quantity: center-aligned display
        colCartQty.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().quantity()));
        colCartQty.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Integer qty, boolean empty) {
                super.updateItem(qty, empty);
                if (empty || qty == null) {
                    setText(null);
                } else {
                    setText(qty.toString());
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Subtotal per row (price * qty)
        colCartSubtotal.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getSubtotal()));
        colCartSubtotal.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal subtotal, boolean empty) {
                super.updateItem(subtotal, empty);
                setText(empty || subtotal == null ? null : CurrencyUtil.format(subtotal));
            }
        });

        // Actions column: minus/plus buttons to adjust qty
        colCartActions.setCellFactory(column -> new TableCell<>() {
            private final Button btnMinus = new Button("-");
            private final Button btnPlus = new Button("+");
            private final HBox container = new HBox(5, btnMinus, btnPlus);

            {
                container.setAlignment(Pos.CENTER);
                // Inline styling: quick and dirty — move to CSS for maintainability
                btnMinus.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                btnPlus.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

                // Wire actions: call handler with delta -1/+1
                btnMinus.setOnAction(e -> handleQtyAdjustment(-1, getIndex()));
                btnPlus.setOnAction(e -> handleQtyAdjustment(1, getIndex()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : container);
            }
        });

        // Bind the model to the table
        tblCart.setItems(cartData);

        // Recalculate change whenever cash input changes
        txtCash.textProperty().addListener((obs, old, newVal) -> calculateChange());
    }

    /**
     * Handle quantity adjustment for an item at a given index.
     *
     * @param delta change in quantity (e.g. -1 or +1)
     * @param index row index in the table/cartData
     *
     * Notes: if qty becomes 0 or negative, item is removed from the cart. Stock
     * validation is checked against ProductDAO (synchronous).
     */
    private void handleQtyAdjustment(int delta, int index) {
        if (index < 0 || index >= cartData.size()) {
            return; // invalid index (can happen during table refresh)
        }

        CartItem item = cartData.get(index);
        int newQty = item.quantity() + delta;

        if (newQty <= 0) {
            // Remove item when qty reaches zero
            cartData.remove(index);
        } else {
            // Validate stock before updating
            Product p = productDAO.getProductById(item.productId());
            if (p != null && newQty > p.stock()) {
                showAlert("Stock Limit", "Stock for " + p.name() + " is insufficient.");
                return;
            }
            // Replace CartItem with a new instance (immutable pattern)
            cartData.set(index, new CartItem(item.productId(), item.name(), item.price(), newQty));
        }
        updateTotal();
    }

    /**
     * Load an existing order for editing.
     *
     * @param items list of CartItem to load into the cart
     * @param oldSaleId id of the sale being edited
     *
     * Side effects: clears previous cart, sets editingSaleId, updates status
     * UI.
     */
    public void loadOrderForEdit(List<CartItem> items, int oldSaleId) {
        handleClear();
        this.editingSaleId = oldSaleId;
        cartData.addAll(items);
        updateTotal();
        if (lblStatus != null) {
            lblStatus.setText("EDITING TRANSACTION #" + oldSaleId);
            lblStatus.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");
        }
    }

    /**
     * Search handler triggered from the UI (txtSearch).
     *
     * Input format: - "barcode" or partial product name -> qty defaults to 1 -
     * "query*qty" e.g. "coffee*3" -> qty = 3
     *
     * If product is found, it is added to the cart via addToCart. If stock is
     * insufficient -> showAlert.
     */
    @FXML
    private void handleSearch() {
        String input = txtSearch.getText().trim();
        if (input.isEmpty()) {
            return;
        }

        String query = input;
        int qty = 1;

        if (input.contains("*")) {
            String[] parts = input.split("\\*");
            query = parts[0];
            try {
                qty = Integer.parseInt(parts[1]);
            } catch (Exception e) {
                qty = 1; // fallback to 1 if parsing fails
            }
        }

        final String finalQuery = query;
        final int finalQty = qty;

        Optional<Product> found = productDAO.getAllProducts().stream()
                .filter(p -> p.barcode().equalsIgnoreCase(finalQuery)
                || p.name().toLowerCase().contains(finalQuery.toLowerCase()))
                .findFirst();

        if (found.isPresent()) {
            Product p = found.get();
            if (p.stock() < finalQty) {
                showAlert("Insufficient Stock", "Only " + p.stock() + " available for " + p.name());
                return;
            }
            addToCart(p, finalQty);
            txtSearch.clear();
        } else {
            showAlert("Not Found", "Product is not registered.");
        }
    }

    /**
     * Add a product to the cart. If product already exists in cart, update
     * quantity (with stock check).
     *
     * @param p Product object
     * @param qty amount to add
     */
    private void addToCart(Product p, int qty) {
        for (int i = 0; i < cartData.size(); i++) {
            CartItem current = cartData.get(i);
            if (current.productId() == p.id()) {
                int updatedQty = current.quantity() + qty;
                if (updatedQty > p.stock()) {
                    showAlert("Stock Limit", "Total order exceeds available stock.");
                    return;
                }
                cartData.set(i, new CartItem(p.id(), p.name(), p.price(), updatedQty));
                updateTotal();
                return;
            }
        }
        // If not in cart, add as new item
        cartData.add(new CartItem(p.id(), p.name(), p.price(), qty));
        updateTotal();
    }

    /**
     * Recalculate total from cartData and update the label. Calls
     * calculateChange() to refresh change display.
     */
    private void updateTotal() {
        totalAmount = cartData.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotal.setText(CurrencyUtil.format(totalAmount));
        calculateChange();
    }

    /**
     * Calculate change based on value in txtCash. If input is invalid ->
     * display 0 change.
     *
     * Note: getCashValue() removes all non-digit characters, so cents/decimals
     * are lost. This treats input as whole currency units (e.g. integer minor
     * units not supported).
     */
    private void calculateChange() {
        try {
            BigDecimal cash = getCashValue();
            BigDecimal change = cash.subtract(totalAmount);
            lblChange.setText("Change: " + CurrencyUtil.format(change.signum() > 0 ? change : BigDecimal.ZERO));
        } catch (Exception e) {
            lblChange.setText("Change: " + CurrencyUtil.format(BigDecimal.ZERO));
        }
    }

    /**
     * Checkout: validate cart and cash, then persist transaction via SaleDAO.
     *
     * - If cash is insufficient -> show alert. - If save successful -> clear
     * cart and show success.
     */
    @FXML
    private void handleCheckout() {
        if (cartData.isEmpty()) {
            return; // nothing to do
        }
        try {
            BigDecimal cash = getCashValue();
            if (cash.compareTo(totalAmount) < 0) {
                showAlert("Failed", "Insufficient cash!");
                return;
            }
            BigDecimal change = cash.subtract(totalAmount);
            if (saleDAO.saveTransaction(cartData, totalAmount, cash, change)) {
                showAlert("Success", editingSaleId != -1 ? "Revision successful." : "Transaction successful.");
                handleClear();
            } else {
                showAlert("Error", "Failed to save transaction.");
            }
        } catch (NumberFormatException e) {
            showAlert("Invalid Input", "Cash amount is not valid.");
        }
    }

    /**
     * Parse cash amount from txtCash.
     *
     * Current implementation: strip all non-digits then construct BigDecimal.
     * Example: "1,000,000" -> "1000000" -> BigDecimal(1000000)
     *
     * Caveat: decimal fractions are removed. If you need fractional currency,
     * switch to NumberFormat/Locale-aware parsing.
     *
     * @return BigDecimal cash amount, or BigDecimal.ZERO if empty
     */
    private BigDecimal getCashValue() {
        String cleanString = txtCash.getText().replaceAll("[^\\d]", "");
        return cleanString.isEmpty() ? BigDecimal.ZERO : new BigDecimal(cleanString);
    }

    /**
     * Clear cart and reset editing state. Clears cash input and sets status
     * label to "Ready".
     */
    @FXML
    private void handleClear() {
        cartData.clear();
        txtCash.clear();
        editingSaleId = -1;
        if (lblStatus != null) {
            lblStatus.setText("Ready");
        }
        updateTotal();
    }

    /**
     * Utility: show an informational Alert dialog.
     *
     * @param title dialog title
     * @param msg message content
     */
    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
