package com.example;

import java.util.HashMap;
import java.util.Map;

public class UserService {

    private final Map<String, User> users = new HashMap<>();

    public void addUser(User user) {
        users.put(user.getId(), user);
    }

    public User getUser(String id) {
        return users.get(id);
    }

    /**
     * BUG: This method does not check if the user exists before accessing
     * the address field. If the user ID is not found, getUser() returns null,
     * and calling getAddress() on null causes a NullPointerException.
     * Additionally, the address itself could be null even if the user exists.
     */
    public String getUserCity(String userId) {
        User user = getUser(userId);
        // Missing null check on user — will throw NPE if user not found
        return user.getAddress().getCity();
    }

    public String formatUserLabel(String userId) {
        User user = getUser(userId);
        // Missing null check — NPE if user not found
        return user.getName().toUpperCase() + " <" + user.getEmail() + ">";
    }
}
