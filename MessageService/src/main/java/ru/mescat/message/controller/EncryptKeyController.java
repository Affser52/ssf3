package ru.mescat.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ru.mescat.keyvault.dto.NewPrivateKeyDto;
import ru.mescat.keyvault.dto.PublicKey;
import ru.mescat.keyvault.service.KeyVaultService;
import ru.mescat.message.exception.MaxActiveKeysLimitExceededException;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.exception.RemoteServiceException;
import ru.mescat.message.exception.SaveToDatabaseException;
import ru.mescat.message.exception.ValidationException;
import ru.mescat.message.service.CreateKeyVault;
import ru.mescat.message.service.NewPrivateKeyService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/encrypt_key")
public class EncryptKeyController {

    private final KeyVaultService keyVaultService;
    private final CreateKeyVault createKeyVault;
    private final NewPrivateKeyService newPrivateKeyService;

    public EncryptKeyController(KeyVaultService keyVaultService,
                                CreateKeyVault createKeyVault,
                                NewPrivateKeyService newPrivateKeyService) {
        this.newPrivateKeyService = newPrivateKeyService;
        this.createKeyVault = createKeyVault;
        this.keyVaultService = keyVaultService;
    }

    @PostMapping("/new_key")
    public ResponseEntity<?> addNewKey(@RequestBody byte[] publicKey,
                                       Authentication authentication) {
        try {
            createKeyVault.addNewKey(publicKey, authentication);
            return ResponseEntity.ok("Ключ успешно создан.");
        } catch (ValidationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (NotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (MaxActiveKeysLimitExceededException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (SaveToDatabaseException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping("/byUserIdIn")
    public ResponseEntity<?> getAllKeys(@RequestBody List<UUID> uuids) {
        try {
            if (uuids == null || uuids.isEmpty()) {
                return ResponseEntity.badRequest().body("Список идентификаторов пользователей не должен быть пустым.");
            }

            List<PublicKey> keys = keyVaultService.getKeysByUserIdIn(uuids);
            if (keys == null || keys.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(keys);
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }

    @GetMapping("/byUserId")
    public ResponseEntity<?> getKeyByUsername() {
        try {
            UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
            PublicKey key = keyVaultService.getKeyByUserId(userId.toString());

            if (key == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(key);
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }

    @GetMapping("/")
    public ResponseEntity<?> getKey() {
        try {
            UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
            PublicKey key = keyVaultService.getKeyByUserId(userId.toString());

            if (key == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(key);
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }

    @GetMapping("/new_private_key")
    public ResponseEntity<?> getNewPrivateKeyEntities() {
        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());

        try {
            List<?> result = newPrivateKeyService.findAllByUserId(userId);
            if (result == null || result.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(result);
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }

    @PostMapping("/new_private_key")
    public ResponseEntity<?> saveNewPrivateKeyEntities(@RequestBody NewPrivateKeyDto newPrivateKeyDto) {
        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());

        if (newPrivateKeyDto == null) {
            return ResponseEntity.badRequest().body("Тело запроса не должно быть пустым.");
        }
        if (newPrivateKeyDto.getUserId() == null || !newPrivateKeyDto.getUserId().equals(userId)) {
            return ResponseEntity.badRequest().body("Идентификатор пользователя не совпадает с авторизованным пользователем.");
        }
        if (newPrivateKeyDto.getKey() == null || newPrivateKeyDto.getKey().length == 0) {
            return ResponseEntity.badRequest().body("Приватный ключ не должен быть пустым.");
        }
        if (newPrivateKeyDto.getPublicKey() == null) {
            return ResponseEntity.badRequest().body("Публичный ключ не должен быть пустым.");
        }

        try {
            Object saved = newPrivateKeyService.save(newPrivateKeyDto);
            if (saved == null) {
                return ResponseEntity.status(500).body("Не удалось сохранить приватный ключ.");
            }

            return ResponseEntity.ok(saved);
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }
}