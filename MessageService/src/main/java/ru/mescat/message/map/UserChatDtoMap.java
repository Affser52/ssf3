package ru.mescat.message.map;


import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ru.mescat.message.dto.ChatDto;
import ru.mescat.message.dto.auxiliary.ChatUserDto;
import ru.mescat.message.entity.ChatEntity;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.entity.MessageEntity;
import ru.mescat.message.entity.enums.ChatType;
import ru.mescat.message.exception.ChatNotFoundException;
import ru.mescat.message.exception.RemoteServiceException;
import ru.mescat.message.service.ChatService;
import ru.mescat.message.service.ChatUserService;
import ru.mescat.message.service.MessageService;
import ru.mescat.user.dto.User;

import java.util.List;
import java.util.UUID;

@Component
public class UserChatDtoMap {

    private ChatService chatService;
    private MessageService messageService;
    private ChatUserService chatUserService;

    public UserChatDtoMap(ChatService chatService, MessageService messageService, ChatUserService chatUserService){
        this.chatUserService=chatUserService;
        this.chatService=chatService;
        this.messageService=messageService;
    }

    public  ChatDto convert(User user){
        UUID userId;
        UUID userTarget;
        try{
            userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
            userTarget = user.getId();
        } catch (IllegalArgumentException e){
            throw new RemoteServiceException(1,"Данные не соответствуют нужному типу!");
        }

        ChatEntity chat = chatService.findPersonalChatBetween(userId,userTarget);
        if(chat==null){
            throw new ChatNotFoundException("Чат не найден.");
        }
        ChatDto chatDto = new ChatDto(chat.getChatId(),chat.getChatType(),user.getUsername(),user.getAvatarUrl());

        MessageEntity message = messageService.findLatestMessageByChatId(chat.getChatId());

        if(message!=null){
            chatDto.setLastMessage(message.getMessage());
            chatDto.setEncryptName(message.getEncryptionName());
        }

        return chatDto;

    }

    public List<ChatDto> convert(List<User> users) {
        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        List<ChatUserEntity> chatUsers = chatUserService.findAllByUserId(userId);
        if (chatUsers == null) {
            return null;
        } else if (chatUsers.isEmpty()) {
            return List.of();
        }

        List<ChatUserDto> userAndChats = chatUserService.findAllChatUsersByChatIds(
                chatUsers.stream().map(u -> u.getChat().getChatId()).toList(), userId
        );

        if (userAndChats == null) {
            return null;
        }

        List<ChatDto> chatDtos = users.stream().map(u -> {
            ChatUserDto chatUserDto = userAndChats.stream()
                    .filter(uc -> uc.getUserId().equals(u.getId()))
                    .findFirst()
                    .orElse(null);

            ChatUserEntity chatUserEntity = null;
            if (chatUserDto != null) {
                chatUserEntity = chatUsers.stream()
                        .filter(cu -> cu.getChat().getChatId().equals(chatUserDto.getChatId()))
                        .findFirst()
                        .orElse(null);
            }

            return new ChatDto(
                    chatUserEntity != null ? chatUserEntity.getChat().getChatId() : null,
                    chatUserEntity != null ? chatUserEntity.getChat().getChatType() : ChatType.PERSONAL,
                    u.getUsername(),
                    u.getAvatarUrl()
            );
        }).toList();

        List<MessageEntity> messageEntities = messageService.getLastNMessagesForEachUserChat(userId, 1);

        if (messageEntities == null) {
            return null;
        } else if (messageEntities.isEmpty()) {
            return chatDtos;
        }

        chatDtos.stream().forEach(cd -> {
            if (cd.getChatId() == null) {
                return;
            }

            MessageEntity message = messageEntities.stream().filter(m ->
                    m.getChat().getChatId().equals(cd.getChatId())).findFirst().orElse(null);

            if (message == null) {
                return;
            }

            cd.setLastMessage(message.getMessage());
            cd.setEncryptName(message.getEncryptionName());

        });

        return chatDtos;
    }
}
