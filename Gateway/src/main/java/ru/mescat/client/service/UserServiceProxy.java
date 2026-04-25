package ru.mescat.client.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class UserServiceProxy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final RestClient restClient;

    public UserServiceProxy(@Qualifier("user") RestClient restClient) {
        this.restClient = restClient;
    }

    public ResponseEntity<?> get(String path) {
        try {
            ResponseEntity<byte[]> upstream = restClient.get()
                    .uri(path)
                    .retrieve()
                    .toEntity(byte[].class);
            return toClientResponse(upstream);
        } catch (RestClientResponseException e) {
            logRemoteError("GET", path, e.getStatusCode().value());
            return toErrorResponse(e);
        } catch (RestClientException e) {
            log.error("UserService недоступен: method=GET, path={}, error={}", path, e.getMessage());
            return ResponseEntity.status(503).body("UserService unavailable: " + e.getMessage());
        }
    }

    private ResponseEntity<?> toClientResponse(ResponseEntity<byte[]> upstream) {
        String body = toBodyString(upstream.getBody());
        if (body.isBlank()) {
            return ResponseEntity.status(upstream.getStatusCode()).build();
        }
        return ResponseEntity.status(upstream.getStatusCode()).body(parseBody(body));
    }

    private ResponseEntity<?> toErrorResponse(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
        return ResponseEntity.status(e.getStatusCode()).body(parseBody(body));
    }

    private void logRemoteError(String method, String path, int status) {
        if (status == 404 || status == 204) {
            log.debug("UserService response: method={}, path={}, status={}", method, path, status);
            return;
        }
        log.warn("UserService error: method={}, path={}, status={}", method, path, status);
    }

    private Object parseBody(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }

        try {
            return OBJECT_MAPPER.readValue(body, Object.class);
        } catch (Exception ignored) {
            return body;
        }
    }

    private String toBodyString(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }
}
