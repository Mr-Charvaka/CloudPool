package com.cloudpool.repository;

import com.cloudpool.model.BackgroundJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, UUID> {
}
