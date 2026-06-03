package com.cloudpool.repository;

import com.cloudpool.model.Bucket;
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
public interface BucketRepository extends TenantAwareRepository<Bucket, UUID> {
    List<Bucket> findByUser(User user);
    Optional<Bucket> findByUserAndName(User user, String name);

    @Override
    @Query("select b from Bucket b where b.id = :id and b.user.id = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Optional<Bucket> findByIdForTenant(@Param("id") UUID id);

    @Override
    @Query("select b from Bucket b where b.user.id = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Page<Bucket> findAllForTenant(Pageable pageable);
}
