package com.cloudpool.repository;

import com.cloudpool.model.FileShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileShareRepository extends JpaRepository<FileShare, UUID> {
    Optional<FileShare> findByToken(String token);
    List<FileShare> findByFileId(UUID fileId);
    void deleteByFileId(UUID fileId);
}
