package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "background_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackgroundJob {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String jobType;

    @Column(nullable = false)
    private String status; // PENDING, RUNNING, COMPLETED, FAILED

    @Column(columnDefinition = "TEXT")
    private String payload; // serialized JSON payload

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
