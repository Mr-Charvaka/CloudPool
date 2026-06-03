package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "passwordHash", "googleAccessToken", "googleRefreshToken", "customClientId", "customClientSecret"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String role; // e.g. "USER", "ADMIN"

    @Builder.Default
    private boolean active = true;

    private LocalDateTime lastLoginAt;

    @Column(length = 1000)
    private String googleAccessToken;

    @Column(length = 1000)
    private String googleRefreshToken;

    private LocalDateTime googleTokenExpiresAt;

    @Column(length = 500)
    private String customClientId;

    @Column(length = 500)
    private String customClientSecret;

    @Builder.Default
    private long currentUsage = 0L;

    @Builder.Default
    private long storageQuota = 5368709120L; // 5GB default

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
