package ru.mescat.client.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvatarUploadInitRequestDto {

    private String originalFileName;
    private String mimeType;
    private Long sizeBytes;
}
