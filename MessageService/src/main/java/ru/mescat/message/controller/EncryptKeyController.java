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
import ru.mescat.message.service.CreateKeyVault;
import ru.mescat.message.service.NewPrivateKeyService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/encrypt_key")
public class EncryptKeyController {

    private KeyVaultService keyVaultService;
    private CreateKeyVault createKeyVault;
    private NewPrivateKeyService newPrivateKeyService;

    public EncryptKeyController(KeyVaultService keyVaultService,
                                CreateKeyVault createKeyVault,
                                NewPrivateKeyService newPrivateKeyService){
        this.newPrivateKeyService=newPrivateKeyService;
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

    @PostMapping("/byUserIdIn")
    public ResponseEntity<?> getAllKeys(@RequestBody List<UUID> uuids){
        try{
            List<PublicKey> keys = keyVaultService.getKeysByUserIdIn(uuids);
            if(keys==null){
                return ResponseEntity.ok(null);
            }
            return ResponseEntity.ok(keys);
        } catch (Exception e){
            return ResponseEntity.status(502).body(e.getMessage());
        }
    }

    @GetMapping("/byUserId")
    public ResponseEntity<?> getKeyByUsername(){
        try{
            UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
            PublicKey key = keyVaultService.getKeyByUserId(userId.toString());
            if(key==null){
                return ResponseEntity.ok(null);
            }
            return ResponseEntity.ok(key);
        } catch (Exception e){
            return ResponseEntity.status(502).body(e.getMessage());
        }
    }

    @GetMapping("/")
    public ResponseEntity<?> getKey(){
        try{
            UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
            PublicKey key = keyVaultService.getKeyByUserId(userId.toString());
            if(key==null){
                return ResponseEntity.ok(null);
            }
            return ResponseEntity.ok(key);
        } catch (Exception e){
            return ResponseEntity.status(502).body(e.getMessage());
        }
    }

    @GetMapping("/new_private_key")
    public ResponseEntity<?> getNewPrivateKeyEntities(){
        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        try{
            return ResponseEntity.ok(newPrivateKeyService.findAllByUserId(userId));
        } catch (RemoteServiceException e){
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }

    @PostMapping("/new_private_key")
    public ResponseEntity<?> saveNewPrivateKeyEntities(@RequestBody NewPrivateKeyDto newPrivateKeyDto){
        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        if(!newPrivateKeyDto.getUserId().equals(userId)){
            return ResponseEntity.status(400).build();
        }
        try{
            return ResponseEntity.ok(newPrivateKeyService.save(newPrivateKeyDto));
        } catch (RemoteServiceException e){
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }
}
