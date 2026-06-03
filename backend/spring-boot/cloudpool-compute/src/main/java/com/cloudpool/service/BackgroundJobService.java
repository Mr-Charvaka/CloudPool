package com.cloudpool.service;

import com.cloudpool.dto.BackgroundJobDTO;
import com.cloudpool.model.BackgroundJob;
import com.cloudpool.repository.BackgroundJobRepository;
import com.cloudpool.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class BackgroundJobService {

    private final BackgroundJobRepository jobRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final GraphQLSubscriptionService subscriptionService;

    public BackgroundJobService(
            BackgroundJobRepository jobRepository,
            Optional<RabbitTemplate> rabbitTemplate,
            ObjectMapper objectMapper,
            GraphQLSubscriptionService subscriptionService) {
        this.jobRepository = jobRepository;
        this.rabbitTemplate = rabbitTemplate.orElse(null);
        this.objectMapper = objectMapper;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Submit file processing job
     */
    public BackgroundJobDTO submitFileProcessingJob(
            String userId,
            String fileId,
            String operation) {
        
        log.info("Submitting file processing job for file: {} operation: {}", fileId, operation);

        String payloadStr = "";
        try {
            payloadStr = objectMapper.writeValueAsString(Map.of(
                "userId", userId,
                "fileId", fileId,
                "operation", operation
            ));
        } catch (Exception e) {
            log.error("Failed to serialize payload", e);
        }

        BackgroundJob job = BackgroundJob.builder()
                .jobType("FILE_PROCESSING")
                .status("PENDING")
                .payload(payloadStr)
                .build();

        BackgroundJob saved = jobRepository.save(job);
        subscriptionService.publishJobUpdate(saved);

        // Send to queue
        if (rabbitTemplate != null) {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                "cloudpool.file.processing",
                saved.getId().toString()
            );
        } else {
            log.warn("RabbitMQ is not configured. Job {} remains PENDING.", saved.getId());
        }

        return BackgroundJobDTO.fromEntity(saved);
    }

    /**
     * Submit embedding generation job
     */
    public BackgroundJobDTO submitEmbeddingJob(
            String userId,
            String collectionId,
            String docId,
            String content) {
        
        log.info("Submitting embedding job for document: {}", docId);

        String payloadStr = "";
        try {
            payloadStr = objectMapper.writeValueAsString(Map.of(
                "userId", userId,
                "collectionId", collectionId,
                "docId", docId,
                "content", content
            ));
        } catch (Exception e) {
            log.error("Failed to serialize payload", e);
        }

        BackgroundJob job = BackgroundJob.builder()
                .jobType("EMBEDDING_GENERATION")
                .status("PENDING")
                .payload(payloadStr)
                .build();

        BackgroundJob saved = jobRepository.save(job);
        subscriptionService.publishJobUpdate(saved);

        // Send to queue
        if (rabbitTemplate != null) {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                "cloudpool.embedding.generate",
                saved.getId().toString()
            );
        } else {
            log.warn("RabbitMQ is not configured. Job {} remains PENDING.", saved.getId());
        }

        return BackgroundJobDTO.fromEntity(saved);
    }
}
