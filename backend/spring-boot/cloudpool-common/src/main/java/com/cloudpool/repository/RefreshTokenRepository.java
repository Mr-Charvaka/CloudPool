package com.cloudpool.repository;

import com.cloudpool.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("SELECT COUNT(r) > 0 FROM RefreshToken r WHERE r.familyId = :familyId AND r.revoked = true")
    boolean existsRevokedInFamily(@Param("familyId") UUID familyId);

    @Query("SELECT r FROM RefreshToken r WHERE r.familyId = :familyId AND r.revoked = false ORDER BY r.createdAt DESC")
    java.util.List<RefreshToken> findActiveByFamilyId(@Param("familyId") UUID familyId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.userId = :userId")
    void revokeAllForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.familyId = :familyId")
    void revokeAllByFamilyId(@Param("familyId") UUID familyId);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
