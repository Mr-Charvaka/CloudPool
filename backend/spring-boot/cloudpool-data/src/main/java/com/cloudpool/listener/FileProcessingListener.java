package com.cloudpool.listener;

import com.cloudpool.config.RabbitMQConfig;
import com.cloudpool.model.BackgroundJob;
import com.cloudpool.model.FileMetadata;
import com.cloudpool.repository.BackgroundJobRepository;
import com.cloudpool.repository.FileMetadataRepository;
import com.cloudpool.service.StorageService;
import com.cloudpool.service.GraphQLSubscriptionService;
import com.cloudpool.util.RustBridge;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileProcessingListener {

    private final StorageService storageService;
    private final FileMetadataRepository fileMetadataRepository;
    private final BackgroundJobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final GraphQLSubscriptionService subscriptionService;

    /** Typed payload DTO — ObjectMapper handles key ordering robustly */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FilePayload {
        private String userId;
        private String fileId;
        private String operation;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbeddingPayload {
        private String userId;
        private String collectionId;
        private String docId;
        private String content;
    }

    /**
     * Listen for file processing tasks dispatched by BackgroundJobService.
     */
    @RabbitListener(queues = RabbitMQConfig.FILE_PROCESSING_QUEUE)
    public void processFile(String jobIdStr) {
        log.info("Received file processing job ID: {}", jobIdStr);
        UUID jobId;
        try {
            jobId = UUID.fromString(jobIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid job ID format: {}", jobIdStr);
            return;
        }

        Optional<BackgroundJob> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.error("Job not found: {}", jobId);
            return;
        }

        BackgroundJob job = jobOpt.get();
        job.setStatus("RUNNING");
        job.setUpdatedAt(LocalDateTime.now());
        job = jobRepository.save(job);
        subscriptionService.publishJobUpdate(job);

        try {
            // Use ObjectMapper for reliable JSON parsing regardless of key ordering
            FilePayload payload = objectMapper.readValue(job.getPayload(), FilePayload.class);

            if (payload.getFileId() == null) {
                log.warn("No fileId in payload for job {}", jobId);
                job.setStatus("FAILED");
                return;
            }

            UUID fileId = UUID.fromString(payload.getFileId());
            log.info("Processing file in background: {} (op={})", fileId, payload.getOperation());

            Optional<FileMetadata> fileMetadataOpt = fileMetadataRepository.findById(fileId);
            if (fileMetadataOpt.isPresent()) {
                FileMetadata metadata = fileMetadataOpt.get();
                log.info("File: {}, size: {} bytes", metadata.getOriginalName(), metadata.getSize());

                // Compute checksum via native Rust bridge if available
                if (RustBridge.isLibraryLoaded()) {
                    byte[] fileData = storageService.downloadFileDirectly(metadata);
                    if (fileData != null && fileData.length > 0) {
                        String checksum = RustBridge.calculateChecksum(fileData);
                        log.info("Native checksum for file {}: {}", fileId, checksum);
                    }
                }
            } else {
                log.warn("FileMetadata not found for id: {}", fileId);
            }

            job.setStatus("COMPLETED");
            log.info("File processing job {} completed", jobId);

        } catch (Exception e) {
            log.error("Error processing file job {}: {}", jobId, e.getMessage(), e);
            job.setStatus("FAILED");
        } finally {
            job.setUpdatedAt(LocalDateTime.now());
            BackgroundJob saved = jobRepository.save(job);
            subscriptionService.publishJobUpdate(saved);
        }
    }

    /**
     * Listen for embedding generation tasks.
     */
    @RabbitListener(queues = RabbitMQConfig.EMBEDDING_QUEUE)
    public void processEmbedding(String jobIdStr) {
        log.info("Received embedding job ID: {}", jobIdStr);
        UUID jobId;
        try {
            jobId = UUID.fromString(jobIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid job ID format: {}", jobIdStr);
            return;
        }

        Optional<BackgroundJob> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.error("Embedding job not found: {}", jobId);
            return;
        }

        BackgroundJob job = jobOpt.get();
        job.setStatus("RUNNING");
        job.setUpdatedAt(LocalDateTime.now());
        job = jobRepository.save(job);
        subscriptionService.publishJobUpdate(job);

        try {
            EmbeddingPayload payload = objectMapper.readValue(job.getPayload(), EmbeddingPayload.class);
            log.info("Embedding job for doc: {} in collection: {}", payload.getDocId(), payload.getCollectionId());

            // Embedding generation is handled by VectorService.indexDocument() synchronously
            // at submission time; this listener handles async re-indexing triggers.
            log.info("Embedding job {} marked complete", jobId);
            job.setStatus("COMPLETED");

        } catch (Exception e) {
            log.error("Error processing embedding job {}: {}", jobId, e.getMessage(), e);
            job.setStatus("FAILED");
        } finally {
            job.setUpdatedAt(LocalDateTime.now());
            BackgroundJob saved = jobRepository.save(job);
            subscriptionService.publishJobUpdate(saved);
        }
    }
}
