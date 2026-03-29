package ru.mescat.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mescat.message.dto.MessageDto;
import ru.mescat.message.dto.MessageForUser;
import ru.mescat.message.dto.NewMessageToNewChat;
import ru.mescat.message.entity.MessageEntity;
import ru.mescat.message.exception.ChatNotFoundException;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.exception.SaveToDatabaseException;
import ru.mescat.message.exception.UserBlockedException;
import ru.mescat.message.map.MessageEntityToMessageForUser;
import ru.mescat.message.service.MessageService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/getLastMessages/{count}")
    public ResponseEntity<?> getLastMessage(@PathVariable Integer count) {
        if (count == null || count <= 0) {
            return ResponseEntity.badRequest().body("Количество сообщений должно быть больше нуля.");
        }

        List<MessageEntity> messages;
        try {
            messages = messageService.getLastNMessagesForEachUserChat(
                    UUID.fromString(
                            org.springframework.security.core.context.SecurityContextHolder
                                    .getContext()
                                    .getAuthentication()
                                    .getName()
                    ),
                    count
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        if (messages == null) {
            return ResponseEntity.notFound().build();
        }

        List<MessageForUser> result = messages.stream()
                .map(MessageEntityToMessageForUser::convert)
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/getMessageInChatWithLimit/{messageId}/{count}")
    public ResponseEntity<?> getMessageInChatWithLimit(@PathVariable Long messageId,
                                                       @PathVariable Integer count) {
        if (messageId == null || count == null || count <= 0) {
            return ResponseEntity.badRequest().body("Идентификатор сообщения или количество указаны некорректно.");
        }

        List<MessageEntity> messages;
        try {
            messages = messageService.getMessagesRelativeToMessage(messageId, count);
        } catch (NotFoundException | ChatNotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        if (messages == null) {
            return ResponseEntity.notFound().build();
        }

        List<MessageForUser> result = messages.stream()
                .map(MessageEntityToMessageForUser::convert)
                .toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/sendMessage")
    public ResponseEntity<?> sendMessage(@RequestBody MessageDto messageDto) {
        if (messageDto == null) {
            return ResponseEntity.badRequest().body("Сообщение не должно быть пустым.");
        }

        try {
            messageService.sendMessage(messageDto);
            return ResponseEntity.ok("Сообщение успешно отправлено.");
        } catch (ChatNotFoundException | NotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (UserBlockedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SaveToDatabaseException e) {
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Внутренняя ошибка сервера.");
        }
    }

    @PostMapping("/newMessageAndNewChat")
    public ResponseEntity<?> newMessageAndNewChat(@RequestBody NewMessageToNewChat newMessageToNewChat) {
        if (newMessageToNewChat == null) {
            return ResponseEntity.badRequest().body("Тело запроса не должно быть пустым.");
        }

        try {
            return ResponseEntity.ok(messageService.sendMessageAndCreateChat(newMessageToNewChat));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Внутренняя ошибка сервера.");
        }
    }
}