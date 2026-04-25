package ru.mescat.message.event.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.mescat.message.dto.kafka.ChatEventDto;
import ru.mescat.message.dto.kafka.ChatEventType;
import ru.mescat.message.entity.ChatEntity;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.entity.UsersBlackListEntity;
import ru.mescat.message.event.dto.DeleteChat;
import ru.mescat.message.event.dto.NewUserBlockInChat;
import ru.mescat.message.event.dto.NewUserInChat;
import ru.mescat.user.dto.User;
import ru.mescat.user.service.UserService;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class ChatEvent {

    private final KafkaTemplate<String, ChatEventDto> kafkaTemplate;
    private final UserService userService;
    private final String topic;

    public ChatEvent(@Qualifier("kafkaTemplateChat") KafkaTemplate<String, ChatEventDto> kafkaTemplate,
                     @Value("${spring.kafka.chat.topic}") String topic,
                     UserService userService) {
        this.topic = topic;
        this.userService = userService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener
    public void deleteChat(DeleteChat event) {
        ChatEntity chat = event != null ? event.getChat() : null;
        if (chat == null) {
            return;
        }

        kafkaTemplate.send(topic, new ChatEventDto(ChatEventType.DELETE_CHAT, deleteChatPayload(chat)));
        log.debug("Событие удаления чата отправлено в Kafka: topic={}", topic);
    }

    @TransactionalEventListener
    public void newUserBlockInChat(NewUserBlockInChat event) {
        UsersBlackListEntity blocked = event != null ? event.getUsersBlackListEntity() : null;
        if (blocked == null) {
            return;
        }

        kafkaTemplate.send(topic, new ChatEventDto(ChatEventType.NEW_USER_BLOCK_IN_CHAT, blockedUserPayload(blocked)));
        log.debug("Событие блокировки пользователя в чате отправлено в Kafka: topic={}", topic);
    }

    @TransactionalEventListener
    public void newUserInChat(NewUserInChat event) {
        ChatUserEntity chatUser = event != null ? event.getChatUserEntity() : null;
        if (chatUser == null) {
            return;
        }

        kafkaTemplate.send(topic, new ChatEventDto(ChatEventType.NEW_USER_IN_CHAT, newUserPayload(chatUser)));
        log.debug("Событие добавления пользователя в чат отправлено в Kafka: topic={}", topic);
    }

    private Map<String, Object> deleteChatPayload(ChatEntity chat) {
        Map<String, Object> chatNode = new HashMap<>();
        chatNode.put("chatId", chat.getChatId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("chat", chatNode);
        return payload;
    }

    private Map<String, Object> newUserPayload(ChatUserEntity chatUser) {
        User user = userService.findById(chatUser.getUserId());

        Map<String, Object> chatNode = new HashMap<>();
        chatNode.put("chatId", chatUser.getChat() != null ? chatUser.getChat().getChatId() : null);

        Map<String, Object> entityNode = new HashMap<>();
        entityNode.put("chat", chatNode);
        entityNode.put("userId", chatUser.getUserId());
        entityNode.put("username", user != null ? user.getUsername() : null);
        entityNode.put("avatarUrl", user != null ? user.getAvatarUrl() : null);
        entityNode.put("online", user != null ? user.isOnline() : null);
        entityNode.put("role", chatUser.getRole());
        entityNode.put("joinedAt", chatUser.getJoinedAt() != null ? chatUser.getJoinedAt().toString() : null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("chatUserEntity", entityNode);
        return payload;
    }

    private Map<String, Object> blockedUserPayload(UsersBlackListEntity blocked) {
        Map<String, Object> chatNode = new HashMap<>();
        chatNode.put("chatId", blocked.getChat() != null ? blocked.getChat().getChatId() : null);

        Map<String, Object> entityNode = new HashMap<>();
        entityNode.put("chat", chatNode);
        entityNode.put("userInitiator", blocked.getUserInitiator());
        entityNode.put("userTarget", blocked.getUserTarget());
        entityNode.put("createdAt", blocked.getCreatedAt() != null ? blocked.getCreatedAt().toString() : null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("usersBlackListEntity", entityNode);
        return payload;
    }
}
