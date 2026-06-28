package com.cloudpool.filter;
 
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.security.Principal;
 
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {
 
    @Value("${cloudpool.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${cloudpool.rate-limit.requests-per-minute:120}")
    private double defaultRequestsPerMinute;
 
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Semaphore globalConcurrencyLimit = new Semaphore(1000);
 
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) 
            throws ServletException, IOException {

        if (!enabled || redisTemplate == null) {
            filterChain.doFilter(request, response);
            return;
        }
        
        if (isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }
 
        String clientId = getClientId(request);
        double limit = "GET".equalsIgnoreCase(request.getMethod()) ? defaultRequestsPerMinute : defaultRequestsPerMinute / 2.0;
        
        if (!globalConcurrencyLimit.tryAcquire()) {
            log.error("Global concurrency limit reached!");
            response.setStatus(503);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(java.util.Map.of("error", "Service unavailable due to high load.")));
            return;
        }

        try {
            String key = "ratelimit:" + clientId;
            Long currentRequests = redisTemplate.opsForValue().increment(key);
            if (currentRequests == 1) {
                redisTemplate.expire(key, 60, TimeUnit.SECONDS);
            }
            
            response.setHeader("X-RateLimit-Limit", String.valueOf((int)limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, (int)limit - currentRequests)));
            
            if (currentRequests > limit) {
                log.debug("Rate limit exceeded for client: {}", clientId);
                response.setStatus(429); // SC_TOO_MANY_REQUESTS
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write(objectMapper.writeValueAsString(java.util.Map.of("error", "Too many requests. Please try again later.")));
                return;
            }
 
            filterChain.doFilter(request, response);
        } finally {
            globalConcurrencyLimit.release();
        }
    }
 
    private String getClientId(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null && principal.getName() != null) {
            return "user:" + principal.getName();
        }
        
        // Try API key first
        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            return "api-key:" + apiKey;
        }
 
        // Prevent manual X-Forwarded-For spoofing. Rely on Spring Boot's 
        // ForwardedHeaderFilter to securely set RemoteAddr when behind a trusted proxy.
        return "ip:" + request.getRemoteAddr();
    }
 
    /**
     * Check if endpoint should be rate limited
     */
    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/error") || 
               path.equals("/") || 
               path.equals("/index.html") || 
               path.startsWith("/static/") || 
               path.equals("/favicon.ico");
    }
}
