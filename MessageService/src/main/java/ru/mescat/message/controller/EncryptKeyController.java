package ru.mescat.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.mescat.keyvault.dto.PublicKey;
import ru.mescat.keyvault.dto.UserPublicKeyDto;
import ru.mescat.keyvault.service.KeyVaultService;
import ru.mescat.message.exception.MaxActiveKeysLimitExceededException;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.exception.SaveToDatabaseException;
import ru.mescat.message.map.PublicKeyUserPublicKeyDtoMapper;
import ru.mescat.message.service.CreateKeyVault;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/encrypt_key")
public class EncryptKeyController {

    private KeyVaultService keyVaultService;
    private Integer maxActiveKey;
    private CreateKeyVault createKeyVault;

    public EncryptKeyController(KeyVaultService keyVaultService,
                                CreateKeyVault createKeyVault){
        this.createKeyVault=createKeyVault;
        this.keyVaultService=keyVaultService;
    }

    @PostMapping("/new_key")
    public ResponseEntity<?> addNewKey(@RequestBody byte[] publicKey, Authentication authentication){
        try{
            boolean ok = createKeyVault.addNewKey(publicKey,authentication);
            return ResponseEntity.ok(ok);
        } catch (NotFoundException e){
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (SaveToDatabaseException e){
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (MaxActiveKeysLimitExceededException e){
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @PostMapping("/")
    public ResponseEntity<?> getAllKeys(@RequestBody List<UUID> uuids){
        try{
            List<PublicKey> keys = keyVaultService.getKeys(uuids);
            if(keys==null){
                return ResponseEntity.ok(null);
            }
            return ResponseEntity.ok(PublicKeyUserPublicKeyDtoMapper.convert(keys));
        } catch (Exception e){
            return ResponseEntity.status(502).body(e.getMessage());
        }
    }
}
