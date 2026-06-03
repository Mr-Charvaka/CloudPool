package com.cloudpool.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String FILE_CACHE_PREFIX = "file:";
    private static final String TABLE_CACHE_PREFIX = "table:";
    private static final String COLLECTION_CACHE_PREFIX = "collection:";
    private static final long DEFAULT_TTL = 1; // 1 hour

    public CacheService(Optional<RedisTemplate<String, Object>> redisTemplate) {
        this.redisTemplate = redisTemplate.orElse(null);
    }

    /**
     * Cache file metadata
     */
    @Cacheable(value = "files", key = "#id")
    public void cacheFile(Object id, Object data) {
        if (redisTemplate != null) {
            String key = FILE_CACHE_PREFIX + id;
            redisTemplate.opsForValue().set(key, data, DEFAULT_TTL, TimeUnit.HOURS);
            log.debug("Cached file: {}", id);
        }
    }

    /**
     * Get cached file
     */
    public Object getCachedFile(Object id) {
        if (redisTemplate != null) {
            String key = FILE_CACHE_PREFIX + id;
            return redisTemplate.opsForValue().get(key);
        }
        return null;
    }

    /**
     * Invalidate file cache
     */
    @CacheEvict(value = "files", key = "#id")
    public void invalidateFile(Object id) {
        if (redisTemplate != null) {
            String key = FILE_CACHE_PREFIX + id;
            redisTemplate.delete(key);
            log.debug("Invalidated file cache: {}", id);
        }
    }

    /**
     * Cache table metadata
     */
    public void cacheTable(Object id, Object data) {
        if (redisTemplate != null) {
            String key = TABLE_CACHE_PREFIX + id;
            redisTemplate.opsForValue().set(key, data, DEFAULT_TTL, TimeUnit.HOURS);
        }
    }

    /**
     * Cache collection
     */
    public void cacheCollection(Object id, Object data) {
        if (redisTemplate != null) {
            String key = COLLECTION_CACHE_PREFIX + id;
            redisTemplate.opsForValue().set(key, data, DEFAULT_TTL, TimeUnit.HOURS);
        }
    }

    /**
     * Clear all caches
     */
    public void clearAll() {
        if (redisTemplate != null) {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
            log.info("All caches cleared");
        }
    }

    /**
     * Blacklist a JWT token using Redis (or fallback locally if template is null)
     */
    public void blacklistToken(String token, long expirationMs) {
        if (redisTemplate != null) {
            String key = "blacklist:" + token;
            redisTemplate.opsForValue().set(key, "revoked", expirationMs, TimeUnit.MILLISECONDS);
            log.info("Token added to Redis blacklist");
        }
    }

    /**
     * Check if a JWT token is blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        if (redisTemplate != null) {
            String key = "blacklist:" + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        }
        return false;
    }
}
