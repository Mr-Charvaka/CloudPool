package com.cloudpool.repository;

import com.cloudpool.model.ProjectSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectSnapshotRepository extends JpaRepository<ProjectSnapshot, UUID> {
    List<ProjectSnapshot> findByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
