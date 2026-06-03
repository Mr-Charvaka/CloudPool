package com.cloudpool.repository;

import com.cloudpool.model.Project;
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
public interface ProjectRepository extends TenantAwareRepository<Project, UUID> {
    List<Project> findByUserId(UUID userId);

    @Override
    @Query("select p from Project p where p.id = :id and p.userId = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Optional<Project> findByIdForTenant(@Param("id") UUID id);

    @Override
    @Query("select p from Project p where p.userId = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Page<Project> findAllForTenant(Pageable pageable);
}
