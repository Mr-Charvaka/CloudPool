package com.cloudpool.repository;

import com.cloudpool.model.ServerlessFunction;
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
public interface ServerlessFunctionRepository extends TenantAwareRepository<ServerlessFunction, UUID> {
    List<ServerlessFunction> findByUser(User user);
    Optional<ServerlessFunction> findByUserAndName(User user, String name);
    Optional<ServerlessFunction> findByUserAndTriggerRoute(User user, String triggerRoute);

    @Override
    @Query("select f from ServerlessFunction f where f.id = :id and f.user.id = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Optional<ServerlessFunction> findByIdForTenant(@Param("id") UUID id);

    @Override
    @Query("select f from ServerlessFunction f where f.user.id = :#{T(java.util.UUID).fromString(T(com.cloudpool.context.TenantContextHolder).getTenantId())}")
    Page<ServerlessFunction> findAllForTenant(Pageable pageable);
}
