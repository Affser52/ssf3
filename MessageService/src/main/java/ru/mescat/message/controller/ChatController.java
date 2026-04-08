package ru.mescat.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mescat.message.dto.*;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.exception.ChatNotFoundException;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.map.ToChatDtoMapper;
import ru.mescat.message.service.ChatService;
import ru.mescat.message.service.ChatUserService;
import ru.mescat.message.service.MessageService;
import ru.mescat.message.service.UsersBlackListService;
import ru.mescat.user.service.UserService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final ChatUserService chatUserService;
    private final UserService userService;
    private final ToChatDtoMapper toChatDtoMapper;
    private final MessageService messageService;
    private final UsersBlackListService usersBlackListService;

    public ChatController(ChatService chatService,
                          ChatUserService chatUserService,
                          UserService userService,
                          ToChatDtoMapper toChatDtoMapper,
                          MessageService messageService,
                          UsersBlackListService usersBlackListService) {
        this.usersBlackListService = usersBlackListService;
        this.messageService = messageService;
        this.toChatDtoMapper = toChatDtoMapper;
        this.userService = userService;
        this.chatService = chatService;
        this.chatUserService = chatUserService;
    }


    //Получить все чаты
    @GetMapping("/chats")
    public ResponseEntity<?> getChats(@RequestHeader("X-User-Id") UUID userId) {
        List<ChatUserEntity> chats = chatUserService.findAllByUserId(userId);
        if (chats == null) {
            return ResponseEntity.notFound().build();
        }

        List<ChatDto> chatDtos = toChatDtoMapper.convert(chats, userId);
        if (chatDtos == null) {
            return ResponseEntity.status(500).body("Не удалось распарсить чаты");
        }

        return ResponseEntity.ok(chatDtos);
    }

    //создать групповой чат
    @PostMapping("/group_chat")
    public ResponseEntity<?> createGroupChat(@RequestHeader("X-User-Id") UUID userId,
                                             @RequestBody CreateGroupChatDto dto) {
        if (dto == null) {
            return ResponseEntity.badRequest().body("Тело запроса не должно быть пустым.");
        }

        try {
            return ResponseEntity.ok(chatService.createGroupChat(userId, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Не удалось создать групповой чат.");
        }
    }

    //заблокировать пользователя в чате.
    @PostMapping("/block_user")
    public ResponseEntity<?> blockUser(@RequestHeader("X-User-Id") UUID userId,
                                       @RequestBody UserBlockDto userBlockDto) {
        if (userBlockDto == null) {
            return ResponseEntity.badRequest().body("Тело запроса не должно быть пустым.");
        }

        try {
            usersBlackListService.addBlock(userId, userBlockDto);
            return ResponseEntity.ok("Пользователь успешно заблокирован.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Не удалось заблокировать пользователя.");
        }
    }

    @PostMapping("/add_user_in_chat")
    public ResponseEntity<?> addUserInChat(@RequestHeader("X-User-Id") UUID userId,
                                           @RequestBody AddUserInChatDto dto){
        try{
            chatUserService.addNewUserInChat(dto);
            return ResponseEntity.ok().build();
        } catch (NotFoundException | ChatNotFoundException e){
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e){
            return ResponseEntity.badRequest().build();
        }
    }
}