package com.cloudpool.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class GraphQLRateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final int MAX_REQUESTS_PER_SECOND = 10;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        
        if (!"/graphql".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = getClientId(request);
        String key = "ratelimit:graphql:" + clientId;

        Long currentRequests = redisTemplate.opsForValue().increment(key);
        if (currentRequests == 1) {
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }

        if (currentRequests > MAX_REQUESTS_PER_SECOND) {
            log.warn("GraphQL rate limit exceeded for client: {}", clientId);
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "errors", List.of(Map.of("message", "Too many requests. Limit is 10 requests per second."))
            )));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientId(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null && principal.getName() != null) {
            return "user:" + principal.getName();
        }
        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            return "api-key:" + com.cloudpool.common.util.ApiKeyUtils.hashApiKey(apiKey);
        }
        return "ip:" + request.getRemoteAddr();
    }
}
