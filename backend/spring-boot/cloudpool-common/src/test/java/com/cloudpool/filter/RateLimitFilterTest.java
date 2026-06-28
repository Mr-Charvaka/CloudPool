package com.cloudpool.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
        
        // Setup default config via reflection
        ReflectionTestUtils.setField(rateLimitFilter, "defaultRequestsPerMinute", 10.0);
        ReflectionTestUtils.setField(rateLimitFilter, "enabled", true);
        
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should allow request when below rate limit (Testing Current Leaky Bucket Baseline)")
    void shouldAllowRequestBelowLimit() throws ServletException, IOException {
        // Arrange
        request.setRemoteAddr("192.168.1.1");
        String expectedRedisKey = "ratelimit:ip:192.168.1.1";
        
        when(valueOperations.increment(expectedRedisKey)).thenReturn(1L);

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertEquals(200, response.getStatus());
        verify(redisTemplate).expire(expectedRedisKey, 60, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should block request with HTTP 429 when limit exceeded")
    void shouldBlockRequestExceedingLimit() throws ServletException, IOException {
        // Arrange
        request.addHeader("X-API-KEY", "test-api-key");
        String expectedRedisKey = "ratelimit:api-key:test-api-key";
        
        when(valueOperations.increment(expectedRedisKey)).thenReturn(121L);

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertEquals(429, response.getStatus());
        // Verify it doesn't set expiration again after the 1st request
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("Should prioritize User Principal over API Key for rate limiting identification")
    void shouldPrioritizeUserPrincipal() throws ServletException, IOException {
        // Arrange
        request.addHeader("X-API-KEY", "test-api-key");
        Principal mockPrincipal = () -> "test-user-uuid";
        request.setUserPrincipal(mockPrincipal);
        
        String expectedRedisKey = "ratelimit:user:test-user-uuid";
        when(valueOperations.increment(expectedRedisKey)).thenReturn(5L);

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertEquals(200, response.getStatus());
        verify(valueOperations).increment(expectedRedisKey);
    }

    @Test
    @DisplayName("Should bypass filter completely if rate limiting is disabled via properties")
    void shouldBypassIfDisabled() throws ServletException, IOException {
        // Arrange
        ReflectionTestUtils.setField(rateLimitFilter, "enabled", false);

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertEquals(200, response.getStatus());
        verifyNoInteractions(redisTemplate);
    }
}
