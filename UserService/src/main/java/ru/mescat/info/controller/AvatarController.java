package ru.mescat.info.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mescat.info.dto.AvatarUploadInitRequestDto;
import ru.mescat.info.service.AvatarService;

import java.util.UUID;

@RestController
@RequestMapping("/user/{userId}/avatar")
public class AvatarController {

    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<?> createUploadUrl(@PathVariable UUID userId,
                                             @RequestBody AvatarUploadInitRequestDto request) {
        try {
            return ResponseEntity.ok(avatarService.createUploadUrl(userId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<?> completeUpload(@PathVariable UUID userId,
                                            @PathVariable UUID uploadId) {
        try {
            return ResponseEntity.ok(avatarService.completeUpload(userId, uploadId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
