package ru.mescat.client.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mescat.client.dto.FileUploadInitRequestDto;
import ru.mescat.client.service.MessageServiceProxy;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final MessageServiceProxy proxy;

    public FileController(MessageServiceProxy proxy) {
        this.proxy = proxy;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<?> createUploadUrl(@RequestBody FileUploadInitRequestDto dto,
                                             Authentication authentication) {
        return proxy.post("/api/files/upload-url", userId(authentication), dto);
    }

    @PostMapping("/{fileId}/complete")
    public ResponseEntity<?> completeUpload(@PathVariable UUID fileId,
                                            Authentication authentication) {
        return proxy.post("/api/files/" + fileId + "/complete", userId(authentication), Map.of());
    }

    @GetMapping("/chats/{chatId}")
    public ResponseEntity<?> getReadyFilesInChat(@PathVariable Long chatId,
                                                 Authentication authentication) {
        return proxy.get("/api/files/chats/" + chatId, userId(authentication));
    }

    @GetMapping("/{fileId}/download-url")
    public ResponseEntity<?> createDownloadUrl(@PathVariable UUID fileId,
                                               @RequestParam(defaultValue = "false") boolean inline,
                                               Authentication authentication) {
        return proxy.get("/api/files/" + fileId + "/download-url?inline=" + inline, userId(authentication));
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
