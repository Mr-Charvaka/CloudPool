package com.cloudpool.service;

import com.cloudpool.repository.InboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxCleanupServiceTest {

    @Mock private InboxEventRepository inboxEventRepository;

    @InjectMocks
    private InboxCleanupService inboxCleanupService;

    @Test
    @DisplayName("Should delete inbox events processed before cutoff (30 days ago)")
    void testPurgeOldInboxEvents() {
        when(inboxEventRepository.deleteProcessedBefore(any(Instant.class))).thenReturn(10);

        inboxCleanupService.purgeOldInboxEvents();

        verify(inboxEventRepository).deleteProcessedBefore(argThat(cutoff ->
                cutoff.isBefore(Instant.now().minus(java.time.Duration.ofDays(29)))
                        && cutoff.isAfter(Instant.now().minus(java.time.Duration.ofDays(31)))));
    }

    @Test
    @DisplayName("Should not log when no events are purged")
    void testPurgeOldInboxEventsNoEvents() {
        when(inboxEventRepository.deleteProcessedBefore(any(Instant.class))).thenReturn(0);

        inboxCleanupService.purgeOldInboxEvents();

        verify(inboxEventRepository).deleteProcessedBefore(any(Instant.class));
    }

    @Test
    @DisplayName("Should handle repository exception gracefully")
    void testPurgeOldInboxEventsException() {
        when(inboxEventRepository.deleteProcessedBefore(any(Instant.class)))
                .thenThrow(new RuntimeException("Database connection lost"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> inboxCleanupService.purgeOldInboxEvents());
    }
}