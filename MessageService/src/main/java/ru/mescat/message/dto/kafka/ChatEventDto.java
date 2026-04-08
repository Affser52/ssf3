package ru.mescat.message.dto.kafka;

import lombok.*;
import tools.jackson.databind.JsonNode;

@AllArgsConstructor
@NoArgsConstructor
@ToString
@Setter
@Getter
public class ChatEventDto {
    private ChatEventType type;
    private JsonNode payload;
}