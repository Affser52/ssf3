package ru.mescat.message.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import ru.mescat.keyvault.dto.NewPrivateKeyDto;
import ru.mescat.keyvault.dto.NewPrivateKeyEntity;
import ru.mescat.keyvault.service.KeyVaultService;
import ru.mescat.message.exception.RemoteServiceException;

import java.util.List;
import java.util.UUID;

@Service
public class NewPrivateKeyService {

    private KeyVaultService keyVaultService;

    public NewPrivateKeyService(KeyVaultService keyVaultService){
        this.keyVaultService=keyVaultService;
    }

    public List<NewPrivateKeyEntity> findAllByUserId(UUID userId){
        return keyVaultService.getPrivateKeys(userId);
    }

    public NewPrivateKeyEntity save(NewPrivateKeyDto newPrivateKeyDto){
        return keyVaultService.saveNewPrivateKey(newPrivateKeyDto);
    }
}
