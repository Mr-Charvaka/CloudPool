package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_shares")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class FileShare {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private UUID fileId;

    private String sharedWithEmail; // Can be null if it's a general shareable link

    @Column(nullable = false)
    @Builder.Default
    private String permission = "READ"; // READ or WRITE

    @Column(nullable = false, unique = true)
    private String token; // Unique secure token

    private LocalDateTime expiresAt; // Can be null for infinite duration

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
