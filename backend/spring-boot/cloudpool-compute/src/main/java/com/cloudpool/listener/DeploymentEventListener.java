package com.cloudpool.listener;

import com.cloudpool.event.DeploymentRequestedEvent;
import com.cloudpool.event.DeploymentSuccessEvent;
import com.cloudpool.event.DeploymentFailedEvent;
import com.cloudpool.event.InboxEvent;
import com.cloudpool.repository.InboxEventRepository;
import com.cloudpool.service.ComputeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

import java.util.Optional;

@Slf4j
@Component
public class DeploymentEventListener {

    private final ComputeService computeService;
    private final InboxEventRepository inboxRepo;
    private final RabbitTemplate rabbitTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public DeploymentEventListener(ComputeService computeService, InboxEventRepository inboxRepo, Optional<RabbitTemplate> rabbitTemplate) {
        this.computeService = computeService;
        this.inboxRepo = inboxRepo;
        this.rabbitTemplate = rabbitTemplate.orElse(null);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "deployment.queue", durable = "true", arguments = {
                    @Argument(name = "x-queue-type", value = "quorum"),
                    @Argument(name = "x-dead-letter-exchange", value = "cloudpool.dlx"),
                    @Argument(name = "x-dead-letter-routing-key", value = "deployment.dlq")
            }),
            exchange = @Exchange(value = "cloudpool.exchange", type = "topic", durable = "true"),
            key = "deployment.requested"
    ))
    public void handleDeploymentRequested(DeploymentRequestedEvent event) {
        log.info("Compute Node: Received deployment request {} for user {}", event.getEventId(), event.getUserId());
        
        // 1. Inbox Pattern (Idempotency)
        if (inboxRepo.existsById(event.getEventId())) {
            log.warn("Compute Node: Event {} already processed. Ignoring duplicate.", event.getEventId());
            return;
        }

        try {
            var deployment = computeService.deployContainer(
                    event.getUserId(), 
                    event.getName(), 
                    event.getDockerImage(), 
                    event.getCpu(), 
                    event.getMemory(), 
                    event.getReplicas()
            );
            computeService.processContainerDeploymentAsync(deployment);

            // 2. Publish Success
            DeploymentSuccessEvent successEvent = DeploymentSuccessEvent.builder()
                    .eventId(java.util.UUID.randomUUID())
                    .correlationId(event.getCorrelationId())
                    .deploymentId(deployment.getId().toString())
                    .timestamp(Instant.now())
                    .build();
            if (rabbitTemplate != null) {
                rabbitTemplate.convertAndSend("deployment.success.queue", successEvent);
            }

            // 3. Mark processed
            inboxRepo.save(InboxEvent.builder()
                    .eventId(event.getEventId())
                    .eventType("DeploymentRequestedEvent")
                    .processedAt(Instant.now())
                    .build());

        } catch (Exception e) {
            log.error("Compute Node: Failed to process deployment event {}: {}", event.getEventId(), e.getMessage());
            
            // 2. Publish Failure
            DeploymentFailedEvent failedEvent = DeploymentFailedEvent.builder()
                    .eventId(java.util.UUID.randomUUID())
                    .correlationId(event.getCorrelationId())
                    .deploymentId("UNKNOWN")
                    .reason(e.getMessage())
                    .timestamp(Instant.now())
                    .build();
            if (rabbitTemplate != null) {
                rabbitTemplate.convertAndSend("deployment.failed.queue", failedEvent);
            }
            
            // We do NOT save to inbox repo here, so RabbitMQ will retry if it was a transient network error.
            // Or we could save it to prevent retries for fatal business logic errors.
        }
    }
}
