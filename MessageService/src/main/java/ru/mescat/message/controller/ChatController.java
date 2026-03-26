package ru.mescat.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.mescat.message.dto.ApiResponse;
import ru.mescat.message.dto.ChatDto;
import ru.mescat.message.entity.ChatEntity;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.entity.enums.ChatType;
import ru.mescat.message.map.ToChatDtoMapper;
import ru.mescat.message.service.ChatService;
import ru.mescat.message.service.ChatUserService;
import ru.mescat.message.websocket.WebSocketService;
import ru.mescat.user.dto.User;
import ru.mescat.user.service.UserService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChatController {

    private ChatService chatService;
    private ChatUserService chatUserService;
    private UserService userService;
    private WebSocketService webSocketService;
    private ToChatDtoMapper toChatDtoMapper;

    public ChatController(ChatService chatService,
                          ChatUserService chatUserService,
                          UserService userService,
                          WebSocketService webSocketService,
                          ToChatDtoMapper toChatDtoMapper){
        this.toChatDtoMapper=toChatDtoMapper;
        this.webSocketService=webSocketService;
        this.userService=userService;
        this.chatService=chatService;
        this.chatUserService=chatUserService;
    }

    @GetMapping("/chats")
    public ResponseEntity<?> getChats(Authentication authentication){
        UUID userId = UUID.fromString(authentication.getName());
        List<ChatUserEntity> chats = chatUserService.findAllByUserId(userId);
        if(chats==null){
            return ResponseEntity.notFound().build();
        }

        List<ChatDto> chatDtos = toChatDtoMapper.convert(chats,userId);
        if(chatDtos==null){
            return ResponseEntity.status(500).body("Не удалось распарсить чаты");
        }

        return ResponseEntity.ok(chatDtos);
    }

}
