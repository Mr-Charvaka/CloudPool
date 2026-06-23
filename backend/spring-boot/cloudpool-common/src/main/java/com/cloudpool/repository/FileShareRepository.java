package com.cloudpool.repository;

import com.cloudpool.model.FileShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileShareRepository extends JpaRepository<FileShare, UUID> {
    Optional<FileShare> findByToken(String token);
    List<FileShare> findByFileId(UUID fileId);
    void deleteByFileId(UUID fileId);

    @Modifying
    @Query("DELETE FROM FileShare f WHERE f.expiresAt IS NOT NULL AND f.expiresAt < :beforeTime")
    int deleteExpiredShares(@Param("beforeTime") LocalDateTime beforeTime);
}
