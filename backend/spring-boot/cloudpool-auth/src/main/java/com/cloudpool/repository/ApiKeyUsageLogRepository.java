package com.cloudpool.repository;

import com.cloudpool.model.ApiKeyUsageLog;
import com.cloudpool.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface ApiKeyUsageLogRepository extends JpaRepository<ApiKeyUsageLog, UUID> {
    
    @Query("SELECT log FROM ApiKeyUsageLog log WHERE log.apiKey.user = :user ORDER BY log.createdAt DESC")
    List<ApiKeyUsageLog> findByUser(@Param("user") User user);

    @Query("SELECT log.apiKey.id as keyId, log.apiKey.name as keyName, COUNT(log) as count FROM ApiKeyUsageLog log WHERE log.apiKey.user = :user GROUP BY log.apiKey.id, log.apiKey.name")
    List<Map<String, Object>> countRequestsByKey(@Param("user") User user);

    @Query("SELECT log.statusCode as statusCode, COUNT(log) as count FROM ApiKeyUsageLog log WHERE log.apiKey.user = :user GROUP BY log.statusCode")
    List<Map<String, Object>> countRequestsByStatus(@Param("user") User user);

    @Query("SELECT log.endpoint as endpoint, COUNT(log) as count FROM ApiKeyUsageLog log WHERE log.apiKey.user = :user GROUP BY log.endpoint")
    List<Map<String, Object>> countRequestsByEndpoint(@Param("user") User user);
}
