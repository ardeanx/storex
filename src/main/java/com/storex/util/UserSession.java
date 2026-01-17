package com.storex.util;

import com.storex.model.User;

public class UserSession {

    private static User loggedInUser;

    public static void init(User user) {
        loggedInUser = user;
    }

    public static User getUser() {
        return loggedInUser;
    }

    public static String getUsername() {
        return (loggedInUser != null) ? loggedInUser.username() : "Guest";
    }

    public static int getUserId() {
        return (loggedInUser != null) ? loggedInUser.id() : 0;
    }

    public static String getRole() {
        return (loggedInUser != null) ? loggedInUser.role() : "CASHIER";
    }

    public static void clear() {
        loggedInUser = null;
    }
}
