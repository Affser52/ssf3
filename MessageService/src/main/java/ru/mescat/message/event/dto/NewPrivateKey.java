package ru.mescat.message.event.dto;

import lombok.*;
import ru.mescat.keyvault.dto.NewPrivateKeyDto;
import ru.mescat.keyvault.dto.NewPrivateKeyEntity;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
public class NewPrivateKey {
    private NewPrivateKeyEntity newPrivateKey;
}
