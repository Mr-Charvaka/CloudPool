package com.cloudpool.filter;

import com.cloudpool.test.CommonTestApplication;
import com.cloudpool.test.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = CommonTestApplication.class)
class RateLimitFilterIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        
        // Clear Redis before each test
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("Should successfully connect to Redis Testcontainer and track IP requests")
    void testRedisIntegrationForRateLimiting() throws ServletException, IOException {
        String testIp = "203.0.113.50";
        request.setRemoteAddr(testIp);
        
        // Execute request
        rateLimitFilter.doFilterInternal(request, response, filterChain);
        
        // Assert Request was successful
        assertEquals(200, response.getStatus());
        
        // Directly query the real Redis container to verify it wrote the key
        String expectedKey = "ratelimit:ip:" + testIp;
        Integer count = (Integer) redisTemplate.opsForValue().get(expectedKey);
        
        assertEquals(1, count, "Redis should have tracked exactly 1 request");
        
        Long ttl = redisTemplate.getExpire(expectedKey);
        assertTrue(ttl != null && ttl > 0 && ttl <= 60, "TTL should be set to max 60 seconds");
    }

    @Test
    @DisplayName("Should return HTTP 429 when hitting Redis limit")
    void testRateLimitExceededIntegration() throws ServletException, IOException {
        String testIp = "203.0.113.99";
        request.setRemoteAddr(testIp);
        String expectedKey = "ratelimit:ip:" + testIp;

        // Manually seed the real Redis container with 120 requests
        redisTemplate.opsForValue().set(expectedKey, 120);

        // Execute request #121
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert that the filter actually blocks it
        assertEquals(429, response.getStatus());
    }
}
