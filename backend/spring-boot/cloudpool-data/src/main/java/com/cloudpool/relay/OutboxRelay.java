package com.cloudpool.relay;

import com.cloudpool.event.OutboxEvent;
import com.cloudpool.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository outboxRepo;
    private final OutboxEventProcessor processor;

    @Scheduled(fixedDelay = 2000)
    @Transactional(readOnly = true)
    public void relayEvents() {
        List<OutboxEvent> events = outboxRepo.findPendingEventsForProcessing(50);

        for (OutboxEvent event : events) {
            try {
                processor.processEvent(event);
            } catch (Exception e) {
                log.warn("Relay [{}]: Event {} failed (attempt {}/{}): {}",
                        event.getAggregateType(), event.getEventId(),
                        event.getAttemptCount(), MAX_ATTEMPTS, e.getMessage());
            }
        }
    }
}
