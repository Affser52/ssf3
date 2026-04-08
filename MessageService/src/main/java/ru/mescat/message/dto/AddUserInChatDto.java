package ru.mescat.message.dto;

import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@ToString
@Setter
@Getter
public class AddUserInChatDto {
    private UUID userTarget;
    private Long chatId;
}
