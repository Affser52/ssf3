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

    private RestClient restClient;

    public KeyVaultService(@Qualifier("key_vault") RestClient restClient){
        this.restClient = restClient;
    }


    public PublicKey getKey(String id){
        try{
            PublicKey keys = restClient.get()
                    .uri("/api/public_key/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(PublicKey.class);
            return keys;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public PublicKey getKeyByUserId(String id){
        try{
            PublicKey keys = restClient.get()
                    .uri("/byUserId/{userId}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(PublicKey.class);
            return keys;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public List<PublicKey> getKeysByUserIdIn(List<UUID> ids){
        try{
            List<PublicKey> keys = restClient.post()
                    .uri("/api/public_key/byUserIdIn")
                    .body(ids)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PublicKey>>() {
                    });
            return keys;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public List<NewPrivateKeyEntity> getPrivateKeys(UUID userId){
        try{
            List<NewPrivateKeyEntity> list = restClient.get()
                    .uri("/api/new_private_key/{userId}",userId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NewPrivateKeyEntity>>() {});
            return list;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public NewPrivateKeyEntity saveNewPrivateKey(NewPrivateKeyDto newPrivateKeyDto){
        try{
            NewPrivateKeyEntity keyEntity = restClient.post()
                    .uri("/api/new_private_key")
                    .body(newPrivateKeyDto)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(NewPrivateKeyEntity.class);
            return keyEntity;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public PublicKey saveKey(SaveDto saveDto){
        try{
            PublicKey key = restClient.post()
                    .uri("/api/public_key/save")
                    .body(saveDto)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(PublicKey.class);
            return key;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public void deleteKeyById(String keyId){
        try{
            restClient.post()
                    .uri("/api/public_key/delete")
                    .body(keyId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }


}
