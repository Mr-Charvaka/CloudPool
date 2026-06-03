package com.cloudpool.repository;

import com.cloudpool.model.StaticSite;
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
public interface StaticSiteRepository extends TenantAwareRepository<StaticSite, UUID> {
    List<StaticSite> findByUser(User user);
    Optional<StaticSite> findByUserAndDomain(User user, String domain);
    Optional<StaticSite> findByDomain(String domain);

    @Override
    @Query("select s from StaticSite s where s.id = :id and s.user.id = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Optional<StaticSite> findByIdForTenant(@Param("id") UUID id);

    @Override
    @Query("select s from StaticSite s where s.user.id = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Page<StaticSite> findAllForTenant(Pageable pageable);
}
