package ru.mescat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mescat.entity.PublicKeyEntity;

import java.util.List;
import java.util.UUID;

public interface PublicKeyRepository extends JpaRepository<PublicKeyEntity, UUID> {
    PublicKeyEntity findByUserId(UUID userId);

    List<PublicKeyEntity> findAllByUserIdIn(List<UUID> userIds);
}
