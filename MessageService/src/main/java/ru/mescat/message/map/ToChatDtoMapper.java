package ru.mescat.message.map;

import org.springframework.stereotype.Component;
import ru.mescat.message.dto.ChatDto;
import ru.mescat.message.dto.auxiliary.ChatUserDto;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.entity.MessageEntity;
import ru.mescat.message.entity.enums.ChatType;
import ru.mescat.message.service.ChatUserService;
import ru.mescat.message.service.MessageService;
import ru.mescat.user.dto.User;
import ru.mescat.user.service.UserService;

import java.util.*;
import java.util.stream.Collectors;

@Component

public class ToChatDtoMapper {

    private final UserService userService;
    private final ChatUserService chatUserService;
    private MessageService messageService;

    public ToChatDtoMapper(UserService userService, ChatUserService chatUserService,MessageService messageService) {
        this.messageService=messageService;
        this.chatUserService = chatUserService;
        this.userService = userService;
    }

    public List<ChatDto> convert(List<ChatUserEntity> chatUserEntities, UUID userMain) {
        List<ChatDto> chatDtos = new ArrayList<>();
        chatDtos.addAll(personalConvert(chatUserEntities, userMain));
        chatDtos.addAll(groupConvert(chatUserEntities, userMain));
        return chatDtos;
    }

    private List<ChatDto> personalConvert(List<ChatUserEntity> chatUserEntities, UUID userMain) {
        List<ChatUserEntity> personalChats = chatUserEntities.stream()
                .filter(c -> c.getChat().getChatType().equals(ChatType.PERSONAL))
                .toList();
        List<Long> chatIds = personalChats.stream()
                .map(c -> c.getChat().getChatId())
                .toList();
        List<ChatUserDto> chatUserDtos = chatUserService.findAllChatUsersByChatIds(chatIds, userMain);
        List<UUID> userIds = chatUserDtos.stream()
                .map(ChatUserDto::getUserId)
                .toList();
        List<User> users = userService.findAllByIds(userIds);
        List<MessageEntity> messageEntities = messageService.getLastNMessagesForEachUserChat(userMain, 1);

        Map<Long, ChatUserDto> chatUserMap = chatUserDtos.stream()
                .collect(Collectors.toMap(
                        ChatUserDto::getChatId,
                        cu -> cu,
                        (a, b) -> a
                ));

        Map<UUID, User> userMap = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> u,
                        (a, b) -> a
                ));

        Map<Long, MessageEntity> messageMap = messageEntities.stream()
                .collect(Collectors.toMap(
                        m -> m.getChat().getChatId(),
                        m -> m,
                        (a, b) -> a
                ));
        return personalChats.stream().map(c -> {
            Long currentChatId = c.getChat().getChatId();
            ChatUserDto chatUserDto = chatUserMap.get(currentChatId);
            User user = chatUserDto != null ? userMap.get(chatUserDto.getUserId()) : null;
            MessageEntity message = messageMap.get(currentChatId);

            return new ChatDto(
                    currentChatId,
                    c.getChat().getChatType(),
                    user != null ? user.getUsername() : null,
                    user != null ? user.getAvatarUrl() : null,
                    message != null ? message.getMessage() : null,
                    message != null ? message.getEncryptionName() : null
            );
        }).toList();
    }

    public List<ChatDto> groupConvert(List<ChatUserEntity> chatUserEntities, UUID userMain) {
        List<MessageEntity> messageEntities = messageService.getLastNMessagesForEachUserChat(userMain, 1);

        List<ChatDto> result = chatUserEntities.stream()
                .filter(c -> c.getChat().getChatType() == ChatType.GROUP)
                .map(c -> new ChatDto(
                        c.getChat().getChatId(),
                        c.getChat().getChatType(),
                        c.getChat().getTitle(),
                        c.getChat().getAvatarUrl()
                ))
                .toList();

        if(messageEntities==null){
            return result;
        }

        for(ChatDto chatDto: result){
            Long chatId = chatDto.getChatId();

            MessageEntity message = messageEntities.stream()
                    .filter(m -> m.getChat().getChatId().equals(chatId))
                    .findFirst()
                    .orElse(null);

            if(message==null){
                continue;
            }
            chatDto.setLastMessage(message.getMessage());
            chatDto.setEncryptName(message.getEncryptionName());
        }

        return result;
    }
}