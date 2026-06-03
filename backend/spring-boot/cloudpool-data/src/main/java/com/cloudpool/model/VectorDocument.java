package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vector_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class VectorDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private VectorCollection collection;

    @Column(nullable = false)
    private String docId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "BYTEA")
    private byte[] embeddingVector; // Serialized float array for local fallback

    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON string representation

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
