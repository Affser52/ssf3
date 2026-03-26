package ru.mescat.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ru.mescat.message.entity.MessageEntity;
import ru.mescat.message.exception.ChatNotFoundException;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.service.MessageService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MessageController {

    private MessageService messageService;

    public MessageController(MessageService messageService){
        this.messageService=messageService;
    }

    @GetMapping("/getLastMessages/{count}")
    public ResponseEntity<?> getLastMessage(@PathVariable Integer count){
        if(count==null){
            return ResponseEntity.status(400).build();
        }
        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());

        List<MessageEntity> messages;
        try{
            messages = messageService.getLastNMessagesForEachUserChat(userId, count);
        } catch (IllegalArgumentException e){
            return ResponseEntity.status(400).body(e.getMessage());
        }

        if (messages==null){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(messages);
    }

    @GetMapping("/getMessageInChatWithLimit/{messageId}/{count}")
    public ResponseEntity<List<MessageEntity>> getMessageInChatWithLimit(@PathVariable Long messageId, @PathVariable Integer count){
        if(messageId==null || count==null){
            return ResponseEntity.status(400).build();
        }
        List<MessageEntity> messages;
        try{
            messages = messageService.getMessagesRelativeToMessage(messageId, count);
        } catch (NotFoundException | ChatNotFoundException e){
            return ResponseEntity.notFound().build();
        }
        if (messages==null){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(messages);
    }


}
