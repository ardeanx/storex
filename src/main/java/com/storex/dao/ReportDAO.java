package com.storex.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.storex.model.Sale;
import com.storex.util.DatabaseHelper;

/**
 * Data Access Object for reporting-related queries.
 *
 * <p>
 * Responsibilities: - Retrieve sales list for reports. - Provide aggregate
 * metrics such as today's revenue and sales count. - Provide top-selling
 * products and sales summary for the last 7 days.
 *
 * <p>
 * Notes: - Uses direct JDBC via {@link DatabaseHelper#connect()} and
 * try-with-resources for resource cleanup. - SQL uses MySQL-specific functions
 * (DATE(), CURDATE(), DATE_FORMAT(), NOW()). Portability to other RDBMS may
 * require changes. - Monetary values are returned as {@link BigDecimal} for
 * getTodayRevenue and as {@code double} in getSalesLast7Days. Prefer consistent
 * monetary types (BigDecimal) to avoid precision issues.
 */
public class ReportDAO {

    /**
     * Retrieve all sales ordered by transaction_date descending.
     *
     * <p>
     * Maps each row to {@link Sale} using timestamp -> LocalDateTime
     * conversion. Returns an empty list on error.
     *
     * @return list of Sale objects (empty if none or on failure)
     */
    public List<Sale> getAllSales() {
        List<Sale> list = new ArrayList<>();
        String sql = "SELECT id, transaction_date, total_amount, cash_amount, change_amount, user_id, status FROM sales ORDER BY transaction_date DESC";

        // try-with-resources closes Connection, Statement and ResultSet automatically
        try (Connection conn = DatabaseHelper.connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new Sale(
                        rs.getInt("id"),
                        rs.getTimestamp("transaction_date").toLocalDateTime(),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("cash_amount"),
                        rs.getBigDecimal("change_amount"),
                        rs.getInt("user_id"),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            // Exception swallowed after printing stack trace — consider proper logging or rethrowing a checked exception
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Compute today's revenue (sum of total_amount for today's rows).
     *
     * <p>
     * SQL note: this query references column {@code sale_date} while other
     * queries use {@code transaction_date}. This indicates a likely column-name
     * inconsistency — verify DB schema.
     *
     * @return BigDecimal sum of today's total_amount or BigDecimal.ZERO on
     * error
     */
    public BigDecimal getTodayRevenue() {
        String sql = "SELECT SUM(total_amount) FROM sales WHERE DATE(sale_date) = CURDATE()";
        try (Connection conn = DatabaseHelper.connect(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getBigDecimal(1) != null ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    /**
     * Count number of sales for today.
     *
     * <p>
     * Same column-name inconsistency as {@link #getTodayRevenue()} (uses
     * {@code sale_date}).
     *
     * @return integer count of today's sales or 0 on error
     */
    public int getTodaySalesCount() {
        String sql = "SELECT COUNT(*) FROM sales WHERE DATE(sale_date) = CURDATE()";
        try (Connection conn = DatabaseHelper.connect(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Get top 5 products by sold quantity.
     *
     * <p>
     * Returns a Map mapping product name -> total quantity sold. Uses
     * aggregation and JOIN between sale_items and products.
     *
     * @return Map of product name to total quantity (empty map on error)
     */
    public Map<String, Integer> getTopProducts() {
        Map<String, Integer> data = new HashMap<>();
        String sql = "SELECT p.name, SUM(si.quantity) as total_qty "
                + "FROM sale_items si JOIN products p ON si.product_id = p.id "
                + "GROUP BY p.id ORDER BY total_qty DESC LIMIT 5";
        try (Connection conn = DatabaseHelper.connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                data.put(rs.getString("name"), rs.getInt("total_qty"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return data;
    }

    /**
     * Get sales totals for the last 7 days.
     *
     * <p>
     * Returns a LinkedHashMap where the keys are formatted date strings (e.g.
     * "01 Jan") and the values are totals as doubles. Uses DATE_FORMAT and
     * groups by date. Ordering preserved by LinkedHashMap to keep chronological
     * order.
     *
     * @return LinkedHashMap of date label -> total sales (empty map on error)
     */
    public Map<String, Double> getSalesLast7Days() {
        Map<String, Double> data = new LinkedHashMap<>();
        String sql = "SELECT DATE_FORMAT(transaction_date, '%d %b') as tgl, SUM(total_amount) as total "
                + "FROM sales "
                + "WHERE transaction_date >= DATE_SUB(NOW(), INTERVAL 7 DAY) "
                + "GROUP BY DATE_FORMAT(transaction_date, '%d %b'), DATE(transaction_date) "
                + "ORDER BY DATE(transaction_date) ASC";

        try (Connection conn = DatabaseHelper.connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                data.put(rs.getString("tgl"), rs.getDouble("total"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return data;
    }
}
