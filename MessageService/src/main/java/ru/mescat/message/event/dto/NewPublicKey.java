package ru.mescat.message.event.dto;

import lombok.*;
import ru.mescat.keyvault.dto.PublicKey;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
public class NewPublicKey {
    private PublicKey publicKey;
}
