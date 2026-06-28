package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_emails")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String toAddress;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.cloudpool.model.enums.EmailStatus status; // SENT, FAILED, QUEUED

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
