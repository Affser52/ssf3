package ru.mescat.client.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.mescat.client.service.MessageServiceProxy;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/encrypt_key")
public class EncryptKeyController {

    private final MessageServiceProxy proxy;

    public EncryptKeyController(MessageServiceProxy proxy) {
        this.proxy = proxy;
    }

    @PostMapping("/new_key")
    public ResponseEntity<?> addNewKey(@RequestBody byte[] publicKey,
                                       Authentication authentication) {
        return proxy.post("/api/encrypt_key/new_key", userId(authentication), publicKey);
    }

    @PostMapping("/byUserIdIn")
    public ResponseEntity<?> getAllKeys(@RequestBody List<UUID> uuids,
                                        Authentication authentication) {
        return proxy.post("/api/encrypt_key/byUserIdIn", userId(authentication), uuids);
    }

    @PostMapping("/byIds")
    public ResponseEntity<?> getAllKeysByIds(@RequestBody List<UUID> ids,
                                             Authentication authentication) {
        return proxy.post("/api/encrypt_key/byIds", userId(authentication), ids);
    }

    @GetMapping("/")
    public ResponseEntity<?> getKey(Authentication authentication) {
        ResponseEntity<?> response = proxy.get("/api/encrypt_key/", userId(authentication));
        if (response.getStatusCode().value() == 404) {
            return ResponseEntity.noContent().build();
        }
        return response;
    }

    @GetMapping("/new_private_key")
    public ResponseEntity<?> getNewPrivateKeyEntities(Authentication authentication) {
        return proxy.get("/api/encrypt_key/new_private_key", userId(authentication));
    }

    @GetMapping("/new_private_key/all")
    public ResponseEntity<?> getNewPrivateKeyChain(Authentication authentication) {
        return proxy.get("/api/encrypt_key/new_private_key/all", userId(authentication));
    }

    @PostMapping("/new_private_key")
    public ResponseEntity<?> saveNewPrivateKeyEntities(@RequestBody Object newPrivateKeyDto,
                                                       Authentication authentication) {
        return proxy.post("/api/encrypt_key/new_private_key", userId(authentication), newPrivateKeyDto);
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
