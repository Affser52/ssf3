package ru.mescat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mescat.dto.SaveDto;
import ru.mescat.entity.PublicKeyEntity;
import ru.mescat.service.PublicKeyService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public_key")
public class PublicKeysController {

    private final PublicKeyService publicKeyService;

    public PublicKeysController(PublicKeyService service) {
        this.publicKeyService = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        if (id == null) {
            return ResponseEntity.badRequest().body("Идентификатор ключа не должен быть пустым.");
        }

        PublicKeyEntity entity = publicKeyService.findById(id);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(entity);
    }

    @GetMapping("/byUserId/{userId}")
    public ResponseEntity<?> getByUserId(@PathVariable UUID userId) {
        if (userId == null) {
            return ResponseEntity.badRequest().body("Идентификатор пользователя не должен быть пустым.");
        }

        PublicKeyEntity entity = publicKeyService.findByUserId(userId);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(entity);
    }

    @PostMapping("/byUserIdIn")
    public ResponseEntity<?> getByUserIdIn(@RequestBody List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.badRequest().body("Список идентификаторов пользователей не должен быть пустым.");
        }

        return ResponseEntity.ok(publicKeyService.findAllByUserIdIn(userIds));
    }

    @PostMapping("/")
    public ResponseEntity<?> get(@RequestBody List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body("Список идентификаторов ключей не должен быть пустым.");
        }

        return ResponseEntity.ok(publicKeyService.findAllById(ids));
    }

    @PostMapping("/save")
    public ResponseEntity<?> save(@RequestBody SaveDto saveDto) {
        if (saveDto == null) {
            return ResponseEntity.badRequest().body("Тело запроса не должно быть пустым.");
        }
        if (saveDto.getUserId() == null) {
            return ResponseEntity.badRequest().body("Идентификатор пользователя не должен быть пустым.");
        }
        if (saveDto.getKey() == null || saveDto.getKey().length == 0) {
            return ResponseEntity.badRequest().body("Ключ не должен быть пустым.");
        }
        if (saveDto.getKey().length > 8192) {
            return ResponseEntity.badRequest().body("Размер ключа превышает допустимый предел.");
        }

        PublicKeyEntity publicKeyEntity = new PublicKeyEntity();
        publicKeyEntity.setKey(saveDto.getKey());
        publicKeyEntity.setUserId(saveDto.getUserId());

        return ResponseEntity.ok(publicKeyService.save(publicKeyEntity));
    }

    @PostMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody UUID id) {
        if (id == null) {
            return ResponseEntity.badRequest().body("Идентификатор ключа не должен быть пустым.");
        }

        PublicKeyEntity existing = publicKeyService.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        publicKeyService.deleteById(id);
        return ResponseEntity.ok("Удаление выполнено успешно.");
    }
}