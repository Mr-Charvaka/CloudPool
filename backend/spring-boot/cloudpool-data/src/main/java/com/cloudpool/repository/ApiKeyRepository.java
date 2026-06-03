package com.cloudpool.repository;

import com.cloudpool.model.ApiKey;
import com.cloudpool.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHash(String keyHash);
    List<ApiKey> findByUser(User user);

    // Eagerly loads User to avoid LazyInitializationException in security filters
    @Query("SELECT k FROM ApiKey k JOIN FETCH k.user WHERE k.keyHash = :keyHash")
    Optional<ApiKey> findByKeyHashWithUser(@Param("keyHash") String keyHash);
}
