package ru.mescat.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.mescat.security.User;
import ru.mescat.security.UserSettings;
import ru.mescat.client.dto.AvatarUploadInitRequestDto;
import ru.mescat.security.dto.RegDto;
import ru.mescat.security.exception.RemoteServiceException;

import java.util.UUID;

@Service
@Slf4j
public class UserService {

    private final RestClient restClient;

    public UserService(@Qualifier("user") RestClient restClient) {
        this.restClient = restClient;
    }

    public User registration(RegDto regDto) {
        try {
            User user = restClient.post()
                    .uri("/auth/reg")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(regDto)
                    .retrieve()
                    .body(User.class);

            if (user != null) {
                log.info("UserService: пользователь зарегистрирован, userId={}, username={}", user.getId(), user.getUsername());
            }
            return user;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            log.warn("UserService: ошибка регистрации, status={}, username={}", status, regDto.getUsername());
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            log.error("UserService недоступен при регистрации: username={}, error={}", regDto.getUsername(), e.getMessage());
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public User info(String username) {
        try {
            User user = restClient.get()
                    .uri("/auth/info/{username}", username)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(User.class);

            if (user == null) {
                log.warn("UserService: пользователь не найден по username={}", username);
            }
            return user;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            log.warn("UserService: ошибка получения пользователя по username, status={}, username={}", status, username);
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            log.error("UserService недоступен при info(username): username={}, error={}", username, e.getMessage());
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public User infoById(UUID id) {
        try {
            User user = restClient.get()
                    .uri("/auth/info/id/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(User.class);

            if (user == null) {
                log.warn("UserService: пользователь не найден по id={}", id);
            }
            return user;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            log.warn("UserService: ошибка получения пользователя по id, status={}, userId={}", status, id);
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            log.error("UserService недоступен при infoById: userId={}, error={}", id, e.getMessage());
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public UserSettings settingsById(UUID id) {
        try {
            return restClient.get()
                    .uri("/user_settings/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(UserSettings.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            log.warn("UserService: ошибка получения настроек, status={}, userId={}", status, id);
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            log.error("UserService недоступен при settingsById: userId={}, error={}", id, e.getMessage());
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public void updatePassword(UUID id, String password) {
        try {
            restClient.patch()
                    .uri("/user/{id}/password", id)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(password)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            log.warn("UserService: ошибка смены пароля, status={}, userId={}", status, id);
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            log.error("UserService недоступен при updatePassword: userId={}, error={}", id, e.getMessage());
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
            log.warn("UserService: ошибка изменения username, status={}, userId={}", status, id);
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            log.error("UserService недоступен при updateUsername: userId={}, error={}", id, e.getMessage());
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public void updateAvatarUrl(UUID id, String avatarUrl) {
        try {
            restClient.patch()
                    .uri("/user/{id}/avatar-url", id)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(avatarUrl == null ? "" : avatarUrl)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            log.warn("UserService: ошибка изменения avatarUrl, status={}, userId={}", status, id);
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            log.error("UserService недоступен при updateAvatarUrl: userId={}, error={}", id, e.getMessage());
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public Object createAvatarUploadUrl(UUID id, AvatarUploadInitRequestDto dto) {
        try {
            return restClient.post()
                    .uri("/user/{id}/avatar/upload-url", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(dto)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            log.warn("UserService: ошибка подготовки загрузки аватарки, status={}, userId={}", status, id);
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            log.error("UserService недоступен при createAvatarUploadUrl: userId={}, error={}", id, e.getMessage());
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public Object completeAvatarUpload(UUID id, UUID uploadId) {
        try {
            return restClient.post()
                    .uri("/user/{id}/avatar/{uploadId}/complete", id, uploadId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            log.warn("UserService: ошибка завершения загрузки аватарки, status={}, userId={}, uploadId={}", status, id, uploadId);
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            log.error("UserService недоступен при completeAvatarUpload: userId={}, uploadId={}, error={}", id, uploadId, e.getMessage());
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public void updateAllowWriting(UUID id, boolean value) {
        try {
            restClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user_settings/{id}/allow-writing")
                            .queryParam("value", value)
                            .build(id))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            log.warn("UserService: ошибка изменения allowWriting, status={}, userId={}", status, id);
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            log.error("UserService недоступен при updateAllowWriting: userId={}, error={}", id, e.getMessage());
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

    public void updateAllowAddChat(UUID id, boolean value) {
        try {
            restClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user_settings/{id}/allow-add-chat")
                            .queryParam("value", value)
                            .build(id))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = e.getResponseBodyAsString();
            log.warn("UserService: ошибка изменения allowAddChat, status={}, userId={}", status, id);
            throw new RemoteServiceException(status, message);
        } catch (RestClientException e) {
            log.error("UserService недоступен при updateAllowAddChat: userId={}, error={}", id, e.getMessage());
            throw new RemoteServiceException(503, "UserService unavailable: " + e.getMessage());
        }
    }

}
