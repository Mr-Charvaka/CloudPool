package com.cloudpool.relay;

import com.cloudpool.event.BaseEvent;
import com.cloudpool.event.DeploymentRequestedEvent;
import com.cloudpool.event.OutboxEvent;
import com.cloudpool.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorTest {

    @Mock private OutboxEventRepository outboxRepo;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventProcessor processor;

    private OutboxEvent pendingEvent;
    private OutboxEvent deploymentEvent;

    @BeforeEach
    void setUp() {
        pendingEvent = OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("deployment")
                .aggregateId("agg-123")
                .eventType("DeploymentRequestedEvent")
                .payload("{\"deploymentId\":\"dep-123\"}")
                .createdAt(Instant.now())
                .status(OutboxEvent.OutboxStatus.PENDING)
                .attemptCount(0)
                .build();

        deploymentEvent = OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("deployment")
                .aggregateId("agg-456")
                .eventType("DeploymentSuccessEvent")
                .payload("{\"deploymentId\":\"dep-456\"}")
                .createdAt(Instant.now())
                .status(OutboxEvent.OutboxStatus.PENDING)
                .attemptCount(0)
                .build();
    }

    @Test
    @DisplayName("Should mark event PROCESSING, publish to RabbitMQ, then mark PUBLISHED")
    void testProcessEventSuccess() throws Exception {
        DeploymentRequestedEvent deserialized = new DeploymentRequestedEvent();
        when(outboxRepo.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(objectMapper.readValue(pendingEvent.getPayload(), DeploymentRequestedEvent.class))
                .thenReturn(deserialized);

        processor.processEvent(pendingEvent);

        verify(outboxRepo, times(2)).save(any(OutboxEvent.class));
        verify(rabbitTemplate).convertAndSend(eq("cloudpool.exchange"), eq("deployment.requested"), eq(deserialized));
        assertEquals(OutboxEvent.OutboxStatus.PUBLISHED, pendingEvent.getStatus());
    }

    @Test
    @DisplayName("Should increment attempt count on failure and set status to FAILED")
    void testProcessEventFailureIncrementsAttempt() {
        when(outboxRepo.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(objectMapper.readValue(anyString(), eq(DeploymentRequestedEvent.class)))
                .thenThrow(new RuntimeException("Deserialization failed"));

        processor.processEvent(pendingEvent);

        assertEquals(1, pendingEvent.getAttemptCount());
        assertEquals(OutboxEvent.OutboxStatus.FAILED, pendingEvent.getStatus());
    }

    @Test
    @DisplayName("Should dead-letter event after max attempts")
    void testProcessEventDeadLetterAfterMaxAttempts() {
        pendingEvent.setAttemptCount(4);
        when(outboxRepo.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(objectMapper.readValue(anyString(), eq(DeploymentRequestedEvent.class)))
                .thenThrow(new RuntimeException("Persistent failure"));

        processor.processEvent(pendingEvent);

        assertEquals(5, pendingEvent.getAttemptCount());
        assertEquals(OutboxEvent.OutboxStatus.DEAD_LETTER, pendingEvent.getStatus());
    }

    @Test
    @DisplayName("Should still save event to DB when RabbitMQ publish fails")
    void testProcessEventPublishFailure() throws Exception {
        DeploymentRequestedEvent deserialized = new DeploymentRequestedEvent();
        when(outboxRepo.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(objectMapper.readValue(pendingEvent.getPayload(), DeploymentRequestedEvent.class))
                .thenReturn(deserialized);
        doThrow(new RuntimeException("RabbitMQ connection lost"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any());

        processor.processEvent(pendingEvent);

        assertEquals(1, pendingEvent.getAttemptCount());
        assertEquals(OutboxEvent.OutboxStatus.FAILED, pendingEvent.getStatus());
        verify(outboxRepo, times(2)).save(pendingEvent);
    }

    @Test
    @DisplayName("Should route DeploymentSuccessEvent to deployment.success routing key")
    void testResolveRoutingKeySuccess() throws Exception {
        Object deserialized = new Object();
        when(outboxRepo.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(objectMapper.readValue(deploymentEvent.getPayload(), BaseEvent.class)).thenReturn(deserialized);

        processor.processEvent(deploymentEvent);

        verify(rabbitTemplate).convertAndSend(eq("cloudpool.exchange"), eq("deployment.success"), eq(deserialized));
    }

    @Test
    @DisplayName("Should route unknown event type to saga.reply routing key")
    void testResolveRoutingKeyUnknown() throws Exception {
        OutboxEvent unknownEvent = OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("unknown")
                .eventType("UnknownEventType")
                .payload("{}")
                .createdAt(Instant.now())
                .status(OutboxEvent.OutboxStatus.PENDING)
                .attemptCount(0)
                .build();
        Object deserialized = new Object();
        when(outboxRepo.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(objectMapper.readValue("{}", BaseEvent.class)).thenReturn(deserialized);

        processor.processEvent(unknownEvent);

        verify(rabbitTemplate).convertAndSend(eq("cloudpool.exchange"), eq("saga.reply"), eq(deserialized));
    }
}