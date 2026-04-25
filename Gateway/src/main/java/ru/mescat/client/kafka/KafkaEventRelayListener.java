package ru.mescat.client.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.mescat.client.websocket.WebSocketService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Slf4j
@Component
public class KafkaEventRelayListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebSocketService webSocketService;

    public KafkaEventRelayListener(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @KafkaListener(topics = "${spring.kafka.message.topic}", groupId = "${spring.kafka.message.group}")
    public void onMessageEvent(String raw) {
        relay(raw, "message-service");
    }

    @KafkaListener(topics = "${spring.kafka.chat.topic}", groupId = "${spring.kafka.chat.group}")
    public void onChatEvent(String raw) {
        relay(raw, "chat-service");
    }

    @KafkaListener(topics = "${spring.kafka.encrypt-keys.topic}", groupId = "${spring.kafka.encrypt-keys.group}")
    public void onEncryptKeyEvent(String raw) {
        relay(raw, "encrypt-keys-service");
    }

    private void relay(String raw, String topicName) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);

            sendToChatIfPresent(root, "payload", "message", "chat", "chatId");
            sendToChatIfPresent(root, "payload", "file", "chat", "chatId");
            sendToChatIfPresent(root, "payload", "chat", "chatId");
            sendToChatIfPresent(root, "payload", "chatUserEntity", "chat", "chatId");
            sendToChatIfPresent(root, "payload", "usersBlackListEntity", "chat", "chatId");

            sendToUserIfPresent(root, "payload", "message", "senderId");
            sendToUserIfPresent(root, "payload", "file", "senderId");
            sendToUserIfPresent(root, "payload", "chatUserEntity", "userId");
            sendToUserIfPresent(root, "payload", "usersBlackListEntity", "userInitiator");
            sendToUserIfPresent(root, "payload", "usersBlackListEntity", "userTarget");
            sendToUserIfPresent(root, "payload", "newPrivateKey", "userId");
            sendToUserIfPresent(root, "payload", "publicKey", "userId");
            sendToUserIfPresent(root, "payload", "userId");

            JsonNode keys = root.path("payload").path("keys");
            if (keys.isArray()) {
                for (JsonNode node : keys) {
                    JsonNode userTarget = node.path("userTarget");
                    if (userTarget.isTextual()) {
                        webSocketService.sendToUser(UUID.fromString(userTarget.asText()), root);
                    }
                }
            }

            log.debug("Событие из Kafka обработано и отправлено в WebSocket: topic={}", topicName);
        } catch (Exception e) {
            log.warn("Не удалось передать событие из Kafka в WebSocket: topic={}, error={}", topicName, e.getMessage());
        }
    }

    private void sendToChatIfPresent(JsonNode root, String... path) {
        JsonNode value = navigate(root, path);
        if (value != null && value.isNumber()) {
            webSocketService.sendToChat(value.asLong(), root);
        }
    }

    private void sendToUserIfPresent(JsonNode root, String... path) {
        JsonNode value = navigate(root, path);
        if (value != null && value.isTextual()) {
            webSocketService.sendToUser(UUID.fromString(value.asText()), root);
        }
    }

    private JsonNode navigate(JsonNode root, String... path) {
        JsonNode node = root;
        for (String p : path) {
            node = node.path(p);
            if (node.isMissingNode() || node.isNull()) {
                return null;
            }
        }
        return node;
    }
}
