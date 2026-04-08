package ru.mescat.message.event.listener;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.mescat.message.dto.kafka.EncryptKeyEventDto;
import ru.mescat.message.dto.kafka.EncryptKeyType;
import ru.mescat.message.dto.kafka.MessageEventDto;
import ru.mescat.message.event.dto.NewMessageKey;
import ru.mescat.message.event.dto.NewPrivateKey;
import ru.mescat.message.event.dto.NewPublicKey;
import tools.jackson.databind.ObjectMapper;

@Component
public class EncryptKeyEvent {

    private KafkaTemplate<String, EncryptKeyEventDto> kafkaTemplate;
    private String topic;
    private ObjectMapper objectMapper = new ObjectMapper();

    public EncryptKeyEvent(@Qualifier("kafkaTemplateEncryptKey") KafkaTemplate<String,EncryptKeyEventDto> kafkaTemplate,
                           @Value("spring.kafka.encrypt-keys.topic")String topic){
        this.topic=topic;
        this.kafkaTemplate=kafkaTemplate;
    }

    @TransactionalEventListener
    public void newMessageKey(NewMessageKey newMessageKey){
        kafkaTemplate.send(topic,new EncryptKeyEventDto(EncryptKeyType.NEW_MESSAGE_KEY,objectMapper.valueToTree(newMessageKey)));
    }

    @TransactionalEventListener
    public void newPrivateKey(NewPrivateKey newPrivateKey){
        kafkaTemplate.send(topic,new EncryptKeyEventDto(EncryptKeyType.NEW_PRIVATE_KEY,objectMapper.valueToTree(newPrivateKey)));
    }

    @TransactionalEventListener
    public void newPublicKey(NewPublicKey newPublicKey){
        kafkaTemplate.send(topic,new EncryptKeyEventDto(EncryptKeyType.NEW_PUBLIC_KEY,objectMapper.valueToTree(newPublicKey)));
    }
}
