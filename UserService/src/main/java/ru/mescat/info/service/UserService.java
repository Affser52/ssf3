package ru.mescat.info.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mescat.info.dto.UserCover;
import ru.mescat.info.dto.UserProfileDto;
import ru.mescat.info.entity.UserEntity;
import ru.mescat.info.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class UserService {

    private static final String DEFAULT_AVATAR_URL =
            "https://90995c79f2f34c065a0d26c1400cc671.bckt.ru/default-avatar/ChatGPT%20Image%2015%20мар.%202026%20г.%2C%2019_46_55.png";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSettingsService userSettingsService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserSettingsService userSettingsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userSettingsService = userSettingsService;
    }

    public UserEntity save(UserEntity user) {
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findById(UUID id) {
        return userRepository.findById(id);
    }

    public void delete(UUID id) {
        userRepository.deleteById(id);
        log.info("Пользователь удален: userId={}", id);
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> searchByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<UserEntity> findByUsernameContaining(String x, int limit) {
        String query = x == null ? "" : x.trim();
        if (query.isBlank()) {
            return List.of();
        }

        int safeLimit = Math.min(Math.max(limit, 1), 10);
        return userRepository.findByUsernameContainingIgnoreCase(query, PageRequest.of(0, safeLimit));
    }

    @Transactional(readOnly = true)
    public Optional<UserProfileDto> findProfileById(UUID userId) {
        return userRepository.findById(userId)
                .map(this::toUserProfile);
    }

    public boolean updatePassword(UUID userId, String password) {
        boolean updated = userRepository.updatePasswordById(userId, passwordEncoder.encode(password)) == 1;
        log.info("Обновление пароля: userId={}, updated={}", userId, updated);
        return updated;
    }

    public boolean updateBlocked(UUID userId, boolean blocked) {
        boolean updated = userRepository.updateBlockedById(userId, blocked) == 1;
        log.info("Обновление статуса блокировки: userId={}, blocked={}, updated={}", userId, blocked, updated);
        return updated;
    }

    public boolean updateOnline(UUID userId, boolean online) {
        boolean updated = userRepository.updateOnlineById(userId, online) == 1;
        log.info("Обновление статуса online: userId={}, online={}, updated={}", userId, online, updated);
        return updated;
    }

    public List<UserEntity> findAllByIds(List<UUID> userIds) {
        return userRepository.findAllById(userIds);
    }

    @Transactional(readOnly = true)
    public List<UserCover> findCoversByIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(userIds).stream()
                .map(this::toUserCover)
                .toList();
    }

    public boolean updateUsername(UUID userId, String username) {
        String normalizedUsername = normalizeUsername(username);

        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalizedUsername, userId)) {
            throw new IllegalStateException("Пользователь с таким username уже существует.");
        }

        boolean updated = userRepository.updateUsernameById(userId, normalizedUsername) == 1;
        log.info("Обновление username: userId={}, newUsername={}, updated={}", userId, normalizedUsername, updated);
        return updated;
    }

    public boolean updateAvatarUrl(UUID userId, String avatarUrl) {
        String normalizedAvatarUrl = normalizeAvatarUrl(avatarUrl);
        boolean updated = userRepository.updateAvatarUrlById(userId, normalizedAvatarUrl) == 1;
        log.info("Обновление avatarUrl: userId={}, updated={}", userId, updated);
        return updated;
    }

    public UserEntity findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    public UUID getIdByUsername(String username) {
        return userRepository.getIdByUsername(username);
    }

    public UserEntity createNewUser(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        UserEntity saved = save(new UserEntity(
                normalizedUsername,
                passwordEncoder.encode(password),
                false,
                true
        ));
        if (saved != null) {
            userSettingsService.createDefaultForUser(saved.getId());
            log.info("Создан новый пользователь: userId={}, username={}", saved.getId(), saved.getUsername());
        }
        return saved;
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.length() < 3 || normalized.length() > 60) {
            throw new IllegalArgumentException("Username должен содержать от 3 до 60 символов.");
        }
        if (!normalized.matches("[\\p{L}\\p{N}._-]+")) {
            throw new IllegalArgumentException(
                    "Username может содержать только буквы, цифры, точку, дефис и нижнее подчеркивание."
            );
        }
        return normalized;
    }

    private String normalizeAvatarUrl(String avatarUrl) {
        String normalized = avatarUrl == null ? "" : avatarUrl.trim();
        if (normalized.isBlank()) {
            return DEFAULT_AVATAR_URL;
        }
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException("Слишком длинный avatar URL.");
        }
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            throw new IllegalArgumentException("Avatar URL должен начинаться с http:// или https://");
        }
        return normalized;
    }

    public UserCover toUserCover(UserEntity user) {
        return new UserCover(
                user.getId(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.isOnline()
        );
    }

    public UserProfileDto toUserProfile(UserEntity user) {
        return new UserProfileDto(
                user.getId(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.isOnline(),
                user.getCreatedAt()
        );
    }
}
