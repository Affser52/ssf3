package ru.mescat.message.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import ru.mescat.message.dto.UserBlockDto;
import ru.mescat.message.entity.ChatEntity;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.entity.UsersBlackListEntity;
import ru.mescat.message.event.dto.NewUserBlockInChat;
import ru.mescat.message.exception.ChatNotFoundException;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.repository.UsersBlackListRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsersBlackListService {

    private final UsersBlackListRepository repository;
    private final ChatUserService chatUserService;
    private final ChatService chatService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public UsersBlackListService(UsersBlackListRepository repository,
                                 ChatUserService chatUserService,
                                 ChatService chatService,
                                 ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher=applicationEventPublisher;
        this.chatService=chatService;
        this.chatUserService=chatUserService;
        this.repository = repository;
    }

    public UsersBlackListEntity save(UsersBlackListEntity entity) {
        UsersBlackListEntity result =  repository.save(entity);
        applicationEventPublisher.publishEvent(new NewUserBlockInChat(result));
        return result;
    }

    public UsersBlackListEntity findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public boolean isBlockedInChat(Long chatId, UUID userTarget) {
        return repository.existsByChat_ChatIdAndUserTarget(chatId, userTarget);
    }

    public boolean isBlockedByUserInChat(UUID userInitiator, Long chatId, UUID userTarget) {
        return repository.existsByUserInitiatorAndChat_ChatIdAndUserTarget(userInitiator, chatId, userTarget);
    }

    public List<UsersBlackListEntity> getAllUserBlocks(UUID userInitiator) {
        return repository.findAllByUserInitiator(userInitiator);
    }

    public List<UsersBlackListEntity> getAllBlocksOfTargetUser(UUID userTarget) {
        return repository.findAllByUserTarget(userTarget);
    }

    public List<UsersBlackListEntity> getAllChatBlocks(Long chatId) {
        return repository.findAllByChat_ChatId(chatId);
    }

    public List<UUID> getAllUserIdBlocksByChatId(Long chatId){
        return repository.getAllUserIdBlocksByChatId(chatId);
    }

    public Optional<UsersBlackListEntity> findBlock(UUID userInitiator, Long chatId, UUID userTarget) {
        return repository.findByUserInitiatorAndChat_ChatIdAndUserTarget(userInitiator, chatId, userTarget);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public void unblock(UUID userInitiator, Long chatId, UUID userTarget) {
        repository.deleteByUserInitiatorAndChat_ChatIdAndUserTarget(userInitiator, chatId, userTarget);
    }

    public UsersBlackListEntity addBlock(UUID userId, UserBlockDto userBlockDto){
        ChatEntity chat = chatService.findById(userBlockDto.getChatId());

        if(chat==null){
            throw new ChatNotFoundException("Чат не найден.");
        }

        ChatUserEntity initiator = chatUserService.findByUserIdAndChatId(userBlockDto.getChatId(),userId);

        if(initiator==null){
            throw new NotFoundException("Вы не состоите в данном чате.");
        }

        ChatUserEntity target= chatUserService.findByUserIdAndChatId(userBlockDto.getChatId(),userBlockDto.getUserId());

        if(target==null){
            throw new NotFoundException("Этот участник не состоит в чате.");
        }

        if(!initiator.getRole().equalsIgnoreCase("ADMIN") && !initiator.getRole().equalsIgnoreCase("CREATOR")){
            throw new AccessDeniedException("Нет прав исключать из группы.");
        }

        if(target.getRole().equalsIgnoreCase("CREATOR")){
            throw new AccessDeniedException("Вы не можете исключить создателя группы.");
        }

        return save(new UsersBlackListEntity(initiator.getUserId(),chat,target.getUserId()));
    }
}