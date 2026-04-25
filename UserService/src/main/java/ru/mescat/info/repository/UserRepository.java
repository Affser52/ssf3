package ru.mescat.info.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import ru.mescat.info.entity.UserEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByUsername(String username);

    List<UserEntity> findByUsernameContainingIgnoreCase(String part, Pageable pageable);

    @Query("""
            select count(u) > 0
            from UserEntity u
            where lower(u.username) = lower(:username)
              and u.id <> :userId
            """)
    boolean existsByUsernameIgnoreCaseAndIdNot(@Param("username") String username,
                                               @Param("userId") UUID userId);

    @Modifying
    @Query("""
           update UserEntity u
           set u.password = :password
           where u.id = :userId
           """)
    int updatePasswordById(@Param("userId") UUID userId,
                           @Param("password") String password);

    @Modifying
    @Query("""
           update UserEntity u
           set u.online = :online
           where u.id = :userId
           """)
    int updateOnlineById(@Param("userId") UUID userId,
                         @Param("online") boolean online);

    @Modifying
    @Query("""
           update UserEntity u
           set u.username = :username
           where u.id = :userId
           """)
    int updateUsernameById(@Param("userId") UUID userId,
                           @Param("username") String username);

    @Modifying
    @Query("""
           update UserEntity u
           set u.avatarUrl = :avatarUrl
           where u.id = :userId
           """)
    int updateAvatarUrlById(@Param("userId") UUID userId,
                            @Param("avatarUrl") String avatarUrl);

    @Modifying
    @Query("""
           update UserEntity u
           set u.blocked = :blocked
           where u.id = :userId
           """)
    int updateBlockedById(@Param("userId") UUID userId,
                          @Param("blocked") boolean blocked);

    @Query("""
            SELECT u.id FROM UserEntity u
            WHERE u.username = :username
            """)
    UUID getIdByUsername(String username);

}
