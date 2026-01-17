package com.storex.controller;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import com.storex.MainApp;
import com.storex.dao.ReportDAO;
import com.storex.util.AppConfig;
import com.storex.util.CurrencyUtil;
import com.storex.util.UserSession;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/**
 * DashboardController manages the main dashboard view for the POS application.
 * 
 * This controller handles:
 * - Display of real-time statistics (revenue, sales count, top products)
 * - Navigation between different application modules (products, POS, reports, users, settings)
 * - Role-based UI visibility (CASHIER role has limited menu options)
 * - Sales chart visualization for the last 7 days
 * - Sidebar logo and app name configuration
 * - POS controller instance management
 * 
 * The dashboard provides a central hub for users to access various features based on their role
 * and displays key performance metrics from the ReportDAO.
 * 
 * @author Ardean Bima Saputra
 * @version 1.0
 */
public class DashboardController {

    @FXML
    private Label lblTodayRevenue;
    @FXML
    private Label lblTodaySales;
    @FXML
    private Label lblTopProduct;
    @FXML
    private Label lblAppFallback;
    @FXML
    private StackPane mainContent;
    @FXML
    private Button btnUsers;
    @FXML
    private Button btnReports;
    @FXML
    private Button btnProducts;
    @FXML
    private ImageView imgLogoSidebar;

    @FXML
    private BarChart<String, Number> salesChart;
    @FXML
    private CategoryAxis xAxis;
    @FXML
    private NumberAxis yAxis;

    private POSController currentPosController;

    private final ReportDAO reportDAO = new ReportDAO();

    @FXML
    public void initialize() {
        String role = UserSession.getRole();

        if ("CASHIER".equals(role)) {
            btnUsers.setManaged(false);
            btnUsers.setVisible(false);
            btnReports.setManaged(false);
            btnReports.setVisible(false);
        }

        updateSidebarLogo();
        refreshStats();

        if (salesChart != null) {
            updateChart();
        }
    }

    @FXML
    private void showProducts() {
        loadView("product");
    }

    @FXML
    public void showPOS() {
        loadView("pos");
    }

    @FXML
    private void showReports() {
        loadView("reports");
    }

    @FXML
    private void showUsers() {
        loadView("users");
    }

    @FXML
    private void showSettings() {
        loadView("settings");
    }

    public POSController getPosController() {
        return currentPosController;
    }

    /**
     * Loads and displays an FXML view in the main content pane.
     * 
     * <p>This method dynamically loads an FXML file, instantiates its controller,
     * and replaces the current view in the main content area. If the loaded controller
     * is a POSController, it is stored as the current POS controller for later use.
     * 
     * @param fxml the name of the FXML file (without extension) to be loaded from
     *             the "/fxml/" resource directory
     * 
     * @throws IOException if the FXML file cannot be loaded (caught and printed to stderr)
     * 
     * @implNote If mainContent is null, the method returns early without performing any action.
     *           Any IOExceptions during loading are caught and printed using printStackTrace().
     *           Consider using a proper logging framework instead of printStackTrace().
     */
    public void loadView(String fxml) {
        if (mainContent == null) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/fxml/" + fxml + ".fxml"));
            Parent view = loader.load();
            Object controller = loader.getController();
            if (controller instanceof POSController) {
                this.currentPosController = (POSController) controller;
            } else {
                this.currentPosController = null;
            }

            mainContent.getChildren().setAll(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateSidebarLogo() {
        if (imgLogoSidebar == null || lblAppFallback == null) {
            return;
        }
        String path = AppConfig.get("logo_path", "");
        String appName = AppConfig.get("app_name", "Ardean POS");
        File file = new File(path);
        if (!path.isEmpty() && file.exists()) {
            imgLogoSidebar.setImage(new Image(file.toURI().toString()));
            imgLogoSidebar.setVisible(true);
            imgLogoSidebar.setManaged(true);
            lblAppFallback.setVisible(false);
            lblAppFallback.setManaged(false);
        } else {
            imgLogoSidebar.setVisible(false);
            imgLogoSidebar.setManaged(false);
            lblAppFallback.setText(appName);
            lblAppFallback.setVisible(true);
            lblAppFallback.setManaged(true);
        }
    }

    /**
     * Refreshes the dashboard statistics by fetching and updating the display of today's revenue,
     * sales count, and top-selling product.
     * 
     * This method performs the following operations:
     * - Retrieves and displays today's total revenue formatted as currency in {@code lblTodayRevenue}
     * - Retrieves and displays the count of today's transactions in {@code lblTodaySales}
     * - Retrieves the list of top products and displays the top product name in {@code lblTopProduct}
     * 
     * All label components are checked for null before updating to prevent NullPointerException.
     * If no top products are available, a dash "-" is displayed as a placeholder.
     * 
     * @throws Exception if any database operation fails during report data retrieval
     * @implNote Exceptions are caught and printed to the error stream. Consider implementing
     *           proper error handling or logging instead of printStackTrace() for production code.
     */
    private void refreshStats() {
        try {
            if (lblTodayRevenue != null) {
                BigDecimal revenue = reportDAO.getTodayRevenue();
                lblTodayRevenue.setText(CurrencyUtil.format(revenue));
            }
            if (lblTodaySales != null) {
                lblTodaySales.setText(reportDAO.getTodaySalesCount() + " Transaksi");
            }
            var topProducts = reportDAO.getTopProducts();
            if (lblTopProduct != null) {
                lblTopProduct.setText(topProducts.isEmpty() ? "-" : topProducts.keySet().iterator().next());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateChart() {
        try {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Pendapatan");
            Map<String, Double> stats = reportDAO.getSalesLast7Days();
            if (stats.isEmpty()) {
                return;
            }
            stats.forEach((date, total) -> series.getData().add(new XYChart.Data<>(date, total)));
            salesChart.getData().clear();
            salesChart.getData().add(series);
            salesChart.setLegendVisible(false);
        } catch (Exception e) {
            System.err.println("Gagal memuat chart: " + e.getMessage());
        }
    }

    @FXML
    private void showHome() {
        try {
            MainApp.setRoot("dashboard");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() {
        try {
            UserSession.clear();
            MainApp.setRoot("login");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
