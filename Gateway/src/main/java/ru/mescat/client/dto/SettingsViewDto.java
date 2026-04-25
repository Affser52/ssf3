package ru.mescat.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SettingsViewDto {
    private UUID id;
    private String username;
    private String avatarUrl;
    private OffsetDateTime createdAt;
    private boolean online;
    private boolean allowWriting;
    private boolean allowAddChat;
}
