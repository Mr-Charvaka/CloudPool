package com.cloudpool.relay;

import com.cloudpool.event.BaseEvent;
import com.cloudpool.event.DeploymentRequestedEvent;
import com.cloudpool.event.OutboxEvent;
import com.cloudpool.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private static final String EXCHANGE = "cloudpool.exchange";
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository outboxRepo;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(OutboxEvent event) {
        try {
            event.setStatus(OutboxEvent.OutboxStatus.PROCESSING);
            outboxRepo.save(event);

            String routingKey = resolveRoutingKey(event.getEventType());
            Object payload = deserializePayload(event.getEventType(), event.getPayload());

            rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);

            event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
            log.info("Relay [{}]: Published event {} to exchange {} routing {}",
                    event.getAggregateType(), event.getEventId(), EXCHANGE, routingKey);
        } catch (Exception e) {
            event.setAttemptCount(event.getAttemptCount() + 1);
            event.setStatus(event.getAttemptCount() >= MAX_ATTEMPTS
                ? OutboxEvent.OutboxStatus.DEAD_LETTER
                : OutboxEvent.OutboxStatus.FAILED);

            if (event.getStatus() == OutboxEvent.OutboxStatus.DEAD_LETTER) {
                log.error("Relay [{}]: Dead-lettered event {} after {} attempts",
                        event.getAggregateType(), event.getEventId(), MAX_ATTEMPTS);
            }
        }
        outboxRepo.save(event);
    }

    private String resolveRoutingKey(String eventType) {
        return switch (eventType) {
            case "DeploymentRequestedEvent" -> "deployment.requested";
            case "DeploymentSuccessEvent" -> "deployment.success";
            case "DeploymentFailedEvent" -> "deployment.failed";
            default -> "saga.reply";
        };
    }

    private Object deserializePayload(String eventType, String payload) throws Exception {
        return switch (eventType) {
            case "DeploymentRequestedEvent" ->
                objectMapper.readValue(payload, DeploymentRequestedEvent.class);
            default ->
                objectMapper.readValue(payload, BaseEvent.class);
        };
    }
}