package ru.mescat.message.event.dto;

import lombok.*;
import ru.mescat.message.entity.ChatUserEntity;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
public class NewUserInChat {
    private ChatUserEntity chatUserEntity;
}
