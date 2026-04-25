package ru.mescat.info.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "avatar_upload")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvatarUploadEntity {

    @Id
    @Column(name = "upload_id", nullable = false)
    private UUID uploadId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AvatarUploadStatus status;

    @Column(name = "temp_bucket", nullable = false)
    private String tempBucket;

    @Column(name = "temp_object_key", nullable = false, unique = true, length = 1024)
    private String tempObjectKey;

    @Column(name = "final_bucket", nullable = false)
    private String finalBucket;

    @Column(name = "final_object_key", nullable = false, length = 1024)
    private String finalObjectKey;

    @Column(name = "original_file_name", nullable = false, length = 512)
    private String originalFileName;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "upload_url_expires_at")
    private OffsetDateTime uploadUrlExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
