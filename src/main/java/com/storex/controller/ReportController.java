package com.storex.controller;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.storex.MainApp;
import com.storex.dao.ReportDAO;
import com.storex.dao.SaleDAO;
import com.storex.model.CartItem;
import com.storex.model.Sale;
import com.storex.util.CurrencyUtil;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Controller for the Sales Report view.
 *
 * <p>
 * Responsibilities: - Render a TableView of Sale records (id, date, total,
 * cash, change, status). - Format currency and date values for display. -
 * Provide a conditional "Edit" action for completed sales which: 1) voids the
 * existing transaction, 2) restores stock, 3) reloads the sale items into the
 * POS for editing.
 *
 * <p>
 * Notes / assumptions: - DAO calls (ReportDAO, SaleDAO) are synchronous and
 * executed on the JavaFX thread. - Date formatting uses the pattern
 * {@code "dd MMM yyyy HH:mm"}. - Currency formatting delegated to
 * {@link CurrencyUtil}.
 */
public class ReportController {

    /* FXML bindings: UI table + columns */
    @FXML
    private TableView<Sale> tableSales;
    @FXML
    private TableColumn<Sale, Number> colId;
    @FXML
    private TableColumn<Sale, String> colDate;
    @FXML
    private TableColumn<Sale, BigDecimal> colTotal;
    @FXML
    private TableColumn<Sale, BigDecimal> colCash;
    @FXML
    private TableColumn<Sale, BigDecimal> colChange;
    @FXML
    private TableColumn<Sale, String> colStatus;
    @FXML
    private TableColumn<Sale, Void> colAction;

    /* Data access objects and helpers */
    private final ReportDAO reportDAO = new ReportDAO(); // fetch sales list
    private final SaleDAO saleDAO = new SaleDAO();       // perform transactional operations
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    /**
     * Initialize method called by JavaFX after FXML load.
     *
     * <p>
     * Actions performed: - Binds id/date/status columns to Sale properties. -
     * Configures currency columns with {@link #setupCurrencyColumn}. -
     * Configures the action column with {@link #setupActionColumn}. - Loads
     * initial data via {@link #loadData()}.
     */
    @FXML
    public void initialize() {
        // ID column: wraps sale id into a Number property
        colId.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().id()));

        // Date column: format LocalDateTime using configured formatter
        colDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().saleDate().format(formatter)));

        // Status column: display the sale status string
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status()));

        // Setup currency columns (total, cash, change) using a shared helper
        setupCurrencyColumn(colTotal);
        setupCurrencyColumn(colCash);
        setupCurrencyColumn(colChange);

        // Setup action column (Edit button shown only for COMPLETED sales)
        setupActionColumn();

        // Load records into the table
        loadData();
    }

    /**
     * Generic helper to configure a monetary column.
     *
     * <p>
     * Behavior: - Maps the correct BigDecimal property depending on which
     * column was passed: - {@code colTotal} => Sale.totalAmount() -
     * {@code colCash} => Sale.cashAmount() - otherwise => Sale.changeAmount() -
     * Uses {@link CurrencyUtil#format(BigDecimal)} for display formatting.
     *
     * @param column the TableColumn to configure
     */
    private void setupCurrencyColumn(TableColumn<Sale, BigDecimal> column) {
        // Provide cell value mapping depending on the actual column reference
        column.setCellValueFactory(data -> {
            if (column == colTotal) {
                return new SimpleObjectProperty<>(data.getValue().totalAmount());
            }
            if (column == colCash) {
                return new SimpleObjectProperty<>(data.getValue().cashAmount());
            }
            // default: treat as change column
            return new SimpleObjectProperty<>(data.getValue().changeAmount());
        });

        // Render the BigDecimal as formatted currency string; clear cell when empty
        column.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty || amount == null ? null : CurrencyUtil.format(amount));
            }
        });
    }

    /**
     * Configure the actions column to include an "Edit" button for completed
     * sales.
     *
     * <p>
     * Behavior: - Each row gets a Button labeled "Edit". - Button is styled
     * inline (orange) as an indicator for edit action. - The Edit button
     * appears only when sale.status equals "COMPLETED" (case-insensitive). -
     * Clicking Edit invokes {@link #handleEdit(Sale)} for the corresponding
     * Sale.
     */
    private void setupActionColumn() {
        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("Edit");

            {
                // Inline styling to visually indicate action; style may be moved to CSS
                btnEdit.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
                btnEdit.setOnAction(event -> {
                    Sale sale = getTableView().getItems().get(getIndex());
                    handleEdit(sale);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    // Show the Edit button only for completed sales
                    Sale s = getTableView().getItems().get(getIndex());
                    setGraphic("COMPLETED".equalsIgnoreCase(s.status()) ? btnEdit : null);
                }
            }
        });
    }

    /**
     * Handle the "Edit" workflow for an existing sale.
     *
     * <p>
     * Flow: 1. Ask the user for confirmation (Alert.CONFIRMATION). 2. If
     * confirmed: call {@link SaleDAO#voidTransaction(int)} to void the sale. 3.
     * Fetch original sale items via {@link SaleDAO#getSaleItems(int)}. 4. If
     * items retrieved: - Obtain the application's DashboardController using
     * {@link MainApp#getDashboardController()}. - Switch UI to POS view and
     * load the old items into POS via
     * {@code dashboard.getPosController().loadOrderForEdit(...)}. - Reload the
     * report table via {@link #loadData()}.
     *
     * <p>
     * Failure modes: - If voidTransaction returns false: show error "Gagal
     * membatalkan transaksi lama." - If item details cannot be retrieved: show
     * error "Gagal mengambil detail item transaksi."
     *
     * @param sale the Sale to be edited/voided and reloaded into POS
     */
    private void handleEdit(Sale sale) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Edit transaksi #" + sale.id() + "?\n\nTransaksi lama akan dibatalkan, stok dikembalikan, dan item dimuat ulang ke Kasir.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Konfirmasi Edit Pesanan");
        confirm.setHeaderText(null);

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // Void the old transaction (should restore stock on success)
                if (saleDAO.voidTransaction(sale.id())) {
                    // Retrieve the old sale's items
                    List<CartItem> oldItems = saleDAO.getSaleItems(sale.id());

                    if (!oldItems.isEmpty()) {
                        // Get dashboard controller and hand off items to POS for editing
                        DashboardController dashboard = MainApp.getDashboardController();
                        if (dashboard != null) {
                            dashboard.showPOS();
                            dashboard.getPosController().loadOrderForEdit(oldItems, sale.id());
                            // Refresh report table after the hand-off
                            loadData();
                        }
                    } else {
                        // Could not retrieve sale items
                        showError("Gagal mengambil detail item transaksi.");
                    }
                } else {
                    // Failure to void the original sale
                    showError("Gagal membatalkan transaksi lama.");
                }
            }
        });
    }

    /**
     * Load all sales from ReportDAO and populate the TableView.
     *
     * <p>
     * On exception: logs stack trace and shows an error alert "Gagal memuat
     * data."
     *
     * Note: reportDAO.getAllSales() is invoked synchronously on the JavaFX
     * thread. Consider using a background Task for large datasets or slow I/O.
     */
    @FXML
    private void loadData() {
        try {
            var data = reportDAO.getAllSales();
            tableSales.setItems(FXCollections.observableArrayList(data));
        } catch (Exception e) {
            e.printStackTrace();
            showError("Gagal memuat data.");
        }
    }

    /**
     * Utility to show an error Alert with a given message.
     *
     * @param msg message to display in the dialog
     */
    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.showAndWait();
    }
}
