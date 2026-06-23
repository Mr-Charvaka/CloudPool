package com.cloudpool.service;

import com.cloudpool.model.FileMetadata;
import com.cloudpool.model.User;
import com.cloudpool.repository.FileMetadataRepository;
import com.cloudpool.repository.VectorCollectionRepository;
import com.cloudpool.repository.VectorDocumentRepository;
import io.weaviate.client.WeaviateClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorServiceTest {

    @Mock private FileMetadataRepository fileMetadataRepository;
    @Mock private VectorCollectionRepository collectionRepository;
    @Mock private VectorDocumentRepository documentRepository;
    @Mock private EmbeddingService embeddingService;
    @Mock private WeaviateClient weaviateClient;

    @InjectMocks
    private VectorService vectorService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder().id(userId).email("test@cloudpool.com").build();
    }

    @Test
    @DisplayName("cosineSimilarity should return 1.0 for identical vectors")
    void testCosineSimilarityIdentical() {
        float[] v1 = {1.0f, 2.0f, 3.0f};
        float[] v2 = {1.0f, 2.0f, 3.0f};
        double similarity = VectorService.cosineSimilarity(v1, v2);
        assertEquals(1.0, similarity, 0.0001);
    }

    @Test
    @DisplayName("cosineSimilarity should return 0.0 for orthogonal vectors")
    void testCosineSimilarityOrthogonal() {
        float[] v1 = {1.0f, 0.0f, 0.0f};
        float[] v2 = {0.0f, 1.0f, 0.0f};
        double similarity = VectorService.cosineSimilarity(v1, v2);
        assertEquals(0.0, similarity, 0.0001);
    }

    @Test
    @DisplayName("cosineSimilarity should return -1.0 for opposite vectors")
    void testCosineSimilarityOpposite() {
        float[] v1 = {1.0f, 2.0f, 3.0f};
        float[] v2 = {-1.0f, -2.0f, -3.0f};
        double similarity = VectorService.cosineSimilarity(v1, v2);
        assertEquals(-1.0, similarity, 0.0001);
    }

    @Test
    @DisplayName("cosineSimilarity should handle null or mismatched lengths gracefully")
    void testCosineSimilarityNullAndMismatch() {
        assertEquals(0.0, VectorService.cosineSimilarity(null, new float[]{1.0f}));
        assertEquals(0.0, VectorService.cosineSimilarity(new float[]{1.0f}, null));
        assertEquals(0.0, VectorService.cosineSimilarity(new float[]{1.0f}, new float[]{1.0f, 2.0f}));
        assertEquals(0.0, VectorService.cosineSimilarity(new float[]{0.0f}, new float[]{0.0f}));
    }

    @Test
    @DisplayName("search should return empty list if query is null or blank")
    void testSearchWithNullOrBlankQuery() {
        List<VectorService.VectorSearchResult> result1 = vectorService.search(null, testUser);
        List<VectorService.VectorSearchResult> result2 = vectorService.search("   ", testUser);
        
        assertTrue(result1.isEmpty());
        assertTrue(result2.isEmpty());
        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("search should return empty list if user has no files")
    void testSearchWithNoFiles() {
        when(fileMetadataRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        
        List<VectorService.VectorSearchResult> result = vectorService.search("test query", testUser);
        
        assertTrue(result.isEmpty());
        verifyNoInteractions(embeddingService);
    }
}
