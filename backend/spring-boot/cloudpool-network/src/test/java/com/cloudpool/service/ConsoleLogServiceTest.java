package com.cloudpool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.cloudpool.dto.ConsoleLogEvent;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class ConsoleLogServiceTest {

    private ConsoleLogService service;

    @AfterEach
    void stop() {
        if (service != null) {
            service.stop();
        }
    }

    private ConsoleLogEvent event(String message) {
        return new ConsoleLogEvent(Instant.now(), "INFO", "com.example.App", "main", message);
    }

    @Test
    void offeredEventsAreBroadcastToTheSystemTopic() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        service = new ConsoleLogService(template, 10);
        service.start();
        ConsoleLogEvent event = event("hello");

        assertTrue(service.offer(event));

        verify(template, timeout(2000)).convertAndSend(ConsoleLogService.TOPIC, event);
    }

    @Test
    void aFullQueueDropsEventsInsteadOfBlockingTheCaller() throws Exception {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            inFlight.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(template).convertAndSend(anyString(), any(Object.class));
        service = new ConsoleLogService(template, 2);
        service.start();

        assertTrue(service.offer(event("taken by the sender thread")));
        assertTrue(inFlight.await(2, TimeUnit.SECONDS));
        assertTrue(service.offer(event("queued 1")));
        assertTrue(service.offer(event("queued 2")));

        assertFalse(service.offer(event("dropped")));
        assertEquals(1, service.getDroppedCount());

        release.countDown();
    }

    @Test
    void aFailingBrokerDoesNotStopTheStream() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        ConsoleLogEvent poison = event("poison");
        ConsoleLogEvent healthy = event("healthy");
        doThrow(new IllegalStateException("broker down")).when(template).convertAndSend(eq(ConsoleLogService.TOPIC), eq(poison));
        service = new ConsoleLogService(template, 10);
        service.start();

        service.offer(poison);
        service.offer(healthy);

        verify(template, timeout(2000)).convertAndSend(ConsoleLogService.TOPIC, healthy);
    }
}
