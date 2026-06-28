package com.cloudpool.repository;

import com.cloudpool.model.ContainerDeployment;
import com.cloudpool.model.User;
import com.cloudpool.repository.base.TenantAwareRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContainerDeploymentRepository extends TenantAwareRepository<ContainerDeployment, UUID> {
    List<ContainerDeployment> findByUserId(UUID userId);
    Optional<ContainerDeployment> findByUserIdAndName(UUID userId, String name);
    long countByUserId(UUID userId);

    @Override
    @Query("select c from ContainerDeployment c where c.id = :id and c.userId = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Optional<ContainerDeployment> findByIdForTenant(@Param("id") UUID id);

    @Override
    @Query("select c from ContainerDeployment c where c.userId = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Page<ContainerDeployment> findAllForTenant(Pageable pageable);
}
