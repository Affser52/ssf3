package ru.mescat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mescat.entity.NewPrivateKeyEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NewPrivateKeyRepository extends JpaRepository<NewPrivateKeyEntity,UUID> {

    List<NewPrivateKeyEntity> findByUserId(UUID userId);

    @Query("""
        select e.id
        from NewPrivateKeyEntity e
        where e.createdAt < :time
    """)
    List<UUID> findIdsByCreatedAtBefore(@Param("time") OffsetDateTime time);
}
