package ru.mescat.message.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mescat.message.dto.ChatDto;
import ru.mescat.message.dto.CreateGroupChatDto;
import ru.mescat.message.dto.auxiliary.ChatIdLastMessageEncryptName;
import ru.mescat.message.entity.ChatEntity;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.entity.enums.ChatType;
import ru.mescat.message.event.dto.DeleteChat;
import ru.mescat.message.exception.ChatNotFoundException;
import ru.mescat.message.exception.DataBaseException;
import ru.mescat.message.repository.ChatRepository;
import ru.mescat.message.repository.MessageRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ChatService {
    private ChatRepository repository;
    private ChatUserService chatUserService;
    private ApplicationEventPublisher applicationEventPublisher;

    public ChatService(ChatRepository repository,
                       ChatUserService chatUserService,
                       ApplicationEventPublisher applicationEventPublisher){
        this.applicationEventPublisher=applicationEventPublisher;
        this.chatUserService=chatUserService;
        this.repository=repository;
    }


    public ChatEntity save(ChatEntity chat) {
        return repository.save(chat);
    }

    public ChatEntity findById(Long chatId) {
        return repository.findById(chatId).orElse(null);
    }

    public List<ChatEntity> findAll() {
        return repository.findAll();
    }

    public boolean existsById(Long chatId) {
        return repository.existsById(chatId);
    }

    public void deleteById(Long chatId) {
        repository.deleteById(chatId);
    }

    public ChatEntity findPersonalChatBetween(UUID currentUserId, UUID targetUserId){
        return repository.findPersonalChatBetween(currentUserId,targetUserId, ChatType.PERSONAL);
    }

    @Transactional
    public ChatDto createGroupChat(UUID userId, CreateGroupChatDto dto){
        ChatEntity chat = save(new ChatEntity(ChatType.GROUP,dto.getTitle(),dto.getAvatarUrl()));

        if(chat==null){
            throw new DataBaseException("Не удалось сохранить чат.");
        }
        ChatUserEntity chatUserEntity = chatUserService.save(new ChatUserEntity(chat,userId,"CREATOR"));

        if(chatUserEntity==null){
            throw new DataBaseException("Не удалось добавить пользователя.");
        }

        return new ChatDto(chat.getChatId(),chat.getChatType(),chat.getTitle(),chat.getAvatarUrl());
    }

    @Transactional
    public void deleteChatById(Long chatId, UUID userId){
        ChatUserEntity chatUserEntity = chatUserService.findByUserIdAndChatId(chatId,userId);

        if(chatUserEntity==null){
            throw new ChatNotFoundException("Чат не найден.");
        }

        if(!chatUserEntity.getRole().equals("CREATOR")){
            throw new AccessDeniedException("Удалить группу может только ее создатель.");
        }

        deleteById(chatId);

        applicationEventPublisher.publishEvent(new DeleteChat(chatUserEntity.getChat()));

    }


}
