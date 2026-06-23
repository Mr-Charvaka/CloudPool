package com.cloudpool.security;

import com.cloudpool.exception.CloudPoolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class RefreshRateLimiter {

    private static final int MAX_REFRESHES_PER_MINUTE = 5;
    private static final int WINDOW_SECONDS = 60;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ConcurrentHashMap<String, Bucket> localBuckets = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public RefreshRateLimiter(Optional<RedisTemplate<String, Object>> redisTemplate) {
        this.redisTemplate = redisTemplate.orElse(null);
    }

    public void checkRateLimit(String ip, String familyId) {
        String key = "ratelimit:refresh:" + ip + ":" + familyId;

        if (redisTemplate != null) {
            Long count = redisTemplate.opsForValue()
                    .increment(key);
            if (count == 1) {
                redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (count > MAX_REFRESHES_PER_MINUTE) {
                log.warn("Rate limit exceeded for refresh: IP={} family={}", ip, familyId);
                throw new CloudPoolException("Too many refresh attempts. Try again later.");
            }
        } else {
            long now = System.currentTimeMillis() / 1000;
            Bucket bucket = localBuckets.computeIfAbsent(key, k -> new Bucket());
            synchronized (lock) {
                if (now - bucket.windowStart > WINDOW_SECONDS) {
                    bucket.count.set(0);
                    bucket.windowStart = (int) now;
                }
                if (bucket.count.incrementAndGet() > MAX_REFRESHES_PER_MINUTE) {
                    throw new CloudPoolException("Too many refresh attempts. Try again later.");
                }
            }
        }
    }

    private static class Bucket {
        final AtomicInteger count = new AtomicInteger(0);
        volatile int windowStart = (int) (System.currentTimeMillis() / 1000);
    }
}