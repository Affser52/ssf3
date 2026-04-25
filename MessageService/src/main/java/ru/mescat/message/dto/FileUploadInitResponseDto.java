package ru.mescat.message.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class FileUploadInitResponseDto {

    private UUID fileId;
    private Long chatId;
    private String uploadUrl;
    private Map<String, String> formFields;
    private OffsetDateTime uploadUrlExpiresAt;
    private String method;
}
