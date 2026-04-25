package ru.mescat.message.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mescat.message.dto.AddUserInChatDto;
import ru.mescat.message.dto.auxiliary.ChatUserDto;
import ru.mescat.message.entity.ChatEntity;
import ru.mescat.message.entity.ChatUserEntity;
import ru.mescat.message.entity.enums.ChatType;
import ru.mescat.message.event.dto.NewUserInChat;
import ru.mescat.message.exception.AccessDeniedException;
import ru.mescat.message.exception.ChatNotFoundException;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.repository.ChatUserRepository;
import ru.mescat.user.dto.User;
import ru.mescat.user.dto.UserSettings;
import ru.mescat.user.service.UserService;

import java.util.List;
import java.util.UUID;

@Service
public class ChatUserService {

    private final ChatUserRepository repository;
    private final ChatService chatService;
    private final UserService userService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ChatUserService(ChatUserRepository repository,
                           ChatService chatService,
                           UserService userService,
                           ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.userService = userService;
        this.chatService = chatService;
        this.repository = repository;
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

    public List<ChatUserDto> findAllChatUsersByChatIds(List<Long> chatIds, UUID noTargetUser) {
        return repository.findAllChatUsersByChatIds(chatIds, noTargetUser);
    }

    public ChatEntity findPersonalBetween(UUID user1, UUID user2, ChatType chatType) {
        return repository.findPersonalChatBetween(user1, user2, chatType);
    }

    public List<UUID> findAllUserIdNotBlocksByChatId(Long chatId) {
        return repository.findAllUserIdNotBlocksByChatId(chatId);
    }

    public List<ChatUserEntity> findAllNotBlocksByChatId(Long chatId) {
        return repository.findAllNotBlocksByChatId(chatId);
    }

    public ChatUserEntity findByUserIdAndChatId(Long chatId, UUID userId) {
        return repository.findByUserIdAndChatId(chatId, userId);
    }

    @Transactional
    public ChatUserEntity addNewUserInChat(AddUserInChatDto dto, UUID initiatorId) {
        if (dto == null || dto.getChatId() == null || dto.getUserTarget() == null || initiatorId == null) {
            throw new IllegalArgumentException("Некорректные данные для добавления участника.");
        }

        ChatEntity chat = chatService.findById(dto.getChatId());
        if (chat == null) {
            throw new ChatNotFoundException("Чат не найден.");
        }

        if (chat.getChatType() == ChatType.PERSONAL) {
            throw new AccessDeniedException("В личный диалог нельзя добавлять новых участников.");
        }

        ChatUserEntity initiator = findByUserIdAndChatId(dto.getChatId(), initiatorId);
        if (initiator == null) {
            throw new AccessDeniedException("Нет доступа к этому чату.");
        }
        if (!canManageMembers(initiator)) {
            throw new AccessDeniedException("Добавлять участников могут только администраторы или создатель группы.");
        }

        User user = userService.findById(dto.getUserTarget());
        if (user == null) {
            throw new NotFoundException("Пользователь не найден.");
        }

        ChatUserEntity existing = findByUserIdAndChatId(dto.getChatId(), user.getId());
        if (existing != null) {
            return existing;
        }

        UserSettings userSettings = userService.getSettingsById(user.getId());
        if (userSettings != null && !userSettings.isAllowAddChat()) {
            throw new AccessDeniedException("Пользователь запретил добавлять себя в чаты.");
        }

        return save(new ChatUserEntity(chat, user.getId()));
    }

    public void deleteUserFromChat(AddUserInChatDto dto, UUID userId) {
        ChatUserEntity initiator = findByUserIdAndChatId(dto.getChatId(), userId);
        if (initiator == null) {
            throw new ChatNotFoundException("Чат не найден.");
        }

        ChatUserEntity target = findByUserIdAndChatId(dto.getChatId(), dto.getUserTarget());
        if (target == null) {
            throw new NotFoundException("Пользователь не найден в чате.");
        }

        if (!canManageMembers(initiator) && !userId.equals(dto.getUserTarget())) {
            throw new AccessDeniedException("Недостаточно прав для удаления участника из чата.");
        }

        deleteById(target.getId());
    }

    private boolean canManageMembers(ChatUserEntity user) {
        return user.getRole() != null
                && ("CREATOR".equalsIgnoreCase(user.getRole()) || "ADMIN".equalsIgnoreCase(user.getRole()));
    }
}
