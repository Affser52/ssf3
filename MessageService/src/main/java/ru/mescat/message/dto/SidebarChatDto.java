package ru.mescat.message.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import ru.mescat.message.entity.enums.ChatType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class SidebarChatDto {
    private Long chatId;
    private ChatType chatType;
    private String title;
    private String avatarUrl;
    private OffsetDateTime lastActivityAt;
    private byte[] lastMessage;
    private String encryptName;
    private UUID counterpartUserId;
    private Boolean counterpartOnline;
    private String currentUserRole;
    private boolean canManageMembers;
    private boolean canDeleteChat;
    private int memberCount;
    private int onlineCount;
    private List<String> memberUsernames = new ArrayList<>();
    private List<SidebarParticipantDto> participants = new ArrayList<>();
}
