package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "received_emails")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String fromAddress;

    @Column(nullable = false)
    private String toAddress;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();
}
