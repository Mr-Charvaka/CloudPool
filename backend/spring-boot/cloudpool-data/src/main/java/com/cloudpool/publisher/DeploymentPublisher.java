package com.cloudpool.publisher;

import com.cloudpool.event.DeploymentRequestedEvent;
import com.cloudpool.event.OutboxEvent;
import com.cloudpool.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void requestDeployment(DeploymentRequestedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outbox = OutboxEvent.builder()
                    .eventId(UUID.randomUUID())
                    .aggregateType("DeploymentRequestedEvent")
                    .aggregateId(event.getCorrelationId() != null ? event.getCorrelationId().toString() : event.getEventId().toString())
                    .eventType("DeploymentRequestedEvent")
                    .payload(payload)
                    .createdAt(Instant.now())
                    .status(OutboxEvent.OutboxStatus.PENDING)
                    .attemptCount(0)
                    .build();

            outboxEventRepository.save(outbox);
            log.info("Deployment request written to outbox: {} for {}", outbox.getEventId(), event.getName());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize deployment event: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize deployment event", e);
        }
    }
}
