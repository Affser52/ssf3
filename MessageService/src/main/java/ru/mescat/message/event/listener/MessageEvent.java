package ru.mescat.message.event.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.mescat.message.dto.FileDto;
import ru.mescat.message.dto.kafka.MessageEventDto;
import ru.mescat.message.dto.kafka.MessageEventType;
import ru.mescat.message.entity.MessageEntity;
import ru.mescat.message.event.dto.DeleteMessage;
import ru.mescat.message.event.dto.FileReady;
import ru.mescat.message.event.dto.NewMessage;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class MessageEvent {

    private final KafkaTemplate<String, MessageEventDto> kafkaTemplate;
    private final String topic;

    public MessageEvent(@Qualifier("kafkaTemplateMessage") KafkaTemplate<String, MessageEventDto> kafkaTemplate,
                        @Value("${spring.kafka.message.topic}") String topic) {
        this.topic = topic;
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener
    public void newMessage(NewMessage event) {
        MessageEntity message = event != null ? event.getMessage() : null;
        if (message == null) {
            return;
        }

        kafkaTemplate.send(topic, new MessageEventDto(MessageEventType.SEND, toMessagePayload(message)));
        log.debug("Событие нового сообщения отправлено в Kafka: topic={}", topic);
    }

    @TransactionalEventListener
    public void deleteMessage(DeleteMessage event) {
        MessageEntity message = event != null ? event.getMessage() : null;
        if (message == null) {
            return;
        }

        kafkaTemplate.send(topic, new MessageEventDto(MessageEventType.DELETE, toMessagePayload(message)));
        log.debug("Событие удаления сообщения отправлено в Kafka: topic={}", topic);
    }

    @TransactionalEventListener
    public void fileReady(FileReady event) {
        FileDto file = event != null ? event.getFile() : null;
        if (file == null) {
            return;
        }

        kafkaTemplate.send(topic, new MessageEventDto(MessageEventType.FILE_READY, toFilePayload(file)));
        log.debug("Событие готового файла отправлено в Kafka: topic={}", topic);
    }

    private Map<String, Object> toMessagePayload(MessageEntity message) {
        Map<String, Object> chat = new HashMap<>();
        chat.put("chatId", message.getChat() != null ? message.getChat().getChatId() : null);

        Map<String, Object> messageNode = new HashMap<>();
        messageNode.put("messageId", message.getMessageId());
        messageNode.put("chat", chat);
        messageNode.put("message", message.getMessage());
        messageNode.put("encryptionName", message.getEncryptionName());
        messageNode.put("senderId", message.getSenderId());
        messageNode.put("createdAt", message.getCreatedAt() != null ? message.getCreatedAt().toString() : null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", messageNode);
        return payload;
    }

    private Map<String, Object> toFilePayload(FileDto file) {
        Map<String, Object> chat = new HashMap<>();
        chat.put("chatId", file.getChatId());

        Map<String, Object> fileNode = new HashMap<>();
        fileNode.put("fileId", file.getFileId());
        fileNode.put("chat", chat);
        fileNode.put("senderId", file.getSenderId());
        fileNode.put("status", file.getStatus());
        fileNode.put("createdAt", file.getCreatedAt() != null ? file.getCreatedAt().toString() : null);
        fileNode.put("fileType", file.getFileType());
        fileNode.put("mimeType", file.getMimeType());
        fileNode.put("sizeBytes", file.getSizeBytes());
        fileNode.put("originalFileName", file.getOriginalFileName());

        Map<String, Object> payload = new HashMap<>();
        payload.put("file", fileNode);
        return payload;
    }
}
