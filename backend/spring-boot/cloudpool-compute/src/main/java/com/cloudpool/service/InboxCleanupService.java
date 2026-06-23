package com.cloudpool.service;

import com.cloudpool.repository.InboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboxCleanupService {

    private final InboxEventRepository inboxEventRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeOldInboxEvents() {
        Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(30));
        int deleted = inboxEventRepository.deleteProcessedBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} inbox events processed before {}", deleted, cutoff);
        }
    }
}