package ru.mescat.message.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mescat.message.dto.EncryptMessageKeySendResultDto;
import ru.mescat.message.dto.MessageKeyForUser;
import ru.mescat.message.dto.PendingMessageKeyDto;
import ru.mescat.message.dto.RequestEncryptMessageKeyForUser;
import ru.mescat.message.dto.SendEncryptKeyDto;
import ru.mescat.message.dto.kafka.KeyDelete;
import ru.mescat.message.entity.SendMessageKeyEntity;
import ru.mescat.message.event.dto.NewMessageKey;
import ru.mescat.message.exception.NotFoundException;
import ru.mescat.message.exception.SaveToDatabaseException;
import ru.mescat.message.exception.UserBlockedException;
import ru.mescat.message.repository.SendMessageKeyRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@Slf4j
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

    public List<PendingMessageKeyDto> getPendingKeysForUser(UUID userTargetId) {
        return findAllByUserTargetId(userTargetId).stream()
                .map(entity -> new PendingMessageKeyDto(
                        entity.getId(),
                        entity.getChatId(),
                        entity.getUserId(),
                        entity.getUserTargetId(),
                        entity.getKey(),
                        entity.getPublicKey(),
                        entity.getEncryptName(),
                        entity.getSendAt()
                ))
                .toList();
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
    public boolean deleteByIdForUser(UUID keyId, UUID userTargetId) {
        if (keyId == null || userTargetId == null) {
            return false;
        }

        return sendMessageKeyRepository.deleteOwnedById(keyId, userTargetId) > 0;
    }

    @Transactional
    public int deleteAllByIdForUsers(List<KeyDelete> keyDeletes) {
        int deleted = 0;
        for (KeyDelete keyDelete : keyDeletes) {
            if (keyDelete == null) {
                continue;
            }
            if (deleteByIdForUser(keyDelete.getKeyId(), keyDelete.getUserTargetId())) {
                deleted++;
            }
        }
        return deleted;
    }

    @Transactional
    public EncryptMessageKeySendResultDto sendEncryptKey(UUID userId, SendEncryptKeyDto sendEncryptKeyDto) {
        log.info("Запрос на отправку ключей сообщений: userId={}, chatId={}",
                userId, sendEncryptKeyDto != null ? sendEncryptKeyDto.getChatId() : null);

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
            log.warn("Отправка ключей отклонена: группа не найдена. userId={}, chatId={}", userId, sendEncryptKeyDto.getChatId());
            throw new NotFoundException("Группа не найдена.");
        }

        if (usersBlackListService.isBlockedInChat(sendEncryptKeyDto.getChatId(), userId)) {
            log.warn("Отправка ключей отклонена: пользователь заблокирован. userId={}, chatId={}", userId, sendEncryptKeyDto.getChatId());
            throw new UserBlockedException("Пользователь заблокирован в данной группе.");
        }

        UUID encryptName = sendEncryptKeyDto.getEncryptName() != null
                ? sendEncryptKeyDto.getEncryptName()
                : UUID.randomUUID();
        sendEncryptKeyDto.setEncryptName(encryptName);

        List<UUID> userIds = chatUserService.findAllUserIdNotBlocksByChatId(sendEncryptKeyDto.getChatId());

        List<RequestEncryptMessageKeyForUser> verifiedUsers = getRequestEncryptMessageKeyForUsers(sendEncryptKeyDto, userIds);

        List<SendMessageKeyEntity> entitiesToSave = verifiedUsers.stream()
                .map(v -> new SendMessageKeyEntity(
                        userId,
                        sendEncryptKeyDto.getChatId(),
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
            log.error("Не удалось сохранить ключи сообщений в БД: userId={}, chatId={}, error={}",
                    userId, sendEncryptKeyDto.getChatId(), e.getMessage());
            throw new SaveToDatabaseException("Не удалось сохранить в бд!");
        }

        if (sendMessageKeyEntities.isEmpty()) {
            throw new SaveToDatabaseException("Не удалось сохранить в бд!");
        }


        applicationEventPublisher.publishEvent(new NewMessageKey(sendMessageKeyEntities.stream()
                .map(m -> new MessageKeyForUser(
                        m.getUserTargetId(),
                        m.getChatId(),
                        m.getKey(),
                        m.getEncryptName(),
                        m.getPublicKey()
                )).toList()));

        log.info("Ключи сообщений отправлены: fromUser={}, chatId={}, recipients={}, encryptName={}",
                userId, sendEncryptKeyDto.getChatId(), sendMessageKeyEntities.size(), encryptName);

        return new EncryptMessageKeySendResultDto(
                sendEncryptKeyDto.getChatId(),
                encryptName,
                sendMessageKeyEntities.size()
        );
    }

    private static List<RequestEncryptMessageKeyForUser> getRequestEncryptMessageKeyForUsers(SendEncryptKeyDto sendEncryptKeyDto, List<UUID> userIds) {
        Set<UUID> userIdsSet = new HashSet<>(userIds);

        List<RequestEncryptMessageKeyForUser> verifiedUsers = sendEncryptKeyDto.getRequestEncryptMessageKeyForUsers();
        Iterator<RequestEncryptMessageKeyForUser> iterator = verifiedUsers.iterator();

        while (iterator.hasNext()) {
            RequestEncryptMessageKeyForUser r = iterator.next();

            if (r == null || r.getUserTarget() == null || !userIdsSet.contains(r.getUserTarget())) {
                iterator.remove();
                continue;
            }

            if (r.getKey() == null || r.getKey().length == 0) {
                throw new IllegalArgumentException("Ключ сообщения для получателя не должен быть пустым.");
            }

            if (r.getPublicKeyUser() == null) {
                throw new IllegalArgumentException("publicKeyUser обязателен для каждого получателя ключа сообщения.");
            }
        }

        if (verifiedUsers.isEmpty()) {
            throw new NotFoundException("Нет доступных получателей для отправки ключей.");
        }
        return verifiedUsers;
    }
}
