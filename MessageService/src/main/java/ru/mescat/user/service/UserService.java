package ru.mescat.user.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.mescat.user.dto.User;
import ru.mescat.user.dto.UserSettings;
import ru.mescat.message.exception.RemoteServiceException;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final RestClient restClient;

    public UserService(@Qualifier("user") RestClient restClient) {
        this.restClient = restClient;
    }

    public User save(User user) {
        try {
            return restClient.post()
                    .uri("/user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .body(User.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public User findById(UUID id) {
        try {
            return restClient.get()
                    .uri("/user/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(User.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public void delete(UUID id) {
        try {
            restClient.delete()
                    .uri("/user/{id}", id)
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

    public User searchByUsername(String username) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/search")
                            .queryParam("username", username)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(User.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public List<User> findAllByIds(List<UUID> userIds) {
        try {
            return restClient.post()
                    .uri("/user/getAllById")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(userIds)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<User>>() {});
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public List<User> findByUsernameContaining(String username) {
        try {
            return restClient.get()
                    .uri("/user/search/contains/{username}", username)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<User>>() {});
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();

            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public void updatePassword(UUID id, String password) {
        try {
            restClient.patch()
                    .uri("/user/{id}/password", id)
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body(password)
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

    public void updateBlocked(UUID id, boolean blocked) {
        try {
            restClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/{id}/blocked")
                            .queryParam("blocked", blocked)
                            .build(id))
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

    public void updateOnline(UUID id, boolean online) {
        try {
            restClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/{id}/online")
                            .queryParam("online", online)
                            .build(id))
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

    public void updateUsername(UUID id, String username) {
        try {
            restClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/{id}/username")
                            .queryParam("username", username)
                            .build(id))
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

    public UserSettings getSettingsById(UUID id) {
        try {
            return restClient.get()
                    .uri("/user_settings/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(UserSettings.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public UUID getIdByUsername(String username){
        try{
            return restClient.get()
                    .uri("/user/{username}/getId",username)
                    .retrieve()
                    .body(UUID.class);
        } catch (RestClientResponseException e){
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }
}
