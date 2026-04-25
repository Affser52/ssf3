package ru.mescat.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mescat.message.dto.DeleteMessageDto;
import ru.mescat.message.dto.MessageDto;
import ru.mescat.message.dto.MessageForUser;
import ru.mescat.message.dto.NewMessageToNewChat;
import ru.mescat.message.exception.*;
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
    public ResponseEntity<?> getLastMessage(@RequestHeader("X-User-Id") UUID userId,
                                            @PathVariable Integer count) {
        if (count == null || count <= 0) {
            return ResponseEntity.badRequest().body("Количество сообщений должно быть больше нуля.");
        }

        try {
            List<MessageForUser> messages = messageService.getLastNMessagesForEachUserChatDto(userId, count);
            if (messages == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(messages);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/messages/{chatId}")
    public ResponseEntity<?> getRecentMessagesInChat(@RequestHeader("X-User-Id") UUID userId,
                                                     @PathVariable Long chatId,
                                                     @RequestParam(defaultValue = "50") Integer limit) {
        if (chatId == null || limit == null || limit <= 0) {
            return ResponseEntity.badRequest().body("Некорректные параметры запроса.");
        }

        try {
            return ResponseEntity.ok(messageService.getRecentMessagesInChatDto(userId, chatId, limit));
        } catch (ChatNotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/messages/{messageId}/key-request/validate")
    public ResponseEntity<?> validateMessageKeyRequest(@RequestHeader("X-User-Id") UUID userId,
                                                       @PathVariable Long messageId,
                                                       @RequestParam Long chatId,
                                                       @RequestParam UUID senderId,
                                                       @RequestParam String encryptName) {
        try {
            messageService.validateMessageKeyRequest(userId, chatId, messageId, senderId, encryptName);
            return ResponseEntity.ok().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (NotFoundException | ChatNotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/getMessageInChatWithLimit/{messageId}/{count}")
    public ResponseEntity<?> getMessageInChatWithLimit(@RequestHeader("X-User-Id") UUID userId,
                                                       @PathVariable Long messageId,
                                                       @PathVariable Integer count) {
        if (messageId == null || count == null || count == 0) {
            return ResponseEntity.badRequest().body("Идентификатор сообщения или количество указаны некорректно.");
        }

        try {
            List<MessageForUser> messages = messageService.getMessagesRelativeToMessageDto(messageId, count, userId);
            if (messages == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(messages);
        } catch (NotFoundException | ChatNotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/sendMessage")
    public ResponseEntity<?> sendMessage(@RequestHeader("X-User-Id") UUID userId,
                                         @RequestBody MessageDto messageDto) {
        if (messageDto == null) {
            return ResponseEntity.badRequest().body("Сообщение не должно быть пустым.");
        }

        try {
            MessageForUser createdMessage = messageService.sendMessage(userId, messageDto);
            return ResponseEntity.ok(createdMessage);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
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
    public ResponseEntity<?> newMessageAndNewChat(@RequestHeader("X-User-Id") UUID userId,
                                                  @RequestBody NewMessageToNewChat newMessageToNewChat) {
        if (newMessageToNewChat == null) {
            return ResponseEntity.badRequest().body("Тело запроса не должно быть пустым.");
        }

        try {
            return ResponseEntity.ok(messageService.sendMessageAndCreateChat(userId, newMessageToNewChat));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Внутренняя ошибка сервера.");
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteMessage(@RequestHeader("X-User-Id") UUID userId,
                                           @RequestBody DeleteMessageDto deleteMessageDto) {
        try {
            messageService.deleteMessage(deleteMessageDto, userId);
            return ResponseEntity.ok().build();
        } catch (ChatNotFoundException | NotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
