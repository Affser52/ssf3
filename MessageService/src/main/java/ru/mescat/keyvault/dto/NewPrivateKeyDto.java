package ru.mescat.keyvault.dto;

import lombok.*;

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
