package com.cloudpool.event;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_events", indexes = {
    @Index(name = "idx_inbox_processed_at", columnList = "processedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxEvent {
    @Id
    private UUID eventId;
    
    private String eventType;
    private Instant processedAt;
}
