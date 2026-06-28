package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "serverless_functions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ServerlessFunction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "trigger_route", nullable = false)
    private String triggerRoute;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String code;

    @Builder.Default
    private boolean wasmCompiled = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.cloudpool.model.enums.ServerlessStatus status; // e.g. com.cloudpool.model.enums.BackgroundJobStatus.PENDING, com.cloudpool.model.enums.ServerlessStatus.ACTIVE, "FAILED"

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}

