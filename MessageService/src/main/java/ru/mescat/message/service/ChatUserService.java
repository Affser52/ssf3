package ru.mescat.message.service;


import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import ru.mescat.message.dto.AddUserInChatDto;
import ru.mescat.message.dto.auxiliary.ChatUserDto;
import ru.mescat.message.entity.ChatEntity;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.entity.enums.ChatType;
import ru.mescat.message.event.dto.NewUserInChat;
import ru.mescat.message.exception.ChatNotFoundException;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.repository.ChatUserRepository;
import ru.mescat.user.dto.User;
import ru.mescat.user.service.UserService;

import java.util.List;
import java.util.UUID;

@Service
public class ChatUserService {
    private ChatUserRepository repository;
    private ChatService chatService;
    private UserService userService;
    private ApplicationEventPublisher applicationEventPublisher;

    public ChatUserService(ChatUserRepository repository,
                           ChatService chatService,
                           UserService userService,
                           ApplicationEventPublisher applicationEventPublisher){
        this.applicationEventPublisher=applicationEventPublisher;
        this.userService=userService;
        this.chatService=chatService;
        this.repository=repository;
    }

    public ChatUserEntity save(ChatUserEntity chatUserEntity) {
        ChatUserEntity result = repository.save(chatUserEntity);
        applicationEventPublisher.publishEvent(new NewUserInChat(result));
        return result;
    }

    public ChatUserEntity findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public List<ChatUserEntity> findAllByUserId(UUID userId) {
        return repository.findAllByUserId(userId);
    }


    public boolean existsById(Long id) {
        return repository.existsById(id);
    }

    public boolean existsByChatIdAndUserId(Long chatId, UUID userId) {
        return repository.existsByChat_ChatIdAndUserId(chatId, userId);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public List<ChatUserDto> findAllChatUsersByChatIds(List<Long> chatIds,UUID noTargetUser){
        return repository.findAllChatUsersByChatIds(chatIds, noTargetUser);
    }

    public ChatEntity findPersonalBetween(UUID user1, UUID user2, ChatType chatType){
        return repository.findPersonalChatBetween(user1,user2,chatType);
    }

    public List<UUID> findAllUserIdNotBlocksByChatId(Long chatId){
        return repository.findAllUserIdNotBlocksByChatId(chatId);
    }

    public List<ChatUserEntity> findAllNotBlocksByChatId(Long chatId){
        return repository.findAllNotBlocksByChatId(chatId);
    }

    public ChatUserEntity findByUserIdAndChatId(Long chatId,UUID userId){
        return repository.findByUserIdAndChatId(chatId,userId);
    }

    public ChatUserEntity addNewUserInChat(AddUserInChatDto dto){

        ChatEntity chat = chatService.findById(dto.getChatId());

        if(chat==null){
            throw new ChatNotFoundException("Чат не найден.");
        }

        User user = userService.findById(dto.getUserTarget());

        if(user==null){
            throw new NotFoundException("Пользователь не найден.");
        }

        ChatUserEntity chatUserEntity = save(new ChatUserEntity(chat,user.getId()));

        applicationEventPublisher.publishEvent(new NewUserInChat(chatUserEntity));

        return chatUserEntity;
    }

}
