package com.cloudpool.service;

import lombok.extern.slf4j.Slf4j;
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
    @org.springframework.beans.factory.annotation.Value("${cloudpool.cache.ttl:1}")
    private long defaultTtl;

    public CacheService(Optional<RedisTemplate<String, Object>> redisTemplate) {
        this.redisTemplate = redisTemplate.orElse(null);
    }

    public void cacheFile(Object id, Object data) {
        if (redisTemplate != null) {
            String key = FILE_CACHE_PREFIX + id;
            redisTemplate.opsForValue().set(key, data, defaultTtl, TimeUnit.HOURS);
            log.debug("Cached file: {}", id);
        }
    }

    public Object getCachedFile(Object id) {
        if (redisTemplate != null) {
            String key = FILE_CACHE_PREFIX + id;
            return redisTemplate.opsForValue().get(key);
        }
        return null;
    }

    public void invalidateFile(Object id) {
        if (redisTemplate != null) {
            String key = FILE_CACHE_PREFIX + id;
            redisTemplate.delete(key);
            log.debug("Invalidated file cache: {}", id);
        }
    }

    public void cacheTable(Object id, Object data) {
        if (redisTemplate != null) {
            String key = TABLE_CACHE_PREFIX + id;
            redisTemplate.opsForValue().set(key, data, defaultTtl, TimeUnit.HOURS);
        }
    }

    public void cacheCollection(Object id, Object data) {
        if (redisTemplate != null) {
            String key = COLLECTION_CACHE_PREFIX + id;
            redisTemplate.opsForValue().set(key, data, defaultTtl, TimeUnit.HOURS);
        }
    }

    public void clearAll() {
        if (redisTemplate != null) {
            java.util.Set<String> keys = new java.util.HashSet<>();
            Optional.ofNullable(redisTemplate.keys(FILE_CACHE_PREFIX + "*")).ifPresent(keys::addAll);
            Optional.ofNullable(redisTemplate.keys(TABLE_CACHE_PREFIX + "*")).ifPresent(keys::addAll);
            Optional.ofNullable(redisTemplate.keys(COLLECTION_CACHE_PREFIX + "*")).ifPresent(keys::addAll);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            log.info("Application specific caches cleared safely");
        }
    }

    public void blacklistToken(String token, long expirationMs) {
        if (redisTemplate != null) {
            String key = "blacklist:" + token;
            redisTemplate.opsForValue().set(key, "revoked", expirationMs, TimeUnit.MILLISECONDS);
            log.info("Token added to Redis blacklist");
        }
    }

    public boolean isTokenBlacklisted(String token) {
        if (redisTemplate != null) {
            String key = "blacklist:" + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        }
        return false;
    }
}
