package com.cloudpool.repository;

import com.cloudpool.model.KvStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KvStoreRepository extends JpaRepository<KvStore, UUID> {

    List<KvStore> findByProjectId(UUID projectId);
    Optional<KvStore> findByProjectIdAndKeyName(UUID projectId, String keyName);

    void deleteByProjectIdAndKeyName(UUID projectId, String keyName);

    @Modifying
    @Query("DELETE FROM KvStore k WHERE k.expiresAt IS NOT NULL AND k.expiresAt < :now")
    int deleteExpiredKeys(LocalDateTime now);
}
