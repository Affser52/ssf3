package ru.mescat.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mescat.message.dto.FileUploadInitRequestDto;
import ru.mescat.message.exception.AccessDeniedException;
import ru.mescat.message.exception.ChatNotFoundException;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.service.ChatFileService;

import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final ChatFileService chatFileService;

    public FileController(ChatFileService chatFileService) {
        this.chatFileService = chatFileService;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<?> createUploadUrl(@RequestHeader("X-User-Id") UUID userId,
                                             @RequestBody FileUploadInitRequestDto request) {
        try {
            return ResponseEntity.ok(chatFileService.createUploadUrl(userId, request));
        } catch (ChatNotFoundException | NotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Не удалось подготовить загрузку файла.");
        }
    }

    @PostMapping("/{fileId}/complete")
    public ResponseEntity<?> completeUpload(@RequestHeader("X-User-Id") UUID userId,
                                            @PathVariable UUID fileId) {
        try {
            return ResponseEntity.ok(chatFileService.completeUpload(userId, fileId));
        } catch (NotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Не удалось завершить загрузку файла.");
        }
    }

    @GetMapping("/chats/{chatId}")
    public ResponseEntity<?> getReadyFilesInChat(@RequestHeader("X-User-Id") UUID userId,
                                                 @PathVariable Long chatId) {
        try {
            return ResponseEntity.ok(chatFileService.getReadyFilesInChat(userId, chatId));
        } catch (ChatNotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @GetMapping("/{fileId}/download-url")
    public ResponseEntity<?> createDownloadUrl(@RequestHeader("X-User-Id") UUID userId,
                                               @PathVariable UUID fileId,
                                               @RequestParam(defaultValue = "false") boolean inline) {
        try {
            return ResponseEntity.ok(chatFileService.createDownloadUrl(userId, fileId, inline));
        } catch (NotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Не удалось подготовить скачивание файла.");
        }
    }
}
