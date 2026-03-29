package ru.mescat.service;

import org.springframework.stereotype.Service;
import ru.mescat.entity.NewPrivateKeyEntity;
import ru.mescat.repository.NewPrivateKeyRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NewPrivateKeyService {

    private NewPrivateKeyRepository newPrivateKeyRepository;

    public NewPrivateKeyService(NewPrivateKeyRepository newPrivateKeyRepository){
        this.newPrivateKeyRepository=newPrivateKeyRepository;
    }

    public List<NewPrivateKeyEntity> findAllByUserId(UUID userId){
        return newPrivateKeyRepository.findByUserId(userId);
    }

    public NewPrivateKeyEntity save(NewPrivateKeyEntity newPrivateKeyEntity){
        return newPrivateKeyRepository.save(newPrivateKeyEntity);
    }

    public List<UUID> findIdsByCreatedAtBefore(OffsetDateTime offsetDateTime){
        return newPrivateKeyRepository.findIdsByCreatedAtBefore(offsetDateTime);
    }

    public void deleteAllById(List<UUID> uuids){
        newPrivateKeyRepository.deleteAllById(uuids);
    }
}
