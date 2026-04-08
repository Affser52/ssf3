package ru.mescat.message.event.dto;

import lombok.*;
import ru.mescat.message.entity.UsersBlackListEntity;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
public class NewUserBlockInChat{
    private UsersBlackListEntity usersBlackListEntity;
}
