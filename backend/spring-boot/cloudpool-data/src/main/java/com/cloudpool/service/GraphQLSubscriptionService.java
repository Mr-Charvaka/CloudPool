package com.cloudpool.service;

import com.cloudpool.model.BackgroundJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
public class GraphQLSubscriptionService {

    // Multicast sink that distributes updates to all active subscribers
    private final Sinks.Many<BackgroundJob> jobUpdatesSink = Sinks.many().multicast().directBestEffort();

    /**
     * Publish a background job update to the sink.
     */
    public void publishJobUpdate(BackgroundJob job) {
        if (job == null) return;
        log.debug("Publishing background job update to subscriptions: {} status={}", job.getId(), job.getStatus());
        try {
            jobUpdatesSink.emitNext(job, Sinks.EmitFailureHandler.FAIL_FAST);
        } catch (Exception e) {
            log.warn("Failed to emit background job update: {}", e.getMessage());
        }
    }

    /**
     * Get the Flux of background job updates for subscribers.
     */
    public Flux<BackgroundJob> getJobUpdatesFlux() {
        return jobUpdatesSink.asFlux();
    }
}
