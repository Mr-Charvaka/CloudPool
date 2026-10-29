package com.cloudpool.config;

import com.cloudpool.service.DatabaseConnectionProfileService;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Cache is an optimization, never a dependency: if Redis is unavailable the calls fall through to
 * the database. Entries also expire on their own, so a missed eviction cannot serve stale data forever.
 */
@Configuration
@Slf4j
public class ConnectionProfileCacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache '{}' read failed for key {}, using the database: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache '{}' write failed for key {}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache '{}' eviction failed for key {}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache '{}' clear failed: {}", cache.getName(), e.getMessage());
            }
        };
    }

    @Bean
    static RedisCacheManagerBuilderCustomizer connectionProfileCacheTtl(
            @Value("${cloudpool.cache.connection-profiles.ttl:10m}") String ttl) {
        Duration entryTtl = DurationStyle.detectAndParse(ttl);
        return builder -> builder.withCacheConfiguration(
                DatabaseConnectionProfileService.CACHE_NAME,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(entryTtl));
    }
}
