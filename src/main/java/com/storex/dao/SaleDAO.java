package com.storex.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.storex.model.CartItem;
import com.storex.util.DatabaseHelper;
import com.storex.util.UserSession;

public class SaleDAO {

    public boolean saveTransaction(List<CartItem> cart, BigDecimal total, BigDecimal cash, BigDecimal change) {
        Connection conn = null;
        PreparedStatement psSale = null;
        PreparedStatement psItems = null;
        PreparedStatement psUpdateStock = null;

        try {
            conn = DatabaseHelper.connect();
            conn.setAutoCommit(false);

            String sqlSale = "INSERT INTO sales (total_amount, cash_amount, change_amount, user_id, status) VALUES (?, ?, ?, ?, 'COMPLETED')";
            psSale = conn.prepareStatement(sqlSale, Statement.RETURN_GENERATED_KEYS);
            psSale.setBigDecimal(1, total);
            psSale.setBigDecimal(2, cash);
            psSale.setBigDecimal(3, change);
            psSale.setInt(4, UserSession.getUserId());
            psSale.executeUpdate();

            ResultSet rs = psSale.getGeneratedKeys();
            int saleId = (rs.next()) ? rs.getInt(1) : 0;
            if (saleId == 0) {
                throw new SQLException("Gagal mendapatkan ID Penjualan.");
            }
            String sqlItems = "INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?)";
            String sqlStock = "UPDATE products SET stock = stock - ? WHERE id = ?";

            psItems = conn.prepareStatement(sqlItems);
            psUpdateStock = conn.prepareStatement(sqlStock);

            for (CartItem item : cart) {
                psItems.setInt(1, saleId);
                psItems.setInt(2, item.productId());
                psItems.setInt(3, item.quantity());
                psItems.setBigDecimal(4, item.price());
                psItems.setBigDecimal(5, item.getSubtotal());
                psItems.addBatch();

                psUpdateStock.setInt(1, item.quantity());
                psUpdateStock.setInt(2, item.productId());
                psUpdateStock.addBatch();
            }

            psItems.executeBatch();
            psUpdateStock.executeBatch();

            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("SAVE TRANSACTION ERROR: " + e.getMessage());
            if (conn != null) try {
                conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return false;
        } finally {
            closeResources(conn, psSale, psItems, psUpdateStock);
        }
    }

    public List<CartItem> getSaleItems(int saleId) {
        List<CartItem> items = new ArrayList<>();
        String sql = "SELECT si.product_id, p.name, si.unit_price, si.quantity "
                + "FROM sale_items si JOIN products p ON si.product_id = p.id WHERE si.sale_id = ?";

        try (Connection conn = DatabaseHelper.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new CartItem(
                            rs.getInt("product_id"),
                            rs.getString("name"),
                            rs.getBigDecimal("unit_price"),
                            rs.getInt("quantity")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("GET SALE ITEMS ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        return items;
    }

    public boolean voidTransaction(int saleId) {
        Connection conn = null;
        try {
            conn = DatabaseHelper.connect();
            conn.setAutoCommit(false);
            String getItemsSql = "SELECT product_id, quantity FROM sale_items WHERE sale_id = ?";
            try (PreparedStatement psGet = conn.prepareStatement(getItemsSql)) {
                psGet.setInt(1, saleId);
                ResultSet rs = psGet.executeQuery();

                String updateStockSql = "UPDATE products SET stock = stock + ? WHERE id = ?";
                try (PreparedStatement psStock = conn.prepareStatement(updateStockSql)) {
                    while (rs.next()) {
                        psStock.setInt(1, rs.getInt("quantity"));
                        psStock.setInt(2, rs.getInt("product_id"));
                        psStock.addBatch();
                    }
                    psStock.executeBatch();
                }
            }
            String voidSql = "UPDATE sales SET status = 'VOID' WHERE id = ?";
            try (PreparedStatement psVoid = conn.prepareStatement(voidSql)) {
                psVoid.setInt(1, saleId);
                psVoid.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("VOID TRANSACTION ERROR: " + e.getMessage());
            if (conn != null) try {
                conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return false;
        } finally {
            closeResources(conn);
        }
    }

    private void closeResources(AutoCloseable... resources) {
        for (AutoCloseable res : resources) {
            if (res != null) {
                try {
                    res.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
