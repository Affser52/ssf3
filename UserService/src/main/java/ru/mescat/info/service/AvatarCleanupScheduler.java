package ru.mescat.info.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AvatarCleanupScheduler {

    private final AvatarService avatarService;

    public AvatarCleanupScheduler(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @Scheduled(fixedDelayString = "${storage.s3.avatar-cleanup-fixed-delay}")
    public void cleanupExpiredAvatarUploads() {
        try {
            avatarService.cleanupExpiredUploads();
        } catch (Exception e) {
            log.warn("Плановая очистка просроченных загрузок аватарок завершилась ошибкой: {}", e.getMessage());
        }
    }
}
