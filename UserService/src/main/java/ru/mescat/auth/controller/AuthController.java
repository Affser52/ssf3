package ru.mescat.auth.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mescat.auth.dto.RegDto;
import ru.mescat.info.entity.UserEntity;
import ru.mescat.info.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService){
        this.userService = userService;
    }

    @GetMapping("/info/{username}")
    public ResponseEntity<?> getUser(@PathVariable String username) {
        log.info("Запрос пользователя по username={}", username);
        UserEntity user = userService.findByUsername(username);

        if (user == null) {
            log.warn("Пользователь не найден: username={}", username);
            return ResponseEntity.status(404).body("Пользователь не найден");
        }

        return ResponseEntity.ok(user);
    }

    @GetMapping("/info/id/{id}")
    public ResponseEntity<?> getUserById(@PathVariable UUID id) {
        log.info("Запрос пользователя по id={}", id);
        return userService.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Пользователь не найден: userId={}", id);
                    return ResponseEntity.status(404).body("Пользователь не найден");
                });
    }

    @PostMapping("/reg")
    public ResponseEntity<?> reg(@RequestBody RegDto regDto) {
        log.info("Запрос на регистрацию: username={}", regDto.getUsername());
        try {
            UserEntity entity = userService.findByUsername(regDto.getUsername());

            if (entity != null) {
                log.warn("Регистрация отклонена: пользователь уже существует, username={}", regDto.getUsername());
                return ResponseEntity.status(409).body("Пользователь уже существует");
            }

            entity = userService.createNewUser(regDto.getUsername(), regDto.getPassword());

            if (entity == null) {
                log.error("Ошибка регистрации: не удалось создать аккаунт, username={}", regDto.getUsername());
                return ResponseEntity.status(500).body("Не удалось создать новый аккаунт");
            }

            log.info("Пользователь зарегистрирован: userId={}, username={}", entity.getId(), entity.getUsername());
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            log.warn("Регистрация отклонена: username={}, reason={}", regDto.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
