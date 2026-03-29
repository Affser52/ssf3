package ru.mescat.keyvault.service;

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
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
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
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
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
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "Сервис хранилища ключей недоступен: " + e.getMessage());
        }
    }

    public List<NewPrivateKeyEntity> getPrivateKeys(UUID userId) {
        try {
            return restClient.get()
                    .uri("/api/new_private_key/{userId}", userId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NewPrivateKeyEntity>>() {});
        } catch (RestClientResponseException e) {
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
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
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
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
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
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
        } catch (RestClientResponseException e) {
            throw new RemoteServiceException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "Сервис хранилища ключей недоступен: " + e.getMessage());
        }
    }
}