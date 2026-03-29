package ru.mescat.keyvault.dto;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@ToString
@AllArgsConstructor
public class NewPrivateKeyEntity {
    private UUID id;
    private UUID userId;
    private byte[] key;
    private OffsetDateTime createdAt;
    private UUID publicKey;
}
