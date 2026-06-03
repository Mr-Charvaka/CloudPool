package com.cloudpool.repository;

import com.cloudpool.model.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {
    List<TenantUser> findByProjectId(UUID projectId);
    Optional<TenantUser> findByProjectIdAndEmail(UUID projectId, String email);
}
