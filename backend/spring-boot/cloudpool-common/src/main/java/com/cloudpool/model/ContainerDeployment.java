package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "container_deployments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ContainerDeployment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "docker_image", nullable = false)
    private String dockerImage;

    @Column(nullable = false)
    private double cpu;

    @Column(nullable = false)
    private int memory; // in MB

    @Column(nullable = false)
    private int replicas;

    @Column(nullable = false)
    private com.cloudpool.model.enums.ContainerStatus status; // e.g. com.cloudpool.model.enums.ContainerStatus.BUILDING, com.cloudpool.model.enums.ContainerStatus.DEPLOYING, com.cloudpool.model.enums.ContainerStatus.LIVE, "FAILED"

    @Column(columnDefinition = "TEXT")
    private String logs;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}

