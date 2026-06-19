package com.cloudpool.repository;

import com.cloudpool.model.VectorCollection;
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
public interface VectorCollectionRepository extends TenantAwareRepository<VectorCollection, UUID> {
    List<VectorCollection> findByUserId(UUID userId);
    Optional<VectorCollection> findByUserIdAndName(UUID userId, String name);

    @Override
    @Query("select vc from VectorCollection vc where vc.id = :id and vc.user.id = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Optional<VectorCollection> findByIdForTenant(@Param("id") UUID id);

    @Override
    @Query("select vc from VectorCollection vc where vc.user.id = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Page<VectorCollection> findAllForTenant(Pageable pageable);
}
