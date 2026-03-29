package ru.mescat.message.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mescat.message.dto.ChatDto;
import ru.mescat.message.dto.MessageDto;
import ru.mescat.message.dto.MessageForUser;
import ru.mescat.message.dto.NewMessageToNewChat;
import ru.mescat.message.entity.ChatEntity;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.entity.MessageEntity;
import ru.mescat.message.entity.enums.ChatType;
import ru.mescat.message.exception.*;
import ru.mescat.message.map.MessageDtoToMessageEntity;
import ru.mescat.message.map.MessageEntityToMessageForUser;
import ru.mescat.message.repository.MessageRepository;
import ru.mescat.message.websocket.WebSocketService;
import ru.mescat.user.dto.User;
import ru.mescat.user.service.UserService;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {
    private MessageRepository repository;
    private ChatUserService chatUserService;
    private UsersBlackListService blackListService;
    private WebSocketService webSocketService;
    private ChatService chatService;
    private UserService userService;
    private MessageDtoToMessageEntity messageDtoToMessageEntityConvert;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public MessageService(MessageRepository messageRepository,
                          ChatUserService chatUserService,
                          UsersBlackListService usersBlackListService,
                          WebSocketService webSocketService,
                          ChatService chatService,
                          UserService userService,
                          MessageDtoToMessageEntity messageDtoToMessageEntity
                          ){
        this.messageDtoToMessageEntityConvert=messageDtoToMessageEntity;
        this.userService=userService;
        this.chatService=chatService;
        this.webSocketService=webSocketService;
        this.blackListService=usersBlackListService;
        this.chatUserService=chatUserService;
        this.repository=messageRepository;
    }

    public MessageEntity save(MessageEntity message) {
        return repository.save(message);
    }

    public MessageEntity findById(Long messageId) {
        return repository.findById(messageId).orElse(null);
    }

    public boolean existsById(Long messageId) {
        return repository.existsById(messageId);
    }

    public void deleteById(Long messageId) {
        repository.deleteById(messageId);
    }

    public void sendMessage(MessageDto messageDto){

        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());

        if(!chatUserService.existsByChatIdAndUserId(messageDto.getChatId(),userId)){
            throw new ChatNotFoundException("Чат не существует.");
        }

        if(blackListService.isBlockedInChat(messageDto.getChatId(),userId)){
            throw new UserBlockedException("Вас заблокировали в данном чате.");
        }

        MessageEntity messageEntity = messageDtoToMessageEntityConvert.convert(messageDto);

        try{
            messageEntity = repository.save(messageEntity);
        } catch (Exception e){
            throw new SaveToDatabaseException("Не удалось сохранить сообщение.");
        }

        if(messageEntity.getMessageId()==null || messageEntity.getCreatedAt()==null){
            throw new DataBaseException("Проблема с постановкой данных.");
        }

        MessageForUser message = MessageEntityToMessageForUser.convert(messageEntity);

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e){
            throw new ParsingException("Не удалось запарсить данные.");
        }
        webSocketService.sendToTopic(json,message.getChatId());
    }

    public List<MessageEntity> getLastNMessagesForEachUserChat(UUID userId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Лимит должно быть больше 0.");
        }
        return repository.findLastNMessagesForEachUserChat(userId, limit);
    }

    @Transactional
    public List<MessageEntity> getMessagesRelativeToMessage(Long messageId, int count) {
        if (count == 0) {
            return List.of();
        }

        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());

        MessageEntity message = findById(messageId);
        if(message==null){
            throw new NotFoundException("Сообщение не найдено.");
        }

        if(!chatUserService.existsByChatIdAndUserId(message.getChat().getChatId(),userId)){
            throw new ChatNotFoundException("Чат не найден.");
        };


        Long chatId = message.getChat().getChatId();
        int limit = Math.abs(count);

        if (count > 0) {
            return repository.findMessagesAfter(
                    chatId,
                    messageId,
                    PageRequest.of(0, limit)
            );
        }
        List<MessageEntity> result = repository.findMessagesBefore(
                chatId,
                messageId,
                PageRequest.of(0, limit)
        );

        Collections.reverse(result);
        return result;
    }

    public MessageEntity findLatestMessageByChatId(Long chatId){
        return repository.findFirstByChat_ChatIdOrderByCreatedAtDesc(chatId);
    }

    @Transactional
    public ChatDto sendMessageAndCreateChat(NewMessageToNewChat message){

        User user = userService.findById(message.getUserId());

        if(user==null){
            throw new NotFoundException("Пользователь не найден.");
        }

        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        ChatEntity chat = chatUserService.findPersonalBetween(message.getUserId(),userId,ChatType.PERSONAL);

        if(chat==null){
            chat = new ChatEntity(ChatType.PERSONAL);
            chat = chatService.save(chat);
            chatUserService.save(new ChatUserEntity(chat,userId));
            chatUserService.save(new ChatUserEntity(chat,user.getId()));
        }

        sendMessage(new MessageDto(chat.getChatId(),message.getMessage(),message.getKeyName()));

        return new ChatDto(chat.getChatId(),chat.getChatType(),user.getUsername(),
                user.getAvatarUrl(),message.getMessage(),message.getKeyName());

    }
}
