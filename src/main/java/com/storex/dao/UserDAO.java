package com.storex.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.storex.model.User;
import com.storex.util.DatabaseHelper;

public class UserDAO {

    public User authenticate(String username, String password) {
        String sql = "SELECT id, username, full_name, role FROM users WHERE username = ? AND password = ? AND status = 1";
        try (Connection conn = DatabaseHelper.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("role")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT id, username, full_name, role FROM users WHERE status = 1 ORDER BY id DESC";
        try (Connection conn = DatabaseHelper.connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("role")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean insert(String username, String password, String fullName, String role) {
        String sql = "INSERT INTO users (username, password, full_name, role, status) VALUES (?, ?, ?, ?, 1)";
        try (Connection conn = DatabaseHelper.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, fullName);
            ps.setString(4, role);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean update(int id, String username, String fullName, String role) {
        String sql = "UPDATE users SET username = ?, full_name = ?, role = ? WHERE id = ?";
        try (Connection conn = DatabaseHelper.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, fullName);
            ps.setString(3, role);
            ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean delete(int id) {
        String sql = "UPDATE users SET status = 0 WHERE id = ?";
        try (Connection conn = DatabaseHelper.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(id));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
