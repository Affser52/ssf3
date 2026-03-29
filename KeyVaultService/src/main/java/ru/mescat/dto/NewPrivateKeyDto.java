package ru.mescat.dto;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@ToString
@AllArgsConstructor
public class NewPrivateKeyDto {
    private UUID userId;
    private byte[] key;
    private UUID publicKey;
}
