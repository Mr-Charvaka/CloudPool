package com.cloudpool.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new CacheService(Optional.of(redisTemplate));
    }

    @Test
    void testCacheFile() {
        String id = "file-123";
        String data = "file-data";

        Object result = cacheService.cacheFile(id, data);

        assertEquals(data, result);
        verify(valueOperations).set("file:" + id, data, 1, TimeUnit.HOURS);
    }

    @Test
    void testCacheFileNoRedis() {
        CacheService noRedis = new CacheService(Optional.empty());

        Object result = noRedis.cacheFile("id", "data");

        assertEquals("data", result);
    }

    @Test
    void testGetCachedFile() {
        String id = "file-456";
        when(valueOperations.get("file:" + id)).thenReturn("cached-data");

        Object result = cacheService.getCachedFile(id);

        assertEquals("cached-data", result);
    }

    @Test
    void testGetCachedFileNoRedis() {
        CacheService noRedis = new CacheService(Optional.empty());

        Object result = noRedis.getCachedFile("id");

        assertNull(result);
    }

    @Test
    void testInvalidateFile() {
        String id = "file-789";

        cacheService.invalidateFile(id);

        verify(redisTemplate).delete("file:" + id);
    }

    @Test
    void testBlacklistToken() {
        String token = "jwt-token-123";

        cacheService.blacklistToken(token, 3600000L);

        verify(valueOperations).set("blacklist:" + token, "revoked", 3600000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void testIsTokenBlacklisted() {
        String token = "blacklisted-token";
        when(redisTemplate.hasKey("blacklist:" + token)).thenReturn(true);

        boolean result = cacheService.isTokenBlacklisted(token);

        assertTrue(result);
    }

    @Test
    void testIsTokenNotBlacklisted() {
        String token = "valid-token";
        when(redisTemplate.hasKey("blacklist:" + token)).thenReturn(false);

        boolean result = cacheService.isTokenBlacklisted(token);

        assertFalse(result);
    }

    @Test
    void testCacheTable() {
        String id = "table-abc";
        String data = "table-data";

        cacheService.cacheTable(id, data);

        verify(valueOperations).set("table:" + id, data, 1, TimeUnit.HOURS);
    }

    @Test
    void testCacheCollection() {
        String id = "col-xyz";
        String data = "collection-data";

        cacheService.cacheCollection(id, data);

        verify(valueOperations).set("collection:" + id, data, 1, TimeUnit.HOURS);
    }

    @Test
    void testTokenBlacklistWithoutRedis() {
        CacheService noRedis = new CacheService(Optional.empty());

        noRedis.blacklistToken("token", 1000);
        assertFalse(noRedis.isTokenBlacklisted("token"));
    }
}
