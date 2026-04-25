package ru.mescat.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mescat.message.dto.SendEncryptKeyDto;
import ru.mescat.message.dto.kafka.KeyDelete;
import ru.mescat.message.exception.ChatNotFoundException;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.exception.SaveToDatabaseException;
import ru.mescat.message.exception.UserBlockedException;
import ru.mescat.message.service.SendMessageKeyService;

import java.util.UUID;

@RestController
@RequestMapping("/api/encrypt_message_key")
public class EncryptMessageKeyController {

    private final SendMessageKeyService sendMessageKeyService;

    public EncryptMessageKeyController(SendMessageKeyService sendMessageKeyService) {
        this.sendMessageKeyService = sendMessageKeyService;
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteMessageKey(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody KeyDelete keyDelete) {
        if (keyDelete == null || keyDelete.getKeyId() == null) {
            return ResponseEntity.badRequest().body("keyId обязателен.");
        }

        sendMessageKeyService.deleteByIdForUser(keyDelete.getKeyId(), userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendKeys(@RequestHeader("X-User-Id") UUID userId,
                                      @RequestBody SendEncryptKeyDto sendEncryptKeyDto) {
        try {
            return ResponseEntity.ok(sendMessageKeyService.sendEncryptKey(userId, sendEncryptKeyDto));
        } catch (SaveToDatabaseException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (ChatNotFoundException | NotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (UserBlockedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingKeys(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(sendMessageKeyService.getPendingKeysForUser(userId));
    }
}
