package com.storex.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.storex.model.Product;
import com.storex.util.DatabaseHelper;

/**
 * Data Access Object for {@link Product} entities.
 *
 * <p>
 * Responsibilities: - Read and write Product records from/to the database. -
 * Map JDBC {@link ResultSet} rows to {@link Product} model objects.
 *
 * <p>
 * Notes: - All methods swallow SQLExceptions (printStackTrace) and return
 * null/false on failure. - All DB calls are executed synchronously; caller must
 * avoid running heavy DB work on UI thread. - This DAO uses direct JDBC
 * connections from {@link DatabaseHelper#connect()} each call.
 */
public class ProductDAO {

    /**
     * Retrieve all products ordered by name.
     *
     * @return List of Product (empty list if none or on error)
     */
    public List<Product> getAllProducts() {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM products ORDER BY name";

        // try-with-resources ensures JDBC resources are closed
        try (Connection conn = DatabaseHelper.connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                // map each result row to a Product and add to list
                list.add(mapResultSetToProduct(rs));
            }
        } catch (SQLException e) {
            // Logging only; caller receives an empty list on error
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Get a single product by its numeric id.
     *
     * @param id product id
     * @return Product instance when found; null if not found or on error
     */
    public Product getProductById(int id) {
        String sql = "SELECT * FROM products WHERE id = ?";
        try (Connection conn = DatabaseHelper.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // Return mapped product for the first row
                    return mapResultSetToProduct(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Insert a new product into the database.
     *
     * @param p Product to insert
     * @return true when inserted (executeUpdate() > 0), false on failure
     */
    public boolean insert(Product p) {
        String sql = "INSERT INTO products (barcode, name, description, price, stock, category) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseHelper.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, p.barcode());
            pstmt.setString(2, p.name());
            pstmt.setString(3, p.description());
            pstmt.setBigDecimal(4, p.price());
            pstmt.setInt(5, p.stock());
            pstmt.setString(6, p.category());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            // Returns false on SQL error; stack trace printed for debugging
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update an existing product record.
     *
     * @param p Product containing updated values (must include valid id)
     * @return true when update affected > 0 rows, false on failure
     *
     * <p>
     * IMPORTANT: SQL updates {@code category_id=?} but sets a String via
     * {@code p.category()}. This indicates a likely schema/code mismatch
     * (category vs category_id). Verify DB schema and adjust code or schema
     * accordingly.
     */
    public boolean update(Product p) {
        String sql = "UPDATE products SET barcode=?, name=?, description=?, price=?, stock=?, category_id=? WHERE id=?";
        try (Connection conn = DatabaseHelper.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, p.barcode());
            pstmt.setString(2, p.name());
            pstmt.setString(3, p.description());
            pstmt.setBigDecimal(4, p.price());
            pstmt.setInt(5, p.stock());
            pstmt.setString(6, p.category()); // potential type mismatch if category_id expects integer
            pstmt.setInt(7, p.id());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete a product by id.
     *
     * @param id product id
     * @return true when delete affected > 0 rows, false on failure
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM products WHERE id = ?";
        try (Connection conn = DatabaseHelper.connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Map the current row of ResultSet to a Product object.
     *
     * @param rs ResultSet positioned at a valid row
     * @return Product constructed from the row values
     * @throws SQLException if any column access fails
     */
    private Product mapResultSetToProduct(ResultSet rs) throws SQLException {
        return new Product(
                rs.getInt("id"),
                rs.getString("barcode"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getInt("stock"),
                rs.getString("category")
        );
    }
}
