package ru.mescat.message.dto.kafka;

import lombok.*;
import tools.jackson.databind.JsonNode;

@AllArgsConstructor
@NoArgsConstructor
@ToString
@Setter
@Getter
public class EncryptKeyEventDto {
    private EncryptKeyType type;
    private JsonNode payload;
}
