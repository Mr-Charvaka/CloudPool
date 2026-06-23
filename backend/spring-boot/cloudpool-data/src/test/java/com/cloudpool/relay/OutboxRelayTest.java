package com.cloudpool.relay;

import com.cloudpool.event.OutboxEvent;
import com.cloudpool.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock private OutboxEventRepository outboxRepo;
    @Mock private OutboxEventProcessor processor;

    @InjectMocks
    private OutboxRelay relay;

    @Test
    @DisplayName("Should fetch pending events and delegate each to processor")
    void testRelayEventsProcessesPendingEvents() {
        OutboxEvent event1 = createPendingEvent();
        OutboxEvent event2 = createPendingEvent();
        when(outboxRepo.findPendingEventsForProcessing(50)).thenReturn(List.of(event1, event2));

        relay.relayEvents();

        verify(processor).processEvent(event1);
        verify(processor).processEvent(event2);
    }

    @Test
    @DisplayName("Should continue processing remaining events when one fails")
    void testRelayEventsHandlesPartialFailure() {
        OutboxEvent event1 = createPendingEvent();
        OutboxEvent event2 = createPendingEvent();
        OutboxEvent event3 = createPendingEvent();
        when(outboxRepo.findPendingEventsForProcessing(50)).thenReturn(List.of(event1, event2, event3));
        doThrow(new RuntimeException("Processing failed")).when(processor).processEvent(event1);

        relay.relayEvents();

        verify(processor).processEvent(event1);
        verify(processor).processEvent(event2);
        verify(processor).processEvent(event3);
    }

    @Test
    @DisplayName("Should do nothing when no pending events")
    void testRelayEventsNoEvents() {
        when(outboxRepo.findPendingEventsForProcessing(50)).thenReturn(List.of());

        relay.relayEvents();

        verify(processor, never()).processEvent(any());
    }

    @Test
    @DisplayName("Should handle all events failing without throwing")
    void testRelayEventsAllFail() {
        OutboxEvent event1 = createPendingEvent();
        OutboxEvent event2 = createPendingEvent();
        when(outboxRepo.findPendingEventsForProcessing(50)).thenReturn(List.of(event1, event2));
        doThrow(new RuntimeException("Fail")).when(processor).processEvent(any());

        relay.relayEvents();

        verify(processor, times(2)).processEvent(any());
    }

    private OutboxEvent createPendingEvent() {
        return OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("test")
                .aggregateId("agg-" + UUID.randomUUID())
                .eventType("TestEvent")
                .payload("{}")
                .createdAt(Instant.now())
                .status(OutboxEvent.OutboxStatus.PENDING)
                .attemptCount(0)
                .build();
    }
}