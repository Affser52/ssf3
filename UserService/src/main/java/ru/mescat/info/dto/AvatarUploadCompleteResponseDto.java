package ru.mescat.info.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AvatarUploadCompleteResponseDto {

    private UUID userId;
    private String avatarUrl;
}
