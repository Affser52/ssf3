package ru.mescat.message.dto.kafka;

import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@ToString
@Setter
@Getter
public class KeyDelete {
    private UUID keyId;
}
