package ru.mescat.message.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import ru.mescat.message.dto.kafka.KeyDelete;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class DeleteSentKeysService {
    private SendMessageKeyService sendMessageKeyService;
    private KafkaTemplate<String,KeyDelete> kafkaTemplate;
    private String topic;

    public DeleteSentKeysService(SendMessageKeyService sendMessageKeyService,
                                 @Qualifier("kafkaTemplateEncryptKeyDelete") KafkaTemplate<String,KeyDelete> kafkaTemplate,
                                 @Value("spring.kafka.delete-encrypt-keys.topic")String topic){
        this.topic=topic;
        this.kafkaTemplate=kafkaTemplate;
        this.sendMessageKeyService=sendMessageKeyService;
    }

    @KafkaListener(
            topics = "${spring.kafka.delete-encrypt-keys.topic}",
            containerFactory = "kafkaListenerEncryptKeyDelete"
    )
    public void listen(List<KeyDelete> messages, Acknowledgment acknowledgment) {
        if (messages.isEmpty()) {
            return;
        }

        sendMessageKeyService.deleteAllById(messages);

        acknowledgment.acknowledge();
    }

    public void addKeyInQueue(KeyDelete keyDelete) {
        try {
            var result = kafkaTemplate.send(topic, keyDelete).get();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось отправить ключ на удаление.", e);
        }
    }


}
