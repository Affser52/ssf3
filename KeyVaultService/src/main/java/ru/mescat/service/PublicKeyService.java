package ru.mescat.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mescat.entity.PublicKeyEntity;
import ru.mescat.repository.PublicKeyRepository;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class PublicKeyService {

    private PublicKeyRepository repository;

    public PublicKeyService(PublicKeyRepository repository){
        this.repository=repository;
    }

    public List<PublicKeyEntity> findAllById(UUID userId){
        return repository.findAllById(Collections.singleton(userId));
    }

    public List<PublicKeyEntity> findAllById(List<UUID> ids){
        return repository.findAllById(ids);
    }

    @Transactional
    public PublicKeyEntity save(PublicKeyEntity entity){
        return repository.save(entity);
    }

    @Transactional
    public void deleteById(UUID id){
        repository.deleteById(id);
    }

   public PublicKeyEntity findById(UUID id){
        return repository.findById(id).orElse(null);
   }

   public PublicKeyEntity findByUserId(UUID userId){
        return repository.findByUserId(userId);
   }

   public List<PublicKeyEntity> findAllByUserIdIn(List<UUID> userIds){
        return repository.findAllByUserIdIn(userIds);
   }

}
