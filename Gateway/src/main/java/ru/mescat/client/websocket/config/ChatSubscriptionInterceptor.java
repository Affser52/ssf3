package ru.mescat.client.websocket.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import ru.mescat.client.service.MessageServiceProxy;

import java.security.Principal;
import java.util.UUID;

@Component
@Slf4j
public class ChatSubscriptionInterceptor implements ChannelInterceptor {

    private final MessageServiceProxy messageServiceProxy;

    public ChatSubscriptionInterceptor(MessageServiceProxy messageServiceProxy) {
        this.messageServiceProxy = messageServiceProxy;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/chat/")) {
                Long chatId = parseChatId(destination);
                UUID userId = resolveUserId(accessor.getUser());

                ResponseEntity<?> response = messageServiceProxy.get("/api/chats/" + chatId + "/members", userId);
                if (!response.getStatusCode().is2xxSuccessful()) {
                    log.warn("Отклонена WebSocket-подписка: нет доступа к чату. userId={}, chatId={}", userId, chatId);
                    throw new IllegalArgumentException("Access denied to chat destination: " + destination);
                }
            }
        }

        return message;
    }

    private Long parseChatId(String destination) {
        String chatIdPart = destination.substring("/topic/chat/".length());
        try {
            return Long.parseLong(chatIdPart);
        } catch (NumberFormatException e) {
            log.warn("Отклонена WebSocket-подписка: некорректный destination={}", destination);
            throw new IllegalArgumentException("Invalid chat destination: " + destination);
        }
    }

    private UUID resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("WebSocket user is not authenticated.");
        }
        return UUID.fromString(principal.getName());
    }
}
