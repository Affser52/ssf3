package ru.mescat.message.event.dto;

import lombok.*;
import ru.mescat.message.entity.ChatEntity;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
public class DeleteChat {
    private ChatEntity chat;
}
