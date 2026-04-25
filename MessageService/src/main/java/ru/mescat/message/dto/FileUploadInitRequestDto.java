package ru.mescat.message.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileUploadInitRequestDto {

    private Long chatId;
    private String originalFileName;
    private String mimeType;
    private Long sizeBytes;
}
