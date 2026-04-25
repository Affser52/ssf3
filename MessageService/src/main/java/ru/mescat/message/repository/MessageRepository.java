package ru.mescat.message.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mescat.message.entity.MessageEntity;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    @Query(value = """
            SELECT *
            FROM (
                SELECT m.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY m.chat_id
                           ORDER BY m.created_at DESC
                       ) AS rn
                FROM message m
                JOIN chat_users cu ON cu.chat_id = m.chat_id
                WHERE cu.user_id = :userId
            ) t
            WHERE t.rn <= :limit
            ORDER BY t.chat_id, t.created_at DESC
            """, nativeQuery = true)
    List<MessageEntity> findLastNMessagesForEachUserChat(@Param("userId") UUID userId,
                                                         @Param("limit") int limit);

    @Query("""
            SELECT m
            FROM MessageEntity m
            WHERE m.chat.chatId = :chatId
              AND m.messageId > :messageId
            ORDER BY m.messageId ASC
            """)
    List<MessageEntity> findMessagesAfter(@Param("chatId") Long chatId,
                                          @Param("messageId") Long messageId,
                                          org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT m
            FROM MessageEntity m
            WHERE m.chat.chatId = :chatId
              AND m.messageId < :messageId
            ORDER BY m.messageId DESC
            """)
    List<MessageEntity> findMessagesBefore(@Param("chatId") Long chatId,
                                           @Param("messageId") Long messageId,
                                           org.springframework.data.domain.Pageable pageable);

    @Modifying
    long deleteByChat_ChatId(Long chatId);


    MessageEntity findFirstByChat_ChatIdOrderByCreatedAtDesc(Long chatId);

    long countByChat_ChatIdAndEncryptionName(Long chatId, String encryptionName);

    boolean existsByMessageIdAndChat_ChatIdAndSenderIdAndEncryptionName(
            Long messageId,
            Long chatId,
            UUID senderId,
            String encryptionName
    );

    @Query("""
            SELECT m
            FROM MessageEntity m
            WHERE m.chat.chatId = :chatId
            ORDER BY m.createdAt DESC
            """)
    List<MessageEntity> findRecentMessagesInChat(@Param("chatId") Long chatId,
                                                 org.springframework.data.domain.Pageable pageable);

}
