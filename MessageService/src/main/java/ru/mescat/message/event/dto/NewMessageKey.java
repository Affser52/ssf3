package ru.mescat.message.event.dto;

import lombok.*;
import ru.mescat.message.dto.MessageKeyForUser;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class NewMessageKey {
    private List<MessageKeyForUser> keys;
}
