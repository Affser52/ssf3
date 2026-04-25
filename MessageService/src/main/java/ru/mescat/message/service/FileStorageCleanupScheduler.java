package ru.mescat.message.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FileStorageCleanupScheduler {

    private final ChatFileService chatFileService;

    public FileStorageCleanupScheduler(ChatFileService chatFileService) {
        this.chatFileService = chatFileService;
    }

    @Scheduled(fixedDelayString = "${storage.s3.cleanup-fixed-delay}")
    public void cleanupExpiredUploads() {
        try {
            chatFileService.cleanupExpiredUploads();
        } catch (Exception e) {
            log.warn("Плановая очистка просроченных загрузок S3 завершилась ошибкой: {}", e.getMessage());
        }
    }
}
