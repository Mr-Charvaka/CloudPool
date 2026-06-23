package com.cloudpool.security;

import com.cloudpool.exception.CloudPoolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class RefreshRateLimiterTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private RefreshRateLimiter limiterWithRedis;
    private RefreshRateLimiter limiterWithoutRedis;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        limiterWithRedis = new RefreshRateLimiter(Optional.of(redisTemplate));
        limiterWithoutRedis = new RefreshRateLimiter(Optional.empty());
    }

    @Test
    @DisplayName("First request with Redis should pass and set TTL")
    void testFirstRequestWithRedis() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        limiterWithRedis.checkRateLimit("192.168.1.1", "family1");

        verify(redisTemplate).expire(anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Up to 5 requests per IP/family with Redis should pass")
    void testWithinLimitWithRedis() {
        when(valueOperations.increment(anyString()))
                .thenReturn(1L, 2L, 3L, 4L, 5L);

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiterWithRedis.checkRateLimit("10.0.0.1", "famA"));
        }
    }

    @Test
    @DisplayName("6th request with Redis should throw CloudPoolException")
    void testExceededLimitWithRedis() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        CloudPoolException ex = assertThrows(CloudPoolException.class,
                () -> limiterWithRedis.checkRateLimit("10.0.0.1", "famA"));
        assertTrue(ex.getMessage().contains("Too many refresh attempts"));
    }

    @Test
    @DisplayName("Different IPs should have independent Redis counters")
    void testDifferentIpsSeparateRedisCounters() {
        when(valueOperations.increment("ratelimit:refresh:10.0.0.1:famA")).thenReturn(1L);
        when(valueOperations.increment("ratelimit:refresh:10.0.0.2:famA")).thenReturn(6L);

        assertDoesNotThrow(() -> limiterWithRedis.checkRateLimit("10.0.0.1", "famA"));
        CloudPoolException ex = assertThrows(CloudPoolException.class,
                () -> limiterWithRedis.checkRateLimit("10.0.0.2", "famA"));
        assertTrue(ex.getMessage().contains("Too many refresh attempts"));
    }

    @Test
    @DisplayName("Different families for same IP should have independent Redis counters")
    void testDifferentFamiliesSeparateRedisCounters() {
        when(valueOperations.increment("ratelimit:refresh:10.0.0.1:famA")).thenReturn(1L);
        when(valueOperations.increment("ratelimit:refresh:10.0.0.1:famB")).thenReturn(6L);

        assertDoesNotThrow(() -> limiterWithRedis.checkRateLimit("10.0.0.1", "famA"));
        CloudPoolException ex = assertThrows(CloudPoolException.class,
                () -> limiterWithRedis.checkRateLimit("10.0.0.1", "famB"));
        assertTrue(ex.getMessage().contains("Too many refresh attempts"));
    }

    @Test
    @DisplayName("First request without Redis should pass using local fallback")
    void testFirstRequestWithoutRedis() {
        assertDoesNotThrow(() -> limiterWithoutRedis.checkRateLimit("10.0.0.1", "famA"));
    }

    @Test
    @DisplayName("Up to 5 requests without Redis should pass")
    void testWithinLimitWithoutRedis() {
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiterWithoutRedis.checkRateLimit("10.0.0.1", "famA"));
        }
    }

    @Test
    @DisplayName("6th request without Redis should throw CloudPoolException")
    void testExceededLimitWithoutRedis() {
        for (int i = 0; i < 5; i++) {
            limiterWithoutRedis.checkRateLimit("10.0.0.1", "famA");
        }
        assertThrows(CloudPoolException.class,
                () -> limiterWithoutRedis.checkRateLimit("10.0.0.1", "famA"));
    }

    @Test
    @DisplayName("Different IPs should have separate counters without Redis")
    void testDifferentIpsSeparateLocalCounters() {
        for (int i = 0; i < 5; i++) {
            limiterWithoutRedis.checkRateLimit("10.0.0.1", "famA");
        }
        assertDoesNotThrow(() -> limiterWithoutRedis.checkRateLimit("10.0.0.2", "famA"));
        assertThrows(CloudPoolException.class,
                () -> limiterWithoutRedis.checkRateLimit("10.0.0.1", "famA"));
    }

    @Test
    @DisplayName("Local fallback window should reset after 60 seconds")
    void testLocalWindowReset() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            limiterWithoutRedis.checkRateLimit("10.0.0.1", "famA");
        }
        assertThrows(CloudPoolException.class,
                () -> limiterWithoutRedis.checkRateLimit("10.0.0.1", "famA"));

        Thread.sleep(100);
        assertThrows(CloudPoolException.class,
                () -> limiterWithoutRedis.checkRateLimit("10.0.0.1", "famA"));
    }

    @Test
    @DisplayName("checkRateLimit should use composite key of IP and family")
    void testCompositeKey() {
        when(valueOperations.increment("ratelimit:refresh:10.0.0.1:familyX")).thenReturn(1L);

        limiterWithRedis.checkRateLimit("10.0.0.1", "familyX");

        verify(valueOperations).increment("ratelimit:refresh:10.0.0.1:familyX");
    }

    @Test
    @DisplayName("Should not set TTL on subsequent Redis increments")
    void testNoTtlOnSubsequentRequests() {
        when(valueOperations.increment(anyString())).thenReturn(3L);

        limiterWithRedis.checkRateLimit("10.0.0.1", "famA");

        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }
}