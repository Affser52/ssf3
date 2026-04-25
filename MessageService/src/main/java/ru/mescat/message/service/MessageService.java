package ru.mescat.message.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mescat.message.dto.ChatDto;
import ru.mescat.message.dto.DeleteMessageDto;
import ru.mescat.message.dto.LatestChatEncryptionUsageDto;
import ru.mescat.message.dto.MessageDto;
import ru.mescat.message.dto.MessageForUser;
import ru.mescat.message.dto.NewMessageToNewChat;
import ru.mescat.message.entity.ChatEntity;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.entity.MessageEntity;
import ru.mescat.message.entity.enums.ChatType;
import ru.mescat.message.event.dto.DeleteMessage;
import ru.mescat.message.event.dto.NewMessage;
import ru.mescat.message.exception.*;
import ru.mescat.message.map.MessageDtoToMessageEntity;
import ru.mescat.message.map.MessageEntityToMessageForUser;
import ru.mescat.message.repository.MessageRepository;
import ru.mescat.user.dto.User;
import ru.mescat.user.dto.UserSettings;
import ru.mescat.user.service.UserService;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class MessageService {
    private MessageRepository repository;
    private ChatUserService chatUserService;
    private UsersBlackListService blackListService;
    private ChatService chatService;
    private UserService userService;
    private MessageDtoToMessageEntity messageDtoToMessageEntityConvert;
    ApplicationEventPublisher applicationEventPublisher;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public MessageService(MessageRepository messageRepository,
                          ChatUserService chatUserService,
                          UsersBlackListService usersBlackListService,
                          ChatService chatService,
                          UserService userService,
                          MessageDtoToMessageEntity messageDtoToMessageEntity,
                          ApplicationEventPublisher applicationEventPublisher
                          ){
        this.applicationEventPublisher=applicationEventPublisher;
        this.messageDtoToMessageEntityConvert=messageDtoToMessageEntity;
        this.userService=userService;
        this.chatService=chatService;
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

    @Transactional
    public MessageForUser sendMessage(UUID userId, MessageDto messageDto){
        log.info("Запрос на отправку сообщения: userId={}, chatId={}", userId, messageDto.getChatId());

        if(!chatUserService.existsByChatIdAndUserId(messageDto.getChatId(),userId)){
            log.warn("Отправка сообщения отклонена: пользователь не состоит в чате. userId={}, chatId={}",
                    userId, messageDto.getChatId());
            throw new ChatNotFoundException("Чат не существует.");
        }

        if(blackListService.isBlockedInChat(messageDto.getChatId(),userId)){
            log.warn("Отправка сообщения отклонена: пользователь заблокирован в чате. userId={}, chatId={}",
                    userId, messageDto.getChatId());
            throw new UserBlockedException("Вас заблокировали в данном чате.");
        }

        ChatEntity chat = chatService.findById(messageDto.getChatId());
        if (chat != null && chat.getChatType() == ChatType.PERSONAL) {
            UUID targetUserId = findOtherUserIdInPersonalChat(messageDto.getChatId(), userId);
            ensureWritingAllowed(targetUserId);
        }

        MessageEntity messageEntity = messageDtoToMessageEntityConvert.convert(messageDto, userId);

        try{
            messageEntity = repository.save(messageEntity);
        } catch (Exception e){
            log.error("Не удалось сохранить сообщение: userId={}, chatId={}, error={}",
                    userId, messageDto.getChatId(), e.getMessage());
            throw new SaveToDatabaseException("Не удалось сохранить сообщение.");
        }

        if(messageEntity.getMessageId()==null || messageEntity.getCreatedAt()==null){
            log.error("Сообщение сохранено некорректно: отсутствует messageId или createdAt. userId={}, chatId={}",
                    userId, messageDto.getChatId());
            throw new DataBaseException("Проблема с постановкой данных.");
        }

        MessageForUser message = MessageEntityToMessageForUser.convert(messageEntity);

        applicationEventPublisher.publishEvent(new NewMessage(messageEntity));
        log.info("Сообщение отправлено: messageId={}, chatId={}, senderId={}",
                messageEntity.getMessageId(), messageEntity.getChat().getChatId(), userId);

        return message;
    }

    public List<MessageEntity> getLastNMessagesForEachUserChat(UUID userId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Лимит должно быть больше 0.");
        }
        return repository.findLastNMessagesForEachUserChat(userId, limit);
    }

    @Transactional(readOnly = true)
    public List<MessageForUser> getLastNMessagesForEachUserChatDto(UUID userId, int limit) {
        return getLastNMessagesForEachUserChat(userId, limit).stream()
                .map(MessageEntityToMessageForUser::convert)
                .toList();
    }

    public List<MessageEntity> getRecentMessagesInChat(UUID userId, Long chatId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Лимит должен быть больше 0.");
        }
        if (!chatUserService.existsByChatIdAndUserId(chatId, userId)) {
            throw new ChatNotFoundException("Чат не найден.");
        }

        List<MessageEntity> desc = repository.findRecentMessagesInChat(
                chatId,
                PageRequest.of(0, limit)
        );

        Collections.reverse(desc);
        return desc;
    }

    @Transactional(readOnly = true)
    public List<MessageForUser> getRecentMessagesInChatDto(UUID userId, Long chatId, int limit) {
        return getRecentMessagesInChat(userId, chatId, limit).stream()
                .map(MessageEntityToMessageForUser::convert)
                .toList();
    }

    @Transactional
    public void deleteMessage(DeleteMessageDto deleteMessage, UUID userId){
        log.info("Запрос на удаление сообщения: userId={}, chatId={}, messageId={}",
                userId, deleteMessage.getChatId(), deleteMessage.getMessageId());

        ChatUserEntity chatUserEntity = chatUserService.findByUserIdAndChatId(deleteMessage.getChatId(),userId);
        if(chatUserEntity==null){
            log.warn("Удаление сообщения отклонено: чат не найден для пользователя. userId={}, chatId={}",
                    userId, deleteMessage.getChatId());
            throw new ChatNotFoundException("Чат не найден.");
        }

        MessageEntity message = findById(deleteMessage.getMessageId());

        if(message==null){
            log.warn("Удаление сообщения отклонено: сообщение не найдено. messageId={}", deleteMessage.getMessageId());
            throw new NotFoundException("Сообщение не найдено.");
        }

        if(chatUserEntity.getChat().getChatType()==ChatType.PERSONAL){
            if(!message.getSenderId().equals(userId)){
                log.warn("Удаление сообщения отклонено: попытка удалить чужое сообщение в личном чате. userId={}, messageId={}",
                        userId, deleteMessage.getMessageId());
                throw new AccessDeniedException("Вы не можете удалить не свое сообщение.");
            }
        } else if(chatUserEntity.getChat().getChatType()==ChatType.GROUP){
            if(!chatUserEntity.getRole().equalsIgnoreCase("ADMIN")
                    && !chatUserEntity.getRole().equalsIgnoreCase("CREATOR")
                    && !message.getSenderId().equals(userId)){
                log.warn("Удаление сообщения отклонено: недостаточно прав. userId={}, chatId={}, role={}",
                        userId, deleteMessage.getChatId(), chatUserEntity.getRole());
                throw new AccessDeniedException("Вы не можете удалить сообщение.");
            }
        }

        deleteById(deleteMessage.getMessageId());

        applicationEventPublisher.publishEvent(new DeleteMessage(message));
        log.info("Сообщение удалено: messageId={}, chatId={}, deletedBy={}",
                deleteMessage.getMessageId(), deleteMessage.getChatId(), userId);
    }

    @Transactional
    public List<MessageEntity> getMessagesRelativeToMessage(Long messageId, int count, UUID userId) {
        if (count == 0) {
            return List.of();
        }

        MessageEntity message = findById(messageId);
        if(message==null){
            throw new NotFoundException("Сообщение не найдено.");
        }

        if(!chatUserService.existsByChatIdAndUserId(message.getChat().getChatId(),userId)){
            throw new ChatNotFoundException("Чат не найден.");
        }

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

    @Transactional(readOnly = true)
    public List<MessageForUser> getMessagesRelativeToMessageDto(Long messageId, int count, UUID userId) {
        return getMessagesRelativeToMessage(messageId, count, userId).stream()
                .map(MessageEntityToMessageForUser::convert)
                .toList();
    }

    public MessageEntity findLatestMessageByChatId(Long chatId){
        return repository.findFirstByChat_ChatIdOrderByCreatedAtDesc(chatId);
    }

    @Transactional(readOnly = true)
    public LatestChatEncryptionUsageDto getLatestEncryptionUsage(UUID userId, Long chatId) {
        if (!chatUserService.existsByChatIdAndUserId(chatId, userId)) {
            throw new ChatNotFoundException("Чат не найден.");
        }

        MessageEntity latestMessage = findLatestMessageByChatId(chatId);
        if (latestMessage == null || latestMessage.getEncryptionName() == null || latestMessage.getEncryptionName().isBlank()) {
            return new LatestChatEncryptionUsageDto(chatId, null, 0L);
        }

        long count = repository.countByChat_ChatIdAndEncryptionName(chatId, latestMessage.getEncryptionName());
        return new LatestChatEncryptionUsageDto(chatId, latestMessage.getEncryptionName(), count);
    }

    @Transactional(readOnly = true)
    public void validateMessageKeyRequest(UUID requesterId,
                                          Long chatId,
                                          Long messageId,
                                          UUID senderId,
                                          String encryptName) {
        if (requesterId == null || chatId == null || messageId == null || senderId == null || encryptName == null || encryptName.isBlank()) {
            throw new IllegalArgumentException("Некорректный запрос ключа сообщения.");
        }
        if (requesterId.equals(senderId)) {
            throw new AccessDeniedException("Нельзя запрашивать ключ у самого себя.");
        }
        if (!chatUserService.existsByChatIdAndUserId(chatId, requesterId)
                || !chatUserService.existsByChatIdAndUserId(chatId, senderId)) {
            throw new AccessDeniedException("Нет доступа к этому чату.");
        }
        boolean messageMatches = repository.existsByMessageIdAndChat_ChatIdAndSenderIdAndEncryptionName(
                messageId,
                chatId,
                senderId,
                encryptName
        );
        if (!messageMatches) {
            throw new NotFoundException("Сообщение с таким ключом не найдено.");
        }
    }

    @Transactional
    public ChatDto sendMessageAndCreateChat(UUID userId,NewMessageToNewChat message){
        log.info("Запрос на создание личного чата и отправку первого сообщения: initiatorId={}, targetId={}",
                userId, message.getUserId());

        User user = userService.findById(message.getUserId());

        if(user==null){
            log.warn("Создание личного чата отклонено: целевой пользователь не найден. targetId={}", message.getUserId());
            throw new NotFoundException("Пользователь не найден.");
        }

        ensureAddChatAllowed(user.getId());

        ChatEntity chat = chatUserService.findPersonalBetween(message.getUserId(),userId,ChatType.PERSONAL);

        if(chat==null){
            chat = new ChatEntity(ChatType.PERSONAL);
            chat = chatService.save(chat);
            chatUserService.save(new ChatUserEntity(chat,userId));
            chatUserService.save(new ChatUserEntity(chat,user.getId()));
            log.info("Создан новый личный чат: chatId={}, userA={}, userB={}",
                    chat.getChatId(), userId, user.getId());
        }

        if(chat==null){
            throw  new RuntimeException("Не удалось создать чат.");
        }

        sendMessage(userId, new MessageDto(chat.getChatId(),message.getMessage(),message.getKeyName()));
        log.info("Первое сообщение в личном чате отправлено: chatId={}, senderId={}", chat.getChatId(), userId);

        return new ChatDto(chat.getChatId(),chat.getChatType(),user.getUsername(),
                user.getAvatarUrl(),message.getMessage(),message.getKeyName());

    }

    @Transactional
    public ChatDto createPersonalChat(UUID userId, UUID targetUserId) {
        log.info("Запрос на создание личного чата без сообщения: initiatorId={}, targetId={}", userId, targetUserId);

        User user = userService.findById(targetUserId);
        if (user == null) {
            throw new NotFoundException("Пользователь не найден.");
        }

        ensureAddChatAllowed(user.getId());

        ChatEntity chat = chatUserService.findPersonalBetween(targetUserId, userId, ChatType.PERSONAL);
        if (chat == null) {
            chat = new ChatEntity(ChatType.PERSONAL);
            chat = chatService.save(chat);
            chatUserService.save(new ChatUserEntity(chat, userId));
            chatUserService.save(new ChatUserEntity(chat, user.getId()));
            log.info("Создан новый личный чат без сообщения: chatId={}, userA={}, userB={}",
                    chat.getChatId(), userId, user.getId());
        }

        return new ChatDto(chat.getChatId(), chat.getChatType(), user.getUsername(), user.getAvatarUrl());
    }

    private UUID findOtherUserIdInPersonalChat(Long chatId, UUID userId) {
        return chatUserService.findAllUserIdNotBlocksByChatId(chatId).stream()
                .filter(candidate -> !candidate.equals(userId))
                .findFirst()
                .orElse(null);
    }

    private void ensureAddChatAllowed(UUID targetUserId) {
        UserSettings userSettings = targetUserId != null ? userService.getSettingsById(targetUserId) : null;
        if (userSettings != null && !userSettings.isAllowAddChat()) {
            throw new AccessDeniedException("Пользователь запретил добавлять себя в чаты.");
        }
    }

    private void ensureWritingAllowed(UUID targetUserId) {
        UserSettings userSettings = targetUserId != null ? userService.getSettingsById(targetUserId) : null;
        if (userSettings != null && !userSettings.isAllowWriting()) {
            throw new AccessDeniedException("Пользователь запретил писать ему в личные сообщения.");
        }
    }
}
