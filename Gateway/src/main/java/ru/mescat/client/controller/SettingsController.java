package ru.mescat.client.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import ru.mescat.client.dto.AvatarUploadInitRequestDto;
import ru.mescat.client.dto.ChangePasswordDto;
import ru.mescat.client.dto.SettingsViewDto;
import ru.mescat.client.dto.UpdateBooleanValueDto;
import ru.mescat.client.dto.UpdateUsernameDto;
import ru.mescat.security.User;
import ru.mescat.security.UserSettings;
import ru.mescat.security.exception.RemoteServiceException;
import ru.mescat.security.service.SecuritySettingsService;
import ru.mescat.security.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final UserService userService;
    private final SecuritySettingsService securitySettingsService;

    public SettingsController(UserService userService,
                              SecuritySettingsService securitySettingsService) {
        this.userService = userService;
        this.securitySettingsService = securitySettingsService;
    }

    @GetMapping
    public ResponseEntity<?> getSettings(Authentication authentication) {
        try {
            return ResponseEntity.ok(loadView(userId(authentication)));
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }

    @PatchMapping("/profile/username")
    public ResponseEntity<?> updateUsername(@RequestBody UpdateUsernameDto dto,
                                            Authentication authentication) {
        try {
            UUID userId = userId(authentication);
            userService.updateUsername(userId, dto != null ? dto.getUsername() : null);
            return ResponseEntity.ok(loadView(userId));
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }

    @PatchMapping("/profile/avatar-url")
    public ResponseEntity<?> updateAvatarUrl(@RequestBody(required = false) Object ignored,
                                             Authentication authentication) {
        return ResponseEntity.status(410).body("\u0410\u0432\u0430\u0442\u0430\u0440\u043a\u0430 \u043e\u0431\u043d\u043e\u0432\u043b\u044f\u0435\u0442\u0441\u044f \u0442\u043e\u043b\u044c\u043a\u043e \u0437\u0430\u0433\u0440\u0443\u0437\u043a\u043e\u0439 \u0438\u0437\u043e\u0431\u0440\u0430\u0436\u0435\u043d\u0438\u044f.");
    }

    @PostMapping("/profile/avatar/upload-url")
    public ResponseEntity<?> createAvatarUploadUrl(@RequestBody AvatarUploadInitRequestDto dto,
                                                   Authentication authentication) {
        try {
            return ResponseEntity.ok(userService.createAvatarUploadUrl(userId(authentication), dto));
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }

    @PostMapping("/profile/avatar/{uploadId}/complete")
    public ResponseEntity<?> completeAvatarUpload(@PathVariable UUID uploadId,
                                                  Authentication authentication) {
        try {
            return ResponseEntity.ok(userService.completeAvatarUpload(userId(authentication), uploadId));
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }

    @PatchMapping("/preferences/allow-writing")
    public ResponseEntity<?> updateAllowWriting(@RequestBody UpdateBooleanValueDto dto,
                                                Authentication authentication) {
        try {
            UUID userId = userId(authentication);
            userService.updateAllowWriting(userId, dto != null && dto.isValue());
            return ResponseEntity.ok(loadView(userId));
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }

    @PatchMapping("/preferences/allow-add-chat")
    public ResponseEntity<?> updateAllowAddChat(@RequestBody UpdateBooleanValueDto dto,
                                                Authentication authentication) {
        try {
            UUID userId = userId(authentication);
            userService.updateAllowAddChat(userId, dto != null && dto.isValue());
            return ResponseEntity.ok(loadView(userId));
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        }
    }

    @PostMapping("/security/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordDto dto,
                                            Authentication authentication,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        try {
            securitySettingsService.changePassword(userId(authentication), dto, request, response);
            return ResponseEntity.ok("Пароль изменен. Выполнен выход из всех сессий.");
        } catch (RemoteServiceException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getResponseBody());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/security/logout-all")
    public ResponseEntity<?> logoutAll(Authentication authentication,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        securitySettingsService.logoutAll(userId(authentication), request, response);
        return ResponseEntity.ok("Все сессии завершены.");
    }

    private SettingsViewDto loadView(UUID userId) {
        User user = userService.infoById(userId);
        UserSettings settings = userService.settingsById(userId);
        return new SettingsViewDto(
                user != null ? user.getId() : userId,
                user != null ? user.getUsername() : null,
                user != null ? user.getAvatarUrl() : null,
                user != null ? user.getCreatedAt() : null,
                user != null && user.isOnline(),
                settings != null && settings.isAllowWriting(),
                settings != null && settings.isAllowAddChat()
        );
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
