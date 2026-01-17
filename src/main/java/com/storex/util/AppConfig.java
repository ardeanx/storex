package com.storex.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class AppConfig {

    private static final Map<String, String> settings = new HashMap<>();

    public static void load() {
        settings.clear();
        try (Connection conn = DatabaseHelper.connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM settings")) {
            while (rs.next()) {
                settings.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String get(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }

    public static void refresh() {
        load();
    }
}
