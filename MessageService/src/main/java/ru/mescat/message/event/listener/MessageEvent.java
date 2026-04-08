package ru.mescat.message.event.listener;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.mescat.message.dto.DeleteMessageDto;
import ru.mescat.message.dto.MessageForUser;
import ru.mescat.message.dto.kafka.MessageEventDto;
import ru.mescat.message.dto.kafka.MessageEventType;
import ru.mescat.message.event.dto.DeleteMessage;
import ru.mescat.message.event.dto.NewMessage;
import tools.jackson.databind.ObjectMapper;

@Component
public class MessageEvent {

    private KafkaTemplate<String, MessageEventDto> kafkaTemplate;
    private String topic;
    private ObjectMapper objectMapper = new ObjectMapper();

    public MessageEvent(@Qualifier("kafkaTemplateMessage") KafkaTemplate<String,MessageEventDto> kafkaTemplate,
                        @Value("spring.kafka.message.topic")String topic){
        this.topic=topic;
        this.kafkaTemplate=kafkaTemplate;
    }

    @TransactionalEventListener
    public void newMessage(NewMessage message){
        kafkaTemplate.send(topic,new MessageEventDto(MessageEventType.SEND,objectMapper.valueToTree(message)));

    }

    @TransactionalEventListener
    public void deleteMessage(DeleteMessage deleteMessage){
        kafkaTemplate.send(topic,new MessageEventDto(MessageEventType.DELETE,objectMapper.valueToTree(deleteMessage)));
    }
}
