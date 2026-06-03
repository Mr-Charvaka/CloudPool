package com.cloudpool.repository;

import com.cloudpool.model.TenantSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantSessionRepository extends JpaRepository<TenantSession, UUID> {
    Optional<TenantSession> findByRefreshToken(String refreshToken);

    void deleteByRefreshToken(String refreshToken);

    @Modifying
    @Query("DELETE FROM TenantSession s WHERE s.expiresAt < :now")
    int deleteExpiredSessions(LocalDateTime now);
}
