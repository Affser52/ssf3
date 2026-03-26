package ru.mescat.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mescat.message.dto.ChatDto;
import ru.mescat.message.map.UserChatDtoMap;
import ru.mescat.user.dto.User;
import ru.mescat.user.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final UserService userService;
    private final UserChatDtoMap userChatDtoMap;

    public SearchController(UserService userService, UserChatDtoMap userChatDtoMap){
        this.userChatDtoMap=userChatDtoMap;
        this.userService = userService;
    }

    @GetMapping("/search_by_username")
    public ResponseEntity<?> searchByUsername(@RequestParam String username){
        if(username == null || username.isBlank()){
            return ResponseEntity.status(400).build();
        }
        List<User> users = userService.findByUsernameContaining(username);

        if(users==null || users.isEmpty()){
            return ResponseEntity.ok(List.of());
        }
        List<ChatDto> chatDtos = userChatDtoMap.convert(users);

        if(chatDtos==null){
            return ResponseEntity.status(500).body("Не удалось подготовить чаты.");
        }

        return ResponseEntity.ok(chatDtos);
    }
}