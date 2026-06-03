package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String action;

    private String resourceType;
    private String resourceId;

    @Column(length = 2000)
    private String details;

    private String ipAddress;
    private String userAgent;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
