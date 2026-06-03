package com.cloudpool.repository;

import com.cloudpool.model.DevTable;
import com.cloudpool.repository.base.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DevTableRepository extends TenantAwareRepository<DevTable, UUID> {
    Page<DevTable> findByUserId(UUID userId, Pageable pageable);
    Page<DevTable> findByProjectId(UUID projectId, Pageable pageable);
    Optional<DevTable> findByUserIdAndName(UUID userId, String name);
    Optional<DevTable> findByProjectIdAndName(UUID projectId, String name);
    boolean existsByName(String name);

    @Override
    @Query("select dt from DevTable dt where dt.id = :id and dt.userId = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Optional<DevTable> findByIdForTenant(@Param("id") UUID id);

    @Override
    @Query("select dt from DevTable dt where dt.userId = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Page<DevTable> findAllForTenant(Pageable pageable);
}
