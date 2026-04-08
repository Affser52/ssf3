package ru.mescat.message.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mescat.message.dto.RequestEncryptMessageKeyForUser;
import ru.mescat.message.dto.MessageKeyForUser;
import ru.mescat.message.dto.SendEncryptKeyDto;
import ru.mescat.message.dto.kafka.KeyDelete;
import ru.mescat.message.entity.SendMessageKeyEntity;
import ru.mescat.message.event.dto.NewMessageKey;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.exception.SaveToDatabaseException;
import ru.mescat.message.exception.UserBlockedException;
import ru.mescat.message.repository.SendMessageKeyRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class SendMessageKeyService {

    private final SendMessageKeyRepository sendMessageKeyRepository;
    private final ChatUserService chatUserService;
    private final UsersBlackListService usersBlackListService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SendMessageKeyService(SendMessageKeyRepository sendMessageKeyRepository,
                                 ChatUserService chatUserService,
                                 UsersBlackListService usersBlackListService,
                                 ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher=applicationEventPublisher;
        this.usersBlackListService = usersBlackListService;
        this.chatUserService = chatUserService;
        this.sendMessageKeyRepository = sendMessageKeyRepository;
    }

    @Transactional
    public SendMessageKeyEntity save(SendMessageKeyEntity entity) {
        return sendMessageKeyRepository.save(entity);
    }

    @Transactional
    public List<SendMessageKeyEntity> saveAll(List<SendMessageKeyEntity> entities) {
        return sendMessageKeyRepository.saveAll(entities);
    }

    public SendMessageKeyEntity findById(UUID id) {
        return sendMessageKeyRepository.findById(id).orElse(null);
    }

    public List<SendMessageKeyEntity> findAll() {
        return sendMessageKeyRepository.findAll();
    }

    public List<SendMessageKeyEntity> findAllByUserId(UUID userId) {
        return sendMessageKeyRepository.findAllByUserId(userId);
    }

    public List<SendMessageKeyEntity> findAllByUserTargetId(UUID userTargetId) {
        return sendMessageKeyRepository.findAllByUserTargetId(userTargetId);
    }

    public SendMessageKeyEntity findByUserIdAndUserTargetId(UUID userId, UUID userTargetId) {
        return sendMessageKeyRepository.findByUserIdAndUserTargetId(userId, userTargetId).orElse(null);
    }

    public List<SendMessageKeyEntity> findAllByPublicKey(UUID publicKey) {
        return sendMessageKeyRepository.findAllByPublicKey(publicKey);
    }

    public boolean existsByUserIdAndUserTargetId(UUID userId, UUID userTargetId) {
        return sendMessageKeyRepository.existsByUserIdAndUserTargetId(userId, userTargetId);
    }

    @Transactional
    public void deleteById(UUID id) {
        sendMessageKeyRepository.deleteById(id);
    }

    @Transactional
    public void deleteByUserIdAndUserTargetId(UUID userId, UUID userTargetId) {
        sendMessageKeyRepository.deleteByUserIdAndUserTargetId(userId, userTargetId);
    }

    @Transactional
    public void deleteAllById(List<KeyDelete> keyDeletes) {
        sendMessageKeyRepository.deleteAllById(
                keyDeletes.stream().map(KeyDelete::getKeyId).toList()
        );
    }

    @Transactional
    public void sendEncryptKey(UUID userId, SendEncryptKeyDto sendEncryptKeyDto) {
        if (sendEncryptKeyDto == null) {
            throw new IllegalArgumentException("sendEncryptKeyDto не должен быть null.");
        }

        if (sendEncryptKeyDto.getChatId() == null) {
            throw new IllegalArgumentException("chatId не должен быть null.");
        }

        if (sendEncryptKeyDto.getRequestEncryptMessageKeyForUsers() == null
                || sendEncryptKeyDto.getRequestEncryptMessageKeyForUsers().isEmpty()) {
            throw new IllegalArgumentException("Список получателей ключей пуст.");
        }

        if (!chatUserService.existsByChatIdAndUserId(sendEncryptKeyDto.getChatId(), userId)) {
            throw new NotFoundException("Группа не найдена.");
        }

        if (usersBlackListService.isBlockedInChat(sendEncryptKeyDto.getChatId(), userId)) {
            throw new UserBlockedException("Пользователь заблокирован в данной группе.");
        }

        UUID encryptName = UUID.randomUUID();
        sendEncryptKeyDto.setEncryptName(encryptName);

        List<UUID> userIds = chatUserService.findAllUserIdNotBlocksByChatId(sendEncryptKeyDto.getChatId());

        List<RequestEncryptMessageKeyForUser> verifiedUsers = getRequestEncryptMessageKeyForUsers(sendEncryptKeyDto, userIds);

        List<SendMessageKeyEntity> entitiesToSave = verifiedUsers.stream()
                .map(v -> new SendMessageKeyEntity(
                        userId,
                        v.getKey(),
                        v.getPublicKeyUser(),
                        v.getUserTarget(),
                        encryptName.toString()
                ))
                .toList();

        List<SendMessageKeyEntity> sendMessageKeyEntities;
        try {
            sendMessageKeyEntities = saveAll(entitiesToSave);
        } catch (Exception e) {
            throw new SaveToDatabaseException("Не удалось сохранить в бд!");
        }

        if (sendMessageKeyEntities.isEmpty()) {
            throw new SaveToDatabaseException("Не удалось сохранить в бд!");
        }


        applicationEventPublisher.publishEvent(new NewMessageKey(sendMessageKeyEntities.stream()
                .map(m -> new MessageKeyForUser(
                        m.getUserTargetId(),
                        m.getKey(),
                        m.getEncryptName(),
                        m.getPublicKey()
                )).toList()));

    }

    private static List<RequestEncryptMessageKeyForUser> getRequestEncryptMessageKeyForUsers(SendEncryptKeyDto sendEncryptKeyDto, List<UUID> userIds) {
        Set<UUID> userIdsSet = new HashSet<>(userIds);

        List<RequestEncryptMessageKeyForUser> verifiedUsers = sendEncryptKeyDto.getRequestEncryptMessageKeyForUsers();
        Iterator<RequestEncryptMessageKeyForUser> iterator = verifiedUsers.iterator();

        while (iterator.hasNext()) {
            RequestEncryptMessageKeyForUser r = iterator.next();

            if (r == null || r.getUserTarget() == null || !userIdsSet.contains(r.getUserTarget())) {
                iterator.remove();
            }
        }

        if (verifiedUsers.isEmpty()) {
            throw new NotFoundException("Нет доступных получателей для отправки ключей.");
        }
        return verifiedUsers;
    }
}