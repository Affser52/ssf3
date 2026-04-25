package ru.mescat.info.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mescat.info.dto.UserProfileDto;
import ru.mescat.info.entity.UserEntity;
import ru.mescat.info.service.UserService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserEntity> save(@RequestBody UserEntity user) {
        UserEntity savedUser = userService.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserEntity> findById(@PathVariable UUID id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<UserEntity> searchByUsername(@RequestParam String username) {
        return userService.searchByUsername(username)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search/contains/{username}")
    public ResponseEntity<List<UserEntity>> findByUsernameContaining(@PathVariable String username,
                                                                     @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(userService.findByUsernameContaining(username, limit));
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileDto> findProfileById(@PathVariable UUID id) {
        return userService.findProfileById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(@PathVariable UUID id,
                                               @RequestBody String password) {
        boolean updated = userService.updatePassword(id, password);
        return updated
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PatchMapping("/{id}/blocked")
    public ResponseEntity<Void> updateBlocked(@PathVariable UUID id,
                                              @RequestParam boolean blocked) {
        boolean updated = userService.updateBlocked(id, blocked);
        return updated
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PatchMapping("/{id}/online")
    public ResponseEntity<Void> updateOnline(@PathVariable UUID id,
                                             @RequestParam boolean online) {
        boolean updated = userService.updateOnline(id, online);
        return updated
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PatchMapping("/{id}/username")
    public ResponseEntity<?> updateUsername(@PathVariable UUID id,
                                            @RequestParam String username) {
        try {
            boolean updated = userService.updateUsername(id, username);
            return updated
                    ? ResponseEntity.ok().build()
                    : ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/avatar-url")
    public ResponseEntity<?> updateAvatarUrl(@PathVariable UUID id,
                                             @RequestBody String avatarUrl) {
        try {
            boolean updated = userService.updateAvatarUrl(id, avatarUrl);
            return updated
                    ? ResponseEntity.ok().build()
                    : ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/getAllById")
    public ResponseEntity<List<UserEntity>> getAllUsersById(@RequestBody List<UUID> usersId){
        List<UserEntity> users = userService.findAllByIds(usersId);
        if (users==null){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{username}/getId")
    public ResponseEntity<UUID> getIdByUsername(@PathVariable String username){
        if(username==null){
            return ResponseEntity.notFound().build();
        }
        UUID userId = userService.getIdByUsername(username);

        if(userId==null){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(userId);
    }
}
