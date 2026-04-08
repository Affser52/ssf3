package ru.mescat.message.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import ru.mescat.keyvault.dto.NewPrivateKeyDto;
import ru.mescat.keyvault.dto.NewPrivateKeyEntity;
import ru.mescat.keyvault.service.KeyVaultService;
import ru.mescat.message.event.dto.NewPrivateKey;
import ru.mescat.message.exception.RemoteServiceException;

import java.util.List;
import java.util.UUID;

@Service
public class NewPrivateKeyService {

    private KeyVaultService keyVaultService;
    private ApplicationEventPublisher applicationEventPublisher;

    public NewPrivateKeyService(KeyVaultService keyVaultService,
                                ApplicationEventPublisher applicationEventPublisher){
        this.applicationEventPublisher=applicationEventPublisher;
        this.keyVaultService=keyVaultService;
    }

    public List<NewPrivateKeyEntity> findAllByUserId(UUID userId){
        return keyVaultService.getPrivateKeys(userId);
    }

    public NewPrivateKeyEntity save(NewPrivateKeyDto newPrivateKeyDto){
        NewPrivateKeyEntity newPrivateKeyEntity = keyVaultService.saveNewPrivateKey(newPrivateKeyDto);
        applicationEventPublisher.publishEvent(new NewPrivateKey(newPrivateKeyEntity));
        return newPrivateKeyEntity;
    }
}
