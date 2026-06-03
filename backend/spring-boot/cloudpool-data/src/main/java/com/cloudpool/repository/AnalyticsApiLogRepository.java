package com.cloudpool.repository;

import com.cloudpool.model.AnalyticsApiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsApiLogRepository extends JpaRepository<AnalyticsApiLog, UUID> {
    List<AnalyticsApiLog> findByProjectIdOrderByTimestampDesc(UUID projectId);
    List<AnalyticsApiLog> findAllByOrderByTimestampDesc();
}
