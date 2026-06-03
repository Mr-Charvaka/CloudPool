package com.cloudpool.service;

import com.cloudpool.model.*;
import com.cloudpool.repository.FileMetadataRepository;
import com.cloudpool.repository.VectorCollectionRepository;
import com.cloudpool.repository.VectorDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.weaviate.client.WeaviateClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {

    private final FileMetadataRepository fileMetadataRepository;
    private final VectorCollectionRepository collectionRepository;
    private final VectorDocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final WeaviateClient weaviateClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache file embeddings to avoid calling OpenAI on every search
    private final Map<UUID, float[]> fileEmbeddingCache = new ConcurrentHashMap<>();

    /**
     * Search across uploaded files semantically
     */
    public List<VectorSearchResult> search(String query, User user) {
        List<FileMetadata> files = fileMetadataRepository.findByUserId(user.getId());
        List<VectorSearchResult> results = new ArrayList<>();

        if (query == null || query.trim().isEmpty() || files.isEmpty()) {
            return results;
        }

        log.info("Performing real-time semantic search for query: '{}' over {} files", query, files.size());
        float[] queryEmbedding = embeddingService.generateEmbedding(query);

        for (FileMetadata file : files) {
            float[] fileEmbedding = getOrGenerateFileEmbedding(file);
            double similarity = cosineSimilarity(queryEmbedding, fileEmbedding);
            
            // Apply a minor boost if the filename itself contains query terms
            double nameBoost = 0.0;
            String nameLower = file.getOriginalName().toLowerCase();
            for (String term : query.toLowerCase().split("\\s+")) {
                if (nameLower.contains(term)) {
                    nameBoost += 0.05;
                }
            }

            double score = similarity + nameBoost;
            if (score > 0.3) { // Similarity threshold
                results.add(new VectorSearchResult(file, score));
            }
        }

        results.sort(Comparator.comparingDouble(VectorSearchResult::getScore).reversed());
        return results;
    }

    private float[] getOrGenerateFileEmbedding(FileMetadata file) {
        return fileEmbeddingCache.computeIfAbsent(file.getId(), id -> {
            String contentToEmbed = file.getOriginalName();
            String ext = file.getExtension() != null ? file.getExtension().toLowerCase() : "";
            
            // Read content if text file
            if ("txt".equalsIgnoreCase(ext) && file.getDriveLocation() != null) {
                try {
                    Path path = Paths.get(file.getDriveLocation());
                    if (Files.exists(path)) {
                        String text = new String(Files.readAllBytes(path));
                        // Take first 500 characters of file
                        if (text.length() > 500) {
                            text = text.substring(0, 500);
                        }
                        contentToEmbed += " " + text;
                    }
                } catch (IOException e) {
                    log.warn("Could not read text content for embedding: {}", e.getMessage());
                }
            }

            return embeddingService.generateEmbedding(contentToEmbed);
        });
    }

    // ── CUSTOM DEVELOPER VECTOR COLLECTIONS CRUD ──

    @Transactional
    public VectorCollection createCollection(User user, String name, String description, int dimension, String distanceMetric) {
        // Try creating class in Weaviate
        String className = sanitizeClassName(name);
        try {
            var vectorClass = io.weaviate.client.v1.schema.model.WeaviateClass.builder()
                    .className(className)
                    .description(description)
                    .vectorizer("none")
                    .build();

            var res = weaviateClient.schema().classCreator()
                    .withClass(vectorClass)
                    .run();
            if (res.hasErrors()) {
                log.warn("Weaviate schema class creation reported errors: {}", res.getError().getMessages());
            }
        } catch (Exception e) {
            log.warn("Weaviate class creation failed: {}. Continuing local JPA creation.", e.getMessage());
        }

        VectorCollection collection = VectorCollection.builder()
                .user(user)
                .name(name)
                .description(description)
                .dimension(dimension)
                .distanceMetric(distanceMetric)
                .build();

        return collectionRepository.save(collection);
    }

    @Transactional
    public void deleteCollection(UUID collectionId, UUID userId) {
        VectorCollection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new NoSuchElementException("Collection not found"));
        if (!collection.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to collection");
        }

        // Delete from Weaviate
        try {
            weaviateClient.schema().classDeleter()
                    .withClassName(sanitizeClassName(collection.getName()))
                    .run();
        } catch (Exception e) {
            log.warn("Weaviate schema class deletion failed: {}", e.getMessage());
        }

        documentRepository.deleteByCollectionId(collectionId);
        collectionRepository.delete(collection);
    }

    @Transactional
    public VectorDocument indexDocument(UUID collectionId, String docId, String content, Map<String, Object> metadataMap, UUID userId) {
        VectorCollection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new NoSuchElementException("Collection not found"));
        if (!collection.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to collection");
        }

        float[] embedding = embeddingService.generateEmbedding(content);

        // Try indexing in Weaviate
        String className = sanitizeClassName(collection.getName());
        try {
            Map<String, Object> objectProperties = new HashMap<>();
            objectProperties.put("docId", docId);
            objectProperties.put("content", content);
            if (metadataMap != null) {
                objectProperties.put("metadata", metadataMap);
            }

            Float[] wrapperEmbedding = new Float[embedding.length];
            for (int i = 0; i < embedding.length; i++) {
                wrapperEmbedding[i] = embedding[i];
            }

            var res = weaviateClient.data().creator()
                    .withClassName(className)
                    .withProperties(objectProperties)
                    .withVector(wrapperEmbedding)
                    .run();
            if (res.hasErrors()) {
                log.warn("Weaviate indexing reported errors: {}", res.getError().getMessages());
            }
        } catch (Exception e) {
            log.warn("Weaviate indexing failed: {}", e.getMessage());
        }

        String metadataJson = null;
        if (metadataMap != null) {
            try {
                metadataJson = objectMapper.writeValueAsString(metadataMap);
            } catch (Exception e) {
                log.warn("Failed to serialize metadata to JSON: {}", e.getMessage());
            }
        }

        // Delete existing doc in collection if duplicate
        documentRepository.findByCollectionIdAndDocId(collectionId, docId)
                .ifPresent(documentRepository::delete);

        VectorDocument doc = VectorDocument.builder()
                .collection(collection)
                .docId(docId)
                .content(content)
                .embeddingVector(floatArrayToByteArray(embedding))
                .metadata(metadataJson)
                .build();

        return documentRepository.save(doc);
    }

    public List<Map<String, Object>> searchCollection(UUID collectionId, String query, int limit, UUID userId) {
        VectorCollection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new NoSuchElementException("Collection not found"));
        if (!collection.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to collection");
        }

        float[] queryEmbedding = embeddingService.generateEmbedding(query);

        // ── Primary: Weaviate ANN search (O(log N)) ────────────────────────
        try {
            String className = sanitizeClassName(collection.getName());
            Float[] wrapperEmbedding = new Float[queryEmbedding.length];
            for (int i = 0; i < queryEmbedding.length; i++) wrapperEmbedding[i] = queryEmbedding[i];

            var gqlResult = weaviateClient.graphQL().get()
                    .withClassName(className)
                    .withFields(
                        io.weaviate.client.v1.graphql.query.fields.Field.builder().name("docId").build(),
                        io.weaviate.client.v1.graphql.query.fields.Field.builder().name("content").build(),
                        io.weaviate.client.v1.graphql.query.fields.Field.builder().name("metadata").build(),
                        io.weaviate.client.v1.graphql.query.fields.Field.builder()
                            .name("_additional")
                            .fields(
                                io.weaviate.client.v1.graphql.query.fields.Field.builder().name("certainty").build()
                            ).build()
                    )
                    .withNearVector(
                        io.weaviate.client.v1.graphql.query.argument.NearVectorArgument.builder()
                            .vector(wrapperEmbedding)
                            .certainty(0.6f)
                            .build()
                    )
                    .withLimit(limit)
                    .run();

            if (!gqlResult.hasErrors() && gqlResult.getResult() != null && gqlResult.getResult().getData() != null) {
                @SuppressWarnings("unchecked")
                var dataMap = (java.util.LinkedHashMap<String, Object>) gqlResult.getResult().getData();
                @SuppressWarnings("unchecked")
                var getMap = (java.util.LinkedHashMap<String, Object>) dataMap.get("Get");
                if (getMap != null) {
                    @SuppressWarnings("unchecked")
                    var objects = (java.util.List<java.util.LinkedHashMap<String, Object>>) getMap.get(className);
                    if (objects != null && !objects.isEmpty()) {
                        List<Map<String, Object>> weaviateResults = new ArrayList<>();
                        for (var obj : objects) {
                            Map<String, Object> res = new HashMap<>();
                            res.put("docId", obj.get("docId"));
                            res.put("content", obj.get("content"));
                            @SuppressWarnings("unchecked")
                            var additional = (java.util.LinkedHashMap<String, Object>) obj.get("_additional");
                            if (additional != null) {
                                Object certainty = additional.get("certainty");
                                res.put("score", certainty != null
                                    ? Math.round(((Number) certainty).doubleValue() * 100.0) / 100.0 : 0.0);
                            }
                            Object metaRaw = obj.get("metadata");
                            if (metaRaw instanceof String metaStr && !metaStr.isBlank()) {
                                try { res.put("metadata", objectMapper.readValue(metaStr, Map.class)); }
                                catch (Exception ignored) {}
                            }
                            weaviateResults.add(res);
                        }
                        log.info("Weaviate ANN search returned {} results for collection {}",
                            weaviateResults.size(), collectionId);
                        return weaviateResults;
                    }
                }
            }
            if (gqlResult.hasErrors()) {
                log.warn("Weaviate errors: {}. Falling back to JPA cosine search.",
                    gqlResult.getError().getMessages());
            }
        } catch (Exception e) {
            log.warn("Weaviate unavailable ({}). Falling back to JPA cosine search.", e.getMessage());
        }

        // ── Fallback: JPA cosine similarity (O(N)) ─────────────────────────
        log.info("JPA cosine fallback for collection {}", collectionId);
        List<VectorDocument> documents = documentRepository.findByCollectionId(collectionId);
        List<Map<String, Object>> results = new ArrayList<>();

        for (VectorDocument doc : documents) {
            float[] docEmbedding = byteArrayToFloatArray(doc.getEmbeddingVector());
            if (docEmbedding == null) continue;
            double score = cosineSimilarity(queryEmbedding, docEmbedding);

            Map<String, Object> res = new HashMap<>();
            res.put("docId", doc.getDocId());
            res.put("content", doc.getContent());
            res.put("score", Math.round(score * 100.0) / 100.0);
            try {
                if (doc.getMetadata() != null) {
                    res.put("metadata", objectMapper.readValue(doc.getMetadata(), Map.class));
                }
            } catch (Exception ignored) {}
            results.add(res);
        }

        results.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));
        return results.size() > limit ? results.subList(0, limit) : results;
    }

    // ── MATH VECTOR UTILITIES ──

    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) return 0.0;
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static byte[] floatArrayToByteArray(float[] floats) {
        if (floats == null) return null;
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    private static float[] byteArrayToFloatArray(byte[] bytes) {
        if (bytes == null) return null;
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    private String sanitizeClassName(String name) {
        // Weaviate class names must start with uppercase letter
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "");
        if (sanitized.isEmpty()) {
            return "CollectionClass";
        }
        return Character.toUpperCase(sanitized.charAt(0)) + (sanitized.length() > 1 ? sanitized.substring(1) : "");
    }

    @Data
    public static class VectorSearchResult {
        private UUID id;
        private String name;
        private String pool;
        private long size;
        private String type;
        private double score;

        public VectorSearchResult(FileMetadata file, double score) {
            this.id = file.getId();
            this.name = file.getOriginalName();
            this.pool = file.getBucket().getName();
            this.size = file.getSize();
            this.type = file.getMimeType();
            this.score = Math.round(score * 100.0) / 100.0;
        }
    }
}
