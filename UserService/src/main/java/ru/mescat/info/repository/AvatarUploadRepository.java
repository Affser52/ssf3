package ru.mescat.info.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mescat.info.entity.AvatarUploadEntity;
import ru.mescat.info.entity.AvatarUploadStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvatarUploadRepository extends JpaRepository<AvatarUploadEntity, UUID> {

    Optional<AvatarUploadEntity> findByUploadIdAndUserId(UUID uploadId, UUID userId);

    List<AvatarUploadEntity> findTop100ByStatusAndUploadUrlExpiresAtBeforeOrderByUploadUrlExpiresAtAsc(
            AvatarUploadStatus status,
            OffsetDateTime uploadUrlExpiresAt
    );
}
