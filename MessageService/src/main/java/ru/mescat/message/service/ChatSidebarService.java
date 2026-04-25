package ru.mescat.message.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mescat.message.dto.SidebarChatDto;
import ru.mescat.message.dto.SidebarParticipantDto;
import ru.mescat.message.entity.ChatEntity;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.entity.MessageEntity;
import ru.mescat.message.entity.enums.ChatType;
import ru.mescat.user.dto.User;
import ru.mescat.user.service.UserService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatSidebarService {

    private final ChatUserService chatUserService;
    private final MessageService messageService;
    private final UserService userService;

    public ChatSidebarService(ChatUserService chatUserService,
                              MessageService messageService,
                              UserService userService) {
        this.chatUserService = chatUserService;
        this.messageService = messageService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<SidebarChatDto> getSidebarChats(UUID userId) {
        List<ChatUserEntity> memberships = chatUserService.findAllByUserId(userId);
        if (memberships == null || memberships.isEmpty()) {
            return List.of();
        }

        List<MessageEntity> latestMessages = messageService.getLastNMessagesForEachUserChat(userId, 1);
        Map<Long, MessageEntity> lastMessages = (latestMessages == null ? List.<MessageEntity>of() : latestMessages)
                .stream()
                .collect(Collectors.toMap(
                        message -> message.getChat().getChatId(),
                        Function.identity(),
                        (left, right) -> left
                ));

        return memberships.stream()
                .map(membership -> buildSidebarChat(userId, membership, lastMessages.get(membership.getChat().getChatId())))
                .sorted(Comparator
                        .comparing(SidebarChatDto::getLastActivityAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SidebarChatDto::getChatId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private SidebarChatDto buildSidebarChat(UUID currentUserId,
                                            ChatUserEntity membership,
                                            MessageEntity lastMessage) {
        ChatEntity chat = membership.getChat();
        List<UUID> participantIds = chatUserService.findAllUserIdNotBlocksByChatId(chat.getChatId());
        List<User> participants = participantIds == null || participantIds.isEmpty()
                ? List.of()
                : userService.findAllByIds(participantIds);

        List<SidebarParticipantDto> participantDtos = participants.stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(user -> new SidebarParticipantDto(
                        user.getId(),
                        user.getUsername(),
                        user.getAvatarUrl(),
                        user.isOnline()
                ))
                .toList();

        SidebarChatDto dto = new SidebarChatDto();
        dto.setChatId(chat.getChatId());
        dto.setChatType(chat.getChatType());
        dto.setLastActivityAt(lastMessage != null ? lastMessage.getCreatedAt() : chat.getCreatedAt());
        dto.setLastMessage(lastMessage != null ? lastMessage.getMessage() : null);
        dto.setEncryptName(lastMessage != null ? lastMessage.getEncryptionName() : null);
        dto.setCurrentUserRole(membership.getRole());
        dto.setParticipants(participantDtos);
        dto.setMemberUsernames(participantDtos.stream().map(SidebarParticipantDto::getUsername).toList());
        dto.setMemberCount(participantDtos.size());
        dto.setOnlineCount((int) participantDtos.stream().filter(SidebarParticipantDto::isOnline).count());

        if (chat.getChatType() == ChatType.PERSONAL) {
            SidebarParticipantDto counterpart = participantDtos.stream()
                    .filter(participant -> !participant.getUserId().equals(currentUserId))
                    .findFirst()
                    .orElse(null);

            dto.setTitle(counterpart != null ? counterpart.getUsername() : chat.getTitle());
            dto.setAvatarUrl(counterpart != null ? counterpart.getAvatarUrl() : chat.getAvatarUrl());
            dto.setCounterpartUserId(counterpart != null ? counterpart.getUserId() : null);
            dto.setCounterpartOnline(counterpart != null ? counterpart.isOnline() : null);
            dto.setCanManageMembers(false);
            dto.setCanDeleteChat(false);
        } else {
            dto.setTitle(chat.getTitle());
            dto.setAvatarUrl(chat.getAvatarUrl());
            dto.setCanManageMembers(hasManagePermissions(membership.getRole()));
            dto.setCanDeleteChat("CREATOR".equalsIgnoreCase(membership.getRole()));
        }

        return dto;
    }

    private boolean hasManagePermissions(String role) {
        if (role == null) {
            return false;
        }
        return "CREATOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
    }
}
