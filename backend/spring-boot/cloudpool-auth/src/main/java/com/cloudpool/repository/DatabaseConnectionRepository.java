package com.cloudpool.repository;

import com.cloudpool.model.DatabaseConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DatabaseConnectionRepository extends JpaRepository<DatabaseConnection, UUID> {
    List<DatabaseConnection> findByProjectId(UUID projectId);
    Optional<DatabaseConnection> findByProjectIdAndDbType(UUID projectId, String dbType);
    void deleteByProjectId(UUID projectId);
}
