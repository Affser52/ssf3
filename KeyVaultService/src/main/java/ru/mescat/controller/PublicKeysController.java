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

    private PublicKeyService publicKeyService;

    public PublicKeysController(PublicKeyService service){
        this.publicKeyService=service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicKeyEntity> get(@PathVariable UUID id){
        return ResponseEntity.ok(publicKeyService.findById(id));
    }

    @GetMapping("/byUserId/{userId}")
    public ResponseEntity<PublicKeyEntity> getByUserId(@PathVariable UUID userId){
        return ResponseEntity.ok(publicKeyService.findByUserId(userId));
    }

    @PostMapping("/byUserIdIn")
    public ResponseEntity<List<PublicKeyEntity>> getByUserIdIn(@RequestBody List<UUID> userIds){
        return ResponseEntity.ok(publicKeyService.findAllByUserIdIn(userIds));
    }

    @PostMapping("/")
    public ResponseEntity<List<PublicKeyEntity>> get(@RequestBody List<UUID> userIds){
        return ResponseEntity.ok(publicKeyService.findAllById(userIds));
    }

    @PostMapping("/save")
    public ResponseEntity<PublicKeyEntity> save(@RequestBody SaveDto saveDto){
        if(saveDto.getKey()==null || saveDto.getUserId()==null){
            return ResponseEntity.ok(null);
        }
        PublicKeyEntity publicKeyEntity = new PublicKeyEntity();
        publicKeyEntity.setKey(saveDto.getKey());
        publicKeyEntity.setUserId(saveDto.getUserId());
        return ResponseEntity.ok(publicKeyService.save(publicKeyEntity));
    }

    @PostMapping("/delete")
    public ResponseEntity delete(@RequestBody UUID id){
        publicKeyService.deleteById(id);
        return ResponseEntity.ok(null);
    }
}
