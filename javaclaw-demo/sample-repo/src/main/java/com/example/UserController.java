package com.example;

public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public String handleGetCity(String userId) {
        // This will propagate the NPE from UserService.getUserCity()
        String city = userService.getUserCity(userId);
        return "User is located in: " + city;
    }

    public String handleGetLabel(String userId) {
        return userService.formatUserLabel(userId);
    }
}
