package ru.mescat.message.event.listener;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.mescat.message.dto.kafka.ChatEventDto;
import ru.mescat.message.dto.kafka.ChatEventType;
import ru.mescat.message.dto.kafka.MessageEventDto;
import ru.mescat.message.event.dto.DeleteChat;
import ru.mescat.message.event.dto.NewUserBlockInChat;
import ru.mescat.message.event.dto.NewUserInChat;
import tools.jackson.databind.ObjectMapper;

@Component
public class ChatEvent {

    private KafkaTemplate<String, ChatEventDto> kafkaTemplate;
    private String topic;
    private ObjectMapper objectMapper = new ObjectMapper();

    public ChatEvent(@Qualifier("kafkaTemplateChat") KafkaTemplate<String, ChatEventDto> kafkaTemplate,
                     @Value("spring.kafka.chat.topic")String topic){
        this.topic=topic;
        this.kafkaTemplate=kafkaTemplate;
    }

    @TransactionalEventListener
    public void deleteChat(DeleteChat chat){
        kafkaTemplate.send(topic,new ChatEventDto(ChatEventType.DELETE_CHAT,objectMapper.valueToTree(chat)));
    }

    @TransactionalEventListener
    public void newUserBlockInChat(NewUserBlockInChat newUserBlockInChat){
        kafkaTemplate.send(topic,new ChatEventDto(ChatEventType.NEW_USER_BLOCK_IN_CHAT,objectMapper.valueToTree(newUserBlockInChat)));

    }

    @TransactionalEventListener
    public void NewUserInChat(NewUserInChat newUserInChat){
        kafkaTemplate.send(topic,new ChatEventDto(ChatEventType.NEW_USER_IN_CHAT,objectMapper.valueToTree(newUserInChat)));
    }
}
