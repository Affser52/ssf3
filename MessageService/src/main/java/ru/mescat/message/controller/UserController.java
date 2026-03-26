package ru.mescat.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mescat.message.exception.RemoteServiceException;
import ru.mescat.user.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {

    private UserService userService;

    public UserController(UserService userService){
        this.userService=userService;
    }

    @GetMapping("/{username}/getId")
    public ResponseEntity<?> getIdByUsername(@PathVariable String username){
        if(username==null){
            return ResponseEntity.notFound().build();
        }

        try{
            UUID userId = userService.getIdByUsername(username);

            return ResponseEntity.ok(userId);
        } catch (Exception e){
            return ResponseEntity.status(502).body("Не удалось получить данные от сервера.");
        }

    }
}
