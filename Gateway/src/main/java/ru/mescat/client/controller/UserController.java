package ru.mescat.client.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mescat.client.service.UserServiceProxy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserServiceProxy userProxy;

    public UserController(UserServiceProxy userProxy) {
        this.userProxy = userProxy;
    }

    @GetMapping("/{username}/getId")
    public ResponseEntity<?> getIdByUsername(@PathVariable String username) {
        return userProxy.get("/user/" + path(username) + "/getId");
    }

    @GetMapping("/search_by_username/{username}")
    public ResponseEntity<?> searchByUsername(@PathVariable String username) {
        return userProxy.get("/user/search/contains/" + path(username) + "?limit=10");
    }

    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<?> getUserProfile(@PathVariable UUID userId) {
        return userProxy.get("/user/" + userId + "/profile");
    }

    private String path(String value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
