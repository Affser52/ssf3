package ru.mescat.info.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mescat.info.entity.UserSettingsEntity;
import ru.mescat.info.service.UserSettingsService;

import java.util.UUID;

@RestController
@RequestMapping("/user_settings")
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    public UserSettingsController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserSettingsEntity> findById(@PathVariable UUID userId) {
        UserSettingsEntity settings = userSettingsService.findOrCreateById(userId);
        if (settings == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(settings);
    }

    @PostMapping
    public ResponseEntity<UserSettingsEntity> save(@RequestBody UserSettingsEntity entity) {
        UserSettingsEntity saved = userSettingsService.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/{userId}/allow-writing")
    public ResponseEntity<Void> setAllowWriting(@PathVariable UUID userId,
                                                @RequestParam boolean value) {
        boolean updated = userSettingsService.setAllowWriting(value, userId);
        return updated
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PatchMapping("/{userId}/allow-add-chat")
    public ResponseEntity<Void> setAllowAddChat(@PathVariable UUID userId,
                                                @RequestParam boolean value) {
        boolean updated = userSettingsService.setAllowAddChat(value, userId);
        return updated
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }
}
