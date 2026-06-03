package com.cloudpool.repository;

import com.cloudpool.model.CronJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CronJobRepository extends JpaRepository<CronJob, UUID> {
    List<CronJob> findByProjectId(UUID projectId);
    Optional<CronJob> findByProjectIdAndName(UUID projectId, String name);
}
