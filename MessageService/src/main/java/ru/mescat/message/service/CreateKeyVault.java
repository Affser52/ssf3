package ru.mescat.message.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import ru.mescat.keyvault.dto.PublicKey;
import ru.mescat.keyvault.dto.SaveDto;
import ru.mescat.keyvault.service.KeyVaultService;
import ru.mescat.message.dto.ApiResponse;
import ru.mescat.message.event.dto.NewPublicKey;
import ru.mescat.message.exception.SaveToDatabaseException;
import ru.mescat.message.exception.ValidationException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class CreateKeyVault {

    private static final int MAX_PUBLIC_KEY_SIZE = 8192;

    private final KeyVaultService keyVaultService;
    ApplicationEventPublisher applicationEventPublisher;

    public CreateKeyVault(KeyVaultService keyVaultService,
                          ApplicationEventPublisher applicationEventPublisher) {
        this.keyVaultService = keyVaultService;
        this.applicationEventPublisher=applicationEventPublisher;
    }

    public boolean addNewKey(UUID userId, byte[] publicKey) {
        if (publicKey == null || publicKey.length == 0) {
            throw new ValidationException("Публичный ключ не должен быть пустым.");
        }
        if (publicKey.length > MAX_PUBLIC_KEY_SIZE) {
            throw new ValidationException("Размер публичного ключа превышает допустимый предел.");
        }


        PublicKey pk = keyVaultService.saveKey(new SaveDto(userId, publicKey));
        if (pk == null || pk.getId() == null) {
            throw new SaveToDatabaseException("Не удалось сохранить новый ключ.");
        }

        applicationEventPublisher.publishEvent(new NewPublicKey(pk));

        return true;
    }
}