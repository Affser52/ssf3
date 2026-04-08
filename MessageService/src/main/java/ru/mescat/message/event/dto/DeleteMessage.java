package ru.mescat.message.event.dto;

import lombok.*;
import ru.mescat.message.entity.MessageEntity;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
public class DeleteMessage {
    private MessageEntity message;
}
