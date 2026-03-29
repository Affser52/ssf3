package ru.mescat.message.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import ru.mescat.keyvault.dto.PublicKey;
import ru.mescat.keyvault.dto.SaveDto;
import ru.mescat.keyvault.service.KeyVaultService;
import ru.mescat.message.dto.ApiResponse;
import ru.mescat.message.exception.SaveToDatabaseException;
import ru.mescat.message.exception.ValidationException;
import ru.mescat.message.websocket.WebSocketService;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class CreateKeyVault {

    private static final int MAX_PUBLIC_KEY_SIZE = 8192;

    private final KeyVaultService keyVaultService;
    private final WebSocketService webSocketService;

    public CreateKeyVault(KeyVaultService keyVaultService,
                          WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
        this.keyVaultService = keyVaultService;
    }

    public boolean addNewKey(byte[] publicKey, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ValidationException("Пользователь не аутентифицирован.");
        }
        if (publicKey == null || publicKey.length == 0) {
            throw new ValidationException("Публичный ключ не должен быть пустым.");
        }
        if (publicKey.length > MAX_PUBLIC_KEY_SIZE) {
            throw new ValidationException("Размер публичного ключа превышает допустимый предел.");
        }

        UUID userId = UUID.fromString(authentication.getName());

        PublicKey pk = keyVaultService.saveKey(new SaveDto(userId, publicKey));
        if (pk == null || pk.getId() == null) {
            throw new SaveToDatabaseException("Не удалось сохранить новый ключ.");
        }

        webSocketService.sendNotification(
                new ApiResponse(
                        0,
                        "Новый ключ успешно создан. Можно начинать общение.",
                        true,
                        OffsetDateTime.now()
                ),
                userId
        );

        return true;
    }
}