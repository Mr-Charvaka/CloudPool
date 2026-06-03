package com.cloudpool.controller;

import com.cloudpool.model.*;
import com.cloudpool.repository.ApiKeyRepository;
import com.cloudpool.repository.BucketRepository;
import com.cloudpool.repository.VectorCollectionRepository;
import com.cloudpool.service.DatabaseService;
import com.cloudpool.service.DatabaseService.FieldRequest;
import com.cloudpool.service.StorageService;
import com.cloudpool.service.VectorService;
import com.cloudpool.service.GraphQLSubscriptionService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GraphQLController {

    private final StorageService storageService;
    private final DatabaseService databaseService;
    private final VectorService vectorService;
    private final BucketRepository bucketRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final VectorCollectionRepository collectionRepository;
    private final GraphQLSubscriptionService subscriptionService;

    private User getAuthenticatedUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new SecurityException("Unauthorized: No authenticated developer found in GraphQL context");
        }
        return (User) auth.getPrincipal();
    }

    // ── QUERIES ──

    @QueryMapping
    public User me() {
        return getAuthenticatedUser();
    }

    @QueryMapping
    public List<Bucket> buckets() {
        User user = getAuthenticatedUser();
        return storageService.listUserBuckets(user);
    }

    @QueryMapping
    public List<FileMetadata> files(@Argument Integer page, @Argument Integer size) {
        User user = getAuthenticatedUser();
        List<FileMetadata> allFiles = storageService.listUserFiles(user);
        if (page == null || size == null || page < 0 || size <= 0) {
            return allFiles;
        }
        int fromIndex = page * size;
        if (fromIndex >= allFiles.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + size, allFiles.size());
        return allFiles.subList(fromIndex, toIndex);
    }

    @QueryMapping
    public List<DevTable> tables() {
        User user = getAuthenticatedUser();
        return databaseService.listTables(user.getId(), null);
    }

    @QueryMapping
    public List<RecordMap> records(@Argument UUID tableId) {
        User user = getAuthenticatedUser();
        List<Map<String, Object>> rows = databaseService.queryRecords(tableId, user.getId());
        return rows.stream().map(this::convertToRecordMap).collect(Collectors.toList());
    }

    @QueryMapping
    public List<VectorCollection> collections() {
        User user = getAuthenticatedUser();
        return collectionRepository.findByUserId(user.getId());
    }

    @QueryMapping
    public List<SearchResult> semanticSearch(
            @Argument UUID collectionId,
            @Argument String query,
            @Argument Integer limit) {
        User user = getAuthenticatedUser();
        int maxLimit = limit != null ? limit : 10;
        List<Map<String, Object>> searchResults = vectorService.searchCollection(collectionId, query, maxLimit, user.getId());
        
        return searchResults.stream().map(m -> new SearchResult(
                String.valueOf(m.get("docId")),
                String.valueOf(m.get("content")),
                (Double) m.get("score")
        )).collect(Collectors.toList());
    }

    @QueryMapping
    public String healthCheck() {
        return "OK";
    }

    // ── MUTATIONS ──

    @MutationMapping
    public Bucket createBucket(@Argument String name, @Argument String description) {
        User user = getAuthenticatedUser();
        Bucket bucket = Bucket.builder()
                .user(user)
                .name(name)
                .description(description)
                .isPublic(false)
                .build();
        return bucketRepository.save(bucket);
    }

    @MutationMapping
    public DevTable createTable(
            @Argument String name,
            @Argument String displayName,
            @Argument String description,
            @Argument List<FieldInput> fields) {
        User user = getAuthenticatedUser();
        List<FieldRequest> fieldRequests = fields.stream()
                .map(f -> new FieldRequest(f.getFieldName(), f.getFieldType(), f.isRequired()))
                .collect(Collectors.toList());
        return databaseService.createTable(user.getId(), null, name, displayName, description, fieldRequests);
    }

    @MutationMapping
    public RecordMap insertRecord(@Argument UUID tableId, @Argument List<KeyValueInput> data) {
        User user = getAuthenticatedUser();
        Map<String, Object> dataMap = new HashMap<>();
        for (KeyValueInput kv : data) {
            dataMap.put(kv.getKey(), kv.getValue());
        }
        Map<String, Object> resultRow = databaseService.insertRecord(tableId, dataMap, user.getId());
        return convertToRecordMap(resultRow);
    }

    @MutationMapping
    public VectorCollection createCollection(
            @Argument String name,
            @Argument String description,
            @Argument int dimension,
            @Argument String distanceMetric) {
        User user = getAuthenticatedUser();
        String metric = distanceMetric != null ? distanceMetric : "cosine";
        return vectorService.createCollection(user, name, description, dimension, metric);
    }

    @MutationMapping
    public VectorDocument indexDocument(
            @Argument UUID collectionId,
            @Argument String docId,
            @Argument String content,
            @Argument List<KeyValueInput> metadata) {
        User user = getAuthenticatedUser();
        Map<String, Object> metaMap = new HashMap<>();
        if (metadata != null) {
            for (KeyValueInput kv : metadata) {
                metaMap.put(kv.getKey(), kv.getValue());
            }
        }
        return vectorService.indexDocument(collectionId, docId, content, metaMap, user.getId());
    }

    @MutationMapping
    public ApiKeyResponse generateApiKey(
            @Argument String name,
            @Argument String description,
            @Argument int daysToLive) {
        User user = getAuthenticatedUser();
        String plainKey = "cp_live_" + generateRandomString(32);
        String hashedKey = hashApiKey(plainKey);

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .name(name)
                .description(description)
                .keyHash(hashedKey)
                .active(true)
                .createdAt(LocalDateTime.now())
                .expiresAt(daysToLive > 0 ? LocalDateTime.now().plusDays(daysToLive) : null)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        return new ApiKeyResponse(saved.getId(), saved.getName(), plainKey, saved.getCreatedAt().toString());
    }

    // ── HELPERS & DTOs ──

    private RecordMap convertToRecordMap(Map<String, Object> map) {
        String id = String.valueOf(map.getOrDefault("id", ""));
        List<KeyValueResult> fields = new ArrayList<>();
        map.forEach((k, v) -> {
            fields.add(new KeyValueResult(k, v != null ? String.valueOf(v) : null));
        });
        return new RecordMap(id, fields);
    }

    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String hashApiKey(String apiKeyRaw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKeyRaw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldInput {
        private String fieldName;
        private String fieldType;
        private boolean isRequired;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyValueInput {
        private String key;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordMap {
        private String id;
        private List<KeyValueResult> fields;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyValueResult {
        private String key;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResult {
        private String docId;
        private String content;
        private double score;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiKeyResponse {
        private UUID id;
        private String name;
        private String apiKey;
        private String createdAt;
    }

    @SubscriptionMapping
    public Publisher<BackgroundJob> jobStatusUpdates() {
        return subscriptionService.getJobUpdatesFlux();
    }
}
