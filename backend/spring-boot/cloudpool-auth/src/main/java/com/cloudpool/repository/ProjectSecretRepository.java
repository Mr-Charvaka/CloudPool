package com.cloudpool.repository;

import com.cloudpool.model.ProjectSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectSecretRepository extends JpaRepository<ProjectSecret, UUID> {
    List<ProjectSecret> findByProjectId(UUID projectId);
    Optional<ProjectSecret> findByProjectIdAndSecretKey(UUID projectId, String secretKey);
    void deleteByProjectId(UUID projectId);
}
