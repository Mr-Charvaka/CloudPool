package com.cloudpool.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UsageTrackingService enforces per-minute API rate limits per user/API-key.
 *
 * Strategy:
 * - Primary store: Redis with a sliding 60-second TTL counter (INCR + EXPIRE).
 * - Fallback: In-memory ConcurrentHashMap when Redis is unavailable.
 *
 * Default limits (configurable via application.yml):
 * - Authenticated user:    300 requests / minute
 * - API key bearer:        120 requests / minute
 * - Unauthenticated:        30 requests / minute
 */
@Slf4j
@Service
public class UsageTrackingService {

    // Redis counter prefix
    private static final String PREFIX_USER   = "ratelimit:user:";
    private static final String PREFIX_APIKEY = "ratelimit:apikey:";
    private static final String PREFIX_ANON   = "ratelimit:anon:";

    // Default per-minute limits
    public static final int LIMIT_USER   = 300;
    public static final int LIMIT_APIKEY = 120;
    public static final int LIMIT_ANON   = 30;

    private final RedisTemplate<String, Object> redisTemplate;

    // In-memory fallback: key → [count, windowStartMs]
    private final Map<String, long[]> localCounters = new ConcurrentHashMap<>();

    public UsageTrackingService(Optional<RedisTemplate<String, Object>> redisTemplate) {
        this.redisTemplate = redisTemplate.orElse(null);
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Check and increment the per-minute request counter for an authenticated user.
     *
     * @return true if the request is allowed, false if the limit is exceeded.
     */
    public boolean checkAndIncrementUser(UUID userId) {
        return checkAndIncrement(PREFIX_USER + userId, LIMIT_USER);
    }

    /**
     * Check and increment the per-minute request counter for an API key.
     *
     * @return true if allowed, false if rate-limited.
     */
    public boolean checkAndIncrementApiKey(UUID apiKeyId) {
        return checkAndIncrement(PREFIX_APIKEY + apiKeyId, LIMIT_APIKEY);
    }

    /**
     * Check and increment the per-minute request counter for an anonymous IP.
     *
     * @return true if allowed, false if rate-limited.
     */
    public boolean checkAndIncrementAnonymous(String ipAddress) {
        return checkAndIncrement(PREFIX_ANON + ipAddress, LIMIT_ANON);
    }

    /**
     * Get the current request count in the active window for a user (for dashboards).
     */
    public long getCurrentUsage(UUID userId) {
        String key = PREFIX_USER + userId;
        if (redisTemplate != null) {
            try {
                Object val = redisTemplate.opsForValue().get(key);
                if (val instanceof Number n) return n.longValue();
            } catch (Exception ignored) {}
        }
        long[] state = localCounters.get(key);
        if (state == null) return 0L;
        if (System.currentTimeMillis() - state[1] > 60_000) return 0L;
        return state[0];
    }

    /**
     * Get the current request count for an API key (for dashboard display).
     */
    public long getCurrentApiKeyUsage(UUID apiKeyId) {
        String key = PREFIX_APIKEY + apiKeyId;
        if (redisTemplate != null) {
            try {
                Object val = redisTemplate.opsForValue().get(key);
                if (val instanceof Number n) return n.longValue();
            } catch (Exception ignored) {}
        }
        long[] state = localCounters.get(key);
        if (state == null) return 0L;
        if (System.currentTimeMillis() - state[1] > 60_000) return 0L;
        return state[0];
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private boolean checkAndIncrement(String key, int limit) {
        if (redisTemplate != null) {
            return checkRedis(key, limit);
        }
        return checkLocal(key, limit);
    }

    private boolean checkRedis(String key, int limit) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) return true; // Fail open on null
            if (count == 1L) {
                // First increment — set 60-second TTL to define the window
                redisTemplate.expire(key, 60, TimeUnit.SECONDS);
            }
            if (count > limit) {
                log.warn("Rate limit exceeded for key={} count={} limit={}", key, count, limit);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Redis rate-limit check failed, failing open: {}", e.getMessage());
            return true; // Fail open — never block users due to Redis downtime
        }
    }

    private boolean checkLocal(String key, int limit) {
        long now = System.currentTimeMillis();
        long[] state = localCounters.compute(key, (k, existing) -> {
            if (existing == null || now - existing[1] > 60_000) {
                // New window
                return new long[]{1L, now};
            }
            existing[0]++;
            return existing;
        });
        if (state[0] > limit) {
            log.warn("Local rate limit exceeded for key={} count={} limit={}", key, state[0], limit);
            return false;
        }
        return true;
    }
}
