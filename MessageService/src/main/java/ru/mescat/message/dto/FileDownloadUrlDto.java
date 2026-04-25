package ru.mescat.message.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class FileDownloadUrlDto {

    private UUID fileId;
    private String downloadUrl;
    private OffsetDateTime downloadUrlExpiresAt;
    private String method;
}
