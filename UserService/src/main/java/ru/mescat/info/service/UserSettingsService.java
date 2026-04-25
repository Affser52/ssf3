package ru.mescat.info.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mescat.info.entity.UserSettingsEntity;
import ru.mescat.info.repository.UserRepository;
import ru.mescat.info.repository.UserSettingsRepository;

import java.util.UUID;

@Service
@Transactional
public class UserSettingsService {
    private final UserSettingsRepository repository;
    private final UserRepository userRepository;

    public UserSettingsService(UserSettingsRepository repository,
                               UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserSettingsEntity findById(UUID userId) {
        return repository.findById(userId).orElse(null);
    }

    public UserSettingsEntity findOrCreateById(UUID userId) {
        UserSettingsEntity settings = findById(userId);
        if (settings != null) {
            return settings;
        }
        if (!userRepository.existsById(userId)) {
            return null;
        }
        return createDefaultForUser(userId);
    }

    public UserSettingsEntity createDefaultForUser(UUID userId) {
        UserSettingsEntity existing = findById(userId);
        if (existing != null) {
            return existing;
        }

        UserSettingsEntity entity = new UserSettingsEntity();
        entity.setUser_id(userId);
        entity.setAllowWriting(true);
        entity.setAllowAddChat(true);
        return repository.save(entity);
    }

    public UserSettingsEntity save(UserSettingsEntity entity) {
        return repository.save(entity);
    }

    public boolean setAllowWriting(boolean value, UUID userId) {
        UserSettingsEntity entity = findOrCreateById(userId);
        if (entity == null) {
            return false;
        }
        entity.setAllowWriting(value);
        repository.save(entity);
        return true;
    }

    public boolean setAllowAddChat(boolean value, UUID userId) {
        UserSettingsEntity entity = findOrCreateById(userId);
        if (entity == null) {
            return false;
        }
        entity.setAllowAddChat(value);
        repository.save(entity);
        return true;
    }
}
