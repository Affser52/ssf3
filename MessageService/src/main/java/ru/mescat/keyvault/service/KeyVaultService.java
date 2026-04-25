package ru.mescat.keyvault.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.mescat.keyvault.dto.NewPrivateKeyDto;
import ru.mescat.keyvault.dto.NewPrivateKeyEntity;
import ru.mescat.keyvault.dto.PublicKey;
import ru.mescat.keyvault.dto.SaveDto;
import ru.mescat.message.exception.RemoteServiceException;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class KeyVaultService {

    private final RestClient restClient;

    public KeyVaultService(@Qualifier("key_vault") RestClient restClient) {
        this.restClient = restClient;
    }

    public PublicKey getKey(String id) {
        try {
            return restClient.get()
                    .uri("/api/public_key/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(PublicKey.class);
        } catch (RestClientResponseException e) {
            log.warn("KeyVaultService getKey failed: id={}, status={}", id, e.getStatusCode().value());
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("KeyVaultService unavailable during getKey: id={}, error={}", id, e.getMessage());
            throw new RemoteServiceException(503, "Сервис хранилища ключей недоступен: " + e.getMessage());
        }
    }

    public PublicKey getKeyByUserId(String id) {
        try {
            return restClient.get()
                    .uri("/api/public_key/byUserId/{userId}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(PublicKey.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("KeyVaultService getKeyByUserId: key not found for userId={}", id);
            } else {
                log.warn("KeyVaultService getKeyByUserId failed: userId={}, status={}", id, e.getStatusCode().value());
            }
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("KeyVaultService unavailable during getKeyByUserId: userId={}, error={}", id, e.getMessage());
            throw new RemoteServiceException(503, "Сервис хранилища ключей недоступен: " + e.getMessage());
        }
    }

    public List<PublicKey> getKeysByUserIdIn(List<UUID> ids) {
        try {
            return restClient.post()
                    .uri("/api/public_key/byUserIdIn")
                    .body(ids)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PublicKey>>() {});
        } catch (RestClientResponseException e) {
            log.warn("KeyVaultService getKeysByUserIdIn failed: usersCount={}, status={}",
                    ids != null ? ids.size() : 0, e.getStatusCode().value());
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("KeyVaultService unavailable during getKeysByUserIdIn: usersCount={}, error={}",
                    ids != null ? ids.size() : 0, e.getMessage());
            throw new RemoteServiceException(503, "Сервис хранилища ключей недоступен: " + e.getMessage());
        }
    }

    public List<PublicKey> getKeysByIdIn(List<UUID> ids) {
        try {
            return restClient.post()
                    .uri("/api/public_key/")
                    .body(ids)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PublicKey>>() {});
        } catch (RestClientResponseException e) {
            log.warn("KeyVaultService getKeysByIdIn failed: keysCount={}, status={}",
                    ids != null ? ids.size() : 0, e.getStatusCode().value());
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("KeyVaultService unavailable during getKeysByIdIn: keysCount={}, error={}",
                    ids != null ? ids.size() : 0, e.getMessage());
            throw new RemoteServiceException(503, "Сервис хранилища ключей недоступен: " + e.getMessage());
        }
    }

    public NewPrivateKeyEntity getPrivateKey(UUID userId) {
        try {
            return restClient.get()
                    .uri("/api/new_private_key/{userId}", userId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(NewPrivateKeyEntity.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("KeyVaultService getPrivateKey: private key not found for userId={}", userId);
            } else {
                log.warn("KeyVaultService getPrivateKey failed: userId={}, status={}", userId, e.getStatusCode().value());
            }
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("KeyVaultService unavailable during getPrivateKey: userId={}, error={}", userId, e.getMessage());
            throw new RemoteServiceException(503, "Сервис хранилища ключей недоступен: " + e.getMessage());
        }
    }

    public List<NewPrivateKeyEntity> getPrivateKeyChain(UUID userId) {
        try {
            return restClient.get()
                    .uri("/api/new_private_key/{userId}/all", userId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NewPrivateKeyEntity>>() {});
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("KeyVaultService getPrivateKeyChain: private key chain not found for userId={}", userId);
            } else {
                log.warn("KeyVaultService getPrivateKeyChain failed: userId={}, status={}", userId, e.getStatusCode().value());
            }
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("KeyVaultService unavailable during getPrivateKeyChain: userId={}, error={}", userId, e.getMessage());
            throw new RemoteServiceException(503, "Сервис хранилища ключей недоступен: " + e.getMessage());
        }
    }

    public NewPrivateKeyEntity saveNewPrivateKey(NewPrivateKeyDto newPrivateKeyDto) {
        try {
            return restClient.post()
                    .uri("/api/new_private_key/")
                    .body(newPrivateKeyDto)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(NewPrivateKeyEntity.class);
        } catch (RestClientResponseException e) {
            log.warn("KeyVaultService saveNewPrivateKey failed: userId={}, status={}",
                    newPrivateKeyDto != null ? newPrivateKeyDto.getUserId() : null, e.getStatusCode().value());
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("KeyVaultService unavailable during saveNewPrivateKey: userId={}, error={}",
                    newPrivateKeyDto != null ? newPrivateKeyDto.getUserId() : null, e.getMessage());
            throw new RemoteServiceException(503, "Сервис хранилища ключей недоступен: " + e.getMessage());
        }
    }

    public PublicKey saveKey(SaveDto saveDto) {
        try {
            return restClient.post()
                    .uri("/api/public_key/save")
                    .body(saveDto)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(PublicKey.class);
        } catch (RestClientResponseException e) {
            log.warn("KeyVaultService saveKey failed: userId={}, status={}",
                    saveDto != null ? saveDto.getUserId() : null, e.getStatusCode().value());
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("KeyVaultService unavailable during saveKey: userId={}, error={}",
                    saveDto != null ? saveDto.getUserId() : null, e.getMessage());
            throw new RemoteServiceException(503, "Сервис хранилища ключей недоступен: " + e.getMessage());
        }
    }

    public void deleteKeyById(UUID keyId) {
        try {
            restClient.post()
                    .uri("/api/public_key/delete")
                    .body(keyId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Удален публичный ключ в KeyVault: keyId={}", keyId);
        } catch (RestClientResponseException e) {
            log.warn("KeyVaultService deleteKeyById failed: keyId={}, status={}", keyId, e.getStatusCode().value());
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("KeyVaultService unavailable during deleteKeyById: keyId={}, error={}", keyId, e.getMessage());
            throw new RemoteServiceException(503, "Сервис хранилища ключей недоступен: " + e.getMessage());
        }
    }
}
