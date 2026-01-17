package com.storex.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseHelper {

    private static final String URL = "jdbc:mysql://localhost:3306/storex";
    private static final String USER = "root";
    private static final String PASS = "00000000";

    public static Connection connect() throws SQLException {
        try {
            return DriverManager.getConnection(URL, USER, PASS);
        } catch (SQLException e) {
            System.err.println("Database Connection Failed! Is MySQL running? " + e.getMessage());
            throw e;
        }
    }
}
