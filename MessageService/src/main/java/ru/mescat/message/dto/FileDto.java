package ru.mescat.message.dto;

import lombok.Builder;
import lombok.Getter;
import ru.mescat.message.entity.enums.FileStatus;
import ru.mescat.message.entity.enums.FileType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class FileDto {

    private UUID fileId;
    private Long chatId;
    private UUID senderId;
    private FileStatus status;
    private OffsetDateTime createdAt;
    private FileType fileType;
    private String mimeType;
    private Long sizeBytes;
    private String originalFileName;
}
