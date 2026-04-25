package ru.mescat.info.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserProfileDto {
    private UUID id;
    private String username;
    private String avatarUrl;
    private boolean online;
    private OffsetDateTime createdAt;
}
