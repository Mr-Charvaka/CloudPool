package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "analytics_api_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsApiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private UUID projectId;

    @Column(nullable = false)
    private String requestPath;

    @Column(nullable = false)
    private String requestMethod;

    @Column(nullable = false)
    private int statusCode;

    @Column(nullable = false)
    private long durationMs;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private String ipAddress;

    private String userAgent;
}
