package com.cloudpool.repository.base;
 
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
 
import java.util.Optional;
 
@NoRepositoryBean
public interface TenantAwareRepository<T, ID> extends JpaRepository<T, ID> {
    
    /**
     * Find by ID for current tenant
     */
    Optional<T> findByIdForTenant(ID id);
    
    /**
     * List for current tenant
     */
    Page<T> findAllForTenant(Pageable pageable);
}
